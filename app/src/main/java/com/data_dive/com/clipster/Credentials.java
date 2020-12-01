package com.data_dive.com.clipster;

import android.util.Base64;
import android.util.Log;

/**
 * Class to define credentials object to pass around
 */

public final class Credentials {
    private static String logtag = "Credentials";
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
    }
}
