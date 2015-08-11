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
 * This file contains code distributed with K-9 sources
 * that didn't include any copyright attribution header.
 *    
 */
package ch.carteggio.net.imap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import android.util.Log;
import ch.carteggio.net.MessagingException;
import ch.carteggio.net.PeekableInputStream;
import ch.carteggio.net.imap.parsing.IImapResponseCallback;
import ch.carteggio.net.imap.parsing.ImapList;
import ch.carteggio.net.imap.parsing.ImapResponse;
import ch.carteggio.net.imap.parsing.ImapResponseParser;
import ch.carteggio.net.security.Authentication;
import ch.carteggio.net.security.AuthenticationFailedException;
import ch.carteggio.net.security.CertificateValidationException;
import ch.carteggio.net.security.ConnectionSecurity;
import ch.carteggio.net.security.TrustManagerFactory;
import ch.carteggio.net.security.TrustedSocketFactory;
import ch.carteggio.net.smtp.EOLConvertingOutputStream;

/**
 * A cacheable class that stores the details for a single IMAP connection.
 */
class ImapConnection {
	
	private Socket mSocket;
	private PeekableInputStream mIn;
	private OutputStream mOut;
	private ImapResponseParser mParser;
	private int mNextCommandTag;
	private Set<String> mCapabilities = new HashSet<String>();
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
			if (!response.isEmpty()
					&& ImapResponseParser.equalsIgnoreCase(response.get(0),
							"OK")) {
				for (Object thisPart : response) {
					if (thisPart instanceof ImapList) {
						ImapList thisList = (ImapList) thisPart;
						if (ImapResponseParser.equalsIgnoreCase(
								thisList.get(0),
								ImapStore.CAPABILITY_CAPABILITY)) {
							capabilityList = thisList;
							break;
						}
					}
				}
			} else if (response.mTag == null) {
				capabilityList = response;
			}

			if (capabilityList != null
					&& !capabilityList.isEmpty()
					&& ImapResponseParser.equalsIgnoreCase(
							capabilityList.get(0),
							ImapStore.CAPABILITY_CAPABILITY)) {
				if (ImapStore.DEBUG) {
					Log.d(ImapStore.LOG_TAG, "Saving " + capabilityList.size()
							+ " capabilities for " + getLogId());
				}
				for (Object capability : capabilityList) {
					if (capability instanceof String) {
						// if (DEBUG)
						// {
						// Log.v(LOG_TAG, "Saving capability '" + capability +
						// "' for " + getLogId());
						// }
						mCapabilities.add(((String) capability)
								.toUpperCase(Locale.US));
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
			Log.w(ImapStore.LOG_TAG, "Could not set DNS ttl to 0 for "
					+ getLogId(), e);
		}

		try {
			Security.setProperty("networkaddress.cache.negative.ttl", "0");
		} catch (Exception e) {
			Log.w(ImapStore.LOG_TAG, "Could not set DNS negative ttl to 0 for "
					+ getLogId(), e);
		}

		try {
			ConnectionSecurity connectionSecurity = mSettings.mConnectionSecurity;

			// Try all IPv4 and IPv6 addresses of the host
			InetAddress[] addresses = InetAddress.getAllByName(mSettings.mHost);
			for (int i = 0; i < addresses.length; i++) {
				try {
					if (ImapStore.DEBUG && ImapStore.DEBUG_PROTOCOL_IMAP) {
						Log.d(ImapStore.LOG_TAG, "Connecting to "
								+ mSettings.mHost + " as " + addresses[i]);
					}

					SocketAddress socketAddress = new InetSocketAddress(
							addresses[i], mSettings.mPort);

					if (connectionSecurity == ConnectionSecurity.SSL_TLS_REQUIRED) {
						SSLContext sslContext = SSLContext.getInstance("TLS");
						sslContext.init(null,
								new TrustManager[] { TrustManagerFactory.get(
										mSettings.mHost, mSettings.mPort) },
								new SecureRandom());
						mSocket = TrustedSocketFactory.createSocket(sslContext);
					} else {
						mSocket = new Socket();
					}

					mSocket.connect(socketAddress,
							ImapStore.SOCKET_CONNECT_TIMEOUT);

					// Successfully connected to the server; don't try any other
					// addresses
					break;
				} catch (SocketException e) {
					if (i < (addresses.length - 1)) {
						// There are still other addresses for that host to try
						continue;
					}
					throw new MessagingException("Cannot connect to host", e);
				}
			}

			setReadTimeout(ImapStore.SOCKET_READ_TIMEOUT);

			mIn = new PeekableInputStream(new BufferedInputStream(
					mSocket.getInputStream(), 1024));
			mParser = new ImapResponseParser(mIn);
			mOut = new BufferedOutputStream(mSocket.getOutputStream(), 1024);

			mCapabilities.clear();
			ImapResponse nullResponse = mParser.readResponse();
			if (ImapStore.DEBUG && ImapStore.DEBUG_PROTOCOL_IMAP)
				Log.v(ImapStore.LOG_TAG, getLogId() + "<<<" + nullResponse);

			List<ImapResponse> nullResponses = new LinkedList<ImapResponse>();
			nullResponses.add(nullResponse);
			receiveCapabilities(nullResponses);

			if (!hasCapability(ImapStore.CAPABILITY_CAPABILITY)) {
				if (ImapStore.DEBUG)
					Log.i(ImapStore.LOG_TAG,
							"Did not get capabilities in banner, requesting CAPABILITY for "
									+ getLogId());
				List<ImapResponse> responses = receiveCapabilities(executeSimpleCommand(ImapStore.COMMAND_CAPABILITY));
				if (responses.size() != 2) {
					throw new MessagingException(
							"Invalid CAPABILITY response received");
				}
			}

			if (mSettings.mConnectionSecurity == ConnectionSecurity.STARTTLS_REQUIRED) {

				if (hasCapability("STARTTLS")) {
					// STARTTLS
					executeSimpleCommand("STARTTLS");

					SSLContext sslContext = SSLContext.getInstance("TLS");
					sslContext.init(null,
							new TrustManager[] { TrustManagerFactory.get(
									mSettings.mHost, mSettings.mPort) },
							new SecureRandom());
					mSocket = TrustedSocketFactory.createSocket(sslContext,
							mSocket, mSettings.mHost, mSettings.mPort, true);
					mSocket.setSoTimeout(ImapStore.SOCKET_READ_TIMEOUT);
					mIn = new PeekableInputStream(new BufferedInputStream(
							mSocket.getInputStream(), 1024));
					mParser = new ImapResponseParser(mIn);
					mOut = new BufferedOutputStream(mSocket.getOutputStream(),
							1024);
					// Per RFC 2595 (3.1): Once TLS has been started, reissue
					// CAPABILITY command
					if (ImapStore.DEBUG)
						Log.i(ImapStore.LOG_TAG,
								"Updating capabilities after STARTTLS for "
										+ getLogId());
					mCapabilities.clear();
					List<ImapResponse> responses = receiveCapabilities(executeSimpleCommand(ImapStore.COMMAND_CAPABILITY));
					if (responses.size() != 2) {
						throw new MessagingException(
								"Invalid CAPABILITY response received");
					}
				} else {
					/*
					 * This exception triggers a "Certificate error"
					 * notification that takes the user to the incoming server
					 * settings for review. This might be needed if the account
					 * was configured with an obsolete "STARTTLS (if available)"
					 * setting.
					 */
					throw new CertificateValidationException(
							"STARTTLS connection security not available",
							new CertificateException());
				}
			}

			switch (mSettings.mAuthType) {
			case CRAM_MD5:
				if (hasCapability(ImapStore.CAPABILITY_AUTH_CRAM_MD5)) {
					authCramMD5();
				} else {
					throw new MessagingException(
							"Server doesn't support encrypted passwords using CRAM-MD5.");
				}
				break;

			case PLAIN:
				if (hasCapability(ImapStore.CAPABILITY_AUTH_PLAIN)) {
					saslAuthPlain();
				} else if (!hasCapability(ImapStore.CAPABILITY_LOGINDISABLED)) {
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
			if (ImapStore.DEBUG) {
				Log.d(ImapStore.LOG_TAG, ImapStore.CAPABILITY_COMPRESS_DEFLATE
						+ " = "
						+ hasCapability(ImapStore.CAPABILITY_COMPRESS_DEFLATE));
			}
			if (hasCapability(ImapStore.CAPABILITY_COMPRESS_DEFLATE)) {
				ConnectivityManager connectivityManager = (ConnectivityManager) mContext
						.getSystemService(Context.CONNECTIVITY_SERVICE);
				boolean useCompression = true;

				NetworkInfo netInfo = connectivityManager
						.getActiveNetworkInfo();
				if (netInfo != null) {
					int type = netInfo.getType();
					if (ImapStore.DEBUG)
						Log.d(ImapStore.LOG_TAG, "On network type " + type);
					useCompression = mSettings.useCompression(type);

				}
				if (ImapStore.DEBUG)
					Log.d(ImapStore.LOG_TAG, "useCompression " + useCompression);
				if (useCompression) {
					try {
						executeSimpleCommand(ImapStore.COMMAND_COMPRESS_DEFLATE);
						Inflater inf = new Inflater(true);
						InflaterInputStream zInputStream = new InflaterInputStream(
								mSocket.getInputStream(), inf);
						mIn = new PeekableInputStream(new BufferedInputStream(
								zInputStream, 1024));
						mParser = new ImapResponseParser(mIn);
						GZIPOutputStream zOutputStream = new GZIPOutputStream(
								mSocket.getOutputStream());
						mOut = new BufferedOutputStream(zOutputStream, 1024);
						if (ImapStore.DEBUG) {
							Log.i(ImapStore.LOG_TAG, "Compression enabled for "
									+ getLogId());
						}
					} catch (Exception e) {
						Log.e(ImapStore.LOG_TAG,
								"Unable to negotiate compression", e);
					}
				}
			}

			if (ImapStore.DEBUG)
				Log.d(ImapStore.LOG_TAG, "NAMESPACE = "
						+ hasCapability(ImapStore.CAPABILITY_NAMESPACE)
						+ ", mPathPrefix = " + mSettings.mPathPrefix);

			if (mSettings.mPathPrefix == null) {
				if (hasCapability(ImapStore.CAPABILITY_NAMESPACE)) {
					if (ImapStore.DEBUG)
						Log.i(ImapStore.LOG_TAG,
								"mPathPrefix is unset and server has NAMESPACE capability");
					List<ImapResponse> namespaceResponses = executeSimpleCommand(ImapStore.COMMAND_NAMESPACE);
					for (ImapResponse response : namespaceResponses) {
						if (ImapResponseParser.equalsIgnoreCase(
								response.get(0), ImapStore.COMMAND_NAMESPACE)) {
							if (ImapStore.DEBUG)
								Log.d(ImapStore.LOG_TAG,
										"Got NAMESPACE response " + response
												+ " on " + getLogId());

							Object personalNamespaces = response.get(1);
							if (personalNamespaces != null
									&& personalNamespaces instanceof ImapList) {
								if (ImapStore.DEBUG)
									Log.d(ImapStore.LOG_TAG,
											"Got personal namespaces: "
													+ personalNamespaces);
								ImapList bracketed = (ImapList) personalNamespaces;
								Object firstNamespace = bracketed.get(0);
								if (firstNamespace != null
										&& firstNamespace instanceof ImapList) {
									if (ImapStore.DEBUG)
										Log.d(ImapStore.LOG_TAG,
												"Got first personal namespaces: "
														+ firstNamespace);
									bracketed = (ImapList) firstNamespace;
									mSettings.mPathPrefix = bracketed
											.getString(0);
									mSettings.mPathDelimeter = bracketed
											.getString(1);
									mSettings.mCombinedPrefix = null;
									if (ImapStore.DEBUG)
										Log.d(ImapStore.LOG_TAG, "Got path '"
												+ mSettings.mPathPrefix
												+ "' and separator '"
												+ mSettings.mPathDelimeter
												+ "'");
								}
							}
						}
					}
				} else {
					if (ImapStore.DEBUG)
						Log.i(ImapStore.LOG_TAG,
								"mPathPrefix is unset but server does not have NAMESPACE capability");
					mSettings.mPathPrefix = "";
				}
			}
			if (mSettings.mPathDelimeter == null) {
				try {
					List<ImapResponse> nameResponses = executeSimpleCommand("LIST \"\" \"\"");
					for (ImapResponse response : nameResponses) {
						if (ImapResponseParser.equalsIgnoreCase(
								response.get(0), "LIST")) {
							mSettings.mPathDelimeter = response.getString(2);
							mSettings.mCombinedPrefix = null;
							if (ImapStore.DEBUG)
								Log.d(ImapStore.LOG_TAG, "Got path delimeter '"
										+ mSettings.mPathDelimeter + "' for "
										+ getLogId());
						}
					}
				} catch (Exception e) {
					Log.e(ImapStore.LOG_TAG,
							"Unable to get path delimeter using LIST", e);
				}
			}

		} catch (SSLException e) {
			throw new CertificateValidationException(e.getMessage(), e);
		} catch (GeneralSecurityException gse) {
			throw new MessagingException(
					"Unable to open connection to IMAP server due to security error.",
					gse);
		} catch (ConnectException ce) {
			String ceMess = ce.getMessage();
			String[] tokens = ceMess.split("-");
			if (tokens != null && tokens.length > 1 && tokens[1] != null) {
				Log.e(ImapStore.LOG_TAG,
						"Stripping host/port from ConnectionException for "
								+ getLogId(), ce);
				throw new ConnectException(tokens[1].trim());
			} else {
				throw ce;
			}
		} finally {
			if (!authSuccess) {
				Log.e(ImapStore.LOG_TAG,
						"Failed to login, closing connection for " + getLogId());
				close();
			}
		}
	}

	protected void login() throws IOException, MessagingException {
		/*
		 * Use quoted strings which permit spaces and quotes. (Using IMAP string
		 * literals would be better, but some servers are broken and don't parse
		 * them correctly.)
		 */

		// escape double-quotes and backslash characters with a backslash
		Pattern p = Pattern.compile("[\\\\\"]");
		String replacement = "\\\\$0";
		String username = p.matcher(mSettings.mUsername)
				.replaceAll(replacement);
		String password = p.matcher(mSettings.mPassword)
				.replaceAll(replacement);
		try {
			receiveCapabilities(executeSimpleCommand(
					String.format("LOGIN \"%s\" \"%s\"", username, password),
					true));
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
		mOut.write(Base64.encode(
				("\000" + mSettings.mUsername + "\000" + mSettings.mPassword)
						.getBytes(), Base64.NO_WRAP));
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
					Log.w(ImapStore.LOG_TAG, "After sending tag " + tag
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
			if (ImapStore.DEBUG && ImapStore.DEBUG_PROTOCOL_IMAP)
				Log.v(ImapStore.LOG_TAG, getLogId() + "<<<" + response);

			if (response.mTag != null && !response.mTag.equalsIgnoreCase(tag)) {
				Log.w(ImapStore.LOG_TAG, "After sending tag " + tag
						+ ", got tag response from previous command "
						+ response + " for " + getLogId());
				Iterator<ImapResponse> iter = responses.iterator();
				while (iter.hasNext()) {
					ImapResponse delResponse = iter.next();
					if (delResponse.mTag != null
							|| delResponse.size() < 2
							|| (!ImapResponseParser.equalsIgnoreCase(
									delResponse.get(1), "EXISTS") && !ImapResponseParser
									.equalsIgnoreCase(delResponse.get(1),
											"EXPUNGE"))) {
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
		if (response.size() < 1
				|| !ImapResponseParser.equalsIgnoreCase(response.get(0), "OK")) {
			throw new ImapException("Command: " + commandToLog + "; response: "
					+ response.toString(), response.getAlertText());
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
		if (ImapStore.DEBUG)
			Log.v(ImapStore.LOG_TAG, "Connection " + getLogId() + " has "
					+ mCapabilities.size() + " capabilities");

		return mCapabilities.contains(ImapStore.CAPABILITY_IDLE);
	}

	public boolean hasCapability(String capability) {
		return mCapabilities.contains(capability.toUpperCase(Locale.US));
	}

	public boolean isOpen() {
		return (mIn != null && mOut != null && mSocket != null
				&& mSocket.isConnected() && !mSocket.isClosed());
	}

	public void close() {

		try {
			if (mIn != null)
				mIn.close();
		} catch (IOException ex) {
		}
		;
		try {
			if (mIn != null)
				mOut.close();
		} catch (IOException ex) {
		}
		;
		try {
			if (mIn != null)
				mSocket.close();
		} catch (IOException ex) {
		}
		;

		mIn = null;
		mOut = null;
		mSocket = null;
	}

	public ImapResponse readResponse() throws IOException, MessagingException {
		return readResponse(null);
	}

	public ImapResponse readResponse(IImapResponseCallback callback)
			throws IOException {
		try {
			ImapResponse response = mParser.readResponse(callback);
			if (ImapStore.DEBUG && ImapStore.DEBUG_PROTOCOL_IMAP)
				Log.v(ImapStore.LOG_TAG, getLogId() + "<<<" + response);

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

		if (ImapStore.DEBUG && ImapStore.DEBUG_PROTOCOL_IMAP)
			Log.v(ImapStore.LOG_TAG, getLogId() + ">>> " + continuation);

	}

	public String sendCommand(String command, boolean sensitive)
			throws MessagingException, IOException {
		try {
			open();
			String tag = Integer.toString(mNextCommandTag++);
			String commandToSend = tag + " " + command + "\r\n";
			mOut.write(commandToSend.getBytes());
			mOut.flush();

			if (ImapStore.DEBUG && ImapStore.DEBUG_PROTOCOL_IMAP) {
				if (sensitive && !ImapStore.DEBUG_SENSITIVE) {
					Log.v(ImapStore.LOG_TAG,
							getLogId()
									+ ">>> "
									+ "[Command Hidden, Enable Sensitive Debug Logging To Show]");
				} else {
					Log.v(ImapStore.LOG_TAG, getLogId() + ">>> "
							+ commandToSend);
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

	public List<ImapResponse> executeSimpleCommand(String command)
			throws IOException, ImapException, MessagingException {
		return executeSimpleCommand(command, false, null);
	}

	public List<ImapResponse> executeSimpleCommand(String command,
			boolean sensitive) throws IOException, ImapException,
			MessagingException {
		return executeSimpleCommand(command, sensitive, null);
	}

	public List<ImapResponse> executeSimpleCommand(String command,
			boolean sensitive, UntaggedHandler untaggedHandler)
			throws IOException, ImapException, MessagingException {
		String commandToLog = command;
		if (sensitive && !ImapStore.DEBUG_SENSITIVE) {
			commandToLog = "*sensitive*";
		}

		// if (DEBUG)
		// Log.v(LOG_TAG, "Sending IMAP command " + commandToLog +
		// " on connection " + getLogId());

		String tag = sendCommand(command, sensitive);
		// if (DEBUG)
		// Log.v(LOG_TAG, "Sent IMAP command " + commandToLog + " with tag " +
		// tag + " for " + getLogId());

		return readStatusResponse(tag, commandToLog, untaggedHandler);
	}

	public void writeMessage(ImapMessage message) throws IOException {

		EOLConvertingOutputStream eolOut;
		
		eolOut = new EOLConvertingOutputStream(mOut);
		
		message.writeTo(eolOut);
		eolOut.write('\r');
		eolOut.write('\n');
		eolOut.flush();
	
	}
	
	interface UntaggedHandler {
	    void handleAsyncUntaggedResponse(ImapResponse respose);
	}
}