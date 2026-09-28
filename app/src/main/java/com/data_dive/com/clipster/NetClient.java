package com.data_dive.com.clipster;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Sends requests to the Clipster server. Network, encryption and decryption run in the background,
 * results are handled on the main thread. If the calling context implements {@link Listener}
 * it is notified when a request starts and finishes, e.g. to show a progress indicator.
 */
public class NetClient {

    public interface Listener {
        void onRequestStarted();

        void onRequestFinished();
    }

    private enum RequestType {
        LOGIN,
        REGISTER,
        GET_LAST_CLIP,
        GET_ALL_CLIPS,
        SET_CLIP
    }

    /** Everything needed to send a request, so it can be repeated after trusting a certificate */
    private static final class ApiRequest {
        final RequestType type;
        final String path;
        final Async.Work<String> payload;
        final String preview;

        ApiRequest(RequestType type, String path, Async.Work<String> payload, String preview) {
            this.type = type;
            this.path = path;
            this.payload = payload;
            this.preview = preview;
        }
    }

    private static final class Result {
        int code;
        String body = "";
        List<Clip> clips = Collections.emptyList();
        // Prepared in the background for GET_LAST_CLIP, as images need to be decoded and stored
        ClipData clipData;
        String clipPreview;
        // Set when the server certificate isn't trusted, the user may choose to trust it
        X509Certificate untrustedCertificate;
        boolean localNetworkBlocked;

        boolean isOk() {
            return code == 200 || code == 201;
        }
    }

    private static final String TAG = "NetClient";
    private static final String PATH_REGISTER = "/register/";
    private static final String PATH_VERIFY = "/verify-user/";
    private static final String PATH_CLIP = "/copy-paste/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Shared, so all clients reuse one connection pool and thread pool
    private static final OkHttpClient BASE_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Context context;
    private final Context appContext;
    private final Credentials credentials;
    private final String deviceName;
    private final PinningTrustManager trustManager;
    private final OkHttpClient client;

    public NetClient(Context context) {
        // We already have saved working credentials
        this(context, Utils.getCreds(context));
    }

    public NetClient(Context context, Credentials credentials) {
        this.context = context;
        this.appContext = context.getApplicationContext();
        this.credentials = credentials;
        this.deviceName = getDeviceName(context);
        try {
            trustManager = new PinningTrustManager(credentials.pinnedCertificate);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[] {trustManager}, null);
            client = BASE_CLIENT
                    .newBuilder()
                    .sslSocketFactory(ssl.getSocketFactory(), trustManager)
                    .hostnameVerifier(trustManager.hostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()))
                    .build();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not set up TLS", e);
        }
    }

    public void login() {
        execute(new ApiRequest(RequestType.LOGIN, PATH_VERIFY, null, null));
    }

    public void register() {
        execute(new ApiRequest(
                RequestType.REGISTER,
                PATH_REGISTER,
                () -> new JSONObject()
                        .put("username", credentials.user)
                        .put("password", credentials.loginHash)
                        .toString(),
                null));
    }

    public void getLastClip() {
        execute(new ApiRequest(RequestType.GET_LAST_CLIP, PATH_CLIP, null, null));
    }

    public void getAllClips() {
        execute(new ApiRequest(RequestType.GET_ALL_CLIPS, PATH_CLIP, null, null));
    }

    public void shareClip(String text, String format) {
        execute(new ApiRequest(
                RequestType.SET_CLIP,
                PATH_CLIP,
                () -> new JSONObject()
                        .put("text", Crypto.encrypt(credentials.encryptionKey, text))
                        .put("device", deviceName)
                        .put("format", format)
                        .toString(),
                Utils.clipPreview(appContext, text, format)));
    }

    private interface SettingLookup {
        String get();
    }

    private static String getDeviceName(Context context) {
        /*
         * Try to get the user defined device name
         * Unfortunately there is no definite way: we try the most common.
         * bluetooth_name throws a SecurityException for apps targeting Android 12L+, so each
         * lookup is tried on its own.
         */
        ContentResolver cr = context.getContentResolver();
        SettingLookup[] lookups = {
            () -> Settings.Global.getString(cr, Settings.Global.DEVICE_NAME),
            () -> Settings.Secure.getString(cr, "bluetooth_name"),
            () -> Settings.System.getString(cr, "bluetooth_name"),
        };
        for (SettingLookup lookup : lookups) {
            try {
                String name = lookup.get();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            } catch (RuntimeException e) {
                Log.d(TAG, "Device name lookup failed: " + e.getClass().getSimpleName());
            }
        }
        return "android";
    }

    private void execute(ApiRequest request) {
        notifyStarted();
        Async.run(() -> perform(request), (result, error) -> {
            boolean waitForUser = false;
            try {
                if (error != null) {
                    handleError(request, error);
                } else {
                    waitForUser = handleResult(request, result);
                }
            } finally {
                if (!waitForUser) {
                    notifyFinished();
                }
            }
        });
    }

    private Result perform(ApiRequest request) throws Exception {
        String payload = request.payload != null ? request.payload.run() : null;
        Request.Builder builder =
                new Request.Builder().url(credentials.server + request.path).header("Accept", "application/json");
        if (request.type != RequestType.REGISTER) {
            builder.header("Authorization", "Basic " + credentials.authToken);
        }
        if (payload != null) {
            builder.post(RequestBody.create(payload, JSON));
        }

        Log.d(TAG, request.type + " " + credentials.server + request.path);
        Result result = new Result();
        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            result.code = response.code();
            result.body = response.body().string();
        } catch (SSLException e) {
            result.untrustedCertificate = trustManager.rejectedCertificate();
            if (result.untrustedCertificate == null) {
                throw e;
            }
            return result;
        } catch (IOException e) {
            if (LocalNetwork.isPermissionMissing(appContext) && LocalNetwork.isLocalServer(credentials.server)) {
                result.localNetworkBlocked = true;
                return result;
            }
            throw e;
        }
        Log.d(TAG, "Response code: " + result.code);

        if (result.isOk() && request.type != RequestType.SET_CLIP) {
            result.clips = Clip.listFromJson(
                    result.body, credentials.encryptionKey, appContext.getString(R.string.error_decrypt_clip));
            if (request.type == RequestType.GET_LAST_CLIP && !result.clips.isEmpty()) {
                Clip last = result.clips.get(result.clips.size() - 1);
                result.clipData = Utils.createClipData(appContext, last);
                result.clipPreview = Utils.clipPreview(appContext, last.text, last.format);
            }
        }
        return result;
    }

    /** Returns true if the user is asked something, the request then finishes when the dialog is closed */
    private boolean handleResult(ApiRequest request, Result result) {
        if (result.untrustedCertificate != null) {
            return askToTrustCertificate(request, result.untrustedCertificate);
        }
        if (result.localNetworkBlocked) {
            showFailure(request.type, appContext.getString(R.string.error_local_network_permission));
            return false;
        }
        if (!result.isOk()) {
            showHttpError(request.type, result.code, parseErrorDetail(result.body));
            return false;
        }
        switch (request.type) {
            case LOGIN:
            case REGISTER:
                Utils.saveCreds(appContext, credentials);
                toast(appContext.getString(
                        request.type == RequestType.LOGIN
                                ? R.string.msg_login_successful
                                : R.string.msg_register_successful));
                startActivity(new Intent(context, ReadyActivity.class), true);
                break;
            case GET_LAST_CLIP:
                if (result.clips.isEmpty()) {
                    toast(appContext.getString(R.string.msg_no_clip_on_server));
                } else {
                    Utils.setClipboard(appContext, result.clipData, result.clipPreview);
                }
                break;
            case GET_ALL_CLIPS:
                Clips.set(result.clips);
                startActivity(new Intent(context, ListClipsActivity.class), false);
                break;
            case SET_CLIP:
                toast(appContext.getString(R.string.msg_clip_shared, request.preview));
                break;
        }
        return false;
    }

    private boolean askToTrustCertificate(ApiRequest request, X509Certificate certificate) {
        if (!credentials.allowSelfSigned) {
            showFailure(request.type, appContext.getString(R.string.error_certificate_untrusted));
            return false;
        }
        if (!(context instanceof Activity)
                || ((Activity) context).isFinishing()
                || ((Activity) context).isDestroyed()) {
            showFailure(request.type, appContext.getString(R.string.error_certificate_declined));
            return false;
        }
        boolean changed = credentials.pinnedCertificate != null;
        String host = URI.create(credentials.server).getHost();
        String message = context.getString(
                changed ? R.string.certificate_changed_message : R.string.trust_certificate_message,
                host,
                PinningTrustManager.fingerprint(certificate));
        boolean[] trusted = {false};
        new MaterialAlertDialogBuilder(context)
                .setTitle(changed ? R.string.certificate_changed_title : R.string.trust_certificate_title)
                .setMessage(message)
                .setPositiveButton(R.string.trust_certificate, (dialog, which) -> {
                    trusted[0] = true;
                    Credentials pinned = credentials.withPinnedCertificate(certificate);
                    if (request.type != RequestType.LOGIN && request.type != RequestType.REGISTER) {
                        // Login and register save the credentials once they succeed
                        Utils.saveCreds(appContext, pinned);
                    }
                    // Started before this request finishes, so listeners don't see an idle state in between
                    new NetClient(context, pinned).execute(request);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(dialog -> {
                    if (!trusted[0]) {
                        showFailure(request.type, appContext.getString(R.string.error_certificate_declined));
                    }
                    notifyFinished();
                })
                .show();
        return true;
    }

    private void handleError(ApiRequest request, Exception error) {
        Log.e(TAG, request.type + " failed: " + error);
        String reason;
        if (error instanceof SSLPeerUnverifiedException) {
            reason = appContext.getString(R.string.error_certificate_hostname);
        } else if (error instanceof IOException) {
            reason = appContext.getString(R.string.error_connection, credentials.server);
        } else if (error instanceof JSONException) {
            reason = appContext.getString(R.string.error_invalid_response);
        } else {
            reason = appContext.getString(R.string.error_unexpected);
        }
        showFailure(request.type, reason);
    }

    private void showHttpError(RequestType type, int code, String detail) {
        String reason;
        if (code == 401 || code == 403) {
            reason = appContext.getString(R.string.error_unauthorized);
        } else if (detail != null && !detail.isEmpty()) {
            reason = detail;
        } else {
            reason = appContext.getString(R.string.error_http_code, code);
        }
        showFailure(type, reason);
    }

    private void showFailure(RequestType type, String reason) {
        toast(appContext.getString(R.string.error_request_failed, actionName(type), reason));
    }

    private String actionName(RequestType type) {
        switch (type) {
            case LOGIN:
                return appContext.getString(R.string.login);
            case REGISTER:
                return appContext.getString(R.string.register);
            case GET_LAST_CLIP:
                return appContext.getString(R.string.get_last_clip);
            case GET_ALL_CLIPS:
                return appContext.getString(R.string.get_all_clips);
            default:
                return appContext.getString(R.string.set_clip);
        }
    }

    private static String parseErrorDetail(String body) {
        // Server errors look like {"detail": "..."}, anything else (e.g. an HTML page) is not shown
        try {
            return new JSONObject(body).getString("detail");
        } catch (JSONException e) {
            return null;
        }
    }

    private void startActivity(Intent intent, boolean finishCurrent) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            activity.startActivity(intent);
            if (finishCurrent) {
                activity.finish();
            }
        }
    }

    private void toast(String msg) {
        Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show();
    }

    private void notifyStarted() {
        if (context instanceof Listener) {
            ((Listener) context).onRequestStarted();
        }
    }

    private void notifyFinished() {
        if (context instanceof Listener) {
            ((Listener) context).onRequestFinished();
        }
    }
}
