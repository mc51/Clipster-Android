package com.data_dive.com.clipster;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 *  Launcher Activity which checks if we need to setup first or can go straight to ready mode
 */

public class MainActivity extends AppCompatActivity implements NetClient.Listener {

    private final static String logtag = "MainActivity";
    // Launched by the app shortcut, see res/xml/shortcuts.xml
    public static final String ACTION_GET_LAST_CLIP = "com.data_dive.com.clipster.action.GET_LAST_CLIP";

    private String SERVER_URI;
    private TextInputLayout user_layout, pw_layout, server_layout;
    private TextInputEditText password, user, server;
    private MaterialCheckBox ignore_cert;
    private MaterialButton register, login;
    private LinearProgressIndicator progress;
    private int running_tasks = 0;

    @Override
    protected void onResume() {
        super.onResume();
        checkForCreds();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        SERVER_URI = getString(R.string.default_host);

        setContentView(R.layout.activity_main);

        user_layout = findViewById(R.id.user_layout);
        pw_layout = findViewById(R.id.pw_layout);
        server_layout = findViewById(R.id.server_layout);
        user = findViewById(R.id.user);
        password = findViewById(R.id.pw);
        server = findViewById(R.id.server);
        login = findViewById(R.id.login);
        register = findViewById(R.id.register);
        ignore_cert = findViewById(R.id.ignore_cert);
        progress = findViewById(R.id.progress);

        login.setOnClickListener(v -> prepareSetupRequest(true));
        register.setOnClickListener(v -> prepareSetupRequest(false));

        if (Intent.ACTION_EDIT.equals(getIntent().getAction()) && savedInstanceState == null) {
            displaySavedCredsAsDefaults();
        }
    }

    private void displaySavedCredsAsDefaults() {
        // Show saved credentials as default entries. The password is never saved.
        if (Utils.areCredsSaved(this)) {
            Credentials creds = Utils.getCreds(this);
            user.setText(creds.user);
            server.setText(creds.server);
            ignore_cert.setChecked(creds.ignore_cert);
        }
    }

    private void checkForCreds() {
        // Edit Creds from ReadyActivity: stay here and allow the user to edit credentials
        String action = getIntent().getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            return;
        }
        if (Utils.areCredsSaved(this)) {
            Intent i = new Intent(this, ReadyActivity.class);
            if (ACTION_GET_LAST_CLIP.equals(action)) {
                i.setAction(ACTION_GET_LAST_CLIP);
            }
            startActivity(i);
            finish();
        } else if (ACTION_GET_LAST_CLIP.equals(action)) {
            Toast.makeText(this, R.string.msg_login_first, Toast.LENGTH_LONG).show();
            // Only show the hint once, not on every resume
            getIntent().setAction(Intent.ACTION_MAIN);
        }
    }

    private void prepareSetupRequest(boolean isLogin) {
        // Validate input, then register or login
        String usr = text(user);
        String pw = text(password);
        String srv = text(server).trim();
        boolean ignore = ignore_cert.isChecked();

        if (srv.isEmpty()) {
            srv = SERVER_URI;
        }
        srv = Utils.formatURIProtocol(srv);

        user_layout.setError(usr.isEmpty() ? getString(R.string.error_username_required) : null);
        if (pw.isEmpty()) {
            pw_layout.setError(getString(R.string.error_password_required));
        } else if (pw.length() < Utils.MIN_PW_LENGTH) {
            pw_layout.setError(getResources().getQuantityString(R.plurals.error_password_too_short,
                    Utils.MIN_PW_LENGTH, Utils.MIN_PW_LENGTH));
        } else {
            pw_layout.setError(null);
        }
        server_layout.setError(Utils.validateServerURI(srv) ? null : getString(R.string.error_server_invalid));
        if (user_layout.getError() != null || pw_layout.getError() != null || server_layout.getError() != null) {
            return;
        }

        final String final_srv = srv;
        onRequestStarted();
        // Key derivation is deliberately slow, keep it off the main thread
        Async.run(() -> Credentials.fromPassword(usr, pw, final_srv, ignore), (creds, error) -> {
            onRequestFinished();
            if (error != null) {
                Log.e(logtag, "Could not create credentials: " + error.getClass().getSimpleName());
                Toast.makeText(this, R.string.error_key_derivation, Toast.LENGTH_LONG).show();
                return;
            }
            NetClient client = new NetClient(this, creds);
            if (isLogin) {
                client.Login();
            } else {
                client.Register();
            }
        });
    }

    private static String text(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }

    @Override
    public void onRequestStarted() {
        running_tasks++;
        updateLoading();
    }

    @Override
    public void onRequestFinished() {
        running_tasks = Math.max(0, running_tasks - 1);
        updateLoading();
    }

    private void updateLoading() {
        boolean loading = running_tasks > 0;
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        login.setEnabled(!loading);
        register.setEnabled(!loading);
    }
}
