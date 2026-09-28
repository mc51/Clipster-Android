package com.data_dive.com.clipster;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Shows decrypted text and image clips. Rows are recycled and images are decoded
 * in the background as downsampled thumbnails, so scrolling stays smooth.
 */
public class ClipListAdapter extends BaseAdapter {

    private static final int TYPE_TXT = 0;
    private static final int TYPE_IMG = 1;

    private final LayoutInflater inflater;
    private final JSONArray clips;
    private final int thumbnailSize;
    private final Set<Integer> loading = new HashSet<>();
    private final LruCache<Integer, Bitmap> thumbnails =
            new LruCache<Integer, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(Integer key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    public ClipListAdapter(Context context, JSONArray clips) {
        this.inflater = LayoutInflater.from(context);
        this.clips = clips;
        this.thumbnailSize = context.getResources().getDisplayMetrics().widthPixels;
    }

    @Override
    public int getCount() {
        return clips.length();
    }

    @Override
    public JSONObject getItem(int position) {
        return clips.optJSONObject(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return Utils.FORMAT_IMG.equals(format(position)) ? TYPE_IMG : TYPE_TXT;
    }

    public String text(int position) {
        JSONObject clip = getItem(position);
        return clip != null ? clip.optString("text_decrypted", "") : "";
    }

    public String format(int position) {
        JSONObject clip = getItem(position);
        return clip != null ? clip.optString("format", Utils.FORMAT_TXT) : Utils.FORMAT_TXT;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        if (getItemViewType(position) == TYPE_TXT) {
            if (view == null) {
                view = inflater.inflate(R.layout.list_single_txt, parent, false);
            }
            ((TextView) view).setText(text(position));
            return view;
        }

        if (view == null) {
            view = inflater.inflate(R.layout.list_single_img, parent, false);
        }
        ImageView imageView = view.findViewById(R.id.img);
        imageView.setTag(position);
        Bitmap cached = thumbnails.get(position);
        imageView.setImageBitmap(cached);
        if (cached == null && loading.add(position)) {
            String b64 = text(position);
            Async.run(() -> Utils.B64StringToThumbnail(b64, thumbnailSize), (bitmap, error) -> {
                loading.remove(position);
                if (bitmap == null) {
                    return;
                }
                thumbnails.put(position, bitmap);
                // The row may have been recycled for another clip in the meantime
                if (Integer.valueOf(position).equals(imageView.getTag())) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        }
        return view;
    }
}
