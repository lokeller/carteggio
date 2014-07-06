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


import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import ch.carteggio.provider.AuthenticatorService;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.R;

public class MainActivity extends Activity {
		
	private static final int LOADER_CONVERSATIONS = 0;
	
	private ListView mConversationsList;
	
	private ConversationsAdapter mAdapter;
	private Button mStartFirstConversation;
	
	private ConversationsCountChange mCountChangeObserver = new ConversationsCountChange();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		
		if ( new CarteggioProviderHelper(this).getDefaultAccount() == null) {
			
			AccountManager.get(this).addAccount(AuthenticatorService.ACCOUNT_TYPE, null, null,
					null, this, new AccountManagerCallback<Bundle>() {
						
						@Override
						public void run(AccountManagerFuture<Bundle> future) {
							startActivity(new Intent(getApplicationContext(), MainActivity.class));
						}
					}, null);
		
			finish();
			return;
		}
		
		setContentView(R.layout.activity_main);
		
		AccountManager manager = AccountManager.get(this);
		
		Account accounts[] = manager.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
		
		if (accounts.length == 0) {
			manager.addAccount(AuthenticatorService.ACCOUNT_TYPE, null, null, null, this, null, null);
		}		
		
		mConversationsList = (ListView) findViewById(R.id.conversations); 

		mAdapter = new ConversationsAdapter(this, null);
				
		mConversationsList.setAdapter(mAdapter);
		
		mConversationsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
											
				Intent intent = new Intent(Intent.ACTION_VIEW);
				
				intent.setData(ContentUris.withAppendedId(Conversations.CONTENT_URI, id));
								
				startActivity(intent);
				
			}
			
			
		});
		

		mConversationsList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		mConversationsList.setMultiChoiceModeListener(mMultichoiceModeListener);
		
		getLoaderManager().initLoader(LOADER_CONVERSATIONS, null, mConversationsLoader);		
	
		mStartFirstConversation = (Button) findViewById(R.id.start_first_conversation);
		
		mStartFirstConversation.setVisibility(View.GONE);
		
		mStartFirstConversation.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				startActivity(new Intent(MainActivity.this, ContactsActivity.class));
			}
		});
		
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		CarteggioProviderHelper carteggioProviderHelper = new CarteggioProviderHelper(this);
		
		carteggioProviderHelper.forceUpdate();
			
	}
	
	

	@Override
	protected void onStop() {
		
		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		if ( item.getItemId() == R.id.action_new_conversation) {
			
			startActivity(new Intent(this, ContactsActivity.class));
			
			return true;
			
		} else if ( item.getItemId() == R.id.action_about) {
				
				startActivity(new Intent(this, AboutActivity.class));
				
				return true;
		 
			
		} else if ( item.getItemId() == R.id.action_settings) {
			
			
			AccountManager accountManager = AccountManager.get(this);
			
			Account[] accountsByType = accountManager.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
			
			if (accountsByType.length > 0) { 
			
				Intent intent = new Intent(this, EditAccountActivity.class);
				
				intent.putExtra("account", accountsByType[0]);
				
				startActivity(intent);
				
			}
			
		}
		
		return super.onOptionsItemSelected(item);		
		
	}

	private MultiChoiceModeListener mMultichoiceModeListener = new MultiChoiceModeListener() {
		
		@Override
		public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {			
			return false;
		}
		
		@Override
		public void onDestroyActionMode(android.view.ActionMode mode) {
			
		}
		
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
		    inflater.inflate(R.menu.conversation_context, menu);
		    return true;	
		}
		
		@Override
		public boolean onActionItemClicked(android.view.ActionMode mode,
				MenuItem item) {
			switch (item.getItemId()) {
            	case R.id.menu_delete:	                
            		            		
            		SparseBooleanArray checkedItemPositions = mConversationsList.getCheckedItemPositions();
            		
            		int itemCount = mConversationsList.getCount();
            		 
                    for(int i = itemCount-1; i >= 0; i--){
                        if(checkedItemPositions.get(i)){
                            getContentResolver().delete(
                            		ContentUris.withAppendedId(Conversations.CONTENT_URI, 
                            				mConversationsList.getItemIdAtPosition(i)), 
                            		null, null);
                        }
                    }
                    
	                mode.finish(); // Action picked, so close the CAB
	                return true;
	            default:
	                return false;
	        }
		}
		
		@Override
		public void onItemCheckedStateChanged(android.view.ActionMode mode,
				int position, long id, boolean checked) {
			
		}
	}; 	
	
	private LoaderCallbacks<Cursor> mConversationsLoader = new LoaderCallbacks<Cursor>() {
		
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {		
								    
			return new CursorLoader(MainActivity.this,
									Conversations.CONTENT_URI, 
									ConversationsAdapter.PROJECTION, null , null, null);
		}
	
		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
	
			mAdapter.swapCursor(data);
	
			mCountChangeObserver.setCursor(data);
		}
	
		@Override
		public void onLoaderReset(Loader<Cursor> loader) {
			mAdapter.swapCursor(null);		
		}
		
	};

	private class ConversationsCountChange extends DataSetObserver {

		private Cursor mCursor;

		public void setCursor(Cursor cursor) {
			
			if ( mCursor != null) {
				mCursor.unregisterDataSetObserver(this);
			}
			
			mCursor = cursor;
			
			mCursor.registerDataSetObserver(this);
			
			onChanged();
		}
		
		@Override
		public void onChanged() {
			super.onChanged();
		
			if ( mCursor == null || mCursor .getCount() > 0 ) {
				mStartFirstConversation.setVisibility(View.GONE);
				mConversationsList.setVisibility(View.VISIBLE);

			} else {
				mStartFirstConversation.setVisibility(View.VISIBLE);
				mConversationsList.setVisibility(View.GONE);
			}

		}
		
	}
	
	
}
