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

import java.io.IOException;
import java.util.HashMap;

import ch.carteggio.net.FixedLengthInputStream;
import ch.carteggio.net.imap.ImapMessage;

/**
 * 
 * This class is used to process the response when we want to download message bodies.
 * 
 * The {@link ImapResponseParser} instance used to parse the response is calling back 
 * this class every time it encounters a literal. This class checks if the literal
 * is body and if it is the case it stores it in one of the {@link ImapMessage} 
 * objects that it received during construction.
 * 
 */
public class FetchBodyCallback implements IImapResponseCallback {
    private HashMap<Long, ImapMessage> mMessageMap;

    public FetchBodyCallback(HashMap<Long, ImapMessage> mesageMap) {
        mMessageMap = mesageMap;
    }

    @Override
    public Object foundLiteral(ImapResponse response,
                               FixedLengthInputStream literal) throws IOException, Exception {
        if (response.mTag == null &&
                ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
            ImapList fetchList = (ImapList)response.getKeyedValue("FETCH");
            long uid = fetchList.getKeyedNumber("UID");

            ImapMessage message = (ImapMessage) mMessageMap.get(uid);
            message.parse(literal);

            // Return placeholder object
            return Integer.valueOf(1);
        }
        return null;
    }
}