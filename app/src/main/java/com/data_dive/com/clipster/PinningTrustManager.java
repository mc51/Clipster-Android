package com.data_dive.com.clipster;

import android.annotation.SuppressLint;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Trusts everything the platform trusts, plus one pinned certificate the user confirmed
 * (trust on first use, for own servers with a self-signed certificate).
 * Remembers the certificate of a rejected server, so the user can be asked whether to trust it.
 */
// Not a trust-all manager: every chain goes through the platform checks first
@SuppressLint("CustomX509TrustManager")
final class PinningTrustManager extends X509ExtendedTrustManager {

    private interface Check {
        void run() throws CertificateException;
    }

    private final X509ExtendedTrustManager platform;
    private final X509Certificate pinned;
    private volatile X509Certificate rejected;

    PinningTrustManager(X509Certificate pinned) throws GeneralSecurityException {
        this(platformTrustManager(), pinned);
    }

    PinningTrustManager(X509ExtendedTrustManager platform, X509Certificate pinned) {
        this.platform = platform;
        this.pinned = pinned;
    }

    static X509ExtendedTrustManager platformTrustManager() throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager tm : factory.getTrustManagers()) {
            if (tm instanceof X509ExtendedTrustManager) {
                return (X509ExtendedTrustManager) tm;
            }
        }
        throw new GeneralSecurityException("No X509ExtendedTrustManager available");
    }

    /** Certificate of the last server that failed the checks, null if none failed */
    X509Certificate rejectedCertificate() {
        return rejected;
    }

    /** Hostname checks are skipped only for the exact pinned certificate */
    HostnameVerifier hostnameVerifier(HostnameVerifier platformVerifier) {
        return (hostname, session) -> {
            if (pinned != null) {
                try {
                    if (pinned.equals(session.getPeerCertificates()[0])) {
                        return true;
                    }
                } catch (SSLPeerUnverifiedException e) {
                    return false;
                }
            }
            return platformVerifier.verify(hostname, session);
        };
    }

    private void check(X509Certificate[] chain, Check platformCheck) throws CertificateException {
        try {
            platformCheck.run();
        } catch (CertificateException e) {
            if (pinned != null && chain != null && chain.length > 0 && pinned.equals(chain[0])) {
                return;
            }
            rejected = chain != null && chain.length > 0 ? chain[0] : null;
            throw e;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        check(chain, () -> platform.checkServerTrusted(chain, authType, socket));
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        check(chain, () -> platform.checkServerTrusted(chain, authType, engine));
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        check(chain, () -> platform.checkServerTrusted(chain, authType));
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        platform.checkClientTrusted(chain, authType, socket);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        platform.checkClientTrusted(chain, authType, engine);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        platform.checkClientTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return platform.getAcceptedIssuers();
    }

    /** SHA-256 fingerprint as shown by: openssl x509 -noout -fingerprint -sha256 -in cert.pem */
    static String fingerprint(X509Certificate cert) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                if (sb.length() > 0) {
                    sb.append(':');
                }
                sb.append(String.format(Locale.ROOT, "%02X", b));
            }
            return sb.toString();
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
