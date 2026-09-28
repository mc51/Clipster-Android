package com.data_dive.com.clipster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.macasaet.fernet.Key;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Expected values were generated independently of this code base with Python's hashlib and
 * cryptography.fernet, following deriveKey() in Clipster-Desktop (clipster/crypt.go):
 *   urlsafe_b64encode(pbkdf2_hmac("sha256", pw, "clipster_" + user + "_" + pw, iters, 32))
 * If these tests fail, Android can no longer talk to the other Clipster clients.
 */
public class CryptoTest {

    private static final String USER = "alice";
    private static final String PASSWORD = "correct horse battery";
    private static final String LOGIN_HASH = "bBthXg9OHtoBhzVQaCvJ-tT6SBNm3k_8d84OEDVjl8E=";
    private static final String MSG_HASH = "BYVy_Lfm1pO5uSEQRq3UmHDegjhE2RpFt60PVZJjfp4=";

    // Fernet token of "Hello from Clipster-Desktop ✂" with MSG_HASH, created at 2026-09-01T00:00:00Z
    private static final String TOKEN = "gAAAAABqlhWAtRsghjyq9rsI8g-UaikhzJTWCAwmUsd7AKLqbKwOMiXkmJMqdQ_9E0y"
            + "MrasS8a9ZJrocwqrpA_CO4cog538pynJdM8PcZ6BnfJ4PYgL0wAQ=";
    private static final Clock AFTER_TOKEN_CREATION =
            Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    public void deriveKey_loginHash_matchesOtherClients() {
        assertEquals(LOGIN_HASH, Crypto.deriveKey(USER, PASSWORD, Crypto.ITERS_LOGIN_HASH));
    }

    @Test
    public void deriveKey_msgHash_matchesOtherClients() {
        assertEquals(MSG_HASH, Crypto.deriveKey(USER, PASSWORD, Crypto.ITERS_MSG_HASH));
    }

    @Test
    public void deriveKey_nonAsciiPassword_isUtf8Encoded() {
        assertEquals("da807iYOiUZngQv_La7PBS0OBX4t-4mXVIKqlPWkVMg=",
                Crypto.deriveKey("bob", "pässwörd-ü€", Crypto.ITERS_LOGIN_HASH));
        assertEquals("U5apiIs8RYDrzHrpqGNxtGf0w1MfIJaZYpx4RRTd0WE=",
                Crypto.deriveKey("bob", "pässwörd-ü€", Crypto.ITERS_MSG_HASH));
    }

    @Test
    public void authToken_isBase64OfUserAndLoginHash() {
        assertEquals("YWxpY2U6YkJ0aFhnOU9IdG9CaHpWUWFDdkotdFQ2U0JObTNrXzhkODRPRURWamw4RT0=",
                Crypto.authToken(USER, LOGIN_HASH));
    }

    @Test
    public void decrypt_tokenFromOtherClient() {
        assertEquals("Hello from Clipster-Desktop ✂",
                Crypto.decrypt(new Key(MSG_HASH), TOKEN, AFTER_TOKEN_CREATION));
    }

    @Test
    public void encrypt_thenDecrypt_roundTrips() {
        Key key = new Key(MSG_HASH);
        String text = "multi\nline ✂ clip";
        assertEquals(text, Crypto.decrypt(key, Crypto.encrypt(key, text)));
    }

    @Test
    public void decrypt_withWrongKey_throws() {
        Key wrongKey = new Key(LOGIN_HASH);
        assertThrows(RuntimeException.class, () -> Crypto.decrypt(wrongKey, TOKEN, AFTER_TOKEN_CREATION));
    }

    @Test
    public void decrypt_malformedToken_throws() {
        assertThrows(RuntimeException.class, () -> Crypto.decrypt(new Key(MSG_HASH), "not a token"));
    }

    @Test
    public void credentials_fromPassword_matchesSavedHashes() {
        Credentials fromPw = Credentials.fromPassword(USER, PASSWORD, "https://clipster.cc", false);
        assertEquals(LOGIN_HASH, fromPw.login_pw_hash);
        assertEquals(MSG_HASH, fromPw.msg_pw_hash);
        assertEquals(Crypto.authToken(USER, LOGIN_HASH), fromPw.token_b64);
    }
}
