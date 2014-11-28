package ch.carteggio.uitests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiSelector;

public class DeviceState {

	private UiDevice mDevice;
	
	public DeviceState(UiDevice device) {
		this.mDevice = device;
	}

	public void onAboutActivityWithEULA() {
		
		// on the about activity
		UiObject actionBar = new UiObject(mDevice, new UiSelector().text("About"));
		assertTrue("Not on About activity", actionBar.exists());
		
		// disagree button exists
		UiObject disagreeButton = new UiObject(mDevice, new UiSelector().resourceId("ch.carteggio:id/disagree_button"));
		assertTrue("Disagree button missing", disagreeButton.exists());

		// agree button exists
		UiObject agreeButton = new UiObject(mDevice, new UiSelector().resourceId("ch.carteggio:id/agree_button"));
		assertTrue("Agree button missing", agreeButton.exists());
		
		
	}
	
	public void onNewAccountActivity() {
		
		// on the new account activity
		UiObject actionBar = new UiObject(mDevice, new UiSelector().text("Setup Carteggio"));
		assertTrue("Not on New account activity", actionBar.exists());
		
	}
	
	public void onAnyCarteggioActivity() {
		assertEquals("Not on a carteggio activity", 
				"ch.carteggio", mDevice.getCurrentPackageName());
	}

	public void onLauncher() {
		assertEquals("Not on launcher", 
				"com.android.launcher", mDevice.getCurrentPackageName());
	
	}

	public void onMainActivity() {
		UiObject actionBar = new UiObject(mDevice, new UiSelector().text("Carteggio"));
		assertTrue("Not on main activity", actionBar.exists());
	}

	public void onEditAccountActivity() {
		UiObject actionBar = new UiObject(mDevice, new UiSelector().text("Edit account"));
		assertTrue("Not on edit account activity", actionBar.exists());
	}

	public void onConversationActivity(String name) {
		UiObject outgoingMessage = new UiObject(mDevice, new UiSelector().resourceId("ch.carteggio:id/outgoingMessage"));
		assertTrue("Not on the conversation activity", outgoingMessage.exists());
		
		if ( name != null ) {
			UiObject actionBar = new UiObject(mDevice, new UiSelector().text(name));
			assertTrue("Not on the correct conversation", actionBar.exists());
		}
			
	}
	
}
