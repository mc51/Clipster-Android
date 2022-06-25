package com.data_dive.com.clipster;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.amdelamar.jhash.Hash;
import com.amdelamar.jhash.algorithms.Type;
import com.macasaet.fernet.Key;
import com.macasaet.fernet.StringValidator;
import com.macasaet.fernet.Token;
import com.macasaet.fernet.TokenExpiredException;
import com.macasaet.fernet.TokenValidationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;

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

    public static final Integer MAX_CLIP_SHOW_LEN = 120;
    public static final Integer MIN_PW_LENGTH = 8;


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

    public static void setClipboard(Context context, String clip_text, String clip_format) {
        // Move text to clipboard and display message
        String clip_text_show = "";

        if (clip_format.equals("txt")) {
            clip_text_show = clip_text.substring(0, Math.min(clip_text.length(), MAX_CLIP_SHOW_LEN));
            if (clip_text.length() > MAX_CLIP_SHOW_LEN) {
                clip_text_show = clip_text_show + " [...]";
            }
            Log.d(logtag, "Storing text to clipboard: " + clip_text);
            ClipboardManager cb = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Clipster", clip_text);
            cb.setPrimaryClip(clip);
        }
        // Show Preview
        Toast.makeText(context, context.getString(R.string.app_name) + " - set Clipboard to:\n"
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

    public static String decryptClip(Context context, String text) {
        // Decrypt Text with Fernet using key and return it as string
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

    public static boolean validateServerURI(String server_uri) {
        /**
         *  Basic validity check for the server address
         */
        if(server_uri.startsWith("https://localhost")) { return true; }
        return android.util.Patterns.WEB_URL.matcher(server_uri).matches();
    }

    public static String formatURIProtocol(String server_uri) {
        /**
         *  Make sure we always use https:// and have no trailing slashes in URI
         */
        server_uri = server_uri.replaceFirst("/*$", "");
        try {
            if (server_uri.substring(0, 7).toLowerCase().startsWith("http://")) {
                Log.d(logtag, "server contains http:// replacing with https://");
                server_uri = server_uri.replace("http://", "https://");
            } else if (!server_uri.substring(0, 8).toLowerCase().startsWith("https://")) {
                Log.w(logtag, "No protocol provided, adding https://");
                server_uri = "https://" + server_uri;
            }
            return server_uri;
        } catch (Exception e) {
            Log.e(logtag, e.toString());
            return server_uri;
        }
    }

    public static Bitmap B64StringToImage(String imgString) {
        // Decode base64 string containing image to Bitmap image and return
        Bitmap decodedImage = null;
        try {
            byte[] imgBytes = Base64.decode(imgString, Base64.DEFAULT);
            decodedImage = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
        } catch (Exception e) {
            Log.e(logtag, "Error B64StringToImage:" + e);
            // TODO: Return placeholder image if no valid image found
        }
        return decodedImage;
    }

    public static JSONArray DecryptClips(Context mContext, JSONArray clips) {
        // DecryptClips from encrypted text to cleartext, add text_decrypted key to clips
        String text;
        JSONObject clip;

        for(int i=0;i<=clips.length();i++) {
            try {
                clip = clips.getJSONObject(i);
                text = clip.getString("text");
                String text_decrypted = Utils.decryptClip(mContext, text);
                clip.put("text_decrypted", text_decrypted);
                clips.put(i, clip);
                Log.d(logtag, "Processed:" + clips.getJSONObject(i).getString("text_decrypted"));
            } catch(JSONException e) {
            }
        }
        return clips;
    }

    public static Uri BitmapToTempFileAsUri(Context mContext, Bitmap bitmap) {
        // Store Bitmap image to a temp .png file and return uri via FileProvider
        File file = new File(mContext.getCacheDir(),"tmp.png");
        Log.d(logtag, "file: " + file);
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();
            file.setReadable(true, false);
        } catch (Exception e) {
            Log.e(logtag, "Error: " + e);
        }
        Uri imageUri = FileProvider.getUriForFile(
                mContext,
                "com.data_dive.com.clipster.provider",
                file);
        Log.d(logtag, "URI: " + imageUri);
        return imageUri;
    }

    public static void SaveBitmapToGallery(Context mContext, Bitmap image, String title, String description) {
        try {
            ContentResolver cr = mContext.getContentResolver();
            MediaStore.Images.Media.insertImage(cr, image, title, description);
        } catch(Exception e) {
            Log.e(logtag, "Error: " + e);
        }
        Log.d(logtag, "Saved image to gallery: " + title + " " + description);
    }

}
