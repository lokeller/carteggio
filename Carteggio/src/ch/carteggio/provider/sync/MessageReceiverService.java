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
import android.content.Intent;
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
	
	private static final int ERROR_NOTIFICATION_ID = 2;	
	
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
		
		private boolean mPushActive;		
		private boolean mPushAvailable;
		
		private Folder mFolder = null;
		
		private MessageReceiver(CarteggioAccount account) {
			this.mAccount = account;						
		}
		
		public void run() {
						
			mWakeLock.acquire();
			
			try {								

				mProcessor = new IncomingMessagesProcessor(getApplicationContext(), mAccount);
				
				mStore = NetworkFactories.getInstance(getApplicationContext()).getMessageStore(mAccount);	
																			
				
				mPushAvailable = true;
				
				while ( !interrupted() ) { 
					
					try {
					
					
						// open a connection if necessary
						if ( mFolder == null) {
						
							Log.d(LOG_TAG, "Opening inbox");
							
							mFolder = mStore.getInbox();						
							mFolder.open();
								
						}
						
						
						// if we can we start waiting for change notifications from
						// the server. We will stop only when the server the thread is
						// interrupted
						if ( mFolder.isWaitingForChangedSupported()) {
							
							while (!interrupted()) {
								

								Log.d(LOG_TAG, "Starting waiting for messages");
								
								// we get the sync point but we will not save it (we save it only after processing the messages)
								SynchronizationPoint syncPoint = mStore.parseSynchronizationPoint(mAccount.getPushState());
								mFolder.waitForChanges(syncPoint, mWakeLock);
						
								Log.d(LOG_TAG, "Folder has changed, processing new messages");
								
								mProcessor.processFolder(mFolder);
								
							}
							
						} else {
											
							Log.d(LOG_TAG, "Polling");
							
							mProcessor.processFolder(mFolder);
						}
					
						NotificationService.setReceivingError(getApplicationContext(), false);
						
					} catch (MessagingException e) {
						
						Log.e(LOG_TAG, "Error while polling folder", e);
						
						if ( mFolder != null) {
							try { mFolder.close(); } catch (Exception e2) {}
							mFolder = null;
						}
						
						NotificationService.setReceivingError(getApplicationContext(), true);
												
					} finally {
					
						mWakeLock.release();
			
					}
					
					try {
						
						synchronized (this) {
							wait();	
						}
						
					} catch (InterruptedException e) {
						break;
					}				
				
					mWakeLock.acquire();
			
				}
				
				if ( mFolder != null) {
					try { mFolder.close(); } catch (Exception e2) {}
				}
							
			} catch ( Exception ex) {

				Log.e(LOG_TAG, "Error while receiving messages", ex);				

				NotificationService.setReceivingError(getApplicationContext(), true);
							
			} finally {
				
				if (mWakeLock.isHeld()) mWakeLock.release();
			
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
