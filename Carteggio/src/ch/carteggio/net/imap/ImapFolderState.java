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
 *    
 */
package ch.carteggio.net.imap;

import java.util.List;

import ch.carteggio.net.imap.parsing.ImapList;
import ch.carteggio.net.imap.parsing.ImapResponse;
import ch.carteggio.net.imap.parsing.ImapResponseParser;

public class ImapFolderState {

	public interface ImapFolderListener {
		
		public void onFolderChanged(String folderName);
		
	}
	
	private String mName;
	
	private ImapFolderListener mListener;
	
	private volatile int mMessageCount = -1;

	private volatile long mUidNext = -1L;
	
	public ImapFolderState(String mName) {
		this.mName = mName;
	}

	public int getMessageCount() {
		return mMessageCount;
	}
	
	public long getUidNext() {
		return mUidNext;
	}

	public void invalidate() {
		mMessageCount = -1;
	}
	
	public void setListener(ImapFolderListener listener) {
		mListener = listener;
	}
	
	/**
	 * Handles any untagged responses received from server.
	 * 
	 * The server sends untagged responses to notify changes in the folder state.
	 * This function goes through a list of untagged responses and updates the folder
	 * state accordingly.
	 * 
	 */
	List<ImapResponse> handleUntaggedResponses(
			List<ImapResponse> responses) {
		for (ImapResponse response : responses) {
			handleUntaggedResponse(response);
		}
		return responses;
	}

	/**
	 * Handles any untagged responses received from server.
	 * 
	 * The server sends untagged responses to notify changes in the folder state.
	 * This function goes through a list of untagged responses and updates the folder
	 * state accordingly.
	 * 
	 */
	void handleUntaggedResponse(ImapResponse response) {
		
		if (response.mTag == null && response.size() > 1) {
			
			if (ImapResponseParser.equalsIgnoreCase(response.get(1), "EXISTS")) {
				
				mMessageCount = response.getNumber(0);
				
				if (mListener != null) mListener.onFolderChanged(mName);
				
			}
			
			handlePossibleUidNext(response);
	
			if (ImapResponseParser.equalsIgnoreCase(response.get(1), "EXPUNGE")
					&& mMessageCount > 0) {
				
				mMessageCount--;
			
				if (mListener != null) mListener.onFolderChanged(mName);
				
			}
	
	        if (ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
	      
	        	//int msgSeq = (int) response.getLong(0);
	
	        	if (mListener != null) mListener.onFolderChanged(mName);
				
	        }
	
			
		}
	
	}

	/**
	 * 
	 * The server can notify what is the next uid that will be used.
	 * 
	 * This function finds the NEXTUID response and updates the folder state
	 * accordingly.
	 * 
	 */
	private void handlePossibleUidNext(ImapResponse response) {
		if (ImapResponseParser.equalsIgnoreCase(response.get(0), "OK")
				&& response.size() > 1) {
			Object bracketedObj = response.get(1);
			if (bracketedObj instanceof ImapList) {
				ImapList bracketed = (ImapList) bracketedObj;

				if (bracketed.size() > 1) {
					Object keyObj = bracketed.get(0);
					if (keyObj instanceof String) {
						String key = (String) keyObj;
						if ("UIDNEXT".equalsIgnoreCase(key)) {
							mUidNext = bracketed.getLong(1);
	
							if (mListener != null) mListener.onFolderChanged(mName);
						}
					}
				}

			}
		}
	}

	public void updateNextUid(long nextUid) {
		mUidNext = nextUid;
	}	
	
}
