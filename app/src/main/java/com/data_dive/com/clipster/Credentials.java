package com.data_dive.com.clipster;

import com.macasaet.fernet.Key;

/**
 *  Define credentials object to pass around functions and activities
 *  Holds the PBKDF2 derived hashes, the password itself is never stored
 */

public final class Credentials {

    public final Key encryption_key;

    public final String user;
    public final String login_pw_hash;
    public final String msg_pw_hash;
    public final String token_b64;
    public final String server;
    public final boolean ignore_cert;

    private Credentials(String username, String login_hash, String msg_hash, String server, boolean ignore_cert) {
        this.user = username;
        this.login_pw_hash = login_hash;
        this.msg_pw_hash = msg_hash;
        this.server = server;
        this.ignore_cert = ignore_cert;
        this.encryption_key = new Key(msg_hash);
        this.token_b64 = Crypto.authToken(user, login_hash);
    }

    /**
     * Derive hashes from a password. Slow by design, don't call on the main thread.
     */
    public static Credentials fromPassword(String username, String password, String server, boolean ignore_cert) {
        return new Credentials(username,
                Crypto.deriveKey(username, password, Crypto.ITERS_LOGIN_HASH),
                Crypto.deriveKey(username, password, Crypto.ITERS_MSG_HASH),
                server, ignore_cert);
    }

    public static Credentials fromSavedHashes(String username, String login_hash, String msg_hash, String server, boolean ignore_cert) {
        return new Credentials(username, login_hash, msg_hash, server, ignore_cert);
    }

    @Override
    public String toString() {
        return "Credentials{user=" + user + ", server=" + server + ", ignore_cert=" + ignore_cert + "}";
    }
}
