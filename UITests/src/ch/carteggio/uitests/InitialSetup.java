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

import com.android.uiautomator.core.UiDevice;

public class InitialSetup extends CarteggioTestCase {

	protected void setUp() throws Exception {
		super.setUp();

		createEmulator("first");

	}

	public void testAcceptEULA() throws Exception {

		goToCarteggioApp(getEmulatorController("first"));
		
		UiDevice uiDevice = getUiDevice("first");

		uiDevice.waitForWindowUpdate("ch.carteggio", 10000);
		
		
	}

}
