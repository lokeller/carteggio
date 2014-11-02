package ch.carteggio.uitests.utils;

import org.junit.Before;

import com.android.uiautomator.core.UiDevice;

public class SinglePhoneTestCase extends CarteggioTestCase {

	protected UiDevice mDevice;
	protected UseCases mUseCases;
	
	@Before
	public void setUp() throws Exception {
		mDevice = setupEmulator("first");
		mUseCases = new UseCases(mDevice);
	}

	
}
