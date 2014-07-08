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

import java.util.ArrayList;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import ch.carteggio.net.MessageStore;
import ch.carteggio.net.MessagingException;
import ch.carteggio.net.NetworkFactories;
import ch.carteggio.net.MessageStore.Folder;
import ch.carteggio.net.MessageStore.SynchronizationPoint;
import ch.carteggio.provider.AuthenticatorService;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioAccountImpl;

public class MessageReceiverService extends Service {
	
	private ArrayList<MessageReceiver> mAccountReceiver = new ArrayList<MessageReceiver>();

	public static final String LOG_TAG = "MessageReceiverService";
	
	public static final String ACTION_POLL = "ch.carteggio.provider.sync.MessageReceiverService.ACTION_POLL";	
	
	private WakeLock mWakeLock;
	
	@Override
	public void onCreate() {

		super.onCreate();
		
		
		mWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MessageReceiverLock");
		
		AccountManager manager = AccountManager.get(this);		
		
		for ( Account account : manager.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE)) {
			
			CarteggioAccount carteggioAccount = new CarteggioAccountImpl(this.getApplicationContext(), account);
			
			MessageReceiver accountReceiver = new MessageReceiver(carteggioAccount);
			
			accountReceiver.start();
			
			mAccountReceiver.add(accountReceiver);
			
		}
		
		
		
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		if ( intent != null && ACTION_POLL.equals(intent.getAction())) {

			for ( MessageReceiver acccountReceiver : mAccountReceiver ) {
				acccountReceiver.poll();
			}
			
		}
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {

		for ( MessageReceiver acccountReceiver : mAccountReceiver ) {
			acccountReceiver.shutdown();
		}
		
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private class MessageReceiver extends Thread {
	
		private CarteggioAccount mAccount;
		
		private IncomingMessagesProcessor mProcessor;		
		
		private MessageStore mStore;
		
		// design consideration: we keep the reference for the folder
		// here to be able to close it when we receive the shutdown call
		private Folder mFolder = null;
		
		private MessageReceiver(CarteggioAccount account) {
			this.mAccount = account;						
		}
		
		public void run() {
						
			mWakeLock.acquire();
			
			try {								

				mProcessor = new IncomingMessagesProcessor(getApplicationContext(), mAccount);
				
				mStore = NetworkFactories.getInstance(getApplicationContext()).getMessageStore(mAccount);	

				ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				
				while ( !interrupted() ) { 

					// check messages only if a connection is available
					NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
					
					if (activeNetwork != null && activeNetwork.isConnected()) {
							checkMessages();
					}
						
					// wait until we receive instruction to check messages again
					// while we wait we will not hold the wake lock
			
					mWakeLock.release();

					synchronized (this) {
						wait();	
					}

					mWakeLock.acquire();
			
				}
							
			} catch ( Exception ex) {

				Log.e(LOG_TAG, "Error while receiving messages", ex);				

				NotificationService.setReceivingError(getApplicationContext(), true);
							
			} finally {
				
				// release the wake lock if we are still holding it
				if (mWakeLock.isHeld()) mWakeLock.release();
			
			}
			
		}

		private void checkMessages() {
			
			try {
			
				Log.d(LOG_TAG, "Opening inbox");
					
				mFolder = mStore.getInbox();						
				mFolder.open();
			
				NotificationService.setReceivingError(getApplicationContext(), false);
				
				// if we can we start waiting for change notifications from
				// the server. We will stop only when the server the thread is
				// interrupted
				if ( mFolder.isWaitingForChangedSupported()) {
					
					while (!interrupted()) {
						
						mProcessor.processFolder(mFolder);
						
						Log.d(LOG_TAG, "Starting waiting for messages");
						
						// we get the sync point but we will not save it (we save it only after processing the messages)
						SynchronizationPoint syncPoint = mStore.createSynchronizationPoint(mAccount.getPushState());
						mFolder.waitForChanges(syncPoint, mWakeLock);
				
						Log.d(LOG_TAG, "Folder has changed, processing new messages");
						
					}
					
				} else {
									
					while (!interrupted()) {

						mProcessor.processFolder(mFolder);

						Log.d(LOG_TAG, "Starting waiting for messages");

						// sleep until the next time we a requested to check emails
						// while we wait for incoming messages we don't hold the wake lock
						mWakeLock.release();
						
						synchronized (this) {
							wait();	
						}
	
						mWakeLock.acquire();

						Log.d(LOG_TAG, "Received poll request, checking for new messages");
					
					}
					
				}
			
			} catch (Exception e) {
				
				Log.e(LOG_TAG, "Error while polling folder", e);
				
				if ( mFolder != null) {
					try { mFolder.close(); } catch (Exception e2) {}
					mFolder = null;
				}
				
				if ( !mWakeLock.isHeld()) mWakeLock.acquire();
				
				NotificationService.setReceivingError(getApplicationContext(), true);
										
			} 
			
		}
		
		public synchronized void poll() {
			
			Log.d(LOG_TAG, "Poll requested");

			notifyAll();			
		}

		public void shutdown() {

			interrupt();
			
			try { mFolder.close(); } catch (Exception e2) {}
			
		}
				
	
	}

	
}
