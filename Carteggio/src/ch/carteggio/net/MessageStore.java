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

import android.os.PowerManager.WakeLock;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioContract.Messages;

/**
 * 
 * This interface defines an object used to store messages online.
 * 
 * The message store contains two folders, one where the incoming messages are
 * received (the inbox) and one where old carteggio messages can be stored (the
 * private folder). These two folders can be accessed using a {@link Folder}
 * objects (see {@link Folder}).
 * 
 * To create a store one need to use a {@link Factory}. A factory is able to
 * build a {@link MessageStore} object connected to a specific {@link CarteggioAccount}.
 * 
 * Design considerations: This API could allow Carteggio to support different
 * types of server protocols (such as IMAP, Exchange, etc. ) but has been
 * introduced mainly to allow implementation of a mock MessageStore that is used
 * for testing of the components that use this this API without having to setup
 * an IMAP server.
 * 
 */

public interface MessageStore {

	/**
	 * 
	 * Returns a {@link Folder} where the message store publishes new messages.
	 * 
	 * @return
	 */
	public Folder getInbox();

	/**
	 * Returns a {@link Folder} where carteggio can store private data.
	 * 
	 * @return
	 */
	public Folder getPrivateFolder();

	/**
	 * Creates a {@link SynchronizationPoint} for folders in this store from its
	 * textual representation. This function always returns a
	 * {@link SynchronizationPoint}, if the input is null or invalid a
	 * {@link SynchronizationPoint} at the beginning of times is returned.
	 * 
	 * @param syncPoint
	 *            a string created with {@link SynchronizationPoint#save()}
	 * 
	 * @return
	 */
	public SynchronizationPoint createSynchronizationPoint(String syncPoint);

	/**
	 * 
	 * A {@link SynchronizationPoint} represent a point in time where the state
	 * of a {@link Folder} has been checked. Such object can be used to retrieve
	 * all new messages that appeared in a folder after last check.
	 * 
	 */
	public interface SynchronizationPoint {

		/**
		 * Returns a textual representation of the {@link SynchronizationPoint}
		 * that can be later de-serialized with
		 * {@link MessageStore#createSynchronizationPoint(String)}.
		 * 
		 * This function can be used when a sync point needs to be saved to
		 * persistent storage
		 * 
		 * @return a String representation of the {@link SynchronizationPoint}
		 */
		public String save();

	}

	/**
	 * 
	 * Represents a folder in the Message store.
	 * 
	 * To use a folder you must first open it with {@link #open()}. Once opened
	 * you can list message, fetch their structure and their parts. Moreover you
	 * can move the messages to other folders.
	 * 
	 * Make sure that when you are done using a folder you call {@link #close()
	 * to release the associated resources.
	 * 
	 * The Folder object is <b>not threadsafe</b>, make sure only one thread at
	 * a time uses it. The only exception is the method close() that can be
	 * called while the method #waitForChanges(SynchronizationPoint, WakeLock)
	 * is blocking. *
	 */
	public interface Folder {

		/**
		 * 
		 * Returns the {@link MessageStore} this Folder belongs to.
		 * 
		 * @return
		 */
		public MessageStore getMessageStore();

		/**
		 * 
		 * Retrieve all messages added to the folder after the specified
		 * {@link SynchronizationPoint}. The function updates the
		 * synchronization point passed as parameter so that a next call to the
		 * function with the same {@link SynchronizationPoint} object will
		 * return only messages added to the folder between the two calls.
		 * 
		 * This function must be called only on open folders.
		 * 
		 * @param point
		 *            a {@link SynchronizationPoint} that will be updated
		 * 
		 * @return a list of {@link Messages} where only the message id has been
		 *         loaded
		 * 
		 * @throws MessagingException
		 */
		public Message[] getMessagesAfter(SynchronizationPoint point)
				throws MessagingException;

		/**
		 * 
		 * Opens the folder. This acquires some resources (such as network
		 * connections) that need to be released manually with a call to
		 * {@link #close()}
		 * 
		 * @throws MessagingException
		 */
		public void open() throws MessagingException;

		/**
		 * Closes the folder and releases associated resources.
		 * 
		 */
		public void close();

		/**
		 * Retrieves the envelope of the messages. The envelope typically
		 * contains sender, receiver and other message headers.
		 * 
		 * This function updates the {@link Message} objects passed as
		 * arguments.
		 * 
		 * This function must be called only on open folders.
		 * 
		 * @param messages
		 *            a list of messages (usually retrieved with
		 *            {@link #getMessagesAfter(SynchronizationPoint)}
		 * 
		 * @throws MessagingException
		 */
		public void fetchEnvelopes(Message[] messages)
				throws MessagingException;

		/**
		 * Retrieves the structure of messages. This allows to find out what
		 * parts are contained in the message and loads the headers for each
		 * part.
		 * 
		 * This function updates the {@link Message} objects passed as
		 * arguments.
		 * 
		 * This function must be called only on open folders.
		 * 
		 * @param messages
		 *            a list of messages (usually retrieved with
		 *            {@link #getMessagesAfter(SynchronizationPoint)}
		 * 
		 * @throws MessagingException
		 */
		public void fetchStructures(Message[] messages)
				throws MessagingException;

		/**
		 * Moves a message from one folder to another.
		 * 
		 * This function must be called only on open folders.
		 * 
		 * @param messages
		 *            messages to be moved
		 * @param destination
		 *            the destination folder, it is not necessary that the
		 *            folder is open.
		 * @throws MessagingException
		 */
		public void moveMessages(Message[] messages, Folder destination)
				throws MessagingException;

		/**
		 * 
		 * Downloads a part of the message. To get a list of parts of the
		 * message use the method {@link #fetchStructures(Message[])}.
		 * 
		 * This function updates the {@link Message} objects passed as
		 * arguments.
		 * 
		 * This function must be called only on open folders.
		 * 
		 * @param msg
		 *            the message that should for which we want to download a
		 *            part
		 * @param entity
		 *            the part that has to be downloaded
		 * @throws MessagingException
		 */
		public void fetchPart(Message msg, Entity entity)
				throws MessagingException;

		/**
		 * 
		 * This method blocks until the content of the folder has changed from
		 * the state at the specified syncpoint.
		 * 
		 * The method takes a wakeLock as argument. This wake lock must be held
		 * at the moment it is passed to the function. The function will release
		 * it while waiting for messages and re-acquire it when a change
		 * happens. This wakeLock is used to make sure the device cannot go to
		 * sleep while the message waiting is being setup and that it can sleep
		 * while the waiting is happening.
		 * 
		 * Some servers do not support waiting for changes, if it is the case a
		 * {@link MessagingException} will be raised. To check if the server
		 * supports waiting call the function
		 * {@link #isWaitingForChangedSupported()}.
		 * 
		 * This function must be called only on open folders.
		 * 
		 * @param point
		 *            the function must return when the state of the folder
		 *            changes from the state at this
		 *            {@link SynchronizationPoint}
		 * 
		 * @param wakeLock
		 *            a {@link WakeLock} currently held, or null if no wake lock
		 *            should be managed.
		 * @throws MessagingException
		 */
		public void waitForChanges(SynchronizationPoint point, WakeLock wakeLock)
				throws MessagingException;

		/**
		 * 
		 * Returns true if the server supports
		 * {@link #waitForChanges(SynchronizationPoint, WakeLock)}.
		 * 
		 * This function must be called only on open folders.
		 * 
		 * @return
		 * 
		 * @throws MessagingException
		 */
		public boolean isWaitingForChangedSupported() throws MessagingException;
	}

	/**
	 * This factory is used to create {@link MessageStore} objects for a given 
	 * {@link CarteggioAccount}.
	 * 
	 * Design considerations: the factory takes as argument an account object
	 * instead of just a URL because it will probably be necessary to add more
	 * preferences to a given account (such as enabling workarounds for specific
	 * server implementations or enabling certificate based logins). Encoding all 
	 * of this inside a URL and parsing it back is cumbersome. Another immediate
	 * advantage is that we can keep URI and password separated.
	 * 
	 */
	public interface Factory {

		/**
		 * 
		 * Returns a message store specified specified in a given account.
		 * 
		 * @param account the account for which we want to create the {@link MessageStore}
		 * 
		 * @return
		 * 
		 * @throws MessagingException
		 */
		public MessageStore getMessageStore(CarteggioAccount account)
				throws MessagingException;

	}

}
