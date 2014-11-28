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


import org.acra.ACRA;
import org.apache.james.mime4j.field.address.AddressBuilder;
import org.apache.james.mime4j.field.address.ParseException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Data;
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
import android.widget.Toast;
import ch.carteggio.R;
import ch.carteggio.provider.AuthenticatorService;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioProviderHelper;

public class MainActivity extends Activity {
		
	private static final int LOADER_CONVERSATIONS = 0;

	private static final int CREATE_CONVERSATION = 1;
	
	private ListView mConversationsList;
	
	private ConversationsAdapter mAdapter;
	private Button mStartFirstConversation;
	
	private ConversationsCountChange mCountChangeObserver = new ConversationsCountChange();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		
		if ( !PreferenceManager.getDefaultSharedPreferences(this)
				.getBoolean(AboutActivity.LICENSE_ACCEPTED, false)) {
		
			startActivity(new Intent(this, AboutActivity.class));
			
			finish();
			
			return;
		}
		
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
				
				pickContact();
				
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		if ( requestCode == CREATE_CONVERSATION) {
			
			if ( resultCode != RESULT_OK) return;
			
			new CreateConversationTask().execute(data.getData());
			
		}
		
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
			
		    pickContact();
			
			return true;
			
		} else if ( item.getItemId() == R.id.action_about) {
				
			startActivity(new Intent(this, AboutActivity.class));
			
			return true;
		} else if ( item.getItemId() == R.id.action_add_contact) {
			
			Intent intent = new Intent(Intent.ACTION_INSERT);
			intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
			
			startActivity(intent);
			
			return true;
		
		} else if ( item.getItemId() == R.id.action_report_bug) {
			
			ACRA.getErrorReporter().handleSilentException(null);
			
			return true;
			
		} else if ( item.getItemId() == R.id.action_network_status) {
			
			startActivity(new Intent(this, NetworkStatusActivity.class));
			
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

	private void pickContact() {
		Intent pickContactIntent = new Intent(Intent.ACTION_PICK, Uri.parse("content://contacts"));
		pickContactIntent.setType(Email.CONTENT_TYPE);
		startActivityForResult(pickContactIntent, CREATE_CONVERSATION);
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
	
	private class CreateConversationTask extends AsyncTask<Uri, Void, Uri> {

		private int mErrorReason;
		
		@Override
		protected Uri doInBackground(Uri... params) {
			
			
			String[] projection = new String[]{ ContactsContract.Data.DISPLAY_NAME,
						  						ContactsContract.CommonDataKinds.Email.ADDRESS,
												ContactsContract.Data._ID };
			
			Cursor c = getContentResolver().query(params[0], projection, null, null, null);
			
			if (!c.moveToFirst()) {
				mErrorReason = R.string.error_contact_not_found;
				return null;
			}
			

			String email = c.getString(c.getColumnIndex(CommonDataKinds.Email.ADDRESS));
			String displayName = c.getString(c.getColumnIndex(Data.DISPLAY_NAME));
			long contactId = c.getLong(c.getColumnIndex(Data._ID));
			
			// we check that the address can be parsed
			try {
				AddressBuilder.DEFAULT.parseMailbox(email);
			} catch ( ParseException ex) {
				mErrorReason = R.string.error_email_address_invalid;
				return null;
			}
			
			CarteggioProviderHelper helper = new CarteggioProviderHelper(MainActivity.this);
			CarteggioAccount account = helper.getDefaultAccount();
			
			Uri contact = helper.createOrUpdateContact(email, displayName, contactId);
			
			Uri conversation = helper.createConversation(account, contact);
						
			return conversation;
		}

		@Override
		protected void onPostExecute(Uri result) {
			
			if ( result == null) {
				
				Toast.makeText(MainActivity.this, mErrorReason, Toast.LENGTH_LONG).show();
				
			} else {
			
				Intent intent = new Intent(Intent.ACTION_VIEW, result);
				
				startActivity(intent);
			
			}
		}
		
		
		
	}

	
}
