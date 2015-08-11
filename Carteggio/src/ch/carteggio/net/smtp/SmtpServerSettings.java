package ch.carteggio.net.smtp;

import ch.carteggio.net.security.AuthType;
import ch.carteggio.net.security.ConnectionSecurity;

public class SmtpServerSettings {

	public String mHost;
	public ConnectionSecurity mConnectionSecurity;
	public int mPort;
	public String mUsername;
	public String mPassword;
	public AuthType mAuthType;
	
}
