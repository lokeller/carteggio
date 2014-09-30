package ch.carteggio.ui;

import ch.carteggio.R;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Conversations.Participants;
import ch.carteggio.provider.CarteggioProviderHelper;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class ParticipantsAdapter extends CursorAdapter {


	public static String [] PROJECTION = { Participants._ID, 
										   Participants.NAME,
										   Participants.EMAIL } ;
	
	private Context mContext;
	
	private Uri mConversation;
	
	public ParticipantsAdapter(Context context, Uri conversation) {		
		super(context, null, FLAG_REGISTER_CONTENT_OBSERVER );
		mContext = context;
		mConversation = conversation;
	}
	
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		View v = inflater.inflate(R.layout.list_item_participant, parent);

		final String email = cursor.getString(cursor.getColumnIndex(Participants.EMAIL));
		
		v.findViewById(R.id.remove_button).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				new DeleteParticipantTask().execute(email);
			}
		});
		
		return v;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		
		String email = cursor.getString(cursor.getColumnIndex(Participants.EMAIL));
		
		((QuickContactBadge) view.findViewById(R.id.participant_badge)).assignContactFromEmail(email, true);
		
		String name = cursor.getString(cursor.getColumnIndex(Participants.NAME));
		
		((TextView) view.findViewById(R.id.name_text)).setText(name);

	}

	private class DeleteParticipantTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... params) {

			CarteggioProviderHelper helper = new CarteggioProviderHelper(mContext);
			
			helper.removeParticipant(mConversation, params[0]);
			
			return null;
		}
		
	}

	public Loader<Cursor> getCursorLoader() {
		return new CursorLoader(mContext, 
				Uri.withAppendedPath(mConversation, 
						Conversations.Participants.CONTENT_DIRECTORY), 
						ParticipantsAdapter.PROJECTION , null, null, null);
	}
	
}
