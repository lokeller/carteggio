package ch.carteggio.uitests;

import org.junit.Test;

import ch.carteggio.uitests.utils.SinglePhoneTestCase;

public class MainActivityTest extends SinglePhoneTestCase {

	
	public void setUp() throws Exception {
	
		super.setUp();
		
		mUseCases.startApplication();
		
		mUseCases.acceptEULA();
		
		mUseCases.enterNewEmailAccount(System.getProperty("account1.displayname"),
				System.getProperty("account1.email"), 
				System.getProperty("account1.password"), true);

	}
	
	@Test
	public void testAddContact() throws Exception {
		mUseCases.addContact(System.getProperty("account2.displayname"), System.getProperty("account2.email"));
	}
	
	@Test
	public void testCreateConversation() throws Exception {
		mUseCases.addContact(System.getProperty("account2.displayname"), System.getProperty("account2.email"));
		mUseCases.createConversation(System.getProperty("account2.displayname"), System.getProperty("account2.email"), false);
	}

	@Test
	public void testSendMessage() throws Exception {
		mUseCases.addContact(System.getProperty("account2.displayname"), System.getProperty("account2.email"));
		mUseCases.createConversation(System.getProperty("account2.displayname"), System.getProperty("account2.email"), false);
		mUseCases.sendMessage("Test message 1");
	}

	
}
