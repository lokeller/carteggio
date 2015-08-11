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
package ch.carteggio.provider;

import java.util.Date;

public interface CarteggioAccount {

	public String getEmail();

	public String getInboxPath();
	
	public String getIncomingProto();

	public String getOutgoingProto();
	
	public String getIncomingHost();

	public String getOutgoingHost();

	public String getIncomingPort();

	public String getOutgoingPort();

	public String getIncomingAuthenticationMethod();

	public String getOutgoingAuthenticationMethod();
	
	public String getIncomingUsername();

	public String getOutgoingUsername();
	
	public String getIncomingPassword();

	public String getOutgoingPassword();

	public String getDisplayName();

	public long getContactId();

	public String getMailDomain();

	public Date getLastCheckDate();

	public void setLastCheckDate(Date date);

	public boolean isPushEnabled();

	public void setPushEnabled(boolean enabled);

	public String getPushState();

	public void setPushState(String state);

	public String createRandomMessageId();

}