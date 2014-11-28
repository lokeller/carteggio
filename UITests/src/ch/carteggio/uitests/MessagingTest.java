package ch.carteggio.uitests;


import org.junit.Before;
import org.junit.Test;

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
			
			mUseCases[i].startApplication();
			
			mUseCases[i].acceptEULA();
			
			mUseCases[i].enterNewEmailAccount(System.getProperty("account" + (i+1) + ".displayname"),
												System.getProperty("account" + (i+1) + ".email"), 
												System.getProperty("account" + (i+1) + ".password"), 
												true);

			
		
		}
		
	}

	@Before
	public void setUp() throws Exception {
		createEmulators(2);
	}

	@Test
	public void testSendMessage() throws Exception {
		mUseCases[0].addContact(System.getProperty("account2.displayname"), System.getProperty("account2.email"));
		mUseCases[0].createConversation(System.getProperty("account2.displayname"), System.getProperty("account2.email"), false);
		mUseCases[0].sendMessage("Test message 1");
		
		
		
	}


	
	
}
