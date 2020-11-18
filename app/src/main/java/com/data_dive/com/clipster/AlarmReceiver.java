package com.data_dive.com.clipster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Set an alarm at regular interval that restarts background service if it's not running
 */


public class AlarmReceiver extends BroadcastReceiver {
    public final String logtag = this.getClass().getSimpleName();

    public void onReceive(Context arg0, Intent arg1) {
        // if the user killed the service, start it back up again
        Log.d(logtag, "onReceive");
        if (!ClipboardWatcherService.serviceIsRunning) {
            Intent intent = new Intent(arg0, ClipboardWatcherService.class);
            arg0.startService(intent);
            Log.d(logtag, "Service was not running. Starting it.");
        } else {
            Log.d(logtag, "Service was already running.");
        }
    }
}