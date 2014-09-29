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
package ch.carteggio.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import ch.carteggio.provider.CarteggioContract.Contacts;
import ch.carteggio.provider.CarteggioContract.Conversations;
import ch.carteggio.provider.CarteggioContract.Conversations.Participants;
import ch.carteggio.provider.CarteggioContract.Messages;

/**
 * 
 * This class gives access to the sqlite database that stores 
 * the application data as a {@link ContentProvider}.
 * 
 * The provider is used to publish collections of objects, and 
 * provides methods to add/remove/edit and list these objects.
 * 
 * Each collection of objects corresponds to a table. The 
 * {@link CarteggioProvider} automatically handles updates to any
 * field of the table and adding/removing objects. The objects
 * are uniquely identified using the _id column of the table that
 * must be an integer.
 * 
 * When answering a querying {@link CarteggioProvider} doesn't
 * directly query the table, instead it queries a view. This view
 * must have all the fields of the table and can additionally
 * have other computed fields (for instance fields computed using
 * a join). The same view is used to select objects when performing
 * deletes or updates.
 * 
 * To publish a new type of objects it is sufficient to add a call to
 * the method {@link #addContentDirectory(Directory)}
 * in the constructor. Nothing else should require changes in the
 * class.
 * 
 * A collection of objects can be published as top level URI,
 * in this case accessing the URI will give access to all objects
 * in the collection. To access the objects it is possible to use
 * a URL of type content://ch.carteggio/messages, to find a specific
 * object use the URL content://ch.carteggio/messages/12.
 * 
 * A collection can also be published under another collection,
 * in this case the collection will be filtered so that only a subset
 * of object linked to the main object will be returned. This is
 * for instance the case for participants: using the URI 
 * content://ch.carteggio/conversations/12/participants will list all
 * the participants that are linked to conversation 12.
 * 
 */

public class CarteggioProvider extends ContentProvider {
	
    private CarteggioDatabaseHelper mOpenHelper;
	
	private UriMatcher mItemsMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	private UriMatcher mCollectionsMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	
	private ArrayList<Directory> mCallbacks = new ArrayList<Directory>();
    	
	@Override
	public boolean onCreate() {
	
	    mOpenHelper = new CarteggioDatabaseHelper(getContext());
	
	    addContentDirectory(new Directory(Contacts.CONTENT_URI, 
	    							Contacts.CONTENT_SUBTYPE, "contacts", "view_contacts"));
	    
	    Directory directory = new Directory(Messages.CONTENT_URI, 
									Messages.CONTENT_SUBTYPE, "messages", "view_messages");
		
	    // changes to messages entail changes to conversations due to the SQL triggers
	    directory.addExtraNotification("conversation_id", Conversations.CONTENT_URI);
	    
	    addContentDirectory(directory);
	
	    addContentDirectory(new Directory(Conversations.CONTENT_URI, 
									Conversations.CONTENT_SUBTYPE, "conversations", "view_conversations"));
	
	    Uri conversationPath = Uri.withAppendedPath(Conversations.CONTENT_URI, "#");
	    Uri participantsPath = Uri.withAppendedPath(conversationPath, Participants.CONTENT_DIRECTORY);
	    
	    Directory participantsDirectory = new Directory(participantsPath, 
				Participants.CONTENT_SUBTYPE, "participants", "view_participants", "conversation_id");
		
	    // changes to participants entail changes to conversations due to the SQL triggers
	    directory.addExtraNotification("conversation_id", Conversations.CONTENT_URI);
	    
	    addContentDirectory(participantsDirectory);
	
	    
	    return true;
	}

	@Override
	public String getType(Uri uri) {

    	int code;
    	
    	if (( code = mCollectionsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    		return mCallbacks.get(code).getDirectoryType();
    	} else if (( code = mItemsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    		return mCallbacks.get(code).getItemType();
    	} else {
    		throw new IllegalArgumentException("Unknown URI " + uri);
    	}		
	}

	private void addContentDirectory(Directory provider) {
		
		int code = mCallbacks.size();
		
		mCallbacks.add(provider);			
	
		Uri itemUri = Uri.withAppendedPath(provider.getUri(), "#");
		
		mItemsMatcher.addURI(CarteggioContract.AUTHORITY, itemUri.getPath().substring(1), code);
		mCollectionsMatcher.addURI(CarteggioContract.AUTHORITY, provider.getUri().getPath().substring(1), code);
	
	}

	@Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
    	
    	int code;
    	
    	if (( code = mCollectionsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    	
    		Directory callback = mCallbacks.get(code);
    		
    		List<Long> parent = callback.getParentFromUri(uri);
    		
    		Cursor c = mCallbacks.get(code).queryItems(parent, projection, selection, selectionArgs, sortOrder);
			
    		c.setNotificationUri(getContext().getContentResolver(), uri);
    		
    		return c;
    	
    	} else if (( code = mItemsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    
    		if (selection != null || selectionArgs != null ) {
            	throw new IllegalArgumentException("Cannot set selection for item query");            
            }
    		
    		Directory callback = mCallbacks.get(code);
    		
    		List<Long> index = callback.getIndexFromUri(uri);
    		
    		Cursor c = mCallbacks.get(code).queryItem(index, projection);
			
    		c.setNotificationUri(getContext().getContentResolver(), uri);
    		
    		return c;
    	
    	} else {
    		
    		throw new IllegalArgumentException("Unknown URI " + uri);
    		
    	}
    	
    }
	    
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
    	
    	int code;
    	
    	if (( code = mCollectionsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    	
    		Directory callback = mCallbacks.get(code);
    		
    		List<Long> parent = callback.getParentFromUri(uri);
    		
    		return mCallbacks.get(code).deleteItems(parent, selection, selectionArgs);
    	
    	} else if (( code = mItemsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    
    		if (selection != null || selectionArgs != null ) {
            	throw new IllegalArgumentException("Cannot set selection for item delete");            
            }
    		
    		Directory callback = mCallbacks.get(code);
    		
    		List<Long> index = callback.getIndexFromUri(uri);
    		
    		return mCallbacks.get(code).deleteItem(index);
    	
    	} else {
    		
    		throw new IllegalArgumentException("Unknown URI " + uri);
    		
    	}
    	
    }
	
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
	
		int code;
    	
    	if (( code = mCollectionsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    	
    		Directory callback = mCallbacks.get(code);
    		
    		List<Long> parent = callback.getParentFromUri(uri);
    		
    		return mCallbacks.get(code).insertItem(parent, initialValues);
    	
    	} else if (( code = mItemsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    
        	throw new IllegalArgumentException("Cannot insert on item URI");            
    	
    	} else {
    		
    		throw new IllegalArgumentException("Unknown URI " + uri);
    		
    	}
		
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

    	int code;
    	
    	if (( code = mCollectionsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    	
    		Directory callback = mCallbacks.get(code);
    		
    		List<Long> parent = callback.getParentFromUri(uri);
    		
    		return mCallbacks.get(code).updateItems(parent, values, selection, selectionArgs);
    	
    	} else if (( code = mItemsMatcher.match(uri) ) != UriMatcher.NO_MATCH) {
    
    		if (selection != null || selectionArgs != null ) {
            	throw new IllegalArgumentException("Cannot set selection for item update");            
            }
    		
    		Directory callback = mCallbacks.get(code);
    		
    		List<Long> index = callback.getIndexFromUri(uri);
    		
    		return mCallbacks.get(code).updateItem(index, values);
    	
    	} else {
    		
    		throw new IllegalArgumentException("Unknown URI " + uri);
    		
    	}

		
	}
	        
    private class Directory {
    	
    	private Uri mUri;
    	private String mBackingTable;
    	private String mView;
    	private String mPrimaryIndexColumn;
    	private String mParentIndexesColumns[];    	
    	private String mSubType;
    	
    	private Map<String, Uri> mExtraNotifications = new HashMap<String, Uri>();
		
		public Directory(Uri mUri, String mBackingTable,
				String mView,
				String mPrimaryIndexColumn, String[] mParentIndexesColumns,
				String mSubType) {
			this.mUri = mUri;
			this.mBackingTable = mBackingTable;
			this.mView = mView;
			this.mPrimaryIndexColumn = mPrimaryIndexColumn;
			this.mParentIndexesColumns = mParentIndexesColumns;
			this.mSubType = mSubType;
		}

		public void addExtraNotification(String field, Uri contentUri) {
			mExtraNotifications.put(field, contentUri);
		}

		public Directory(Uri mUri, String mSubType, 
				String mBackingTable, String mView) {
			this(mUri, mBackingTable, mView, "_id", new String[0], mSubType);
		}

		public Directory(Uri mUri, String mSubType, 
				String mBackingTable, String mView, String parentIndex) {
			this(mUri, mBackingTable, mView, "_id", new String[] { parentIndex }, mSubType);
		}
		
		public String getItemType() {
			return "vnd.android.cursor.item/" + mSubType;
		}

		public String getDirectoryType() {
			return "vnd.android.cursor.dir/" + mSubType;
		}

		public Uri getUri() {
    		return mUri;
    	}
		
		private List<Long> getParent(List<Long> id) {
			List<Long> output = new ArrayList<Long>(id);
			output.remove(output.size() - 1);
			return output;
		}
		
		private List<Long> getIndex(List<Long> parent, long id) {
			List<Long> output = new ArrayList<Long>(parent);
			output.add(id);
			return output;
		}

		private List<Long> getParentFromUri(Uri effective) {

			int effectiveParts = effective.getPathSegments().size();
			
			if ( effectiveParts < mUri.getPathSegments().size()) {
				throw new IllegalArgumentException("Invalid input path ");
			}
			
			ArrayList<Long> index = new ArrayList<Long>();
			
			for (int i = 0 ; i < mUri.getPathSegments().size(); i++) {
				
				String templatePart = mUri.getPathSegments().get(i);
				String effectivePart = effective.getPathSegments().get(i);
				
				if (templatePart.equals("#")) {
					index.add(Long.parseLong(effectivePart));
				} else if ( !templatePart.equals(effectivePart)) {
					throw new IllegalArgumentException("Invalid input");
				}
				
			}
		
			return index;
			
		}

		
		private List<Long> getIndexFromUri(Uri effective) {

			int effectiveParts = effective.getPathSegments().size();

			if ( effectiveParts != mUri.getPathSegments().size() + 1) {
				throw new IllegalArgumentException("Invalid input path ");
			}
			
			ArrayList<Long> index = (ArrayList<Long>) getParentFromUri(effective);
		
			index.add(Long.parseLong(effective.getPathSegments().get(effectiveParts - 1)));
		
			return index;
			
		}
    	
    	private Uri getUriForParent(List<Long> parent) {
    		
    		Uri.Builder output = new Uri.Builder();
    		output.authority(mUri.getAuthority());
    		output.scheme(mUri.getScheme());
    		
    		ArrayList<Long> remainingParts = new ArrayList<Long>(parent);
    		
    		for (int i = 0 ; i < mUri.getPathSegments().size(); i++) {
				
    			String templatePart = mUri.getPathSegments().get(i);
    			
				if (templatePart.equals("#")) {
					output.appendPath(Long.toString(remainingParts.remove(0)));
				} else {
					output.appendEncodedPath(templatePart);
				}
				
			}
    		
    		if (remainingParts.size() > 0) {
    			throw new IllegalArgumentException("Invalid parent id");
    		}
    		
    		return output.build();
		
    	}
    	
    	private Uri getUriForItem(List<Long> index) {
    		
    		ArrayList<Long> remainingParts = new ArrayList<Long>(index);
    		
    		long itemId = remainingParts.remove(remainingParts.size() - 1);
		
    		return ContentUris.withAppendedId(getUriForParent(remainingParts), itemId);
    		
    	}
		
    	private String buildBaseTableSelection(List<Long> parent, String selection) {
			String subQuery = SQLiteQueryBuilder.buildQueryString(false, mView, new String[] {mPrimaryIndexColumn},
            								getSelectionWithParent(parent, selection), null, null, null, null);
           
            return mPrimaryIndexColumn + " IN (" + subQuery + ")";
		}
    	
        private String getSelectionWithParent(List<Long> parent, String selection) {
			
			StringBuilder output = new StringBuilder();
        	
        	for ( int i = 0 ; i < mParentIndexesColumns.length ; i++ ) {
        		
        		if ( i != 0 ) output.append(" AND ");
        		
        		output.append(mParentIndexesColumns[i]);
        		output.append(" = ");
        		output.append(parent.get(i));
        	}
        	
        	
        	if ( selection != null) {
	        	if ( parent.size() > 0 ) {
	        		output.append(" AND (");
	        		output.append(selection);
	        		output.append(")");        		
	        	} else {
	        		output.append(selection);
	        	}
        	}
        	
        	return output.toString();
		}

        private String getSelectionForItem(List<Long> id) {
			return mPrimaryIndexColumn + " = " + id.get(id.size() - 1);
		}

		private void notifyAffectedUris(Set<Uri> changedUris) {
			for ( Uri uri : changedUris ) {
			    getContext().getContentResolver().notifyChange(uri, null);
				
			}
		}

		private Set<Uri> findAffectedUris(SQLiteDatabase db, String selection, List<Long> parent) {
			
			Set<Uri> changedUris = new HashSet<Uri>();
			
			Set<String> fieldsSet = new HashSet<String>(mExtraNotifications.keySet());
			fieldsSet.add(mPrimaryIndexColumn);
			
			String[] fields = fieldsSet.toArray(new String[0]);
			
			Cursor c = db.query(mView, fields, selection, null, null, null, null, null);
		
			// add the uri for our own directory of objects
			Uri parentUri = getUriForParent(parent);
			
			changedUris.add(parentUri);
			
			while ( c.moveToNext()) {
				
				// add the uri of each object matching the selection
				long objectId = c.getLong(c.getColumnIndex(mPrimaryIndexColumn));
				Uri objectUri = ContentUris.withAppendedId(parentUri, objectId);
				changedUris.add(objectUri);
				
				
				// add each of the other objects linked to this object and their directory
				for ( Map.Entry<String, Uri> entry : mExtraNotifications.entrySet() ) {
					
					changedUris.add(entry.getValue());
					
					long extraId = c.getLong(c.getColumnIndex(entry.getKey()));
					
					changedUris.add(ContentUris.withAppendedId(entry.getValue(), extraId));
				}
				
			}
			
			return changedUris;
		}

		public Cursor queryItems(List<Long> parent, String[] projection, String selection, String[] selectionArgs,
		        String sortOrder) {
			
			SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		    
			String realSelection = getSelectionWithParent(parent, selection);
		
			return db.query(mView, projection, realSelection, selectionArgs, null, null, sortOrder);
			
		}

		public Cursor queryItem(List<Long> id, String[] projection) {
        	
    		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
            
            return db.query(mView, projection, getSelectionForItem(id), null, null, null, null);
                    
        }
        
    	public int deleteItems(List<Long> parent, String selection, String[] selectionArgs)  {

    		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            String realSelection = buildBaseTableSelection(parent, selection); 

            int count;
            Set<Uri> changedUris;
    		
    		db.beginTransaction();
    		
    		try {
    			
    			changedUris = findAffectedUris(db, selection, parent);
    	    
    			count = db.delete(mBackingTable, realSelection, selectionArgs);

	            db.setTransactionSuccessful();
	
			} finally {
				db.endTransaction();
			} 

    		if ( count > 0) {
    			notifyAffectedUris(changedUris);	
    		}
    		
            return count;
    		
    	}
    	
    	public int deleteItem(List<Long> id)  {

    		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    		String selection = getSelectionForItem(id);
    		
    		int count;
    		Set<Uri> changedUris;
    		
    		db.beginTransaction();
    		
    		try {
    			
	    		changedUris = findAffectedUris(db, selection, getParent(id));
	    		
	            count = db.delete(mBackingTable, selection, null);
	            
	            db.setTransactionSuccessful();

    		} finally {
    			db.endTransaction();
    		} 

    		
    		if ( count > 0) {
    			notifyAffectedUris(changedUris);	
    		}

            return count;

    	}

		public Uri insertItem(List<Long> parent, ContentValues initialValues)  {

    		ContentValues realValues = new ContentValues(initialValues);
    		
    		for (int i = 0 ; i < parent.size() ; i ++) {
    			realValues.put(mParentIndexesColumns[i], parent.get(i));
    		}
    		
    		Set<Uri> changedUris;
    		
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();

    		db.beginTransaction();
    		
    		long itemId;
    		
    		List<Long> index;
    		
    		try {
            
	            itemId = db.insert(mBackingTable, mPrimaryIndexColumn, realValues);
	
	            index = getIndex(parent, itemId);
	
	            String selection = getSelectionForItem(index);
	    	
	        	changedUris = findAffectedUris(db, selection, parent);
	    	   
	            db.setTransactionSuccessful();
        	
			} finally {
				db.endTransaction();
			} 
	
			if ( itemId > -1 ) {
				notifyAffectedUris(changedUris);	
			}
            
            return getUriForItem(index);

    	}

    	public int updateItems(List<Long> parent, ContentValues values, String selection, String[] selectionArgs)  {
    		
    		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            String realSelection = buildBaseTableSelection(parent, selection); 

            int count;
            Set<Uri> changedUris;
    		
    		db.beginTransaction();
    		
    		try {
    			
    			changedUris = findAffectedUris(db, selection, parent);
    	    
    			count = db.update(mBackingTable, values, realSelection, selectionArgs);

    			changedUris.addAll(findAffectedUris(db, selection, parent));
    			
	            db.setTransactionSuccessful();
	
			} finally {
				db.endTransaction();
			} 

    		if ( count > 0) {
    			notifyAffectedUris(changedUris);	
    		}
    		
            return count;
    		
    	}
    	
    	public int updateItem(List<Long> id, ContentValues values) {
    		
    		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    		String selection = getSelectionForItem(id);
    		
    		int count;
    		Set<Uri> changedUris;
    		
    		db.beginTransaction();
    		
    		try {
    			
    			changedUris = findAffectedUris(db, selection, getParent(id));
	    			
    			count = db.update(mBackingTable, values, getSelectionForItem(id), null);
    		    
    			db.setTransactionSuccessful();

    		} finally {
    			db.endTransaction();
    		} 

    		
    		if ( count > 0) {
    			notifyAffectedUris(changedUris);	
    		}

            return count;
    	}    	
    }
	
	
}
