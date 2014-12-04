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

import java.util.Date;
import java.util.HashMap;

import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.FieldName;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.MessageImpl;
import org.apache.james.mime4j.storage.StorageBodyFactory;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.util.MimeUtil;

public class ConfirmationReceipt extends CarteggioMessage {

	private Date mDate;
	
	public static class Builder extends CarteggioMessage.Builder<Builder, ConfirmationReceipt> {
		
		public Builder() {
			mMessage = new ConfirmationReceipt();
		}
		
		public Builder setDate(Date date) {
			mMessage.mDate = date;
			return this;
		}
		
	}
	
	private ConfirmationReceipt() {}	
	
	@Override
	public Message getMessage() {
			
		MessageBuilder builder = new DefaultMessageBuilder();
		
		MessageImpl msg = new MessageImpl();
		
		/* add the headers to the message */
		
		Header header = msg.getHeader();
				
		header.addField(Fields.subject(mSubject));		
		header.addField(Fields.from(mFrom));
		header.addField(Fields.to(mDestinations));
		header.addField(new RawField("References", "<" + mParentId + ">"));		
		header.addField(Fields.date(mDate));
		header.addField(new RawField(FieldName.MESSAGE_ID, "<" + mMessageId + ">"));
						
		BodyPart receiptPart = buildReceiptPart();        				
		BodyPart receiptText = buildTextPart();        		
		
		/* multipart containing the the receipt */ 
		
		Multipart multipart = builder.newMultipart("report");
		
        multipart.addBodyPart(receiptPart);
        multipart.addBodyPart(receiptText);
        
        /* add the multipart as body of the message */
        
        HashMap<String, String> multipartParameters = new HashMap<String, String>();
		
        multipartParameters.put("report-type", "disposition-notification");
        multipartParameters.put("boundary", MimeUtil.createUniqueBoundary());
		
		msg.setMultipart(multipart, multipartParameters);
		
		return msg;
	}

	private BodyPart buildTextPart() {
		StorageBodyFactory bodyFactory = new StorageBodyFactory();
				
		String text;
		
		if ( mDate == null) {		
			text = "This is a reception confirmation for a message you sent";
		} else {
			text = "This is a reception confirmation for the message\nyou sent on " + mDate + ".";
		}
				
		TextBody body = bodyFactory.textBody(text, "UTF-8");
		
		BodyPart bodyPart = new BodyPart();
        bodyPart.setText(body);
        bodyPart.setContentTransferEncoding("quoted-printable");
		        
		return bodyPart;
	}
	
	private BodyPart buildReceiptPart() {
		StorageBodyFactory bodyFactory = new StorageBodyFactory();
		
		StringBuilder bodyText = new StringBuilder();
		
		bodyText.append("Reporting-UA: " + CARTEGGIO_USER_AGENT + "\n");
		bodyText.append("Final-Recipient: rfc822;" + mFrom.getAddress() + "\n");
		bodyText.append("Original-Message-ID: <" + mParentId + ">\n");
		bodyText.append("Disposition: action/MDN-sent-automatically; displayed\n");
		
		TextBody body = bodyFactory.textBody(bodyText.toString(), "UTF-8");

        BodyPart bodyPart = new BodyPart();
        bodyPart.setText(body);
        bodyPart.setContentTransferEncoding("quoted-printable");
		
        /* build the receipt body part ( receipt + headers ) */		
        
		BodyPart part = new BodyPart();
		
		HashMap<String, String> parameters = new HashMap<String, String>();
		
		parameters.put("name", "MDNPart2.txt");
		
		part.setBody(body, "message/disposition-notification", parameters);
		
		part.setContentDisposition(ContentDispositionField.DISPOSITION_TYPE_INLINE);
		part.setContentTransferEncoding(MimeUtil.ENC_7BIT);
		return part;
	}
	
}
