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

public class LineWrapOutputStream extends FilterOutputStream {
    private static final byte[] CRLF = new byte[] {'\r', '\n'};

    private byte[] buffer;
    private int bufferStart = 0;
    private int lineLength = 0;
    private int endOfLastWord = 0;


    public LineWrapOutputStream(OutputStream out, int maxLineLength) {
        super(out);
        buffer = new byte[maxLineLength - 2];
    }

    @Override
    public void write(int oneByte) throws IOException {
        // Buffer full?
        if (lineLength == buffer.length) {
            // Usable word-boundary found earlier?
            if (endOfLastWord > 0) {
                // Yes, so output everything up to that word-boundary
                out.write(buffer, bufferStart, endOfLastWord - bufferStart);
                out.write(CRLF);

                bufferStart = 0;

                // Skip the <SPACE> in the buffer
                endOfLastWord++;
                lineLength = buffer.length - endOfLastWord;
                if (lineLength > 0) {
                    // Copy rest of the buffer to the front
                    System.arraycopy(buffer, endOfLastWord + 0, buffer, 0, lineLength);
                }
                endOfLastWord = 0;
            } else {
                // No word-boundary found, so output whole buffer
                out.write(buffer, bufferStart, buffer.length - bufferStart);
                out.write(CRLF);
                lineLength = 0;
                bufferStart = 0;
            }
        }

        if ((oneByte == '\n') || (oneByte == '\r')) {
            // <CR> or <LF> character found, so output buffer ...
            if (lineLength - bufferStart > 0) {
                out.write(buffer, bufferStart, lineLength - bufferStart);
            }
            // ... and that character
            out.write(oneByte);
            lineLength = 0;
            bufferStart = 0;
            endOfLastWord = 0;
        } else {
            // Remember this position as last word-boundary if <SPACE> found
            if (oneByte == ' ') {
                endOfLastWord = lineLength;
            }

            // Write character to the buffer
            buffer[lineLength] = (byte)oneByte;
            lineLength++;
        }
    }

    @Override
    public void flush() throws IOException {
        // Buffer empty?
        if (lineLength > bufferStart) {
            // Output everything we have up till now
            out.write(buffer, bufferStart, lineLength - bufferStart);

            // Mark current position as new start of the buffer
            bufferStart = (lineLength == buffer.length) ? 0 : lineLength;
            endOfLastWord = 0;
        }
        out.flush();
    }
}
