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
import java.util.HashSet;

import android.content.Context;
import android.database.Cursor;
import android.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ch.carteggio.net.EmailUtils;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.CarteggioContract.Messages;
import ch.carteggio.R;


public class MessagesAdapter extends CursorAdapter {

	private static final int TYPE_OUTGOING = 0;
	private static final int TYPE_INCOMING = 1;
	
	private HashSet<Long> expandedMessages = new HashSet<Long>();
	
	private boolean isGroupConversation;
	
	public static String [] PROJECTION = { Messages._ID,
											Messages.STATE,
											Messages.SENT_DATE,
											Messages.TEXT,
											Messages.SENDER_NAME } ;
	private CarteggioProviderHelper mHelper;
		
	public MessagesAdapter(Context context, Cursor c, boolean isGroupConversation) {
		super(context, c, FLAG_REGISTER_CONTENT_OBSERVER );				
		this.isGroupConversation = isGroupConversation;
		mHelper = new CarteggioProviderHelper(context);
	}

	@Override
	public int getItemViewType(int position) {

		Cursor c = (Cursor) getItem(position);
		
		return getItemTypeForCursor(c);

	}

	private int getItemTypeForCursor(Cursor c) {
		if ( c == null) return 0;
		
		int state = c.getInt(c.getColumnIndex(Messages.STATE));
						
		if ( Messages.isOutgoing(state)) {
			return TYPE_OUTGOING;
		} else {
			return TYPE_INCOMING;
		}
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}


	@Override
	public void bindView(View view, Context arg1, Cursor c) {
		
		int type = getItemTypeForCursor(c);
		
		TextView messageDetails = (TextView) view.findViewById(R.id.message_details);
		
		Date date = new Date(getCursor().getLong(getCursor().getColumnIndex(Messages.SENT_DATE)));
				
		String text = getCursor().getString(getCursor().getColumnIndex(Messages.TEXT));

		int state = getCursor().getInt(getCursor().getColumnIndex(Messages.STATE));
		
		long messageId = getCursor().getInt(getCursor().getColumnIndex(Messages._ID));
		
		boolean isSent = Messages.isSent(state);
		boolean isReceived = Messages.isDelivered(state);
				
		if ( type == TYPE_INCOMING ) {		
			messageDetails.setText(NiceDateFormat.niceDate(date));			
		} else {
			messageDetails.setText(NiceDateFormat.niceDate(date) + ( isSent ? "✓" : "" ) + ( isReceived ? "✓" : "" ));
		}
		
		TextView messageText = (TextView) view.findViewById(R.id.message);
				
		if (EmailUtils.containsQuote(text)) {
		
			if ( expandedMessages.contains(messageId)) {
				messageText.setText(text);
			} else {
				messageText.setText(EmailUtils.stripQuote(text));				
				messageDetails.setText("...  " + messageDetails.getText());				
			}
			
		} else {
			messageText.setText(text);
		}
		
		if ( type == TYPE_INCOMING ) {
		
			TextView sender = (TextView) view.findViewById(R.id.message_sender);			
			
			if ( isGroupConversation && type == TYPE_INCOMING) {				
		
				String senderName = getCursor().getString(getCursor().getColumnIndex(Messages.SENDER_NAME));
				int senderColor = getCursor().getInt(getCursor().getColumnIndex(Messages.SENDER_COLOR));				
				
				sender.setText(senderName);
				sender.setTextColor(senderColor);
				
				sender.setVisibility(View.VISIBLE);
				
			} else {
				
				sender.setVisibility(View.GONE);
				
			}
			
			// mark the message as read if necessary
			if ( state == Messages.STATE_WAITING_TO_BE_READ ) {
				mHelper.setMessageState(messageId, Messages.STATE_READ_LOCALLY_PENDING_CONFIRMATION_TO_REMOTE);
			}
			
		}
	}


	@Override
	public View newView(Context context, Cursor cursor, ViewGroup arg2) {

		int type = getItemTypeForCursor(cursor);		
	
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		if ( type == TYPE_INCOMING ) {
			return inflater.inflate(R.layout.list_item_message_incoming, null);
		} else {
			return inflater.inflate(R.layout.list_item_message_outgoing, null);
		}					
		
	}

	public void toggleExpanded(long id) {
		
		if (expandedMessages.contains(id)) {
			expandedMessages.remove(id);	
		} else {
			expandedMessages.add(id);
		}
		
		notifyDataSetChanged();
	}

	
}
