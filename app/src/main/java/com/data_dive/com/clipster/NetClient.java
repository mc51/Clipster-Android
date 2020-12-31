package com.data_dive.com.clipster;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Singleton Class for Dealing with Client requests
 * Async subclass for https requests
 * I guess this is really ugly: how would you do it properly?
 * TODO: Could clean up returns of request by using Object instead of Array
 */

public class NetClient {

    private final static String logtag = "NetClient";
    private static final int TIMEOUT_CONN = 8000;
    private final static String URI_REGISTER = "/register/";
    private final static String URI_VERIFY = "/verify-user/";
    private final static String URI_CLIP = "/copy-paste/";

    private static Context mContext;
    private static Credentials credentials;
    private static String SERVER_URI = "";
    private static String clip_clear = "";
    private static String device_name;

    protected NetClient(Context context) {
        // Default constructor - we already have saved working credentials
        Log.d(logtag, "Default constructor. Getting creds.");
        credentials = Utils.getCreds(context);
        SERVER_URI = credentials.server;
        device_name = getDeviceName(context);

        Log.d(logtag, "Default constructor.\nSERVER: " + SERVER_URI + "\nDevice Name: " + device_name);
        if(credentials.ignore_cert) {
            disableSSLCertChecks();
        } else {
            enableSSLCertChecks();
        }
        mContext = context;
    }

    protected NetClient(Context context, Credentials creds) {
        // Constructor before we have saved credentials
        credentials = creds;
        SERVER_URI = credentials.server;
        device_name = getDeviceName(context);

        if(credentials.ignore_cert) {
            disableSSLCertChecks();
        } else {
            enableSSLCertChecks();
        }
        mContext = context;
    }

    private String getDeviceName(Context context) {
        /**
         * Try to get the user defined device name
         * Unfortunately there is no definite way: we try the most common
         */
        String name;
        try {
            name = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
            if(name == null) {
                name = Settings.System.getString(context.getContentResolver(), "bluetooth_name");
            }
            if(name == null) {
                name = Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
            }
            if (name == null) {
                name = "android";
            }
        } catch(Exception e) {
            Log.e(logtag,"Couldn't get device name: " +  e.toString());
            name = "android";
        }
        return name;
    }

    protected void Login() {
        ClientRequest req = new ClientRequest(mContext);
        String request_uri = SERVER_URI + URI_VERIFY;
        Log.d(logtag," Login function. Token: " + credentials.token_b64);
        req.execute(request_uri, credentials.token_b64, "login", "");
    }

    protected void Register() {
        // Register new account on server
        ClientRequest req = new ClientRequest(mContext);
        String request_uri = SERVER_URI + URI_REGISTER;
        String payload = "";
        JSONObject jsonPayload = new JSONObject();
        try {
            jsonPayload.put("username", credentials.user);
            jsonPayload.put("password", credentials.login_pw_hash);
            payload = jsonPayload.toString();
            Log.d(logtag, "Parsed text to json: " + payload);
        }  catch(JSONException e) {
            Log.e(logtag, "Could not parse text to json: " + payload);
        }
        req.execute(request_uri, credentials.token_b64, "register", payload);
    }

    protected void GetLastClipFromServer() {
        ClientRequest req = new ClientRequest(mContext);
        String request_uri = SERVER_URI + URI_CLIP;
        req.execute(request_uri, credentials.token_b64, "get_last_clip",  "");
    }

    protected void GetAllClipsFromServer() {
        ClientRequest req = new ClientRequest(mContext);
        String request_uri = SERVER_URI + URI_CLIP;
        req.execute(request_uri, credentials.token_b64, "get_all_clips",  "");
    }

    protected void SetClipOnServer(String clip) {
        // Encrypt clip and create Payload for calling request
        ClientRequest req = new ClientRequest(mContext);
        String request_uri = SERVER_URI + URI_CLIP;
        String payload = "";
        JSONObject jsonPayload = new JSONObject();
        clip_clear = clip;
        String clip_encrypted = Utils.encryptText(mContext, clip);
        try {
            jsonPayload.put("text", clip_encrypted);
            jsonPayload.put("device", device_name);
            payload = jsonPayload.toString();
            Log.d(logtag, "Parsed text to json: " + payload);
        }  catch(JSONException e) {
            Log.e(logtag, "Could not parse clip text to json: " + clip);
        }
        req.execute(request_uri, credentials.token_b64, "set_clip", payload);
    }

    public static void disableSSLCertChecks() {
        /** Ignore Self signed SSL Certificated - Trust all certs
         *  Don't check for hostname match either
         *  https://stackoverflow.com/questions/2893819/accept-servers-self-signed-ssl-certificate-in-java-client
         */
        Log.d(logtag, "Disabling SSL Cert Checks");
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    @SuppressLint("TrustAllX509TrustManager")
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                    @SuppressLint("TrustAllX509TrustManager")
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };
        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @SuppressLint("BadHostnameVerifier")
                @Override
                public boolean verify(String arg0, SSLSession arg1) {
                    return true;
                }
            });
        } catch (GeneralSecurityException e) {
            Log.e(logtag, e.toString());
        }
    }

    public static void enableSSLCertChecks() {
        // Re-enable SSL Certification Checks
        Log.d(logtag, "Enabling SSL Cert Checks");
        // Install standard trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, null, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
        } catch (GeneralSecurityException e) {
            Log.e(logtag, "Error enabling SSL Cert: " + e.toString());
        }
    }

    // Send request to API on server
    public static class ClientRequest extends AsyncTask<String, Void, ArrayList> {

        private ArrayList<String> return_values = new ArrayList<String>();

        private ClientRequest(Context context) {
            mContext = context;
        }

        @Override
        protected ArrayList doInBackground(String[] params) {
            // do above Server call here
            String request_uri = params[0];
            String token = params[1];
            String req_type = params[2];
            String payload = params[3];
            StringBuilder webPage = new StringBuilder();
            String data = "";
            int response_code = 0;
            BufferedReader r_buffer;

            String mode = "GET";
            if (!payload.isEmpty()) {
                // if we have a payload, we post it
                mode = "POST";
            }
            Log.d(logtag, request_uri + " " + mode + " " + token + " " + req_type + " " + payload);

            try {
                URL url = new URL(request_uri);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(TIMEOUT_CONN);
                conn.setRequestProperty("Content-type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");

                if(!req_type.equals("register")) {
                    // For register request don't authenticate
                    conn.setRequestProperty("Authorization", "Basic " + token);
                }

                if (mode.equals("POST")) {
                    Log.d(logtag, "POST: " + payload);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    } catch (Exception e) {
                        Log.d(logtag, "Exception in outstream: " + e);
                    }
                } else if (mode.equals("GET")) {
                    Log.d(logtag, "GET");
                    conn.setRequestMethod("GET");
                    try {
                        conn.connect();
                    } catch (Exception e) {
                        Log.e(logtag, "Get connection: " + e);
                    }
                }

                response_code = conn.getResponseCode();
                Log.d(logtag, "Response: " + response_code);

                if (response_code >= 200 && response_code <= 399) {
                    // OK response
                    r_buffer = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                } else {
                    // Error response code
                    r_buffer = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                }
                while ((data = r_buffer.readLine()) != null) {
                    webPage.append(data).append("\n");
                }
            } catch (Exception e) {
                Log.d(logtag, "Exception: " + e);
                webPage.append(e.toString());
            }
            return_values.add(SERVER_URI);
            return_values.add(token);
            return_values.add(req_type);
            return_values.add(Integer.toString(response_code));
            return_values.add(webPage.toString());
            Log.d(logtag, "HTTP Values Answer: " + return_values);
            return return_values;
        }

        @Override
        protected void onPostExecute(ArrayList values) {
            Log.d(logtag, "onPostExecute: " + values);
            // Deal with responses
            handleRequestAnswer(values);
        }
    }

    private static void handleRequestAnswer(ArrayList values) {
        // Called from NetClient class when request finishes
        // TODO: use custom Object as with Credentials might be a lot nicer
        String rsp_server_uri = "";
        String rsp_token = "";
        String rsp_req_type = "";
        String rsp_code = "";
        String rsp_msg = "";
        ArrayList<String> message_ok = new ArrayList<String>();
        String message_error = null;

        try {
            rsp_server_uri = values.get(0).toString();
            rsp_token = values.get(1).toString();
            rsp_req_type = values.get(2).toString();
            rsp_code = values.get(3).toString();
            rsp_msg = values.get(4).toString();
        } catch (IndexOutOfBoundsException e) {
            Log.e(logtag, "not all values returned.");
        }
        Log.d(logtag, "Server: " + rsp_server_uri + " Req Type: " + rsp_req_type +
                " Token :" + rsp_token +  " Resp Code: " + rsp_code + " Msg: " + rsp_msg);

        // Handle http response codes
        if (rsp_code.equals("200") || rsp_code.equals("201")) {
            // OK Reponses
            message_ok = parseJSONOKResponse(rsp_msg);
            if(rsp_req_type.equals("login")) {
                Log.d(logtag, "Login successful - saving Used credentials to file");
                Utils.saveCreds(mContext, credentials);
                Toast.makeText(mContext, mContext.getString(R.string.app_name) + " - Login successful!",
                        Toast.LENGTH_LONG).show();
                startReadyActivity(mContext);
            } else if(rsp_req_type.equals("register")) {
                Log.d(logtag, "Registration successful - saving Used credentials to file");
                Utils.saveCreds(mContext, credentials);
                Toast.makeText(mContext, mContext.getString(R.string.app_name) + " - Registration successful!",
                        Toast.LENGTH_LONG).show();
                startReadyActivity(mContext);
            } else if(rsp_req_type.equals("get_last_clip")) {
                String clip = getLastClipTextFromJSON(message_ok);
                Log.d(logtag, "Get_last_clip successful: " + clip);
                Utils.setClipboard(mContext, clip);
            } else if(rsp_req_type.equals("get_all_clips")) {
                ArrayList<String> clips = getAllClipsTextFromJSON(message_ok);
                Log.d(logtag, "Get_all_clips successful: " + clips.toString());
                startListClipsActivity(mContext, clips);
            } else if(rsp_req_type.equals("set_clip")) {
                String clip = getLastClipTextFromJSON(message_ok);
                Log.d(logtag, "Set_clip successful: " + rsp_msg + "\n" + clip);
                Toast.makeText(mContext, mContext.getString(R.string.app_name) + " - Set clip to:\n" + clip,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            // ERROR Responses
            message_error = parseJSONErrorResponse(rsp_msg);
            Toast.makeText(mContext, mContext.getString(R.string.app_name) + " - " + rsp_req_type + " failed with code: " +
                            rsp_code + "\n" + "Message: " + message_error,
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String getLastClipTextFromJSON(ArrayList<String> message) {
        /**
         * Get the last Clip as a cleartext string from the ArrayList containing all encrypted clips
         */
        String clip_encrypted = message.get(message.size() - 1);
        String clip_clear = Utils.decryptText(mContext, clip_encrypted);
        return clip_clear;
    }

    private static ArrayList<String> getAllClipsTextFromJSON(ArrayList<String> message) {
        /**
         * Get all Clips as a cleartext ArrayList from the ArrayList containing all encrypted Clips
         */
        ArrayList<String> clips_clear = new ArrayList<String>();
        for (int i=0; i < message.size(); i++) {
            clips_clear.add(Utils.decryptText(mContext, message.get(i)));
        }
        return clips_clear;
    }

    private static ArrayList<String> parseJSONOKResponse(String res) {
        /**
         * Parse ok HTTP JSON responses and return ArrayList of Strings
         */
        ArrayList<String> res_text = new ArrayList<String>();
        try {
            JSONObject jRes = new JSONObject(res);
            res_text.add(jRes.getString("text"));
        } catch(Exception JSONException) {
            Log.d(logtag, "Could not parse as JSONObject: " + res.toString());
            try {
                JSONArray jclips = new JSONArray(res);
                for (int i=0; i < jclips.length(); i++) {
                    res_text.add(jclips.getJSONObject(i).getString("text"));
                }
            } catch (JSONException e) {
                Log.e(logtag, "Could not parse as JSONArray: " + res.toString());
            }
        }
        Log.d(logtag, "OK Parsing as JSONArray: " + res_text.toString());
        return res_text;
    }

    private static String parseJSONErrorResponse(String res) {
        /**
         * Parse ok HTTP JSON responses and return ArrayList of Strings
         */
        String res_text = null;
        try {
            JSONObject jRes = new JSONObject(res);
            res_text = (jRes.getString("text"));
        } catch(Exception JSONException) {
            Log.d(logtag, "Could not parse as JSONObject: " + res.toString());
        }
        Log.d(logtag, "OK Parsing error JSON response: " + res_text.toString());
        return res_text;
    }

    private static void startReadyActivity(Context context) {
        // Login successful switch to Ready Screen
        Log.d(logtag, "startReadyActivity");
        try {
            Intent i = new Intent(context, ReadyActivity.class);
            mContext.startActivity(i);
        } catch (Exception e) {
            Log.e(logtag, "Exception calling: " + e);
        }
    }

    private static void startListClipsActivity(Context context, ArrayList<String> clips) {
        /**
         * Start Activity to show all decrypted Clips
         */

        Log.d(logtag, "startListClipsActivity");
        String[] clips_array = new String[clips.size()];

        for(int i=0; i< clips.size(); i++) {
            // ArrayList to String Array
            clips_array[i] = clips.get(i);
        }

        try {
            Intent i = new Intent(context, ListClipsActivity.class);
            i.putExtra("clips", clips_array);
            mContext.startActivity(i);
        } catch (Exception e) {
            Log.e(logtag, "Error starting Activity: " + e);
        }
    }
}