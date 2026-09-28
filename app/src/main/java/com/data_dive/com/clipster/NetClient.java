package com.data_dive.com.clipster;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    private enum RequestType { LOGIN, REGISTER, GET_LAST_CLIP, GET_ALL_CLIPS, SET_CLIP }

    private static final String logtag = "NetClient";
    private static final int TIMEOUT_CONN = 8000;
    private static final int TIMEOUT_READ = 30000;
    private static final String URI_REGISTER = "/register/";
    private static final String URI_VERIFY = "/verify-user/";
    private static final String URI_CLIP = "/copy-paste/";
    // Captured before disableSSLCertChecks() can replace it, so it can be restored later
    private static final HostnameVerifier DEFAULT_HOSTNAME_VERIFIER = HttpsURLConnection.getDefaultHostnameVerifier();

    private final Context context;
    private final Context appContext;
    private final Credentials credentials;
    private final String device_name;

    protected NetClient(Context context) {
        // We already have saved working credentials
        this(context, Utils.getCreds(context));
    }

    protected NetClient(Context context, Credentials creds) {
        this.context = context;
        this.appContext = context.getApplicationContext();
        this.credentials = creds;
        this.device_name = getDeviceName(context);
        if (credentials.ignore_cert) {
            disableSSLCertChecks();
        } else {
            enableSSLCertChecks();
        }
    }

    private static final class Response {
        int code;
        String body = "";
        JSONArray clips = new JSONArray();
        // Prepared in the background for GET_LAST_CLIP, as images need to be decoded and stored
        ClipData clipData;
        String clipPreview;

        boolean isOk() {
            return code == 200 || code == 201;
        }
    }

    private interface SettingLookup {
        String get();
    }

    private String getDeviceName(Context context) {
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
                Log.d(logtag, "Device name lookup failed: " + e.getClass().getSimpleName());
            }
        }
        return "android";
    }

    protected void Login() {
        execute(RequestType.LOGIN, URI_VERIFY, null);
    }

    protected void Register() {
        execute(RequestType.REGISTER, URI_REGISTER, () -> {
            JSONObject payload = new JSONObject();
            payload.put("username", credentials.user);
            payload.put("password", credentials.login_pw_hash);
            return payload.toString();
        });
    }

    protected void GetLastClipFromServer() {
        execute(RequestType.GET_LAST_CLIP, URI_CLIP, null);
    }

    protected void GetAllClipsFromServer() {
        execute(RequestType.GET_ALL_CLIPS, URI_CLIP, null);
    }

    protected void SetClipOnServer(String clip, String format) {
        final String clip_format = format != null ? format : Utils.FORMAT_TXT;
        execute(RequestType.SET_CLIP, URI_CLIP, () -> {
            JSONObject payload = new JSONObject();
            payload.put("text", Crypto.encrypt(credentials.encryption_key, clip));
            payload.put("device", device_name);
            payload.put("format", clip_format);
            return payload.toString();
        }, Utils.clipPreview(appContext, clip, clip_format));
    }

    private void execute(RequestType type, String path, Async.Work<String> payload) {
        execute(type, path, payload, null);
    }

    private void execute(RequestType type, String path, Async.Work<String> payload, String preview) {
        final String url = credentials.server + path;
        notifyStarted();
        Async.run(() -> {
            Response response = send(type, url, payload != null ? payload.run() : null);
            if (response.isOk() && type != RequestType.SET_CLIP) {
                response.clips = parseClips(response.body);
                if (type == RequestType.GET_LAST_CLIP && response.clips.length() > 0) {
                    JSONObject last = response.clips.getJSONObject(response.clips.length() - 1);
                    String text = last.getString("text_decrypted");
                    String format = last.optString("format", Utils.FORMAT_TXT);
                    response.clipData = Utils.createClipData(appContext, text, format);
                    response.clipPreview = Utils.clipPreview(appContext, text, format);
                }
            }
            return response;
        }, (response, error) -> {
            try {
                if (error != null) {
                    handleError(type, error);
                } else {
                    handleResponse(type, response, preview);
                }
            } finally {
                notifyFinished();
            }
        });
    }

    private Response send(RequestType type, String request_uri, String payload) throws IOException {
        Log.d(logtag, type + " " + request_uri);
        HttpsURLConnection conn = (HttpsURLConnection) new URL(request_uri).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_CONN);
            conn.setReadTimeout(TIMEOUT_READ);
            conn.setRequestProperty("Content-type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (type != RequestType.REGISTER) {
                conn.setRequestProperty("Authorization", "Basic " + credentials.token_b64);
            }
            if (payload != null) {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                conn.setRequestMethod("GET");
            }

            Response response = new Response();
            response.code = conn.getResponseCode();
            Log.d(logtag, "Response code: " + response.code);
            InputStream stream = response.code < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (stream != null) {
                response.body = readAll(stream);
            }
            return response;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private JSONArray parseClips(String body) {
        // The server answers with an array of clips, or a single clip object
        JSONArray clips;
        try {
            clips = new JSONArray(body);
        } catch (JSONException e) {
            clips = new JSONArray();
            try {
                clips.put(new JSONObject(body));
            } catch (JSONException err) {
                Log.e(logtag, "Could not parse response as JSON array or object");
                return clips;
            }
        }
        return Utils.decryptClips(clips, credentials.encryption_key,
                appContext.getString(R.string.error_decrypt_clip));
    }

    private void handleResponse(RequestType type, Response response, String preview) {
        if (!response.isOk()) {
            showError(type, response.code, parseErrorDetail(response.body));
            return;
        }
        switch (type) {
            case LOGIN:
            case REGISTER:
                Utils.saveCreds(appContext, credentials);
                toast(appContext.getString(type == RequestType.LOGIN
                        ? R.string.msg_login_successful : R.string.msg_register_successful));
                startActivity(new Intent(context, ReadyActivity.class), true);
                break;
            case GET_LAST_CLIP:
                if (response.clipData == null) {
                    toast(appContext.getString(R.string.msg_no_clip_on_server));
                } else {
                    Utils.setClipboard(appContext, response.clipData, response.clipPreview);
                }
                break;
            case GET_ALL_CLIPS:
                Clips.getInstance().setData(response.clips);
                startActivity(new Intent(context, ListClipsActivity.class), false);
                break;
            case SET_CLIP:
                toast(appContext.getString(R.string.msg_clip_shared, preview));
                break;
        }
    }

    private void handleError(RequestType type, Exception error) {
        Log.e(logtag, type + " failed: " + error);
        String reason = error instanceof IOException
                ? appContext.getString(R.string.error_connection, credentials.server)
                : appContext.getString(R.string.error_unexpected);
        toast(appContext.getString(R.string.error_request_failed, actionName(type), reason));
    }

    private void showError(RequestType type, int code, String detail) {
        String reason;
        if (code == 401 || code == 403) {
            reason = appContext.getString(R.string.error_unauthorized);
        } else if (detail != null && !detail.isEmpty()) {
            reason = detail;
        } else {
            reason = appContext.getString(R.string.error_http_code, code);
        }
        toast(appContext.getString(R.string.error_request_failed, actionName(type), reason));
    }

    private String actionName(RequestType type) {
        switch (type) {
            case LOGIN: return appContext.getString(R.string.login);
            case REGISTER: return appContext.getString(R.string.register);
            case GET_LAST_CLIP: return appContext.getString(R.string.get_last_clip);
            case GET_ALL_CLIPS: return appContext.getString(R.string.get_all_clips);
            default: return appContext.getString(R.string.set_clip);
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

    public static void disableSSLCertChecks() {
        /* Ignore Self signed SSL Certificated - Trust all certs
         *  Don't check for hostname match either
         *  https://stackoverflow.com/questions/2893819/accept-servers-self-signed-ssl-certificate-in-java-client
         */
        Log.d(logtag, "Disabling SSL Cert Checks");
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    @SuppressLint("TrustAllX509TrustManager")
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    @SuppressLint("TrustAllX509TrustManager")
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (GeneralSecurityException e) {
            Log.e(logtag, e.toString());
        }
    }

    public static void enableSSLCertChecks() {
        Log.d(logtag, "Enabling SSL Cert Checks");
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, null, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(DEFAULT_HOSTNAME_VERIFIER);
        } catch (GeneralSecurityException e) {
            Log.e(logtag, "Error enabling SSL Cert: " + e);
        }
    }
}
