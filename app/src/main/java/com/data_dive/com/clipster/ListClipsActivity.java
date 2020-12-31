package com.data_dive.com.clipster;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ListClipsActivity extends AppCompatActivity {

    public final String logtag = this.getClass().getSimpleName();
    private static String[] clips;
    private static String selected_clip;
    private static final int BUTTON_DELAY = 2000;
    TextView copy_to_clipboard, back;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_clips);

        ArrayAdapter adapter = null;
        // Buttons
        copy_to_clipboard = findViewById(R.id.CopyToClipboard);
        back = findViewById(R.id.Back);

        if (getIntent().getExtras() != null) {
            clips = getIntent().getStringArrayExtra("clips");
            if(clips.length > 0) {
                adapter = new ArrayAdapter<String>(this, R.layout.list_clips_items,
                        R.id.clip_items_textview, clips);
            } else {
                // No Clips yet
                adapter = new ArrayAdapter<String>(this, R.layout.list_clips_items,
                        R.id.clip_items_textview, new String[]{getString(R.string.empty_clip_list)});
            }
        }

        copy_to_clipboard.setOnClickListener(btnListener);
        copy_to_clipboard.setTag("copy_to_clipboard");
        back.setOnClickListener(btnListener);
        back.setTag("back");

        // Initialize ListView for showing all Clips
        final ListView listview = findViewById(R.id.ListOfClipsView);
        listview.setAdapter(adapter);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                selected_clip = (String) parent.getItemAtPosition(position);
                Log.d(logtag, "Got Item: " + selected_clip);
            }

        });
    }

    private View.OnClickListener btnListener = new DebouncedOnClickListener(BUTTON_DELAY, this) {
        public void onDebouncedClick(View v) {
            // Delay excessive clicks
            String action_tag = v.getTag().toString();
            Log.d(logtag, "Clicked Button: " + action_tag);
            if(action_tag.equals("back")) {
                finish();
            } else if (action_tag.equals("copy_to_clipboard")) {
                Utils.setClipboard(v.getContext(), selected_clip);
            }
        }
    };

}