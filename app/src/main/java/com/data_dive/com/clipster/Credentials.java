package com.data_dive.com.clipster;

import android.util.Base64;
import android.util.Log;

import com.amdelamar.jhash.Hash;
import com.amdelamar.jhash.algorithms.Type;
import com.macasaet.fernet.Key;
import java.nio.charset.StandardCharsets;

/**
 *  Define credentials object to pass around functions and activities
 *  Create PB2KDF hash Key for symmetric encryption
 */

public final class Credentials {
    private static String logtag = "Credentials";

    private static final Integer CRYPT_ITERATIONS = 100000;
    private static final Integer CRYPT_HASH_LENGTH = 32;
    public Key encryption_key;

    public final String user;
    public final String pw;
    public final String token_b64;
    public final String server;
    public final boolean ignore_cert;


    public Credentials(String user, String pw, String server, boolean ignore_cert) {
        this.user = user;
        this.pw = pw;
        this.server = server;
        this.ignore_cert = ignore_cert;

        String token = user + ":" + pw;
        try {
            byte[] token_byte = token.getBytes("UTF-8");
            token = Base64.encodeToString(token_byte, Base64.NO_WRAP);
        } catch (Exception UnsupportedEncodingException) {
            Log.e(logtag, "UnsupportedEncodingException");
            token = null;
        }
        this.token_b64 = token;
        Log.d(logtag, "called constructor, param: " + user + " " + pw + " " + " " + server
                + " " + token + " " + ignore_cert);

        createKey();
        // testKey();
    }

    public void testKey() {
        String clear = "androidtest";
        String secret = "gAAAAABf0qoqSW18fUXv86ZoDjaR6N3JNAtI7z12144BQ2-lSQRGnzAjcl0DQgd_hvBalATpfCxYLhizFPptVa4-W31aIbsrPQ==";
        String hash = "K_OQsD5WSw2iHTWy8M1pwXy5UvKKOgFsiKVPY37Dzr8=";
        this.encryption_key = new Key(hash);
        // String answer = decryptText(secret);
        // Log.d(logtag, "ANSWER: " + answer);
        // String encrypted = encryptText(clear);
        // Log.d(logtag, "ENCRYPTED: " + encrypted);
    }

    public void createKey() {
        /** Create a PB2KDF hash key derived from the password and a salt
         *  Use in Fernet for encrypting and decrypting
         */
        String hash = "";
        String salt_string = "clipster_"+user+"_"+pw;
        Log.d(logtag, "salt_string: " + salt_string);

        try {
            byte[] salt = salt_string.getBytes(StandardCharsets.UTF_8);
            String h = Hash.password(pw.toCharArray())
                    .algorithm(Type.PBKDF2_SHA512)
                    .salt(salt)
                    .hashLength(CRYPT_HASH_LENGTH)
                    .factor(CRYPT_ITERATIONS)
                    .create();
            hash = h.split(":")[6]; // jhash returns 7 concatenated strings, last one is hash
            Log.d(logtag, "JHash output: " + h);
        } catch (Exception e) {
            Log.e(logtag, "Error creating Hash for Key: " + e);
        }

        Log.d(logtag, "Hash : " + hash);
        // TODO: So WTF is going on here? Is that a bug in Jhash?
        // TODO: WE GET "/" which we should never get for b64 encoded
        hash = hash.replace("/","_");
        Log.d(logtag, "Hash : " + hash);
        this.encryption_key = new Key(hash);
        Log.d(logtag, "Created Key: " + this.encryption_key);
    }
}
