/*******************************************************************************
 * Copyright (c) 2014, Lorenzo Keller
 *
 * Based on an example from the Android SDK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package ch.carteggio.uitests;

import org.junit.Test;

import ch.carteggio.uitests.utils.SinglePhoneTestCase;

public class EulaTest extends SinglePhoneTestCase {

	@Test
	public void testRefuseEULA() throws Exception {

		mUseCases.startApplication();
		
		mUseCases.refuseEULA();

		// we re-start the application to check that the eula is going to be
		// displayed again
		mUseCases.startApplication();
		
		mUseCases.getState().onAboutActivityWithEULA();
	}
	
	@Test
	public void testAcceptEULA() throws Exception {

		mUseCases.startApplication();
		
		mUseCases.acceptEULA();
		
		// go back to launcher
		mDevice.pressHome();
		mDevice.waitForIdle();

		mUseCases.getState().onLauncher();
		
		// restart app and check that we are not asked again to accept the EULA
		mUseCases.startApplication();
		
		mUseCases.getState().onNewAccountActivity();
		
	}	
	

}
