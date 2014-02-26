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

package ch.carteggio.net.smtp;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SmtpDataStuffing extends FilterOutputStream {
    private static final int STATE_NORMAL = 0;
    private static final int STATE_CR = 1;
    private static final int STATE_CRLF = 2;

    private int state = STATE_NORMAL;

    public SmtpDataStuffing(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int oneByte) throws IOException {
        if (oneByte == '\r') {
            state = STATE_CR;
        } else if ((state == STATE_CR) && (oneByte == '\n')) {
            state = STATE_CRLF;
        } else if ((state == STATE_CRLF) && (oneByte == '.')) {
            // Read <CR><LF><DOT> so this line needs an additional period.
            super.write('.');
            state = STATE_NORMAL;
        } else {
            state = STATE_NORMAL;
        }
        super.write(oneByte);
    }
}
