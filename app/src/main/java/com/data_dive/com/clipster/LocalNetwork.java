package com.data_dive.com.clipster;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * From Android 17 on, apps need the ACCESS_LOCAL_NETWORK runtime permission to reach
 * servers on the local network, e.g. a self-hosted Clipster server at home.
 */
final class LocalNetwork {

    static final String PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final int ANDROID_17 = 37;

    private LocalNetwork() {}

    /** True if the permission is enforced on this device and not granted yet */
    static boolean isPermissionMissing(Context context) {
        return Build.VERSION.SDK_INT >= ANDROID_17
                && context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Whether the server resolves to a local network address. Does a DNS lookup, so call it in the background.
     */
    static boolean isLocalServer(String serverUri) {
        String host;
        try {
            host = URI.create(serverUri).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) {
            return false;
        }
        if (host.toLowerCase(Locale.ROOT).endsWith(".local")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isLocalAddress(address)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            return false;
        }
        return false;
    }

    static boolean isLocalAddress(InetAddress address) {
        if (address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        // IPv6 unique local addresses fc00::/7
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
