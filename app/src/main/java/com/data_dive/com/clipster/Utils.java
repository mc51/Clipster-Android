package com.data_dive.com.clipster;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import static android.content.Context.CLIPBOARD_SERVICE;


public class Utils {

    private static String logtag = "Utils";
    private static final String PREF_FILE = "pref_file";
    private static final String PREF_IS_SAVED = "saved_id";
    private static final String PREF_CRED_SERVER = "cred_server";
    private static final String PREF_CRED_USER = "cred_user";
    private static final String PREF_CRED_PASSWORD = "cred_password";
    private static final String PREF_CRED_TOKEN = "cred_token";

    public static boolean areCredsSaved(Context context) {
        // Check if creds are saved to file
        Log.d(logtag, "AreCredsSaved");
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return pref.getBoolean(PREF_IS_SAVED, false);
    }

    public static void saveCreds(Context context, String server, String token) {
        // Save Creds to file
        Log.d(logtag, "saveCreds - Server: " + server + " Token: " + token);
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(PREF_CRED_SERVER, server);
        editor.putString(PREF_CRED_TOKEN, token);
        editor.putBoolean(PREF_IS_SAVED, true);
        editor.apply();
    }

    public static String[] getCreds(Context context) {
        // Get and return saved Creds from file
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        String[] creds = new String[2];
        creds[0] = pref.getString(PREF_CRED_SERVER, "");
        creds[1] = pref.getString(PREF_CRED_TOKEN, "");
        Log.d(logtag, "getCreds - Server: " + creds[0] + " Token: " + creds[1]);
        return creds;
    }

    public static void clearCreds(Context context) {
        // Clear all saved files
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
}
