package com.data_dive.com.clipster;

import com.amdelamar.jhash.Hash;
import com.amdelamar.jhash.algorithms.Type;
import com.macasaet.fernet.Key;
import com.macasaet.fernet.StringValidator;
import com.macasaet.fernet.Token;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Base64;

/**
 * Key derivation and Fernet encryption. Free of Android dependencies so it can be unit tested.
 * All parameters must stay in sync with Clipster-Desktop (clipster/crypt.go), otherwise clients
 * can no longer authenticate or decrypt each other's clips.
 */
public final class Crypto {

    public static final int ITERS_LOGIN_HASH = 20000;
    public static final int ITERS_MSG_HASH = 10000;
    private static final int HASH_LENGTH = 32;
    private static final Duration TOKEN_TTL = Duration.ofDays(3650);

    private Crypto() {}

    /**
     * PBKDF2-SHA256 hash of the password, returned as url-safe base64
     */
    public static String deriveKey(String user, String password, int iterations) {
        byte[] salt = ("clipster_" + user + "_" + password).getBytes(StandardCharsets.UTF_8);
        String h = Hash.password(password.toCharArray())
                .algorithm(Type.PBKDF2_SHA256)
                .salt(salt)
                .hashLength(HASH_LENGTH)
                .factor(iterations)
                .create();
        // jhash returns 7 colon separated fields, the last one is the standard base64 hash
        String hash = h.split(":")[6];
        return hash.replace('/', '_').replace('+', '-');
    }

    /**
     * Base64 encoded "user:login_hash" for HTTP Basic Auth
     */
    public static String authToken(String user, String loginHash) {
        String token = user + ":" + loginHash;
        return Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    public static String encrypt(Key key, String text) {
        return Token.generate(key, text).serialise();
    }

    /**
     * @throws RuntimeException if the token is malformed, expired or was encrypted with another key
     */
    public static String decrypt(Key key, String token) {
        return decrypt(key, token, Clock.systemUTC());
    }

    static String decrypt(Key key, String token, Clock clock) {
        StringValidator validator = new StringValidator() {
            @Override
            public TemporalAmount getTimeToLive() {
                return TOKEN_TTL;
            }

            @Override
            public Clock getClock() {
                return clock;
            }
        };
        return Token.fromString(token).validateAndDecrypt(key, validator);
    }
}
