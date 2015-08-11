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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MultipartImpl;
import org.apache.james.mime4j.storage.StorageBodyFactory;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.util.MimeUtil;

import android.annotation.SuppressLint;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import ch.carteggio.net.MessagingException;
import ch.carteggio.net.imap.ImapConnection.UntaggedHandler;
import ch.carteggio.net.imap.parsing.FetchBodyCallback;
import ch.carteggio.net.imap.parsing.FetchPartCallback;
import ch.carteggio.net.imap.parsing.IImapResponseCallback;
import ch.carteggio.net.imap.parsing.ImapList;
import ch.carteggio.net.imap.parsing.ImapResponse;
import ch.carteggio.net.imap.parsing.ImapResponseParser;

public class ImapSession {

	public enum FolderType {
	    HOLDS_FOLDERS, HOLDS_MESSAGES,
	}
	
	protected ImapFolderState mState;
	
	public static final int OPEN_MODE_RW = 0;
	public static final int OPEN_MODE_RO = 1;
	
	protected volatile ImapConnection mConnection;

    private final SimpleDateFormat RFC3501_DATE = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);

	private int mMode;
	protected ImapStore mStore = null;
	private boolean mInSearch = false;
	private boolean mCanCreateKeywords;
    private Set<Flag> mPermanentFlagsIndex = new HashSet<Flag>();

    private ImapFolderName mName;
    
	public ImapSession(ImapStore store, String name) {
		mStore = store;
		this.mState = new ImapFolderState(name);
		this.mName = new ImapFolderName(name, mStore);
	}

	protected List<ImapResponse> executeSimpleCommand(String command)
			throws MessagingException, IOException {
		return mState.handleUntaggedResponses(mConnection
				.executeSimpleCommand(command));
	}

	protected List<ImapResponse> executeSimpleCommand(String command,
			boolean sensitive, UntaggedHandler untaggedHandler)
			throws MessagingException, IOException {
		return mState.handleUntaggedResponses(mConnection.executeSimpleCommand(
				command, sensitive, untaggedHandler));
	}

	public void open(int mode) throws MessagingException {
		internalOpen(mode);

		if (mState.getMessageCount() == -1) {
			throw new MessagingException(
					"Did not find message count during open");
		}
	}

	protected List<ImapResponse> internalOpen(int mode) throws MessagingException {
		if (isOpen() && mMode == mode) {
			// Make sure the connection is valid. If it's not we'll close it
			// down and continue
			// on to get a new one.
			try {
				List<ImapResponse> responses = executeSimpleCommand("NOOP");
				return responses;
			} catch (IOException ioe) {
				ioExceptionHandler(mConnection, ioe);
			}
		}
		mStore.releaseConnection(mConnection);
		synchronized (this) {
			mConnection = mStore.getConnection();
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
			mState.invalidate();
			String command = String.format("%s %s",
					mode == OPEN_MODE_RW ? "SELECT" : "EXAMINE", ImapUtility
							.encodeString(ImapUtility
									.encodeFolderName(mName.getPrefixedName())));

			List<ImapResponse> responses = executeSimpleCommand(command);

			/*
			 * If the command succeeds we expect the folder has been opened
			 * read-write unless we are notified otherwise in the responses.
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
						// parse: * OK [PERMANENTFLAGS (\Answered \Flagged
						// \Deleted
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
			
			return responses;
		} catch (IOException ioe) {
			throw ioExceptionHandler(mConnection, ioe);
		} catch (MessagingException me) {
			Log.e(ImapStore.LOG_TAG, "Unable to open connection for "
					+ getLogId(), me);
			throw me;
		}
	}

	/**
	 * Parses an string like PERMANENTFLAGS (\Answered \Flagged \Deleted //
	 * \Seen \Draft NonJunk $label1 \*)
	 * 
	 * the parsed flags are stored in the mPermanentFlagsIndex
	 * 
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
		
		mState.invalidate();
		
		if (!isOpen()) {
			return;
		}

		synchronized (this) {
			// If we are mid-search and we get a close request, we gotta trash
			// the connection.
			if (mInSearch && mConnection != null) {
				Log.i(ImapStore.LOG_TAG,
						"IMAP search was aborted, shutting down connection.");
				mConnection.close();
			} else {
				mStore.releaseConnection(mConnection);
			}
			mConnection = null;
		}
	}
	
	public ImapFolderState getFolderState() {
		return mState;
	}

	/**
	 * Check if a given folder exists on the server.
	 * 
	 * @param folderName
	 *            The name of the folder encoded as quoted string. See
	 *            {@link ImapUtility#encodeString}
	 * 
	 * @return {@code True}, if the folder exists. {@code False}, otherwise.
	 */
	private boolean exists(String folderName) throws MessagingException {
		try {
			// Since we don't care about RECENT, we'll use that for the check,
			// because we're checking
			// a folder other than ourself, and don't want any untagged
			// responses to cause a change
			// in our own fields
			mConnection.executeSimpleCommand(String.format(
					"STATUS %s (RECENT)", folderName));
			return true;
		} catch (IOException ioe) {
			throw ioExceptionHandler(mConnection, ioe);
		} catch (ImapException ie) {
			// We got a response, but it was not "OK"
			return false;
		}
	}



	/**
	 * Copies the given messages to the specified folder.
	 * 
	 * <p>
	 * <strong>Note:</strong> Only the UIDs of the given {@link Message}
	 * instances are used. It is assumed that all UIDs represent valid messages
	 * in this folder.
	 * </p>
	 * 
	 * @param messages
	 *            The messages to copy to the specfied folder.
	 * @param folder
	 *            The name of the target folder.
	 * 
	 * @return The mapping of original message UIDs to the new server UIDs.
	 */
	public Map<String, String> copyMessages(ImapMessage[] messages,
			ImapSession folder) throws MessagingException {
		if (!(folder instanceof ImapSession)) {
			throw new MessagingException(
					"ImapFolder.copyMessages passed non-ImapFolder");
		}

		if (messages.length == 0) {
			return null;
		}

		ImapSession iFolder = (ImapSession) folder;
		checkOpen(); // only need READ access

		Long[] uids = new Long[messages.length];
		for (int i = 0, count = messages.length; i < count; i++) {
			uids[i] = messages[i].getUid();
		}

		try {
			String remoteDestName = ImapUtility.encodeString(ImapUtility
					.encodeFolderName(iFolder.getFolderName().getPrefixedName()));

			// TODO: Try to copy/move the messages first and only create the
			// folder if the
			// operation fails. This will save a roundtrip if the folder already
			// exists.
			if (!exists(remoteDestName)) {
				/*
				 * If the remote folder doesn't exist we try to create it.
				 */
				if (ImapStore.DEBUG) {
					Log.i(ImapStore.LOG_TAG,
							"ImapFolder.copyMessages: attempting to create remote "
									+ "folder '" + remoteDestName + "' for "
									+ getLogId());
				}

				mStore.createFolder(iFolder.getFolderName().getName(), FolderType.HOLDS_MESSAGES);
			}

			// TODO: Split this into multiple commands if the command exceeds a
			// certain length.
			List<ImapResponse> responses = executeSimpleCommand(String.format(
					"UID COPY %s %s", ImapUtility.combine(uids, ','),
					remoteDestName));

			// Get the tagged response for the UID COPY command
			ImapResponse response = responses.get(responses.size() - 1);

			Map<String, String> uidMap = null;
			if (response.size() > 1) {
				/*
				 * If the server supports UIDPLUS, then along with the COPY
				 * response it will return an COPYUID response code, e.g.
				 * 
				 * 24 OK [COPYUID 38505 304,319:320 3956:3958] Success
				 * 
				 * COPYUID is followed by UIDVALIDITY, the set of UIDs of copied
				 * messages from the source folder and the set of corresponding
				 * UIDs assigned to them in the destination folder.
				 * 
				 * We can use the new UIDs included in this response to update
				 * our records.
				 */
				Object responseList = response.get(1);

				if (responseList instanceof ImapList) {
					final ImapList copyList = (ImapList) responseList;
					if (copyList.size() >= 4
							&& copyList.getString(0).equals("COPYUID")) {
						List<String> srcUids = ImapUtility
								.getImapSequenceValues(copyList.getString(2));
						List<String> destUids = ImapUtility
								.getImapSequenceValues(copyList.getString(3));

						if (srcUids != null && destUids != null) {
							if (srcUids.size() == destUids.size()) {
								Iterator<String> srcUidsIterator = srcUids
										.iterator();
								Iterator<String> destUidsIterator = destUids
										.iterator();
								uidMap = new HashMap<String, String>();
								while (srcUidsIterator.hasNext()
										&& destUidsIterator.hasNext()) {
									String srcUid = srcUidsIterator.next();
									String destUid = destUidsIterator.next();
									uidMap.put(srcUid, destUid);
								}
							} else {
								if (ImapStore.DEBUG) {
									Log.v(ImapStore.LOG_TAG,
											"Parse error: size of source UIDs "
													+ "list is not the same as size of destination "
													+ "UIDs list.");
								}
							}
						} else {
							if (ImapStore.DEBUG) {
								Log.v(ImapStore.LOG_TAG,
										"Parsing of the sequence set failed.");
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

	public Map<String, String> moveMessages(ImapMessage[] messages,
			ImapSession folder) throws MessagingException {
		if (messages.length == 0)
			return null;
		Map<String, String> uidMap = copyMessages(messages, folder);
		setFlags(messages, new Flag[] { Flag.DELETED }, true);
		return uidMap;
	}
	
	public ImapFolderName getFolderName() {
		return mName;
	}

	public void delete(ImapMessage[] messages, String trashFolderName)
			throws MessagingException {
		if (messages.length == 0)
			return;

		if (trashFolderName == null
				|| mName.getName().equalsIgnoreCase(trashFolderName)) {
			setFlags(messages, new Flag[] { Flag.DELETED }, true);
		} else {
			ImapSession remoteTrashFolder = (ImapSession) getStore().getSession(
					trashFolderName);
			String remoteTrashName = ImapUtility.encodeString(ImapUtility
					.encodeFolderName(remoteTrashFolder.getFolderName().getPrefixedName()));

			if (!exists(remoteTrashName)) {
				/*
				 * If the remote trash folder doesn't exist we try to create it.
				 */
				if (ImapStore.DEBUG)
					Log.i(ImapStore.LOG_TAG,
							"IMAPMessage.delete: attempting to create remote '"
									+ trashFolderName + "' folder for "
									+ getLogId());
				mStore.createFolder(remoteTrashFolder.getFolderName().getName(), FolderType.HOLDS_MESSAGES);
			}

			if (exists(remoteTrashName)) {
				if (ImapStore.DEBUG)
					Log.d(ImapStore.LOG_TAG,
							"IMAPMessage.delete: copying remote "
									+ messages.length + " messages to '"
									+ trashFolderName + "' for " + getLogId());

				moveMessages(messages, remoteTrashFolder);
			} else {
				throw new MessagingException(
						"IMAPMessage.delete: remote Trash folder "
								+ trashFolderName
								+ " does not exist and could not be created for "
								+ getLogId(), true);
			}
		}
	}

	private int getRemoteMessageCount(String criteria)
			throws MessagingException {
		checkOpen(); // only need READ access
		try {
			int count = 0;
			int start = 1;

			List<ImapResponse> responses = executeSimpleCommand(String.format(
					Locale.US, "SEARCH %d:* %s", start, criteria));
			for (ImapResponse response : responses) {
				if (ImapResponseParser.equalsIgnoreCase(response.get(0),
						"SEARCH")) {
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

	public long getHighestUid() {
		try {
			ImapSearcher searcher = new ImapSearcher() {
				public List<ImapResponse> search() throws IOException,
						MessagingException {
					return executeSimpleCommand("UID SEARCH *:*");
				}
			};
			ImapMessage[] messages = search(searcher, null).toArray(
					ImapStore.EMPTY_MESSAGE_ARRAY);
			if (messages.length > 0) {
				return messages[0].getUid();
			}
		} catch (Exception e) {
			Log.e(ImapStore.LOG_TAG, "Unable to find highest UID in folder "
					+ mName.getName(), e);
		}
		return -1L;

	}

	public void delete(boolean recurse) throws MessagingException {
		throw new Error("ImapStore.delete() not yet implemented");
	}

	public ImapMessage getMessage(long uid) throws MessagingException {
		return new ImapMessage(uid, this);
	}

	public ImapMessage[] getMessages(int start, int end, Date earliestDate,
			MessageRetrievalListener listener) throws MessagingException {
		return getMessages(start, end, earliestDate, false, listener);
	}

	public ImapMessage[] getMessagesAddedAfter(final long uid, MessageRetrievalListener listener) throws MessagingException {
		
		ImapSearcher searcher = new ImapSearcher() {
			public List<ImapResponse> search() throws IOException,
					MessagingException {
				return executeSimpleCommand(String.format(
						"UID SEARCH UID %d:* NOT DELETED", uid));
			}
		};
		
		List<ImapMessage> results = search(searcher, listener);
		
		
		// if there are no new messages the IMAP server will return the latest message
		// even if it has uid smaller than what we searched (see meaning of * operator
		// in IMAP specification)
		if ( results.size() == 1 && results.get(0).getUid() < uid) {
			results.remove(0);
		}
		
		return results
				.toArray(ImapStore.EMPTY_MESSAGE_ARRAY);
	}

	protected ImapMessage[] getMessages(final int start, final int end,
			Date earliestDate, final boolean includeDeleted,
			final MessageRetrievalListener listener) throws MessagingException {
		if (start < 1 || end < 1 || end < start) {
			throw new MessagingException(String.format(Locale.US,
					"Invalid message set %d %d", start, end));
		}
		final StringBuilder dateSearchString = new StringBuilder();
		if (earliestDate != null) {
			dateSearchString.append(" SINCE ");
			synchronized (RFC3501_DATE) {
				dateSearchString.append(RFC3501_DATE
						.format(earliestDate));
			}
		}

		ImapSearcher searcher = new ImapSearcher() {
			public List<ImapResponse> search() throws IOException,
					MessagingException {
				return executeSimpleCommand(String.format(Locale.US,
						"UID SEARCH %d:%d%s%s", start, end, dateSearchString,
						includeDeleted ? "" : " NOT DELETED"));
			}
		};
		return search(searcher, listener)
				.toArray(ImapStore.EMPTY_MESSAGE_ARRAY);

	}

	protected ImapMessage[] getMessages(final List<Long> mesgSeqs,
			final boolean includeDeleted,
			final MessageRetrievalListener listener) throws MessagingException {
		ImapSearcher searcher = new ImapSearcher() {
			public List<ImapResponse> search() throws IOException,
					MessagingException {
				return executeSimpleCommand(String.format("UID SEARCH %s%s",
						ImapUtility.combine(mesgSeqs.toArray(), ','),
						includeDeleted ? "" : " NOT DELETED"));
			}
		};
		return search(searcher, listener)
				.toArray(ImapStore.EMPTY_MESSAGE_ARRAY);
	}

	protected ImapMessage[] getMessagesFromUids(final List<Long> mesgUids,
			final boolean includeDeleted,
			final MessageRetrievalListener listener) throws MessagingException {
		ImapSearcher searcher = new ImapSearcher() {
			public List<ImapResponse> search() throws IOException,
					MessagingException {
				return executeSimpleCommand(String.format(
						"UID SEARCH UID %s%s",
						ImapUtility.combine(mesgUids.toArray(), ','),
						includeDeleted ? "" : " NOT DELETED"));
			}
		};
		return search(searcher, listener)
				.toArray(ImapStore.EMPTY_MESSAGE_ARRAY);
	}

	private List<ImapMessage> search(ImapSearcher searcher,
			MessageRetrievalListener listener) throws MessagingException {

		checkOpen(); // only need READ access
		ArrayList<ImapMessage> messages = new ArrayList<ImapMessage>();
		try {
			ArrayList<Long> uids = new ArrayList<Long>();
			List<ImapResponse> responses = searcher.search(); //
			for (ImapResponse response : responses) {
				if (response.mTag == null) {
					if (ImapResponseParser.equalsIgnoreCase(response.get(0),
							"SEARCH")) {
						for (int i = 1, count = response.size(); i < count; i++) {
							uids.add(response.getLong(i));
						}
					}
				}
			}

			// Sort the uids in numerically decreasing order
			// By doing it in decreasing order, we ensure newest messages are
			// dealt with first
			// This makes the most sense when a limit is imposed, and also
			// prevents UI from going
			// crazy adding stuff at the top.
			Collections.sort(uids, Collections.reverseOrder());

			for (int i = 0, count = uids.size(); i < count; i++) {
				Long uid = uids.get(i);
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

	public ImapMessage[] getMessages(MessageRetrievalListener listener)
			throws MessagingException {
		return getMessages(null, listener);
	}

	public ImapMessage[] getMessages(Long[] uids,
			MessageRetrievalListener listener) throws MessagingException {
		checkOpen(); // only need READ access
		ArrayList<ImapMessage> messages = new ArrayList<ImapMessage>();
		try {
			if (uids == null) {
				List<ImapResponse> responses = executeSimpleCommand("UID SEARCH 1:* NOT DELETED");
				ArrayList<Long> tempUids = new ArrayList<Long>();
				for (ImapResponse response : responses) {
					if (ImapResponseParser.equalsIgnoreCase(response.get(0),
							"SEARCH")) {
						for (int i = 1, count = response.size(); i < count; i++) {
							tempUids.add(response.getLong(i));
						}
					}
				}
				uids = tempUids.toArray(ImapStore.EMPTY_LONG_ARRAY);
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
		return messages.toArray(ImapStore.EMPTY_MESSAGE_ARRAY);
	}

	public void fetch(ImapMessage[] messages, FetchProfile fp,
			MessageRetrievalListener listener) throws MessagingException {
		if (messages == null || messages.length == 0) {
			return;
		}
		checkOpen(); // only need READ access
		List<Long> uids = new ArrayList<Long>(messages.length);
		HashMap<Long, ImapMessage> messageMap = new HashMap<Long, ImapMessage>();
		for (ImapMessage msg : messages) {
			long uid = msg.getUid();
			uids.add(uid);
			messageMap.put(uid, msg);
		}

		/*
		 * Figure out what command we are going to run: Flags - UID FETCH
		 * (FLAGS) Envelope - UID FETCH ([FLAGS] INTERNALDATE UID RFC822.SIZE
		 * FLAGS BODY.PEEK[HEADER.FIELDS (date subject from content-type to
		 * cc)])
		 */
		Set<String> fetchFields = new LinkedHashSet<String>();
		fetchFields.add("UID");
		if (fp.contains(FetchProfile.Item.FLAGS)) {
			fetchFields.add("FLAGS");
		}
		if (fp.contains(FetchProfile.Item.ENVELOPE)) {
			fetchFields.add("INTERNALDATE");
			fetchFields.add("RFC822.SIZE");
	//		fetchFields
	//				.add("BODY.PEEK[HEADER.FIELDS (date subject from content-type to cc "
	//						+ "reply-to message-id references in-reply-to user-agent )]");
			
			fetchFields.add("BODY.PEEK[HEADER]");
					
					
		}
		if (fp.contains(FetchProfile.Item.STRUCTURE)) {
			fetchFields.add("BODYSTRUCTURE");
		}
		if (fp.contains(FetchProfile.Item.BODY_SANE)) {
			// If the user wants to download unlimited-size messages, don't go
			// only for the truncated body
			if (mStore.getPreferences().getMaximumAutoDownloadMessageSize() > 0) {
				fetchFields.add(String.format(Locale.US, "BODY.PEEK[]<0.%d>",
						mStore.getPreferences().getMaximumAutoDownloadMessageSize()));
			} else {
				fetchFields.add("BODY.PEEK[]");
			}
		}
		if (fp.contains(FetchProfile.Item.BODY)) {
			fetchFields.add("BODY.PEEK[]");
		}

		for (int windowStart = 0; windowStart < messages.length; windowStart += (ImapStore.FETCH_WINDOW_SIZE)) {
			List<Long> uidWindow = uids.subList(windowStart, Math.min(
					(windowStart + ImapStore.FETCH_WINDOW_SIZE),
					messages.length));

			try {
				mConnection
						.sendCommand(String.format("UID FETCH %s (%s)",
								ImapUtility.combine(uidWindow
										.toArray(new Long[uidWindow.size()]),
										','), ImapUtility.combine(
										fetchFields
												.toArray(new String[fetchFields
														.size()]), ' ')), false);
				ImapResponse response;
				int messageNumber = 0;

				IImapResponseCallback callback = null;
				if (fp.contains(FetchProfile.Item.BODY)
						|| fp.contains(FetchProfile.Item.BODY_SANE)) {
					callback = new FetchBodyCallback(messageMap);
				}

				do {
					response = mConnection.readResponse(callback);

					if (response.mTag == null
							&& ImapResponseParser.equalsIgnoreCase(
									response.get(1), "FETCH")) {
						ImapList fetchList = (ImapList) response
								.getKeyedValue("FETCH");
						Long uid = fetchList.getKeyedNumber("UID");
						long msgSeq = response.getLong(0);
						if (uid != null) {
							try {
								
								if (ImapStore.DEBUG) {
									Log.v(ImapStore.LOG_TAG, "Stored uid '"
											+ uid + "' for msgSeq " + msgSeq
											+ " into map " /*
															 * +
															 * msgSeqUidMap.toString
															 * ()
															 */);
								}
							} catch (Exception e) {
								Log.e(ImapStore.LOG_TAG,
										"Unable to store uid '" + uid
												+ "' for msgSeq " + msgSeq);
							}
						}

						ImapMessage message = messageMap.get(uid);
						if (message == null) {
							if (ImapStore.DEBUG)
								Log.d(ImapStore.LOG_TAG,
										"Do not have message in messageMap for UID "
												+ uid + " for " + getLogId());

							mState.handleUntaggedResponse(response);
							continue;
						}
						if (listener != null) {
							listener.messageStarted(uid, messageNumber++,
									messageMap.size());
						}

						ImapMessage imapMessage = (ImapMessage) message;

						Object literal = handleFetchResponse(imapMessage,
								fetchList);

						if (literal != null) {
							if (literal instanceof String) {
								String bodyString = (String) literal;
								InputStream bodyStream = new ByteArrayInputStream(
										bodyString.getBytes());
								imapMessage.parse(bodyStream);
							} else if (literal instanceof Integer) {
								// All the work was done in
								// FetchBodyCallback.foundLiteral()
							} else {
								// This shouldn't happen
								throw new MessagingException(
										"Got FETCH response with bogus parameters");
							}
						}

						if (listener != null) {
							listener.messageFinished(message, messageNumber,
									messageMap.size());
						}
					} else {
						mState.handleUntaggedResponse(response);
					}

				} while (response.mTag == null);
			} catch (IOException ioe) {
				throw ioExceptionHandler(mConnection, ioe);
			}
		}
	}

	public void fetchPart(ImapMessage message, Entity part,
			MessageRetrievalListener listener) throws MessagingException {
		checkOpen(); // only need READ access

		if (part.getHeader() == null
				|| part.getHeader().getField(
						ImapStore.HEADER_ANDROID_ATTACHMENT_STORE_DATA) == null) {
			return;
		}

		String parts = part.getHeader()
				.getField(ImapStore.HEADER_ANDROID_ATTACHMENT_STORE_DATA)
				.getBody();
		if (parts == null) {
			return;
		}

		String fetch;
		String partId = parts;
		if ("TEXT".equalsIgnoreCase(partId)) {
			fetch = String
					.format(Locale.US, "BODY.PEEK[TEXT]<0.%d>",
							mStore.getPreferences().getMaximumAutoDownloadMessageSize());
		} else {
			fetch = String.format("BODY.PEEK[%s]", partId);
		}

		try {
			mConnection.sendCommand(String.format("UID FETCH %s (UID %s)",
					message.getUid(), fetch), false);

			ImapResponse response;
			int messageNumber = 0;

			IImapResponseCallback callback = new FetchPartCallback(part);

			do {
				response = mConnection.readResponse(callback);

				if ((response.mTag == null)
						&& (ImapResponseParser.equalsIgnoreCase(
								response.get(1), "FETCH"))) {
					ImapList fetchList = (ImapList) response
							.getKeyedValue("FETCH");
					Long uid = fetchList.getKeyedNumber("UID");

					if (message.getUid() != uid) {
						if (ImapStore.DEBUG)
							Log.d(ImapStore.LOG_TAG, "Did not ask for UID "
									+ uid + " for " + getLogId());

						mState.handleUntaggedResponse(response);
						continue;
					}
					if (listener != null) {
						listener.messageStarted(uid, messageNumber++, 1);
					}

					ImapMessage imapMessage = (ImapMessage) message;

					Object literal = handleFetchResponse(imapMessage, fetchList);

					if (literal != null) {
						if (literal instanceof Body) {
							// Most of the work was done in
							// FetchAttchmentCallback.foundLiteral()

							if (part.getBody() != null) {
								part.removeBody().dispose();
							}

							part.setBody((Body) literal);

						} else if (literal instanceof String) {

							String bodyString = (String) literal;

							InputStream bodyStream = new ByteArrayInputStream(
									bodyString.getBytes());

							StorageBodyFactory bodyFactory = new StorageBodyFactory();

							String transferEncoding = part
									.getContentTransferEncoding();

							InputStream stream = bodyStream;

							if (MimeUtil.isBase64Encoding(transferEncoding)) {
								stream = new Base64InputStream(bodyStream,
										DecodeMonitor.SILENT);
							} else if (MimeUtil
									.isQuotedPrintableEncoded(transferEncoding)) {
								stream = new QuotedPrintableInputStream(
										bodyStream, DecodeMonitor.SILENT);
							}

							TextBody body = bodyFactory.textBody(stream);

							if (part.getBody() != null) {
								part.removeBody().dispose();
							}

							part.setBody(body);

						} else {
							// This shouldn't happen
							throw new MessagingException(
									"Got FETCH response with bogus parameters");
						}
					}

					if (listener != null) {
						listener.messageFinished(message, messageNumber, 1);
					}
				} else {
					mState.handleUntaggedResponse(response);
				}

			} while (response.mTag == null);
		} catch (IOException ioe) {
			throw ioExceptionHandler(mConnection, ioe);
		}
	}

	// Returns value of body field
	private Object handleFetchResponse(ImapMessage message, ImapList fetchList)
			throws MessagingException {
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
						/*
						 * a message contains FORWARDED FLAG -> so we can also
						 * create them
						 */
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
			long size = fetchList.getKeyedNumber("RFC822.SIZE");
			message.setSize(size);
		}

		if (fetchList.containsKey("BODYSTRUCTURE")) {
			ImapList bs = fetchList.getKeyedList("BODYSTRUCTURE");
			if (bs != null) {
				try {
					parseBodyStructure(bs, message, "TEXT");
				} catch (MessagingException e) {
					if (ImapStore.DEBUG)
						Log.d(ImapStore.LOG_TAG, "Error handling message for "
								+ getLogId(), e);
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
					 * For each part in the message we're going to add a new
					 * BodyPart and parse into it.
					 */

					BodyPart bp = new BodyPart();
					if (id.equalsIgnoreCase("TEXT")) {
						parseBodyStructure(bs.getList(i), bp,
								Integer.toString(i + 1));
					} else {
						parseBodyStructure(bs.getList(i), bp, id + "."
								+ (i + 1));
					}
					parts.add(bp);
				} else {
					/*
					 * We've got to the end of the children of the part, so now
					 * we can find out what type it is and bail out.
					 */
					subType = bs.getString(i).toLowerCase(Locale.US);
					break;
				}
			}

			if (subType == null)
				return;

			Multipart mp = new MultipartImpl(subType);

			for (BodyPart bp : parts) {
				mp.addBodyPart(bp);
			}

			if (part.getBody() != null) {
				part.removeBody().dispose();
			}

			part.setBody(mp);

		} else {
			/*
			 * This is a body. We need to add as much information as we can find
			 * out about it to the Part.
			 */

			/*
			 * 0| 0 body type 1| 1 body subtype 2| 2 body parameter
			 * parenthesized list 3| 3 body id (unused) 4| 4 body description
			 * (unused) 5| 5 body encoding 6| 6 body size -| 7 text lines (only
			 * for type TEXT, unused) Extensions (optional): 7| 8 body MD5
			 * (unused) 8| 9 body disposition 9|10 body language (unused) 10|11
			 * body location (unused)
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
				// A body type of type MESSAGE and subtype RFC822
				// contains, immediately after the basic fields, the
				// envelope structure, body structure, and size in
				// text lines of the encapsulated message.
				// [MESSAGE, RFC822, [NAME, Fwd: [#HTR-517941]: update plans at
				// 1am Friday - Memory allocation - displayware.eml], NIL, NIL,
				// 7BIT, 5974, NIL, [INLINE, [FILENAME*0, Fwd: [#HTR-517941]:
				// update plans at 1am Friday - Memory all, FILENAME*1, ocation
				// - displayware.eml]], NIL]
				/*
				 * This will be caught by fetch and handled appropriately.
				 */
				throw new MessagingException(
						"BODYSTRUCTURE message/rfc822 not yet supported.");
			}

			/*
			 * Set the content type with as much information as we know right
			 * now.
			 */
			StringBuilder contentType = new StringBuilder();
			contentType.append(mimeType);

			if (bodyParams != null) {
				/*
				 * If there are body params we might be able to get some more
				 * information out of them.
				 */
				for (int i = 0, count = bodyParams.size(); i < count; i += 2) {
					contentType.append(String.format(";\r\n %s=\"%s\"",
							bodyParams.getString(i),
							bodyParams.getString(i + 1)));
				}
			}

			if (part.getHeader() == null)
				part.setHeader(new HeaderImpl());

			part.getHeader().addField(
					Fields.contentType(contentType.toString()));

			// Extension items
			ImapList bodyDisposition = null;
			if (("text".equalsIgnoreCase(type)) && (bs.size() > 9)
					&& (bs.get(9) instanceof ImapList)) {
				bodyDisposition = bs.getList(9);
			} else if (!("text".equalsIgnoreCase(type)) && (bs.size() > 8)
					&& (bs.get(8) instanceof ImapList)) {
				bodyDisposition = bs.getList(8);
			}

			StringBuilder contentDisposition = new StringBuilder();

			if (bodyDisposition != null && !bodyDisposition.isEmpty()) {
				if (!"NIL".equalsIgnoreCase(bodyDisposition.getString(0))) {
					contentDisposition.append(bodyDisposition.getString(0)
							.toLowerCase(Locale.US));
				}

				if ((bodyDisposition.size() > 1)
						&& (bodyDisposition.get(1) instanceof ImapList)) {
					ImapList bodyDispositionParams = bodyDisposition.getList(1);
					/*
					 * If there is body disposition information we can pull some
					 * more information about the attachment out.
					 */
					for (int i = 0, count = bodyDispositionParams.size(); i < count; i += 2) {
						contentDisposition.append(String.format(
								";\r\n %s=\"%s\"", bodyDispositionParams
										.getString(i).toLowerCase(Locale.US),
								bodyDispositionParams.getString(i + 1)));
					}
				}
			}

			ContentDispositionField contentDispositionField = Fields
					.contentDisposition(contentDisposition.toString());

			if (contentDispositionField
					.getParameter(ContentDispositionField.PARAM_SIZE) == null) {
				contentDisposition.append(String.format(Locale.US,
						";\r\n size=%d", size));
				contentDispositionField = Fields
						.contentDisposition(contentDisposition.toString());
			}

			/*
			 * Set the content disposition containing at least the size.
			 * Attachment handling code will use this down the road.
			 */
			part.getHeader().addField(contentDispositionField);

			/*
			 * Set the Content-Transfer-Encoding header. Attachment code will
			 * use this to parse the body.
			 */
			part.getHeader().addField(Fields.contentTransferEncoding(encoding));

			part.getHeader()
					.addField(
							new RawField(
									ImapStore.HEADER_ANDROID_ATTACHMENT_STORE_DATA,
									id));

		}

	}

	/**
	 * Appends the given messages to the selected folder.
	 * 
	 * <p>
	 * This implementation also determines the new UIDs of the given messages on
	 * the IMAP server and changes the messages' UIDs to the new server UIDs.
	 * </p>
	 * 
	 * @param messages
	 *            The messages to append to the folder.
	 * 
	 * @return The mapping of original message UIDs to the new server UIDs.
	 */
	public Map<Long, Long> appendMessages(ImapMessage[] messages)
			throws MessagingException {
		open(OPEN_MODE_RW);
		checkOpen();
		try {
			Map<Long, Long> uidMap = new HashMap<Long, Long>();
			for (ImapMessage message : messages) {
				mConnection.sendCommand(String.format(Locale.US,
						"APPEND %s (%s) {%d}", ImapUtility
								.encodeString(ImapUtility
										.encodeFolderName(mName.getPrefixedName())),
						combineFlags(message.getFlags()), message
								.calculateSize()), false);

				ImapResponse response;
				do {
					response = mConnection.readResponse();
					mState.handleUntaggedResponse(response);
					if (response.mCommandContinuationRequested) {
						mConnection.writeMessage(message);
					}
				} while (response.mTag == null);

				if (response.size() > 1) {
					/*
					 * If the server supports UIDPLUS, then along with the
					 * APPEND response it will return an APPENDUID response
					 * code, e.g.
					 * 
					 * 11 OK [APPENDUID 2 238268] APPEND completed
					 * 
					 * We can use the UID included in this response to update
					 * our records.
					 */
					Object responseList = response.get(1);

					if (responseList instanceof ImapList) {
						ImapList appendList = (ImapList) responseList;
						if (appendList.size() >= 3
								&& appendList.getString(0).equals("APPENDUID")) {
								
							try {
								
								long newUid = appendList.getKeyedNumber(2);
								
								message.setUid(newUid);
								uidMap.put(message.getUid(), newUid);
								continue;
							} catch (Exception ex) {
								Log.e(ImapStore.LOG_TAG, "Error while appending message", ex);
							}
						}
					}
				}

				/*
				 * This part is executed in case the server does not support
				 * UIDPLUS or does not implement the APPENDUID response code.
				 */

				try {
					long newUid = getUidFromMessageId(message);
					if (ImapStore.DEBUG) {
						Log.d(ImapStore.LOG_TAG, "Got UID " + newUid
								+ " for message for " + getLogId());
					}
	
					uidMap.put(message.getUid(), newUid);
					message.setUid(newUid);
				} catch (Exception ex) {
					Log.e(ImapStore.LOG_TAG, "Error while appending message", ex);
				}
			}

			/*
			 * We need uidMap to be null if new UIDs are not available to
			 * maintain consistency with the behavior of other similar methods
			 * (copyMessages, moveMessages) which return null.
			 */
			return (uidMap.size() == 0) ? null : uidMap;
		} catch (IOException ioe) {
			throw ioExceptionHandler(mConnection, ioe);
		}
	}

	public long getUidFromMessageId(ImapMessage message)
			throws MessagingException {
		try {
			/*
			 * Try to find the UID of the message we just appended using the
			 * Message-ID header.
			 */
			String messageId = message.getMessageId();

			if (messageId == null) {
				if (ImapStore.DEBUG)
					Log.d(ImapStore.LOG_TAG,
							"Did not get a message-id in order to search for UID  for "
									+ getLogId());
				throw new RuntimeException("Uid not found");
			}

			if (ImapStore.DEBUG)
				Log.d(ImapStore.LOG_TAG,
						"Looking for UID for message with message-id "
								+ messageId + " for " + getLogId());

			List<ImapResponse> responses = executeSimpleCommand(String.format(
					"UID SEARCH HEADER MESSAGE-ID %s",
					ImapUtility.encodeString(messageId)));
			for (ImapResponse response1 : responses) {
				if (response1.mTag == null
						&& ImapResponseParser.equalsIgnoreCase(
								response1.get(0), "SEARCH")
						&& response1.size() > 1) {
					return response1.getLong(1);
				}
			}
			
			throw new RuntimeException("Uid not found");
		} catch (IOException ioe) {
			throw new MessagingException(
					"Could not find UID for message based on Message-ID", ioe);
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
					&& (mCanCreateKeywords || mPermanentFlagsIndex
							.contains(Flag.FORWARDED))) {
				flagNames.add("$Forwarded");
			}

		}
		return ImapUtility.combine(
				flagNames.toArray(new String[flagNames.size()]), ' ');
	}

	public void setFlags(Flag[] flags, boolean value) throws MessagingException {
		open(OPEN_MODE_RW);
		checkOpen();

		try {
			executeSimpleCommand(String.format(
					"UID STORE 1:* %sFLAGS.SILENT (%s)", value ? "+" : "-",
					combineFlags(flags)));
		} catch (IOException ioe) {
			throw ioExceptionHandler(mConnection, ioe);
		}
	}

	public void setFlags(ImapMessage[] messages, Flag[] flags, boolean value)
			throws MessagingException {
		open(OPEN_MODE_RW);
		checkOpen();
		Long[] uids = new Long[messages.length];
		for (int i = 0, count = messages.length; i < count; i++) {
			uids[i] = messages[i].getUid();
		}
		try {
			executeSimpleCommand(String.format(
					"UID STORE %s %sFLAGS.SILENT (%s)",
					ImapUtility.combine(uids, ','), value ? "+" : "-",
					combineFlags(flags)));
		} catch (IOException ioe) {
			throw ioExceptionHandler(mConnection, ioe);
		}
	}

	private void checkOpen() throws MessagingException {
		if (!isOpen()) {
			throw new MessagingException("Folder " + mName.getPrefixedName()
					+ " is not open.");
		}
	}

	private MessagingException ioExceptionHandler(ImapConnection connection,
			IOException ioe) {
		Log.e(ImapStore.LOG_TAG, "IOException for " + getLogId(), ioe);
		if (connection != null) {
			connection.close();
		}
		close();
		return new MessagingException("IO Error", ioe);
	}

	public ImapStore getStore() {
		return mStore;
	}

	protected String getLogId() {
		String id = mName.getName() + "/" + Thread.currentThread().getName();
		if (mConnection != null) {
			id += "/" + mConnection.getLogId();
		}
		return id;
	}

	/**
	 * Search the remote ImapFolder.
	 * 
	 * @param queryString
	 *            String to query for.
	 * @param requiredFlags
	 *            Mandatory flags
	 * @param forbiddenFlags
	 *            Flags to exclude
	 * @return List of messages found
	 * @throws MessagingException
	 *             On any error.
	 */
	public List<ImapMessage> search(final String queryString,
			final Flag[] requiredFlags, final Flag[] forbiddenFlags)
			throws MessagingException {

		if (!mStore.getPreferences().allowRemoteSearch()) {
			throw new MessagingException(
					"Your settings do not allow remote searching of this account");
		}

		// Setup the searcher
		final ImapSearcher searcher = new ImapSearcher() {
			public List<ImapResponse> search() throws IOException,
					MessagingException {
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
				final String encodedQry = ImapUtility.encodeString(queryString);
				if (mStore.getPreferences().isRemoteSearchFullText()) {
					imapQuery += "TEXT " + encodedQry;
				} else {
					imapQuery += "OR SUBJECT " + encodedQry + " FROM "
							+ encodedQry;
				}
				return executeSimpleCommand(imapQuery);
			}
		};

		// Execute the search
		try {
			open(OPEN_MODE_RO);
			checkOpen();

			mInSearch = true;
			// don't pass listener--we don't want to add messages until we've
			// downloaded them
			return search(searcher, null);
		} finally {
			mInSearch = false;
		}

	}

	public boolean isIdleCapable() throws MessagingException {
		
		checkOpen();
        
		return mConnection.isIdleCapable();
	}
	
	
	/**
	 * 
	 * This function blocks until a change in the folder happens or an communication
	 * error occurs. The caller can pass a currently held wakelock that will be released
	 * while we wait for updates and will be re-acquired when we return from the function.
	 * 
	 * @param wakeLock a currently held wakelock or null if no wakelock has to be released while waiting
	 * @throws MessagingException the session doesn't support waiting for changes or an communication error occured
	 */
    public void waitForChanges(final WakeLock wakeLock) throws MessagingException {

    	final AtomicBoolean lockReleased = new AtomicBoolean(false);
    	
    	try {
	    	
	    	checkOpen();
	        
	        if (!mConnection.isIdleCapable()) {
	            throw new MessagingException("IMAP server is not IDLE capable:" + mConnection.toString());
	        }
	
	        try {
	        
	        	
		        mConnection.setReadTimeout((mStore.getPreferences().getIdleRefreshMinutes() * 60 * 1000) + ImapStore.IDLE_READ_TIMEOUT_INCREMENT);
		        executeSimpleCommand(ImapStore.COMMAND_IDLE, false, new UntaggedHandler() {
					
		        	boolean doneSent;
		        	boolean continuationReceived;
		        	
					@SuppressLint("Wakelock")
					@Override
					public void handleAsyncUntaggedResponse(ImapResponse respose) {
						
						if (respose.mCommandContinuationRequested) {
							
							continuationReceived = true;
			
							// release the wake lock while we wait for updates from the server
							if (wakeLock != null) {
								lockReleased.set(true);								
								wakeLock.release();
							}
							
						} else if (!doneSent && continuationReceived){
					
							// re-acquire the log since we are going to stop to wait for updates
							if (wakeLock != null) {
								lockReleased.set(false);
								wakeLock.acquire();
							}
							
							try {
								mConnection.setReadTimeout(ImapStore.SOCKET_READ_TIMEOUT);
					            mConnection.sendContinuation("DONE");
							} catch (IOException ex) {
								Log.e(getLogId(), "Error while shutting down IDLE", ex);
							}
							
				            doneSent = true;
							
						}
						
						mState.handleUntaggedResponse(respose);
						
					}
				});
		        
	        } catch (IOException ex) {
	        	
	        	mConnection.close();
	        	
	        	throw new MessagingException("Error while IDLEing", ex);
	        }
        
    	} finally {
    		
    		// make sure re-acquire the lock even if there was an error and 
    		// we could not send a DONE message
    		if (wakeLock != null && lockReleased.get()) wakeLock.acquire();
    	}
        

    }
	
	interface ImapSearcher {
	    List<ImapResponse> search() throws IOException, MessagingException;
	}
}
