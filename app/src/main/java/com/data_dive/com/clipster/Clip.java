package com.data_dive.com.clipster;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.Serializable;

/**
 *  Define Clips object to pass around functions and activities
 */

public final class Clip implements Serializable {
    private static String logtag = "Clip";
    public final String user;
    public final String text;
    public final String format;
    public final String device;
    public final String created_at;
    public String text_decrypted;
    public Bitmap image;

    public Clip(String user, String text, String format, String device, String created_at) {

        this.user = user;
        this.text = text;
        this.format = format;
        this.device = device;
        this.created_at = created_at;
        this.text_decrypted = null;
        this.image = null;
        Log.d(logtag, "Called default constructor");
    }
}
