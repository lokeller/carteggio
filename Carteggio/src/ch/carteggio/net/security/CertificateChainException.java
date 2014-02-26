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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * A {@link CertificateException} extension that provides access to
 * the pertinent certificate chain.
 *
 */
public class CertificateChainException extends CertificateException {

    private static final long serialVersionUID = 1103894512106650107L;
    private X509Certificate[] mCertChain;

    public CertificateChainException(String msg, X509Certificate[] chain) {
        super(msg);
        setCertChain(chain);
    }

    public CertificateChainException(CertificateException ce,
            X509Certificate[] chain) {
        super.initCause(ce);
        setCertChain(chain);
    }

    public void setCertChain(X509Certificate[] chain) {
        mCertChain = chain;
    }
    public X509Certificate[] getCertChain() {
        return mCertChain;
    }

}
