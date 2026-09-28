package com.data_dive.com.clipster;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 *  Add our app to "share with" menu when sharing texts and images.
 *  Shows a small progress card until the clip is uploaded, then finishes.
 */

public class ShareActivity extends AppCompatActivity implements NetClient.Listener {

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
            handleSharedText(intent);
        } else if (type.startsWith("image/")) {
            handleSharedImage(intent);
        } else {
            Toast.makeText(this, getString(R.string.error_share_unknown_type, type), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void handleSharedImage(Intent intent) {
        Uri imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        if (imageUri == null) {
            finish();
            return;
        }
        // Decoding and re-encoding large images takes a while
        Async.run(() -> Utils.ImageUriToB64String(this, imageUri), (imageString, error) -> {
            if (imageString == null) {
                Toast.makeText(this, R.string.error_open_image, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            new NetClient(this).SetClipOnServer(imageString, Utils.FORMAT_IMG);
        });
    }

    private void handleSharedText(Intent intent) {
        String shared_clip = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared_clip == null || shared_clip.isEmpty()) {
            finish();
            return;
        }
        new NetClient(this).SetClipOnServer(shared_clip, Utils.FORMAT_TXT);
    }

    @Override
    public void onRequestStarted() {
    }

    @Override
    public void onRequestFinished() {
        finish();
    }
}
