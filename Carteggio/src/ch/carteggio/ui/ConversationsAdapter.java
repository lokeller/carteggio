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
package ch.carteggio.ui;

import java.util.Date;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.webkit.WebView.FindListener;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Messages;
import ch.carteggio.R;


public class ConversationsAdapter extends CursorAdapter {
	

	public static String [] PROJECTION = { Conversations._ID,
									        
									        Conversations.SUBJECT,
									        
									        Conversations.PARTICIPANTS_COUNT,
									        Conversations.PARTICIPANTS_NAMES,
									        
									        Conversations.LAST_MESSAGE_ID,
									        Conversations.LAST_MESSAGE_SENT_DATE,
									        Conversations.LAST_MESSAGE_TEXT,
									        Conversations.LAST_MESSAGE_SENDER_EMAIL,
									        Conversations.LAST_MESSAGE_SENDER_NAME,
									        Conversations.LAST_MESSAGE_STATE,

									        Conversations.UNREAD_MESSAGES_COUNT} ;

	private ConversationIconLoader mIconLoader;
	
	public ConversationsAdapter(Context context, Cursor c) {		
		super(context, c, FLAG_REGISTER_CONTENT_OBSERVER );
		mIconLoader = new ConversationIconLoader(context, Color.RED);				
	}
		
	@Override
	public void bindView(View viewParent, Context arg1, Cursor cursor) {		
		
		TextView subject = (TextView) viewParent.findViewById(R.id.subject);
		TextView lastMessageTime = (TextView) viewParent.findViewById(R.id.last_message_time);
		TextView lastMessage = (TextView) viewParent.findViewById(R.id.last_message);
		TextView unreadCount = (TextView) viewParent.findViewById(R.id.unread_message_count);
		
		ImageView chatImage = (ImageView) viewParent.findViewById(R.id.chat_image);
		
		int participantsCount = cursor.getInt(cursor.getColumnIndex(Conversations.PARTICIPANTS_COUNT));
		
		if ( participantsCount == 1) {
			subject.setText(cursor.getString(cursor.getColumnIndex(Conversations.PARTICIPANTS_NAMES)));
		} else {
			subject.setText(cursor.getString(cursor.getColumnIndex(Conversations.SUBJECT)));
		}
	
				
		if ( cursor.isNull(cursor.getColumnIndex(Conversations.LAST_MESSAGE_ID)) ) {
			lastMessageTime.setVisibility(View.GONE);
		} else {
			lastMessageTime.setVisibility(View.VISIBLE);
			lastMessageTime.setText(NiceDateFormat.niceDate(new Date(cursor.getInt(cursor.getColumnIndex(Conversations.LAST_MESSAGE_SENT_DATE)))));
		}		
		
		if ( cursor.isNull(cursor.getColumnIndex(Conversations.LAST_MESSAGE_ID)) ) {
			lastMessage.setText("No messages");
		} else {
			
			String messageSnippet = cursor.getString(cursor.getColumnIndex(Conversations.LAST_MESSAGE_TEXT)); 
						
			if ( participantsCount > 1) {
				messageSnippet = cursor.getString(cursor.getColumnIndex(Conversations.LAST_MESSAGE_SENDER_NAME)) + ": " + messageSnippet;
			}
			
			String sender = cursor.getString(cursor.getColumnIndex(Conversations.LAST_MESSAGE_SENDER_NAME));
								
			int state = cursor.getInt(cursor.getColumnIndex(Conversations.LAST_MESSAGE_STATE));
				
			if ( Messages.isOutgoing(state)) {
				
				boolean sent = Messages.isSent(state);
				boolean read = Messages.isDelivered(state);
				
				messageSnippet = ( sent ? "✓" : "" ) + ( read ? "✓" : "" ) + messageSnippet;
			} else {
				messageSnippet = "► "  + sender + ": " + messageSnippet;
			}
			
			if ( messageSnippet.length() < 23) {		
				lastMessage.setText(messageSnippet);
			} else {
				lastMessage.setText(messageSnippet.substring(0, 20) + "...");
			}
			
		}
					
		int count = cursor.getInt(cursor.getColumnIndex(Conversations.UNREAD_MESSAGES_COUNT));
		
		if ( count == 0 ) {
			unreadCount.setVisibility(View.GONE);
		} else {
			unreadCount.setVisibility(View.VISIBLE);
			unreadCount.setText(Long.toString(count));
		}					
		
		mIconLoader.loadConversationPicture(cursor.getLong(cursor.getColumnIndex(Conversations._ID)), chatImage);
		
	}


	@Override
	public View newView(Context context, Cursor cursor, ViewGroup arg2) {
	
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		return inflater.inflate(R.layout.list_item_conversation, null);
		
	}

	
}
