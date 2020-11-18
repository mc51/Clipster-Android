package com.data_dive.com.clipster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This gets called each time the phone starts up.
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
    public final String logtag = this.getClass().getSimpleName();

    public void onReceive(Context arg0, Intent arg1) {

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(arg0, Restarter.class);
        arg0.sendBroadcast(broadcastIntent);

        Log.d(logtag, "onReceive after Boot. Called restartservice.");
    }
}