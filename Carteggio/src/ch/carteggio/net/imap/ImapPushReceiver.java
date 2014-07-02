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

public interface ImapPushReceiver {
    public void onFolderChanged(String folder);
    public void onListeningError(String folder, String errorMessage, Exception e);
    public void onPushNotSupported(String folderName);
	public void onListeningStarted(String folderName);
}
