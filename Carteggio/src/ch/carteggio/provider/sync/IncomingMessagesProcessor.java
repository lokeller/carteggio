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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.FieldName;
import org.apache.james.mime4j.field.address.AddressBuilder;
import org.apache.james.mime4j.stream.Field;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import ch.carteggio.net.MessageStore;
import ch.carteggio.net.MessageStore.SynchronizationPoint;
import ch.carteggio.net.MessagingException;
import ch.carteggio.net.MessageStore.Folder;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.CarteggioContract.Messages;

public class IncomingMessagesProcessor {
			
	private CarteggioAccount mAccount;
	
	private static final String LOG_TAG = "IncomingMessageProcessor";

	private CarteggioProviderHelper mHelper;
	private Context mContext;
	
	
	public IncomingMessagesProcessor(Context context, CarteggioAccount account) {			
		this.mAccount = account;
		this.mHelper = new CarteggioProviderHelper(context);
		this.mContext = context;
	}

	public void checkNewMessages() {
	
		Intent intent = new Intent(mContext, MessageReceiverService.class);
		
		intent.setAction(MessageReceiverService.ACTION_POLL);
		
		mContext.startService(intent);
		
	}

	public void processFolder(MessageStore.Folder folder) throws MessagingException {
		
		SynchronizationPoint syncPoint = folder.getMessageStore().createSynchronizationPoint(mAccount.getPushState());
		
		// get the list of all messages that have been added since the last time we 
		// checked the mailbox					 
		
		Message[] messages = folder.getMessagesAfter(syncPoint);
		
		// now look for messages that are for us and update the database
		processMessages(folder, messages);
		
		// save the sync point only after we finished processing the messages
		mAccount.setPushState(syncPoint.save());
		
	}

	public void processMessages(MessageStore.Folder folder, Message[] messages) throws MessagingException {
						
		folder.fetchEnvelopes(messages);
		
		HashMap<Message, Uri> receipts = new HashMap<Message, Uri>();
		HashMap<Message, Uri> incomingMessages = new HashMap<Message, Uri>();											
		
		for (Message msg : messages ) {
			
			// ignore messages not for the currently configured identity
			// this allows multiple identities to work on the same mailbox
			if ( !checkMessageIsForAccount(msg) ) {
				continue;
			}						
			
			boolean isFromCarteggio = checkIsFromCarteggio(msg);
			
			if (isDeliveryReport(msg) ) {							
			
				HashSet<String> referencedMessagesIds = getReferencedMessagesIds(msg);
				
				if ( referencedMessagesIds.size() == 1) {
				
					String messageId = referencedMessagesIds.iterator().next();
					
					Uri referencedMessage = mHelper.findMessageByGlobalId(messageId);
					
					if ( referencedMessage != null) {					
						receipts.put(msg, referencedMessage);
					}
					
				}
				
			} else {
				
				Uri referencedMessage = mHelper.findConversationByMessageGlobalIds(getReferencedMessagesIds(msg));
				
				if ( referencedMessage != null) {
					incomingMessages.put(msg, referencedMessage);								
				} else if ( isFromCarteggio) {
					incomingMessages.put(msg, null);
				}
				
			}
							
		
		}
							
		// now that we know which are the emails we are really interested into, we can download them and process them
		folder.fetchStructures(incomingMessages.keySet().toArray(new Message[0]));
		
		for ( Map.Entry<Message, Uri> entry: incomingMessages.entrySet()) {
			
			Log.d(LOG_TAG, "Received message");
			
			processMessage(folder, entry.getKey(), entry.getValue());
		}
	
		// process all the return recipes
		for ( Uri message: receipts.values()) {
			
			Log.d(LOG_TAG, "Received receipt");
			
			mHelper.setMessageState(message, Messages.STATE_RECEIVED_BY_DESTINATION);
		}
	
		Folder carteggioFolder = folder.getMessageStore().getPrivateFolder();
		
		folder.moveMessages(incomingMessages.keySet().toArray(new Message[0]), carteggioFolder);
							
		folder.moveMessages(receipts.keySet().toArray(new Message[0]), carteggioFolder);
		
		if ( incomingMessages.size() > 0) { 
			NotificationService.notifyNewIncomingMessages(mContext);
		}
		
	}


	private void processMessage(Folder folder, Message msg, Uri conversation) {
				
		String senderEmail = msg.getFrom().get(0).getAddress();
		
		Uri sender;
		
		if (conversation == null) {
			
			String senderName = msg.getFrom().get(0).getName(); 
			
			sender = mHelper.createOrUpdateContact(senderEmail, senderName, -1);
			
			conversation = mHelper.createConversation(mAccount, sender);
			
		} else {
		
			sender = mHelper.getContact(senderEmail);
		
			if ( sender == null) {
				Log.e(LOG_TAG, "Contact for the message sender " + senderEmail + " was not found");
				return;
			}
			
		}			
		
		String message = fetchText(folder, msg, msg);		
		
		Date sentDate = msg.getDate();
		
		String globalId = msg.getMessageId().substring(1, msg.getMessageId().length() - 1);		
		
		mHelper.createIncomingMessage(mAccount, conversation, sender, message, sentDate, globalId);		
		
	}

	private boolean checkIsFromCarteggio(Message msg) {
				
		Field userAgentField = msg.getHeader().getField("User-Agent");
				
		return userAgentField != null && userAgentField.getBody().startsWith("Carteggio");		
	}	

	

	private boolean isDeliveryReport(Message msg) {
		
		ContentTypeField contentType = (ContentTypeField) msg.getHeader().getField(FieldName.CONTENT_TYPE);
		
		return contentType != null &&
				contentType.isMimeType("multipart/report") && 
				"disposition-notification".equalsIgnoreCase(contentType.getParameter("report-type"));
	}

	private HashSet<String> getReferencedMessagesIds(Message msg) {
		
		HashSet<String> referencedEmails = new HashSet<String>();
		
		Field replyTo = msg.getHeader().getField("in-reply-to");
		Field references = msg.getHeader().getField("references");
		
		if ( replyTo != null) {
			
			
			try {
				Mailbox address = AddressBuilder.DEFAULT.parseMailbox(replyTo.getBody());
				
				referencedEmails.add(address.getAddress());
				
			} catch (Exception ex) {
				Log.d(LOG_TAG, "Invalid in-reply-to header, skipping message");
			}							
			
		}
		
		if (references != null) {
			
			try {
				
				Mailbox address = AddressBuilder.DEFAULT.parseMailbox(references.getBody());
				
				referencedEmails.add(address.getAddress());
												
			} catch (Exception ex) {
				Log.d(LOG_TAG, "Invalid references header, skipping message");
			}
			
		}
		
		return referencedEmails;
	}

	private boolean checkMessageIsForAccount(org.apache.james.mime4j.dom.Message msg) {
		boolean isForMe = false;
		
		if ( msg.getTo() != null) {
		
			for ( Address addr : msg.getTo()) {
				if ( addr instanceof Mailbox) {
					if ( ((Mailbox) addr).getAddress().equals(mAccount.getEmail())) {
						isForMe = true;
					}
				}
			}
			
		}
			
		return isForMe;
	}

	private String fetchText(Folder folder, Message msg, Entity entity) {
							
		if ( entity.isMultipart()) {
			
			Multipart mp = (Multipart) entity.getBody();
			
			for ( Entity bodyPart : mp.getBodyParts() ) {
									
				String text = fetchText(folder, msg, bodyPart);
				
				if ( text != null) {
					return text;
				}
				
			}
			
		} else if ( entity.getMimeType().equals("text/plain")){
			
			try {
				
				folder.fetchPart(msg, entity);
				
				InputStream stream = ((TextBody) entity.getBody()).getInputStream();
				
				InputStreamReader streamReader = new InputStreamReader(stream, entity.getCharset());
				
				StringWriter writer = new StringWriter();
				
				char [] data = new char[1024];
	
				int readChars;
				
				while ( (readChars = streamReader.read(data)) >= 0 ) {
	
					writer.write(data, 0, readChars);
					
				}
				
				return writer.getBuffer().toString();					
				
			} catch (MessagingException ex ) {
				Log.e(LOG_TAG, "Unable to fetch body of message", ex);
			} catch (IOException ex ) {
				Log.e(LOG_TAG, "Unable to read body of message", ex);
			}
		}
		
		
		return null;
	}

	
}
