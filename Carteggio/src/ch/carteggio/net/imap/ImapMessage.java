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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;

import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.MessageImpl;
import org.apache.james.mime4j.stream.MimeConfig;

import ch.carteggio.net.MessagingException;
import ch.carteggio.net.smtp.EOLConvertingOutputStream;

public class ImapMessage extends MessageImpl {
	 
    private long mUid;
    private ImapSession mFolder;	
	private long mSize;
	private HashSet<Flag> mFlags = new HashSet<Flag>();
	private Date mInternalDate;

	public ImapMessage(long uid, ImapSession folder) {
        this.mUid = uid;
        this.mFolder = folder;
    }

    public void setUid(long newUid) {
    	mUid = newUid;
	}

	public void writeTo(EOLConvertingOutputStream eolOut) {
		// TODO to implement
		throw new UnsupportedOperationException("Writing messages not supported");
	}

	public Object calculateSize() {
		return null;
	}

	public Flag[] getFlags() {
		return mFlags.toArray(new Flag[0]);
	}

	public long getUid() {
		return mUid;
	}
	
	public long getSize() {
		return mSize;
	}

	public Date getInternalDate() {
		return mInternalDate;
	}

	public void setInternalDate(Date internalDate) {
		mInternalDate = internalDate;
	}
	
	public void setSize(long size) {
        this.mSize = size;
    }

    public void setFlag(Flag flag, boolean set) throws MessagingException {
                	
        mFolder.setFlags(new ImapMessage[] { this }, new Flag[] { flag }, set);
                    
    	setFlagInternal(flag, set);
    }

    public void delete(String trashFolderName) throws MessagingException {
        mFolder.delete(new ImapMessage[] { this }, trashFolderName);
    }

	public void setFlagInternal(Flag flag, boolean set) throws MessagingException {
		if ( set ) {
    		mFlags.add(flag);
    	} else {
    		mFlags.remove(flag);
    	}
	}

	public void parse(InputStream literal) throws IOException {
	
		DefaultMessageBuilder builder = new DefaultMessageBuilder();

        MimeConfig parserConfig  = new MimeConfig();
        parserConfig.setMaxHeaderLen(-1); // The default is a mere 10k
        parserConfig.setMaxLineLen(-1); // The default is 1000 characters. Some MUAs generate
        // REALLY long References: headers
        parserConfig.setMaxHeaderCount(-1); // Disable the check for header count.
		
		builder.setMimeEntityConfig(parserConfig);
			
	    try {
	    	
	    	  Message message = builder.parseMessage(literal);
	        
	    	  setHeader(message.getHeader());		    	  	
	    	  
	    	  if ( getBody() == null ) {
	    	   	  setBody(message.getBody());
	    	  }
	    	  
	    } finally {
	          literal.close();
	    }
		
	}

	public ImapSession getFolder() {
		return mFolder;
	}
}