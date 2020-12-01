package com.data_dive.com.clipster;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A Debounced OnClickListener
 * Rejects clicks that are too close together in time.
 * This class is safe to use as an OnClickListener for multiple views, and will debounce each one separately.
 * Source: https://stackoverflow.com/a/16534470/5251061
 */

public abstract class DebouncedOnClickListener implements View.OnClickListener {

    private final long minimumIntervalMillis;
    private Map<View, Long> lastClickMap;
    private Context context;

    public abstract void onDebouncedClick(View v);

    public DebouncedOnClickListener(long minimumIntervalMillis, Context mContext) {
        this.context = mContext;
        this.minimumIntervalMillis = minimumIntervalMillis;
        this.lastClickMap = new WeakHashMap<>();
    }

    @Override
    public void onClick(View clickedView) {
        Long previousClickTimestamp = lastClickMap.get(clickedView);
        long currentTimestamp = SystemClock.uptimeMillis();
        lastClickMap.put(clickedView, currentTimestamp);
        if(previousClickTimestamp == null
                || Math.abs(currentTimestamp - previousClickTimestamp) > minimumIntervalMillis) {
            onDebouncedClick(clickedView);
        } else {
            Log.w("DebouncedClick", "Clicking too fast there!");
            Toast.makeText(context, R.string.msg_warn_clicking_too_fast,
                    Toast.LENGTH_LONG).show();
        }
    }
}