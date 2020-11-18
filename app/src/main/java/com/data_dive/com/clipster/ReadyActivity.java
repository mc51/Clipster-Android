package com.data_dive.com.clipster;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Finished all authentication - listener mode
 */

public class ReadyActivity extends AppCompatActivity {

    public final static  String APP_NAME = "Clipster";
    public final String logtag = this.getClass().getSimpleName();
    private static final int BUTTON_DELAY = 5000;
    int num_clicks = 0;
    boolean throttle_clicks = false;
    TextView get_clip, set_clip, edit_creds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(logtag, "onCreate");

        checkForCreds();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ready);

        get_clip = findViewById(R.id.get_clip);
        set_clip = findViewById(R.id.set_clip);
        edit_creds = findViewById(R.id.edit_creds);

        get_clip.setOnClickListener(btnListener);
        get_clip.setTag("get_clip");
        set_clip.setOnClickListener(btnListener);
        set_clip.setTag("set_clip");
        edit_creds.setOnClickListener(btnListener);
        edit_creds.setTag("edit_creds");
    }

    private View.OnClickListener btnListener = new View.OnClickListener() {
        public void onClick(View v) {
            // Delay excessive clicks
            String action_tag = v.getTag().toString();
            Log.d(logtag, "Clicked Button: " + action_tag);
            num_clicks += 1;

            if(num_clicks % 3 != 0 && !throttle_clicks) {
                prepareClipRequest(action_tag);
            } else {
                throttle_clicks = true;
                Toast.makeText(getBaseContext(), APP_NAME + " - " + getString(R.string.button_delay_msg),
                        Toast.LENGTH_LONG).show();
                get_clip.setEnabled(false);
                set_clip.setEnabled(false);
                get_clip.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        get_clip.setEnabled(true);
                        set_clip.setEnabled(true);
                        throttle_clicks = false;
                    }
                }, BUTTON_DELAY);
            }
        }
    };

    private void checkForCreds() {
        if(!Utils.areCredsSaved(this)) {
            Log.d(logtag, "Creds are not saved yet. Start Main and ask for them.");
            Intent i = new Intent(this, MainActivity.class);
            startActivity(i);
        } else {
            Log.d(logtag, "Creds available. Stay in Ready Activity.");
        }
    }

    private void prepareClipRequest(String action) {
        if(action.equals("get_clip")) {
            Log.d(logtag,"Calling GetClip function");
            NetClient client = new NetClient(this);
            client.GetClipFromServer();
        } else if(action.equals("set_clip")) {
            Log.d(logtag, "Calling SetClip function");
            NetClient client = new NetClient(this);
            String clip = Utils.checkClipboard(this);
            client.SetClipOnServer(clip);
        } else if(action.equals("edit_creds")) {
            Log.d(logtag, "Calling edit_creds -> Start Main Activity");
            Intent i = new Intent(this, MainActivity.class);
            i.setAction(Intent.ACTION_EDIT);
            startActivity(i);
        }
    }
}