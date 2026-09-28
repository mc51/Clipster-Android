package com.data_dive.com.clipster;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;

/**
 * Adds Clipster to the "share with" menu for texts and images.
 * Shows a small progress card until the clip is uploaded, then finishes.
 */
public class ShareActivity extends AppCompatActivity implements NetClient.Listener {

    private int runningRequests = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Utils.areCredsSaved(this)) {
            Toast.makeText(this, R.string.msg_login_first, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (savedInstanceState != null) {
            // Recreated (e.g. rotated) while uploading, the running request finishes the old instance
            finish();
            return;
        }

        setContentView(R.layout.activity_share);

        Intent intent = getIntent();
        String type = intent.getType();
        if (!Intent.ACTION_SEND.equals(intent.getAction()) || type == null) {
            finish();
        } else if (type.equals("text/plain")) {
            shareText(intent);
        } else if (type.startsWith("image/")) {
            shareImage(intent);
        } else {
            Toast.makeText(this, getString(R.string.error_share_unknown_type, type), Toast.LENGTH_LONG)
                    .show();
            finish();
        }
    }

    private void shareImage(Intent intent) {
        Uri imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        if (imageUri == null) {
            finish();
            return;
        }
        // Decoding and re-encoding large images takes a while
        Async.run(() -> Utils.imageUriToB64(this, imageUri), (image, error) -> {
            if (image == null) {
                Toast.makeText(this, R.string.error_open_image, Toast.LENGTH_LONG)
                        .show();
                finish();
                return;
            }
            new NetClient(this).shareClip(image, Clip.FORMAT_IMG);
        });
    }

    private void shareText(Intent intent) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null || text.isEmpty()) {
            finish();
            return;
        }
        new NetClient(this).shareClip(text, Clip.FORMAT_TXT);
    }

    @Override
    public void onRequestStarted() {
        runningRequests++;
    }

    @Override
    public void onRequestFinished() {
        // A retry after trusting the server certificate starts before the first request finishes
        runningRequests = Math.max(0, runningRequests - 1);
        if (runningRequests == 0) {
            finish();
        }
    }
}
