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
 * This file contains code distributed with K-9 sources
 * that didn't include any copyright attribution header.
 *    
 */
package ch.carteggio.net.imap;

import ch.carteggio.net.MessagingException;

class ImapException extends MessagingException {

	private static final long serialVersionUID = 3725007182205882394L;
    private String mAlertText;

    public ImapException(String message, String alertText) {
        super(message, true);
        this.mAlertText = alertText;
    }

    public String getAlertText() {
        return mAlertText;
    }

    public void setAlertText(String alertText) {
        mAlertText = alertText;
    }
}