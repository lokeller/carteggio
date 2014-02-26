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

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioContract;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.CarteggioContract.Contacts;
import ch.carteggio.R;

public class ContactsActivity extends Activity {
	
	private static final int CONTACTS_LOADER_ID = 1;

	private ListView mContactsList;

	private SimpleCursorAdapter mContactsAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_contacts);
				
		mContactsList = (ListView) findViewById(R.id.contacts); 
		
		String[] fromColumns = {
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.CommonDataKinds.Email.ADDRESS,
				ContactsContract.CommonDataKinds.Email.TYPE
		};
		
		int [] toViews = {
				R.id.name,
				R.id.email,
				R.id.type
		};
						
	    mContactsAdapter = new SimpleCursorAdapter(
	            this,                        // the context of the activity
	            R.layout.list_item_contact,   // the view item containing the detail widgets
	            null,                     // the backing cursor
	            fromColumns,                // the columns in the cursor that provide the data
	            toViews,                    // the views in the view item that display the data
	            0);
	    
	    mContactsAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				
				if ( columnIndex == cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)) {
				
					((TextView) view).setText(ContactsContract.CommonDataKinds.Email.getTypeLabel(getResources(), 
										cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)), 
										cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL))));
					
					return true;
					
				} else {
					
					return false;
					
				}
			}
		});
	    
	    mContactsList.setAdapter(mContactsAdapter);
	    
		mContactsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
				
				Cursor contactsCursor = mContactsAdapter.getCursor();
				
				contactsCursor.moveToPosition(position);
				
				String contactId = contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.Data._ID));
				String name = contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));				
				String email = contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS));
				
				ContentValues values = new ContentValues();
				
				values.put(CarteggioContract.Contacts.EMAIL, email);
				values.put(CarteggioContract.Contacts.ANDROID_CONTACT_ID, contactId);
				values.put(CarteggioContract.Contacts.NAME, name);
				
				new CreateConversationTask().execute(values);
								
				
			}
			
		});
		
		getLoaderManager().initLoader(CONTACTS_LOADER_ID, null, mContactsLoader);
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.contacts, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	
		if ( item.getItemId() == R.id.action_search) {
			Toast.makeText(getApplicationContext(), "Not implemented", Toast.LENGTH_SHORT).show();
		} else if ( item.getItemId() == R.id.action_add_contact) {
			 Intent intent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
			 startActivity(intent);
		}
		
		return super.onOptionsItemSelected(item);
	}



	private LoaderCallbacks<Cursor> mContactsLoader = new LoaderCallbacks<Cursor>() {
		
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {		
			
			String sort = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
			
			String[] selectionArgs = { ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE } ;
			
			String selection = ContactsContract.Data.MIMETYPE +" = ?";
			
			String[] projection = {
					ContactsContract.Data._ID, 
					ContactsContract.Data.DISPLAY_NAME,
					ContactsContract.Data.PHOTO_THUMBNAIL_URI,
					ContactsContract.CommonDataKinds.Email.ADDRESS,
					ContactsContract.CommonDataKinds.Email.TYPE,
					ContactsContract.CommonDataKinds.Email.LABEL
			};
			
			return new CursorLoader(ContactsActivity.this,
									ContactsContract.Data.CONTENT_URI, 
									projection, selection, selectionArgs, sort);
		}
	
		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
	
			mContactsAdapter.swapCursor(data);
	
		}
	
		@Override
		public void onLoaderReset(Loader<Cursor> loader) {
			mContactsAdapter.swapCursor(null);		
		}
		
	};
	
	private class CreateConversationTask extends AsyncTask<ContentValues, Void, Uri> {

		@Override
		protected Uri doInBackground(ContentValues... params) {
			
			CarteggioProviderHelper helper = new CarteggioProviderHelper(ContactsActivity.this);
						
			CarteggioAccount account = helper.getDefaultAccount();
			
			Uri contact = helper.createOrUpdateContact(params[0].getAsString(Contacts.EMAIL), 
												params[0].getAsString(Contacts.NAME), 
												params[0].getAsInteger(Contacts.ANDROID_CONTACT_ID));
			
			Uri conversation = helper.createConversation(account, contact);
						
			return conversation;
		}

		@Override
		protected void onPostExecute(Uri result) {
			
			Intent intent = new Intent(Intent.ACTION_VIEW, result);
			
			startActivity(intent);
			
		}
		
		
		
	}

}
