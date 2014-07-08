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
package ch.carteggio.provider.sync;

import ch.carteggio.net.MessagingException;
import ch.carteggio.provider.AuthenticatorService;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioAccountImpl;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class MessageSenderService extends IntentService {

	public static final String ACTION_SEND = "ch.carteggio.provider.sync.MessageSenderService.ACTION_SEND";
	
	private WakeLock mWakeLock;
	
	
	public MessageSenderService() {
		super("MessageSenderService"); 
	}


	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	
	@Override
	public void onCreate() {

		super.onCreate();
		
		mWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MessageReceiverLock");
		
	}

	
	
	@Override
	protected void onHandleIntent(Intent intent) {
		
		if ( intent != null && ACTION_SEND.equals(intent.getAction())) {

			mWakeLock.acquire();
			
			// don't try to send if there is no connection available
			ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			
			NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
			if (activeNetwork == null || !activeNetwork.isConnected()) {
				return;
			}
			
			try {
				
				AccountManager manager = AccountManager.get(this);		
				
				for ( Account account : manager.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE)) {
					
					CarteggioAccount carteggioAccount = new CarteggioAccountImpl(this.getApplicationContext(), account);
					
					OutgoingMessagesProcessor processor = new OutgoingMessagesProcessor(getApplicationContext(), carteggioAccount);
				
					boolean failures = false;
					
					try {
						processor.sendPendingMessages();
					} catch (MessagingException e) {
						failures = true;
					}
					
					try {
						processor.sendPendingConfirmations();
					} catch (MessagingException e) {
						failures = true;
					}
					
					NotificationService.setSendingError(getApplicationContext(), failures);					
					
				}
			
			} finally {
				
				mWakeLock.release();
				
			}
					
		}

		
	}
	
	
}
