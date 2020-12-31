package com.data_dive.com.clipster;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 *  Add our app to "share with" menu when sharing texts. Alternative to ClipboardWatcherService
 *  which does only work up to Android 9
 */

public class ShareActivity extends AppCompatActivity {

    public final String logtag = this.getClass().getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(logtag, "Oncreate");
        super.onCreate(savedInstanceState);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Log.d(logtag, "ACTION_SEND received");
            if ("text/plain".equals(type)) {
                Log.d(logtag, "text/plain received.");
                 handleSharedText(intent); // Handle text being sent
            } else {
                Log.d(logtag, "unknown MIME Type");
            }
        }
    }

    private void handleSharedText(Intent intent) {
        Log.d(logtag, "handleSendText");
        String shared_clip = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared_clip != null) {
            Log.d(logtag, "received Text: " + shared_clip);
            if(shared_clip.toLowerCase().contains(getString(R.string.toggle_get_keyword))) {
                // On keyword update clip from server
                Log.d(logtag, "Keyword detected. Getting new clip from server.");
                ClientGetClip();
            } else {
                // Update clip to server
                ClientSetClip(shared_clip);
            }
            Toast.makeText(this, getString(R.string.app_name) + " got new text: " + shared_clip,
                    Toast.LENGTH_LONG).show();
        }
        // directly finish so that our activity doesn't show in foreground
        finish();
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
