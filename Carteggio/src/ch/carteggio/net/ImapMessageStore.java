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
package ch.carteggio.net;


import java.util.ArrayList;

import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;

import android.content.Context;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import ch.carteggio.net.imap.FetchProfile;
import ch.carteggio.net.imap.FetchProfile.Item;
import ch.carteggio.net.imap.ImapMessage;
import ch.carteggio.net.imap.ImapPreferences;
import ch.carteggio.net.imap.ImapServerSettings;
import ch.carteggio.net.imap.ImapSession;
import ch.carteggio.net.imap.ImapStore;
import ch.carteggio.net.security.AuthType;
import ch.carteggio.net.security.ConnectionSecurity;
import ch.carteggio.provider.CarteggioAccount;

public class ImapMessageStore implements MessageStore {

	private static final String FOLDER_NAME = "INBOX";

	private ImapStore mStore;
	
	private static final String LOG_TAG = "ImapMessageStore";
	
	private ImapMessageStore(Context context, ImapStore store) {
		this.mStore = store;
	}

	@Override
	public Folder getInbox() {
	
		ImapSession imapFolder = mStore.getSession(FOLDER_NAME);
				
		return new Folder(imapFolder);
				
	}
	
	@Override
	public Folder getPrivateFolder() {
		
		ImapSession carteggioFolder = mStore.getSession("Carteggio");
		
		return new Folder(carteggioFolder);		
		
	}
	
	
	
	@Override
	public SynchronizationPoint createSynchronizationPoint(String syncPoint) {
		
		if (syncPoint == null) return new ImapSynchronizationPoint(-1);
		
		try {
			return new ImapSynchronizationPoint(Long.parseLong(syncPoint));
		} catch (Exception ex) {
			return new ImapSynchronizationPoint(-1);
		}
	}

	private class ImapSynchronizationPoint implements SynchronizationPoint {

		long nextMinimumMessageUid;
		
		public ImapSynchronizationPoint(long nextMinimumMessageUid) {
			this.nextMinimumMessageUid = nextMinimumMessageUid;
		}

		public void update(long nextMessageId) {
			nextMinimumMessageUid = Math.max(nextMessageId, nextMinimumMessageUid);
		}

		@Override
		public String save() {
			return Long.toString(nextMinimumMessageUid);
		}
		
	}
	
	private class Folder implements MessageStore.Folder {

		private ImapSession mFolder;
		
		public Folder(ImapSession folder) {
			this.mFolder = folder;
		}
		
		@Override
		public MessageStore getMessageStore() {
			return ImapMessageStore.this;
		}

		@Override
		public void open() throws MessagingException {
			try {
				mFolder.open(ImapSession.OPEN_MODE_RW);
			} catch (MessagingException e) {
				throw new MessagingException("Unable to open folder", e);
			}
		}

		@Override
		public Message[] getMessagesAfter(SynchronizationPoint point) throws MessagingException {
			
			ImapSynchronizationPoint imapSyncPoint = (ImapSynchronizationPoint) point;
			
			try {
				
				// if this is the first time we start carteggio we don't want to load all
				// messages (the mailbox could be huge). Instead we check what is the highest
				// uid and next time we will be able to look for new messages
				if  (imapSyncPoint.nextMinimumMessageUid == -1) {
					
					imapSyncPoint.update(mFolder.getHighestUid() + 1);
					
					return new Message[0];
				} else {
								
					ImapMessage[] imapMessages = mFolder.getMessagesAddedAfter(imapSyncPoint.nextMinimumMessageUid, null);
					
					for ( ImapMessage message : imapMessages ) {
						imapSyncPoint.update(message.getUid() + 1);					
					}
					
					return imapMessages;
				}
				
			} catch (MessagingException e) {
				throw new MessagingException("Unable to retrieve list of new messages", e);
			}
		
		}
		
		@Override
		public void waitForChanges(SynchronizationPoint point, WakeLock wakeLock) throws MessagingException  {
			
			ImapSynchronizationPoint imapSyncPoint = (ImapSynchronizationPoint) point;
			
			// check if there was already a new message coming in from the sync point
			
			long highestUid = mFolder.getHighestUid();
			
			if ( highestUid >= imapSyncPoint.nextMinimumMessageUid) {
				imapSyncPoint.update(highestUid);
				return;
			}
			
			mFolder.waitForChanges(wakeLock);
			
		}
		
		@Override
		public boolean isWaitingForChangedSupported() throws MessagingException {
			return mFolder.isIdleCapable();
		}

		@Override
		public void fetchEnvelopes(Message[] messages) throws MessagingException {
			
			try {
				
				ImapMessage[] imapMessages = toImapMessages(messages);
				
				FetchProfile profile = new FetchProfile();
				
				profile.add(FetchProfile.Item.ENVELOPE);
				
				mFolder.fetch(imapMessages, profile, null);
				
			} catch (MessagingException e) {				
				throw new MessagingException("Unable to fetch envelopes", e);				
			}
		}

		private ImapMessage[] toImapMessages(Message[] messages) {
			ArrayList<ImapMessage> imapMessages = new ArrayList<ImapMessage>();
			
			for ( Message m : messages) {
				imapMessages.add((ImapMessage) m);
			}
			return imapMessages.toArray(new ImapMessage[0]);
		}
		
		@Override
		public void fetchStructures(Message[] messages) throws MessagingException {
			
			FetchProfile profile = new FetchProfile();
			
			profile.add(Item.STRUCTURE);
			
			ImapMessage[] imapMessages = toImapMessages(messages);
			
			try {
				mFolder.fetch(imapMessages, profile, null);
			} catch (MessagingException e) {
				throw new MessagingException("Unable to fetch structures", e);
			}
		}

		
		
		@Override
		public void moveMessages(Message[] messages, MessageStore.Folder destination) throws MessagingException {
									
			try {
				mFolder.moveMessages(toImapMessages(messages), ((Folder) destination).mFolder);
				
				mFolder.expunge();
				
			} catch (MessagingException e) {
				throw new MessagingException("Unable to move messages", e);				
			}
				
		}
		
		@Override
		public void fetchPart(Message msg, Entity entity) throws MessagingException {
			
			try {
			
				mFolder.fetchPart((ImapMessage) msg, entity, null);
				
			} catch (MessagingException e) {
				throw new MessagingException("Unable to move messages", e);				
			}
			
		}

		@Override
		public void close() {			
			mFolder.close();
		}
		
	}	
	

	public static class Factory implements MessageStore.Factory {
	
		private Context mContext;
		
		public Factory(Context context) {
			this.mContext = context;
		}
		
		@Override
		public MessageStore getMessageStore(CarteggioAccount account) {
			
			ImapServerSettings settings = new ImapServerSettings();
				
	        if (account.getIncomingProto().equals("imap")) {
	            settings.mConnectionSecurity = ConnectionSecurity.NONE;
	            settings.mPort = 143;
	        } else if (account.getIncomingProto().startsWith("imap+tls")) {
	        	settings.mConnectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED;
	        	settings.mPort = 143;
	        } else if (account.getIncomingProto().startsWith("imap+ssl")) {
	        	settings.mConnectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED;
	        	settings.mPort = 993;
	        } else {
	            throw new IllegalArgumentException("Unsupported protocol (" + account.getIncomingProto() + ")");
	        }

	        settings.mHost = account.getIncomingHost();
	    	settings.mPort = Integer.parseInt(account.getIncomingPort());
	    	settings.mUsername = account.getIncomingUsername();
	        settings.mPassword = account.getIncomingPassword();
	        
	        settings.mAuthType = AuthType.valueOf(account.getIncomingAuthenticationMethod());
		        
	        boolean autoDetectNamespace = true;
	        
	        String inbox = account.getInboxPath();
	        
	        if (inbox != null && inbox.length() > 0) {
	      
	            if (inbox.length() >= 2 && inbox.charAt(1) == '|') {
	                autoDetectNamespace = inbox.charAt(0) == '1';
	                if (!autoDetectNamespace) {
	                	settings.mPathPrefix = inbox.substring(2);
	                }
	            } else {
	            	settings.mPathPrefix = inbox;
	                autoDetectNamespace = false;
	            }
	        }
	        // Make extra sure mPathPrefix is null if "auto-detect namespace" is configured
	        settings.mPathPrefix = autoDetectNamespace ? null : settings.mPathPrefix;
			
			try {
			
				ImapStore store = new ImapStore(mContext, settings, new ImapPreferences());
	
				return new ImapMessageStore(mContext, store);
				
			} catch ( MessagingException ex) {
				
				Log.e(LOG_TAG , "Unable to connect to IMAP server");
				return null;
				
			}
			
		}
		
	}
		
}
