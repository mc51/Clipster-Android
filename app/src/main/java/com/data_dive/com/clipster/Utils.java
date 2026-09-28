package com.data_dive.com.clipster;

import static android.content.Context.CLIPBOARD_SERVICE;

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
import android.util.Patterns;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Common functions used by different Activities and classes.
 * Never log credentials, hashes, tokens or clip contents here.
 */
public final class Utils {

    private static final String TAG = "Utils";

    // Keys are kept from earlier versions, so saved logins keep working after updates
    private static final String PREF_FILE = "pref_file";
    private static final String PREF_IS_SAVED = "saved_id";
    private static final String PREF_CRED_SERVER = "cred_server";
    private static final String PREF_CRED_USER = "cred_user";
    private static final String PREF_CRED_LOGIN_PW_HASH = "cred_login_pw_hash";
    private static final String PREF_CRED_MSG_PW_HASH = "cred_msg_pw_hash";
    private static final String PREF_CRED_TOKEN = "cred_token";
    private static final String PREF_CRED_ALLOW_SELF_SIGNED = "cred_ignore_cert";
    private static final String PREF_CRED_PINNED_CERT = "cred_pinned_cert";

    public static final int MAX_CLIP_SHOW_LEN = 120;
    public static final int MIN_PW_LENGTH = 8;

    private Utils() {}

    public static boolean areCredsSaved(Context context) {
        // Invalid creds (e.g. corrupted or from an incompatible version) are cleared so the user can log in again
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        if (!pref.getBoolean(PREF_IS_SAVED, false)) {
            return false;
        }
        try {
            getCreds(context);
            return true;
        } catch (RuntimeException e) {
            Log.e(
                    TAG,
                    "Saved credentials are invalid, clearing them: "
                            + e.getClass().getSimpleName());
            clearCreds(context);
            return false;
        }
    }

    public static void saveCreds(Context context, Credentials creds) {
        Log.d(TAG, "saveCreds: " + creds);
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_CRED_USER, creds.user)
                .putString(PREF_CRED_LOGIN_PW_HASH, creds.loginHash)
                .putString(PREF_CRED_MSG_PW_HASH, creds.msgHash)
                .putString(PREF_CRED_TOKEN, creds.authToken)
                .putString(PREF_CRED_SERVER, creds.server)
                .putBoolean(PREF_CRED_ALLOW_SELF_SIGNED, creds.allowSelfSigned)
                .putString(PREF_CRED_PINNED_CERT, creds.encodedPinnedCertificate())
                .putBoolean(PREF_IS_SAVED, true)
                .apply();
    }

    public static Credentials getCreds(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return Credentials.fromSaved(
                pref.getString(PREF_CRED_USER, ""),
                pref.getString(PREF_CRED_LOGIN_PW_HASH, ""),
                pref.getString(PREF_CRED_MSG_PW_HASH, ""),
                pref.getString(PREF_CRED_SERVER, ""),
                pref.getBoolean(PREF_CRED_ALLOW_SELF_SIGNED, false),
                pref.getString(PREF_CRED_PINNED_CERT, ""));
    }

    public static void clearCreds(Context context) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    public static String clipPreview(Context context, String text, String format) {
        if (Clip.FORMAT_IMG.equals(format)) {
            return context.getString(R.string.clip_preview_image);
        }
        if (text.length() > MAX_CLIP_SHOW_LEN) {
            return text.substring(0, MAX_CLIP_SHOW_LEN) + " [...]";
        }
        return text;
    }

    /**
     * Create the clipboard content for a clip. Images are decoded and written to a temp file,
     * so call this in the background. Returns null if the clip can't be converted.
     */
    public static ClipData createClipData(Context context, Clip clip) {
        if (clip.isImage()) {
            Uri imageUri = bitmapToTempFileUri(context, b64ToBitmap(clip.text));
            if (imageUri == null) {
                return null;
            }
            return ClipData.newUri(context.getContentResolver(), "Clipster Image", imageUri);
        }
        return ClipData.newPlainText("Clipster", clip.text);
    }

    public static void setClipboard(Context context, ClipData clip, String preview) {
        if (clip == null) {
            Toast.makeText(context, R.string.error_set_clipboard, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        ClipboardManager cb = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(clip);
        // From Android 13 on the system shows its own confirmation with a preview
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(R.string.msg_clipboard_set, preview), Toast.LENGTH_LONG)
                    .show();
        }
    }

    public static String readClipboardText(Context context) {
        ClipboardManager cb = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        ClipData cd = cb.getPrimaryClip();
        if (cd != null && cd.getItemCount() > 0) {
            // coerceToText also handles HTML and URI clips, where getText() may be null
            CharSequence text = cd.getItemAt(0).coerceToText(context);
            if (text != null) {
                return text.toString();
            }
        }
        return "";
    }

    public static boolean isValidServerUri(String serverUri) {
        if (serverUri.startsWith("https://localhost")) {
            return true;
        }
        return Patterns.WEB_URL.matcher(serverUri).matches();
    }

    /**
     * Make sure we always use https:// and have no trailing slashes
     */
    public static String formatServerUri(String serverUri) {
        String uri = serverUri.trim().replaceFirst("/+$", "");
        String lower = uri.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) {
            return "https://" + uri.substring("https://".length());
        }
        if (lower.startsWith("http://")) {
            return "https://" + uri.substring("http://".length());
        }
        return "https://" + uri;
    }

    /** Decode a base64 image, null if it's not a valid image */
    public static Bitmap b64ToBitmap(String b64) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid base64 image: " + e);
            return null;
        }
    }

    /**
     * Decode a base64 image downsampled by powers of two, while keeping the longer side at least
     * maxSize pixels. Saves a lot of memory for list thumbnails.
     */
    public static Bitmap b64ToThumbnail(String b64, int maxSize) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            int sample = 1;
            while (Math.max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSize) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid base64 image: " + e);
            return null;
        }
    }

    /** Encode an image as base64 PNG, null if there is no image */
    public static String bitmapToB64(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)) {
            Log.e(TAG, "Could not compress image");
            return null;
        }
        return Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT);
    }

    /** Store an image in a temp PNG file and return its FileProvider uri, null on failure */
    public static Uri bitmapToTempFileUri(Context context, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        File file = new File(context.getCacheDir(), "tmp.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            Log.e(TAG, "Error writing temp image: " + e);
            return null;
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
    }

    /** Load an image from a content uri, null if it can't be read */
    public static Bitmap imageUriToBitmap(Context context, Uri imageUri) {
        ContentResolver cr = context.getContentResolver();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Software bitmap, hardware bitmaps can't be compressed to PNG
                return ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(cr, imageUri),
                        (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
            }
            return MediaStore.Images.Media.getBitmap(cr, imageUri);
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Error reading shared image: " + e);
            return null;
        }
    }

    public static String imageUriToB64(Context context, Uri imageUri) {
        return bitmapToB64(imageUriToBitmap(context, imageUri));
    }

    /**
     * Save an image to the gallery (Pictures/Clipster from Android 10 on). Returns true on success.
     */
    public static boolean saveBitmapToGallery(Context context, Bitmap image) {
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
                Log.e(TAG, "Error saving image: " + e);
            }
            cr.delete(uri, null, null);
            return false;
        }
        return MediaStore.Images.Media.insertImage(cr, image, name, "Image shared via Clipster") != null;
    }
}
