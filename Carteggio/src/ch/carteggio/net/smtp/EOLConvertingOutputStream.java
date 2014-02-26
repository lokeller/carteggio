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

public class EOLConvertingOutputStream extends FilterOutputStream {
    private int lastChar;
    private boolean ignoreNextIfLF = false;

    public EOLConvertingOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int oneByte) throws IOException {
        if (!ignoreNextIfLF) {
            if ((oneByte == '\n') && (lastChar != '\r')) {
                super.write('\r');
            }
            super.write(oneByte);
            lastChar = oneByte;
        }
        ignoreNextIfLF = false;
    }

    @Override
    public void flush() throws IOException {
        if (lastChar == '\r') {
            super.write('\n');
            lastChar = '\n';

            // We have to ignore the next character if it is <LF>. Otherwise it
            // will be expanded to an additional <CR><LF> sequence although it
            // belongs to the one just completed.
            ignoreNextIfLF = true;
        }
        super.flush();
    }
}
