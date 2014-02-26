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

import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import ch.carteggio.net.MessagingException;

public class CertificateValidationException extends MessagingException {
    public static final long serialVersionUID = -1;
    private X509Certificate[] mCertChain;
    private boolean mNeedsUserAttention = false;

    public CertificateValidationException(String message) {
        super(message);
        scanForCause();
    }

    public CertificateValidationException(final String message, Throwable throwable) {
        super(message, throwable);
        scanForCause();
    }

    private void scanForCause() {
        Throwable throwable = getCause();

        /* user attention is required if the certificate was deemed invalid */
        while (throwable != null
                && !(throwable instanceof CertPathValidatorException)
                && !(throwable instanceof CertificateException)) {
            throwable = throwable.getCause();
        }

        if (throwable != null) {
            mNeedsUserAttention = true;
            if (throwable instanceof CertificateChainException) {
                mCertChain = ((CertificateChainException) throwable).getCertChain();
            }
        }
    }

    public boolean needsUserAttention() {
        return mNeedsUserAttention;
    }

    /**
     * If the cause of this {@link CertificateValidationException} was a
     * {@link CertificateChainException}, then the offending chain is available
     * for return.
     * 
     * @return An {@link X509Certificate X509Certificate[]} containing the Cert.
     *         chain, or else null.
     */
    public X509Certificate[] getCertChain() {
        return mCertChain;
    }
}