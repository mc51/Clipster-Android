package com.data_dive.com.clipster;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.macasaet.fernet.Key;
import com.macasaet.fernet.StringValidator;
import com.macasaet.fernet.Token;
import com.macasaet.fernet.TokenExpiredException;
import com.macasaet.fernet.TokenValidationException;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

import static android.content.Context.CLIPBOARD_SERVICE;


/**
 * implement common functions that are used in different Activities / Classes
 */

public class Utils {

    private static String logtag = "Utils";
    private static final String PREF_FILE = "pref_file";
    private static final String PREF_IS_SAVED = "saved_id";
    private static final String PREF_CRED_SERVER = "cred_server";
    private static final String PREF_CRED_USER = "cred_user";
    private static final String PREF_CRED_PASSWORD = "cred_password";
    private static final String PREF_CRED_TOKEN = "cred_token";
    private static final String PREF_CRED_IGNORE_CERT = "cred_ignore_cert";
    private static final Integer CRYPT_TOKEN_TTL = 3650;

    public static boolean areCredsSaved(Context context) {
        // Check if creds are saved to file already
        Log.d(logtag, "AreCredsSaved");
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return pref.getBoolean(PREF_IS_SAVED, false);
    }

    public static void saveCreds(Context context, Credentials creds) {
        // Save Creds to shared preferences file
        Log.d(logtag, creds.toString());
        Log.d(logtag, "saveCreds - User: " + creds.user + "PW: " + creds.pw
                + "Server: " + creds.server + " Token: " + creds.token_b64
                + "Ignore Cert: " + creds.ignore_cert);
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(PREF_CRED_USER, creds.user);
        editor.putString(PREF_CRED_PASSWORD, creds.pw);
        editor.putString(PREF_CRED_TOKEN, creds.token_b64);
        editor.putString(PREF_CRED_SERVER, creds.server);
        editor.putBoolean(PREF_CRED_IGNORE_CERT, creds.ignore_cert);
        editor.putBoolean(PREF_IS_SAVED, true);
        editor.apply();
    }

    public static Credentials getCreds(Context context) {
        // Read credentials from file and create Credentials object
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        String user = pref.getString(PREF_CRED_USER, "");
        String pw = pref.getString(PREF_CRED_PASSWORD, "");
        String server = pref.getString(PREF_CRED_SERVER, "");
        boolean ignore_cert = pref.getBoolean(PREF_CRED_IGNORE_CERT, false);

        Credentials creds = new Credentials(user, pw, server, ignore_cert);
        Log.d(logtag, "getCreds: " + user + " " + pw + " " + " " + server
                + " " + ignore_cert);
        return creds;
    }

    public static void clearCreds(Context context) {
        // Clear all saved shared pref files
        Log.d(logtag, "Clearning creds from file");
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear().commit();
    }

    public static void setClipboard(Context context, String clip_text) {
        // Move text to clipboard
        Log.d(logtag, "Storing text to clipboard");
        ClipboardManager cb = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Clipster", clip_text);
        cb.setPrimaryClip(clip);
    }

    public static String checkClipboard(Context context) {
        Log.d(logtag, "checkClipboard");
        String clip = "";
        ClipboardManager cb = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        if (cb.hasPrimaryClip()) {
            ClipData cd = cb.getPrimaryClip();
            if (cd.getDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                clip = cd.getItemAt(0).getText().toString();
                Log.d(logtag, "Clipboard checked:\n" + clip);
            }
        }
        return clip;
    }

    public static String encryptText(Context context, String text) {
        // Encrypt Text with Fernet  using key and output b64 representation
        Credentials credentials = getCreds(context);
        Key key = credentials.encryption_key;
        Token token = Token.generate(key, text);
        return token.serialise();
    }

    public static String decryptText(Context context, String text) {
        // Decrypt Text with Fernet using key
        Credentials credentials = getCreds(context);
        Key key = credentials.encryption_key;
        String cleartext = "";

        Token token = Token.fromString(text);
        StringValidator validator = new StringValidator() {
            @Override
            public TemporalAmount getTimeToLive() {
                // need to overwrite in order to set ttl of token
                return Duration.ofDays(CRYPT_TOKEN_TTL);
            }
        };

        try {
            cleartext = token.validateAndDecrypt(key, validator);
        } catch (TokenExpiredException e) {
            Log.e(logtag, "Decrypt Error : " + e);
            cleartext = "ERROR: Could not decrypt clip";
        } catch (TokenValidationException e) {
            Log.e(logtag, "Decrypt Error:" + e);
            cleartext = "ERROR: Could not decrypt clip";
        }
        Log.d(logtag, "CRYPTO: " + cleartext);
        return cleartext;
    }
}
