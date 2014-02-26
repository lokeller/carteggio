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

import java.util.List;

import ch.carteggio.net.imap.ImapStore.ImapFolder;
import ch.carteggio.net.imap.ImapStore.ImapMessage;

import android.content.Context;
import android.os.PowerManager.WakeLock;

public interface PushReceiver {
    public Context getContext();
    public void syncFolder(ImapFolder folder);
    public void messagesArrived(ImapFolder folder, List<ImapMessage> mess);
    public void messagesFlagsChanged(ImapFolder folder, List<ImapMessage> mess);
    public void messagesRemoved(ImapFolder folder, List<ImapMessage> mess);
    public String getPushState(String folderName);
    public void pushError(String errorMessage, Exception e);
    public void pushNotSupported();
    public void setPushActive(String folderName, boolean enabled);
    public void sleep(WakeLock wakeLock, long millis);
}
