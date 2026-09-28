package com.data_dive.com.clipster;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Main screen once we are logged in
 */
public class ReadyActivity extends AppCompatActivity implements NetClient.Listener {

    private static final int LOCAL_NETWORK_REQUEST = 1;

    private MaterialButton getLastClip, getAllClips, shareClipboard, editCreds;
    private LinearProgressIndicator progress;
    private int runningRequests = 0;

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
        getLastClip = findViewById(R.id.get_last_clip);
        getAllClips = findViewById(R.id.get_all_clips);
        shareClipboard = findViewById(R.id.set_clip);
        editCreds = findViewById(R.id.edit_creds);

        getLastClip.setOnClickListener(v -> new NetClient(this).getLastClip());
        getAllClips.setOnClickListener(v -> new NetClient(this).getAllClips());
        shareClipboard.setOnClickListener(v -> shareClipboard());
        editCreds.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(Intent.ACTION_EDIT);
            startActivity(intent);
            finish();
        });

        if (savedInstanceState == null) {
            requestLocalNetworkIfNeeded(creds.server);
            if (MainActivity.ACTION_GET_LAST_CLIP.equals(getIntent().getAction())) {
                new NetClient(this).getLastClip();
            }
        }
    }

    /** E.g. after updating to Android 17 with a server on the local network */
    private void requestLocalNetworkIfNeeded(String server) {
        if (!LocalNetwork.isPermissionMissing(this)) {
            return;
        }
        Async.run(() -> LocalNetwork.isLocalServer(server), (isLocal, error) -> {
            if (Boolean.TRUE.equals(isLocal) && !isFinishing()) {
                requestPermissions(new String[] {LocalNetwork.PERMISSION}, LOCAL_NETWORK_REQUEST);
            }
        });
    }

    private void shareClipboard() {
        String clip = Utils.readClipboardText(this);
        if (clip.isEmpty()) {
            Toast.makeText(this, R.string.msg_clipboard_empty, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        new NetClient(this).shareClip(clip, Clip.FORMAT_TXT);
    }

    @Override
    public void onRequestStarted() {
        runningRequests++;
        updateLoading();
    }

    @Override
    public void onRequestFinished() {
        runningRequests = Math.max(0, runningRequests - 1);
        updateLoading();
    }

    private void updateLoading() {
        boolean loading = runningRequests > 0;
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        getLastClip.setEnabled(!loading);
        getAllClips.setEnabled(!loading);
        shareClipboard.setEnabled(!loading);
        editCreds.setEnabled(!loading);
    }
}
