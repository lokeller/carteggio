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

import ch.carteggio.provider.CarteggioContract.Contacts;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Messages;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

public class CarteggioDatabaseHelper extends SQLiteOpenHelper {

	private static final String DATABASE_NAME = "messages.db";
	private static final int DATABASE_VERSION = 1;
	
	public interface Tables {
		public static final String MESSAGES = "messages";
		public static final String CONVERSATIONS = "conversations";
		public static final String CONTACTS = "contacts";
		public static final String PARTICIPANTS = "participants";
	}
	
	public interface Views {
		public static final String MESSAGES = "view_messages";
		public static final String CONVERSATIONS = "view_conversations";
		public static final String CONTACTS = "view_contacts";
		public static final String PARTICIPANTS = "view_participants";
	}
	
	public interface MessagesColumns extends BaseColumns {
		
        String CONCRETE_ID = Tables.MESSAGES + "." + BaseColumns._ID;

        String CONVERSATION_ID = Messages.CONVERSATION_ID;
        String GLOBAL_ID = Messages.GLOBAL_ID;
        String STATE = Messages.STATE;
        String SENDER_ID = Messages.SENDER_ID;
        String SENT_DATE = Messages.SENT_DATE;
        String TEXT = Messages.TEXT;
        
        String CONCRETE_CONVERSATION_ID = Tables.MESSAGES + "." + CONVERSATION_ID;
        String CONCRETE_STATE = Tables.MESSAGES + "." + STATE;
        String CONCRETE_SENDER_ID = Tables.MESSAGES + "." + SENDER_ID;
        String CONCRETE_SENT_DATE = Tables.MESSAGES + "." + SENT_DATE;
        String CONCRETE_TEXT = Tables.MESSAGES + "." + TEXT;
        String CONCRETE_GLOBAL_ID = Tables.MESSAGES + "." + GLOBAL_ID;
        
    }
	
	public interface ContactsColumns extends BaseColumns {
		
        String CONCRETE_ID = Tables.CONTACTS + "." + BaseColumns._ID;

        String COLOR = Contacts.COLOR;
        String ANDROID_CONTACT_ID = Contacts.ANDROID_CONTACT_ID;
        String EMAIL = Contacts.EMAIL;
        String NAME = Contacts.NAME;
        
        String CONCRETE_COLOR = Tables.CONTACTS + "." + COLOR;
        String CONCRETE_ANDROID_CONTACT_ID = Tables.CONTACTS + "." + ANDROID_CONTACT_ID;
        String CONCRETE_EMAIL = Tables.CONTACTS + "." + EMAIL;
        String CONCRETE_NAME = Tables.CONTACTS + "." + NAME;
        
        
    }
	
	public interface ConversationsColumns extends BaseColumns {
		
        String CONCRETE_ID = Tables.CONVERSATIONS + "." + BaseColumns._ID;
       
        String SUBJECT = Conversations.SUBJECT;        
        
        String LAST_MESSAGE_ID = Conversations.LAST_MESSAGE_ID;				
		String UNREAD_MESSAGES = Conversations.UNREAD_MESSAGES_COUNT;
		String PARTICIPANTS_COUNT = Conversations.PARTICIPANTS_COUNT;
		String PARTICIPANTS_NAMES = Conversations.PARTICIPANTS_NAMES;
                       
        String CONCRETE_SUBJECT = Tables.CONVERSATIONS + "." + SUBJECT;        

		String CONCRETE_UNREAD_MESSAGES = Tables.CONVERSATIONS + "." + UNREAD_MESSAGES;
		String CONCRETE_PARTICIPANTS_COUNT = Tables.CONVERSATIONS + "." + PARTICIPANTS_COUNT;
		String CONCRETE_LAST_MESSAGE_ID = Tables.CONVERSATIONS + "." + LAST_MESSAGE_ID;

		String CONCRETE_PARTICIPANTS_NAMES = Tables.CONVERSATIONS + "." + PARTICIPANTS_NAMES;				
        
    }
	
	public interface ParticipantsColumns extends BaseColumns {
		
        String CONCRETE_ID = Tables.PARTICIPANTS + "." + BaseColumns._ID;

        String CONVERSATION_ID = "conversation_id";       
        String CONTACT_ID = "contact_id";
        
        String CONCRETE_CONVERSATION_ID = Tables.PARTICIPANTS + "." + CONVERSATION_ID;       
        String CONCRETE_CONTACT_ID = Tables.PARTICIPANTS + "." + CONTACT_ID;
           
    }
	
	public CarteggioDatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);		
	}
	  	
	@Override
	public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE " + Tables.CONTACTS + " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +                                
                ContactsColumns.COLOR + " INTEGER," +
                ContactsColumns.ANDROID_CONTACT_ID + " INTEGER," +
                ContactsColumns.EMAIL + " TEXT NOT NULL UNIQUE," +
                ContactsColumns.NAME + " TEXT" +
        ");");

        db.execSQL("CREATE TABLE " + Tables.MESSAGES + " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +                                
                MessagesColumns.GLOBAL_ID + " TEXT UNIQUE NOT NULL," +
                MessagesColumns.CONVERSATION_ID + " INTEGER NOT NULL REFERENCES " + Tables.CONVERSATIONS + "(" + BaseColumns._ID + ") ON DELETE CASCADE," +
                MessagesColumns.STATE + " INTEGER," +
                MessagesColumns.SENDER_ID + " INTEGER NOT NULL REFERENCES " + Tables.CONTACTS + "(" + BaseColumns._ID + ")," +
                MessagesColumns.SENT_DATE + " INTEGER NOT NULL," +
                MessagesColumns.TEXT + " INTEGER" +
                
        ");");

        db.execSQL("CREATE TABLE " + Tables.CONVERSATIONS + " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +                                
                ConversationsColumns.SUBJECT + " TEXT, " +             
                ConversationsColumns.LAST_MESSAGE_ID + " INTEGER," +
                ConversationsColumns.PARTICIPANTS_COUNT + " INTEGER DEFAULT 0," +
                ConversationsColumns.PARTICIPANTS_NAMES + " TEXT," +
                ConversationsColumns.UNREAD_MESSAGES + " INTEGER DEFAULT 0" +
        ");");
        
        db.execSQL("CREATE TABLE " + Tables.PARTICIPANTS + " (" +
                BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +                                
                ParticipantsColumns.CONTACT_ID + " INTEGER REFERENCES " + Tables.CONTACTS + "(" + BaseColumns._ID + ")," +
                ParticipantsColumns.CONVERSATION_ID + " INTEGER REFERENCES " + Tables.CONVERSATIONS + "(" + BaseColumns._ID + ") ON DELETE CASCADE" +                
        ");");
        

        db.execSQL("CREATE VIEW " + Views.CONTACTS + " AS " + 
        				"SELECT " + ContactsColumns.CONCRETE_ID + " AS " + Contacts._ID + ", " +
        				 			ContactsColumns.CONCRETE_COLOR + " AS " + Contacts.COLOR + ", " +
        				 			ContactsColumns.CONCRETE_EMAIL + " AS " + Contacts.EMAIL + ", " +
        				 			ContactsColumns.CONCRETE_NAME + " AS " + Contacts.NAME + ", " +
        				 			ContactsColumns.CONCRETE_ANDROID_CONTACT_ID + " AS " + Contacts.ANDROID_CONTACT_ID + " " +
        				 	" FROM " + Tables.CONTACTS + ";");
        
        db.execSQL("CREATE VIEW " + Views.MESSAGES + " AS " + 
				"SELECT " + ContactsColumns.CONCRETE_ID + " AS " + Messages.SENDER_ID + ", " +	 						
				 			ContactsColumns.CONCRETE_COLOR + " AS " + Messages.SENDER_COLOR + ", " +
				 			ContactsColumns.CONCRETE_EMAIL + " AS " + Messages.SENDER_EMAIL + ", " +
				 			ContactsColumns.CONCRETE_NAME + " AS " + Messages.SENDER_NAME + ", " +
				 			MessagesColumns.CONCRETE_CONVERSATION_ID + " AS " + Messages.CONVERSATION_ID + ", " +
				 			MessagesColumns.CONCRETE_ID + " AS " + Messages._ID + ", " +
				 			MessagesColumns.CONCRETE_STATE + " AS " + Messages.STATE + ", " +
				 			MessagesColumns.CONCRETE_GLOBAL_ID + " AS " + Messages.GLOBAL_ID + ", " +
				 			MessagesColumns.CONCRETE_SENT_DATE + " AS " + Messages.SENT_DATE + ", " +
				 			MessagesColumns.CONCRETE_TEXT + " AS " + Messages.TEXT + " " +				 			
				 		" FROM " + Tables.MESSAGES + " INNER JOIN " + Tables.CONTACTS + " ON " + MessagesColumns.CONCRETE_SENDER_ID + " = " + ContactsColumns.CONCRETE_ID  + ";"); 
		
        db.execSQL("CREATE VIEW " + Views.CONVERSATIONS + " AS " + 
				"SELECT " + ContactsColumns.CONCRETE_EMAIL + " AS " + Conversations.LAST_MESSAGE_SENDER_EMAIL + ", " +
				 			ContactsColumns.CONCRETE_NAME + " AS " + Conversations.LAST_MESSAGE_SENDER_NAME + ", " +				 			
				 			MessagesColumns.CONCRETE_ID + " AS " + Conversations.LAST_MESSAGE_ID + ", " +
				 			MessagesColumns.CONCRETE_STATE + " AS " + Conversations.LAST_MESSAGE_STATE + ", " +
				 			MessagesColumns.CONCRETE_SENT_DATE + " AS " + Conversations.LAST_MESSAGE_SENT_DATE + ", " +
				 			MessagesColumns.CONCRETE_TEXT + " AS " + Conversations.LAST_MESSAGE_TEXT + ", " +
				 			ConversationsColumns.CONCRETE_ID + " AS " + Conversations._ID + ", " +
				 			ConversationsColumns.CONCRETE_SUBJECT + " AS " + Conversations.SUBJECT + ", " +
				 			ConversationsColumns.CONCRETE_PARTICIPANTS_COUNT + " AS " + Conversations.PARTICIPANTS_COUNT + ", " +
				 			ConversationsColumns.CONCRETE_PARTICIPANTS_NAMES + " AS " + Conversations.PARTICIPANTS_NAMES + ", " +
				 			ConversationsColumns.CONCRETE_UNREAD_MESSAGES + " AS " + Conversations.UNREAD_MESSAGES_COUNT + " " +
				 			
				 			
				 		" FROM " + Tables.CONVERSATIONS + 
				 				" LEFT JOIN " + Tables.MESSAGES + 
				 					" ON " + MessagesColumns.CONCRETE_ID + " = " + ConversationsColumns.CONCRETE_LAST_MESSAGE_ID +  
				 				" LEFT JOIN " + Tables.CONTACTS + 
				 					" ON " + MessagesColumns.CONCRETE_SENDER_ID + " = " + ContactsColumns.CONCRETE_ID  + ";");
        
        db.execSQL("CREATE VIEW " + Views.PARTICIPANTS + " AS " + 
				"SELECT " + ContactsColumns.CONCRETE_EMAIL + " AS " + Conversations.Participants.EMAIL + ", " +
							ContactsColumns.CONCRETE_NAME + " AS " + Conversations.Participants.NAME+ ", " +
							ContactsColumns.CONCRETE_ANDROID_CONTACT_ID + " AS " + Conversations.Participants.ANDROID_CONTACT_ID + ", " +
							ContactsColumns.CONCRETE_COLOR + " AS " + Conversations.Participants.COLOR + ", " +
							ContactsColumns.CONCRETE_ID + " AS " + Conversations.Participants._ID+ ", " +
							ParticipantsColumns.CONCRETE_CONVERSATION_ID + " AS " + Conversations.Participants.CONVERSATION_ID + " " +
				 			
				 		" FROM " + Tables.PARTICIPANTS + 
				 				" INNER JOIN " + Tables.CONTACTS + 
				 					" ON " + ParticipantsColumns.CONCRETE_CONTACT_ID + " = " + ContactsColumns.CONCRETE_ID + ";");
        
        String selectUnread = " ( SELECT COUNT(*) FROM " + Tables.MESSAGES + 
				" WHERE " + MessagesColumns.CONVERSATION_ID + " = NEW." + MessagesColumns.CONVERSATION_ID + 
				" AND " + MessagesColumns.CONCRETE_STATE + " = " + Messages.STATE_WAITING_TO_BE_READ + ")"; 

        
        String whenLastMessage = "NEW." + MessagesColumns.SENT_DATE + 
		   		" = ( SELECT MAX(" + Messages.SENT_DATE + ") " + 
   				" FROM " + Tables.MESSAGES + 
   				" WHERE " + MessagesColumns.CONVERSATION_ID + " = " + 
   					"NEW." + MessagesColumns.CONVERSATION_ID + ")";   

        
        db.execSQL("CREATE TRIGGER on_insert_new_message AFTER INSERT ON " + Tables.MESSAGES + " " +
        		   "WHEN " + whenLastMessage + 
        		   "BEGIN" +
        		   "   UPDATE " + Tables.CONVERSATIONS + " SET " +
        		   			ConversationsColumns.LAST_MESSAGE_ID + " = " + "NEW." + MessagesColumns._ID+ "," +
        		   			ConversationsColumns.UNREAD_MESSAGES + " = " + selectUnread + 
        		   			" WHERE " + ConversationsColumns._ID + " = " + "NEW." + MessagesColumns.CONVERSATION_ID + "; " +
        		   "END");
        
        db.execSQL("CREATE TRIGGER on_update_message AFTER UPDATE ON " + Tables.MESSAGES + " " + 
     		   "BEGIN" +
     		   "   UPDATE " + Tables.CONVERSATIONS + " SET " +     		   			
     		   			ConversationsColumns.UNREAD_MESSAGES + " = " + selectUnread + 
     		   			" WHERE " + ConversationsColumns._ID + " = " + "NEW." + MessagesColumns.CONVERSATION_ID + "; " +
     		   "END");        
        
        String selectParticipantsCount = " ( SELECT COUNT(*) FROM " + Tables.PARTICIPANTS + 
        									" WHERE " + ParticipantsColumns.CONVERSATION_ID + " = NEW." + ParticipantsColumns.CONVERSATION_ID + ")";         

        String selectParticipantsNames = " ( SELECT GROUP_CONCAT(" + ContactsColumns.CONCRETE_NAME + ", ', ') FROM " + Tables.PARTICIPANTS +
        		" INNER JOIN " + Tables.CONTACTS + " ON " + ContactsColumns.CONCRETE_ID + " = " + ParticipantsColumns.CONCRETE_CONTACT_ID + " " + 
				" WHERE " + ParticipantsColumns.CONVERSATION_ID + " = NEW." + ParticipantsColumns.CONVERSATION_ID + ")";         

        
        db.execSQL("CREATE TRIGGER on_insert_participant AFTER INSERT ON " + Tables.PARTICIPANTS +  " " +
     		   "BEGIN" +
     		   "   UPDATE " + Tables.CONVERSATIONS + " SET " +
     		   			ConversationsColumns._ID + " = " + "NEW." + ParticipantsColumns.CONVERSATION_ID + "," +
     		   			ConversationsColumns.PARTICIPANTS_COUNT + " = " + selectParticipantsCount + "," +
     		   			ConversationsColumns.PARTICIPANTS_NAMES + " = " + selectParticipantsNames + 
     		   			" WHERE " + ConversationsColumns._ID + " = " + "NEW." + ParticipantsColumns.CONVERSATION_ID + "; " +     		   			
     		   "END");        
        
        db.execSQL("CREATE TRIGGER on_delete_participant AFTER DELETE ON " + Tables.PARTICIPANTS +  " " +
      		   "BEGIN" +
      		   "   UPDATE " + Tables.CONVERSATIONS + " SET " +
      		   			ConversationsColumns._ID + " = " + "NEW." + ParticipantsColumns.CONVERSATION_ID + "," +
     		   			ConversationsColumns.PARTICIPANTS_COUNT + " = " + selectParticipantsCount + "," +
     		   			ConversationsColumns.PARTICIPANTS_NAMES + " = " + selectParticipantsNames + 
     		   			" WHERE " + ConversationsColumns._ID + " = " + "NEW." + ParticipantsColumns.CONVERSATION_ID + "; " +     		   			
      		   "END");        
         
        
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
	}
	
}
