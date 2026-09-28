package com.data_dive.com.clipster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import org.junit.Test;

public class LocalNetworkTest {

    private static boolean isLocal(String ip) throws Exception {
        // Literal addresses are parsed without a DNS lookup
        return LocalNetwork.isLocalAddress(InetAddress.getByName(ip));
    }

    @Test
    public void privateAndLinkLocalAddresses_areLocal() throws Exception {
        assertTrue(isLocal("192.168.1.20"));
        assertTrue(isLocal("10.0.2.2"));
        assertTrue(isLocal("172.16.5.1"));
        assertTrue(isLocal("169.254.1.1"));
        assertTrue(isLocal("fd12:3456::1"));
        assertTrue(isLocal("fe80::1"));
    }

    @Test
    public void publicAndLoopbackAddresses_areNotLocal() throws Exception {
        assertFalse(isLocal("93.184.216.34"));
        assertFalse(isLocal("2606:4700::1111"));
        assertFalse(isLocal("127.0.0.1"));
    }

    @Test
    public void mdnsHostnames_areLocalWithoutLookup() {
        assertTrue(LocalNetwork.isLocalServer("https://clipster.local:9999"));
    }
}
