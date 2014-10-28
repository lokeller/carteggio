package ch.carteggio.uitests;

import ch.nodo.multiuiautomator.SdkTools.EmulatorController;

import com.android.uiautomator.testrunner.UiAutomatorTestCase;

public class CarteggioTestCase extends UiAutomatorTestCase {

	@Override
	protected EmulatorController createEmulator(String name) {
		EmulatorController controller = super.createEmulator(name);
		
		String appPath = System.getProperty("APP_APK");
		
		
		assertNotNull("APP_APK property must point to Carteggio APK",
							System.getProperty("APP_APK"));
		
		controller.installApplication(appPath);
		
		return controller;
		
	}

	public void goToCarteggioApp(EmulatorController controller) {
		controller.executeCommand(" am start -n ch.carteggio/.ui.MainActivity");
	}
	
}
