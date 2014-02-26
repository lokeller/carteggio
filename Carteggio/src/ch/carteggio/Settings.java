/*******************************************************************************
 * Copyright (c) 2014, Lorenzo Keller
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
package ch.carteggio;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Settings {

	private final static String EMAIL_PREFERENCE = "email";	
	
	private Context context;
	
	public Settings(Context context) {
		this.context = context;
	}

	public String getEmail() {
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		
		return preferences.getString(EMAIL_PREFERENCE, null);
		
	}
		
	
}
