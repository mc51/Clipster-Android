package com.data_dive.com.clipster;

import android.util.Base64;
import android.util.Log;
import com.macasaet.fernet.Key;

import java.nio.charset.StandardCharsets;

/**
 *  Define credentials object to pass around functions and activities
 *  Create PB2KDF hash Key for symmetric encryption
 */

public final class Credentials {
    private static String logtag = "Credentials";

    public Key encryption_key;

    public final String user;
    public final String pw;
    public final String login_pw_hash;
    public final String msg_pw_hash;
    public final String token_b64;
    public final String server;
    public final boolean ignore_cert;

    public Credentials(String username, String password, String login_hash, String msg_hash, String server, boolean ignore_cert) {
        /**
         * Constructor when credentials already saved
         */
        this.user = username;
        this.pw = password;

        if(!pw.isEmpty()) {
            this.login_pw_hash = Utils.stringToHash(user, pw, Utils.CRYPT_ITERS_LOGIN_HASH);
            this.msg_pw_hash = Utils.stringToHash(user, pw, Utils.CRYPT_ITERS_MSG_HASH);
        } else {
            this.login_pw_hash = login_hash;
            this.msg_pw_hash = msg_hash;
        }

        this.server = server;
        this.ignore_cert = ignore_cert;

        this.encryption_key = new Key(this.msg_pw_hash);
        this.token_b64 = createAuthToken(this.user, this.login_pw_hash);
        Log.d(logtag, "Called Constructor with:\n" + this.user + " " + this.pw + " " +  this.login_pw_hash + " "
                + this.msg_pw_hash + " " + this.server + " " + token_b64 + " " + this.ignore_cert);
    }

    private String createAuthToken(String user, String pw) {
        /**
         *  Create b64 encoded String Token for Basic Auth to use for API requests
         */
        String token = user + ":" + pw;
        try {
            byte[] token_byte = token.getBytes(StandardCharsets.UTF_8);
            token = Base64.encodeToString(token_byte, Base64.NO_WRAP);
        } catch (Exception UnsupportedEncodingException) {
            Log.e(logtag, "UnsupportedEncodingException");
            token = null;
        }
        return token;
    }
}
