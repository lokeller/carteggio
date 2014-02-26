/*   
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
 * 
 * This file is a modified version of a file distributed with K-9 sources
 * that didn't include any copyright attribution header.    
 *    
 */

package ch.carteggio.net.imap;

public class ImapPreferences {

	public int getMaximumAutoDownloadMessageSize() {
		return 1024;
	}

	public boolean isPushPollOnConnect() {
		// TODO Auto-generated method stub
		return true;
	}

	public long getDisplayCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getIdleRefreshMinutes() {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean allowRemoteSearch() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isRemoteSearchFullText() {
		// TODO Auto-generated method stub
		return false;
	}

}
