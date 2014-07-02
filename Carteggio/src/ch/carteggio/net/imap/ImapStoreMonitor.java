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
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import ch.carteggio.net.imap.ImapFolderState.ImapFolderListener;
import ch.carteggio.net.imap.ImapIdleSession.IdleListener;

/**
 * 
 * Starts and stops pushers for a list of folders.
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
	    
	    private WakeLock mWakeLock;
		
	    public ListeningThread(ImapIdleSession mSession) {
			this.mSession = mSession;
			
	        PowerManager pm = (PowerManager) mSession.getStore().getContext().getSystemService(Context.POWER_SERVICE);
	        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ImapFolderPusher " + ":" + mSession.getFolderName().getName());
	        mWakeLock.setReferenceCounted(false);
			
		}

	    public void stopWaiting() {
	    	interrupt();
	    	mSession.stopWaiting();
	    }
	    
		public void run() {

	        if (ImapStore.DEBUG) Log.i(ImapStore.LOG_TAG, "Pusher starting");

	    	mWakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT);
	 
	    	try {
	
		        while (!isInterrupted()) {
		        	
		            try {
		            	
		            	mSession.open(ImapSession.OPEN_MODE_RO);
		            	
		            	if (!mSession.isIdleCapable()) {
		            		mReceiver.onPushNotSupported(mSession.getFolderName().getName());
		            		break;
		            	}
	
		            	mSession.waitForNewMessages(this);
		            	
		                mDelayTime.set(ImapStore.NORMAL_DELAY_TIME);
	
		            } catch (Exception e) {
		            	
		            	Log.e(ImapStore.LOG_TAG, "Error while IDLEing", e);
	
		            	mWakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT);
		     
		                if (isInterrupted()) {
		                	break;
		                }
	
	                    int delayTimeInt = mDelayTime.get();
	                    
	                    try {
	                    	Thread.sleep(delayTimeInt);
	                    } catch (InterruptedException ex) {
	                    	break;
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

			mReceiver.onListeningStarted(mSession.getFolderName().getName());
        	
			mWakeLock.release();
      	     
		}    	
		
	}

}