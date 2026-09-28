package com.data_dive.com.clipster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import okhttp3.tls.HeldCertificate;
import org.junit.Test;

public class PinningTrustManagerTest {

    private static X509Certificate selfSigned() {
        return new HeldCertificate.Builder()
                .commonName("my-own-server")
                .rsa2048()
                .build()
                .certificate();
    }

    @Test
    public void unpinnedSelfSignedCertificate_isRejectedAndRemembered() throws Exception {
        X509Certificate cert = selfSigned();
        PinningTrustManager tm = new PinningTrustManager(null);

        assertThrows(CertificateException.class, () -> tm.checkServerTrusted(new X509Certificate[] {cert}, "RSA"));
        assertEquals(cert, tm.rejectedCertificate());
    }

    @Test
    public void pinnedCertificate_isTrusted() throws Exception {
        X509Certificate cert = selfSigned();
        PinningTrustManager tm = new PinningTrustManager(cert);

        tm.checkServerTrusted(new X509Certificate[] {cert}, "RSA");
        assertNull(tm.rejectedCertificate());
    }

    @Test
    public void otherCertificate_isRejectedEvenWithPin() throws Exception {
        PinningTrustManager tm = new PinningTrustManager(selfSigned());
        X509Certificate other = selfSigned();

        assertThrows(CertificateException.class, () -> tm.checkServerTrusted(new X509Certificate[] {other}, "RSA"));
        assertEquals(other, tm.rejectedCertificate());
    }

    @Test
    public void fingerprint_isColonSeparatedSha256() {
        String fingerprint = PinningTrustManager.fingerprint(selfSigned());
        assertTrue(fingerprint, fingerprint.matches("([0-9A-F]{2}:){31}[0-9A-F]{2}"));
    }

    @Test
    public void pinnedCertificate_survivesSavingInCredentials() {
        X509Certificate cert = selfSigned();
        Credentials pinned = Credentials.fromSaved(
                        "alice",
                        "bBthXg9OHtoBhzVQaCvJ-tT6SBNm3k_8d84OEDVjl8E=",
                        "BYVy_Lfm1pO5uSEQRq3UmHDegjhE2RpFt60PVZJjfp4=",
                        "https://example.org",
                        true,
                        "")
                .withPinnedCertificate(cert);

        Credentials restored = Credentials.fromSaved(
                pinned.user,
                pinned.loginHash,
                pinned.msgHash,
                pinned.server,
                pinned.allowSelfSigned,
                pinned.encodedPinnedCertificate());

        assertEquals(cert, restored.pinnedCertificate);
    }
}
