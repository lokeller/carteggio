package ch.carteggio.ui;



import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import ch.carteggio.R;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class AboutActivity extends Activity {

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
		
	}

	
	
}
