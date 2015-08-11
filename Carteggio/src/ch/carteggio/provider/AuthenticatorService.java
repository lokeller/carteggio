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

import ch.carteggio.ui.EditAccountActivity;
import ch.carteggio.ui.NewAccountActivity;
import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class AuthenticatorService extends Service {

	public static final String ACCOUNT_TYPE = "ch.carteggio.emailaccount";

	public static final String KEY_INCOMING_PROTO = "ch.carteggio.EmailAuthenticator.KEY_INCOMING_PROTO";
	public static final String KEY_OUTGOING_PROTO = "ch.carteggio.EmailAuthenticator.KEY_OUTGOING_PROTO";
	
	public static final String KEY_INCOMING_AUTH = "ch.carteggio.EmailAuthenticator.KEY_INCOMING_AUTH";
	public static final String KEY_OUTGOING_AUTH = "ch.carteggio.EmailAuthenticator.KEY_OUTGOING_AUTH";
	
	public static final String KEY_INCOMING_HOST = "ch.carteggio.EmailAuthenticator.KEY_INCOMING_HOST";
	public static final String KEY_OUTGOING_HOST = "ch.carteggio.EmailAuthenticator.KEY_OUTGOING_HOST";
	
	public static final String KEY_INBOX_PATH = "ch.carteggio.EmailAuthenticator.KEY_INBOX_PATH";
	
	public static final String KEY_INCOMING_USERNAME = "ch.carteggio.EmailAuthenticator.KEY_INCOMING_USERNAME";
	public static final String KEY_OUTGOING_USERNAME = "ch.carteggio.EmailAuthenticator.KEY_OUTGOING_USERNAME";
	
	public static final String KEY_INCOMING_PORT = "ch.carteggio.EmailAuthenticator.KEY_INCOMING_PORT";
	public static final String KEY_OUTGOING_PORT = "ch.carteggio.EmailAuthenticator.KEY_OUTGOING_PORT";

	public static final String KEY_INCOMING_PASSWORD = "ch.carteggio.EmailAuthenticator.KEY_INCOMING_PASSWORD";
	public static final String KEY_OUTGOING_PASSWORD = "ch.carteggio.EmailAuthenticator.KEY_OUTGOING_PASSWORD";
	
	public static final String KEY_DISPLAY_NAME = "ch.carteggio.EmailAuthenticator.KEY_DISPLAY_NAME";
	
	public static final String KEY_LAST_CHECK = "ch.carteggio.EmailAuthenticator.KEY_LAST_CHECK";

	public static final String KEY_PUSH_STATE = "ch.carteggio.AuthenticatorService.KEY_PUSH_STATE";
	
	public static final String KEY_PUSH_ENABLED = "ch.carteggio.AuthenticatorService.KEY_PUSH_ENABLED";
	
	
	private EmailAuthenticator mAuthenticator;

	@Override
	public void onCreate() {
		super.onCreate();
		
		mAuthenticator = new  EmailAuthenticator(this);
		
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mAuthenticator.getIBinder();
	}
	
	public class EmailAuthenticator extends AbstractAccountAuthenticator {
		
		private Context mContext;
		
		public EmailAuthenticator(Context context) {
			super(context);	
			this.mContext = context;
		}
		
		@Override
		public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
			
			return null;

		}

		@Override
		public Bundle addAccount(AccountAuthenticatorResponse response,
				String accountType, String authTokenType,
				String[] requiredFeatures, Bundle options)
				throws NetworkErrorException {
			
			Bundle bundle = new Bundle();
			
			Intent intent = new Intent(mContext, NewAccountActivity.class);
			
			intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
			
			bundle.putParcelable(AccountManager.KEY_INTENT, intent);
			
			return bundle;
		}

		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse response,
				Account account, Bundle options) throws NetworkErrorException {
			
			Bundle bundle = new Bundle();
			
			bundle.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
			
			return bundle;
		}

		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse response,
				Account account, String authTokenType, Bundle options)
				throws NetworkErrorException {
			throw new UnsupportedOperationException();		
		}

		@Override
		public String getAuthTokenLabel(String authTokenType) {
			
			throw new UnsupportedOperationException();
			
		}

		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse response,
				Account account, String authTokenType, Bundle options)
				throws NetworkErrorException {
			
			Bundle bundle = new Bundle();
			
			Intent intent = new Intent(mContext, EditAccountActivity.class);
			
			intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);					
			bundle.putParcelable(AccountManager.KEY_INTENT, intent);
						
			return bundle;		
		}

		@Override
		public Bundle hasFeatures(AccountAuthenticatorResponse response,
				Account account, String[] features) throws NetworkErrorException {
						
			throw new UnsupportedOperationException();		
		}

	}

	
}
