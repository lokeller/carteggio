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


package ch.carteggio.net.imap.parsing;

import java.io.IOException;

import ch.carteggio.net.FixedLengthInputStream;

public interface IImapResponseCallback {
    /**
     * Callback method that is called by the parser when a literal string
     * is found in an IMAP response.
     *
     * @param response ImapResponse object with the fields that have been
     *                 parsed up until now (excluding the literal string).
     * @param literal  FixedLengthInputStream that can be used to access
     *                 the literal string.
     *
     * @return an Object that will be put in the ImapResponse object at the
     *         place of the literal string.
     *
     * @throws IOException passed-through if thrown by FixedLengthInputStream
     * @throws Exception if something goes wrong. Parsing will be resumed
     *                   and the exception will be thrown after the
     *                   complete IMAP response has been parsed.
     */
    public Object foundLiteral(ImapResponse response, FixedLengthInputStream literal)
    throws IOException, Exception;
}