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

package ch.carteggio.ui;



import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import ch.carteggio.R;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class AboutActivity extends Activity implements OnClickListener {

	public static final String LICENSE_ACCEPTED = "license_accepted";
	private static final String LOG_TAG = "AboutActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {	
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_about);
		
		try {
			InputStream is = getResources().getAssets().open("LICENSE");
			
			InputStreamReader ir = new InputStreamReader(is);
			
			StringBuilder builder = new StringBuilder();
			
			char ch[] = new char[1024];
			int read = 0;
			
			while ( (read = ir.read(ch)) != -1) {
				builder.append(ch, 0, read);
			}
						
			((TextView) findViewById(R.id.text_license)).setText(builder.toString());
			
		} catch (IOException e) {
			Log.e(LOG_TAG, "Error while reading license", e);
		}		
		
		if ( PreferenceManager.getDefaultSharedPreferences(this).getBoolean(LICENSE_ACCEPTED, false)) {
			findViewById(R.id.agree_layout).setVisibility(View.GONE);
		} else {
			findViewById(R.id.agree_button).setOnClickListener(this);
			findViewById(R.id.disagree_button).setOnClickListener(this);
		}
	}

	@Override
	public void onClick(View v) {

		if ( v.getId() == R.id.agree_button) {
			
			Editor e = PreferenceManager.getDefaultSharedPreferences(this).edit();
			
			e.putBoolean(LICENSE_ACCEPTED, true);
			
			e.commit();
			
			startActivity(new Intent(this, MainActivity.class));
			
			finish();
			
		} else if ( v.getId() == R.id.disagree_button) {
			finish();
		}
		
	}
	
}
