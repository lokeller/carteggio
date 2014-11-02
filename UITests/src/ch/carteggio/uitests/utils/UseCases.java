package ch.carteggio.uitests.utils;

import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiObjectNotFoundException;
import com.android.uiautomator.core.UiScrollable;
import com.android.uiautomator.core.UiSelector;
import com.github.uiautomatorstub.Selector;

import static org.junit.Assert.*;
import static ch.carteggio.uitests.utils.DeviceState.*;

public class UseCases {

	private UiDevice mDevice;
	
	private DeviceState mDeviceState;
	
	public UseCases(UiDevice mDevice) {
		this.mDevice = mDevice;
		this.mDeviceState = new DeviceState(mDevice);
	}

	private UiObject findViewById(String id) {
		return new UiObject(mDevice, new UiSelector().resourceId("ch.carteggio:id/" + id));
	}
	
	public DeviceState getState() {
		return mDeviceState;
	}
	
	public void startApplication() {
		
		// no-preconditions
		
		//  start the application with an intent
		mDevice.getEmulatorController().executeCommand(" am start -n ch.carteggio/.ui.MainActivity");
		
		mDeviceState.onAnyCarteggioActivity();
	}
	
	public void acceptEULA() throws Exception {
		
		mDeviceState.onAboutActivityWithEULA();

		// click agree button and wait for new window
		findViewById("agree_button").clickAndWaitForNewWindow();
		
		mDeviceState.onNewAccountActivity();
		
	}

	public void refuseEULA() throws Exception {

		mDeviceState.onAboutActivityWithEULA();
	
		// click on disagree button
		findViewById("disagree_button").clickAndWaitForNewWindow();
		
		mDeviceState.onLauncher();
	}

	public void enterNewEmailAccount(String displayName, String email, String password, boolean autoconfigure) throws Exception{
		
		mDeviceState.onNewAccountActivity();
		
		// insert name
		if ( displayName != null) {
			findViewById("text_name").setText(displayName);
		}
		
		// insert email address
		if (email != null) {
			findViewById("text_email").setText(email);
		}
		
		// password
		if (password != null) {
			findViewById("text_password").setText(password);	
		}
		
		// take away the keyboard
		mDevice.pressBack();
		mDevice.waitForIdle();
		
		// set autoconfigure setup
		UiObject autoconfigureField = findViewById("checkbox_autoconfigure");
		
		if ( autoconfigureField.isChecked() != autoconfigure ) {
			autoconfigureField.click();
			mDevice.waitForIdle();
		}
	
		// click on the accept button
		findViewById("action_accept").clickAndWaitForNewWindow();
		
	}

	
	public void editAccount(String displayName, String incomingServer, String outgoingServer, String incomingPassword, String outgoingPassword) throws Exception {
	
		mDeviceState.onEditAccountActivity();
		
		UiScrollable scrollable = new UiScrollable(mDevice, new UiSelector().resourceId("ch.carteggio:id/account_scroll"));
		
		if ( displayName != null) {
			UiObject editBox = findViewById("account_display_name");
			reliableClear(editBox);
			editBox.setText(displayName);
		}

		if ( incomingServer != null) {
			UiObject editBox = findViewById("account_incoming");
			scrollable.scrollIntoView(editBox);
			reliableClear(editBox);
			scrollable.scrollIntoView(editBox);
			editBox.setText(incomingServer);
		}		

		if ( outgoingServer != null) {
			UiObject editBox = findViewById("account_outgoing");
			scrollable.scrollIntoView(editBox);
			reliableClear(editBox);
			scrollable.scrollIntoView(editBox);
			editBox.setText(outgoingServer);
		}		
		
		if ( incomingPassword != null) {
			UiObject editBox = findViewById("account_incoming_password");
			scrollable.scrollIntoView(editBox);
			reliableClear(editBox);
			scrollable.scrollIntoView(editBox);
			editBox.setText(incomingPassword);
		}		

		if ( outgoingPassword != null) {
			UiObject editBox = findViewById("account_outgoing_password");
			scrollable.scrollIntoView(editBox);
			reliableClear(editBox);
			scrollable.scrollIntoView(editBox);
			editBox.setText(outgoingPassword);
		}		

		// click on the accept button
		findViewById("action_accept").clickAndWaitForNewWindow();
		
	}	

	
	/** 
	 * 
	 *  Unfortunately UI automator clearText is not reliable ( see bug 68564 et al). 
	 *  So we need to implement this ugly hack.
	 *  
	 * @param editBox
	 * @throws UiObjectNotFoundException
	 */
	private void reliableClear(UiObject editBox)
			throws UiObjectNotFoundException {
		
		editBox.clickTopLeft();
		
		// while there is text inside the box (and is not an hint)
		while ( editBox.getText() != "") {			
			mDevice.pressDPadRight();
			
			// this is used to detect when the only 
			// stuff remaining in the box is the hint
			// in that case we go to the previous thing in the activity
			// so we lose the focus and we can even disappear from the
			// screen
			if ( !editBox.exists() || !editBox.isFocused()) {
				break;
			}
			
			mDevice.pressDelete();
		}
	}
	
	
}
