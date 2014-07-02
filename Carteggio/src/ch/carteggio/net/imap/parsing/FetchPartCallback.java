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
import java.io.InputStream;

import org.apache.james.mime4j.codec.Base64InputStream;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.QuotedPrintableInputStream;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.storage.StorageBodyFactory;
import org.apache.james.mime4j.util.MimeUtil;

import ch.carteggio.net.FixedLengthInputStream;

/**
 * 
 * This class is used to process the response when we want to download a message part
 * 
 * The {@link ImapResponseParser} instance used to parse the response is calling back
 * this class every time it encounters a literal. This class checks if the literal is a
 * body part and if it is the case it stores it in the {@link Entity} that it received
 * as parameter of its constructor.
 * 
 */

public class FetchPartCallback implements IImapResponseCallback {
    private Entity mPart;

    public FetchPartCallback(Entity part) {
        mPart = part;
    }

    @Override
    public Object foundLiteral(ImapResponse response,
                               FixedLengthInputStream literal) throws IOException, Exception {
        if (response.mTag == null &&
                ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
            //TODO: check for correct UID
        	
            StorageBodyFactory bodyFactory = new StorageBodyFactory();
    		
            String transferEncoding = mPart.getContentTransferEncoding();
            
            InputStream stream = literal;
            
            if (MimeUtil.isBase64Encoding(transferEncoding)) {
                stream = new Base64InputStream(literal, DecodeMonitor.SILENT);
            } else if (MimeUtil.isQuotedPrintableEncoded(transferEncoding)) {
                stream = new QuotedPrintableInputStream(literal, DecodeMonitor.SILENT);
            }
            
    		TextBody body = bodyFactory.textBody(stream);        	
                            
            return body;
        }
        
        return null;
    }
}