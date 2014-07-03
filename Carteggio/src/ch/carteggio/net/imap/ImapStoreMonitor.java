/*   
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
 * 
 * This file contains code distributed with K-9 sources
 * that didn't include any copyright attribution header.
 *    
 */


package ch.carteggio.net.imap;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import ch.carteggio.net.imap.ImapFolderState.ImapFolderListener;
import ch.carteggio.net.imap.ImapIdleSession.IdleListener;

/**
 * 
 * This class is used to monitor multiple IMAP folders for changes.
 * 
 * The changes to the folder are delivered to the {@link ImapPushReceiver}
 * class passed in the constructor. A client of this class starts the
 * listening by calling the method {@link ImapStore.start}. 
 * 
 * The class starts one thread for every folder that has to be observed.
 * This thread initiates an {@link ImapIdleSession} the folder and
 * automatically re-start the session if an error occurs. It stop only
 * if either the method {@link ImapStore.stop} is called or the server 
 * notifies that it doesn't support IMAP idle.
 * 
 * Notifications for changes are forwarded to the designated 
 * {@link ImapPushReceiver} from a listener that is registered on the
 * {@link ImapIdleSession}. See {@link ImapPushReceiver} for a description
 * of the events that are notified.
 * 
 */

public class ImapStoreMonitor {
	
	private static final long PUSH_WAKE_LOCK_TIMEOUT = 0;
	
	private final ImapStore mStore;
    private final ImapPushReceiver mReceiver;

    private HashMap<String, ListeningThread> mFolderPushers = new HashMap<String, ListeningThread>();

    public ImapStoreMonitor(ImapStore store, ImapPushReceiver receiver) {
		mStore = store;
        mReceiver = receiver;
    }

    private ImapFolderListener mListener = new ImapFolderListener() {

		@Override
		public void onFolderChanged(String folderName) {
			mReceiver.onFolderChanged(folderName);
		}
    	
	};
    
    public void start(List<String> folderNames) {
        
        synchronized (mFolderPushers) {

        	for (String folderName : folderNames) {
                
            	ListeningThread pusher = mFolderPushers.get(folderName);
                
                if (pusher == null) {
                	
                    ImapIdleSession session = new ImapIdleSession(mStore, folderName);
                    
                    session.getFolderState().setListener(mListener);
                    
                    pusher = new ListeningThread(session);
                    
                    mFolderPushers.put(folderName, pusher);
                    
                    pusher.start();
                
                }
            }
        }
    }

    public void stop() {
    	
        if (ImapStore.DEBUG)
            Log.i(ImapStore.LOG_TAG, "Requested stop of IMAP pusher");

        new Thread() {
        
        	public void run () {
        		
	            synchronized (mFolderPushers) {
	                for (ListeningThread folderPusher : mFolderPushers.values()) {
	                    try {
	                        if (ImapStore.DEBUG)
	                            Log.i(ImapStore.LOG_TAG, "Requesting stop of IMAP folderPusher ");
	                        folderPusher.stopWaiting();
	                    } catch (Exception e) {
	                        Log.e(ImapStore.LOG_TAG, "Got exception while stopping ", e);
	                    }
	                }
	                mFolderPushers.clear();
	            }
        	}
        }.start();
    }
    
	private class ListeningThread extends Thread implements IdleListener {

		private ImapIdleSession mSession;
		
	    private final AtomicInteger mDelayTime = new AtomicInteger(ImapStore.NORMAL_DELAY_TIME);
	
	    private boolean mCanConnect = false;
	    private boolean mCanWait = false;
	    
	    private final Object mWaitObject = new Object();
	    private Semaphore mSemaphore = new Semaphore(1);
	    
	    private WakeLock mWakeLock;
		
	    public ListeningThread(ImapIdleSession mSession) {
			this.mSession = mSession;
			
	        PowerManager pm = (PowerManager) mSession.getStore().getContext().getSystemService(Context.POWER_SERVICE);
	        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ImapFolderPusher " + ":" + mSession.getFolderName().getName());
	        mWakeLock.setReferenceCounted(false);
			
		}

	    public void stopWaiting() {
	    	
	    	// stop pending IDLE session and prevent further sessions
	    	try {
		    	
	    		
	    		// we wait until either no session is ideling or the 
	    		// session is sucessfully ideling
	    		mSemaphore.acquire();
		    	
		    	// no more connections will happen after this call
		    	mCanConnect = true;
		    	
		    	// stop any pending session
		    	mSession.stopWaiting();
		    
	    	} catch (InterruptedException ex) {
	    		// this should not happen
	    	} finally {
	    		mSemaphore.release();
	    	}
	    	
	    	
	    	// stop waiting and prevent further wait
	    	synchronized (mWaitObject) {
	    		
	    		// no more will happen after this line
	    		mCanWait = false;
	    		
	    		// stop any pending wait
		    	interrupt();	
			}
	    	
	    }
	    
		public void run() {

	        if (ImapStore.DEBUG) Log.i(ImapStore.LOG_TAG, "Pusher starting");

	    	mWakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT);
	 
	    	try {
	
		        while (mCanConnect) {
		        	
		            try {
		            	
		            	mSession.open(ImapSession.OPEN_MODE_RO);
		
		            	// if the server says we cannot do IDLE we need to abort.
		            	if (!mSession.isIdleCapable()) {
		            		mReceiver.onPushNotSupported(mSession.getFolderName().getName());
		            		break;
		            	}
	
		            	if (isInterrupted()) {
		            		break;
		            	}
		            	
		            	mSemaphore.acquire();
		            	
		            	try { 
			            
		            		if (!mCanConnect) break;
			            		
			                mSession.waitForNewMessages(new IdleListener() {
								
								@Override
								public void onIdleStarted() {
									
									mReceiver.onListeningStarted(mSession.getFolderName().getName());
						        	
									mWakeLock.release();
						      	     
									mSemaphore.release();
															
								}
							});					

		            	} finally {
		            		mSemaphore.release();
		            	}
		            	
		            	// this was a successful attempt to connect, we can reset the 
		            	// delay between attempts
		                mDelayTime.set(ImapStore.NORMAL_DELAY_TIME);
	
		            } catch (Exception e) {
		            	
		            	Log.e(ImapStore.LOG_TAG, "Error while IDLEing", e);
	
		            	// we need to re-acquire the wake lock because it may have been
		            	// released when we started idleing (see the onIdleStarted method)
		            	mWakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT);
		     
	                    int delayTimeInt = mDelayTime.get();
	         
	                    
	                    synchronized (mWaitObject) {
							
	                    	try {
	                    		
	                            if (mCanWait) break;
				              
		                    	mWaitObject.wait(delayTimeInt);
		                    
	                    	} catch (InterruptedException ex) {
		                    	break;
		                    }
		                    
						}
	                    	
	                    delayTimeInt *= 2;
	                    if (delayTimeInt > ImapStore.MAX_DELAY_TIME) {
	                        delayTimeInt = ImapStore.MAX_DELAY_TIME;
	                    }
	                    mDelayTime.set(delayTimeInt);                    
		            }
		            
		        }
		        
	    	} finally {
	    		mWakeLock.release();
	    	}
	    }

		@Override
		public void onIdleStarted() {

			
		}    	
		
	}

}