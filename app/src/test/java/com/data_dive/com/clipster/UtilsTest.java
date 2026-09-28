package com.data_dive.com.clipster;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilsTest {

    @Test
    public void formatServerUri_addsHttpsWhenMissing() {
        assertEquals("https://clipster.cc", Utils.formatServerUri("clipster.cc"));
    }

    @Test
    public void formatServerUri_upgradesHttp() {
        assertEquals("https://example.org:9999", Utils.formatServerUri("http://example.org:9999"));
        assertEquals("https://example.org", Utils.formatServerUri("HTTP://example.org"));
    }

    @Test
    public void formatServerUri_keepsHttpsAndStripsTrailingSlashesAndWhitespace() {
        assertEquals("https://example.org/sub", Utils.formatServerUri(" https://example.org/sub// "));
        assertEquals("https://Example.org", Utils.formatServerUri("HTTPS://Example.org/"));
    }
}
