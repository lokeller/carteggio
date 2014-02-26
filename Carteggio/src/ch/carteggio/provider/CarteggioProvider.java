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
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import ch.carteggio.provider.CarteggioContract.Contacts;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Messages;
import ch.carteggio.provider.CarteggioDatabaseHelper.ContactsColumns;
import ch.carteggio.provider.CarteggioDatabaseHelper.ConversationsColumns;
import ch.carteggio.provider.CarteggioDatabaseHelper.MessagesColumns;
import ch.carteggio.provider.CarteggioDatabaseHelper.ParticipantsColumns;
import ch.carteggio.provider.CarteggioDatabaseHelper.Tables;
import ch.carteggio.provider.CarteggioDatabaseHelper.Views;

@SuppressLint("NewApi")
public class CarteggioProvider extends ContentProvider {
	
    private static final int CONVERSATION_ID_PATH_POSITION = 1;
    private static final int MESSAGE_ID_PATH_POSITION = 1;
    private static final int CONTACT_ID_PATH_POSITION = 1;
    
	private static final UriMatcher sUriMatcher;

	private static final int CONTACTS = 0;
	private static final int CONTACT_ID = 1;
	private static final int CONVERSATIONS = 2;
	private static final int CONVERSATION_ID = 3;
	private static final int CONVERSATION_PARTICIPANTS = 4;
	private static final int MESSAGE_ID = 5;
	private static final int MESSAGES = 6;

    private CarteggioDatabaseHelper mOpenHelper;

    
    static {

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(CarteggioContract.AUTHORITY, Contacts.CONTENT_URI.getPath().substring(1), CONTACTS);
        sUriMatcher.addURI(CarteggioContract.AUTHORITY, Uri.withAppendedPath(Contacts.CONTENT_URI, "#").getPath().substring(1), CONTACT_ID);
        
        sUriMatcher.addURI(CarteggioContract.AUTHORITY, Conversations.CONTENT_URI.getPath().substring(1), CONVERSATIONS);
        sUriMatcher.addURI(CarteggioContract.AUTHORITY, Uri.withAppendedPath(Conversations.CONTENT_URI, "#").getPath().substring(1), CONVERSATION_ID);
        sUriMatcher.addURI(CarteggioContract.AUTHORITY, Uri.withAppendedPath(Conversations.CONTENT_URI, "#/" + Conversations.Participants.CONTENT_DIRECTORY).getPath().substring(1), CONVERSATION_PARTICIPANTS);
        
        sUriMatcher.addURI(CarteggioContract.AUTHORITY, Messages.CONTENT_URI.getPath().substring(1), MESSAGES);
        sUriMatcher.addURI(CarteggioContract.AUTHORITY, Uri.withAppendedPath(Messages.CONTENT_URI, "#").getPath().substring(1), MESSAGE_ID);        

    }
    
    @Override
    public boolean onCreate() {

        mOpenHelper = new CarteggioDatabaseHelper(getContext());

        return true;
    }
    
    
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        String orderBy = sortOrder;
        
        String appendSelection = null;
        
        switch (sUriMatcher.match(uri)) {
        
        	case CONVERSATIONS:
        
        	{
            	qb.setTables(Views.CONVERSATIONS);
        
                if (TextUtils.isEmpty(sortOrder)) {                	                	
                    orderBy = Conversations.LAST_MESSAGE_SENT_DATE + " DESC";
                }
                
                break;
        	}

        	case CONVERSATION_ID:
                
        	{

            	String conversationId = uri.getPathSegments().get(CONVERSATION_ID_PATH_POSITION);
               	appendSelection = Conversations._ID + " = " + conversationId;            	
        		qb.setTables(Views.CONVERSATIONS);            	
                        
                break;
        	}

        	case MESSAGES:
        
        	{
            	qb.setTables(Views.MESSAGES);            
                
                break;
        	}

        	
        	case MESSAGE_ID:
                
        	{

            	String messageId = uri.getPathSegments().get(MESSAGE_ID_PATH_POSITION);
               	appendSelection = Messages._ID + " = " + messageId;
        		qb.setTables(Views.MESSAGES);
                
                break;
        	}

        	case CONTACTS:
                
        	{
            	qb.setTables(Views.CONTACTS);            
                
                break;
        	}
        	
        	case CONTACT_ID:
                
        	{

            	String contactId = uri.getPathSegments().get(CONTACT_ID_PATH_POSITION);
               	appendSelection = Contacts._ID + " = " + contactId;
        		qb.setTables(Views.CONTACTS);
                
                break;
        	}        
            
            case CONVERSATION_PARTICIPANTS:

            {   
            	String conversationId = uri.getPathSegments().get(CONVERSATION_ID_PATH_POSITION);

            	appendSelection = Conversations.Participants.CONVERSATION_ID +" = " + conversationId;
            	
               	qb.setTables(Views.PARTICIPANTS);
                
                break;
          
            }   
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (appendSelection != null) {
        
        	if (selection == null) {
        		selection = appendSelection;
        	} else {
        		selection += " AND " + appendSelection;
        	}
        }
        
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        
        c.setNotificationUri(getContext().getContentResolver(), uri);
        
        return c;
    }


	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();        

        int count;

        if (selection != null ) {
        	throw new IllegalArgumentException("Cannot set selection for delete");            
        }
        
        String finalSelection;
        Uri collectionUri;
        
        switch (sUriMatcher.match(uri)) {
        	
        	case CONVERSATION_PARTICIPANTS:        	
        	case CONVERSATIONS:
	        case MESSAGES:
	
	        	throw new IllegalArgumentException("Cannot delete from this URI");
        
	        case MESSAGE_ID:
	            
            	String messageID = uri.getPathSegments().get(MESSAGE_ID_PATH_POSITION);
            	
            	finalSelection = MessagesColumns.CONCRETE_ID + " = " +  messageID;                
                
            	collectionUri = Messages.CONTENT_URI;
            	
                break;
	        	
            case CONVERSATION_ID:
        
            	String conversationID = uri.getPathSegments().get(CONVERSATION_ID_PATH_POSITION);
            	
            	finalSelection = ConversationsColumns.CONCRETE_ID + " = " +  conversationID;                
                
            	collectionUri = Conversations.CONTENT_URI;
            	
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        count = db.delete(CarteggioDatabaseHelper.Tables.CONVERSATIONS, finalSelection, selectionArgs);                        
       
        getContext().getContentResolver().notifyChange(collectionUri, null);        
        getContext().getContentResolver().notifyChange(uri, null);
       
        return count;
		
	}


	@Override
	public String getType(Uri uri) {
	
		switch (sUriMatcher.match(uri)) {
   	
			case CONVERSATIONS:           
				return Conversations.CONTENT_TYPE;
			
			case CONVERSATION_ID:
				return Conversations.CONTENT_ITEM_TYPE;
		
			case CONVERSATION_PARTICIPANTS:
				return Conversations.Participants.CONTENT_TYPE;
								
			case MESSAGES:
				return Messages.CONTENT_TYPE;
				
			case MESSAGE_ID:
				return Messages.CONTENT_ITEM_TYPE;				
	
			case CONTACTS:
				return Contacts.CONTENT_TYPE;
				
			case CONTACT_ID:
				return Contacts.CONTENT_ITEM_TYPE;
		
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
	}
	
	
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {

		Map<String, String> insertMap = new HashMap<String, String>();
		
		String table;
		        
        ArrayList<Uri> additionalUri = new ArrayList<Uri>();        
				
		switch (sUriMatcher.match(uri)) {
			
			case CONVERSATIONS:
	
				insertMap.put(Conversations.SUBJECT, ConversationsColumns.SUBJECT);        
		         
				table = Tables.CONVERSATIONS;
				
				break;

			case MESSAGES:
				
		        insertMap.put(Messages.GLOBAL_ID, Messages.GLOBAL_ID);
		        insertMap.put(Messages.CONVERSATION_ID, MessagesColumns.CONVERSATION_ID);
		        insertMap.put(Messages.STATE, MessagesColumns.STATE);
		        insertMap.put(Messages.SENDER_ID, MessagesColumns.SENDER_ID);
		        insertMap.put(Messages.SENT_DATE, MessagesColumns.SENT_DATE);
		        insertMap.put(Messages.TEXT, MessagesColumns.TEXT);

				table = Tables.MESSAGES;
				
				additionalUri.add(Conversations.CONTENT_URI);
				additionalUri.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, 
						initialValues.getAsLong(Messages.CONVERSATION_ID)));
				
				break;
					
			case CONTACTS:
				
				table = Tables.CONTACTS;
		        
				insertMap.put(Contacts.COLOR, ContactsColumns.COLOR);
		        insertMap.put(Contacts.ANDROID_CONTACT_ID, ContactsColumns.ANDROID_CONTACT_ID);
		        insertMap.put(Contacts.EMAIL, ContactsColumns.EMAIL);
		        insertMap.put(Contacts.NAME, ContactsColumns.NAME);		        
				
				break;
			
			case CONVERSATION_PARTICIPANTS:
			{	
				
				table = Tables.PARTICIPANTS;
				
				insertMap.put(Conversations.Participants._ID, ParticipantsColumns.CONTACT_ID);
				insertMap.put(Conversations.Participants.CONVERSATION_ID, ParticipantsColumns.CONVERSATION_ID);		       
				
            	String conversationId = uri.getPathSegments().get(CONVERSATION_ID_PATH_POSITION);            	
				String contactId = initialValues.getAsString(Conversations.Participants._ID);
            	
				initialValues.clear();
				
            	initialValues.put(Conversations.Participants.CONVERSATION_ID, conversationId);				
            	initialValues.put(Conversations.Participants._ID, contactId);            
				
				additionalUri.add(Conversations.CONTENT_URI);
				
				Uri conversationUri = ContentUris.withAppendedId(Conversations.CONTENT_URI, Long.parseLong(conversationId));				
				additionalUri.add(conversationUri);

				break;
			}	
			
			default:
	            throw new IllegalArgumentException("Unknown URI " + uri);
				
		}
			
		ContentValues values = new ContentValues();
		
        for ( Map.Entry<String, Object> entries : initialValues.valueSet()) {        	
        				
        	if ( !insertMap.containsKey(entries.getKey()) ) {
        		throw new IllegalArgumentException("Cannot set initial value " + entries.getKey());
        	} else {        	
        		values.put(insertMap.get(entries.getKey()), initialValues.getAsString(entries.getKey()));         		
        	}
        	
        }
        
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        
		long rowId = db.insert( table, null, values);

        if (rowId == 0) {
        	throw new SQLException("Failed to insert row into " + uri);            
        }
    	
        
	    getContext().getContentResolver().notifyChange(uri, null);
	    
        for ( Uri nuri : additionalUri) {        
        	getContext().getContentResolver().notifyChange(nuri, null);                            
        }
        
        return ContentUris.withAppendedId(uri, rowId);            
		
	}


	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        if (selection != null ) {
        	throw new IllegalArgumentException("Cannot set selection for update");            
        }
		
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        
        int count;
        
        String table;        
        String finalSelection;
        
        ArrayList<Uri> changedUris = new ArrayList<Uri>();
        
        changedUris.add(uri);

        Map<String,String> updateMap = new HashMap<String, String>();
        
        switch (sUriMatcher.match(uri)) {

            case CONVERSATION_ID:
            {
            	String conversationId = uri.getPathSegments().get(CONVERSATION_ID_PATH_POSITION);

                finalSelection = ConversationsColumns.CONCRETE_ID + " = " +  conversationId;

                table = Tables.CONVERSATIONS;                
                
                changedUris.add(Conversations.CONTENT_URI);
                changedUris.add(uri);
                
                updateMap.put(Conversations.SUBJECT, ConversationsColumns.SUBJECT);
                
                break;                
            }   

            case CONTACT_ID:
            {
            	String contactId = uri.getPathSegments().get(CONTACT_ID_PATH_POSITION);

                finalSelection = ContactsColumns.CONCRETE_ID + " = " +  contactId;

                table = Tables.CONTACTS;                
                
                // look for all messages sent by the contact, we will need to notify they have changed
                
                Cursor cursor = db.query(Tables.MESSAGES, new String[] { MessagesColumns._ID},
						MessagesColumns.CONCRETE_SENDER_ID + " = ? ", new String[] { contactId } , null, null, null);	

				try {
				
					while ( cursor.moveToNext()) {
						changedUris.add(ContentUris.withAppendedId(Messages.CONTENT_URI, 
								cursor.getLong(cursor.getColumnIndex( MessagesColumns._ID))));		
					}
					
				} finally{
					cursor.close();
				}

                // look for all messages sent by the contact, we will need to notify they have changed
				
                Cursor cursor2 = db.query(Tables.PARTICIPANTS, new String[] { ParticipantsColumns.CONVERSATION_ID},
						ParticipantsColumns.CONCRETE_CONTACT_ID + " = ? ", new String[] { contactId } , null, null, null);	

				try {
				
					while ( cursor2.moveToNext()) {
						changedUris.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, 
								cursor2.getLong(cursor2.getColumnIndex(ParticipantsColumns.CONVERSATION_ID))));		
					}
					
				} finally{
					cursor.close();
				}
				
                changedUris.add(Conversations.CONTENT_URI);                
                changedUris.add(Messages.CONTENT_URI);
                changedUris.add(Contacts.CONTENT_URI);
                
                updateMap.put(Contacts.COLOR, ContactsColumns.COLOR);
                updateMap.put(Contacts.ANDROID_CONTACT_ID, ContactsColumns.ANDROID_CONTACT_ID);
                updateMap.put(Contacts.EMAIL, ContactsColumns.EMAIL);
                updateMap.put(Contacts.NAME, ContactsColumns.NAME);
                
                break;
            }

            case MESSAGE_ID:
            {
            	String messageId = uri.getPathSegments().get(MESSAGE_ID_PATH_POSITION);

                finalSelection = MessagesColumns.CONCRETE_ID + " = " +  messageId;

                table = Tables.MESSAGES;                
                
                changedUris.add(Conversations.CONTENT_URI);
                
                Cursor cursor = db.query(Tables.MESSAGES, new String[] { MessagesColumns.CONVERSATION_ID},
                							MessagesColumns.CONCRETE_ID + " = ? ", new String[] { messageId } , null, null, null);	
                
                try {
                	
                	if ( !cursor.moveToFirst() ) {
                		return 0;
                	}
                	
                	long conversationId = cursor.getLong(cursor.getColumnIndex(MessagesColumns.CONVERSATION_ID));
                	
                	changedUris.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, conversationId));                    
                	
                } finally{
                	cursor.close();
                }
                                
            	changedUris.add(Conversations.CONTENT_URI);
                changedUris.add(Messages.CONTENT_URI);                                
                
                updateMap.put(Messages.STATE, MessagesColumns.STATE);
                updateMap.put(Messages.SENDER_ID, MessagesColumns.SENDER_ID);
                updateMap.put(Messages.SENT_DATE, MessagesColumns.SENT_DATE);
                updateMap.put(Messages.TEXT, MessagesColumns.TEXT);                
                
                break;
            }
            
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
		ContentValues mappedValues = new ContentValues();
		
        for ( Map.Entry<String, Object> entries : values.valueSet()) {        	
        				
        	if ( !updateMap.containsKey(entries.getKey()) ) {
        		throw new IllegalArgumentException("Cannot set initial value " + entries.getKey());
        	} else {        	
        		mappedValues.put(updateMap.get(entries.getKey()), values.getAsString(entries.getKey()));         		
        	}
        	
        }

        count = db.update(table, mappedValues, finalSelection, null);
                
        for ( Uri u : changedUris) {
        	getContext().getContentResolver().notifyChange(u, null);
        }

        return count;
		
	}
    
    
	
}
