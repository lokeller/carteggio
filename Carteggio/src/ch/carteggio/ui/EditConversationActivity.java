package ch.carteggio.ui;

import ch.carteggio.R;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Conversations.Participants;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class EditConversationActivity extends Activity {

	private static final int LOADER_CONVERSATION = 0;
	private static final int LOADER_PARTICIPANTS = 1;
	
	private Uri mConversation;
	
	private EditText mSubject;
	private ListView mParticipants;
	private ParticipantsAdapter mAdapter;
	private View mContent;
	private View mProgress;
	
	private boolean mConversationLoaded = false;
	private boolean mParticipantsLoaded = false;
	
	private LoaderCallbacks<Cursor> mConversationLoader = new LoaderCallbacks<Cursor>() {
		
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {		
						
			return new CursorLoader(EditConversationActivity.this, mConversation, 
									new String[] { Conversations.SUBJECT} , null, null, null);
		}
	
		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
	
			if ( !data.moveToFirst() ) {
				finish();
				return;
			}
			
			String subject = data.getString(data.getColumnIndex(Conversations.SUBJECT));				

			mSubject.setText(subject);		
			
			mConversationLoaded = true;

			updateProgress();
		}
	
		@Override
		public void onLoaderReset(Loader<Cursor> loader) {
			mConversationLoaded = false;

			updateProgress();
		}
		
	};

	private LoaderCallbacks<Cursor> mParticipantsLoader = new LoaderCallbacks<Cursor>() {
		
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {		
			return mAdapter.getCursorLoader();
		}
	
		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
			mAdapter.swapCursor(data);
			mParticipantsLoaded = true;

			updateProgress();
		}
	
		@Override
		public void onLoaderReset(Loader<Cursor> loader) {
			mAdapter.swapCursor(null);
			mParticipantsLoaded = false;
			
			updateProgress();
		}
		
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_edit_conversation);
	
		mConversation = getIntent().getData();
		
		mContent = findViewById(R.id.content);
		mProgress = findViewById(R.id.progress_bar);
		
		mSubject = (EditText) findViewById(R.id.subject_text);
		mParticipants = (ListView) findViewById(R.id.participants_list);
		mAdapter = new ParticipantsAdapter(this, mConversation);
		
		getLoaderManager().initLoader(LOADER_CONVERSATION, null, mConversationLoader);
		getLoaderManager().initLoader(LOADER_PARTICIPANTS, null, mParticipantsLoader);
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.edit_conversation, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		if ( item.getItemId() == R.id.action_accept) {
			finish();
			return true;
		}
		
		return false;
	}
	
	private void updateProgress() {
		if ( mConversationLoaded && mParticipantsLoaded ) {
			mProgress.setVisibility(View.GONE);
			mContent.setVisibility(View.VISIBLE);
		} else {
			mProgress.setVisibility(View.VISIBLE);
			mContent.setVisibility(View.GONE);
		}
	}
}
