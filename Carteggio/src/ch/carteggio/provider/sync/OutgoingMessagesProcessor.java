/*******************************************************************************
 * Copyright (c) 2014, Lorenzo Keller
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ch.carteggio.provider.sync;

import java.util.ArrayList;
import java.util.Date;

import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.ParseException;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import ch.carteggio.net.ConfirmationReceipt;
import ch.carteggio.net.MessageTransport;
import ch.carteggio.net.MessagingException;
import ch.carteggio.net.NetworkFactories;
import ch.carteggio.net.TextMessage;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Messages;
import ch.carteggio.provider.CarteggioContract.Conversations.Participants;

public class OutgoingMessagesProcessor {

	private static final String LOG_TAG = "OutgoingMessageProcessor";
	
	private CarteggioAccount mAccount;
	private CarteggioProviderHelper mHelper;

	private ContentResolver mContentResolver;
	private Context mContext;

	private static String[] MESSAGES_PROJECTION = new String[] { Messages._ID,
																   Messages.CONVERSATION_ID, 
																   Messages.GLOBAL_ID,
																   Messages.SENT_DATE,
																   Messages.TEXT };

	private static String OUTGOING_MESSAGES_CONDITION = Messages.STATE + " = " + Messages.STATE_WAITING_TO_BE_SENT;
	
	private static String READ_MESSAGES_CONDITION = Messages.STATE + " = " + Messages.STATE_READ_LOCALLY_PENDING_CONFIRMATION_TO_REMOTE;

	public OutgoingMessagesProcessor(Context context, CarteggioAccount account) {
		mAccount = account;
		mHelper = new CarteggioProviderHelper(context);
		mContentResolver = context.getContentResolver();
		mContext = context;
	}
	
		
	
	public void sendPendingConfirmations() throws MessagingException {
		
		boolean someMessagesFailed = false;
		
		Cursor c = mContentResolver.query(Messages.CONTENT_URI, MESSAGES_PROJECTION, READ_MESSAGES_CONDITION, null, null);
		
		try {
					
			MessageTransport transport = NetworkFactories.getInstance(mContext).getMessageTransport(mAccount);
							
			while (c.moveToNext()) {

				long messageId = c.getLong(c.getColumnIndex(Messages._ID));
				long conversationId = c.getLong(c.getColumnIndex(Messages.CONVERSATION_ID));
				Date messageDate = new Date(c.getLong(c.getColumnIndex(Messages.SENT_DATE)));
				
				Uri conversationUri = ContentUris.withAppendedId(Conversations.CONTENT_URI, conversationId);
				
				String senderMailbox = mAccount.getDisplayName() + " <" + mAccount.getEmail() + ">";
				
				String[] destinationMailboxes = getParticipantsMailboxes(conversationUri);
				
				String messageSubject = getConversationSubject(conversationUri);
				
				String messageParent = c.getString(c.getColumnIndex(Messages.GLOBAL_ID));
								
				String messageGlobalId = mAccount.createRandomMessageId();
									
				try {
				
					Log.d(LOG_TAG, "Sending confirmation");
					
					ConfirmationReceipt receipt = new ConfirmationReceipt.Builder().setFrom(senderMailbox)
							   .setDate(messageDate)
							   .setDestinations(destinationMailboxes)
							   .setSubject(messageSubject)
							   .setMessageId(messageGlobalId)
							   .setParentId(messageParent)
							   .setDate(new Date())
							   .build();		
					
					transport.sendMessage(receipt.getMessage());
										
					mHelper.setMessageState(messageId , Messages.STATE_READ_LOCALLY_CONFIRMD_TO_REMOTE);
					
				} catch (ParseException ex) {
					Log.e(LOG_TAG, "Unable to parse address", ex);
					someMessagesFailed = true;
				} catch (MessagingException ex) {
					Log.e(LOG_TAG, "Unable to send message", ex);
					someMessagesFailed = true;
				}
				
			}
			
		} catch (Exception e) {
			Log.e(LOG_TAG, "Unable to create transport", e);
			someMessagesFailed = true;
		} finally {
			c.close();
		}
		
		if ( someMessagesFailed ) {
			throw new MessagingException("Sending some confirmations failed");
		}
		
	}
	
	
	public void sendPendingMessages() throws MessagingException {
				
		boolean messagesFailed = false;
		
		Cursor c = mContentResolver.query(Messages.CONTENT_URI, MESSAGES_PROJECTION, OUTGOING_MESSAGES_CONDITION, null, null);
		
		try {
						
			MessageTransport transport = NetworkFactories.getInstance(mContext).getMessageTransport(mAccount);
			
			while (c.moveToNext()) {

				long messageId = c.getLong(c.getColumnIndex(Messages._ID));
				long conversationId = c.getLong(c.getColumnIndex(Messages.CONVERSATION_ID));
				
				Uri conversationUri = ContentUris.withAppendedId(Conversations.CONTENT_URI, conversationId);
				
				String senderMailbox = mAccount.getDisplayName() + " <" + mAccount.getEmail() + ">";
				
				String[] destinationMailboxes = getParticipantsMailboxes(conversationUri);
				
				String messageSubject = getConversationSubject(conversationUri);				
				
				String messageText = c.getString(c.getColumnIndex(Messages.TEXT));
				
				String messageParent = getPreviousMessageGlobalIdInConversation(conversationId, messageId);								
				
				// to make sure the receiver will file the message in the correct conversation, we 
				// add a references field with references to some messages already sent
				String messageReferences = getPreviousReceivedMessageGlobalIdInConversation(conversationId, messageId);
				
				String messageGlobalId = c.getString(c.getColumnIndex(Messages.GLOBAL_ID));
				
				Date messageDate = new Date(c.getLong(c.getColumnIndex(Messages.SENT_DATE)));
				

				try {
					
					Log.d(LOG_TAG, "Sending message");					
					
					TextMessage message = new TextMessage.Builder().setFrom(senderMailbox)
																   .setDestinations(destinationMailboxes)															   
																   .setMessageId(messageGlobalId)
																   .setParentId(messageParent)
																   .setReferences(messageReferences)
																   .setDate(messageDate)
																   .setSubject(messageSubject)
																   .setText(messageText)
																   .build();
							   
					transport.sendMessage(message.getMessage());				
					
					mHelper.setMessageState(messageId , Messages.STATE_DELIVERED_TO_SERVER);
					
				} catch (ParseException ex) {
					Log.e(LOG_TAG, "Unable to parse address", ex);
					messagesFailed = true;
				} catch (MessagingException ex) {
					Log.e(LOG_TAG, "Unable to send message", ex);
					messagesFailed = true;
				}
				
			}
		} catch (Exception ex) {
			Log.e(LOG_TAG, "Unable to send message", ex);
			messagesFailed = true;
		} finally {
			c.close();
		}
		
		if (messagesFailed) {
			throw new MessagingException("Failed to send some messages");
		}
		
	}	

	private String getPreviousReceivedMessageGlobalIdInConversation(long conversationId, long messageId) {
		
		Cursor c = mContentResolver.query(Messages.CONTENT_URI, new String[] { Messages.GLOBAL_ID },
															Messages.CONVERSATION_ID + " = ? AND " + Messages._ID + " < ? AND " + Messages.SENDER_ID + " != " + mAccount.getContactId(),
															new String[] { Long.toString(conversationId), Long.toString(messageId) }, Messages.SENT_DATE + " DESC");
							
		
		try {
			
			if ( c.moveToFirst()) {
				return c.getString(c.getColumnIndex(Messages.GLOBAL_ID));
			}
			
		} finally {						
			c.close();
		}
		
		return null;
	}

	
	private String getPreviousMessageGlobalIdInConversation(long conversationId, long messageId) {
		
		Cursor c = mContentResolver.query(Messages.CONTENT_URI, new String[] { Messages.GLOBAL_ID },
															Messages.CONVERSATION_ID + " = ? AND " + Messages._ID + " < ?",
															new String[] { Long.toString(conversationId), Long.toString(messageId) }, Messages.SENT_DATE + " DESC");
							
		
		try {
			
			if ( c.moveToFirst()) {
				return c.getString(c.getColumnIndex(Messages.GLOBAL_ID));
			}
			
		} finally {						
			c.close();
		}
		
		return null;
	}


	private String[] getParticipantsMailboxes(Uri conversationUri) {
				
		Uri participantsUri = Uri.withAppendedPath(conversationUri, Conversations.Participants.CONTENT_DIRECTORY);		
		
		Cursor c = mContentResolver.query(participantsUri, new String[] { Participants.NAME, Participants.EMAIL} ,
																			null, null, null );
		
		ArrayList<String> destinationMailboxes = new ArrayList<String>();
		
		try {						
							
			while ( c.moveToNext()) {
				
				String email = c.getString(c.getColumnIndex(Participants.EMAIL));				
				
				String emailParts [] = email.split("@");
				
				if ( emailParts.length != 2) {
					continue;
				}
				
				Mailbox m = new Mailbox(c.getString(c.getColumnIndex(Participants.NAME)),
										emailParts[0], emailParts[1]);
				
				destinationMailboxes.add(m.getAddress());
				
			}
			
		} finally {
			c.close();
		}
		
		return destinationMailboxes.toArray(new String[0]);
	}


	private String getConversationSubject(Uri conversationUri) {
			
		Cursor c = mContentResolver.query(conversationUri, new String[] { Conversations.SUBJECT }, null, null, null);					
		
		try {
			
			if ( c.moveToFirst()) {
				return c.getString(c.getColumnIndex(Conversations.SUBJECT));				
			}
			
		} finally {
			c.close();
		}
		
		return null;
	}

	
}
