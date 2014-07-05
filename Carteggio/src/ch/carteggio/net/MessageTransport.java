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

import org.apache.james.mime4j.dom.Message;

import ch.carteggio.provider.CarteggioAccount;

/**
 * 
 * This interface defines an object used to send messages.
 * 
 * Design considerations: This API could allow Carteggio to support different
 * types of server protocols (such as SMTP, Exchange, etc. ) but has been
 * introduced mainly to allow implementation of a mock {@link MessageTransport}
 * that is used for testing of the components that use this this API without
 * having to setup an SMTP server.
 * 
 * 
 */

public interface MessageTransport {

	/**
	 * 
	 * Sends the given message.
	 * 
	 * @param message
	 *            a message to be sent
	 * 
	 * @throws MessagingException
	 */
	public void sendMessage(Message message) throws MessagingException;

	/**
	 * 
	 * Factory used to create a message transport for a given
	 * {@link CarteggioAccount}
	 * 
	 * Design considerations: the factory takes as argument an account object
	 * instead of just a URL because it will probably be necessary to add more
	 * preferences to a given account (such as enabling workarounds for specific
	 * server implementations or enabling certificate based logins). Encoding
	 * all of this inside a URL and parsing it back is cumbersome. Another
	 * immediate advantage is that we can keep URI and password separated.
	 * 
	 */
	public interface Factory {

		/**
		 * 
		 * Creates a new {@link MessageTransport} for the given account.
		 * 
		 * @param account
		 * @return
		 * @throws MessagingException
		 */
		public MessageTransport getMessageTransport(CarteggioAccount account)
				throws MessagingException;

	}

}
