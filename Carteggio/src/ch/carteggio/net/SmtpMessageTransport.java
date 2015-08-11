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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;

import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageWriter;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.DefaultMessageWriter;

import ch.carteggio.net.security.AuthType;
import ch.carteggio.net.security.ConnectionSecurity;
import ch.carteggio.net.smtp.SmtpMessage;
import ch.carteggio.net.smtp.SmtpServerSettings;
import ch.carteggio.net.smtp.SmtpTransport;
import ch.carteggio.net.smtp.SmtpMessage.Encoding;
import ch.carteggio.provider.CarteggioAccount;

public class SmtpMessageTransport implements MessageTransport {

	private SmtpTransport mTransport;
	
	public SmtpMessageTransport(SmtpTransport transport) {
		mTransport = transport;
	}


	@Override
	public void sendMessage(Message message) throws MessagingException {

		String fromAddress = message.getFrom().get(0).getAddress();
		
		ArrayList<String> destinationAddresses = new ArrayList<String>();
		
		for ( Address a : message.getTo()) {
			
			if ( ! (a instanceof Mailbox) ) continue;
			
			destinationAddresses.add( ( (Mailbox) a).getAddress());
		}
		
		MessageWriter writer = new DefaultMessageWriter();
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		try {
			writer.writeMessage(message, out);
		} catch (IOException e) {
			throw new MessagingException("Error while serializing message", e);
		}
		
		message.dispose();
		
		SmtpMessage sm = new SmtpMessage(fromAddress, destinationAddresses.toArray(new String[0]), out.toByteArray(), Encoding.ENCODING_7BIT);
		
		mTransport.sendMessageTo(sm);
		
	}
	
	public static class Factory implements MessageTransport.Factory {

		@Override
		public MessageTransport getMessageTransport(CarteggioAccount account) throws MessagingException {
			
			SmtpServerSettings settings = new SmtpServerSettings();
			
	        settings.mAuthType = AuthType.valueOf(account.getOutgoingAuthenticationMethod());
	        
	        settings.mUsername = account.getOutgoingUsername();
	        settings.mPassword = account.getOutgoingPassword();
	        if (account.getOutgoingProto().equals("smtp")) {
	        	settings.mConnectionSecurity = ConnectionSecurity.NONE;
	        	settings.mPort = 25;
	        } else if (account.getOutgoingProto().startsWith("smtp+tls")) {
	        	settings.mConnectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED;
	        	settings.mPort = 587;
	        } else if (account.getOutgoingProto().startsWith("smtp+ssl")) {
	        	settings.mConnectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED;
	        	settings.mPort = 465;
	        } else {
	            throw new IllegalArgumentException("Unsupported protocol (" + account.getOutgoingProto()+ ")");
	        }

	        settings.mHost = account.getOutgoingHost();

	        settings.mPort = Integer.parseInt(account.getOutgoingPort());
	        
	        settings.mUsername = account.getOutgoingUsername();
			
			SmtpTransport transport = new SmtpTransport(settings);
			
			return new SmtpMessageTransport(transport);
		}
		
		
	}
	
	
}
