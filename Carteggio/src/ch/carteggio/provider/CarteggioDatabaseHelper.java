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


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 
 * This class makes sure the database schema is the up to date.
 * 
 * For each version of the schema there is a directory in assets/schema,
 * this directory contains files that need to be applied in order to 
 * upgrade from version n-1 to n. The files are prefixed with a number
 * to make sure the order in which they are applied is deterministic.
 * 
 * To create a new database version n create the corresponding directory
 * in assests/schema and add all the scripts required to upgrade the 
 * database from version n-1 to n. Finally update the {@link #DATABASE_VERSION}
 * constant in this file.
 * 
 * Design considerations: We need to use this approach because we need
 * to always be update the database from any version when the application
 * is updated on a device.
 *
 */

public class CarteggioDatabaseHelper extends SQLiteOpenHelper {

	private static final String DATABASE_NAME = "messages.db";
			
	private static final int DATABASE_VERSION = 1;
	
	private Context mContext;
	
	public CarteggioDatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);		
	
		mContext = context;
	}
	  	
	@Override
	public void onCreate(SQLiteDatabase db) {
        
		// we perform creating inside a transaction to avoid leaving
		// an half created database in case of errors (useful mostly
		// during development)
		
		db.beginTransaction();
		
		try {
			onUpgrade(db, 0, DATABASE_VERSION);
			
			db.setTransactionSuccessful();
			
		} finally {
			db.endTransaction();
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		db.beginTransaction();
		
		try {
			
			for ( int i = oldVersion + 1; i <= newVersion ; i++) {
				
				String [] statements;
				
				try {	
					statements = mContext.getAssets().list("schema/" + i);
				} catch (IOException ex) {
					throw new RuntimeException("Unable to find schema version " + i, ex);
				}
				
				Arrays.sort(statements);
				
				for ( String statement : statements) {
				
					String fileName = "schema/" + i + "/" + statement;
					
					StringBuilder sql = new StringBuilder();
					
					try {
					
						InputStream in = mContext.getAssets().open(fileName);
						
						InputStreamReader ir = new InputStreamReader(in);
						
						char data[] = new char [1000];
						int read;
						
						while ((read = ir.read(data)) != -1 ) {
							sql.append(data, 0, read);
						}
						
					} catch (IOException e) {
						throw new RuntimeException("Unable read file " + fileName, e);
					}
					
					try {
						db.execSQL(sql.toString());
					} catch (SQLException e) {
						throw new RuntimeException("Unable read file " + fileName, e);
					}
					
				}
								
			}
			
			db.setTransactionSuccessful();
			
		} finally {
			db.endTransaction();
		}
		
	}
	
}
