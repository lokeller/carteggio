package ch.carteggio.uitests.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import ch.nodo.multiuiautomator.SdkTools.EmulatorController;
import static org.junit.Assert.*;

import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

public class CarteggioTestCase extends UiAutomatorTestCase {

	public CarteggioTestCase() {
		
		InputStream i = null;
		
		try {
		
			i = new FileInputStream("test.properties");
			System.getProperties().load(i);
			
		} catch (FileNotFoundException ex) {
			
			fail("You need to setup the test environment in the test.properties file");
		} catch (IOException e) {
			fail(e.getMessage());
		} finally {
			if (i != null) { try {i.close(); } catch (IOException ex) {}}	
		}
		
		
	}
	
	protected UiDevice setupEmulator(String name) {
		
		
		EmulatorController controller = getEmulatorController(name);
		
		controller.uninstallApplication("ch.carteggio");
		
		String appPath = System.getProperty("app.apk");
		
		assertNotNull("APP_APK property must point to Carteggio APK",
							System.getProperty("app.apk"));
		
		controller.installApplication(appPath);
		
		UiDevice device = controller.getUiDevice();
		
		// go out of lock screen if necessary
		if (device.getCurrentPackageName().equals("com.android.keyguard")) {
			device.pressMenu();
		}
		
		device.waitForIdle();
		
		device.pressHome();
		
		device.waitForIdle();
		
		assertEquals("Unable to go to launcher", 
				"com.android.launcher", device.getCurrentPackageName());
		
		return device;
		
		
	}

}
