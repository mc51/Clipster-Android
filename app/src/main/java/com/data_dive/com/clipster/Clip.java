package com.data_dive.com.clipster;

import android.util.Log;
import com.macasaet.fernet.Key;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A decrypted clip as returned by the server (see ClipSerializer in Clipster-Server)
 */
public final class Clip {

    public static final String FORMAT_TXT = "txt";
    public static final String FORMAT_IMG = "img";

    private static final String TAG = "Clip";

    /** Decrypted text, or base64 encoded PNG for images */
    public final String text;

    public final String format;
    /** Name of the device that shared the clip, may be empty */
    public final String device;
    /** When the clip was shared, null if the server didn't send a valid timestamp */
    public final Instant createdAt;

    public Clip(String text, String format, String device, Instant createdAt) {
        this.text = text;
        this.format = format;
        this.device = device;
        this.createdAt = createdAt;
    }

    public boolean isImage() {
        return FORMAT_IMG.equals(format);
    }

    /**
     * Parse and decrypt the server response, which is an array of clips or a single clip.
     * Clips that can't be decrypted are shown as text with errorText, so one bad clip doesn't hide the others.
     */
    public static List<Clip> listFromJson(String body, Key key, String errorText) throws JSONException {
        JSONArray array;
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            array = new JSONArray().put(new JSONObject(trimmed));
        } else {
            array = new JSONArray(trimmed);
        }
        List<Clip> clips = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            clips.add(fromJson(array.getJSONObject(i), key, errorText));
        }
        return clips;
    }

    static Clip fromJson(JSONObject json, Key key, String errorText) {
        String format = json.optString("format", FORMAT_TXT);
        String text;
        try {
            text = Crypto.decrypt(key, json.getString("text"));
        } catch (JSONException | RuntimeException e) {
            Log.e(TAG, "Could not decrypt clip: " + e.getClass().getSimpleName());
            text = errorText;
            format = FORMAT_TXT;
        }
        return new Clip(text, format, json.optString("device", ""), parseTimestamp(json.optString("created_at")));
    }

    static Instant parseTimestamp(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            // Django REST framework sends ISO 8601, e.g. 2026-09-28T19:55:18.431234Z or with +02:00
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
