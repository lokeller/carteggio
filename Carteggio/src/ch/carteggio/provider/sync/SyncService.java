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
package ch.carteggio.provider.sync;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;

public class SyncService extends Service {

	
	public static final String LOG_TAG = "SyncService";
	private CarteggioSyncAdapter mAdapter; 
	
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mAdapter = new CarteggioSyncAdapter(this, true);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mAdapter.getSyncAdapterBinder();
	}	

	private class CarteggioSyncAdapter extends AbstractThreadedSyncAdapter {
		
		public CarteggioSyncAdapter(Context context, boolean autoInitialize) {
			super(context, autoInitialize);		
		}

		@Override
		public void onPerformSync(Account account, Bundle extras,
				String authority, ContentProviderClient provider,
				SyncResult syncResult) {
			
			Intent sendIntent = new Intent(getApplicationContext(), MessageSenderService.class);			
			sendIntent.setAction(MessageSenderService.ACTION_SEND);			
			startService(sendIntent);		
			
			Intent receiveIntent = new Intent(getApplicationContext(), MessageReceiverService.class);			
			receiveIntent.setAction(MessageReceiverService.ACTION_POLL);			
			startService(receiveIntent);			
			
						
			
		}
		
		
		
	}
}
