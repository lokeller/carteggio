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


package ch.carteggio.net.imap.parsing;


/**
 * Represents a single response from the IMAP server.
 *
 * Tagged responses will have a non-null tag. Untagged responses will have a null tag. The
 * object will contain all of the available tokens at the time the response is received.
 *
 */
public class ImapResponse extends ImapList {
	
    private static final long serialVersionUID = 6886458551615975669L;

    public boolean mCommandContinuationRequested;
    public String mTag;

    public String getAlertText() {
        if (size() > 1 && ImapResponseParser.equalsIgnoreCase("[ALERT]", get(1))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2, count = size(); i < count; i++) {
                sb.append(get(i).toString());
                sb.append(' ');
            }
            return sb.toString();
        } else {
            return null;
        }
    }

	@Override
    public String toString() {
        return "#" + (mCommandContinuationRequested ? "+" : mTag) + "# " + super.toString();
    }
}