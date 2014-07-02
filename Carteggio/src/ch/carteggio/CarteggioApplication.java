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
package ch.carteggio;

import android.app.Application;
import ch.carteggio.net.ImapMessageStore;
import ch.carteggio.net.NetworkFactories;
import ch.carteggio.net.SmtpMessageTransport;

public class CarteggioApplication extends Application {

	@Override
	public void onCreate() {	
		super.onCreate();
				
		NetworkFactories.getInstance(getApplicationContext()).registerStoreFactory("imap", new ImapMessageStore.Factory(getApplicationContext()));
		NetworkFactories.getInstance(getApplicationContext()).registerStoreFactory("imap+ssl+", new ImapMessageStore.Factory(getApplicationContext()));
		NetworkFactories.getInstance(getApplicationContext()).registerStoreFactory("imap+tls+", new ImapMessageStore.Factory(getApplicationContext()));
		
		
		NetworkFactories.getInstance(getApplicationContext()).registerTransportFactory("smtp", new SmtpMessageTransport.Factory());

		NetworkFactories.getInstance(getApplicationContext()).registerTransportFactory("smtp+ssl+", new SmtpMessageTransport.Factory());

		NetworkFactories.getInstance(getApplicationContext()).registerTransportFactory("smtp+tls+", new SmtpMessageTransport.Factory());
				
	}

	
}
