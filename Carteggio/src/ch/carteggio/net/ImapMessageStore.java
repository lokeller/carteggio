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
import java.util.Date;
import java.util.List;

import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;

import android.content.Context;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import ch.carteggio.net.imap.FetchProfile;
import ch.carteggio.net.imap.ImapPreferences;
import ch.carteggio.net.imap.ImapStore;
import ch.carteggio.net.imap.PushReceiver;
import ch.carteggio.net.imap.Pusher;
import ch.carteggio.net.imap.FetchProfile.Item;
import ch.carteggio.net.imap.ImapStore.ImapFolder;
import ch.carteggio.net.imap.ImapStore.ImapMessage;
import ch.carteggio.net.imap.ImapStore.ImapPusher;
import ch.carteggio.provider.CarteggioAccount;

public class ImapMessageStore implements MessageStore {

	private ImapStore mStore;
	
	private static final String LOG_TAG = "ImapMessageStore";
	
	private CarteggioAccount mAccount;
	private Context mContext;
	
	private ArrayList<ImapPusher> mPushers = new ArrayList<ImapPusher>();
		
	private ImapMessageStore(Context context, ImapStore store, CarteggioAccount account) {
		this.mStore = store;
		this.mContext = context;
		this.mAccount = account;
	}

	@Override
	public void addMessageListener(MessageStore.Folder folder, MessageListener listener) {
			
		ImapPusher pusher = mStore.getPusher(new PushReceiverImpl(mContext, listener, (Folder) folder));
		
		List<String> folders = new ArrayList<String>();
		
		folders.add(((Folder) folder).mFolder.getName());
		
		pusher.start(folders);
		
		mPushers.add(pusher);
		
	}


	@Override
	public void removeMessageListeners() {

		for ( Pusher p : mPushers) {
			p.stop();
		}
		
		mPushers.clear();
		
	}

	@Override
	public Folder getInbox() {
	
		ImapFolder imapFolder = mStore.getFolder("INBOX");
				
		return new Folder(imapFolder);
				
	}
	
	@Override
	public Folder getPrivateFolder() {
		
		ImapFolder carteggioFolder = mStore.getFolder("Carteggio");
		
		return new Folder(carteggioFolder);		
		
	}
	
	private class Folder implements MessageStore.Folder {

		private ImapFolder mFolder;
		
		public Folder(ImapFolder folder) {
			this.mFolder = folder;
		}
		
		@Override
		public MessageStore getMessageStore() {
			return ImapMessageStore.this;
		}

		@Override
		public void open() throws MessagingException {
			try {
				mFolder.open(ImapFolder.OPEN_MODE_RW);
			} catch (MessagingException e) {
				throw new MessagingException("Unable to open folder", e);
			}
		}

		@Override
		public Message[] getMessagesAfter(Date date) throws MessagingException {
			
			int count = mFolder.getMessageCount();
					
			try {
								
				ImapMessage[] imapMessages = mFolder.getMessages(1, count, date, null);
				
				// set the push state so that we don't re-download the messages we just downloaded
				for ( ImapMessage message : imapMessages ) {
							
					String pushState = mFolder.getNewPushState(mAccount.getPushState(), message);
							
					if ( pushState != null ) {
						mAccount.setPushState(pushState);
					}
				}
				
				return imapMessages;
				
			} catch (MessagingException e) {
				throw new MessagingException("Unable to retrieve list of new messages", e);
			}
		
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
			ArrayList<ImapMessage> imapMessages = new ArrayList<ImapStore.ImapMessage>();
			
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
			
			try {
			
				ImapStore store = new ImapStore(mContext, account.getIncomingServer(), new ImapPreferences(), account.getIncomingPassword());
	
				return new ImapMessageStore(mContext, store, account);
				
			} catch ( MessagingException ex) {
				
				Log.e(LOG_TAG , "Unable to connect to IMAP server");
				return null;
				
			}
			
		}
		
	}
	
	private class PushReceiverImpl implements PushReceiver {
		
		private Context mContext;
		private MessageListener mMessageListener;
		private Folder mFolder;
				
		public PushReceiverImpl(Context mContext, MessageListener mMessageListener, Folder mFolder) {
			super();
			this.mContext = mContext;
			this.mMessageListener = mMessageListener;
			this.mFolder = mFolder;
		}

		@Override
		public Context getContext() {
			return mContext;
		}
	
		@Override
		public void syncFolder(ImapFolder folder) {
						
			mMessageListener.listenerStarted(mFolder);			
		}
	
		@Override
		public void messagesArrived(ImapFolder folder, List<ImapMessage> mess) {
						
			
			mMessageListener.messagesArrived(mFolder, mess.toArray(new Message[0]));
		}
	
		@Override
		public void messagesFlagsChanged(ImapFolder folder, List<ImapMessage> mess) {}
	
		@Override
		public void messagesRemoved(ImapFolder folder, List<ImapMessage> mess) {}
	
		@Override
		public String getPushState(String folderName) {
			
			return mAccount.getPushState();
			
		}
	
		@Override
		public void pushError(String errorMessage, Exception e) {
			Log.e(LOG_TAG, "Error while waiting for push");
		}
	
		@Override
		public void setPushActive(String folderName, boolean enabled) {
						
		}
		
		@Override
		public void pushNotSupported() {
						
			mMessageListener.listeningNotSupported();
			
		}

		@Override
		public void sleep(WakeLock wakeLock, long millis) {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException e) {}
		}
		
	}
	
}
