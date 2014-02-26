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

import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.MessageImpl;
import org.apache.james.mime4j.storage.StorageBodyFactory;
import org.apache.james.mime4j.stream.RawField;

public class TextMessage extends CarteggioMessage {

	private String mBody;
	private String mReferences;
	
	public static class Builder extends CarteggioMessage.Builder<Builder, TextMessage> {
		
		public Builder() {
			mMessage = new TextMessage();
		}
		
		public Builder setText(String text) {
			mMessage.mBody = text;
			return this;
		}
		
		public Builder setReferences(String referencesId) {			
			mMessage.mReferences = referencesId;			
			return this;
		}
		
		public TextMessage build() {
			return mMessage;
		}
				
	}
	
	private TextMessage() {}	
	
	public Message getMessage() {
		
		MessageBuilder builder = new DefaultMessageBuilder();
		
		MessageImpl msg = new MessageImpl();
		
		Header header = msg.getHeader();
				
		header.addField(Fields.subject(mSubject));		
		header.addField(Fields.from(mFrom));
		header.addField(Fields.to(mDestinations));
		
		if ( mReferences != null ) {
			header.addField(new RawField("References: ", "<" + mParentId + ">"));			
		}		
		
		if ( mParentId != null) {			
			header.addField(new RawField("In-Reply-To", "<" + mParentId + ">"));
		}
				
		header.addField(new RawField("User-Agent", CARTEGGIO_USER_AGENT));
		
		header.addField(Fields.date(mDate));
		
		header.addField(new RawField("Message-ID", "<" + mMessageId + ">"));
		
		header.addField(new RawField("Disposition-Notification-To", mFrom.getAddress()));
		
		Multipart multipart = builder.newMultipart("mixed");
		
		StorageBodyFactory bodyFactory = new StorageBodyFactory();
		
		TextBody body = bodyFactory.textBody(mBody, "UTF-8");

        BodyPart bodyPart = new BodyPart();
        bodyPart.setText(body);
        bodyPart.setContentTransferEncoding("quoted-printable");
		        
        multipart.addBodyPart(bodyPart);
						
		msg.setMultipart(multipart);
		
		return msg;		
		
	}
	
}
