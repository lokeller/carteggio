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

import java.util.HashMap;

import android.content.Context;
import android.net.Uri;

import ch.carteggio.provider.CarteggioAccount;

public class NetworkFactories {

	private HashMap<String, MessageStore.Factory> mStoreFactories = new HashMap<String, MessageStore.Factory>();
	private HashMap<String, MessageTransport.Factory> mTransportFactories = new HashMap<String, MessageTransport.Factory>();
	
	
	private static NetworkFactories mInstance;
	
	private NetworkFactories(Context context) {
	
		registerStoreFactory("imap", new ImapMessageStore.Factory(context));
		registerStoreFactory("imap+tls", new ImapMessageStore.Factory(context));
		registerStoreFactory("imap+ssl", new ImapMessageStore.Factory(context));
		registerTransportFactory("smtp", new SmtpMessageTransport.Factory());
		registerTransportFactory("smtp+ssl", new SmtpMessageTransport.Factory());
		registerTransportFactory("smtp+ssl", new SmtpMessageTransport.Factory());
		
	}	
	
	public void registerStoreFactory(String scheme, MessageStore.Factory factory) {
		mStoreFactories.put(scheme, factory);
	}
	
	public void registerTransportFactory(String scheme, MessageTransport.Factory factory) {
		mTransportFactories.put(scheme, factory);
	}
	
	public static NetworkFactories getInstance(Context context) {
		
		if ( mInstance == null) {			
			mInstance = new NetworkFactories(context.getApplicationContext());			
		}
		
		return mInstance;
		
	}
	
	public MessageTransport getMessageTransport(CarteggioAccount account) throws MessagingException {
		
		MessageTransport.Factory factory = mTransportFactories.get(account.getOutgoingProto());
				
		if ( factory == null ) {
			throw new MessagingException("No factory for protocol " + account.getOutgoingProto());
		}
				
		return factory.getMessageTransport(account);
				
	}
	
	public MessageStore getMessageStore(CarteggioAccount account) throws MessagingException {
		
		MessageStore.Factory factory = mStoreFactories.get(account.getIncomingProto());
				
		if ( factory == null ) {
			throw new MessagingException("No factory for protocol " + account.getIncomingProto());
		}
				
		return factory.getMessageStore(account);
				
	}
	
}
