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

package ch.carteggio.net.imap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.CharacterCodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.util.Log;
import ch.carteggio.net.MessagingException;
import ch.carteggio.net.imap.ImapSession.FolderType;
import ch.carteggio.net.imap.parsing.ImapList;
import ch.carteggio.net.imap.parsing.ImapResponse;
import ch.carteggio.net.imap.parsing.ImapResponseParser;
import ch.carteggio.net.security.AuthType;
import ch.carteggio.net.security.ConnectionSecurity;



/**
 * <pre>
 * TODO Need to start keeping track of UIDVALIDITY
 * TODO Need a default response handler for things like folder updates
 * </pre>
 */
public class ImapStore {

	static final String LOG_TAG = "ImapStore";
	
	public static boolean DEBUG = true;
	public static boolean DEBUG_PROTOCOL_IMAP = true;
	public static boolean DEBUG_SENSITIVE = false;
	
    protected static final int SOCKET_CONNECT_TIMEOUT = 30000;
    protected static final int SOCKET_READ_TIMEOUT = 60000;
    
	public static final String STORE_TYPE = "IMAP";

    static final int IDLE_READ_TIMEOUT_INCREMENT = 5 * 60 * 1000;
    static final int IDLE_FAILURE_COUNT_LIMIT = 10;
    static int MAX_DELAY_TIME = 5 * 60 * 1000; // 5 minutes
    static int NORMAL_DELAY_TIME = 5000;

    static int FETCH_WINDOW_SIZE = 100;

    static final String CAPABILITY_IDLE = "IDLE";
    static final String CAPABILITY_AUTH_CRAM_MD5 = "AUTH=CRAM-MD5";
    static final String CAPABILITY_AUTH_PLAIN = "AUTH=PLAIN";
    static final String CAPABILITY_LOGINDISABLED = "LOGINDISABLED";
    static final String COMMAND_IDLE = "IDLE";
    static final String CAPABILITY_NAMESPACE = "NAMESPACE";
    static final String COMMAND_NAMESPACE = "NAMESPACE";

    static final String CAPABILITY_CAPABILITY = "CAPABILITY";
    static final String COMMAND_CAPABILITY = "CAPABILITY";

    static final String CAPABILITY_COMPRESS_DEFLATE = "COMPRESS=DEFLATE";
    static final String COMMAND_COMPRESS_DEFLATE = "COMPRESS DEFLATE";

    static final ImapMessage[] EMPTY_MESSAGE_ARRAY = new ImapMessage[0];

    static final String[] EMPTY_STRING_ARRAY = new String[0];
    static final Long[] EMPTY_LONG_ARRAY = new Long[0];

    
    public static final String HEADER_ANDROID_ATTACHMENT_STORE_DATA = "X-Android-Attachment-StoreData";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HEADER_CONTENT_ID = "Content-ID";
    
    private Context mContext;
    
    private ImapPreferences mPreferences;
    
    private ImapServerSettings mSettings = new ImapServerSettings();

    private LinkedList<ImapConnection> mConnections = new LinkedList<ImapConnection>();


    /**
     * Cache of ImapFolder objects. ImapFolders are attached to a given folder on the server
     * and as long as their associated connection remains open they are reusable between
     * requests. This cache lets us make sure we always reuse, if possible, for a given
     * folder name.
     */
    private HashMap<String, ImapSession> mSessionCache = new HashMap<String, ImapSession>();

    public ImapStore(Context context, ImapServerSettings settings, ImapPreferences preferences) throws MessagingException {
    
    	mContext = context;
    	
        mPreferences = preferences;
        
        mSettings = settings;
        
    }

    public Context getContext() {
    	return mContext;
    }
    
    public ImapSession getSession(String name) {
        ImapSession folder;
        synchronized (mSessionCache) {
            folder = mSessionCache.get(name);
            if (folder == null) {
                folder = new ImapSession(this, name);
                mSessionCache.put(name, folder);
            }
        }
        return folder;
    }

    String getCombinedPrefix() {
        if (mSettings.mCombinedPrefix == null) {
            if (mSettings.mPathPrefix != null) {
                String tmpPrefix = mSettings.mPathPrefix.trim();
                String tmpDelim = (mSettings.mPathDelimeter != null ? mSettings.mPathDelimeter.trim() : "");
                if (tmpPrefix.endsWith(tmpDelim)) {
                	mSettings.mCombinedPrefix = tmpPrefix;
                } else if (tmpPrefix.length() > 0) {
                	mSettings.mCombinedPrefix = tmpPrefix + tmpDelim;
                } else {
                	mSettings.mCombinedPrefix = "";
                }
            } else {
            	mSettings.mCombinedPrefix = "";
            }
        }
        return mSettings.mCombinedPrefix;
    }

    
    public List <ImapSession> getPersonalNamespaces(boolean forceListAll) throws MessagingException {
        ImapConnection connection = getConnection();
        try {
            List <ImapSession > allFolders = listFolders(connection, false);
            if (forceListAll) {
                return allFolders;
            } else {
                List<ImapSession> resultFolders = new LinkedList<ImapSession>();
                Set<String> subscribedFolderNames = new HashSet<String>();
                List <? extends ImapSession > subscribedFolders = listFolders(connection, true);
                for (ImapSession subscribedFolder : subscribedFolders) {
                    subscribedFolderNames.add(subscribedFolder.getFolderName().getName());
                }
                for (ImapSession folder : allFolders) {
                    if (subscribedFolderNames.contains(folder.getFolderName())) {
                        resultFolders.add(folder);
                    }
                }
                return resultFolders;
            }
        } catch (IOException ioe) {
            connection.close();
            throw new MessagingException("Unable to get folder list.", ioe);
        } catch (MessagingException me) {
            connection.close();
            throw new MessagingException("Unable to get folder list.", me);
        } finally {
            releaseConnection(connection);
        }
    }

	public boolean createFolder(String name, FolderType type) throws MessagingException {
		
		String prefixedName = getCombinedPrefix();

		prefixedName += name;
		
		ImapConnection connection = getConnection();

		try {
			connection.executeSimpleCommand(String.format("CREATE %s",
					ImapUtility.encodeString(ImapUtility
							.encodeFolderName(prefixedName))));
			return true;
		} catch (ImapException ie) {
			// We got a response, but it was not "OK"
			return false;
		} catch (IOException ioe) {
			
			throw new MessagingException("IO Error", ioe);
		} finally {
			releaseConnection(connection);
		}
	}
    

    private List <ImapSession> listFolders(ImapConnection connection, boolean LSUB) throws IOException, MessagingException {
        String commandResponse = LSUB ? "LSUB" : "LIST";

        LinkedList<ImapSession> folders = new LinkedList<ImapSession>();

        List<ImapResponse> responses =
            connection.executeSimpleCommand(String.format("%s \"\" %s", commandResponse,
                                            ImapUtility.encodeString(getCombinedPrefix() + "*")));

        for (ImapResponse response : responses) {
            if (ImapResponseParser.equalsIgnoreCase(response.get(0), commandResponse)) {
                boolean includeFolder = true;

                if (response.size() > 4 || !(response.getObject(3) instanceof String)) {
                    Log.w(LOG_TAG, "Skipping incorrectly parsed " + commandResponse +
                            " reply: " + response);
                    continue;
                }

                String decodedFolderName;
                try {
                    decodedFolderName = ImapUtility.decodeFolderName(response.getString(3));
                } catch (CharacterCodingException e) {
                    Log.w(LOG_TAG, "Folder name not correctly encoded with the UTF-7 variant " +
                          "as defined by RFC 3501: " + response.getString(3), e);

                    //TODO: Use the raw name returned by the server for all commands that require
                    //      a folder name. Use the decoded name only for showing it to the user.

                    // We currently just skip folders with malformed names.
                    continue;
                }

                String folder = decodedFolderName;

                if (mSettings.mPathDelimeter == null) {
                	mSettings.mPathDelimeter = response.getString(2);
                	mSettings.mCombinedPrefix = null;
                }

            
                int prefixLength = getCombinedPrefix().length();
                if (prefixLength > 0) {
                    // Strip prefix from the folder name
                    if (folder.length() >= prefixLength) {
                        folder = folder.substring(prefixLength);
                    }
                    if (!decodedFolderName.equalsIgnoreCase(getCombinedPrefix() + folder)) {
                        includeFolder = false;
                    }
                }
            

                ImapList attributes = response.getList(1);
                for (int i = 0, count = attributes.size(); i < count; i++) {
                    String attribute = attributes.getString(i);
                    if (attribute.equalsIgnoreCase("\\NoSelect")) {
                        includeFolder = false;
                    }
                }
                if (includeFolder) {
                    folders.add(getSession(folder));
                }
            }
        }
        
        return folders;

    }


    public String findFolderByType(final ImapConnection connection, String type ) throws IOException, MessagingException {
    	
        String commandResponse = null;
        String commandOptions = "";

        if (connection.hasCapability("XLIST")) {
            if (DEBUG) Log.d(LOG_TAG, "Folder auto-configuration: Using XLIST.");
            commandResponse = "XLIST";
        } else if(connection.hasCapability("SPECIAL-USE")) {
            if (DEBUG) Log.d(LOG_TAG, "Folder auto-configuration: Using RFC6154/SPECIAL-USE.");
            commandResponse = "LIST";
            commandOptions = " (SPECIAL-USE)";
        } else {
            if (DEBUG) Log.d(LOG_TAG, "No detected folder auto-configuration methods.");
            return null;
        }

        final List<ImapResponse> responses =
            connection.executeSimpleCommand(String.format("%s%s \"\" %s", commandResponse, commandOptions,
                ImapUtility.encodeString(getCombinedPrefix() + "*")));

        for (ImapResponse response : responses) {
            if (ImapResponseParser.equalsIgnoreCase(response.get(0), commandResponse)) {

                String decodedFolderName;
                try {
                    decodedFolderName = ImapUtility.decodeFolderName(response.getString(3));
                } catch (CharacterCodingException e) {
                    Log.w(LOG_TAG, "Folder name not correctly encoded with the UTF-7 variant " +
                        "as defined by RFC 3501: " + response.getString(3), e);
                    // We currently just skip folders with malformed names.
                    continue;
                }

                if ( mSettings.mPathDelimeter == null) {
                	mSettings.mPathDelimeter = response.getString(2);
                	mSettings.mCombinedPrefix = null;
                }

                ImapList attributes = response.getList(1);
                for (int i = 0, count = attributes.size(); i < count; i++) {
                    String attribute = attributes.getString(i);
                    if (attribute.equals(type)) {
                        return decodedFolderName;                       
                    }
                }
            }
        }
        
        return null;
    }
    

    /**
     * Gets a connection if one is available for reuse, or creates a new one if not.
     * @return
     */
    ImapConnection getConnection() throws MessagingException {
        synchronized (mConnections) {
            ImapConnection connection = null;
            while ((connection = mConnections.poll()) != null) {
                try {
                    connection.executeSimpleCommand("NOOP");
                    break;
                } catch (IOException ioe) {
                    connection.close();
                }
            }
            if (connection == null) {
                connection = new ImapConnection(mContext, mSettings);
            }
            return connection;
        }
    }

    void releaseConnection(ImapConnection connection) {
        if (connection != null && connection.isOpen()) {
            synchronized (mConnections) {
                mConnections.offer(connection);
            }
        }
    }

    ImapPreferences getPreferences() {
    	return mPreferences;
    }
    
}
