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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import android.util.Log;
import ch.carteggio.net.MessagingException;
import ch.carteggio.net.imap.ImapConnection.UntaggedHandler;
import ch.carteggio.net.imap.parsing.ImapResponse;

class ImapIdleSession extends ImapSession {
    
	/** this variable is used to make sure that we send a DONE even if we receiver a stop
	 *  while we are still setting up the idle command. */
    private final AtomicBoolean mStop = new AtomicBoolean(false);

    /** this variable is used to make sure that we don't start idleing multiple times in parallel*/
    private final AtomicBoolean mIdling = new AtomicBoolean(false);
    
    /** this variable is used to make sure that we send at most one DONE when going out of IDLE */
    private final AtomicBoolean mDoneSent = new AtomicBoolean(false);
    
    public ImapIdleSession(ImapStore store, String name) {
        super(store, name);
    }
    
    public void waitForNewMessages(final IdleListener listener) throws MessagingException {

    	if ( mIdling.compareAndSet(false, true)) {
            throw new MessagingException("Already IDLEing");    		
    	}
    	
    	mStop.set(false);
    	mDoneSent.set(false);
    	
        internalOpen(OPEN_MODE_RO);
        
        if (mConnection == null) {
            throw new MessagingException("Could not establish connection for IDLE");

        }

        if (!mConnection.isIdleCapable()) {
            throw new MessagingException("IMAP server is not IDLE capable:" + mConnection.toString());
        }

        try {
        	
	        mConnection.setReadTimeout((mStore.getPreferences().getIdleRefreshMinutes() * 60 * 1000) + ImapStore.IDLE_READ_TIMEOUT_INCREMENT);
	        executeSimpleCommand(ImapStore.COMMAND_IDLE, false, new UntaggedHandler() {
				
				@Override
				public void handleAsyncUntaggedResponse(ImapResponse respose) {
					
					if (respose.mCommandContinuationRequested) {
						
						if (listener != null) listener.onIdleStarted();
						
						if (mStop.get()) {	
							try {
								sendDone();
							} catch ( Exception ex) {
								Log.e(ImapStore.LOG_TAG, "Error while shutting down idle connection", ex);
							}
						}
					}
					
					mState.handleUntaggedResponse(respose);
					
				}
			});
	        
        } catch (IOException ex) {
        	
        	mConnection.close();
        	
        	throw new MessagingException("Error while IDLEing", ex);
        }
        
        mIdling.set(false);

    }
    
    public void stopWaiting() {
    
    	mStop.set(true);
    	
    	try {
			sendDone();
		} catch ( Exception ex) {
			Log.e(ImapStore.LOG_TAG, "Error while shutting down idle connection", ex);
		}
    	
    }
    
    
    private void sendDone() throws IOException, MessagingException {
	    if (mDoneSent.compareAndSet(false, true)) {
	        ImapConnection conn = mConnection;
	        if (conn != null) {
	            conn.setReadTimeout(ImapStore.SOCKET_READ_TIMEOUT);
	            sendContinuation("DONE");
	        }
	
	    }
	}

	private void sendContinuation(String continuation)
	throws IOException {
	    ImapConnection conn = mConnection;
	    if (conn != null) {
	        conn.sendContinuation(continuation);
	    }
	}


	public interface IdleListener {
    	public void onIdleStarted();
    }
    

}