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

import java.util.Date;
import java.util.UUID;

import ch.carteggio.provider.CarteggioContract.Contacts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;

public class CarteggioAccountImpl implements CarteggioAccount {
	
	private AccountManager mAccountManager;
	private Account mAccount;
	private CarteggioProviderHelper mHelper;
	
	public CarteggioAccountImpl(Context context, Account account) {
		
		if (!AuthenticatorService.ACCOUNT_TYPE.equals(account.type)) {
			throw new RuntimeException("Invalid account");
		}
		
		this.mAccount = account;
		
		// we use the application context to avoid to keep references to activities or services
		
		this.mAccountManager = AccountManager.get(context.getApplicationContext());
		this.mHelper = new CarteggioProviderHelper(context.getApplicationContext());				
	}
	
	@Override
	public String getEmail() {
		return mAccount.name;
	}	
	
	@Override
	public String getInboxPath() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_INBOX_PATH);
	}

	@Override
	public String getIncomingProto() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_PROTO);
	}

	@Override
	public String getOutgoingProto() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PROTO);
	}

	@Override
	public String getIncomingHost() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_HOST);
	}

	@Override
	public String getOutgoingHost() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_HOST);
	}

	@Override
	public String getIncomingPort() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_PORT);
	}

	@Override
	public String getOutgoingPort() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PORT);
	}

	@Override
	public String getIncomingAuthenticationMethod() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_AUTH);
	}

	@Override
	public String getOutgoingAuthenticationMethod() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_AUTH);
	}

	@Override
	public String getIncomingUsername() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_USERNAME);
	}

	@Override
	public String getOutgoingUsername() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_USERNAME);
	}

	@Override
	public String getIncomingPassword() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_INCOMING_PASSWORD);
	}
	
	@Override
	public String getOutgoingPassword() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_OUTGOING_PASSWORD);
	}
	
	@Override
	public String getDisplayName() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_DISPLAY_NAME);
	}
	
	@Override
	public long getContactId() {
		
		Uri uri = mHelper.getContact(mAccount.name);
		
		if ( uri == null ) {
			
			// database is corrupted, we need to re-create the contact
			uri = mHelper.createOrUpdateContact(getEmail(), getDisplayName(), Contacts.NO_ANDROID_CONTACT);
		}
		
		return ContentUris.parseId(uri);
	}
	
	@Override
	public String getMailDomain() {
		return mAccount.name.substring(mAccount.name.indexOf('@'));
	}
	
	@Override
	public Date getLastCheckDate() {
		String date = mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_LAST_CHECK);
		
		return new Date(Long.parseLong(date));
	}
	
	@Override
	public void setLastCheckDate(Date date) {
		mAccountManager.setUserData(mAccount, AuthenticatorService.KEY_LAST_CHECK, Long.toString(date.getTime()));
	}
	
	@Override
	public boolean isPushEnabled() {
		return Boolean.parseBoolean(mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_PUSH_ENABLED));
	}
	
	@Override
	public void setPushEnabled(boolean enabled) {
		mAccountManager.setUserData(mAccount, AuthenticatorService.KEY_PUSH_ENABLED, Boolean.toString(enabled));
	}
	
	@Override
	public String getPushState() {
		return mAccountManager.getUserData(mAccount, AuthenticatorService.KEY_PUSH_STATE);
	}
	
	@Override
	public void setPushState(String state) {
		mAccountManager.setUserData(mAccount, AuthenticatorService.KEY_PUSH_STATE, state);
	}

	@Override
	public String createRandomMessageId() {				
		return UUID.randomUUID().toString() + getMailDomain();
	}	
	
}
