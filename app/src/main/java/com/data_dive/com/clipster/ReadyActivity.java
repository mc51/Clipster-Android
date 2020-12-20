package com.data_dive.com.clipster;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
 * We're authenticated - Show main screen
 * Deal with button clicks -> requests
 *
 */

public class ReadyActivity extends AppCompatActivity {

    public final String logtag = this.getClass().getSimpleName();
    private static final int BUTTON_DELAY = 3000;
    TextView get_clip, set_clip, edit_creds, server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(logtag, "onCreate");

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ready);

        server = findViewById(R.id.active_server);
        setActiveServerAddress();

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

    private View.OnClickListener btnListener = new DebouncedOnClickListener(BUTTON_DELAY, this) {
        public void onDebouncedClick(View v) {
            // Delay excessive clicks
            String action_tag = v.getTag().toString();
            Log.d(logtag, "Clicked Button: " + action_tag);
            prepareClipRequest(action_tag);
        }
    };

    private void setActiveServerAddress() {
        // set currently used server address
        if(Utils.areCredsSaved(this)) {
            Log.d(logtag, "Creds saved, getting active server address");
            Credentials creds = Utils.getCreds(this);
            server.setText(creds.server);
        } else {
            Log.d(logtag, "Creds not saved. Not displaying active server");
            server.setText("No saved server");
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
            finish();
        }
    }
}