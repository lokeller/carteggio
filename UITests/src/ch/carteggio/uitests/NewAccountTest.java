package ch.carteggio.uitests;

import org.junit.Test;

import ch.carteggio.uitests.utils.SinglePhoneTestCase;

import static org.junit.Assert.*;

public class NewAccountTest extends SinglePhoneTestCase {

	@Test
	public void testGMailAccount() throws Exception {
		
		mUseCases.startApplication();
		
		mUseCases.acceptEULA();
		
		assertTrue("account1 must be a gmail account", System.getProperty("account1.email").endsWith("gmail.com"));
		
		mUseCases.enterNewEmailAccount(System.getProperty("account1.displayname"),
										System.getProperty("account1.email"), 
										System.getProperty("account1.password"), true);
		
		mUseCases.getState().onMainActivity();
		
	}
	
	@Test
	public void testManualAccount() throws Exception {
		
		mUseCases.startApplication();
		
		mUseCases.acceptEULA();
		
		mUseCases.enterNewEmailAccount(System.getProperty("account1.displayname"),
										System.getProperty("account1.email"), 
										System.getProperty("account1.password"), false);
		
		
		mUseCases.editAccount(System.getProperty("account1.displayname"), 
								System.getProperty("account1.incomingserver"), 
								System.getProperty("account1.outgoingserver"), 
								System.getProperty("account1.password"), 
								System.getProperty("account1.password"));
		
		mUseCases.getState().onMainActivity();
		
		
		
	}
	
	
}
