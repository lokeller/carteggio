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

package ch.carteggio.net.smtp;

import java.io.IOException;
import java.io.OutputStream;

import ch.carteggio.net.MessagingException;

import android.util.Log;


public class SmtpMessage {

	
	
	public enum Encoding {		
		ENCODING_7BIT,
		ENCODING_8BIT		
	}

	private static final String LOG_TAG = "SmtpMessage";
	
	private Encoding mEncoding;
	private byte[] mBody;
	private String[] mDestinations;
	private String mFrom;
	
	public SmtpMessage(String mFrom, String[] mDestinations, byte[] mBody, Encoding mEncoding) {
		this.mFrom = mFrom;
		this.mDestinations = mDestinations;
		this.mBody = mBody;
		this.mEncoding = mEncoding;
	}

	public long calculateSize() {	    
        try {

            CountingOutputStream out = new CountingOutputStream();
            EOLConvertingOutputStream eolOut = new EOLConvertingOutputStream(out);
            writeTo(eolOut);
            eolOut.flush();
            return out.getCount();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to calculate a message size", e);
        } catch (MessagingException e) {
            Log.e(LOG_TAG, "Failed to calculate a message size", e);
        }
        return 0;
	}

	public String getFrom() {
		return mFrom;
	}

	public String[] getDestinations() {
		return mDestinations;
	}
	
	public Encoding getEncoding() {
		return mEncoding;
	}

	public void writeTo(OutputStream msgOut) throws MessagingException {
		try {
			msgOut.write(mBody);
		} catch (IOException e) {
			throw new MessagingException("Error while writing", e);
		}
	}

	
}
