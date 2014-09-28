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

import android.net.Uri;
import android.provider.BaseColumns;


public final class CarteggioContract {
	
	public static final String AUTHORITY = "ch.carteggio";

	private static final String SCHEME = "content://";

	public static final Uri AUTHORITY_URI = Uri.parse(SCHEME + AUTHORITY); 
	
	public static final class Messages implements BaseColumns {
	
		public static final String SENT_DATE = "sent_date";		
		
		public static final String SENDER_ID = "sender_id";
		
		public static final String SENDER_NAME = "sender_name";
		
		public static final String SENDER_EMAIL = "sender_email";
		
		public static final String SENDER_COLOR = "sender_color";
		
		public static final String GLOBAL_ID = "global_id";
		
		public static final String TEXT = "text";
		
		public static final String STATE = "state";		
	
		public static final String CONVERSATION_ID = "conversation_id";		
		
		public static String CONTENT_SUBTYPE = "vnd.ch.carteggio.message";

		public static final int STATE_READ_LOCALLY_PENDING_CONFIRMATION_TO_REMOTE = 0;
		public static final int STATE_DELIVERED_TO_SERVER = 1;
		public static final int STATE_RECEIVED_BY_DESTINATION = 2;
		public static final int STATE_WAITING_TO_BE_SENT = 3;
		public static final int STATE_WAITING_TO_BE_READ = 4;
		public static final int STATE_READ_LOCALLY_CONFIRMD_TO_REMOTE = 5;
		
		public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "messages");
	
		public static boolean isSent(int state) {			
			return state == STATE_DELIVERED_TO_SERVER || state == STATE_RECEIVED_BY_DESTINATION;						
		}
		
		public static boolean isDelivered(int state) {			
			return state == STATE_RECEIVED_BY_DESTINATION;						
		}

		public static boolean isOutgoing(int state) {
			return state == STATE_DELIVERED_TO_SERVER ||
					state == STATE_WAITING_TO_BE_SENT || 
					state == STATE_RECEIVED_BY_DESTINATION;
		}
		
	
	}
	
	public static final class Conversations implements BaseColumns {		
		
		public static final String SUBJECT = "subject";
		
		public static final String LAST_MESSAGE_ID = "last_message_id";
		
		public static final String PARTICIPANTS_NAMES = "participants_names";
		
		public static final String LAST_MESSAGE_SENDER_NAME = "last_message_sender_name";
		
		public static final String LAST_MESSAGE_SENDER_EMAIL = "last_message_sender_email";		
		
		public static final String LAST_MESSAGE_SENT_DATE = "last_sent_date";				
		
		public static final String LAST_MESSAGE_TEXT = "last_message_text";
		
		public static final String LAST_MESSAGE_STATE = "last_message_state";		
		
		public static final String UNREAD_MESSAGES_COUNT = "unread_messages_count";
		
		public static final String PARTICIPANTS_COUNT = "participants_count";

		public static String CONTENT_SUBTYPE = "vnd.ch.carteggio.conversation";
		
		public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "conversations");		


		public static final class Participants implements ContactsColumns {
			
			public static final String TABLE_NAME = "participants";			
			
			public static final String CONTENT_DIRECTORY = "participants";
			
			public static final String CONVERSATION_ID = "conversation_id";

			public static final String CONTACT_ID = "contact_id";
		
			public static String EMAIL = "email";
			public static String NAME = "name";
			
			public static String CONTENT_SUBTYPE = "vnd.ch.carteggio.participant";
			
		}
		
	}

	public static interface ContactsColumns extends BaseColumns {
		
		public static String ANDROID_CONTACT_ID = "contact_id";
		public static String EMAIL = "email";
		public static String NAME = "name";
		public static String COLOR = "color";		

		public static final long NO_ANDROID_CONTACT = -1;
	}

	public static final class Contacts implements ContactsColumns {
	
		public static final String TABLE_NAME = "contacts";			

		public static String CONTENT_SUBTYPE = "vnd.ch.carteggio.contact";
		
		public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "contacts");		
		
		
	}
	
}
