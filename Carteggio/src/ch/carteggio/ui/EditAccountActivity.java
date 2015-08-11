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
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import ch.carteggio.R;
import ch.carteggio.net.security.AuthType;
import ch.carteggio.provider.AuthenticatorService;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.sync.MessageReceiverService;

public class EditAccountActivity extends Activity {

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    
    private Account mAccount;
    
	private TextView mEmailText;
	private TextView mDisplayNameText;
	private Spinner mIncomingAuthMethod;
	private Spinner mOutgoingAuthMethod;
	private Spinner mIncomingProto;
	private Spinner mOutgoingProto;
	private TextView mIncomingHostText;
	private TextView mOutgoingHostText;
	private TextView mIncomingPortText;
	private TextView mOutgoingPortText;
	private TextView mInboxPathText;
	private TextView mIncomingUsernameText;
	private TextView mOutgoingUsernameText;
	private TextView mIncomingPasswordText;
	private TextView mOutgoingPasswordText;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {	
		super.onCreate(savedInstanceState);
			
		setContentView(R.layout.activity_edit_account);
		
		if (!getIntent().getExtras().containsKey("account")) {
			throw new IllegalArgumentException("account missing form intent");
		}
		
		mAccount = getIntent().getExtras().getParcelable("account");
		
		setupFields();
			
		// This is received when the new account activity couldn't configure
		// the account and called this activity to let the user finish to setup
		// the account. We will need to send a response when we are done
		if ( getIntent().getExtras().containsKey(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)) {
			
			mAccountAuthenticatorResponse = getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
						
			mAccountAuthenticatorResponse.onRequestContinued();
		}
		
		
	}

	private void setupFields() {
		
		AccountManager manager = AccountManager.get(this);
					
		mEmailText = ((TextView) findViewById(R.id.account_email));
		mEmailText.setEnabled(false);
		mEmailText.setText(mAccount.name);
		
		mDisplayNameText = ((TextView) findViewById(R.id.account_display_name));
		mDisplayNameText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_DISPLAY_NAME));
		
		mOutgoingHostText  = ((TextView) findViewById(R.id.account_outgoing_host));
		mOutgoingHostText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_HOST));
		
		mIncomingHostText  = ((TextView) findViewById(R.id.account_incoming_host));
		mIncomingHostText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_HOST));
		
		mIncomingPortText = ((TextView) findViewById(R.id.account_incoming_port));
		mIncomingPortText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_PORT));
		
		mOutgoingPortText = ((TextView) findViewById(R.id.account_outgoing_port));
		mOutgoingPortText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PORT));
		
		mInboxPathText = ((TextView) findViewById(R.id.account_inbox_path));
		mInboxPathText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_INBOX_PATH));
		
		mIncomingUsernameText = ((TextView) findViewById(R.id.account_incoming_user));
		mIncomingUsernameText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_USERNAME));
		
		mOutgoingUsernameText = ((TextView) findViewById(R.id.account_outgoing_user));
		mOutgoingUsernameText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_USERNAME));
		
		mIncomingPasswordText = ((TextView) findViewById(R.id.account_incoming_password));
		mIncomingPasswordText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_PASSWORD));
		
		mOutgoingPasswordText = ((TextView) findViewById(R.id.account_outgoing_password));
		mOutgoingPasswordText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PASSWORD));
	
		ArrayAdapter<AuthType> authTypeArray = new ArrayAdapter<AuthType>(this, android.R.layout.simple_list_item_1, AuthType.values());
		
		mIncomingAuthMethod = (Spinner) findViewById(R.id.account_incoming_auth);
		mIncomingAuthMethod.setAdapter(authTypeArray);
		mIncomingAuthMethod.setSelection(AuthType.valueOf(manager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_AUTH)).ordinal());
		
		mOutgoingAuthMethod = (Spinner) findViewById(R.id.account_outgoing_auth);
		mOutgoingAuthMethod.setAdapter(authTypeArray);
		mOutgoingAuthMethod.setSelection(AuthType.valueOf(manager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_AUTH)).ordinal());
		
		// incoming protocol
		
		ArrayAdapter<String> incomingProtoArray = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[] {"imap", "imap+tls", "imap+ssl"});
		
		mIncomingProto = (Spinner) findViewById(R.id.account_incoming_protocol);
		mIncomingProto.setAdapter(incomingProtoArray);

		String currentIncomingProto = manager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_PROTO);
		
		for ( int i = 0 ; i < mIncomingProto.getCount(); i++) {
			if (mIncomingProto.getItemAtPosition(i).equals(currentIncomingProto)) {
				mIncomingProto.setSelection(i);
				break;
			}
		}
		
		// outgoing protocol
		
		ArrayAdapter<String> outgoingProtoArray = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[] {"smtp", "smtp+tls", "smtp+ssl"});
		
		mOutgoingProto = (Spinner) findViewById(R.id.account_outgoing_protocol);
		mOutgoingProto.setAdapter(outgoingProtoArray);
	
		String currentOutgoingProto = manager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PROTO);
		
		for ( int i = 0 ; i < mOutgoingProto.getCount(); i++) {
			if (mOutgoingProto.getItemAtPosition(i).equals(currentOutgoingProto)) {
				mOutgoingProto.setSelection(i);
				break;
			}
		}
	
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		if ( item.getItemId() == R.id.action_accept) {
		
			if ( mEmailText.getText().length() == 0) {
				Toast.makeText(this, "You need to insert a valid email", Toast.LENGTH_SHORT).show();
				return true;
			}
			
			CarteggioProviderHelper helper = new CarteggioProviderHelper(this);
			
			helper.createOrUpdateContact(mAccount.name, 
					mDisplayNameText.getText().toString(), -1);

			AccountManager manager = AccountManager.get(this);

			manager.setUserData(mAccount, AuthenticatorService.KEY_INBOX_PATH, 
					mInboxPathText.getText().toString());
			
			manager.setUserData(mAccount, AuthenticatorService.KEY_INCOMING_PROTO, 
					mIncomingProto.getSelectedItem().toString());

			manager.setUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PROTO, 
					mOutgoingProto.getSelectedItem().toString());

			manager.setUserData(mAccount, AuthenticatorService.KEY_INCOMING_AUTH, 
					mIncomingAuthMethod.getSelectedItem().toString());

			manager.setUserData(mAccount, AuthenticatorService.KEY_OUTGOING_AUTH, 
					mOutgoingAuthMethod.getSelectedItem().toString());
			
			manager.setUserData(mAccount, AuthenticatorService.KEY_INCOMING_PORT, 
					mIncomingPortText.getText().toString());

			manager.setUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PORT, 
					mOutgoingPortText.getText().toString());

			manager.setUserData(mAccount, AuthenticatorService.KEY_INCOMING_USERNAME, 
					mIncomingUsernameText.getText().toString());

			manager.setUserData(mAccount, AuthenticatorService.KEY_OUTGOING_USERNAME, 
					mOutgoingUsernameText.getText().toString());

			manager.setUserData(mAccount, AuthenticatorService.KEY_INCOMING_HOST, 
									mIncomingHostText.getText().toString());
			
			manager.setUserData(mAccount, AuthenticatorService.KEY_OUTGOING_HOST, 
									mOutgoingHostText.getText().toString());
			
			manager.setUserData(mAccount, AuthenticatorService.KEY_DISPLAY_NAME, 
									mDisplayNameText.getText().toString());								
			
			manager.setUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PASSWORD, 
									mOutgoingPasswordText.getText().toString());
			
			manager.setUserData(mAccount, AuthenticatorService.KEY_INCOMING_PASSWORD, 
									mIncomingPasswordText.getText().toString());
			
		
			// inform that we finished setting up the account
			if ( mAccountAuthenticatorResponse != null ) {
				
				Bundle bundle = new Bundle();
				
				bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, 
									AuthenticatorService.ACCOUNT_TYPE);
				bundle.putString(AccountManager.KEY_ACCOUNT_NAME, 
									mEmailText.getText().toString());
								
			    mAccountAuthenticatorResponse.onResult(bundle);
		
			} 
		    
			// we need to stop this service to make sure it picks up the new configuration
			stopService(new Intent(getApplicationContext(), MessageReceiverService.class));
			
		    finish();
		    
			return true;
		}
		
		return super.onOptionsItemSelected(item);		
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.account, menu);
		return true;
	}

	
	
	
	
}
