package com.data_dive.com.clipster;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;


public class ListClipsActivity extends AppCompatActivity {

    public final String logtag = this.getClass().getSimpleName();
    private static String clip_text;
    private static String clip_format;
    private static final int BUTTON_DELAY = 2000;
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST = 123;
    Bitmap image;
    TextView copy_to_clipboard, back;
    ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_clips);

        // Buttons
        // copy_to_clipboard = findViewById(R.id.CopyToClipboard);
        back = findViewById(R.id.Back);

        // Initialize ListView for showing all Clips
        list = findViewById(R.id.ListOfClipsView);
        JSONArray clips = null;

        if (getIntent().getExtras() != null) {
            String clips_string = getIntent().getStringExtra("clips");
            try {
                clips = new JSONArray(clips_string);
            } catch(JSONException e) {
                Log.e(logtag, "Could not convert to JSON array: " + clips_string + e);
            }

            if(clips != null && clips.length() > 0) {
                // hackiest hack of all shitty hacks
                int num_entries = clips.length();
                String[] entry_placeholders = new String[num_entries];
                CustomList cust_adapter = new CustomList(ListClipsActivity.this, clips, entry_placeholders);
                list.setAdapter(cust_adapter);
            } else {
                // No Clips yet
                ArrayAdapter adapter = new ArrayAdapter<String>(this, R.layout.list_clips_items,
                        R.id.clip_items_textview, new String[]{getString(R.string.empty_clip_list)});
                list.setAdapter(adapter);
            }
        }

//        copy_to_clipboard.setOnClickListener(btnListener);
//        copy_to_clipboard.setTag("copy_to_clipboard");
        back.setOnClickListener(btnListener);
        back.setTag("back");

        JSONArray finalClips = clips;
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                try {
                    clip_text = finalClips.getJSONObject(position).getString("text_decrypted");
                    clip_format = finalClips.getJSONObject(position).getString("format");
                } catch (JSONException e) {
                    Log.e(logtag, "Error: " + e);
                }
                Log.d(logtag, "Got Item: " + clip_text);
                openPopupMenu(view, clip_text, clip_format);
            }

        });
    }

    private void openPopupMenu(View view, String clip_text, String clip_format) {
        // Initializing the popup menu and giving the reference as current context
        PopupMenu popupMenu = new PopupMenu(this, view);

        if (clip_format.equals("img")) {
            popupMenu.getMenuInflater().inflate(R.menu.popup_menu_image, popupMenu.getMenu());
        } else {
            popupMenu.getMenuInflater().inflate(R.menu.popup_menu_text, popupMenu.getMenu());
        }

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                    Log.d(logtag, "Got Menu Item Text: " + menuItem.getTitle());
                    Log.d(logtag, "Got Menu Item ID: " + menuItem.getItemId());

                    if (menuItem.getItemId() == R.id.copy_to_clipboard) {
                        Utils.setClipboard(view.getContext(), clip_text, clip_format);
                    } else if (menuItem.getItemId() == R.id.share) {
                        openShareMenu(clip_text, clip_format);
                    } else if (menuItem.getItemId() == R.id.save_to_file) {
                        image = Utils.B64StringToImage(clip_text);
                        getPermissionAndSaveBitmapToGallery(image);
                    }

                return true;
            }
        });
        popupMenu.show();
    }

    private void getPermissionAndSaveBitmapToGallery(Bitmap image) {
        int check = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (check == PackageManager.PERMISSION_GRANTED) {
            Log.d(logtag, "Has permissions for WRITE_EXTERNAL_STORAGE");
            Utils.SaveBitmapToGallery(this, image, "Clipster image", "Image shared via Clipster");
        } else {
            Log.d(logtag, "No Permission for WRITE_EXTERNAL_STORAGE. Asking for it");
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case WRITE_EXTERNAL_STORAGE_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(logtag, "Permission granted!");
                    Utils.SaveBitmapToGallery(this, image, "Clipster image", "Image shared via Clipster");
                } else {
                    Log.d(logtag, "Permission not granted");
                }
            }
        }
    }


    private void openShareMenu(String clip_text, String clip_format) {
        // openShareMenu and deal with txt vs image sharing
        final Intent intent = new Intent(Intent.ACTION_SEND);
        if (clip_format.equals("txt")) {
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, clip_text);
        } else if (clip_format.equals("img")) {
            intent.setType("image/png");
            Bitmap image = Utils.B64StringToImage(clip_text);
            Uri imageUri = Utils.BitmapToTempFileAsUri(this, image);
            intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        }
        try {
            startActivity(Intent.createChooser(intent, "Select an action"));
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(logtag, "Error: " + e);
        }
    }

    private View.OnClickListener btnListener = new DebouncedOnClickListener(BUTTON_DELAY, this) {
        public void onDebouncedClick(View v) {
            // Delay excessive clicks
            String action_tag = v.getTag().toString();
            Log.d(logtag, "Clicked Button: " + action_tag);
            if(action_tag.equals("back")) {
                finish();
            } else if (action_tag.equals("copy_to_clipboard")) {
                // Utils.setClipboard(v.getContext(), clip_text);
            }
        }
    };

}