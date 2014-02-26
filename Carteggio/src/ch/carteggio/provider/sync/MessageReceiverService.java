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

import org.apache.james.mime4j.dom.Message;

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
import ch.carteggio.net.MessageStore.MessageListener;
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
		
		private MessageReceiver(CarteggioAccount account) {
			this.mAccount = account;						
		}
		
		public synchronized void run() {
						
			mWakeLock.acquire();
			
			try {								

				mProcessor = new IncomingMessagesProcessor(getApplicationContext(), mAccount);
				
				mStore = NetworkFactories.getInstance(getApplicationContext()).getMessageStore(mAccount);	
																			
				Folder mFolder = null;
				
				mPushAvailable = true;
				
				while ( !interrupted() ) { 
					
					try {
					
						if ( mPushAvailable ) {							
							
							if ( !mPushActive ) {
								
								Log.d(LOG_TAG, "Starting push");
								
								mStore.addMessageListener(mStore.getInbox(), new MessageListenerImpl());								
								
								mPushActive = true;
							}
							
							// we don't actually read the new messages if sync is active because 
							// we will receive a notification
							
							Log.d(LOG_TAG, "Poll ignored because we are IDLEing");
							
						} else {
							
							// the server doesn't support push, then let's run a poll
							
							
							// open a connection if necessary
							if ( mFolder == null) {
							
								Log.d(LOG_TAG, "Starting poll");
								
								mFolder = mStore.getInbox();						
								mFolder.open();
									
							}
											
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
						
						wait();
						
					} catch (InterruptedException e) {
						break;
					}				
				
				}
				
				if ( mFolder != null) {
					mFolder.close();
				}
			
				mStore.removeMessageListeners();
				
			} catch ( MessagingException ex) {
				Log.e(LOG_TAG, "Error while setting up connection", ex);				
			}
			
			mWakeLock.release();
			
			
		}
		
		
		public synchronized void poll() {
			
			Log.d(LOG_TAG, "Poll requested");

			mWakeLock.acquire();
			
			notifyAll();			
		}

		public synchronized void shutdown() {
			interrupt();
		}

		private class MessageListenerImpl implements MessageListener {

			
			@Override
			public void messagesArrived(Folder folder, Message[] messages) {
				
				Log.d(LOG_TAG, "Incoming message");								
				
				try {
					folder.open();
					
					mProcessor.processMessages(folder, messages);
					
					folder.close();
				} catch (MessagingException e) {
					Log.e(LOG_TAG, "Error while processsing incoming messages", e);
				}
			}

			@Override
			public void listeningNotSupported() {
				
				Log.d(LOG_TAG, "Listener not supported");				
				
				mAccount.setPushEnabled(false);			
				mPushAvailable = false;
				poll();
			}

			@Override
			public void listenerStarted(Folder folder) {
				
				Log.d(LOG_TAG, "Listener was started");				
				
				try {
					folder.open();
					
					mProcessor.processFolder(folder);
					
					folder.close();
				} catch (MessagingException e) {			
					Log.e(LOG_TAG, "Error intially processing folder", e);
				}
			}

		}
				
	
	}

	
}
