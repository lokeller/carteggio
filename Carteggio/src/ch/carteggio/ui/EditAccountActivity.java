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
import android.widget.TextView;
import android.widget.Toast;
import ch.carteggio.R;
import ch.carteggio.provider.AuthenticatorService;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.sync.MessageReceiverService;

public class EditAccountActivity extends Activity {

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    
    private Account mAccount;
    
	private TextView mEmailText;
	private TextView mDisplayNameText;
	private TextView mIncomingServerText;
	private TextView mOutgoingServerText;    
	private TextView mIncomingPasswordText;
	private TextView mOutgoingPasswordText;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {	
		super.onCreate(savedInstanceState);
			
		setContentView(R.layout.activity_edit_account);
		
		mEmailText = ((TextView) findViewById(R.id.account_email));
		mDisplayNameText = ((TextView) findViewById(R.id.account_display_name));
		mIncomingServerText = ((TextView) findViewById(R.id.account_incoming));		
		mOutgoingServerText = ((TextView) findViewById(R.id.account_outgoing));
		mIncomingPasswordText = ((TextView) findViewById(R.id.account_incoming_password));
		mOutgoingPasswordText = ((TextView) findViewById(R.id.account_outgoing_password));
		
		if (!getIntent().getExtras().containsKey("account")) {
			throw new IllegalArgumentException("account missing form intent");
		}
		
		mAccount = getIntent().getExtras().getParcelable("account");
		
		AccountManager manager = AccountManager.get(this);
		
		mEmailText.setText(mAccount.name);
		mDisplayNameText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_DISPLAY_NAME));			
		mIncomingServerText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_SERVER));
		mOutgoingServerText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_SERVER));			
		mIncomingPasswordText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_PASSWORD));
		mOutgoingPasswordText.setText(manager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PASSWORD));
		
		mEmailText.setEnabled(false);
			
		// This is received when the new account activity couldn't configure
		// the account and called this activity to let the user finish to setup
		// the account. We will need to send a response when we are done
		if ( getIntent().getExtras().containsKey(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)) {
			
			mAccountAuthenticatorResponse = getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
						
			mAccountAuthenticatorResponse.onRequestContinued();
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
			
			manager.setUserData(mAccount, AuthenticatorService.KEY_INCOMING_SERVER, 
									mIncomingServerText.getText().toString());
			
			manager.setUserData(mAccount, AuthenticatorService.KEY_OUTGOING_SERVER, 
									mOutgoingServerText.getText().toString());
			
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
