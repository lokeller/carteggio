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
 *    
 */

package ch.carteggio.net.imap;

import ch.carteggio.net.MessagingException;

public class ImapFolderName {
	
	private final String mName;

	private ImapStore mStore;

	public ImapFolderName(String mName, ImapStore mStore) {
		this.mName = mName;
		this.mStore = mStore;
	}
	
	public String getName() {
		return mName;
	}
	
	public String getPrefixedName() throws MessagingException {
		return mStore.getCombinedPrefix() + mName;		
	}	
	
}
