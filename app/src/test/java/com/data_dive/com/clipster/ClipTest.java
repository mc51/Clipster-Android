package com.data_dive.com.clipster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.macasaet.fernet.Key;
import java.time.Instant;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ClipTest {

    private static final Key KEY = new Key("BYVy_Lfm1pO5uSEQRq3UmHDegjhE2RpFt60PVZJjfp4=");

    private static JSONObject clipJson(String text, String format) throws Exception {
        return new JSONObject()
                .put("id", 1)
                .put("user", "alice")
                .put("text", text)
                .put("format", format)
                .put("device", "desktop")
                .put("created_at", "2026-09-28T19:55:18.431234Z");
    }

    @Test
    public void listFromJson_decryptsArrayWithDeviceAndTimestamp() throws Exception {
        String body = new JSONArray()
                .put(clipJson(Crypto.encrypt(KEY, "first"), "txt"))
                .put(clipJson(Crypto.encrypt(KEY, "iVBOR"), "img"))
                .toString();

        List<Clip> clips = Clip.listFromJson(body, KEY, "error");

        assertEquals(2, clips.size());
        assertEquals("first", clips.get(0).text);
        assertEquals("desktop", clips.get(0).device);
        assertEquals(Instant.parse("2026-09-28T19:55:18.431234Z"), clips.get(0).createdAt);
        assertTrue(clips.get(1).isImage());
    }

    @Test
    public void listFromJson_acceptsSingleObject() throws Exception {
        String body = clipJson(Crypto.encrypt(KEY, "only"), "txt").toString();
        assertEquals("only", Clip.listFromJson(body, KEY, "error").get(0).text);
    }

    @Test
    public void listFromJson_badClipDoesNotHideOthers() throws Exception {
        String body = new JSONArray()
                .put(clipJson("garbage", "img"))
                .put(clipJson(Crypto.encrypt(KEY, "ok"), "txt"))
                .toString();

        List<Clip> clips = Clip.listFromJson(body, KEY, "error");

        assertEquals("error", clips.get(0).text);
        // Shown as text, not handed to the image decoder
        assertEquals(Clip.FORMAT_TXT, clips.get(0).format);
        assertEquals("ok", clips.get(1).text);
    }

    @Test
    public void listFromJson_missingOptionalFields() throws Exception {
        String body = new JSONArray()
                .put(new JSONObject().put("text", Crypto.encrypt(KEY, "old server")))
                .toString();

        Clip clip = Clip.listFromJson(body, KEY, "error").get(0);

        assertEquals(Clip.FORMAT_TXT, clip.format);
        assertEquals("", clip.device);
        assertNull(clip.createdAt);
    }

    @Test
    public void parseTimestamp_acceptsOffsetsAndRejectsGarbage() {
        assertEquals(Instant.parse("2026-09-28T17:55:18Z"), Clip.parseTimestamp("2026-09-28T19:55:18+02:00"));
        assertNull(Clip.parseTimestamp("yesterday"));
        assertNull(Clip.parseTimestamp(""));
    }
}
