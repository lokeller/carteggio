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
package ch.carteggio.provider;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.text.TextUtils;
import android.util.Log;
import ch.carteggio.provider.CarteggioContract.Contacts;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Messages;
import ch.carteggio.provider.CarteggioContract.Conversations.Participants;

public class CarteggioProviderHelper {
	
	public static final String DEFAULT_SUBJECT = "Carteggio conversation";
	private static final String LOG_TAG = "CarteggioProviderHelper";
	private Context mContext;
	
	public CarteggioProviderHelper(Context context) {
		mContext = context;
	}	
	
	public void forceUpdate() {
		
		Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(
                ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(
                ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        
		Account[] accountsByType = AccountManager.get(mContext).getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		
		if ( accountsByType.length == 0 ) {
			return;
		}
		
		Account account = accountsByType[0];
        
        ContentResolver.requestSync(account, CarteggioContract.AUTHORITY, settingsBundle);
	}

	
	public Uri createOutgoingMessage(CarteggioAccount account, Uri conversation, String message) {
				
		ContentResolver cr = mContext.getContentResolver();
		
		ContentValues values = new ContentValues();
		
		values.put(Messages.SENT_DATE, new Date().getTime());
		values.put(Messages.TEXT, message);
		values.put(Messages.CONVERSATION_ID, ContentUris.parseId(conversation));
		values.put(Messages.STATE, Messages.STATE_WAITING_TO_BE_SENT);
		values.put(Messages.SENDER_ID, account.getContactId());
		values.put(Messages.GLOBAL_ID, account.createRandomMessageId());
						
		return cr.insert(Messages.CONTENT_URI, values);
		
	}
	
	public Uri createIncomingMessage(CarteggioAccount account, Uri conversation, Uri sender, String message, Date sentDate, String globalId) {
		
		ContentResolver cr = mContext.getContentResolver();
								
		ContentValues values = new ContentValues();
		
		values.put(Messages.SENT_DATE, sentDate.getTime());
		values.put(Messages.TEXT, message);
		values.put(Messages.CONVERSATION_ID, ContentUris.parseId(conversation));
		values.put(Messages.STATE, Messages.STATE_WAITING_TO_BE_READ);
		values.put(Messages.SENDER_ID, ContentUris.parseId(sender));
		values.put(Messages.GLOBAL_ID, globalId);
						
		try {
			
			
			return cr.insert(Messages.CONTENT_URI, values);
			
		} catch ( SQLiteConstraintException ex ) {
			
			Log.d(LOG_TAG, "Message with globalId " +  globalId + " already exists in database");
			
			return null;
		}
		
	}
	
	
	public Uri createConversation(CarteggioAccount account, Uri contact) {
		return createConversation(account, new Uri[] { contact });
	}
	
	public Uri createConversation(CarteggioAccount account, Uri[] contacts) {
		
		ContentResolver cr = mContext.getContentResolver();
		
		/* create the conversation */
		
		ContentValues newConversation = new ContentValues();
		
		newConversation.put(CarteggioContract.Conversations.SUBJECT, DEFAULT_SUBJECT);
		
		Uri conversation = cr.insert(CarteggioContract.Conversations.CONTENT_URI, newConversation);
		
		/* add the participants to the conversation */
		
		for ( Uri contact : contacts) {
		
			long contactId = ContentUris.parseId(contact);
			
			ContentValues participant = new ContentValues();
			
			participant.put(CarteggioContract.Conversations.Participants.CONTACT_ID, contactId);
			
			cr.insert(Uri.withAppendedPath(conversation, CarteggioContract.Conversations.Participants.CONTENT_DIRECTORY), participant);
			
		}
		
		return conversation;
		
	}
	
	/**
	 * Creates a contact if none with the same email exist, or updates an existing contact 
	 * 
	 * @param email the email of the contact
	 * @param name the name of the contact
	 * @param androidContactId the ID of the contact in the android contacts provider, -1 if this is not available
	 * 
	 * @return the uri of the contact
	 */
	public Uri createOrUpdateContact(String email, String name, long androidContactId) {
		
		ContentResolver cr = mContext.getContentResolver();
		
		String [] projection = { CarteggioContract.Contacts._ID };
		
		String selection = CarteggioContract.Contacts.EMAIL + " = ?";
		
		String [] selectionArgs = { email };
		
		Cursor c = cr.query(CarteggioContract.Contacts.CONTENT_URI, projection, selection, selectionArgs, null);
		
		try {
		
			ContentValues values = new ContentValues();
			
			values.put(Contacts.EMAIL, email);
			values.put(Contacts.NAME, name);
			values.put(Contacts.ANDROID_CONTACT_ID, androidContactId);
						
			if ( c.isAfterLast()) {
							
				return cr.insert(CarteggioContract.Contacts.CONTENT_URI, values);				
				
			} else {
				
				c.moveToFirst();
				
				long contactId = c.getLong(c.getColumnIndex(CarteggioContract.Contacts._ID));
							
				Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
				
				cr.update(uri, values, null, null);
							
				return uri;
				
			}

		} finally {
			c.close();
		}

		
	}

	public CarteggioAccount getDefaultAccount() {

		AccountManager accountManager = AccountManager.get(mContext);
		
		Account[] accountsByType = accountManager.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		
		if ( accountsByType.length == 0 ) {
			return null;
		}
		
		return new CarteggioAccountImpl(mContext, accountsByType[0]);
	}

	public Uri findConversationByMessageGlobalIds(HashSet<String> globalIds) {
		
		String where = TextUtils.join(",", globalIds);
		
		Cursor c = mContext.getContentResolver().query(Messages.CONTENT_URI, 
																new String[] { Messages.CONVERSATION_ID},															
																Messages.GLOBAL_ID + " IN ( " + DatabaseUtils.sqlEscapeString(where) + ")", null, null);							
		
		try {
			
			if ( c.moveToFirst()) {													
		
				long conversationId = c.getLong(c.getColumnIndex(Messages.CONVERSATION_ID));
				
				return ContentUris.withAppendedId(Conversations.CONTENT_URI, conversationId); 
			}
			
		} finally {
			c.close();
		}
		
		return null;
	}
	

	public Uri findMessageByGlobalId(String globalMessageId) {
		
		Cursor c = mContext.getContentResolver().query(Messages.CONTENT_URI, 
																new String[] { Messages._ID},
																Messages.GLOBAL_ID + " = ?" ,
																new String[] { globalMessageId }, null);							
				
		try {
			
			if ( c.moveToFirst()) {
		
				long messageId = c.getLong(c.getColumnIndex(Messages._ID));
		
				return ContentUris.withAppendedId(Messages.CONTENT_URI, messageId);
				
			}
			
		} finally {
			c.close();
		}
		
		return null;
	}


	public Uri getContact(String email) {
		
		ContentResolver cr = mContext.getContentResolver();
		
		String [] projection = { CarteggioContract.Contacts._ID };
		
		String selection = CarteggioContract.Contacts.EMAIL + " = ?";
		
		String [] selectionArgs = { email };
		
		Cursor c = cr.query(CarteggioContract.Contacts.CONTENT_URI, projection, selection, selectionArgs, null);
		
		try {
		
			if ( !c.isAfterLast()) {
				
				c.moveToFirst();
				
				long contactId = c.getLong(c.getColumnIndex(CarteggioContract.Contacts._ID));
			
				return ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
				
			}

		} finally {
			c.close();
		}

		return null;
	}
	
	public void setMessageState(Uri message, int state) {
		
		ContentValues values = new ContentValues();
		
		values.put(Messages.STATE, state);
		
		mContext.getContentResolver().update(message, values, null, null);
					
	}
	
	public void setMessageState(long message, int state) {
		setMessageState(ContentUris.withAppendedId(Messages.CONTENT_URI, message), state);
	}
	
	public String getConversationSubject(Uri conversation) {
		
		ContentResolver cr = mContext.getContentResolver();
		
		Cursor conversationCursor = cr.query(conversation, new String[] { Conversations.SUBJECT }, null, null, null);
				
		try {
			
			if ( !conversationCursor.moveToFirst()) {
				return null;
			}
			
			return conversationCursor.getString(conversationCursor.getColumnIndex(Conversations.SUBJECT));
			
		} finally {
			conversationCursor.close();
		}
		
	}

	public int getUnreadCount() {
		
		ContentResolver cr = mContext.getContentResolver();
		
		Cursor conversationCursor = cr.query(Conversations.CONTENT_URI, new String[] { "SUM(" + Conversations.UNREAD_MESSAGES_COUNT + ")" }, null, null, null);
		
		try {
			
			conversationCursor.moveToFirst();
			
			return conversationCursor.getInt(0);
			
		} finally {
			conversationCursor.close();
		}
		
	}

	public String[] getParticipantsEmails(long conversationId) {
		
		ContentResolver cr = mContext.getContentResolver();
		
		Uri conversationUri = ContentUris.withAppendedId(Conversations.CONTENT_URI, 
															conversationId);
		
		Uri participantsUri = Uri.withAppendedPath(conversationUri,
												Participants.CONTENT_DIRECTORY);
		
		Cursor c = cr.query(participantsUri, 
				new String[] { Participants.EMAIL }, null, null, null);
		
		
		ArrayList<String> emails = new ArrayList<String>();
		
		try {
			
			int columnIndex = c.getColumnIndex(Participants.EMAIL);
			
			while (c.moveToNext()) {
				emails.add(c.getString(columnIndex));
			}
			
		} finally {
			c.close();
		}
		
		return emails.toArray(new String[0]);
		
	}

	public Uri getContactPhotoUri(String email) {

		ContentResolver cr = mContext.getContentResolver();
		
		Uri lookupUri = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(email));
		
		Cursor c = cr.query(lookupUri, 
							new String[] { Email.PHOTO_THUMBNAIL_URI}, 
							null, null, null);
		try {
			
			if (!c.moveToFirst()) {
				return null;
			}
			
			String photoUri = c.getString(c.getColumnIndex(Email.PHOTO_THUMBNAIL_URI));
			
			if (photoUri == null) {
				return null;
			}
			
			return Uri.parse(photoUri);
			
		} finally {
			c.close();
		}
		
	}

	public void removeParticipant(Uri mConversation, String email) {
	
		Uri contact = getContact(email);
		
		
		
	}
	
}
