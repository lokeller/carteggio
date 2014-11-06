package ch.carteggio.uitests;


import ch.carteggio.uitests.utils.CarteggioTestCase;
import ch.carteggio.uitests.utils.UseCases;

import com.android.uiautomator.core.UiDevice;

public class MessagingTest extends CarteggioTestCase {

	protected UiDevice mDevice[];
	protected UseCases mUseCases[];
	
	protected void createEmulators(int count) throws Exception {

		mDevice = new UiDevice[count];
		mUseCases = new UseCases[count];
		
		for ( int i = 0 ; i < count; i++) {
			mDevice[i] = setupEmulator("tester" + i);
			mUseCases[i] = new UseCases(mDevice[i]);
			
			mUseCases[i].acceptEULA();
			
			mUseCases[i].enterNewEmailAccount(System.getProperty("account" + i + ".displayname"),
												System.getProperty("account" + i + ".email"), 
												System.getProperty("account" + i + ".password"), 
												true);

			
		
		}
		
	}
	
	
	
	
	
}
