package com.data_dive.com.clipster;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static android.app.AlarmManager.INTERVAL_HOUR;

/**
 * This gets called as Broadcast intent when the service gets killed
 * Then, restart the service
 * see: https://stackoverflow.com/questions/30525784/android-keep-service-running-when-app-is-killed
 */

public class Restarter extends BroadcastReceiver {

    public final String logtag = this.getClass().getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(logtag, "onReceive");

        if(!ClipboardWatcherService.serviceIsRunning) {
            Log.d(logtag, "restarting ClipboardWatcherService");
            Intent i = new Intent(context, ClipboardWatcherService.class);
            context.startService(i);
            scheduleAlarmRestart(context);
        } else {
            Log.d(logtag, "ClipboardWatcherService already running");
        }
    }

    public void scheduleAlarmRestart(Context context) {
        // schedule an alarm which will make sure the service is still running
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        alarmMgr.setInexactRepeating(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                INTERVAL_HOUR, alarmIntent);
    }

}