package com.data_dive.com.clipster;

import static org.junit.Assert.assertEquals;

import com.macasaet.fernet.Key;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class UtilsTest {

    private static final Key KEY = new Key("BYVy_Lfm1pO5uSEQRq3UmHDegjhE2RpFt60PVZJjfp4=");

    @Test
    public void formatURIProtocol_addsHttpsWhenMissing() {
        assertEquals("https://clipster.cc", Utils.formatURIProtocol("clipster.cc"));
    }

    @Test
    public void formatURIProtocol_upgradesHttp() {
        assertEquals("https://example.org:9999", Utils.formatURIProtocol("http://example.org:9999"));
        assertEquals("https://example.org", Utils.formatURIProtocol("HTTP://example.org"));
    }

    @Test
    public void formatURIProtocol_keepsHttpsAndStripsTrailingSlashesAndWhitespace() {
        assertEquals("https://example.org/sub", Utils.formatURIProtocol(" https://example.org/sub// "));
        assertEquals("https://Example.org", Utils.formatURIProtocol("HTTPS://Example.org/"));
    }

    @Test
    public void decryptClips_addsCleartext() throws Exception {
        JSONArray clips = new JSONArray()
                .put(new JSONObject().put("text", Crypto.encrypt(KEY, "first")).put("format", "txt"))
                .put(new JSONObject().put("text", Crypto.encrypt(KEY, "second")).put("format", "txt"));

        Utils.decryptClips(clips, KEY, "error");

        assertEquals("first", clips.getJSONObject(0).getString("text_decrypted"));
        assertEquals("second", clips.getJSONObject(1).getString("text_decrypted"));
    }

    @Test
    public void decryptClips_badClipDoesNotHideOthers() throws Exception {
        JSONArray clips = new JSONArray()
                .put(new JSONObject().put("text", "garbage").put("format", "img"))
                .put(new JSONObject().put("text", Crypto.encrypt(KEY, "ok")).put("format", "txt"));

        Utils.decryptClips(clips, KEY, "error");

        assertEquals("error", clips.getJSONObject(0).getString("text_decrypted"));
        // Shown as text, not handed to the image decoder
        assertEquals("txt", clips.getJSONObject(0).getString("format"));
        assertEquals("ok", clips.getJSONObject(1).getString("text_decrypted"));
    }
}
