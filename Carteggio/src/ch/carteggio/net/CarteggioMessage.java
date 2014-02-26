/*******************************************************************************
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
 *******************************************************************************/


package ch.carteggio.net;

import java.util.ArrayList;
import java.util.Date;

import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.AddressBuilder;
import org.apache.james.mime4j.field.address.ParseException;

public abstract class CarteggioMessage {
	
	protected static final String CARTEGGIO_USER_AGENT = "Carteggio/1";
	
	protected Mailbox mFrom;
	protected Mailbox[] mDestinations;	
	protected String mSubject;	
	protected String mParentId;
	protected String mMessageId;
	protected Date mDate;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected static class Builder<BuilderType extends Builder, MessageType extends CarteggioMessage> {
		
		protected MessageType mMessage;
								
		public BuilderType setFrom(String from) throws ParseException {
			
			mMessage.mFrom = AddressBuilder.DEFAULT.parseMailbox(from);			
						
			return (BuilderType) this;
		}
		
		public BuilderType setDestinations(String[] destinations) throws ParseException {		
			
			ArrayList<Mailbox> destinationMailboxes = new ArrayList<Mailbox>();
			
			for ( String destination : destinations) {
				
				Mailbox parsedMailbox = AddressBuilder.DEFAULT.parseMailbox(destination);
				
				destinationMailboxes.add(parsedMailbox);				
				
			}
			
			mMessage.mDestinations = destinationMailboxes.toArray(new Mailbox[0]);
			
			return (BuilderType) this;
		}
		
		public BuilderType setSubject(String subject) {			
			mMessage.mSubject = subject;			
			return (BuilderType) this;
		}
		
		public BuilderType setMessageId(String messageId) {			
			mMessage.mMessageId = messageId;			
			return (BuilderType) this;
		}
		
		public BuilderType setParentId(String parentId) {			
			mMessage.mParentId = parentId;			
			return (BuilderType) this;
		}
		
		public BuilderType setDate(Date date) {			
			mMessage.mDate = date;			
			return (BuilderType) this;
		}
		
		public MessageType build() {
			return mMessage;
		}
		
		
	}
	
	public abstract Message getMessage();
	
}
