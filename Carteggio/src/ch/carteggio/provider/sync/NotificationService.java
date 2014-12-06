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
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import ch.carteggio.R;
import ch.carteggio.provider.CarteggioContract;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.ui.MainActivity;
import ch.carteggio.ui.NetworkStatusActivity;

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
	
	private static final String UPDATE_SENDING_STATE_ACTION = "ch.carteggio.provider.sync.NotificationService.UPDATE_SENDING_STATE_ACTION";
	private static final String UPDATE_RECEIVING_STATE_ACTION = "ch.carteggio.provider.sync.NotificationService.UPDATE_RECEIVING_STATE_ACTION";
	private static final String UPDATE_UNREAD_STATE_ACTION = "ch.carteggio.provider.sync.NotificationService.UPDATE_UNREAD_STATE_ACTION";

	private static final String FAILURE_MESSAGE_EXTRA = "ch.carteggio.provider.sync.NotificationService.FAILURE_MESSAGE_EXTRA";
	private static final String FAILURE_EXTRA = "ch.carteggio.provider.sync.NotificationService.SUCCESS_EXTRA";
	private static final String NEW_MESSAGE_EXTRA = "ch.carteggio.provider.sync.NotificationService.NEW_MESSAGE_EXTRA";
		
	private static final long DISCONNECTION_TIME_THRESHOLD = 30 * 1000;
	
	/**
	 * 
	 * An intent with this action is broadcasted to inform the UI that the network state has changed. This allows us
	 * to update the screen as soon as it changes.
	 * 
	 */
	public static final String NETWORK_STATE_CHANGED_ACTION = "ch.carteggio.provider.sync.NotificationService.NETWORK_STATE_CHANGED_ACTION";
	
	private Observer mObserver;
	
	private boolean mSendFailure;
	private boolean mReceiveFailure;
	
	private long mLastSendSuccessTime;
	private long mLastReceiveSuccessTime;
	
	private String mSendMessage;
	private String mReceiveMessage;
	
	@Override
	public IBinder onBind(Intent intent) { 
		return new Binder();
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
				
				mReceiveMessage = intent.getStringExtra(FAILURE_MESSAGE_EXTRA);
				
				if (!mReceiveFailure) {
					mLastReceiveSuccessTime = SystemClock.elapsedRealtime();
				}
				
			} else if ( UPDATE_SENDING_STATE_ACTION.equals(intent.getAction())) {				
				
				mSendFailure = intent.getBooleanExtra(FAILURE_EXTRA, true);
				
				mSendMessage = intent.getStringExtra(FAILURE_MESSAGE_EXTRA);
				
				if (!mSendFailure) {
					mLastSendSuccessTime = SystemClock.elapsedRealtime();
				}
				
			} else if ( UPDATE_UNREAD_STATE_ACTION.equals(intent.getAction())) {
				
				boolean newMessage = intent.getBooleanExtra(NEW_MESSAGE_EXTRA, false);
				
				updateUnreadNotification(newMessage);
				
			}
			
		}
		
		// update the notifications
		if ( (mSendFailure && 
				SystemClock.elapsedRealtime() - mLastSendSuccessTime > DISCONNECTION_TIME_THRESHOLD) ||
			 ( mReceiveFailure && 
				SystemClock.elapsedRealtime() - mLastReceiveSuccessTime > DISCONNECTION_TIME_THRESHOLD)) {
			showFailureNotification();
		} else {
			hideFailureNotification();
		}
		
		// inform UI of the changes
		broadcastNetworkStateChange();
		
		
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
						
			NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(this)
			    .setContentTitle(String.format(quantityString, unreadCount))	    
			    .setSmallIcon(android.R.drawable.stat_notify_chat)			    
			    .setContentText(getString(R.string.notification_text_new_messages));
		
			Intent resultIntent = new Intent(this, MainActivity.class);
						
			TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
			stackBuilder.addParentStack(MainActivity.class);			
			stackBuilder.addNextIntent(resultIntent);

			PendingIntent resultPendingIntent = stackBuilder.getPendingIntent( 0, PendingIntent.FLAG_UPDATE_CURRENT);

			notifyBuilder.setContentIntent(resultPendingIntent);
			
			if ( newMessage ) {
				
				Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				
				AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
				
				long pattern [] = { 1000, 500, 2000 };
				
				if ( manager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
					notifyBuilder.setSound(uri);
					notifyBuilder.setVibrate(pattern);
				} else if (manager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
					notifyBuilder.setVibrate(pattern);
				}
			}
			
			mNotificationManager.notify(INCOMING_NOTIFICATION_ID, notifyBuilder.build());

		}
		
		
		
	}
	
	private void hideFailureNotification() {
		
		NotificationManager mNotificationManager =
		        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		mNotificationManager.cancel(ERROR_NOTIFICATION_ID);
	}


	private void showFailureNotification() {

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		
		NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(this)
		    .setContentTitle("Carteggio network error")		    
		    .setSmallIcon(android.R.drawable.stat_notify_error);

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		stackBuilder.addParentStack(NetworkStatusActivity.class);			
		stackBuilder.addNextIntent(new Intent(this, NetworkStatusActivity.class));
		
		notifyBuilder.setContentIntent(stackBuilder.getPendingIntent( 0, PendingIntent.FLAG_UPDATE_CURRENT));
		
		if ( mSendFailure && mReceiveFailure) {
			notifyBuilder.setContentText("There was a problem while delivering and receiving messages");
		} else if ( mSendFailure ) {
			notifyBuilder.setContentText("There was a problem while delivering messages");
		} else if (mReceiveFailure) {
			notifyBuilder.setContentText("There was a problem while receiving messages");
		}
		
		mNotificationManager.notify(ERROR_NOTIFICATION_ID, notifyBuilder.build());
		
	}
	
	private void broadcastNetworkStateChange() {
		
		Intent intent = new Intent(NETWORK_STATE_CHANGED_ACTION);
		
		sendBroadcast(intent);
		
	}

	public static void setSendingError(Context c, boolean error, String message) {
		
		Intent service = new Intent(c, NotificationService.class);			
		service.setAction(UPDATE_SENDING_STATE_ACTION);
		service.putExtra(FAILURE_EXTRA, error);
		
		if ( error ) {
			service.putExtra(FAILURE_MESSAGE_EXTRA, message);
		}
		
		c.startService(service);
		
	}
	
	public static void setReceivingError(Context c, boolean error, String message) {
		
		Intent service = new Intent(c, NotificationService.class);			
		service.setAction(UPDATE_RECEIVING_STATE_ACTION);
		service.putExtra(FAILURE_EXTRA, error);
		
		if ( error ) {
			service.putExtra(FAILURE_MESSAGE_EXTRA, message);
		}
		
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
	
	public class Binder extends android.os.Binder {
		
		public boolean isOutgoingMessagesFailure() {
			return mSendFailure;
		}
		
		public boolean isIncomingMessagesFailure() {
			return mReceiveFailure;
		}
		
		public String getOutgoingMessageError() {
			return mSendMessage;
		}
		
		public String getIncomingMessageError() {
			return mReceiveMessage;
		}
		
	}
	
}
