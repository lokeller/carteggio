package ch.carteggio.net.imap;

import ch.carteggio.net.security.AuthType;
import ch.carteggio.net.security.ConnectionSecurity;

public class ImapServerSettings {

    public String mHost;
    public int mPort;
    public String mUsername;
    public String mPassword;
    public ConnectionSecurity mConnectionSecurity;
    public AuthType mAuthType;
    public String mPathPrefix;
    public String mCombinedPrefix = null;
    public String mPathDelimeter = null;
    
	public boolean useCompression(int type) {		
		return false;
	}		

}