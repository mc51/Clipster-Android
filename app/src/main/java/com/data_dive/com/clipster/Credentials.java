package com.data_dive.com.clipster;

import com.macasaet.fernet.Key;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Credentials and server settings. Holds the PBKDF2 derived hashes, the password itself is never stored.
 */
public final class Credentials {

    public final String user;
    public final String loginHash;
    public final String msgHash;
    public final String authToken;
    public final Key encryptionKey;
    public final String server;
    /** Whether the user may be asked to trust a certificate Android doesn't trust (own server) */
    public final boolean allowSelfSigned;
    /** Self-signed server certificate the user confirmed, null if none */
    public final X509Certificate pinnedCertificate;

    private Credentials(
            String user,
            String loginHash,
            String msgHash,
            String server,
            boolean allowSelfSigned,
            X509Certificate pinnedCertificate) {
        this.user = user;
        this.loginHash = loginHash;
        this.msgHash = msgHash;
        this.server = server;
        this.allowSelfSigned = allowSelfSigned;
        this.pinnedCertificate = pinnedCertificate;
        this.encryptionKey = new Key(msgHash);
        this.authToken = Crypto.authToken(user, loginHash);
    }

    /**
     * Derive hashes from a password. Slow by design, don't call on the main thread.
     */
    public static Credentials fromPassword(String user, String password, String server, boolean allowSelfSigned) {
        return new Credentials(
                user,
                Crypto.deriveKey(user, password, Crypto.ITERS_LOGIN_HASH),
                Crypto.deriveKey(user, password, Crypto.ITERS_MSG_HASH),
                server,
                allowSelfSigned,
                null);
    }

    /**
     * @param pinnedCertificate base64 DER encoded certificate, empty or null if none
     */
    public static Credentials fromSaved(
            String user,
            String loginHash,
            String msgHash,
            String server,
            boolean allowSelfSigned,
            String pinnedCertificate) {
        return new Credentials(user, loginHash, msgHash, server, allowSelfSigned, decodeCertificate(pinnedCertificate));
    }

    public Credentials withPinnedCertificate(X509Certificate certificate) {
        return new Credentials(user, loginHash, msgHash, server, allowSelfSigned, certificate);
    }

    /** Base64 DER encoding of the pinned certificate for storage, empty if none */
    public String encodedPinnedCertificate() {
        if (pinnedCertificate == null) {
            return "";
        }
        try {
            return Base64.getEncoder().encodeToString(pinnedCertificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static X509Certificate decodeCertificate(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] der = Base64.getDecoder().decode(encoded);
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        } catch (CertificateException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid pinned certificate", e);
        }
    }

    @Override
    public String toString() {
        return "Credentials{user=" + user + ", server=" + server + ", allowSelfSigned=" + allowSelfSigned + ", pinned="
                + (pinnedCertificate != null) + "}";
    }
}
