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

package ch.carteggio.net.security;

import java.security.MessageDigest;

import ch.carteggio.net.MessagingException;

import android.util.Base64;

public class Authentication {
    private static final String US_ASCII = "US-ASCII";

    /**
     * Computes the response for CRAM-MD5 authentication mechanism given the user credentials and
     * the server-provided nonce.
     *
     * @param username The username.
     * @param password The password.
     * @param b64Nonce The nonce as base64-encoded string.
     * @return The CRAM-MD5 response.
     *
     * @throws MessagingException If something went wrong.
     *
     * @see Authentication#computeCramMd5Bytes(String, String, byte[])
     */
    public static String computeCramMd5(String username, String password, String b64Nonce)
    throws MessagingException {

        try {
            byte[] b64NonceBytes = b64Nonce.getBytes(US_ASCII);
            byte[] b64CRAM = computeCramMd5Bytes(username, password, b64NonceBytes);
            return new String(b64CRAM, US_ASCII);
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagingException("This shouldn't happen", e);
        }
    }

    /**
     * Computes the response for CRAM-MD5 authentication mechanism given the user credentials and
     * the server-provided nonce.
     *
     * @param username The username.
     * @param password The password.
     * @param b64Nonce The nonce as base64-encoded byte array.
     * @return The CRAM-MD5 response as byte array.
     *
     * @throws MessagingException If something went wrong.
     *
     * @see <a href="https://tools.ietf.org/html/rfc2195">RFC 2195</a>
     */
    public static byte[] computeCramMd5Bytes(String username, String password, byte[] b64Nonce)
    throws MessagingException {

        try {
            byte[] nonce = Base64.decode(b64Nonce, Base64.DEFAULT);

            byte[] secretBytes = password.getBytes();
            MessageDigest md = MessageDigest.getInstance("MD5");
            if (secretBytes.length > 64) {
                secretBytes = md.digest(secretBytes);
            }

            byte[] ipad = new byte[64];
            byte[] opad = new byte[64];
            System.arraycopy(secretBytes, 0, ipad, 0, secretBytes.length);
            System.arraycopy(secretBytes, 0, opad, 0, secretBytes.length);
            for (int i = 0; i < ipad.length; i++) ipad[i] ^= 0x36;
            for (int i = 0; i < opad.length; i++) opad[i] ^= 0x5c;

            md.update(ipad);
            byte[] firstPass = md.digest(nonce);

            md.update(opad);
            byte[] result = md.digest(firstPass);

            String plainCRAM = username + " " + new String(Hex.encodeHex(result));
            byte[] b64CRAM = Base64.encode(plainCRAM.getBytes(), Base64.DEFAULT);

            return b64CRAM;

        } catch (Exception e) {
            throw new MessagingException("Something went wrong during CRAM-MD5 computation", e);
        }
    }
}
