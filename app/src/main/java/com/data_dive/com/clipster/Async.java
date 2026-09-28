package com.data_dive.com.clipster;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Run slow work (network, key derivation, crypto, image decoding) off the main thread
 */
final class Async {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Work<T> {
        T run() throws Exception;
    }

    interface Callback<T> {
        /** Called on the main thread. Exactly one of result or error is non-null (result may be null on success). */
        void done(T result, Exception error);
    }

    private Async() {}

    static <T> void run(Work<T> work, Callback<T> callback) {
        EXECUTOR.execute(() -> {
            T result = null;
            Exception error = null;
            try {
                result = work.run();
            } catch (Exception e) {
                error = e;
            } catch (OutOfMemoryError e) {
                // Large images can exhaust the heap, report it instead of leaving the UI waiting
                error = new RuntimeException(e);
            }
            final T r = result;
            final Exception err = error;
            MAIN.post(() -> callback.done(r, err));
        });
    }
}
