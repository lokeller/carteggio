package ch.carteggio.ui;

import org.acra.ACRA;

import ch.carteggio.R;
import ch.carteggio.provider.AuthenticatorService;
import ch.carteggio.provider.CarteggioAccount;
import ch.carteggio.provider.CarteggioProviderHelper;
import ch.carteggio.provider.sync.NotificationService;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;

public class NetworkStatusActivity extends Activity implements OnClickListener {

	private View mOuterLayer;
	private ProgressBar mProgress;
	private TextView mIncomingState;
	private TextView mIncomingMessages;
	private TextView mOutgoingState;
	private TextView mOutgoingMessages;
	
	private NotificationService.Binder mBinder;

	private BroadcastReceiver mNetworkStateChanged = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			updateState();
		}
		
	};
	
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {

			mProgress.setVisibility(View.GONE);
			mOuterLayer.setVisibility(View.VISIBLE);

			mBinder = (NotificationService.Binder) service;
			
			updateState();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

			mProgress.setVisibility(View.VISIBLE);
			mOuterLayer.setVisibility(View.GONE);

			mBinder = null;
			
		}
		
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_network_status);
		
		mOuterLayer = findViewById(R.id.layout_outer);
		mProgress = (ProgressBar) findViewById(R.id.progress_loading);
		mIncomingState = (TextView) findViewById(R.id.text_incoming);
		mOutgoingState = (TextView) findViewById(R.id.text_outgoing);
		mIncomingMessages = (TextView) findViewById(R.id.text_incoming_errors);
		mOutgoingMessages = (TextView) findViewById(R.id.text_outgoing_errors);

		mProgress.setVisibility(View.VISIBLE);
		mOuterLayer.setVisibility(View.GONE);

		findViewById(R.id.configure_button).setOnClickListener(this);
		findViewById(R.id.report_problem_button).setOnClickListener(this);
		
		bindService(new Intent(this, NotificationService.class), mConnection, BIND_AUTO_CREATE);

		registerReceiver(mNetworkStateChanged, new IntentFilter(NotificationService.NETWORK_STATE_CHANGED_ACTION));
		
	}
	
	private void updateState() {
		
		if (mBinder == null) return;
		
		CarteggioProviderHelper helper = new CarteggioProviderHelper(this);
		
		CarteggioAccount account = helper.getDefaultAccount();
		
		mOutgoingState.setText(account.getOutgoingHost());
		mIncomingState.setText(account.getIncomingHost());
		
		Drawable iconOk = getResources().getDrawable(android.R.drawable.presence_online);
		Drawable iconNok = getResources().getDrawable(android.R.drawable.presence_busy);
		
		if ( mBinder.isIncomingMessagesFailure()) {
			mIncomingState.setCompoundDrawablesWithIntrinsicBounds(null, null, iconNok, null);
			mIncomingMessages.setText(mBinder.getIncomingMessageError());
		} else {
			mIncomingState.setCompoundDrawablesWithIntrinsicBounds(null, null, iconOk, null);
			mIncomingMessages.setText(getString(R.string.label_state_no_errors));
		}

		if ( mBinder.isOutgoingMessagesFailure()) {
			mOutgoingState.setCompoundDrawablesWithIntrinsicBounds(null, null, iconNok, null);
			mOutgoingMessages.setText(mBinder.getOutgoingMessageError());
		} else {
			mOutgoingState.setCompoundDrawablesWithIntrinsicBounds(null, null, iconOk, null);
			mOutgoingMessages.setText(getString(R.string.label_state_no_errors));
		}
		
	}

	@Override
	protected void onDestroy() {
	
		unregisterReceiver(mNetworkStateChanged);
		unbindService(mConnection);
		super.onDestroy();
	
	}

	@Override
	public void onClick(View v) {

		if (v.getId() == R.id.configure_button) {
	
			AccountManager accountManager = AccountManager.get(this);
			
			Account[] accountsByType = accountManager.getAccountsByType(AuthenticatorService.ACCOUNT_TYPE);
			
			if (accountsByType.length > 0) { 
			
				Intent intent = new Intent(this, EditAccountActivity.class);
				
				intent.putExtra("account", accountsByType[0]);
				
				startActivity(intent);
				
			}
			
		} else if (v.getId() == R.id.report_problem_button) {
			ACRA.getErrorReporter().handleSilentException(null);
		} 
		
	}
	
	
		
}
