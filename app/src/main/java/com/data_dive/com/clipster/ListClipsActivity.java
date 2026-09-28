package com.data_dive.com.clipster;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;

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

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;


public class ListClipsActivity extends AppCompatActivity {

    private static final String logtag = "ListClipsActivity";
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST = 123;
    // Image waiting for the storage permission (only needed up to Android 9)
    private String pending_image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        JSONArray clips = Clips.getInstance().getData();
        if (clips == null) {
            // Clips only live in memory, e.g. lost when Android killed the app in the background
            finish();
            return;
        }

        setContentView(R.layout.activity_list_clips);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView list = findViewById(R.id.ListOfClipsView);
        list.setEmptyView(findViewById(R.id.empty));
        ClipListAdapter adapter = new ClipListAdapter(this, clips);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                openPopupMenu(view, adapter.text(position), adapter.format(position)));
    }

    private void openPopupMenu(View view, String clip_text, String clip_format) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        boolean isImage = Utils.FORMAT_IMG.equals(clip_format);
        popupMenu.getMenuInflater().inflate(isImage ? R.menu.popup_menu_image : R.menu.popup_menu_text,
                popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.copy_to_clipboard) {
                Utils.setClipboard(this, ClipData.newPlainText("Clipster", clip_text),
                        Utils.clipPreview(this, clip_text, clip_format));
            } else if (id == R.id.share) {
                openShareMenu(clip_text, clip_format);
            } else if (id == R.id.save_to_file) {
                getPermissionAndSaveToGallery(clip_text);
            }
            return true;
        });
        popupMenu.show();
    }

    private void getPermissionAndSaveToGallery(String image_b64) {
        // MediaStore needs no storage permission from Android 10 on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            saveToGallery(image_b64);
        } else {
            pending_image = image_b64;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
        }
    }

    private void saveToGallery(String image_b64) {
        Async.run(() -> Utils.SaveBitmapToGallery(this, Utils.B64StringToImage(image_b64)), (saved, error) -> {
            boolean ok = error == null && Boolean.TRUE.equals(saved);
            if (!ok) {
                Log.e(logtag, "Could not save image: " + error);
            }
            Toast.makeText(this, ok ? R.string.msg_image_saved : R.string.error_save_image,
                    Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST && pending_image != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveToGallery(pending_image);
            } else {
                Toast.makeText(this, R.string.error_save_image, Toast.LENGTH_LONG).show();
            }
            pending_image = null;
        }
    }

    private void openShareMenu(String clip_text, String clip_format) {
        if (!Utils.FORMAT_IMG.equals(clip_format)) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, clip_text);
            startChooser(intent);
            return;
        }
        Async.run(() -> Utils.BitmapToTempFileAsUri(this, Utils.B64StringToImage(clip_text)), (imageUri, error) -> {
            if (imageUri == null) {
                Toast.makeText(this, R.string.error_open_image, Toast.LENGTH_LONG).show();
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
            Log.e(logtag, "No app to share with: " + e);
        }
    }
}
