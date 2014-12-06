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

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Messages;
import ch.carteggio.R;

public class ConversationActivity extends Activity {
	
	protected static final String CONVERSATION_ID_EXTRA = "ch.carteggio.ConversationActivity.MESSAGE_ID_EXTRA";

	private ListView mMessagesList;
	private MessagesAdapter mAdapter;
	
	private Uri mConversation;
		
	private static final int LOADER_CONVERSATION = 0;
	private static final int LOADER_MESSAGES = 1;
	
	private ConversationIconLoader mIconLoader;
	
	private LoaderCallbacks<Cursor> mConversationLoader = new LoaderCallbacks<Cursor>() {
		
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {		
						
			return new CursorLoader(ConversationActivity.this, mConversation, 
									new String[] { Conversations.SUBJECT, Conversations.PARTICIPANTS_COUNT, 
													Conversations.PARTICIPANTS_NAMES } , null, null, null);
		}
	
		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
	
			if ( !data.moveToFirst() ) {
				finish();
				return;
			}
			
			int participantsCount = data.getInt(data.getColumnIndex(Conversations.PARTICIPANTS_COUNT));
			
			if ( participantsCount > 1) {
			
				String subject = data.getString(data.getColumnIndex(Conversations.SUBJECT));				
				
			    getActionBar().setTitle(subject);

			    String participantsString = data.getString(data.getColumnIndex(Conversations.PARTICIPANTS_NAMES));
			    
			    if ( participantsString.length() > 30 ) {
			    	participantsString = participantsString.substring(0, 25) + "...";
			    }
			    
			    getActionBar().setSubtitle(participantsString);
			    
			} else { 		

				String name = data.getString(data.getColumnIndex(Conversations.PARTICIPANTS_NAMES)); 				
				
				getActionBar().setTitle(name);
			}
		    					
			mAdapter = new MessagesAdapter(ConversationActivity.this, null, participantsCount > 1);
			
			mMessagesList.setAdapter(mAdapter);
			
			getLoaderManager().initLoader(LOADER_MESSAGES, null, mMessagesLoader);
			
			
		}
	
		@Override
		public void onLoaderReset(Loader<Cursor> loader) {

		
		}
		
	};
	
	private LoaderCallbacks<Cursor> mMessagesLoader = new LoaderCallbacks<Cursor>() {
		
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {		
						
			return new CursorLoader(ConversationActivity.this,
									Messages.CONTENT_URI, 
									MessagesAdapter.PROJECTION , Messages.CONVERSATION_ID + " = ?" , 
									new String[] { Long.toString(ContentUris.parseId(mConversation)) }, null);
		}
	
		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
	
			mAdapter.swapCursor(data);
						
			mMessagesList.setSelection(mMessagesList.getCount() - 1);
			
		}
	
		@Override
		public void onLoaderReset(Loader<Cursor> loader) {
			mAdapter.swapCursor(null);		
		}
		
	};
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_conversation);
				
		mMessagesList = (ListView) findViewById(R.id.messages); 
		
		mConversation = getIntent().getData();
		
		getActionBar().setDisplayHomeAsUpEnabled(true);
		
		findViewById(R.id.send).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
			
				TextView message = (TextView) findViewById(R.id.outgoingMessage);
								
				CarteggioProviderHelper helper = new CarteggioProviderHelper(ConversationActivity.this);				
							
				CarteggioAccount account = helper.getDefaultAccount();				
								
				helper.createOutgoingMessage(account, mConversation, message.getText().toString());
											
				helper.forceUpdate();
				
				message.setText("");
			}
		});
		
		mMessagesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				
				if ( mAdapter == null ) return;
				
				mAdapter.toggleExpanded(id);
				
			}
			

		});
		
		getLoaderManager().initLoader(LOADER_CONVERSATION, null, mConversationLoader);
		
		mIconLoader = new ConversationIconLoader(this, Color.RED);
		
		mIconLoader.loadConversationPicture( ContentUris.parseId(mConversation) , getActionBar());
		
	}
		
	@Override
	protected void onStart() {
		super.onStart();
		
		new CarteggioProviderHelper(this).forceUpdate();
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.conversation, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	
		if ( item.getItemId() == R.id.action_add_attachment) {
			Toast.makeText(getApplicationContext(), "Not implemented", Toast.LENGTH_SHORT).show();
		}
		
		return super.onOptionsItemSelected(item);
	}
	
}
