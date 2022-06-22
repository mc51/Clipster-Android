package com.data_dive.com.clipster;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

public class CustomList extends ArrayAdapter<String>{

    public final String logtag = this.getClass().getSimpleName();
    private final Activity context;
    private final JSONArray clips;
    private final String[] entry_placeholders;

    public CustomList(Activity context, JSONArray clips, String[] entry_placeholders) {

        super(context, R.layout.list_single, entry_placeholders);
        Log.d(logtag, "CustomList constructor");
        this.context = context;
        this.entry_placeholders = entry_placeholders;
        this.clips = clips;
    }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            Log.d(logtag, "getView");
            String text_decrypted = "";
            String format = "txt";
            Bitmap image;

            LayoutInflater inflater = context.getLayoutInflater();
            View rowView = inflater.inflate(R.layout.list_single, null, true);

            try {
                text_decrypted = clips.getJSONObject(position).getString("text_decrypted");
                format = clips.getJSONObject(position).getString("format");
            } catch(JSONException e) {
                Log.e(logtag, "getView:" + e);
            }

            if (format.equals("img")) {
                image = Utils.B64StringToImage(text_decrypted);
                ImageView imageView = (ImageView) rowView.findViewById(R.id.img);
                imageView.setImageBitmap(image);
            } else {
                TextView txtTitle = (TextView) rowView.findViewById(R.id.txt);
                txtTitle.setText(text_decrypted);
            }

            return rowView;
        }
    }