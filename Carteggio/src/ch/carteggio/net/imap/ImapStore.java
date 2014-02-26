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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;

import org.apache.james.mime4j.codec.Base64InputStream;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.QuotedPrintableInputStream;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MessageImpl;
import org.apache.james.mime4j.message.MultipartImpl;
import org.apache.james.mime4j.storage.StorageBodyFactory;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.util.MimeUtil;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import ch.carteggio.net.FixedLengthInputStream;
import ch.carteggio.net.MessagingException;
import ch.carteggio.net.PeekableInputStream;
import ch.carteggio.net.imap.ImapResponseParser.ImapList;
import ch.carteggio.net.imap.ImapResponseParser.ImapResponse;
import ch.carteggio.net.security.AuthType;
import ch.carteggio.net.security.Authentication;
import ch.carteggio.net.security.AuthenticationFailedException;
import ch.carteggio.net.security.CertificateValidationException;
import ch.carteggio.net.security.ConnectionSecurity;
import ch.carteggio.net.security.TrustManagerFactory;
import ch.carteggio.net.security.TrustedSocketFactory;
import ch.carteggio.net.smtp.EOLConvertingOutputStream;

import com.beetstra.jutf7.CharsetProvider;


/**
 * <pre>
 * TODO Need to start keeping track of UIDVALIDITY
 * TODO Need a default response handler for things like folder updates
 * </pre>
 */
public class ImapStore {

	private static final String LOG_TAG = "ImapStore";
	
	public static boolean DEBUG = true;
	public static boolean DEBUG_PROTOCOL_IMAP = true;
	public static boolean DEBUG_SENSITIVE = false;
	
    protected static final int SOCKET_CONNECT_TIMEOUT = 30000;
    protected static final int SOCKET_READ_TIMEOUT = 60000;

    
	public static final String STORE_TYPE = "IMAP";

    private static final int IDLE_READ_TIMEOUT_INCREMENT = 5 * 60 * 1000;
    private static final int IDLE_FAILURE_COUNT_LIMIT = 10;
    private static int MAX_DELAY_TIME = 5 * 60 * 1000; // 5 minutes
    private static int NORMAL_DELAY_TIME = 5000;

    private static int FETCH_WINDOW_SIZE = 100;

    private Set<Flag> mPermanentFlagsIndex = new HashSet<Flag>();

    private static final String CAPABILITY_IDLE = "IDLE";
    private static final String CAPABILITY_AUTH_CRAM_MD5 = "AUTH=CRAM-MD5";
    private static final String CAPABILITY_AUTH_PLAIN = "AUTH=PLAIN";
    private static final String CAPABILITY_LOGINDISABLED = "LOGINDISABLED";
    private static final String COMMAND_IDLE = "IDLE";
    private static final String CAPABILITY_NAMESPACE = "NAMESPACE";
    private static final String COMMAND_NAMESPACE = "NAMESPACE";

    private static final String CAPABILITY_CAPABILITY = "CAPABILITY";
    private static final String COMMAND_CAPABILITY = "CAPABILITY";

    private static final String CAPABILITY_COMPRESS_DEFLATE = "COMPRESS=DEFLATE";
    private static final String COMMAND_COMPRESS_DEFLATE = "COMPRESS DEFLATE";

    private static final ImapMessage[] EMPTY_MESSAGE_ARRAY = new ImapMessage[0];

    private static final String[] EMPTY_STRING_ARRAY = new String[0];


    public static final String HEADER_ANDROID_ATTACHMENT_STORE_DATA = "X-Android-Attachment-StoreData";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HEADER_CONTENT_ID = "Content-ID";
    
    private Context mContext;
    
    private class ImapServerSettings {
    
	    String mHost;
	    int mPort;
	    String mUsername;
	    String mPassword;
	    ConnectionSecurity mConnectionSecurity;
	    AuthType mAuthType;
	    String mPathPrefix;
	    String mCombinedPrefix = null;
	    String mPathDelimeter = null;
	    
		public boolean useCompression(int type) {		
			return false;
		}		
	    
    }
    
    private ImapPreferences mAccount;
    
    private ImapServerSettings mSettings = new ImapServerSettings();

    private static final SimpleDateFormat RFC3501_DATE = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);

    private LinkedList<ImapConnection> mConnections =
        new LinkedList<ImapConnection>();

    /**
     * Charset used for converting folder names to and from UTF-7 as defined by RFC 3501.
     */
    private Charset mModifiedUtf7Charset;

    /**
     * Cache of ImapFolder objects. ImapFolders are attached to a given folder on the server
     * and as long as their associated connection remains open they are reusable between
     * requests. This cache lets us make sure we always reuse, if possible, for a given
     * folder name.
     */
    private HashMap<String, ImapFolder> mFolderCache = new HashMap<String, ImapFolder>();

    public ImapStore(Context context, String accountUri, ImapPreferences preferences, String password) throws MessagingException {
    
    	mContext = context;
    	
        URI imapUri;
        
        mAccount = preferences;
        
        try {
            imapUri = new URI(accountUri);
        } catch (URISyntaxException use) {
            throw new IllegalArgumentException("Invalid ImapStore URI", use);
        }

        String scheme = imapUri.getScheme();
        /*
         * Currently available schemes are:
         * imap
         * imap+tls+
         * imap+ssl+
         *
         * The following are obsolete schemes that may be found in pre-existing
         * settings from earlier versions or that may be found when imported. We
         * continue to recognize them and re-map them appropriately:
         * imap+tls
         * imap+ssl
         */
        if (scheme.equals("imap")) {
            mSettings.mConnectionSecurity = ConnectionSecurity.NONE;
            mSettings.mPort = 143;
        } else if (scheme.startsWith("imap+tls")) {
        	mSettings.mConnectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED;
        	mSettings.mPort = 143;
        } else if (scheme.startsWith("imap+ssl")) {
        	mSettings.mConnectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED;
        	mSettings.mPort = 993;
        } else {
            throw new IllegalArgumentException("Unsupported protocol (" + scheme + ")");
        }

        mSettings.mHost = imapUri.getHost();

        if (imapUri.getPort() != -1) {
        	mSettings.mPort = imapUri.getPort();
        }

        mSettings.mPassword = password;
        
        if (imapUri.getUserInfo() != null) {
            try {
                String userinfo = imapUri.getUserInfo();
                String[] userInfoParts = userinfo.split(":");

                if (userinfo.endsWith(":")) {
                    // Password is empty. This can only happen after an account was imported.
                	mSettings.mAuthType = AuthType.valueOf(userInfoParts[0]);
                	mSettings.mUsername = URLDecoder.decode(userInfoParts[1], "UTF-8");
                } else if (userInfoParts.length == 1) {
                	mSettings.mAuthType = AuthType.PLAIN;
                	mSettings.mUsername = URLDecoder.decode(userInfoParts[0], "UTF-8");                	
                } else {
                	mSettings.mAuthType = AuthType.valueOf(userInfoParts[0]);
                	mSettings.mUsername = URLDecoder.decode(userInfoParts[1], "UTF-8");
                	
                }
            } catch (UnsupportedEncodingException enc) {
                // This shouldn't happen since the encoding is hardcoded to UTF-8
                throw new IllegalArgumentException("Couldn't urldecode username or password.", enc);
            }
        }

        boolean autoDetectNamespace = true;
        
        String path = imapUri.getPath();
        if (path != null && path.length() > 1) {
            // Strip off the leading "/"
            String cleanPath = path.substring(1);

            if (cleanPath.length() >= 2 && cleanPath.charAt(1) == '|') {
                autoDetectNamespace = cleanPath.charAt(0) == '1';
                if (!autoDetectNamespace) {
                	mSettings.mPathPrefix = cleanPath.substring(2);
                }
            } else {
                if (cleanPath.length() > 0) {
                	mSettings.mPathPrefix = cleanPath;
                    autoDetectNamespace = false;
                }
            }
        }
        // Make extra sure mPathPrefix is null if "auto-detect namespace" is configured
        mSettings.mPathPrefix = autoDetectNamespace ? null : mSettings.mPathPrefix;

        mModifiedUtf7Charset = new CharsetProvider().charsetForName("X-RFC-3501");
    }

    
    public ImapFolder getFolder(String name) {
        ImapFolder folder;
        synchronized (mFolderCache) {
            folder = mFolderCache.get(name);
            if (folder == null) {
                folder = new ImapFolder(this, name);
                mFolderCache.put(name, folder);
            }
        }
        return folder;
    }

    private String getCombinedPrefix() {
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

    
    public List <ImapFolder> getPersonalNamespaces(boolean forceListAll) throws MessagingException {
        ImapConnection connection = getConnection();
        try {
            List <ImapFolder > allFolders = listFolders(connection, false);
            if (forceListAll) {
                return allFolders;
            } else {
                List<ImapFolder> resultFolders = new LinkedList<ImapFolder>();
                Set<String> subscribedFolderNames = new HashSet<String>();
                List <? extends ImapFolder > subscribedFolders = listFolders(connection, true);
                for (ImapFolder subscribedFolder : subscribedFolders) {
                    subscribedFolderNames.add(subscribedFolder.getName());
                }
                for (ImapFolder folder : allFolders) {
                    if (subscribedFolderNames.contains(folder.getName())) {
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


    private List <ImapFolder> listFolders(ImapConnection connection, boolean LSUB) throws IOException, MessagingException {
        String commandResponse = LSUB ? "LSUB" : "LIST";

        LinkedList<ImapFolder> folders = new LinkedList<ImapFolder>();

        List<ImapResponse> responses =
            connection.executeSimpleCommand(String.format("%s \"\" %s", commandResponse,
                                            encodeString(getCombinedPrefix() + "*")));

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
                    decodedFolderName = decodeFolderName(response.getString(3));
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
                    folders.add(getFolder(folder));
                }
            }
        }
        
        return folders;

    }


    public String findFolderByType(final ImapConnection connection, String type ) throws IOException, MessagingException {
    	
        String commandResponse = null;
        String commandOptions = "";

        if (connection.capabilities.contains("XLIST")) {
            if (DEBUG) Log.d(LOG_TAG, "Folder auto-configuration: Using XLIST.");
            commandResponse = "XLIST";
        } else if(connection.capabilities.contains("SPECIAL-USE")) {
            if (DEBUG) Log.d(LOG_TAG, "Folder auto-configuration: Using RFC6154/SPECIAL-USE.");
            commandResponse = "LIST";
            commandOptions = " (SPECIAL-USE)";
        } else {
            if (DEBUG) Log.d(LOG_TAG, "No detected folder auto-configuration methods.");
            return null;
        }

        final List<ImapResponse> responses =
            connection.executeSimpleCommand(String.format("%s%s \"\" %s", commandResponse, commandOptions,
                encodeString(getCombinedPrefix() + "*")));

        for (ImapResponse response : responses) {
            if (ImapResponseParser.equalsIgnoreCase(response.get(0), commandResponse)) {

                String decodedFolderName;
                try {
                    decodedFolderName = decodeFolderName(response.getString(3));
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
    private ImapConnection getConnection() throws MessagingException {
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

    private void releaseConnection(ImapConnection connection) {
        if (connection != null && connection.isOpen()) {
            synchronized (mConnections) {
                mConnections.offer(connection);
            }
        }
    }

    /**
     * Encode a string to be able to use it in an IMAP command.
     *
     * "A quoted string is a sequence of zero or more 7-bit characters,
     *  excluding CR and LF, with double quote (<">) characters at each
     *  end." - Section 4.3, RFC 3501
     *
     * Double quotes and backslash are escaped by prepending a backslash.
     *
     * @param str
     *     The input string (only 7-bit characters allowed).
     * @return
     *     The string encoded as quoted (IMAP) string.
     */
    private static String encodeString(String str) {
        return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String encodeFolderName(String name) {
        try {
            ByteBuffer bb = mModifiedUtf7Charset.encode(name);
            byte[] b = new byte[bb.limit()];
            bb.get(b);
            return new String(b, "US-ASCII");
        } catch (UnsupportedEncodingException uee) {
            /*
             * The only thing that can throw this is getBytes("US-ASCII") and if US-ASCII doesn't
             * exist we're totally screwed.
             */
            throw new RuntimeException("Unable to encode folder name: " + name, uee);
        }
    }

    private String decodeFolderName(String name) throws CharacterCodingException {
        /*
         * Convert the encoded name to US-ASCII, then pass it through the modified UTF-7
         * decoder and return the Unicode String.
         */
        try {
            // Make sure the decoder throws an exception if it encounters an invalid encoding.
            CharsetDecoder decoder = mModifiedUtf7Charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
            CharBuffer cb = decoder.decode(ByteBuffer.wrap(name.getBytes("US-ASCII")));
            return cb.toString();
        } catch (UnsupportedEncodingException uee) {
            /*
             * The only thing that can throw this is getBytes("US-ASCII") and if US-ASCII doesn't
             * exist we're totally screwed.
             */
            throw new RuntimeException("Unable to decode folder name: " + name, uee);
        }
    }

    public boolean isMoveCapable() {
        return true;
    }

    public boolean isCopyCapable() {
        return true;
    }

    public boolean isPushCapable() {
        return true;
    }
    
    public boolean isExpungeCapable() {
        return true;
    }

    public enum FolderType {
        HOLDS_FOLDERS, HOLDS_MESSAGES,
    }

    /**
     * Combines the given array of Objects into a single String using
     * each Object's toString() method and the separator character
     * between each part.
     *
     * @param parts
     * @param separator
     * @return new String
     */
    public static String combine(Object[] parts, char separator) {
        if (parts == null) {
            return null;
        }
        return TextUtils.join(String.valueOf(separator), parts);
    }
    
    public class ImapFolder {

        public static final int OPEN_MODE_RW=0;
        public static final int OPEN_MODE_RO=1;
    	
        private String mName;
        protected volatile int mMessageCount = -1;
        protected volatile long uidNext = -1L;
        protected volatile ImapConnection mConnection;
        private int mMode;
        private volatile boolean mExists;
        private ImapStore store = null;
        Map<Long, String> msgSeqUidMap = new ConcurrentHashMap<Long, String>();
        private boolean mInSearch = false;
		private boolean mCanCreateKeywords;
        
	    
		
        public ImapFolder(ImapStore nStore, String name) {
            super();
            store = nStore;
            this.mName = name;
        }

        public String getPrefixedName() throws MessagingException {
            String prefixedName = "";
            
            ImapConnection connection = null;
            synchronized (this) {
                if (mConnection == null) {
                    connection = getConnection();
                } else {
                    connection = mConnection;
                }
            }
            try {

                connection.open();
            } catch (IOException ioe) {
                throw new MessagingException("Unable to get IMAP prefix", ioe);
            } finally {
                if (mConnection == null) {
                    releaseConnection(connection);
                }
            }
            prefixedName = getCombinedPrefix();

            prefixedName += mName;
            return prefixedName;
        }

        protected List<ImapResponse> executeSimpleCommand(String command) throws MessagingException, IOException {
            return handleUntaggedResponses(mConnection.executeSimpleCommand(command));
        }

        protected List<ImapResponse> executeSimpleCommand(String command, boolean sensitve, UntaggedHandler untaggedHandler) throws MessagingException, IOException {
            return handleUntaggedResponses(mConnection.executeSimpleCommand(command, sensitve, untaggedHandler));
        }

        public void open(int mode) throws MessagingException {
            internalOpen(mode);

            if (mMessageCount == -1) {
                throw new MessagingException(
                    "Did not find message count during open");
            }
        }

        public List<ImapResponse> internalOpen(int mode) throws MessagingException {
            if (isOpen() && mMode == mode) {
                // Make sure the connection is valid. If it's not we'll close it down and continue
                // on to get a new one.
                try {
                    List<ImapResponse> responses = executeSimpleCommand("NOOP");
                    return responses;
                } catch (IOException ioe) {
                    ioExceptionHandler(mConnection, ioe);
                }
            }
            releaseConnection(mConnection);
            synchronized (this) {
                mConnection = getConnection();
            }
            // * FLAGS (\Answered \Flagged \Deleted \Seen \Draft NonJunk
            // $MDNSent)
            // * OK [PERMANENTFLAGS (\Answered \Flagged \Deleted \Seen \Draft
            // NonJunk $MDNSent \*)] Flags permitted.
            // * 23 EXISTS
            // * 0 RECENT
            // * OK [UIDVALIDITY 1125022061] UIDs valid
            // * OK [UIDNEXT 57576] Predicted next UID
            // 2 OK [READ-WRITE] Select completed.
            try {
                msgSeqUidMap.clear();
                String command = String.format("%s %s", mode == OPEN_MODE_RW ? "SELECT"
                        : "EXAMINE", encodeString(encodeFolderName(getPrefixedName())));

                List<ImapResponse> responses = executeSimpleCommand(command);

                /*
                 * If the command succeeds we expect the folder has been opened read-write unless we
                 * are notified otherwise in the responses.
                 */
                mMode = mode;

                for (ImapResponse response : responses) {
                    if (response.size() >= 2) {
                        Object bracketedObj = response.get(1);
                        if (!(bracketedObj instanceof ImapList)) {
                            continue;
                        }
                        ImapList bracketed = (ImapList) bracketedObj;
                        if (bracketed.isEmpty()) {
                            continue;
                        }

                        ImapList flags = bracketed.getKeyedList("PERMANENTFLAGS");
                        if (flags != null) {
                            // parse: * OK [PERMANENTFLAGS (\Answered \Flagged \Deleted
                            // \Seen \Draft NonJunk $label1 \*)] Flags permitted.
                            parseFlags(flags);
                        } else {
                            Object keyObj = bracketed.get(0);
                            if (keyObj instanceof String) {
                                String key = (String) keyObj;
                                if (response.mTag != null) {

                                    if ("READ-ONLY".equalsIgnoreCase(key)) {
                                        mMode = OPEN_MODE_RO;
                                    } else if ("READ-WRITE".equalsIgnoreCase(key)) {
                                        mMode = OPEN_MODE_RW;
                                    }
                                }
                            }
                        }
                    }
                }
                mExists = true;
                return responses;
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            } catch (MessagingException me) {
                Log.e(LOG_TAG, "Unable to open connection for " + getLogId(), me);
                throw me;
            }
        }

        /**
         * Parses an string like PERMANENTFLAGS (\Answered \Flagged \Deleted // \Seen \Draft NonJunk
         * $label1 \*)
         *
         * the parsed flags are stored in the mPermanentFlagsIndex
         * @param flags
         *            the imapflags as strings
         */
        private void parseFlags(ImapList flags) {
            for (Object flag : flags) {
                flag = flag.toString().toLowerCase(Locale.US);
                if (flag.equals("\\deleted")) {
                    mPermanentFlagsIndex.add(Flag.DELETED);
                } else if (flag.equals("\\answered")) {
                    mPermanentFlagsIndex.add(Flag.ANSWERED);
                } else if (flag.equals("\\seen")) {
                    mPermanentFlagsIndex.add(Flag.SEEN);
                } else if (flag.equals("\\flagged")) {
                    mPermanentFlagsIndex.add(Flag.FLAGGED);
                } else if (flag.equals("$forwarded")) {
                    mPermanentFlagsIndex.add(Flag.FORWARDED);
                } else if (flag.equals("\\*")) {
                    mCanCreateKeywords = true;
                }
            }
        }

        public boolean isOpen() {
            return mConnection != null;
        }

        public int getMode() {
            return mMode;
        }

        public void close() {
            if (mMessageCount != -1) {
                mMessageCount = -1;
            }
            if (!isOpen()) {
                return;
            }

            synchronized (this) {
                // If we are mid-search and we get a close request, we gotta trash the connection.
                if (mInSearch && mConnection != null) {
                    Log.i(LOG_TAG, "IMAP search was aborted, shutting down connection.");
                    mConnection.close();
                } else {
                    releaseConnection(mConnection);
                }
                mConnection = null;
            }
        }

        public String getName() {
            return mName;
        }

        /**
         * Check if a given folder exists on the server.
         *
         * @param folderName
         *     The name of the folder encoded as quoted string.
         *     See {@link ImapStore#encodeString}
         *
         * @return
         *     {@code True}, if the folder exists. {@code False}, otherwise.
         */
        private boolean exists(String folderName) throws MessagingException {
            try {
                // Since we don't care about RECENT, we'll use that for the check, because we're checking
                // a folder other than ourself, and don't want any untagged responses to cause a change
                // in our own fields
                mConnection.executeSimpleCommand(String.format("STATUS %s (RECENT)", folderName));
                return true;
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            } catch (ImapException ie) {
                // We got a response, but it was not "OK"
                return false;
            }
        }

        public boolean exists() throws MessagingException {
            if (mExists) {
                return true;
            }
            /*
             * This method needs to operate in the unselected mode as well as the selected mode
             * so we must get the connection ourselves if it's not there. We are specifically
             * not calling checkOpen() since we don't care if the folder is open.
             */
            ImapConnection connection = null;
            synchronized (this) {
                if (mConnection == null) {
                    connection = getConnection();
                } else {
                    connection = mConnection;
                }
            }
            try {
                connection.executeSimpleCommand(String.format("STATUS %s (UIDVALIDITY)",
                                                encodeString(encodeFolderName(getPrefixedName()))));
                mExists = true;
                return true;
            } catch (ImapException ie) {
                // We got a response, but it was not "OK"
                return false;
            } catch (IOException ioe) {
                throw ioExceptionHandler(connection, ioe);
            } finally {
                if (mConnection == null) {
                    releaseConnection(connection);
                }
            }
        }

        public boolean create(FolderType type) throws MessagingException {
            /*
             * This method needs to operate in the unselected mode as well as the selected mode
             * so we must get the connection ourselves if it's not there. We are specifically
             * not calling checkOpen() since we don't care if the folder is open.
             */
            ImapConnection connection = null;
            synchronized (this) {
                if (mConnection == null) {
                    connection = getConnection();
                } else {
                    connection = mConnection;
                }
            }
            try {
                connection.executeSimpleCommand(String.format("CREATE %s",
                                                encodeString(encodeFolderName(getPrefixedName()))));
                return true;
            } catch (ImapException ie) {
                // We got a response, but it was not "OK"
                return false;
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            } finally {
                if (mConnection == null) {
                    releaseConnection(connection);
                }
            }
        }

        /**
         * Copies the given messages to the specified folder.
         *
         * <p>
         * <strong>Note:</strong>
         * Only the UIDs of the given {@link Message} instances are used. It is assumed that all
         * UIDs represent valid messages in this folder.
         * </p>
         *
         * @param messages
         *         The messages to copy to the specfied folder.
         * @param folder
         *         The name of the target folder.
         *
         * @return The mapping of original message UIDs to the new server UIDs.
         */
        public Map<String, String> copyMessages(ImapMessage[] messages, ImapFolder folder)
                throws MessagingException {
            if (!(folder instanceof ImapFolder)) {
                throw new MessagingException("ImapFolder.copyMessages passed non-ImapFolder");
            }

            if (messages.length == 0) {
                return null;
            }

            ImapFolder iFolder = (ImapFolder)folder;
            checkOpen(); //only need READ access

            String[] uids = new String[messages.length];
            for (int i = 0, count = messages.length; i < count; i++) {
                uids[i] = messages[i].getUid();
            }

            try {
                String remoteDestName = encodeString(encodeFolderName(iFolder.getPrefixedName()));

                //TODO: Try to copy/move the messages first and only create the folder if the
                //      operation fails. This will save a roundtrip if the folder already exists.
                if (!exists(remoteDestName)) {
                    /*
                     * If the remote folder doesn't exist we try to create it.
                     */
                    if (DEBUG) {
                        Log.i(LOG_TAG, "ImapFolder.copyMessages: attempting to create remote " +
                                "folder '" + remoteDestName + "' for " + getLogId());
                    }

                    iFolder.create(FolderType.HOLDS_MESSAGES);
                }

                //TODO: Split this into multiple commands if the command exceeds a certain length.
                List<ImapResponse> responses = executeSimpleCommand(String.format("UID COPY %s %s",
                                                      combine(uids, ','),
                                                      remoteDestName));

                // Get the tagged response for the UID COPY command
                ImapResponse response = responses.get(responses.size() - 1);

                Map<String, String> uidMap = null;
                if (response.size() > 1) {
                    /*
                     * If the server supports UIDPLUS, then along with the COPY response it will
                     * return an COPYUID response code, e.g.
                     *
                     * 24 OK [COPYUID 38505 304,319:320 3956:3958] Success
                     *
                     * COPYUID is followed by UIDVALIDITY, the set of UIDs of copied messages from
                     * the source folder and the set of corresponding UIDs assigned to them in the
                     * destination folder.
                     *
                     * We can use the new UIDs included in this response to update our records.
                     */
                    Object responseList = response.get(1);

                    if (responseList instanceof ImapList) {
                        final ImapList copyList = (ImapList) responseList;
                        if (copyList.size() >= 4 && copyList.getString(0).equals("COPYUID")) {
                            List<String> srcUids = ImapUtility.getImapSequenceValues(
                                    copyList.getString(2));
                            List<String> destUids = ImapUtility.getImapSequenceValues(
                                    copyList.getString(3));

                            if (srcUids != null && destUids != null) {
                                if (srcUids.size() == destUids.size()) {
                                    Iterator<String> srcUidsIterator = srcUids.iterator();
                                    Iterator<String> destUidsIterator = destUids.iterator();
                                    uidMap = new HashMap<String, String>();
                                    while (srcUidsIterator.hasNext() &&
                                            destUidsIterator.hasNext()) {
                                        String srcUid = srcUidsIterator.next();
                                        String destUid = destUidsIterator.next();
                                        uidMap.put(srcUid, destUid);
                                    }
                                } else {
                                    if (DEBUG) {
                                        Log.v(LOG_TAG, "Parse error: size of source UIDs " +
                                                "list is not the same as size of destination " +
                                                "UIDs list.");
                                    }
                                }
                            } else {
                                if (DEBUG) {
                                    Log.v(LOG_TAG, "Parsing of the sequence set failed.");
                                }
                            }
                        }
                    }
                }

                return uidMap;
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
        }

        public Map<String, String> moveMessages(ImapMessage[] messages, ImapFolder folder) throws MessagingException {
            if (messages.length == 0)
                return null;
            Map<String, String> uidMap = copyMessages(messages, folder);
            setFlags(messages, new Flag[] { Flag.DELETED }, true);
            return uidMap;
        }

        public void delete(ImapMessage[] messages, String trashFolderName) throws MessagingException {
            if (messages.length == 0)
                return;

            if (trashFolderName == null || getName().equalsIgnoreCase(trashFolderName)) {
                setFlags(messages, new Flag[] { Flag.DELETED }, true);
            } else {
                ImapFolder remoteTrashFolder = (ImapFolder)getStore().getFolder(trashFolderName);
                String remoteTrashName = encodeString(encodeFolderName(remoteTrashFolder.getPrefixedName()));

                if (!exists(remoteTrashName)) {
                    /*
                     * If the remote trash folder doesn't exist we try to create it.
                     */
                    if (DEBUG)
                        Log.i(LOG_TAG, "IMAPMessage.delete: attempting to create remote '" + trashFolderName + "' folder for " + getLogId());
                    remoteTrashFolder.create(FolderType.HOLDS_MESSAGES);
                }

                if (exists(remoteTrashName)) {
                    if (DEBUG)
                        Log.d(LOG_TAG, "IMAPMessage.delete: copying remote " + messages.length + " messages to '" + trashFolderName + "' for " + getLogId());

                    moveMessages(messages, remoteTrashFolder);
                } else {
                    throw new MessagingException("IMAPMessage.delete: remote Trash folder " + trashFolderName + " does not exist and could not be created for " + getLogId()
                                                 , true);
                }
            }
        }


        public int getMessageCount() {
            return mMessageCount;
        }


        private int getRemoteMessageCount(String criteria) throws MessagingException {
            checkOpen(); //only need READ access
            try {
                int count = 0;
                int start = 1;

                List<ImapResponse> responses = executeSimpleCommand(String.format(Locale.US, "SEARCH %d:* %s", start, criteria));
                for (ImapResponse response : responses) {
                    if (ImapResponseParser.equalsIgnoreCase(response.get(0), "SEARCH")) {
                        count += response.size() - 1;
                    }
                }
                return count;
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }


        }

        public int getUnreadMessageCount() throws MessagingException {
            return getRemoteMessageCount("UNSEEN NOT DELETED");
        }

        public int getFlaggedMessageCount() throws MessagingException {
            return getRemoteMessageCount("FLAGGED NOT DELETED");
        }

        protected long getHighestUid() {
            try {
                ImapSearcher searcher = new ImapSearcher() {
                    public List<ImapResponse> search() throws IOException, MessagingException {
                        return executeSimpleCommand("UID SEARCH *:*");
                    }
                };
                ImapMessage[] messages = search(searcher, null).toArray(EMPTY_MESSAGE_ARRAY);
                if (messages.length > 0) {
                    return Long.parseLong(messages[0].getUid());
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Unable to find highest UID in folder " + getName(), e);
            }
            return -1L;

        }

        public void delete(boolean recurse) throws MessagingException {
            throw new Error("ImapStore.delete() not yet implemented");
        }

        public ImapMessage getMessage(String uid) throws MessagingException {
            return new ImapMessage(uid, this);
        }


        public ImapMessage[] getMessages(int start, int end, Date earliestDate, MessageRetrievalListener listener)
        throws MessagingException {
            return getMessages(start, end, earliestDate, false, listener);
        }

        protected ImapMessage[] getMessages(final int start, final int end, Date earliestDate, final boolean includeDeleted, final MessageRetrievalListener listener)
        throws MessagingException {
            if (start < 1 || end < 1 || end < start) {
                throw new MessagingException(
                    String.format(Locale.US, "Invalid message set %d %d",
                                  start, end));
            }
            final StringBuilder dateSearchString = new StringBuilder();
            if (earliestDate != null) {
                dateSearchString.append(" SINCE ");
                synchronized (RFC3501_DATE) {
                    dateSearchString.append(RFC3501_DATE.format(earliestDate));
                }
            }


            ImapSearcher searcher = new ImapSearcher() {
                public List<ImapResponse> search() throws IOException, MessagingException {
                    return executeSimpleCommand(String.format(Locale.US, "UID SEARCH %d:%d%s%s", start, end, dateSearchString, includeDeleted ? "" : " NOT DELETED"));
                }
            };
            return search(searcher, listener).toArray(EMPTY_MESSAGE_ARRAY);

        }
        
        protected ImapMessage[] getMessages(final List<Long> mesgSeqs, final boolean includeDeleted, final MessageRetrievalListener listener)
        throws MessagingException {
            ImapSearcher searcher = new ImapSearcher() {
                public List<ImapResponse> search() throws IOException, MessagingException {
                    return executeSimpleCommand(String.format("UID SEARCH %s%s", combine(mesgSeqs.toArray(), ','), includeDeleted ? "" : " NOT DELETED"));
                }
            };
            return search(searcher, listener).toArray(EMPTY_MESSAGE_ARRAY);
        }

        protected ImapMessage[] getMessagesFromUids(final List<String> mesgUids, final boolean includeDeleted, final MessageRetrievalListener listener)
        throws MessagingException {
            ImapSearcher searcher = new ImapSearcher() {
                public List<ImapResponse> search() throws IOException, MessagingException {
                    return executeSimpleCommand(String.format("UID SEARCH UID %s%s", combine(mesgUids.toArray(), ','), includeDeleted ? "" : " NOT DELETED"));
                }
            };
            return search(searcher, listener).toArray(EMPTY_MESSAGE_ARRAY);
        }

        private List<ImapMessage> search(ImapSearcher searcher, MessageRetrievalListener listener) throws MessagingException {

            checkOpen(); //only need READ access
            ArrayList<ImapMessage> messages = new ArrayList<ImapMessage>();
            try {
                ArrayList<Long> uids = new ArrayList<Long>();
                List<ImapResponse> responses = searcher.search(); //
                for (ImapResponse response : responses) {
                    if (response.mTag == null) {
                        if (ImapResponseParser.equalsIgnoreCase(response.get(0), "SEARCH")) {
                            for (int i = 1, count = response.size(); i < count; i++) {
                                uids.add(response.getLong(i));
                            }
                        }
                    }
                }

                // Sort the uids in numerically decreasing order
                // By doing it in decreasing order, we ensure newest messages are dealt with first
                // This makes the most sense when a limit is imposed, and also prevents UI from going
                // crazy adding stuff at the top.
                Collections.sort(uids, Collections.reverseOrder());

                for (int i = 0, count = uids.size(); i < count; i++) {
                    String uid = uids.get(i).toString();
                    if (listener != null) {
                        listener.messageStarted(uid, i, count);
                    }
                    ImapMessage message = new ImapMessage(uid, this);
                    messages.add(message);
                    if (listener != null) {
                        listener.messageFinished(message, i, count);
                    }
                }
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
            return messages;
        }


        public ImapMessage[] getMessages(MessageRetrievalListener listener) throws MessagingException {
            return getMessages(null, listener);
        }
        
        public ImapMessage[] getMessages(String[] uids, MessageRetrievalListener listener)
        throws MessagingException {
            checkOpen(); //only need READ access
            ArrayList<ImapMessage> messages = new ArrayList<ImapMessage>();
            try {
                if (uids == null) {
                    List<ImapResponse> responses = executeSimpleCommand("UID SEARCH 1:* NOT DELETED");
                    ArrayList<String> tempUids = new ArrayList<String>();
                    for (ImapResponse response : responses) {
                        if (ImapResponseParser.equalsIgnoreCase(response.get(0), "SEARCH")) {
                            for (int i = 1, count = response.size(); i < count; i++) {
                                tempUids.add(response.getString(i));
                            }
                        }
                    }
                    uids = tempUids.toArray(EMPTY_STRING_ARRAY);
                }
                for (int i = 0, count = uids.length; i < count; i++) {
                    if (listener != null) {
                        listener.messageStarted(uids[i], i, count);
                    }
                    ImapMessage message = new ImapMessage(uids[i], this);
                    messages.add(message);
                    if (listener != null) {
                        listener.messageFinished(message, i, count);
                    }
                }
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
            return messages.toArray(EMPTY_MESSAGE_ARRAY);
        }

        public void fetch(ImapMessage[] messages, FetchProfile fp, MessageRetrievalListener listener)
        throws MessagingException {
            if (messages == null || messages.length == 0) {
                return;
            }
            checkOpen(); //only need READ access
            List<String> uids = new ArrayList<String>(messages.length);
            HashMap<String, ImapMessage> messageMap = new HashMap<String, ImapMessage>();
            for (ImapMessage msg : messages) {
                String uid = msg.getUid();
                uids.add(uid);
                messageMap.put(uid, msg);
            }

            /*
             * Figure out what command we are going to run:
             * Flags - UID FETCH (FLAGS)
             * Envelope - UID FETCH ([FLAGS] INTERNALDATE UID RFC822.SIZE FLAGS BODY.PEEK[HEADER.FIELDS (date subject from content-type to cc)])
             *
             */
            Set<String> fetchFields = new LinkedHashSet<String>();
            fetchFields.add("UID");
            if (fp.contains(FetchProfile.Item.FLAGS)) {
                fetchFields.add("FLAGS");
            }
            if (fp.contains(FetchProfile.Item.ENVELOPE)) {
                fetchFields.add("INTERNALDATE");
                fetchFields.add("RFC822.SIZE");
                fetchFields.add("BODY.PEEK[HEADER.FIELDS (date subject from content-type to cc " +
                        "reply-to message-id references in-reply-to user-agent )]");
            }
            if (fp.contains(FetchProfile.Item.STRUCTURE)) {
                fetchFields.add("BODYSTRUCTURE");
            }
            if (fp.contains(FetchProfile.Item.BODY_SANE)) {
                // If the user wants to download unlimited-size messages, don't go only for the truncated body
                if (mAccount.getMaximumAutoDownloadMessageSize() > 0) {
                    fetchFields.add(String.format(Locale.US, "BODY.PEEK[]<0.%d>", mAccount.getMaximumAutoDownloadMessageSize()));
                } else {
                    fetchFields.add("BODY.PEEK[]");
                }
            }
            if (fp.contains(FetchProfile.Item.BODY)) {
                fetchFields.add("BODY.PEEK[]");
            }



            for (int windowStart = 0; windowStart < messages.length; windowStart += (FETCH_WINDOW_SIZE)) {
                List<String> uidWindow = uids.subList(windowStart, Math.min((windowStart + FETCH_WINDOW_SIZE), messages.length));

                try {
                    mConnection.sendCommand(String.format("UID FETCH %s (%s)",
                                                          combine(uidWindow.toArray(new String[uidWindow.size()]), ','),
                                                          combine(fetchFields.toArray(new String[fetchFields.size()]), ' ')
                                                         ), false);
                    ImapResponse response;
                    int messageNumber = 0;

                    ImapResponseParser.IImapResponseCallback callback = null;
                    if (fp.contains(FetchProfile.Item.BODY) || fp.contains(FetchProfile.Item.BODY_SANE)) {
                        callback = new FetchBodyCallback(messageMap);
                    }

                    do {
                        response = mConnection.readResponse(callback);

                        if (response.mTag == null && ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
                            ImapList fetchList = (ImapList)response.getKeyedValue("FETCH");
                            String uid = fetchList.getKeyedString("UID");
                            long msgSeq = response.getLong(0);
                            if (uid != null) {
                                try {
                                    msgSeqUidMap.put(msgSeq, uid);
                                    if (DEBUG) {
                                        Log.v(LOG_TAG, "Stored uid '" + uid + "' for msgSeq " + msgSeq + " into map " /*+ msgSeqUidMap.toString() */);
                                    }
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "Unable to store uid '" + uid + "' for msgSeq " + msgSeq);
                                }
                            }

                            ImapMessage message = messageMap.get(uid);
                            if (message == null) {
                                if (DEBUG)
                                    Log.d(LOG_TAG, "Do not have message in messageMap for UID " + uid + " for " + getLogId());

                                handleUntaggedResponse(response);
                                continue;
                            }
                            if (listener != null) {
                                listener.messageStarted(uid, messageNumber++, messageMap.size());
                            }

                            ImapMessage imapMessage = (ImapMessage) message;

                            Object literal = handleFetchResponse(imapMessage, fetchList);

                            if (literal != null) {
                                if (literal instanceof String) {
                                    String bodyString = (String)literal;
                                    InputStream bodyStream = new ByteArrayInputStream(bodyString.getBytes());
                                    imapMessage.parse(bodyStream);
                                } else if (literal instanceof Integer) {
                                    // All the work was done in FetchBodyCallback.foundLiteral()
                                } else {
                                    // This shouldn't happen
                                    throw new MessagingException("Got FETCH response with bogus parameters");
                                }
                            }

                            if (listener != null) {
                                listener.messageFinished(message, messageNumber, messageMap.size());
                            }
                        } else {
                            handleUntaggedResponse(response);
                        }

                    } while (response.mTag == null);
                } catch (IOException ioe) {
                    throw ioExceptionHandler(mConnection, ioe);
                }
            }
        }

        public void fetchPart(ImapMessage message, Entity part, MessageRetrievalListener listener) throws MessagingException {
            checkOpen(); //only need READ access

            if ( part.getHeader() == null || part.getHeader().getField(HEADER_ANDROID_ATTACHMENT_STORE_DATA) == null) {
            	return;
            }
            
            String parts = part.getHeader().getField(HEADER_ANDROID_ATTACHMENT_STORE_DATA).getBody();
            if (parts == null) {
                return;
            }

            String fetch;
            String partId = parts;
            if ("TEXT".equalsIgnoreCase(partId)) {
                fetch = String.format(Locale.US, "BODY.PEEK[TEXT]<0.%d>",
                        mAccount.getMaximumAutoDownloadMessageSize());
            } else {
                fetch = String.format("BODY.PEEK[%s]", partId);
            }

            try {
                mConnection.sendCommand(
                    String.format("UID FETCH %s (UID %s)", message.getUid(), fetch),
                    false);

                ImapResponse response;
                int messageNumber = 0;

                ImapResponseParser.IImapResponseCallback callback = new FetchPartCallback(part);

                do {
                    response = mConnection.readResponse(callback);

                    if ((response.mTag == null) &&
                            (ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH"))) {
                        ImapList fetchList = (ImapList)response.getKeyedValue("FETCH");
                        String uid = fetchList.getKeyedString("UID");

                        if (!message.getUid().equals(uid)) {
                            if (DEBUG)
                                Log.d(LOG_TAG, "Did not ask for UID " + uid + " for " + getLogId());

                            handleUntaggedResponse(response);
                            continue;
                        }
                        if (listener != null) {
                            listener.messageStarted(uid, messageNumber++, 1);
                        }

                        ImapMessage imapMessage = (ImapMessage) message;

                        Object literal = handleFetchResponse(imapMessage, fetchList);

                        if (literal != null) {
                            if (literal instanceof Body) {
                                // Most of the work was done in FetchAttchmentCallback.foundLiteral()
                            	                            	
                        		if ( part.getBody() != null) {
                        			part.removeBody().dispose();
                            	}
                            	
                                part.setBody((Body)literal);
                                
                            } else if (literal instanceof String) {
                            	
                                String bodyString = (String)literal;
                                
                                InputStream bodyStream = new ByteArrayInputStream(bodyString.getBytes());

                                StorageBodyFactory bodyFactory = new StorageBodyFactory();
                        		
                                String transferEncoding = part.getContentTransferEncoding();
                                
                                InputStream stream = bodyStream;
                                
                                if (MimeUtil.isBase64Encoding(transferEncoding)) {
                                    stream = new Base64InputStream(bodyStream, DecodeMonitor.SILENT);
                                } else if (MimeUtil.isQuotedPrintableEncoded(transferEncoding)) {
                                    stream = new QuotedPrintableInputStream(bodyStream, DecodeMonitor.SILENT);
                                }
                                
                        		TextBody body = bodyFactory.textBody(stream);     

                        		if ( part.getBody() != null) {
                        			part.removeBody().dispose();
                            	}
                            	
                                part.setBody(body);
                                
                            } else {
                                // This shouldn't happen
                                throw new MessagingException("Got FETCH response with bogus parameters");
                            }
                        }

                        if (listener != null) {
                            listener.messageFinished(message, messageNumber, 1);
                        }
                    } else {
                        handleUntaggedResponse(response);
                    }

                } while (response.mTag == null);
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
        }

        // Returns value of body field
        private Object handleFetchResponse(ImapMessage message, ImapList fetchList) throws MessagingException {
            Object result = null;
            if (fetchList.containsKey("FLAGS")) {
                ImapList flags = fetchList.getKeyedList("FLAGS");
                if (flags != null) {
                    for (int i = 0, count = flags.size(); i < count; i++) {
                        String flag = flags.getString(i);
                        if (flag.equalsIgnoreCase("\\Deleted")) {
                            message.setFlagInternal(Flag.DELETED, true);
                        } else if (flag.equalsIgnoreCase("\\Answered")) {
                            message.setFlagInternal(Flag.ANSWERED, true);
                        } else if (flag.equalsIgnoreCase("\\Seen")) {
                            message.setFlagInternal(Flag.SEEN, true);
                        } else if (flag.equalsIgnoreCase("\\Flagged")) {
                            message.setFlagInternal(Flag.FLAGGED, true);
                        } else if (flag.equalsIgnoreCase("$Forwarded")) {
                            message.setFlagInternal(Flag.FORWARDED, true);
                            /* a message contains FORWARDED FLAG -> so we can also create them */
                            mPermanentFlagsIndex.add(Flag.FORWARDED);
                        }
                    }
                }
            }

            if (fetchList.containsKey("INTERNALDATE")) {
                Date internalDate = fetchList.getKeyedDate("INTERNALDATE");
                message.setInternalDate(internalDate);
            }

            if (fetchList.containsKey("RFC822.SIZE")) {
                int size = fetchList.getKeyedNumber("RFC822.SIZE");
                message.setSize(size);
            }

            if (fetchList.containsKey("BODYSTRUCTURE")) {
                ImapList bs = fetchList.getKeyedList("BODYSTRUCTURE");
                if (bs != null) {
                    try {
                        parseBodyStructure(bs, message, "TEXT");
                    } catch (MessagingException e) {
                        if (DEBUG)
                            Log.d(LOG_TAG, "Error handling message for " + getLogId(), e);
                        message.setBody(null);
                    }
                }
            }

            if (fetchList.containsKey("BODY")) {
                int index = fetchList.getKeyIndex("BODY") + 2;
                int size = fetchList.size();
                if (index < size) {
                    result = fetchList.getObject(index);

                    // Check if there's an origin octet
                    if (result instanceof String) {
                        String originOctet = (String) result;
                        if (originOctet.startsWith("<") && (index + 1) < size) {
                            result = fetchList.getObject(index + 1);
                        }
                    }
                }
            }

            return result;
        }

        /**
         * Handle any untagged responses that the caller doesn't care to handle themselves.
         * @param responses
         */
        protected List<ImapResponse> handleUntaggedResponses(List<ImapResponse> responses) {
            for (ImapResponse response : responses) {
                handleUntaggedResponse(response);
            }
            return responses;
        }

        protected void handlePossibleUidNext(ImapResponse response) {
            if (ImapResponseParser.equalsIgnoreCase(response.get(0), "OK") && response.size() > 1) {
                Object bracketedObj = response.get(1);
                if (bracketedObj instanceof ImapList) {
                    ImapList bracketed = (ImapList)bracketedObj;

                    if (bracketed.size() > 1) {
                        Object keyObj = bracketed.get(0);
                        if (keyObj instanceof String) {
                            String key = (String)keyObj;
                            if ("UIDNEXT".equalsIgnoreCase(key)) {
                                uidNext = bracketed.getLong(1);
                                if (DEBUG)
                                    Log.d(LOG_TAG, "Got UidNext = " + uidNext + " for " + getLogId());
                            }
                        }
                    }


                }
            }
        }

        /**
         * Handle an untagged response that the caller doesn't care to handle themselves.
         * @param response
         */
        protected void handleUntaggedResponse(ImapResponse response) {
            if (response.mTag == null && response.size() > 1) {
                if (ImapResponseParser.equalsIgnoreCase(response.get(1), "EXISTS")) {
                    mMessageCount = response.getNumber(0);
                    if (DEBUG)
                        Log.d(LOG_TAG, "Got untagged EXISTS with value " + mMessageCount + " for " + getLogId());
                }
                handlePossibleUidNext(response);

                if (ImapResponseParser.equalsIgnoreCase(response.get(1), "EXPUNGE") && mMessageCount > 0) {
                    mMessageCount--;
                    if (DEBUG)
                        Log.d(LOG_TAG, "Got untagged EXPUNGE with mMessageCount " + mMessageCount + " for " + getLogId());
                }

            }

        }

        private void parseBodyStructure(ImapList bs, Entity part, String id)
        throws MessagingException {
        	        	
            if (bs.get(0) instanceof ImapList) {
                
            	/*
                 * This is a multipart/*
                 */
            	      
            	String subType = null;
                
            	ArrayList<BodyPart> parts = new ArrayList<BodyPart>();
            	
                for (int i = 0, count = bs.size(); i < count; i++) {
                    if (bs.get(i) instanceof ImapList) {
                        /*
                         * For each part in the message we're going to add a new BodyPart and parse
                         * into it.
                         */
                    	
                        BodyPart bp = new BodyPart();
                        if (id.equalsIgnoreCase("TEXT")) {
                            parseBodyStructure(bs.getList(i), bp, Integer.toString(i + 1));
                        } else {
                            parseBodyStructure(bs.getList(i), bp, id + "." + (i + 1));
                        }
                        parts.add(bp);
                    } else {
                        /*
                         * We've got to the end of the children of the part, so now we can find out
                         * what type it is and bail out.
                         */
                        subType = bs.getString(i).toLowerCase(Locale.US);                        
                        break;
                    }
                }
                
                if (subType == null) return;
                
                Multipart mp = new MultipartImpl(subType);

                for ( BodyPart bp : parts) {
                	mp.addBodyPart(bp);
                }

        		if ( part.getBody() != null) {
        			part.removeBody().dispose();
            	}
        		
                part.setBody(mp);
                
            } else {
                /*
                 * This is a body. We need to add as much information as we can find out about
                 * it to the Part.
                 */

                /*
                 *  0| 0  body type
                 *  1| 1  body subtype
                 *  2| 2  body parameter parenthesized list
                 *  3| 3  body id (unused)
                 *  4| 4  body description (unused)
                 *  5| 5  body encoding
                 *  6| 6  body size
                 *  -| 7  text lines (only for type TEXT, unused)
                 * Extensions (optional):
                 *  7| 8  body MD5 (unused)
                 *  8| 9  body disposition
                 *  9|10  body language (unused)
                 * 10|11  body location (unused)
                 */

                String type = bs.getString(0);
                String subType = bs.getString(1);
                String mimeType = (type + "/" + subType).toLowerCase(Locale.US);

                ImapList bodyParams = null;
                if (bs.get(2) instanceof ImapList) {
                    bodyParams = bs.getList(2);
                }
                String encoding = bs.getString(5);
                int size = bs.getNumber(6);

                if (MimeUtil.isSameMimeType(mimeType, "message/rfc822")) {
//                  A body type of type MESSAGE and subtype RFC822
//                  contains, immediately after the basic fields, the
//                  envelope structure, body structure, and size in
//                  text lines of the encapsulated message.
//                    [MESSAGE, RFC822, [NAME, Fwd: [#HTR-517941]:  update plans at 1am Friday - Memory allocation - displayware.eml], NIL, NIL, 7BIT, 5974, NIL, [INLINE, [FILENAME*0, Fwd: [#HTR-517941]:  update plans at 1am Friday - Memory all, FILENAME*1, ocation - displayware.eml]], NIL]
                    /*
                     * This will be caught by fetch and handled appropriately.
                     */
                    throw new MessagingException("BODYSTRUCTURE message/rfc822 not yet supported.");
                }

                /*
                 * Set the content type with as much information as we know right now.
                 */
                StringBuilder contentType = new StringBuilder();
                contentType.append(mimeType);

                if (bodyParams != null) {
                    /*
                     * If there are body params we might be able to get some more information out
                     * of them.
                     */
                    for (int i = 0, count = bodyParams.size(); i < count; i += 2) {
                        contentType.append(String.format(";\r\n %s=\"%s\"",
                                           bodyParams.getString(i),
                                           bodyParams.getString(i + 1)));
                    }
                }

                if ( part.getHeader() == null) part.setHeader(new HeaderImpl()); 
                
                part.getHeader().addField(Fields.contentType(contentType.toString()));

                // Extension items
                ImapList bodyDisposition = null;
                if (("text".equalsIgnoreCase(type))
                        && (bs.size() > 9)
                        && (bs.get(9) instanceof ImapList)) {
                    bodyDisposition = bs.getList(9);
                } else if (!("text".equalsIgnoreCase(type))
                           && (bs.size() > 8)
                           && (bs.get(8) instanceof ImapList)) {
                    bodyDisposition = bs.getList(8);
                }

                StringBuilder contentDisposition = new StringBuilder();

                if (bodyDisposition != null && !bodyDisposition.isEmpty()) {
                    if (!"NIL".equalsIgnoreCase(bodyDisposition.getString(0))) {
                        contentDisposition.append(bodyDisposition.getString(0).toLowerCase(Locale.US));
                    }

                    if ((bodyDisposition.size() > 1)
                            && (bodyDisposition.get(1) instanceof ImapList)) {
                        ImapList bodyDispositionParams = bodyDisposition.getList(1);
                        /*
                         * If there is body disposition information we can pull some more information
                         * about the attachment out.
                         */
                        for (int i = 0, count = bodyDispositionParams.size(); i < count; i += 2) {
                            contentDisposition.append(String.format(";\r\n %s=\"%s\"",
                                                      bodyDispositionParams.getString(i).toLowerCase(Locale.US),
                                                      bodyDispositionParams.getString(i + 1)));
                        }
                    }
                }
                
                ContentDispositionField contentDispositionField = Fields.contentDisposition(contentDisposition.toString());                
                		
                if (contentDispositionField.getParameter(ContentDispositionField.PARAM_SIZE) == null) {
                    contentDisposition.append(String.format(Locale.US, ";\r\n size=%d", size));                    
                    contentDispositionField = Fields.contentDisposition(contentDisposition.toString());
                }

                /*
                 * Set the content disposition containing at least the size. Attachment
                 * handling code will use this down the road.
                 */
                part.getHeader().addField(contentDispositionField);

                /*
                 * Set the Content-Transfer-Encoding header. Attachment code will use this
                 * to parse the body.
                 */
                part.getHeader().addField(Fields.contentTransferEncoding(encoding));
                
                part.getHeader().addField(new RawField(HEADER_ANDROID_ATTACHMENT_STORE_DATA, id));
                
            }

        }

		/**
         * Appends the given messages to the selected folder.
         *
         * <p>
         * This implementation also determines the new UIDs of the given messages on the IMAP
         * server and changes the messages' UIDs to the new server UIDs.
         * </p>
         *
         * @param messages
         *         The messages to append to the folder.
         *
         * @return The mapping of original message UIDs to the new server UIDs.
         */
        public Map<String, String> appendMessages(ImapMessage[] messages) throws MessagingException {
            open(OPEN_MODE_RW);
            checkOpen();
            try {
                Map<String, String> uidMap = new HashMap<String, String>();
                for (ImapMessage message : messages) {
                    mConnection.sendCommand(
                        String.format(Locale.US, "APPEND %s (%s) {%d}",
                                      encodeString(encodeFolderName(getPrefixedName())),
                                      combineFlags(message.getFlags()),
                                      message.calculateSize()), false);

                    ImapResponse response;
                    do {
                        response = mConnection.readResponse();
                        handleUntaggedResponse(response);
                        if (response.mCommandContinuationRequested) {
                            EOLConvertingOutputStream eolOut = new EOLConvertingOutputStream(mConnection.mOut);
                            message.writeTo(eolOut);
                            eolOut.write('\r');
                            eolOut.write('\n');
                            eolOut.flush();
                        }
                    } while (response.mTag == null);

                    if (response.size() > 1) {
                        /*
                         * If the server supports UIDPLUS, then along with the APPEND response it
                         * will return an APPENDUID response code, e.g.
                         *
                         * 11 OK [APPENDUID 2 238268] APPEND completed
                         *
                         * We can use the UID included in this response to update our records.
                         */
                        Object responseList = response.get(1);

                        if (responseList instanceof ImapList) {
                            ImapList appendList = (ImapList) responseList;
                            if (appendList.size() >= 3 &&
                                    appendList.getString(0).equals("APPENDUID")) {

                                String newUid = appendList.getString(2);

                                if (newUid != null && newUid.length() > 0 ) {
                                    message.setUid(newUid);
                                    uidMap.put(message.getUid(), newUid);
                                    continue;
                                }
                            }
                        }
                    }

                    /*
                     * This part is executed in case the server does not support UIDPLUS or does
                     * not implement the APPENDUID response code.
                     */
                    String newUid = getUidFromMessageId(message);
                    if (DEBUG) {
                        Log.d(LOG_TAG, "Got UID " + newUid + " for message for " + getLogId());
                    }

                    if (newUid != null && newUid.length() > 0 ) {
                        uidMap.put(message.getUid(), newUid);
                        message.setUid(newUid);
                    }
                }

                /*
                 * We need uidMap to be null if new UIDs are not available to maintain consistency
                 * with the behavior of other similar methods (copyMessages, moveMessages) which
                 * return null.
                 */
                return (uidMap.size() == 0) ? null : uidMap;
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
        }

        public String getUidFromMessageId(ImapMessage message) throws MessagingException {
            try {
                /*
                * Try to find the UID of the message we just appended using the
                * Message-ID header.
                */
                String messageId = message.getMessageId();

                if (messageId == null ) {
                    if (DEBUG)
                        Log.d(LOG_TAG, "Did not get a message-id in order to search for UID  for " + getLogId());
                    return null;
                }
                
                if (DEBUG)
                    Log.d(LOG_TAG, "Looking for UID for message with message-id " + messageId + " for " + getLogId());

                List<ImapResponse> responses =
                    executeSimpleCommand(
                        String.format("UID SEARCH HEADER MESSAGE-ID %s", encodeString(messageId)));
                for (ImapResponse response1 : responses) {
                    if (response1.mTag == null && ImapResponseParser.equalsIgnoreCase(response1.get(0), "SEARCH")
                            && response1.size() > 1) {
                        return response1.getString(1);
                    }
                }
                return null;
            } catch (IOException ioe) {
                throw new MessagingException("Could not find UID for message based on Message-ID", ioe);
            }
        }


        public void expunge() throws MessagingException {
            open(OPEN_MODE_RW);
            checkOpen();
            try {
                executeSimpleCommand("EXPUNGE");
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
        }

        private String combineFlags(Flag[] flags) {
            ArrayList<String> flagNames = new ArrayList<String>();
            for (Flag flag : flags) {
                if (flag == Flag.SEEN) {
                    flagNames.add("\\Seen");
                } else if (flag == Flag.DELETED) {
                    flagNames.add("\\Deleted");
                } else if (flag == Flag.ANSWERED) {
                    flagNames.add("\\Answered");
                } else if (flag == Flag.FLAGGED) {
                    flagNames.add("\\Flagged");
                } else if (flag == Flag.FORWARDED
                        && (mCanCreateKeywords || mPermanentFlagsIndex.contains(Flag.FORWARDED))) {
                    flagNames.add("$Forwarded");
                }

            }
            return combine(flagNames.toArray(new String[flagNames.size()]), ' ');
        }


        public void setFlags(Flag[] flags, boolean value) throws MessagingException {
            open(OPEN_MODE_RW);
            checkOpen();


            try {
                executeSimpleCommand(String.format("UID STORE 1:* %sFLAGS.SILENT (%s)",
                                                   value ? "+" : "-", combineFlags(flags)));
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
        }

        public String getNewPushState(String oldPushStateS, ImapMessage message) {
            try {
                String messageUidS = message.getUid();
                long messageUid = Long.parseLong(messageUidS);
                ImapPushState oldPushState = ImapPushState.parse(oldPushStateS);
                if (messageUid >= oldPushState.uidNext) {
                    long uidNext = messageUid + 1;
                    ImapPushState newPushState = new ImapPushState(uidNext);
                    return newPushState.toString();
                } else {
                    return null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Exception while updated push state for " + getLogId(), e);
                return null;
            }
        }


        public void setFlags(ImapMessage[] messages, Flag[] flags, boolean value)
        throws MessagingException {
            open(OPEN_MODE_RW);
            checkOpen();
            String[] uids = new String[messages.length];
            for (int i = 0, count = messages.length; i < count; i++) {
                uids[i] = messages[i].getUid();
            }
            try {
                executeSimpleCommand(String.format("UID STORE %s %sFLAGS.SILENT (%s)",
                                                   combine(uids, ','),
                                                   value ? "+" : "-",
                                                   combineFlags(flags)));
            } catch (IOException ioe) {
                throw ioExceptionHandler(mConnection, ioe);
            }
        }

        private void checkOpen() throws MessagingException {
            if (!isOpen()) {
                throw new MessagingException("Folder " + getPrefixedName() + " is not open.");
            }
        }

        private MessagingException ioExceptionHandler(ImapConnection connection, IOException ioe) {
            Log.e(LOG_TAG, "IOException for " + getLogId(), ioe);
            if (connection != null) {
                connection.close();
            }
            close();
            return new MessagingException("IO Error", ioe);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ImapFolder) {
                return ((ImapFolder)o).getName().equalsIgnoreCase(getName());
            }
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }

        public ImapStore getStore() {
            return store;
        }

        protected String getLogId() {
            String id = getName() + "/" + Thread.currentThread().getName();
            if (mConnection != null) {
                id += "/" + mConnection.getLogId();
            }
            return id;
        }

        /**
         * Search the remote ImapFolder.
         * @param queryString String to query for.
         * @param requiredFlags Mandatory flags
         * @param forbiddenFlags Flags to exclude
         * @return List of messages found
         * @throws MessagingException On any error.
         */
        public List<ImapMessage> search(final String queryString, final Flag[] requiredFlags, final Flag[] forbiddenFlags)
            throws MessagingException {

            if (!mAccount.allowRemoteSearch()) {
                throw new MessagingException("Your settings do not allow remote searching of this account");
            }

            // Setup the searcher
            final ImapSearcher searcher = new ImapSearcher() {
                public List<ImapResponse> search() throws IOException, MessagingException {
                    String imapQuery = "UID SEARCH ";
                    if (requiredFlags != null) {
                        for (Flag f : requiredFlags) {
                            switch (f) {
                                case DELETED:
                                    imapQuery += "DELETED ";
                                    break;

                                case SEEN:
                                    imapQuery += "SEEN ";
                                    break;

                                case ANSWERED:
                                    imapQuery += "ANSWERED ";
                                    break;

                                case FLAGGED:
                                    imapQuery += "FLAGGED ";
                                    break;

                                case DRAFT:
                                    imapQuery += "DRAFT ";
                                    break;

                                case RECENT:
                                    imapQuery += "RECENT ";
                                    break;

                                default:
                                    break;
                            }
                        }
                    }
                    if (forbiddenFlags != null) {
                        for (Flag f : forbiddenFlags) {
                            switch (f) {
                                case DELETED:
                                    imapQuery += "UNDELETED ";
                                    break;

                                case SEEN:
                                    imapQuery += "UNSEEN ";
                                    break;

                                case ANSWERED:
                                    imapQuery += "UNANSWERED ";
                                    break;

                                case FLAGGED:
                                    imapQuery += "UNFLAGGED ";
                                    break;

                                case DRAFT:
                                    imapQuery += "UNDRAFT ";
                                    break;

                                case RECENT:
                                    imapQuery += "UNRECENT ";
                                    break;

                                default:
                                    break;
                            }
                        }
                    }
                    final String encodedQry = encodeString(queryString);
                    if (mAccount.isRemoteSearchFullText()) {
                        imapQuery += "TEXT " + encodedQry;
                    } else {
                        imapQuery += "OR SUBJECT " + encodedQry + " FROM " + encodedQry;
                    }
                    return executeSimpleCommand(imapQuery);
                }
            };

            // Execute the search
            try {
                open(OPEN_MODE_RO);
                checkOpen();

                mInSearch = true;
                // don't pass listener--we don't want to add messages until we've downloaded them
                return search(searcher, null);
            } finally {
                mInSearch = false;
            }

        }

		public boolean isIdleCapable() {
		
			return mConnection.isIdleCapable();
			
		}
    }

    /**
     * A cacheable class that stores the details for a single IMAP connection.
     */
    public static class ImapConnection {
        protected Socket mSocket;
        protected PeekableInputStream mIn;
        protected OutputStream mOut;
        protected ImapResponseParser mParser;
        protected int mNextCommandTag;
        protected Set<String> capabilities = new HashSet<String>();
        private Context mContext;

        private ImapServerSettings mSettings;

        public ImapConnection(Context context, final ImapServerSettings settings) {
            this.mSettings = settings;
            this.mContext = context;
        }

        protected String getLogId() {
            return "conn" + hashCode();
        }

        private List<ImapResponse> receiveCapabilities(List<ImapResponse> responses) {
            for (ImapResponse response : responses) {
                ImapList capabilityList = null;
                if (!response.isEmpty() && ImapResponseParser.equalsIgnoreCase(response.get(0), "OK")) {
                    for (Object thisPart : response) {
                        if (thisPart instanceof ImapList) {
                            ImapList thisList = (ImapList)thisPart;
                            if (ImapResponseParser.equalsIgnoreCase(thisList.get(0), CAPABILITY_CAPABILITY)) {
                                capabilityList = thisList;
                                break;
                            }
                        }
                    }
                } else if (response.mTag == null) {
                    capabilityList = response;
                }

                if (capabilityList != null && !capabilityList.isEmpty() &&
                        ImapResponseParser.equalsIgnoreCase(capabilityList.get(0), CAPABILITY_CAPABILITY)) {
                    if (DEBUG) {
                        Log.d(LOG_TAG, "Saving " + capabilityList.size() + " capabilities for " + getLogId());
                    }
                    for (Object capability : capabilityList) {
                        if (capability instanceof String) {
//                            if (DEBUG)
//                            {
//                                Log.v(LOG_TAG, "Saving capability '" + capability + "' for " + getLogId());
//                            }
                            capabilities.add(((String)capability).toUpperCase(Locale.US));
                        }
                    }
                }
            }
            return responses;
        }

        public void open() throws IOException, MessagingException {
            if (isOpen()) {
                return;
            }

            boolean authSuccess = false;

            mNextCommandTag = 1;
            try {
                Security.setProperty("networkaddress.cache.ttl", "0");
            } catch (Exception e) {
                Log.w(LOG_TAG, "Could not set DNS ttl to 0 for " + getLogId(), e);
            }


            try {
                Security.setProperty("networkaddress.cache.negative.ttl", "0");
            } catch (Exception e) {
                Log.w(LOG_TAG, "Could not set DNS negative ttl to 0 for " + getLogId(), e);
            }

            try {
                ConnectionSecurity connectionSecurity = mSettings.mConnectionSecurity;

                // Try all IPv4 and IPv6 addresses of the host
                InetAddress[] addresses = InetAddress.getAllByName(mSettings.mHost);
                for (int i = 0; i < addresses.length; i++) {
                    try {
                        if (DEBUG && DEBUG_PROTOCOL_IMAP) {
                            Log.d(LOG_TAG, "Connecting to " + mSettings.mHost + " as " +
                                    addresses[i]);
                        }

                        SocketAddress socketAddress = new InetSocketAddress(addresses[i],
                                mSettings.mPort);

                        if (connectionSecurity == ConnectionSecurity.SSL_TLS_REQUIRED) {
                            SSLContext sslContext = SSLContext.getInstance("TLS");
                            sslContext
                                    .init(null,
                                            new TrustManager[] { TrustManagerFactory.get(
                                                    mSettings.mHost,
                                                    mSettings.mPort) },
                                            new SecureRandom());
                            mSocket = TrustedSocketFactory.createSocket(sslContext);
                        } else {
                            mSocket = new Socket();
                        }

                        mSocket.connect(socketAddress, SOCKET_CONNECT_TIMEOUT);

                        // Successfully connected to the server; don't try any other addresses
                        break;
                    } catch (SocketException e) {
                        if (i < (addresses.length - 1)) {
                            // There are still other addresses for that host to try
                            continue;
                        }
                        throw new MessagingException("Cannot connect to host", e);
                    }
                }

                setReadTimeout(SOCKET_READ_TIMEOUT);

                mIn = new PeekableInputStream(new BufferedInputStream(mSocket.getInputStream(),
                                              1024));
                mParser = new ImapResponseParser(mIn);
                mOut = new BufferedOutputStream(mSocket.getOutputStream(), 1024);

                capabilities.clear();
                ImapResponse nullResponse = mParser.readResponse();
                if (DEBUG && DEBUG_PROTOCOL_IMAP)
                    Log.v(LOG_TAG, getLogId() + "<<<" + nullResponse);

                List<ImapResponse> nullResponses = new LinkedList<ImapResponse>();
                nullResponses.add(nullResponse);
                receiveCapabilities(nullResponses);

                if (!hasCapability(CAPABILITY_CAPABILITY)) {
                    if (DEBUG)
                        Log.i(LOG_TAG, "Did not get capabilities in banner, requesting CAPABILITY for " + getLogId());
                    List<ImapResponse> responses = receiveCapabilities(executeSimpleCommand(COMMAND_CAPABILITY));
                    if (responses.size() != 2) {
                        throw new MessagingException("Invalid CAPABILITY response received");
                    }
                }

                if (mSettings.mConnectionSecurity == ConnectionSecurity.STARTTLS_REQUIRED) {

                    if (hasCapability("STARTTLS")) {
                        // STARTTLS
                        executeSimpleCommand("STARTTLS");

                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(null,
                                new TrustManager[] { TrustManagerFactory.get(
                                        mSettings.mHost,
                                        mSettings.mPort) },
                                new SecureRandom());
                        mSocket = TrustedSocketFactory.createSocket(sslContext, mSocket,
                                mSettings.mHost, mSettings.mPort, true);
                        mSocket.setSoTimeout(SOCKET_READ_TIMEOUT);
                        mIn = new PeekableInputStream(new BufferedInputStream(mSocket
                                                      .getInputStream(), 1024));
                        mParser = new ImapResponseParser(mIn);
                        mOut = new BufferedOutputStream(mSocket.getOutputStream(), 1024);
                        // Per RFC 2595 (3.1):  Once TLS has been started, reissue CAPABILITY command
                        if (DEBUG)
                            Log.i(LOG_TAG, "Updating capabilities after STARTTLS for " + getLogId());
                        capabilities.clear();
                        List<ImapResponse> responses = receiveCapabilities(executeSimpleCommand(COMMAND_CAPABILITY));
                        if (responses.size() != 2) {
                            throw new MessagingException("Invalid CAPABILITY response received");
                        }
                    } else {
                        /*
                         * This exception triggers a "Certificate error"
                         * notification that takes the user to the incoming
                         * server settings for review. This might be needed if
                         * the account was configured with an obsolete
                         * "STARTTLS (if available)" setting.
                         */
                        throw new CertificateValidationException(
                                "STARTTLS connection security not available",
                                new CertificateException());
                    }
                }

                switch (mSettings.mAuthType) {
                case CRAM_MD5:
                    if (hasCapability(CAPABILITY_AUTH_CRAM_MD5)) {
                        authCramMD5();
                    } else {
                        throw new MessagingException(
                                "Server doesn't support encrypted passwords using CRAM-MD5.");
                    }
                    break;

                case PLAIN:
                    if (hasCapability(CAPABILITY_AUTH_PLAIN)) {
                        saslAuthPlain();
                    } else if (!hasCapability(CAPABILITY_LOGINDISABLED)) {
                        login();
                    } else {
                        throw new MessagingException(
                                "Server doesn't support unencrypted passwords using AUTH=PLAIN and LOGIN is disabled.");
                    }
                    break;

                default:
                    throw new MessagingException(
                            "Unhandled authentication method found in the server settings (bug).");
                }
                authSuccess = true;
                if (DEBUG) {
                    Log.d(LOG_TAG, CAPABILITY_COMPRESS_DEFLATE + " = " + hasCapability(CAPABILITY_COMPRESS_DEFLATE));
                }
                if (hasCapability(CAPABILITY_COMPRESS_DEFLATE)) {
                    ConnectivityManager connectivityManager = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                    boolean useCompression = true;

                    NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
                    if (netInfo != null) {
                        int type = netInfo.getType();
                        if (DEBUG)
                            Log.d(LOG_TAG, "On network type " + type);
                        useCompression = mSettings.useCompression(type);

                    }
                    if (DEBUG)
                        Log.d(LOG_TAG, "useCompression " + useCompression);
                    if (useCompression) {
                        try {
                            executeSimpleCommand(COMMAND_COMPRESS_DEFLATE);
                            Inflater inf = new Inflater(true);
                            InflaterInputStream zInputStream = new InflaterInputStream(mSocket.getInputStream(), inf);
                            mIn = new PeekableInputStream(new BufferedInputStream(zInputStream, 1024));
                            mParser = new ImapResponseParser(mIn);
                            GZIPOutputStream zOutputStream = new GZIPOutputStream(mSocket.getOutputStream());                            
                            mOut = new BufferedOutputStream(zOutputStream, 1024);                            
                            if (DEBUG) {
                                Log.i(LOG_TAG, "Compression enabled for " + getLogId());
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Unable to negotiate compression", e);
                        }
                    }
                }


                if (DEBUG)
                    Log.d(LOG_TAG, "NAMESPACE = " + hasCapability(CAPABILITY_NAMESPACE)
                          + ", mPathPrefix = " + mSettings.mPathPrefix);

                if (mSettings.mPathPrefix == null) {
                    if (hasCapability(CAPABILITY_NAMESPACE)) {
                        if (DEBUG)
                            Log.i(LOG_TAG, "mPathPrefix is unset and server has NAMESPACE capability");
                        List<ImapResponse> namespaceResponses =
                            executeSimpleCommand(COMMAND_NAMESPACE);
                        for (ImapResponse response : namespaceResponses) {
                            if (ImapResponseParser.equalsIgnoreCase(response.get(0), COMMAND_NAMESPACE)) {
                                if (DEBUG)
                                    Log.d(LOG_TAG, "Got NAMESPACE response " + response + " on " + getLogId());

                                Object personalNamespaces = response.get(1);
                                if (personalNamespaces != null && personalNamespaces instanceof ImapList) {
                                    if (DEBUG)
                                        Log.d(LOG_TAG, "Got personal namespaces: " + personalNamespaces);
                                    ImapList bracketed = (ImapList)personalNamespaces;
                                    Object firstNamespace = bracketed.get(0);
                                    if (firstNamespace != null && firstNamespace instanceof ImapList) {
                                        if (DEBUG)
                                            Log.d(LOG_TAG, "Got first personal namespaces: " + firstNamespace);
                                        bracketed = (ImapList)firstNamespace;
                                        mSettings.mPathPrefix = bracketed.getString(0);
                                        mSettings.mPathDelimeter = bracketed.getString(1);
                                        mSettings.mCombinedPrefix = null;
                                        if (DEBUG)
                                            Log.d(LOG_TAG, "Got path '" + mSettings.mPathPrefix + "' and separator '" + mSettings.mPathDelimeter + "'");
                                    }
                                }
                            }
                        }
                    } else {
                        if (DEBUG)
                            Log.i(LOG_TAG, "mPathPrefix is unset but server does not have NAMESPACE capability");
                        mSettings.mPathPrefix = "";
                    }
                }
                if (mSettings.mPathDelimeter == null) {
                    try {
                        List<ImapResponse> nameResponses =
                            executeSimpleCommand("LIST \"\" \"\"");
                        for (ImapResponse response : nameResponses) {
                            if (ImapResponseParser.equalsIgnoreCase(response.get(0), "LIST")) {
                                mSettings.mPathDelimeter = response.getString(2);
                                mSettings.mCombinedPrefix = null;
                                if (DEBUG)
                                    Log.d(LOG_TAG, "Got path delimeter '" + mSettings.mPathDelimeter + "' for " + getLogId());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Unable to get path delimeter using LIST", e);
                    }
                }


            } catch (SSLException e) {
                throw new CertificateValidationException(e.getMessage(), e);
            } catch (GeneralSecurityException gse) {
                throw new MessagingException(
                    "Unable to open connection to IMAP server due to security error.", gse);
            } catch (ConnectException ce) {
                String ceMess = ce.getMessage();
                String[] tokens = ceMess.split("-");
                if (tokens != null && tokens.length > 1 && tokens[1] != null) {
                    Log.e(LOG_TAG, "Stripping host/port from ConnectionException for " + getLogId(), ce);
                    throw new ConnectException(tokens[1].trim());
                } else {
                    throw ce;
                }
            } finally {
                if (!authSuccess) {
                    Log.e(LOG_TAG, "Failed to login, closing connection for " + getLogId());
                    close();
                }
            }
        }

        protected void login() throws IOException, MessagingException {
            /*
             * Use quoted strings which permit spaces and quotes. (Using IMAP
             * string literals would be better, but some servers are broken
             * and don't parse them correctly.)
             */

            // escape double-quotes and backslash characters with a backslash
            Pattern p = Pattern.compile("[\\\\\"]");
            String replacement = "\\\\$0";
            String username = p.matcher(mSettings.mUsername).replaceAll(
                    replacement);
            String password = p.matcher(mSettings.mPassword).replaceAll(
                    replacement);
            try {
                receiveCapabilities(executeSimpleCommand(
                        String.format("LOGIN \"%s\" \"%s\"", username, password), true));
            } catch (ImapException e) {
                throw new AuthenticationFailedException(e.getMessage());
            }
        }

        protected void authCramMD5() throws MessagingException, IOException {
            String command = "AUTHENTICATE CRAM-MD5";
            String tag = sendCommand(command, false);
            ImapResponse response = readContinuationResponse(tag);
            if (response.size() != 1 || !(response.get(0) instanceof String)) {
                throw new MessagingException("Invalid Cram-MD5 nonce received");
            }
            byte[] b64Nonce = response.getString(0).getBytes();
            byte[] b64CRAM = Authentication.computeCramMd5Bytes(
                    mSettings.mUsername, mSettings.mPassword, b64Nonce);

            mOut.write(b64CRAM);
            mOut.write('\r');
            mOut.write('\n');
            mOut.flush();
            try {
                receiveCapabilities(readStatusResponse(tag, command, null));
            } catch (MessagingException e) {
                throw new AuthenticationFailedException(e.getMessage());
            }
        }

        protected void saslAuthPlain() throws IOException, MessagingException {
            String command = "AUTHENTICATE PLAIN";
            String tag = sendCommand(command, false);
            readContinuationResponse(tag);
            mOut.write(Base64.encode(("\000" + mSettings.mUsername
                    + "\000" + mSettings.mPassword).getBytes(), Base64.NO_WRAP));
            mOut.write('\r');
            mOut.write('\n');
            mOut.flush();
            try {
                receiveCapabilities(readStatusResponse(tag, command, null));
            } catch (MessagingException e) {
                throw new AuthenticationFailedException(e.getMessage());
            }
        }

        protected ImapResponse readContinuationResponse(String tag)
                throws IOException, MessagingException {
            ImapResponse response;
            do {
                response = readResponse();
                if (response.mTag != null) {
                    if (response.mTag.equalsIgnoreCase(tag)) {
                        throw new MessagingException(
                                "Command continuation aborted: " + response);
                    } else {
                        Log.w(LOG_TAG, "After sending tag " + tag
                                + ", got tag response from previous command "
                                + response + " for " + getLogId());
                    }
                }
            } while (!response.mCommandContinuationRequested);
            return response;
        }

        protected ArrayList<ImapResponse> readStatusResponse(String tag,
                String commandToLog, UntaggedHandler untaggedHandler)
                throws IOException, MessagingException {
            ArrayList<ImapResponse> responses = new ArrayList<ImapResponse>();
            ImapResponse response;
            do {
                response = mParser.readResponse();
                if (DEBUG && DEBUG_PROTOCOL_IMAP)
                    Log.v(LOG_TAG, getLogId() + "<<<" + response);

                if (response.mTag != null && !response.mTag.equalsIgnoreCase(tag)) {
                    Log.w(LOG_TAG, "After sending tag " + tag + ", got tag response from previous command " + response + " for " + getLogId());
                    Iterator<ImapResponse> iter = responses.iterator();
                    while (iter.hasNext()) {
                        ImapResponse delResponse = iter.next();
                        if (delResponse.mTag != null || delResponse.size() < 2
                                || (!ImapResponseParser.equalsIgnoreCase(delResponse.get(1), "EXISTS") && !ImapResponseParser.equalsIgnoreCase(delResponse.get(1), "EXPUNGE"))) {
                            iter.remove();
                        }
                    }
                    response.mTag = null;
                    continue;
                }
                if (untaggedHandler != null) {
                    untaggedHandler.handleAsyncUntaggedResponse(response);
                }
                responses.add(response);
            } while (response.mTag == null);
            if (response.size() < 1 || !ImapResponseParser.equalsIgnoreCase(response.get(0), "OK")) {
                throw new ImapException("Command: " + commandToLog + "; response: " + response.toString(), response.getAlertText());
            }
            return responses;
        }

        protected void setReadTimeout(int millis) throws SocketException {
            Socket sock = mSocket;
            if (sock != null) {
                sock.setSoTimeout(millis);
            }
        }

        protected boolean isIdleCapable() {
            if (DEBUG)
                Log.v(LOG_TAG, "Connection " + getLogId() + " has " + capabilities.size() + " capabilities");

            return capabilities.contains(CAPABILITY_IDLE);
        }

        protected boolean hasCapability(String capability) {
            return capabilities.contains(capability.toUpperCase(Locale.US));
        }

        public boolean isOpen() {
            return (mIn != null && mOut != null && mSocket != null && mSocket.isConnected() && !mSocket.isClosed());
        }

        public void close() {
        	
        	try { if (mIn != null) mIn.close(); } catch (IOException ex) {};
        	try { if (mIn != null) mOut.close(); } catch (IOException ex) {};
        	try { if (mIn != null) mSocket.close(); } catch (IOException ex) {};
        	
            mIn = null;
            mOut = null;
            mSocket = null;
        }

        public ImapResponse readResponse() throws IOException, MessagingException {
            return readResponse(null);
        }

        public ImapResponse readResponse(ImapResponseParser.IImapResponseCallback callback) throws IOException {
            try {
                ImapResponse response = mParser.readResponse(callback);
                if (DEBUG && DEBUG_PROTOCOL_IMAP)
                    Log.v(LOG_TAG, getLogId() + "<<<" + response);

                return response;
            } catch (IOException ioe) {
                close();
                throw ioe;
            }
        }

        public void sendContinuation(String continuation) throws IOException {
            mOut.write(continuation.getBytes());
            mOut.write('\r');
            mOut.write('\n');
            mOut.flush();

            if (DEBUG && DEBUG_PROTOCOL_IMAP)
                Log.v(LOG_TAG, getLogId() + ">>> " + continuation);

        }

        public String sendCommand(String command, boolean sensitive)
        throws MessagingException, IOException {
            try {
                open();
                String tag = Integer.toString(mNextCommandTag++);
                String commandToSend = tag + " " + command + "\r\n";
                mOut.write(commandToSend.getBytes());
                mOut.flush();

                if (DEBUG && DEBUG_PROTOCOL_IMAP) {
                    if (sensitive && !DEBUG_SENSITIVE) {
                        Log.v(LOG_TAG, getLogId() + ">>> "
                              + "[Command Hidden, Enable Sensitive Debug Logging To Show]");
                    } else {
                        Log.v(LOG_TAG, getLogId() + ">>> " + commandToSend);
                    }
                }

                return tag;
            } catch (IOException ioe) {
                close();
                throw ioe;
            } catch (ImapException ie) {
                close();
                throw ie;
            } catch (MessagingException me) {
                close();
                throw me;
            }
        }

        public List<ImapResponse> executeSimpleCommand(String command) throws IOException,
            ImapException, MessagingException {
            return executeSimpleCommand(command, false, null);
        }

        public List<ImapResponse> executeSimpleCommand(String command, boolean sensitive) throws IOException,
            ImapException, MessagingException {
            return executeSimpleCommand(command, sensitive, null);
        }

        public List<ImapResponse> executeSimpleCommand(String command, boolean sensitive, UntaggedHandler untaggedHandler)
        throws IOException, ImapException, MessagingException {
            String commandToLog = command;
            if (sensitive && !DEBUG_SENSITIVE) {
                commandToLog = "*sensitive*";
            }


            //if (DEBUG)
            //    Log.v(LOG_TAG, "Sending IMAP command " + commandToLog + " on connection " + getLogId());

            String tag = sendCommand(command, sensitive);
            //if (DEBUG)
            //    Log.v(LOG_TAG, "Sent IMAP command " + commandToLog + " with tag " + tag + " for " + getLogId());

            return readStatusResponse(tag, commandToLog, untaggedHandler);
        }
    }

    static public class ImapMessage extends MessageImpl {
    	 
        private String mUid;
        private ImapFolder mFolder;	
		private int mSize;
		private HashSet<Flag> mFlags = new HashSet<Flag>();
		private Date mInternalDate;

		ImapMessage(String uid, ImapFolder folder) {
            this.mUid = uid;
            this.mFolder = folder;
        }

        public void setUid(String newUid) {
        	mUid = newUid;
		}

		public void writeTo(EOLConvertingOutputStream eolOut) {
			// TODO Auto-generated method stub
			
		}

		public Object calculateSize() {
			return null;
		}

		public Flag[] getFlags() {
			return mFlags.toArray(new Flag[0]);
		}

		public String getUid() {
			return mUid;
		}
		
		public int getSize() {
			return mSize;
		}

		public Date getInternalDate() {
			return mInternalDate;
		}

		public void setInternalDate(Date internalDate) {
			mInternalDate = internalDate;
		}
		
		public void setSize(int size) {
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

		public ImapFolder getFolder() {
			return mFolder;
		}
    }

    static class ImapException extends MessagingException {
        private static final long serialVersionUID = 3725007182205882394L;
        String mAlertText;

        public ImapException(String message, String alertText) {
            super(message, true);
            this.mAlertText = alertText;
        }

        public String getAlertText() {
            return mAlertText;
        }

        public void setAlertText(String alertText) {
            mAlertText = alertText;
        }
    }

    @SuppressLint("Wakelock")
	public class ImapFolderPusher extends ImapFolder implements UntaggedHandler {
        
    	private static final long PUSH_WAKE_LOCK_TIMEOUT = 0;
        
		final PushReceiver receiver;
        Thread listeningThread = null;
        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicBoolean idling = new AtomicBoolean(false);
        final AtomicBoolean doneSent = new AtomicBoolean(false);
        final AtomicInteger delayTime = new AtomicInteger(NORMAL_DELAY_TIME);
        final AtomicInteger idleFailureCount = new AtomicInteger(0);
        final AtomicBoolean needsPoll = new AtomicBoolean(false);
        List<ImapResponse> storedUntaggedResponses = new ArrayList<ImapResponse>();
        WakeLock wakeLock = null;

        public ImapFolderPusher(ImapStore store, String name, PushReceiver nReceiver) {
            super(store, name);
            receiver = nReceiver;
            PowerManager pm = (PowerManager) receiver.getContext().getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ImapFolderPusher " + ":" + getName());
            wakeLock.setReferenceCounted(false);

        }
        public void refresh() throws IOException, MessagingException {
            if (idling.get()) {
                wakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT);
                sendDone();
            }
        }

        private void sendDone() throws IOException, MessagingException {
            if (doneSent.compareAndSet(false, true)) {
                ImapConnection conn = mConnection;
                if (conn != null) {
                    conn.setReadTimeout(SOCKET_READ_TIMEOUT);
                    sendContinuation("DONE");
                }

            }
        }

        private void sendContinuation(String continuation)
        throws IOException {
            ImapConnection conn = mConnection;
            if (conn != null) {
                conn.sendContinuation(continuation);
            }
        }

        public void start() {
            Runnable runner = new Runnable() {
                public void run() {
                    wakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT);
                    if (DEBUG)
                        Log.i(LOG_TAG, "Pusher starting for " + getLogId());

                    long lastUidNext = -1L;
                    while (!stop.get()) {
                        try {
                            long oldUidNext = -1L;
                            try {
                                String pushStateS = receiver.getPushState(getName());
                                ImapPushState pushState = ImapPushState.parse(pushStateS);
                                oldUidNext = pushState.uidNext;
                                if (DEBUG)
                                    Log.i(LOG_TAG, "Got oldUidNext " + oldUidNext + " for " + getLogId());
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "Unable to get oldUidNext for " + getLogId(), e);
                            }

                            /*
                             * This makes sure 'oldUidNext' is never smaller than 'UIDNEXT' from
                             * the last loop iteration. This way we avoid looping endlessly causing
                             * the battery to drain.
                             *
                             * See issue 4907
                             */
                            if (oldUidNext < lastUidNext) {
                                oldUidNext = lastUidNext;
                            }

                            ImapConnection oldConnection = mConnection;
                            internalOpen(OPEN_MODE_RO);
                            ImapConnection conn = mConnection;
                            if (conn == null) {
                                receiver.pushError("Could not establish connection for IDLE", null);
                                throw new MessagingException("Could not establish connection for IDLE");

                            }
                            if (!conn.isIdleCapable()) {
                                stop.set(true);
                                receiver.pushNotSupported();
                                throw new MessagingException("IMAP server is not IDLE capable:" + conn.toString());
                            }

                            if (!stop.get() && mAccount.isPushPollOnConnect() && (conn != oldConnection || needsPoll.getAndSet(false))) {
                                List<ImapResponse> untaggedResponses = new ArrayList<ImapResponse>(storedUntaggedResponses);
                                storedUntaggedResponses.clear();
                                processUntaggedResponses(untaggedResponses);
                                if (mMessageCount == -1) {
                                    throw new MessagingException("Message count = -1 for idling");
                                }
                                receiver.syncFolder(ImapFolderPusher.this);
                            }
                            if (stop.get()) {
                                continue;
                            }
                            long startUid = oldUidNext;

                            long newUidNext = uidNext;

                            if (newUidNext == -1) {
                                if (DEBUG) {
                                    Log.d(LOG_TAG, "uidNext is -1, using search to find highest UID");
                                }
                                long highestUid = getHighestUid();
                                if (highestUid != -1L) {
                                    if (DEBUG)
                                        Log.d(LOG_TAG, "highest UID = " + highestUid);
                                    newUidNext = highestUid + 1;
                                    if (DEBUG)
                                        Log.d(LOG_TAG, "highest UID = " + highestUid
                                              + ", set newUidNext to " + newUidNext);
                                }
                            }

                            if (startUid < newUidNext - mAccount.getDisplayCount()) {
                                startUid = newUidNext - mAccount.getDisplayCount();
                            }
                            if (startUid < 1) {
                                startUid = 1;
                            }

                            lastUidNext = newUidNext;
                            if (newUidNext > startUid) {

                                if (DEBUG)
                                    Log.i(LOG_TAG, "Needs sync from uid " + startUid  + " to " + newUidNext + " for " + getLogId());
                                List<ImapMessage> messages = new ArrayList<ImapMessage>();
                                for (long uid = startUid; uid < newUidNext; uid++) {
                                    ImapMessage message = new ImapMessage("" + uid, ImapFolderPusher.this);
                                    messages.add(message);
                                }
                                if (!messages.isEmpty()) {
                                    pushMessages(messages, true);
                                }

                            } else {
                                List<ImapResponse> untaggedResponses = null;
                                while (!storedUntaggedResponses.isEmpty()) {
                                    if (DEBUG)
                                        Log.i(LOG_TAG, "Processing " + storedUntaggedResponses.size() + " untagged responses from previous commands for " + getLogId());
                                    untaggedResponses = new ArrayList<ImapResponse>(storedUntaggedResponses);
                                    storedUntaggedResponses.clear();
                                    processUntaggedResponses(untaggedResponses);
                                }

                                if (DEBUG)
                                    Log.i(LOG_TAG, "About to IDLE for " + getLogId());

                                receiver.setPushActive(getName(), true);
                                idling.set(true);
                                doneSent.set(false);

                                conn.setReadTimeout((mAccount.getIdleRefreshMinutes() * 60 * 1000) + IDLE_READ_TIMEOUT_INCREMENT);
                                untaggedResponses = executeSimpleCommand(COMMAND_IDLE, false, ImapFolderPusher.this);
                                idling.set(false);
                                delayTime.set(NORMAL_DELAY_TIME);
                                idleFailureCount.set(0);
                            }
                        } catch (Exception e) {
                        	
                        	Log.e(LOG_TAG, "Error while IDLEing", e);
                            wakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT);
                            storedUntaggedResponses.clear();
                            idling.set(false);
                            receiver.setPushActive(getName(), false);
                            try {
                                close();
                            } catch (Exception me) {
                                Log.e(LOG_TAG, "Got exception while closing for exception for " + getLogId(), me);
                            }
                            if (stop.get()) {
                                Log.i(LOG_TAG, "Got exception while idling, but stop is set for " + getLogId());
                            } else {
                                receiver.pushError("Push error for " + getName(), e);
                                Log.e(LOG_TAG, "Got exception while idling for " + getLogId(), e);
                                int delayTimeInt = delayTime.get();
                                receiver.sleep(wakeLock, delayTimeInt);
                                delayTimeInt *= 2;
                                if (delayTimeInt > MAX_DELAY_TIME) {
                                    delayTimeInt = MAX_DELAY_TIME;
                                }
                                delayTime.set(delayTimeInt);
                                if (idleFailureCount.incrementAndGet() > IDLE_FAILURE_COUNT_LIMIT) {
                                    Log.e(LOG_TAG, "Disabling pusher for " + getLogId() + " after " + idleFailureCount.get() + " consecutive errors");
                                    receiver.pushError("Push disabled for " + getName() + " after " + idleFailureCount.get() + " consecutive errors", e);
                                    stop.set(true);
                                }

                            }
                        }
                    }
                    receiver.setPushActive(getName(), false);
                    try {
                        if (DEBUG)
                            Log.i(LOG_TAG, "Pusher for " + getLogId() + " is exiting");
                        close();
                    } catch (Exception me) {
                        Log.e(LOG_TAG, "Got exception while closing for " + getLogId(), me);
                    } finally {
                        wakeLock.release();
                    }
                }
            };
            listeningThread = new Thread(runner);
            listeningThread.start();
        }

        @Override
        protected void handleUntaggedResponse(ImapResponse response) {
            if (response.mTag == null && response.size() > 1) {
                Object responseType = response.get(1);
                if (ImapResponseParser.equalsIgnoreCase(responseType, "FETCH")
                        || ImapResponseParser.equalsIgnoreCase(responseType, "EXPUNGE")
                        || ImapResponseParser.equalsIgnoreCase(responseType, "EXISTS")) {
                    if (DEBUG)
                        Log.d(LOG_TAG, "Storing response " + response + " for later processing");

                    storedUntaggedResponses.add(response);
                }
                handlePossibleUidNext(response);
            }
        }

        protected void processUntaggedResponses(List<ImapResponse> responses) throws MessagingException {
            boolean skipSync = false;
            int oldMessageCount = mMessageCount;
            if (oldMessageCount == -1) {
                skipSync = true;
            }
            List<Long> flagSyncMsgSeqs = new ArrayList<Long>();
            List<String> removeMsgUids = new LinkedList<String>();

            for (ImapResponse response : responses) {
                oldMessageCount += processUntaggedResponse(oldMessageCount, response, flagSyncMsgSeqs, removeMsgUids);
            }
            if (!skipSync) {
                if (oldMessageCount < 0) {
                    oldMessageCount = 0;
                }
                if (mMessageCount > oldMessageCount) {
                    syncMessages(mMessageCount, true);
                }
            }
            if (DEBUG)
                Log.d(LOG_TAG, "UIDs for messages needing flag sync are " + flagSyncMsgSeqs + "  for " + getLogId());

            if (!flagSyncMsgSeqs.isEmpty()) {
                syncMessages(flagSyncMsgSeqs);
            }
            if (!removeMsgUids.isEmpty()) {
                removeMessages(removeMsgUids);
            }
        }

        private void syncMessages(int end, boolean newArrivals) throws MessagingException {
            long oldUidNext = -1L;
            try {
                String pushStateS = receiver.getPushState(getName());
                ImapPushState pushState = ImapPushState.parse(pushStateS);
                oldUidNext = pushState.uidNext;
                if (DEBUG)
                    Log.i(LOG_TAG, "Got oldUidNext " + oldUidNext + " for " + getLogId());
            } catch (Exception e) {
                Log.e(LOG_TAG, "Unable to get oldUidNext for " + getLogId(), e);
            }

            ImapMessage[] messageArray = getMessages(end, end, null, true, null);
            if (messageArray != null && messageArray.length > 0) {
                long newUid = Long.parseLong(messageArray[0].getUid());
                if (DEBUG)
                    Log.i(LOG_TAG, "Got newUid " + newUid + " for message " + end + " on " + getLogId());
                long startUid = oldUidNext;
                if (startUid < newUid - 10) {
                    startUid = newUid - 10;
                }
                if (startUid < 1) {
                    startUid = 1;
                }
                if (newUid >= startUid) {

                    if (DEBUG)
                        Log.i(LOG_TAG, "Needs sync from uid " + startUid  + " to " + newUid + " for " + getLogId());
                    List<ImapMessage> messages = new ArrayList<ImapMessage>();
                    for (long uid = startUid; uid <= newUid; uid++) {
                        ImapMessage message = new ImapMessage(Long.toString(uid), ImapFolderPusher.this);
                        messages.add(message);
                    }
                    if (!messages.isEmpty()) {
                        pushMessages(messages, true);
                    }
                }
            }
        }

        private void syncMessages(List<Long> flagSyncMsgSeqs) {
            try {
            	ImapMessage[] messageArray = null;

                messageArray = getMessages(flagSyncMsgSeqs, true, null);

                List<ImapMessage> messages = new ArrayList<ImapMessage>();
                messages.addAll(Arrays.asList(messageArray));
                pushMessages(messages, false);

            } catch (Exception e) {
                receiver.pushError("Exception while processing Push untagged responses", e);
            }
        }

        private void removeMessages(List<String> removeUids) {
            List<ImapMessage> messages = new ArrayList<ImapMessage>(removeUids.size());

            try {
            	ImapMessage[] existingMessages = getMessagesFromUids(removeUids, true, null);
                for (ImapMessage existingMessage : existingMessages) {
                    needsPoll.set(true);
                    msgSeqUidMap.clear();
                    String existingUid = existingMessage.getUid();
                    Log.w(LOG_TAG, "Message with UID " + existingUid + " still exists on server, not expunging");
                    removeUids.remove(existingUid);
                }
                for (String uid : removeUids) {
                    ImapMessage message = new ImapMessage(uid, this);
                    try {
                        message.setFlagInternal(Flag.DELETED, true);
                    } catch (MessagingException me) {
                        Log.e(LOG_TAG, "Unable to set DELETED flag on message " + message.getUid());
                    }
                    messages.add(message);
                }
                receiver.messagesRemoved(this, messages);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Cannot remove EXPUNGEd messages", e);
            }

        }

        protected int processUntaggedResponse(long oldMessageCount, ImapResponse response, List<Long> flagSyncMsgSeqs, List<String> removeMsgUids) {
            super.handleUntaggedResponse(response);
            int messageCountDelta = 0;
            if (response.mTag == null && response.size() > 1) {
                try {
                    Object responseType = response.get(1);
                    if (ImapResponseParser.equalsIgnoreCase(responseType, "FETCH")) {
                        Log.i(LOG_TAG, "Got FETCH " + response);
                        long msgSeq = response.getLong(0);

                        if (DEBUG)
                            Log.d(LOG_TAG, "Got untagged FETCH for msgseq " + msgSeq + " for " + getLogId());

                        if (!flagSyncMsgSeqs.contains(msgSeq)) {
                            flagSyncMsgSeqs.add(msgSeq);
                        }
                    }
                    if (ImapResponseParser.equalsIgnoreCase(responseType, "EXPUNGE")) {
                        long msgSeq = response.getLong(0);
                        if (msgSeq <= oldMessageCount) {
                            messageCountDelta = -1;
                        }
                        if (DEBUG)
                            Log.d(LOG_TAG, "Got untagged EXPUNGE for msgseq " + msgSeq + " for " + getLogId());

                        List<Long> newSeqs = new ArrayList<Long>();
                        Iterator<Long> flagIter = flagSyncMsgSeqs.iterator();
                        while (flagIter.hasNext()) {
                            long flagMsg = flagIter.next();
                            if (flagMsg >= msgSeq) {
                                flagIter.remove();
                                if (flagMsg > msgSeq) {
                                    newSeqs.add(flagMsg--);
                                }
                            }
                        }
                        flagSyncMsgSeqs.addAll(newSeqs);


                        List<Long> msgSeqs = new ArrayList<Long>(msgSeqUidMap.keySet());
                        Collections.sort(msgSeqs);  // Have to do comparisons in order because of msgSeq reductions

                        for (long msgSeqNum : msgSeqs) {
                            if (DEBUG) {
                                Log.v(LOG_TAG, "Comparing EXPUNGEd msgSeq " + msgSeq + " to " + msgSeqNum);
                            }
                            if (msgSeqNum == msgSeq) {
                                String uid = msgSeqUidMap.get(msgSeqNum);
                                if (DEBUG) {
                                    Log.d(LOG_TAG, "Scheduling removal of UID " + uid + " because msgSeq " + msgSeqNum + " was expunged");
                                }
                                removeMsgUids.add(uid);
                                msgSeqUidMap.remove(msgSeqNum);
                            } else if (msgSeqNum > msgSeq) {
                                String uid = msgSeqUidMap.get(msgSeqNum);
                                if (DEBUG) {
                                    Log.d(LOG_TAG, "Reducing msgSeq for UID " + uid + " from " + msgSeqNum + " to " + (msgSeqNum - 1));
                                }
                                msgSeqUidMap.remove(msgSeqNum);
                                msgSeqUidMap.put(msgSeqNum - 1, uid);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Could not handle untagged FETCH for " + getLogId(), e);
                }
            }
            return messageCountDelta;
        }


        private void pushMessages(List<ImapMessage> messages, boolean newArrivals) {
            RuntimeException holdException = null;
            try {
                if (newArrivals) {
                    receiver.messagesArrived(this, messages);
                } else {
                    receiver.messagesFlagsChanged(this, messages);
                }
            } catch (RuntimeException e) {
                holdException = e;
            }

            if (holdException != null) {
                throw holdException;
            }
        }

        public void stop() {
            stop.set(true);
            if (listeningThread != null) {
                listeningThread.interrupt();
            }
            ImapConnection conn = mConnection;
            if (conn != null) {
                if (DEBUG)
                    Log.v(LOG_TAG, "Closing mConnection to stop pushing for " + getLogId());
                conn.close();
            } else {
                Log.w(LOG_TAG, "Attempt to interrupt null mConnection to stop pushing on folderPusher for " + getLogId());
            }
        }

        public void handleAsyncUntaggedResponse(ImapResponse response) {
            if (DEBUG)
                Log.v(LOG_TAG, "Got async response: " + response);

            if (stop.get()) {
                if (DEBUG)
                    Log.d(LOG_TAG, "Got async untagged response: " + response + ", but stop is set for " + getLogId());

                try {
                    sendDone();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Exception while sending DONE for " + getLogId(), e);
                }
            } else {
                if (response.mTag == null) {
                    if (response.size() > 1) {
                        boolean started = false;
                        Object responseType = response.get(1);
                        if (ImapResponseParser.equalsIgnoreCase(responseType, "EXISTS") || ImapResponseParser.equalsIgnoreCase(responseType, "EXPUNGE") ||
                                ImapResponseParser.equalsIgnoreCase(responseType, "FETCH")) {
                            if (!started) {
                                wakeLock.acquire(PUSH_WAKE_LOCK_TIMEOUT);
                                started = true;
                            }

                            if (DEBUG)
                                Log.d(LOG_TAG, "Got useful async untagged response: " + response + " for " + getLogId());

                            try {
                                sendDone();
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "Exception while sending DONE for " + getLogId(), e);
                            }
                        }
                    } else if (response.mCommandContinuationRequested) {
                        if (DEBUG)
                            Log.d(LOG_TAG, "Idling " + getLogId());

                        wakeLock.release();
                    }
                }
            }
        }
    }
    
    public ImapPusher getPusher(PushReceiver receiver) {
        return new ImapPusher(this, receiver);
    }

    public class ImapPusher implements Pusher {
        final ImapStore mStore;
        final PushReceiver mReceiver;
        private long lastRefresh = -1;

        HashMap<String, ImapFolderPusher> folderPushers = new HashMap<String, ImapFolderPusher>();

        public ImapPusher(ImapStore store, PushReceiver receiver) {
            mStore = store;
            mReceiver = receiver;
        }

        public void start(List<String> folderNames) {
            
            synchronized (folderPushers) {
                setLastRefresh(System.currentTimeMillis());
                for (String folderName : folderNames) {
                    ImapFolderPusher pusher = folderPushers.get(folderName);
                    if (pusher == null) {
                        pusher = new ImapFolderPusher(mStore, folderName, mReceiver);
                        folderPushers.put(folderName, pusher);
                        pusher.start();
                    }
                }
            }
        }

        public void refresh() {
            synchronized (folderPushers) {
                for (ImapFolderPusher folderPusher : folderPushers.values()) {
                    try {
                        folderPusher.refresh();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Got exception while refreshing for " + folderPusher.getName(), e);
                    }
                }
            }
        }

        public void stop() {
            if (DEBUG)
                Log.i(LOG_TAG, "Requested stop of IMAP pusher");

            new Thread() {
            
            	public void run () {
            		
		            synchronized (folderPushers) {
		                for (ImapFolderPusher folderPusher : folderPushers.values()) {
		                    try {
		                        if (DEBUG)
		                            Log.i(LOG_TAG, "Requesting stop of IMAP folderPusher " + folderPusher.getName());
		                        folderPusher.stop();
		                    } catch (Exception e) {
		                        Log.e(LOG_TAG, "Got exception while stopping " + folderPusher.getName(), e);
		                    }
		                }
		                folderPushers.clear();
		            }
            	}
            }.start();
        }

        public int getRefreshInterval() {
            return (mAccount.getIdleRefreshMinutes() * 60 * 1000);
        }

        public long getLastRefresh() {
            return lastRefresh;
        }

        public void setLastRefresh(long lastRefresh) {
            this.lastRefresh = lastRefresh;
        }
        
        public PushReceiver getReceiver() {
        	return mReceiver;
        }

    }
    private interface UntaggedHandler {
        void handleAsyncUntaggedResponse(ImapResponse respose);
    }

    protected static class ImapPushState {
        protected long uidNext;
        protected ImapPushState(long nUidNext) {
            uidNext = nUidNext;
        }
        protected static ImapPushState parse(String pushState) {
            long newUidNext = -1L;
            if (pushState != null) {
                StringTokenizer tokenizer = new StringTokenizer(pushState, ";");
                while (tokenizer.hasMoreTokens()) {
                    StringTokenizer thisState = new StringTokenizer(tokenizer.nextToken(), "=");
                    if (thisState.hasMoreTokens()) {
                        String key = thisState.nextToken();

                        if ("uidNext".equalsIgnoreCase(key) && thisState.hasMoreTokens()) {
                            String value = thisState.nextToken();
                            try {
                                newUidNext = Long.parseLong(value);
                            } catch (NumberFormatException e) {
                                Log.e(LOG_TAG, "Unable to part uidNext value " + value, e);
                            }

                        }
                    }
                }
            }
            return new ImapPushState(newUidNext);
        }
        @Override
        public String toString() {
            return "uidNext=" + uidNext;
        }

    }
    private interface ImapSearcher {
        List<ImapResponse> search() throws IOException, MessagingException;
    }

    private static class FetchBodyCallback implements ImapResponseParser.IImapResponseCallback {
        private HashMap<String, ImapMessage> mMessageMap;

        FetchBodyCallback(HashMap<String, ImapMessage> mesageMap) {
            mMessageMap = mesageMap;
        }

        @Override
        public Object foundLiteral(ImapResponse response,
                                   FixedLengthInputStream literal) throws IOException, Exception {
            if (response.mTag == null &&
                    ImapResponseParser.equalsIgnoreCase(response.get(1), "FETCH")) {
                ImapList fetchList = (ImapList)response.getKeyedValue("FETCH");
                String uid = fetchList.getKeyedString("UID");

                ImapMessage message = (ImapMessage) mMessageMap.get(uid);
                message.parse(literal);

                // Return placeholder object
                return Integer.valueOf(1);
            }
            return null;
        }
    }

    private static class FetchPartCallback implements ImapResponseParser.IImapResponseCallback {
        private Entity mPart;

        FetchPartCallback(Entity part) {
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
}
