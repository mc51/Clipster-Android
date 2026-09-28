package com.data_dive.com.clipster;

import java.util.List;

/**
 * Holds the clips shown by ListClipsActivity. They can be too large for an Intent extra,
 * so they are passed in memory and are lost when Android kills the process.
 */
public final class Clips {

    private static List<Clip> clips;

    private Clips() {}

    public static synchronized void set(List<Clip> newClips) {
        clips = newClips;
    }

    /** Null if no clips were loaded in this process */
    public static synchronized List<Clip> get() {
        return clips;
    }
}
