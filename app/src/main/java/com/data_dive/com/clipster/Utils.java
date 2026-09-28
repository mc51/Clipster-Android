package com.data_dive.com.clipster;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import com.macasaet.fernet.Key;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import static android.content.Context.CLIPBOARD_SERVICE;

/**
 * implement common functions that are used in different Activities / Classes
 * Never log credentials, hashes, tokens or clip contents here.
 */

public class Utils {

    private static final String logtag = "Utils";

    private static final String PREF_FILE = "pref_file";
    private static final String PREF_IS_SAVED = "saved_id";
    private static final String PREF_CRED_SERVER = "cred_server";
    private static final String PREF_CRED_USER = "cred_user";
    private static final String PREF_CRED_LOGIN_PW_HASH = "cred_login_pw_hash";
    private static final String PREF_CRED_MSG_PW_HASH = "cred_msg_pw_hash";
    private static final String PREF_CRED_TOKEN = "cred_token";
    private static final String PREF_CRED_IGNORE_CERT = "cred_ignore_cert";

    public static final String FORMAT_TXT = "txt";
    public static final String FORMAT_IMG = "img";

    public static final int MAX_CLIP_SHOW_LEN = 120;
    public static final int MIN_PW_LENGTH = 8;


    public static boolean areCredsSaved(Context context) {
        // Check if valid creds are saved to file already. Invalid ones (e.g. corrupted or
        // restored from a backup of an older version) are cleared so the user can log in again.
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        if (!pref.getBoolean(PREF_IS_SAVED, false)) {
            return false;
        }
        try {
            getCreds(context);
            return true;
        } catch (RuntimeException e) {
            Log.e(logtag, "Saved credentials are invalid, clearing them: " + e.getClass().getSimpleName());
            clearCreds(context);
            return false;
        }
    }

    public static void saveCreds(Context context, Credentials creds) {
        Log.d(logtag, "saveCreds: " + creds);
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
        return Credentials.fromSavedHashes(
                pref.getString(PREF_CRED_USER, ""),
                pref.getString(PREF_CRED_LOGIN_PW_HASH, ""),
                pref.getString(PREF_CRED_MSG_PW_HASH, ""),
                pref.getString(PREF_CRED_SERVER, ""),
                pref.getBoolean(PREF_CRED_IGNORE_CERT, false));
    }

    public static void clearCreds(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        pref.edit().clear().apply();
    }

    public static String clipPreview(Context context, String clip_text, String clip_format) {
        if (FORMAT_IMG.equals(clip_format)) {
            return context.getString(R.string.clip_preview_image);
        }
        if (clip_text.length() > MAX_CLIP_SHOW_LEN) {
            return clip_text.substring(0, MAX_CLIP_SHOW_LEN) + " [...]";
        }
        return clip_text;
    }

    /**
     * Create the clipboard content for a clip. Images are decoded and written to a temp file,
     * so call this in the background. Returns null if the clip can't be converted.
     */
    public static ClipData createClipData(Context context, String clip_text, String clip_format) {
        if (FORMAT_IMG.equals(clip_format)) {
            Uri imageUri = BitmapToTempFileAsUri(context, B64StringToImage(clip_text));
            if (imageUri == null) {
                return null;
            }
            return ClipData.newUri(context.getContentResolver(), "Clipster Image", imageUri);
        }
        return ClipData.newPlainText("Clipster", clip_text);
    }

    public static void setClipboard(Context context, ClipData clip, String preview) {
        if (clip == null) {
            Toast.makeText(context, R.string.error_set_clipboard, Toast.LENGTH_LONG).show();
            return;
        }
        ClipboardManager cb = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(clip);
        // From Android 13 on the system shows its own confirmation with a preview
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(R.string.msg_clipboard_set, preview),
                    Toast.LENGTH_LONG).show();
        }
    }

    public static String checkClipboard(Context context) {
        String clip = "";
        ClipboardManager cb = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        ClipData cd = cb.getPrimaryClip();
        if (cd != null && cd.getItemCount() > 0) {
            // coerceToText also handles HTML and URI clips, where getText() may be null
            CharSequence text = cd.getItemAt(0).coerceToText(context);
            if (text != null) {
                clip = text.toString();
            }
        }
        return clip;
    }

    public static boolean validateServerURI(String server_uri) {
        /*
         *  Basic validity check for the server address
         */
        if (server_uri.startsWith("https://localhost")) { return true; }
        return android.util.Patterns.WEB_URL.matcher(server_uri).matches();
    }

    public static String formatURIProtocol(String server_uri) {
        /*
         *  Make sure we always use https:// and have no trailing slashes in URI
         */
        String uri = server_uri.trim().replaceFirst("/+$", "");
        String lower = uri.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) {
            return "https://" + uri.substring("https://".length());
        }
        if (lower.startsWith("http://")) {
            return "https://" + uri.substring("http://".length());
        }
        return "https://" + uri;
    }

    public static Bitmap B64StringToImage(String imgString) {
        // Decode base64 string containing image to Bitmap image, null if it's not a valid image
        try {
            byte[] imgBytes = Base64.decode(imgString, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
        } catch (IllegalArgumentException e) {
            Log.e(logtag, "Error B64StringToImage: " + e);
            return null;
        }
    }

    /**
     * Decode a base64 image downsampled by powers of two, while keeping the longer side at least
     * maxSize pixels. Saves a lot of memory for list thumbnails.
     */
    public static Bitmap B64StringToThumbnail(String imgString, int maxSize) {
        try {
            byte[] imgBytes = Base64.decode(imgString, Base64.DEFAULT);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length, opts);
            int sample = 1;
            while (Math.max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSize) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length, opts);
        } catch (IllegalArgumentException e) {
            Log.e(logtag, "Error B64StringToThumbnail: " + e);
            return null;
        }
    }

    public static String BitmapToB64String(Bitmap imageBitmap) {
        // Encode Bitmap Image to a base64 string PNG, null if there is no image
        if (imageBitmap == null) {
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)) {
            Log.e(logtag, "Error BitmapToB64String: compress failed");
            return null;
        }
        return Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT);
    }

    /**
     * Decrypt all clips and add the cleartext as "text_decrypted". Clips that can't be decrypted
     * get errorText instead, so one bad clip doesn't hide the others.
     */
    public static JSONArray decryptClips(JSONArray clips, Key key, String errorText) {
        for (int i = 0; i < clips.length(); i++) {
            try {
                JSONObject clip = clips.getJSONObject(i);
                String text_decrypted;
                try {
                    text_decrypted = Crypto.decrypt(key, clip.getString("text"));
                } catch (RuntimeException e) {
                    Log.e(logtag, "Could not decrypt clip " + i + ": " + e.getClass().getSimpleName());
                    text_decrypted = errorText;
                    clip.put("format", FORMAT_TXT);
                }
                clip.put("text_decrypted", text_decrypted);
            } catch (JSONException e) {
                Log.e(logtag, "Invalid clip " + i + ": " + e);
            }
        }
        return clips;
    }

    public static Uri BitmapToTempFileAsUri(Context context, Bitmap bitmap) {
        // Store Bitmap image to a temp .png file and return uri via FileProvider, null on failure
        if (bitmap == null) {
            return null;
        }
        File file = new File(context.getCacheDir(), "tmp.png");
        try (FileOutputStream fOut = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
        } catch (IOException e) {
            Log.e(logtag, "Error writing temp image: " + e);
            return null;
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
    }

    public static Bitmap ImageUriToBitmap(Context context, Uri imageUri) {
        // Load an image from a content URI, null if it can't be read
        ContentResolver cr = context.getContentResolver();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Software bitmap, hardware bitmaps can't be compressed to PNG
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, imageUri),
                        (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
            }
            return MediaStore.Images.Media.getBitmap(cr, imageUri);
        } catch (IOException | SecurityException e) {
            Log.e(logtag, "Error reading shared image: " + e);
            return null;
        }
    }

    public static String ImageUriToB64String(Context context, Uri imageUri) {
        return BitmapToB64String(ImageUriToBitmap(context, imageUri));
    }

    /**
     * Save image to the gallery (Pictures/Clipster from Android 10 on). Returns true on success.
     */
    public static boolean SaveBitmapToGallery(Context context, Bitmap image) {
        if (image == null) {
            return false;
        }
        ContentResolver cr = context.getContentResolver();
        String name = "clipster_" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Clipster");
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return false;
            }
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os != null && image.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                    return true;
                }
            } catch (IOException e) {
                Log.e(logtag, "Error saving image: " + e);
            }
            cr.delete(uri, null, null);
            return false;
        }
        return MediaStore.Images.Media.insertImage(cr, image, name, "Image shared via Clipster") != null;
    }

}
