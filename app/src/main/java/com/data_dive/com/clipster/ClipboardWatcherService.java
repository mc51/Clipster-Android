package com.data_dive.com.clipster;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;

import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


/** Watch for changes in the clipboard
 * Code adapted from https://github.com/grepx/android-clipboard-security/
 */

public class ClipboardWatcherService extends Service {

    public static boolean serviceIsRunning = false;
    public final String logtag = this.getClass().getSimpleName();
    public String clip_text = "";

    private ClipboardManager.OnPrimaryClipChangedListener listener = new ClipboardManager.OnPrimaryClipChangedListener() {
        public void onPrimaryClipChanged() {
            performClipboardCheck();
        }
    };

    @Override
    public void onCreate() {
        // Setup Listener for when clipboard content changes
        Log.d(logtag, "Oncreate");
        try {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).addPrimaryClipChangedListener(listener);
            serviceIsRunning = true;
        } catch (Exception e) {
            Log.e(logtag, e.toString());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(logtag, "onStartCommand");
        // See: https://developer.android.com/guide/components/services.html
        return START_STICKY; // restart if killed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void performClipboardCheck() {
        Log.d(logtag, "performClipboardCheck");
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb.hasPrimaryClip()) {
            ClipData cd = cb.getPrimaryClip();
            if (cd.getDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                String clip_new = cd.getItemAt(0).getText().toString();
                Log.d(logtag, "Clipboard checked: " + clip_new);
                if(clip_new.toLowerCase().contains(getString(R.string.toggle_get_keyword))) {
                    // On keyword copy, update clip from cloud
                    Log.d(logtag, "Keyword detected. Getting new clip from server.");
                    ClientGetClip();
                } else if(!clip_new.equals(clip_text) && !clip_new.toLowerCase().contains(getString(R.string.toggle_get_keyword))) {
                    // If we have a new text and it was not our keyword set it
                    Log.d(logtag, "Clipboard changed");
                    Toast.makeText(this, getString(R.string.app_name) + " got new text!",
                            Toast.LENGTH_LONG).show();
                    clip_text = clip_new;
                    ClientSetClip(clip_text);
                } else {
                    // TODO Maybe see if we have a new clip on the server
                    // Or how to proceed ?
                    Log.d(logtag, "Clipboard unchanged");
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(logtag, "onDestroy");
        super.onDestroy();
        // Restart our service when it is getting closed
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, Restarter.class);
        this.sendBroadcast(broadcastIntent);
    }

    private void ClientSetClip(String clip) {
        // Send Request for Updating the cloud clip
        NetClient client = new NetClient(this);
        client.SetClipOnServer(clip);
    }

    private void ClientGetClip() {
        // Send Request for getting clip
        NetClient client = new NetClient(this);
        client.GetLastClipFromServer();
    }

}