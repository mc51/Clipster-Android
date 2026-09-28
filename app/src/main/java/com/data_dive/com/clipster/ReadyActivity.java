package com.data_dive.com.clipster;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * We're authenticated - Show main screen
 * Deal with button clicks -> requests
 *
 */

public class ReadyActivity extends AppCompatActivity implements NetClient.Listener {

    private MaterialButton get_last_clip, get_all_clips, set_clip, edit_creds;
    private LinearProgressIndicator progress;
    private int running_requests = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        if (!Utils.areCredsSaved(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_ready);

        Credentials creds = Utils.getCreds(this);
        ((TextView) findViewById(R.id.active_user)).setText(getString(R.string.logged_in_as, creds.user));
        ((TextView) findViewById(R.id.active_server)).setText(creds.server);

        progress = findViewById(R.id.progress);
        get_last_clip = findViewById(R.id.get_last_clip);
        get_all_clips = findViewById(R.id.get_all_clips);
        set_clip = findViewById(R.id.set_clip);
        edit_creds = findViewById(R.id.edit_creds);

        get_last_clip.setOnClickListener(v -> new NetClient(this).GetLastClipFromServer());
        get_all_clips.setOnClickListener(v -> new NetClient(this).GetAllClipsFromServer());
        set_clip.setOnClickListener(v -> shareClipboard());
        edit_creds.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.setAction(Intent.ACTION_EDIT);
            startActivity(i);
            finish();
        });

        if (savedInstanceState == null && MainActivity.ACTION_GET_LAST_CLIP.equals(getIntent().getAction())) {
            new NetClient(this).GetLastClipFromServer();
        }
    }

    private void shareClipboard() {
        String clip = Utils.checkClipboard(this);
        if (clip.isEmpty()) {
            Toast.makeText(this, R.string.msg_clipboard_empty, Toast.LENGTH_LONG).show();
            return;
        }
        new NetClient(this).SetClipOnServer(clip, Utils.FORMAT_TXT);
    }

    @Override
    public void onRequestStarted() {
        running_requests++;
        updateLoading();
    }

    @Override
    public void onRequestFinished() {
        running_requests = Math.max(0, running_requests - 1);
        updateLoading();
    }

    private void updateLoading() {
        boolean loading = running_requests > 0;
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        get_last_clip.setEnabled(!loading);
        get_all_clips.setEnabled(!loading);
        set_clip.setEnabled(!loading);
        edit_creds.setEnabled(!loading);
    }
}
