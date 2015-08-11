package ch.carteggio.ui;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import ch.carteggio.R;
import ch.carteggio.net.security.AuthType;
import ch.carteggio.provider.AuthenticatorService;
import ch.carteggio.provider.CarteggioContract;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.sync.MessageReceiverService;

public class NewAccountActivity extends Activity implements OnClickListener {

	private EditText mDisplayName;
	private EditText mEmail;
	private EditText mPassword;
	private CheckBox mAutoconfig;
	
	private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_new_account);
	
		mDisplayName = (EditText) findViewById(R.id.text_name);
		mEmail = (EditText) findViewById(R.id.text_email);
		mPassword = (EditText) findViewById(R.id.text_password);
		mAutoconfig = (CheckBox) findViewById(R.id.checkbox_autoconfigure);
		

		if ( getIntent().getExtras().containsKey(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)) {
			
			mAccountAuthenticatorResponse = getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
						
			mAccountAuthenticatorResponse.onRequestContinued();
		}
		
	}

	@Override
	public void onClick(View v) {
	
		
		
		
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		if ( item.getItemId() == R.id.action_accept) {
		
			if ( mEmail.getText().length() == 0) {
				Toast.makeText(this, "You need to insert a valid email", Toast.LENGTH_SHORT).show();
				return true;
			}
			
			AccountManager manager = AccountManager.get(this);
			
			CarteggioProviderHelper helper = new CarteggioProviderHelper(this);
			
			helper.createOrUpdateContact(mEmail.getText().toString(), 
											mDisplayName.getText().toString(), -1);
			
			Account mAccount = new Account(mEmail.getText().toString(), 
												AuthenticatorService.ACCOUNT_TYPE);
			
			Bundle settings = new Bundle();
			
			boolean autoconfigured = false;
			
			if (mAutoconfig.isChecked()) {
				autoconfigured = autoconfigure(mEmail.getText().toString(), settings);
			}
				
			settings.putString(AuthenticatorService.KEY_DISPLAY_NAME, 
									mDisplayName.getText().toString());
			settings.putString(AuthenticatorService.KEY_OUTGOING_PASSWORD, 
									mPassword.getText().toString());
			settings.putString(AuthenticatorService.KEY_INCOMING_PASSWORD, 
									mPassword.getText().toString());				

			if (!autoconfigured ) {
			
				settings.putString(AuthenticatorService.KEY_INCOMING_AUTH, 
						AuthType.PLAIN.toString());			
				settings.putString(AuthenticatorService.KEY_INCOMING_PROTO, 
						"imap+ssl");			
				settings.putString(AuthenticatorService.KEY_INCOMING_PORT, 
						"993");
				
				settings.putString(AuthenticatorService.KEY_OUTGOING_AUTH, 
						AuthType.PLAIN.toString());			
				settings.putString(AuthenticatorService.KEY_OUTGOING_PROTO, 
						"smtp+ssl");			
				settings.putString(AuthenticatorService.KEY_OUTGOING_PORT, 
						"465");
			}
			
			ContentResolver.setIsSyncable(mAccount, CarteggioContract.AUTHORITY, 1);
			ContentResolver.setSyncAutomatically(mAccount, CarteggioContract.AUTHORITY, true);				
			ContentResolver.addPeriodicSync(mAccount, CarteggioContract.AUTHORITY, new Bundle(), 60);
			
			manager.addAccountExplicitly(mAccount, "", settings);				
					    
			
			if (!autoconfigured ) {	
				
				if ( mAutoconfig.isChecked() ) {
					Toast.makeText(this, R.string.message_autoconfig_failed, Toast.LENGTH_LONG).show();
				}
				
				Intent intent = new Intent(this, EditAccountActivity.class);
				
				intent.putExtra("account", mAccount);
				
				// pass on the account authenticator response so that we the receiving 
				// activity will be able to notify that the account is ready
				intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, mAccountAuthenticatorResponse);
				
				startActivity(intent);
				
			} else {
			
				// we need to stop this service to make sure it picks up the new configuration
				stopService(new Intent(getApplicationContext(), MessageReceiverService.class));
				
				// notify the initiator that the account was created
				if ( mAccountAuthenticatorResponse != null ) {
					
					Bundle bundle = new Bundle();
					
					bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, AuthenticatorService.ACCOUNT_TYPE);
					bundle.putString(AccountManager.KEY_ACCOUNT_NAME, mEmail.getText().toString());
									
				    mAccountAuthenticatorResponse.onResult(bundle);
			
				} 
				
				
			}
			
			
			
		    finish();
		    
			return true;
		}
		
		return super.onOptionsItemSelected(item);		
		
	}
	
	
	private boolean autoconfigure(String email, Bundle settings) {
		
		if (email.endsWith("@gmail.com")) {
			
			String username = email.substring(0, email.indexOf('@'));
			
			settings.putString(AuthenticatorService.KEY_INCOMING_AUTH, AuthType.PLAIN.toString());
			settings.putString(AuthenticatorService.KEY_INCOMING_HOST, "imap.gmail.com");
			settings.putString(AuthenticatorService.KEY_INCOMING_PORT, "993");
			settings.putString(AuthenticatorService.KEY_INCOMING_PROTO, "imap+ssl");
			settings.putString(AuthenticatorService.KEY_INCOMING_USERNAME, username);
			
			settings.putString(AuthenticatorService.KEY_OUTGOING_AUTH, AuthType.PLAIN.toString());
			settings.putString(AuthenticatorService.KEY_OUTGOING_HOST, "smtp.gmail.com");
			settings.putString(AuthenticatorService.KEY_OUTGOING_PORT, "465");
			settings.putString(AuthenticatorService.KEY_OUTGOING_PROTO, "smtp+ssl");
			settings.putString(AuthenticatorService.KEY_OUTGOING_USERNAME, username);
			
			settings.putString(AuthenticatorService.KEY_INBOX_PATH, "");
			
			Toast.makeText(this, getString(R.string.gmail_warning), Toast.LENGTH_LONG).show();
			
			return true;
			

		} else if (email.endsWith("@fastmail.com")) {
				
			settings.putString(AuthenticatorService.KEY_INCOMING_AUTH, AuthType.PLAIN.toString());
			settings.putString(AuthenticatorService.KEY_INCOMING_HOST, "mail.messagingengine.com");
			settings.putString(AuthenticatorService.KEY_INCOMING_PORT, "993");
			settings.putString(AuthenticatorService.KEY_INCOMING_PROTO, "imap+ssl");
			settings.putString(AuthenticatorService.KEY_INCOMING_USERNAME, email);
			
			settings.putString(AuthenticatorService.KEY_OUTGOING_AUTH, AuthType.PLAIN.toString());
			settings.putString(AuthenticatorService.KEY_OUTGOING_HOST, "mail.messagingengine.com");
			settings.putString(AuthenticatorService.KEY_OUTGOING_PORT, "465");
			settings.putString(AuthenticatorService.KEY_OUTGOING_PROTO, "smtp+ssl");
			settings.putString(AuthenticatorService.KEY_OUTGOING_USERNAME, email);
			
			settings.putString(AuthenticatorService.KEY_INBOX_PATH, "");
			
			return true;
			
		} else {
			return false;
		}
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.account, menu);
		return true;
	}

	
	
}
