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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import ch.carteggio.R;
import ch.carteggio.provider.CarteggioContract;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.ui.MainActivity;

/**
 * 
 * This service is responsible to show notifications.
 * 
 * Design considerations: the service is very similar to an
 * IntentService. It however is implemented as an extension
 * of Service directly because, differently from IntentServices,
 * we want this service to stay alive to make sure it will be
 * notified of changes in the list of messages.
 * 
 */

public class NotificationService extends Service {

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private static final int ERROR_NOTIFICATION_ID = 1;	
	private static final int INCOMING_NOTIFICATION_ID = 2;	
	
	public static final String UPDATE_SENDING_STATE_ACTION = "ch.carteggio.provider.sync.NotificationService.UPDATE_SENDING_STATE_ACTION";
	public static final String UPDATE_RECEIVING_STATE_ACTION = "ch.carteggio.provider.sync.NotificationService.UPDATE_RECEIVING_STATE_ACTION";
	public static final String UPDATE_UNREAD_STATE_ACTION = "ch.carteggio.provider.sync.NotificationService.UPDATE_UNREAD_STATE_ACTION";

	public static final String FAILURE_EXTRA = "ch.carteggio.provider.sync.NotificationService.SUCCESS_EXTRA";
	private static final String NEW_MESSAGE_EXTRA = "ch.carteggio.provider.sync.NotificationService.NEW_MESSAGE_EXTRA";
	
	private Observer mObserver;
	
	private boolean mSendFailure;
	private boolean mReceiveFailure;
	
	@Override
	public IBinder onBind(Intent intent) { 
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();	
		
		HandlerThread thread = new HandlerThread("NotificationService");
        thread.start();
		
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
        
		mObserver = new Observer();
		
		getContentResolver().registerContentObserver(CarteggioContract.Messages.CONTENT_URI, true, mObserver);
		
	}
	
	@Override
	public void onDestroy() {
		
		getContentResolver().unregisterContentObserver(mObserver);
		
		mServiceLooper.quit();
		
		super.onDestroy();
	}

	protected void onHandleIntent(Intent intent) {
		
		if ( intent != null) {
			
			if ( UPDATE_RECEIVING_STATE_ACTION.equals(intent.getAction())) {
				
				mReceiveFailure = intent.getBooleanExtra(FAILURE_EXTRA, true);
				
			} else if ( UPDATE_SENDING_STATE_ACTION.equals(intent.getAction())) {				
				
				mSendFailure = intent.getBooleanExtra(FAILURE_EXTRA, true);
				
			} else if ( UPDATE_UNREAD_STATE_ACTION.equals(intent.getAction())) {
				
				boolean newMessage = intent.getBooleanExtra(NEW_MESSAGE_EXTRA, false);
				
				updateUnreadNotification(newMessage);
				
			}
			
		}
		
		if ( mSendFailure || mReceiveFailure) {
			showFailureNotification();
		} else {
			hideFailureNotification();
		}
		
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {		
		super.onStartCommand(intent, flags, startId);
		
		Message msg = mServiceHandler.obtainMessage();
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
		
		// we want the service to be sticky to make sure we remember the state
		return START_STICKY;
	}

	private void updateUnreadNotification(boolean newMessage) {

		NotificationManager mNotificationManager =
		        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		
		CarteggioProviderHelper helper = new CarteggioProviderHelper(this);
		
		int unreadCount = helper.getUnreadCount();
		
		if (unreadCount == 0 ) {
			
			mNotificationManager.cancel(INCOMING_NOTIFICATION_ID);
			
		} else {

			String quantityString = getResources().getQuantityString(R.plurals.notification_new_incoming_messages, unreadCount);
			
			PendingIntent intent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), Intent.FLAG_ACTIVITY_SINGLE_TOP);
			
			Notification.Builder mNotifyBuilder = new Notification.Builder(this)
			    .setContentTitle(String.format(quantityString, unreadCount))	    
			    .setSmallIcon(android.R.drawable.stat_notify_chat)
			    .setContentIntent(intent)
			    .setContentText(getString(R.string.notification_text_new_messages));
		
			if ( newMessage ) {
				
				Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				
				AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
				
				long pattern [] = { 1000, 500, 2000 };
				
				if ( manager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
					mNotifyBuilder.setSound(uri);
					mNotifyBuilder.setVibrate(pattern);
				} else if (manager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
					mNotifyBuilder.setVibrate(pattern);
				}
			}
			
			mNotificationManager.notify(INCOMING_NOTIFICATION_ID, mNotifyBuilder.getNotification());

		}
		
		
		
	}
	
	private void hideFailureNotification() {
		
		NotificationManager mNotificationManager =
		        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		mNotificationManager.cancel(ERROR_NOTIFICATION_ID);
	}


	private void showFailureNotification() {

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		
		Notification.Builder mNotifyBuilder = new Notification.Builder(this)
		    .setContentTitle("Carteggio network error")		    
		    .setSmallIcon(android.R.drawable.stat_notify_error);
				
		if ( mSendFailure && mReceiveFailure) {
			mNotifyBuilder.setContentText("There was a problem while delivering and receiving messages");
		} else if ( mSendFailure ) {
			mNotifyBuilder.setContentText("There was a problem while delivering messages");
		} else if (mReceiveFailure) {
			mNotifyBuilder.setContentText("There was a problem while receiving messages");
		}
		
		mNotificationManager.notify(ERROR_NOTIFICATION_ID, mNotifyBuilder.getNotification());
		
	}

	public static void setSendingError(Context c, boolean error) {
		
		Intent service = new Intent(c, NotificationService.class);			
		service.setAction(UPDATE_SENDING_STATE_ACTION);
		service.putExtra(FAILURE_EXTRA, error);
		
		c.startService(service);
		
	}
	
	public static void setReceivingError(Context c, boolean error) {
		
		Intent service = new Intent(c, NotificationService.class);			
		service.setAction(UPDATE_RECEIVING_STATE_ACTION);
		service.putExtra(FAILURE_EXTRA, error);
		
		c.startService(service);
	
	}
	
	public static void notifyNewIncomingMessages(Context c) {
	
		
		Intent service = new Intent(c, NotificationService.class);			
		service.setAction(UPDATE_UNREAD_STATE_ACTION);
		service.putExtra(NEW_MESSAGE_EXTRA, true);
		c.startService(service);
		
	}

	public static void updateUnreadNotification(Context c) {
		
		Intent service = new Intent(c, NotificationService.class);			
		service.setAction(UPDATE_UNREAD_STATE_ACTION);
		
		c.startService(service);
	
	}

	private final class ServiceHandler extends Handler {
	    
		public ServiceHandler(Looper looper) {
	        super(looper);
	    }
	
	    @Override
	    public void handleMessage(Message msg) {
	        onHandleIntent((Intent)msg.obj);
	    }
	}

	private class Observer extends ContentObserver {
	
		public Observer() {
			super(new Handler());
		}
	
		@Override
		public void onChange(boolean selfChange) {
			
			// we send an intent here because we don't want to do database operations in the main thread
			updateUnreadNotification(NotificationService.this);
		}
		
	}
	
}
