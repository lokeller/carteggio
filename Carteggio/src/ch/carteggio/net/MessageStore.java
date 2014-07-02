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

import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;

import ch.carteggio.provider.CarteggioAccount;


public interface MessageStore {

	public Folder getInbox();

	public Folder getPrivateFolder();

	public void addMessageListener(Folder folder, FolderListener listener);
	
	public void removeMessageListeners();
	
	public SynchronizationPoint parseSynchronizationPoint(String syncPoint);
	
	public interface FolderListener {
		
		public void onFolderChanged(Folder folder);
		
		public void onListeningNotSupported();

		public void onListeningStarted(Folder folder);
		
	}
	
	public interface SynchronizationPoint {
		
		public String save();
		
	}
	
	public interface Folder {

		public MessageStore getMessageStore();
		
		public Message[] getMessagesAfter(SynchronizationPoint point) throws MessagingException ;

		public void open() throws MessagingException;
		
		public void close();

		public void fetchEnvelopes(Message[] messages) throws MessagingException;

		public void fetchStructures(Message[] messages) throws MessagingException;

		public void moveMessages(Message[] messages, Folder destination) throws MessagingException;

		public void fetchPart(Message msg, Entity entity) throws MessagingException;
		
	}
		
	public interface Factory {

		public MessageStore getMessageStore(CarteggioAccount account) throws MessagingException;
		
	}

	
}
