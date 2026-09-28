package com.data_dive.com.clipster;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows decrypted text and image clips with their device and time. Rows are recycled and images
 * are decoded in the background as downsampled thumbnails, so scrolling stays smooth.
 */
public class ClipListAdapter extends BaseAdapter {

    private static final int TYPE_TXT = 0;
    private static final int TYPE_IMG = 1;

    private static final class ViewHolder {
        final TextView text;
        final ImageView image;
        final TextView meta;

        ViewHolder(View row) {
            text = row.findViewById(R.id.txt);
            image = row.findViewById(R.id.img);
            meta = row.findViewById(R.id.meta);
        }
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<Clip> clips;
    private final int thumbnailSize;
    private final Set<Integer> loading = new HashSet<>();
    private final LruCache<Integer, Bitmap> thumbnails =
            new LruCache<Integer, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(Integer key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    public ClipListAdapter(Context context, List<Clip> clips) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.clips = clips;
        this.thumbnailSize = context.getResources().getDisplayMetrics().widthPixels;
    }

    @Override
    public int getCount() {
        return clips.size();
    }

    @Override
    public Clip getItem(int position) {
        return clips.get(position);
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
        return getItem(position).isImage() ? TYPE_IMG : TYPE_TXT;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        Clip clip = getItem(position);
        if (row == null) {
            row = inflater.inflate(clip.isImage() ? R.layout.list_single_img : R.layout.list_single_txt, parent, false);
            row.setTag(new ViewHolder(row));
        }
        ViewHolder holder = (ViewHolder) row.getTag();
        String meta = metaText(clip);
        holder.meta.setText(meta);
        holder.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);

        if (clip.isImage()) {
            bindImage(holder.image, position, clip);
        } else {
            holder.text.setText(clip.text);
        }
        return row;
    }

    private String metaText(Clip clip) {
        String time = "";
        if (clip.createdAt != null) {
            long created = clip.createdAt.toEpochMilli();
            long now = System.currentTimeMillis();
            time = now - created < DateUtils.MINUTE_IN_MILLIS
                    ? context.getString(R.string.clip_just_now)
                    : DateUtils.getRelativeTimeSpanString(
                                    created, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                            .toString();
        }
        if (clip.device.isEmpty() || time.isEmpty()) {
            return clip.device + time;
        }
        return context.getString(R.string.clip_meta, clip.device, time);
    }

    private void bindImage(ImageView imageView, int position, Clip clip) {
        imageView.setTag(position);
        Bitmap cached = thumbnails.get(position);
        imageView.setImageBitmap(cached);
        if (cached != null || !loading.add(position)) {
            return;
        }
        Async.run(() -> Utils.b64ToThumbnail(clip.text, thumbnailSize), (bitmap, error) -> {
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
}
