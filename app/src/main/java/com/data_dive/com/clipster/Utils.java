package com.data_dive.com.clipster;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.amdelamar.jhash.Hash;
import com.amdelamar.jhash.algorithms.Type;
import com.macasaet.fernet.Key;
import com.macasaet.fernet.StringValidator;
import com.macasaet.fernet.Token;
import com.macasaet.fernet.TokenExpiredException;
import com.macasaet.fernet.TokenValidationException;

import java.nio.charset.StandardCharsets;
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
    private static final String PREF_CRED_LOGIN_PW_HASH = "cred_login_pw_hash";
    private static final String PREF_CRED_MSG_PW_HASH = "cred_msg_pw_hash";
    private static final String PREF_CRED_TOKEN = "cred_token";
    private static final String PREF_CRED_IGNORE_CERT = "cred_ignore_cert";

    private static final Integer CRYPT_TOKEN_TTL = 3650;
    public static final Integer CRYPT_ITERS_LOGIN_HASH = 20000;
    public static final Integer CRYPT_ITERS_MSG_HASH = 10000;
    private static final Integer CRYPT_HASH_LENGTH = 32;

    private static final Integer MAX_CLIP_SHOW_LEN = 120;

    public static boolean areCredsSaved(Context context) {
        // Check if creds are saved to file already
        Log.d(logtag, "AreCredsSaved");
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return pref.getBoolean(PREF_IS_SAVED, false);
    }

    public static void saveCreds(Context context, Credentials creds) {
        // Save Creds to shared preferences file
        Log.d(logtag, creds.toString());
        Log.d(logtag, "saveCreds - User: " + creds.user + " PW: " + creds.pw
                + " Server: " + creds.server + " Token: " + creds.token_b64
                + " Ignore Cert: " + creds.ignore_cert
                + " Login PW Hash: " + creds.login_pw_hash
                + " Msg PW Hash: " + creds.msg_pw_hash);
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(PREF_CRED_USER, creds.user);
        editor.putString(PREF_CRED_LOGIN_PW_HASH, creds.login_pw_hash);
        editor.putString(PREF_CRED_MSG_PW_HASH, creds.msg_pw_hash);
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
        String login_pw_hash = pref.getString(PREF_CRED_LOGIN_PW_HASH, "");
        String msg_pw_hash = pref.getString(PREF_CRED_MSG_PW_HASH, "");
        String server = pref.getString(PREF_CRED_SERVER, "");
        boolean ignore_cert = pref.getBoolean(PREF_CRED_IGNORE_CERT, false);

        Credentials creds = new Credentials(user, "", login_pw_hash, msg_pw_hash, server, ignore_cert);

        Log.d(logtag, "getCreds: " + user + " " + login_pw_hash + " " + msg_pw_hash + " "
                + server + " " + ignore_cert);
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
        // Move text to clipboard and display message
        String clip_text_show = clip_text.substring(0, Math.min(clip_text.length(), MAX_CLIP_SHOW_LEN));
        if (clip_text.length() > MAX_CLIP_SHOW_LEN) { clip_text_show = clip_text_show + " [...]"; }
        Log.d(logtag, "Storing text to clipboard: " + clip_text);
        ClipboardManager cb = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Clipster", clip_text);
        cb.setPrimaryClip(clip);
        // Show Preview
        Toast.makeText(context, context.getString(R.string.app_name) + " - shared Clip:\n"
                + clip_text_show, Toast.LENGTH_LONG).show();
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

    public static String stringToHash(String username, String text, Integer iters) {
        /**
         * Create a hash representation of text using PBKDF2
         */
        String hash = "";
        String salt_string = "clipster_"+username+"_"+text;
        try {
            byte[] salt = salt_string.getBytes(StandardCharsets.UTF_8);
            String h = Hash.password(text.toCharArray())
                    .algorithm(Type.PBKDF2_SHA256)
                    .salt(salt)
                    .hashLength(CRYPT_HASH_LENGTH)
                    .factor(iters)
                    .create();
            hash = h.split(":")[6]; // jhash returns 7 concatenated strings, last one is hash
            Log.d(logtag, "JHash output: " + h);
        } catch (Exception e) {
            Log.e(logtag, "Error creating Hash for Key: " + e);
        }
        // jHash uses b64 standard i.e. not urlsafe encoding "/" "+" -> "_" "-"
        hash = hash.replace("/","_").replace("+", "-");
        Log.d(logtag, "Hash : " + hash);
        return hash;
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
