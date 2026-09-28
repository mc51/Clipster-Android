package com.data_dive.com.clipster;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListClipsActivity extends AppCompatActivity {

    private static final String TAG = "ListClipsActivity";
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST = 123;
    // Image waiting for the storage permission (only needed up to Android 9)
    private String pendingImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        List<Clip> loaded = Clips.get();
        if (loaded == null) {
            // Clips only live in memory, e.g. lost when Android killed the app in the background
            finish();
            return;
        }
        // The server sends the oldest clip first
        List<Clip> clips = new ArrayList<>(loaded);
        Collections.reverse(clips);

        setContentView(R.layout.activity_list_clips);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView list = findViewById(R.id.clip_list);
        list.setEmptyView(findViewById(R.id.empty));
        ClipListAdapter adapter = new ClipListAdapter(this, clips);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openPopupMenu(view, adapter.getItem(position)));
    }

    private void openPopupMenu(View view, Clip clip) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu
                .getMenuInflater()
                .inflate(clip.isImage() ? R.menu.popup_menu_image : R.menu.popup_menu_text, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.copy_to_clipboard) {
                Utils.setClipboard(
                        this,
                        ClipData.newPlainText("Clipster", clip.text),
                        Utils.clipPreview(this, clip.text, clip.format));
            } else if (id == R.id.share) {
                openShareMenu(clip);
            } else if (id == R.id.save_to_file) {
                saveToGalleryWithPermission(clip.text);
            }
            return true;
        });
        popupMenu.show();
    }

    private void saveToGalleryWithPermission(String imageB64) {
        // MediaStore needs no storage permission from Android 10 on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            saveToGallery(imageB64);
        } else {
            pendingImage = imageB64;
            ActivityCompat.requestPermissions(
                    this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
        }
    }

    private void saveToGallery(String imageB64) {
        Async.run(() -> Utils.saveBitmapToGallery(this, Utils.b64ToBitmap(imageB64)), (saved, error) -> {
            boolean ok = error == null && Boolean.TRUE.equals(saved);
            if (!ok) {
                Log.e(TAG, "Could not save image: " + error);
            }
            Toast.makeText(this, ok ? R.string.msg_image_saved : R.string.error_save_image, Toast.LENGTH_LONG)
                    .show();
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST && pendingImage != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveToGallery(pendingImage);
            } else {
                Toast.makeText(this, R.string.error_save_image, Toast.LENGTH_LONG)
                        .show();
            }
            pendingImage = null;
        }
    }

    private void openShareMenu(Clip clip) {
        if (!clip.isImage()) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, clip.text);
            startChooser(intent);
            return;
        }
        Async.run(() -> Utils.bitmapToTempFileUri(this, Utils.b64ToBitmap(clip.text)), (imageUri, error) -> {
            if (imageUri == null) {
                Toast.makeText(this, R.string.error_open_image, Toast.LENGTH_LONG)
                        .show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, imageUri);
            intent.setClipData(ClipData.newRawUri(null, imageUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startChooser(intent);
        });
    }

    private void startChooser(Intent intent) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)));
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "No app to share with: " + e);
        }
    }
}
