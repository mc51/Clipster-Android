package com.data_dive.com.clipster;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 *  Launcher Activity which checks if we need to setup first or can go straight to ready mode
 */

public class MainActivity extends AppCompatActivity {

    private final static String logtag = "MainActivity";
    private final static int BUTTON_DELAY = 2000;

    private String SERVER_URI;
    EditText password, user, server;
    CheckBox ignore_cert;
    TextView register, login;
    String usr, pw, srv;

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(logtag, "on Resume: Check for creds");
        checkForCreds();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Utils.clearCreds(this); // For debugging
        Log.d(logtag, "Oncreate");
        super.onCreate(savedInstanceState);

        SERVER_URI = getResources().getString(R.string.default_host);

        setContentView(R.layout.activity_main);

        user = findViewById(R.id.user);
        password = findViewById(R.id.pw);
        server = findViewById(R.id.server);
        login = findViewById(R.id.login);
        register = findViewById(R.id.register);
        ignore_cert = findViewById(R.id.ignore_cert);

        login.setOnClickListener(btnListener);
        login.setTag("login");
        register.setOnClickListener(btnListener);
        register.setTag("register");

        checkForCreds();
    }

    private  final View.OnClickListener btnListener = new DebouncedOnClickListener(BUTTON_DELAY, this) {
        public void onDebouncedClick(View v) {
            // Handle Clicks but debounced
            String action_tag = v.getTag().toString();
            Log.d(logtag, "Clicked Button: " + action_tag);
            prepareSetupRequest(action_tag);
        }
    };

    private void displaySavedCredsAsDefaults() {
        // Show saved credentials as default entries
        if(Utils.areCredsSaved(this)) {
            Log.d(logtag, "Creds saved, setting as defaults");
            Credentials creds = Utils.getCreds(this);
            user.setText(creds.user);
            password.setText(creds.pw);
            server.setText(creds.server);
        } else {
            Log.d(logtag, "Creds not saved. Not displaying defaults");
        }
    }

    private void checkForCreds() {
        // Get intent and action -> Edit Creds from ReadyActivity calls this
        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            // Allow user to edit credentials
            displaySavedCredsAsDefaults();
            Log.d(logtag, "Received ACTION_EDIT Intent. Allow to edit credentials.");
        } else {
            // if Creds are saved skip to ReadyActivity
            Log.d(logtag, "Skip to check for Creds");
            if (!Utils.areCredsSaved(this)) {
                Log.d(logtag, "Creds are not saved yet. Ask for them.");
            } else {
                Log.d(logtag, "Creds available. Switch to Ready Activity.");
                Intent i = new Intent(this, ReadyActivity.class);
                startActivity(i);
            }
        }
    }

    private void prepareSetupRequest(String action) {
        // Start Setup process: register or login
        usr = user.getText().toString();
        pw = password.getText().toString();
        srv = server.getText().toString();
        boolean ignore = ignore_cert.isChecked();

        if (srv.isEmpty()) {
            srv = SERVER_URI;
        }
        srv = formatURIProtocol(srv);

        if (usr.isEmpty() || pw.isEmpty()) {
            Toast.makeText(this, getString(R.string.app_name) +
                    " - Please enter an username and password", Toast.LENGTH_LONG).show();
        } else if (!validateServerURI(srv)) {
            Log.e(logtag, "server uri INVALID: " + srv);
            Toast.makeText(this, getString(R.string.app_name) +
                            " - Invalid server address.\nFormat should be: https://clipster.cc:9999",
                    Toast.LENGTH_LONG).show();
        } else {
            Log.d(logtag, "server uri VALID: " + srv);
            Log.d(logtag, "Disable ssl certificate check: " + ignore);
            Credentials creds = new Credentials(usr, pw, "", "", srv, ignore);
            NetClient client = new NetClient(this, creds);

            if (action.equals("login")) {
                Log.d(logtag, "Calling login function");
                client.Login();
            } else if (action.equals("register")) {
                Log.d(logtag, "Calling register function");
                client.Register();
            }
        }
    }

    private String formatURIProtocol(String server) {

        return server;
        /**
        // Make sure we always use https:// and have no trailing slashes in URI
        server = server.replaceFirst("/*$", "");
        try {
            if (server.substring(0, 7).toLowerCase().contains("http://")) {
                Log.d(logtag, "server contains http:// replacing with https://");
                server = server.replace("http://", "https://");
            } else if (!server.substring(0, 8).toLowerCase().contains("https://")) {
                Log.w(logtag, "No protocol provided, adding https://");
                server = "https://" + server;
            }
            return server;
        } catch (Exception e) {
            Log.e(logtag, e.toString());
            return server;
        }
         */
    }

    private boolean validateServerURI(String server) {
        // Basic validity check for the server address
        if(server.contains("https://localhost")) { return true; }
        return android.util.Patterns.WEB_URL.matcher(server).matches();
    }
}
