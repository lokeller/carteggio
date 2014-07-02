/*
 * Copyright (C) 2012 The K-9 Dog Walkers
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2014 Lorenzo Keller
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This file is a modified version of a file distributed with K-9 sources.
 */

package ch.carteggio.net.imap;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

import android.text.TextUtils;
import android.util.Log;

import com.beetstra.jutf7.CharsetProvider;

/**
 * Utility methods for use with IMAP.
 */
public class ImapUtility {
	
    private static final String LOG_TAG = "ImapUtility";

    
    /**
     * Charset used for converting folder names to and from UTF-7 as defined by RFC 3501.
     */
    private static final Charset mModifiedUtf7Charset = new CharsetProvider().charsetForName("X-RFC-3501");
    
    
	/**
     * Gets all of the values in a sequence set per RFC 3501.
     *
     * <p>
     * Any ranges are expanded into a list of individual numbers.
     * </p>
     *
     * <pre>
     * sequence-number = nz-number / "*"
     * sequence-range  = sequence-number ":" sequence-number
     * sequence-set    = (sequence-number / sequence-range) *("," sequence-set)
     * </pre>
     *
     * @param set
     *         The sequence set string as received by the server.
     *
     * @return The list of IDs as strings in this sequence set. If the set is invalid, an empty
     *         list is returned.
     */
    public static List<String> getImapSequenceValues(String set) {
        ArrayList<String> list = new ArrayList<String>();
        if (set != null) {
            String[] setItems = set.split(",");
            for (String item : setItems) {
                if (item.indexOf(':') == -1) {
                    // simple item
                    if (isNumberValid(item)) {
                        list.add(item);
                    }
                } else {
                    // range
                    list.addAll(getImapRangeValues(item));
                }
            }
        }

        return list;
    }

    /**
     * Expand the given number range into a list of individual numbers.
     *
     * <pre>
     * sequence-number = nz-number / "*"
     * sequence-range  = sequence-number ":" sequence-number
     * sequence-set    = (sequence-number / sequence-range) *("," sequence-set)
     * </pre>
     *
     * @param range
     *         The range string as received by the server.
     *
     * @return The list of IDs as strings in this range. If the range is not valid, an empty list
     *         is returned.
     */
    public static List<String> getImapRangeValues(String range) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            if (range != null) {
                int colonPos = range.indexOf(':');
                if (colonPos > 0) {
                    long first  = Long.parseLong(range.substring(0, colonPos));
                    long second = Long.parseLong(range.substring(colonPos + 1));
                    if (is32bitValue(first) && is32bitValue(second)) {
                        if (first < second) {
                            for (long i = first; i <= second; i++) {
                                list.add(Long.toString(i));
                            }
                        } else {
                            for (long i = first; i >= second; i--) {
                                list.add(Long.toString(i));
                            }
                        }
                    } else {
                        Log.d(LOG_TAG, "Invalid range: " + range);
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.d(LOG_TAG, "Invalid range value: " + range, e);
        }

        return list;
    }

    private static boolean isNumberValid(String number) {
        try {
            long value = Long.parseLong(number);
            if (is32bitValue(value)) {
                return true;
            }
        } catch (NumberFormatException e) {
            // do nothing
        }

        Log.d(LOG_TAG, "Invalid UID value: " + number);

        return false;
    }

    private static boolean is32bitValue(long value) {
        return ((value & ~0xFFFFFFFFL) == 0L);
    }
    
    public static String encodeFolderName(String name) {
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

    public static String decodeFolderName(String name) throws CharacterCodingException {
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
	public static String encodeString(String str) {
	    return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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

}
