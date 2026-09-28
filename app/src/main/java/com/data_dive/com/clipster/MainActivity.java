package com.data_dive.com.clipster;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Launcher Activity: login or register, or go straight to ReadyActivity if we are logged in
 */
public class MainActivity extends AppCompatActivity implements NetClient.Listener {

    private static final String TAG = "MainActivity";
    // Launched by the app shortcut, see res/xml/shortcuts.xml
    public static final String ACTION_GET_LAST_CLIP = "com.data_dive.com.clipster.action.GET_LAST_CLIP";
    private static final int LOCAL_NETWORK_REQUEST = 1;

    private TextInputLayout userLayout, passwordLayout, serverLayout;
    private TextInputEditText user, password, server;
    private MaterialCheckBox allowSelfSigned;
    private MaterialButton login, register;
    private LinearProgressIndicator progress;
    private int runningTasks = 0;

    // Waiting for the local network permission
    private Credentials pendingCredentials;
    private boolean pendingIsLogin;

    private static final class Setup {
        final Credentials credentials;
        final boolean needsLocalNetwork;

        Setup(Credentials credentials, boolean needsLocalNetwork) {
            this.credentials = credentials;
            this.needsLocalNetwork = needsLocalNetwork;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userLayout = findViewById(R.id.user_layout);
        passwordLayout = findViewById(R.id.pw_layout);
        serverLayout = findViewById(R.id.server_layout);
        user = findViewById(R.id.user);
        password = findViewById(R.id.pw);
        server = findViewById(R.id.server);
        login = findViewById(R.id.login);
        register = findViewById(R.id.register);
        allowSelfSigned = findViewById(R.id.allow_self_signed);
        progress = findViewById(R.id.progress);

        login.setOnClickListener(v -> startSetup(true));
        register.setOnClickListener(v -> startSetup(false));

        if (Intent.ACTION_EDIT.equals(getIntent().getAction()) && savedInstanceState == null) {
            showSavedCreds();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        skipLoginIfPossible();
    }

    private void showSavedCreds() {
        // The password is never saved
        if (Utils.areCredsSaved(this)) {
            Credentials creds = Utils.getCreds(this);
            user.setText(creds.user);
            server.setText(creds.server);
            allowSelfSigned.setChecked(creds.allowSelfSigned);
        }
    }

    private void skipLoginIfPossible() {
        // Edit Credentials in ReadyActivity: stay here and allow the user to edit them
        String action = getIntent().getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            return;
        }
        if (Utils.areCredsSaved(this)) {
            Intent intent = new Intent(this, ReadyActivity.class);
            if (ACTION_GET_LAST_CLIP.equals(action)) {
                intent.setAction(ACTION_GET_LAST_CLIP);
            }
            startActivity(intent);
            finish();
        } else if (ACTION_GET_LAST_CLIP.equals(action)) {
            Toast.makeText(this, R.string.msg_login_first, Toast.LENGTH_LONG).show();
            // Only show the hint once, not on every resume
            getIntent().setAction(Intent.ACTION_MAIN);
        }
    }

    private void startSetup(boolean isLogin) {
        String usr = text(user);
        String pw = text(password);
        String srv = text(server).trim();
        boolean selfSigned = allowSelfSigned.isChecked();
        srv = Utils.formatServerUri(srv.isEmpty() ? getString(R.string.default_host) : srv);

        userLayout.setError(usr.isEmpty() ? getString(R.string.error_username_required) : null);
        if (pw.isEmpty()) {
            passwordLayout.setError(getString(R.string.error_password_required));
        } else if (pw.length() < Utils.MIN_PW_LENGTH) {
            passwordLayout.setError(getResources()
                    .getQuantityString(R.plurals.error_password_too_short, Utils.MIN_PW_LENGTH, Utils.MIN_PW_LENGTH));
        } else {
            passwordLayout.setError(null);
        }
        serverLayout.setError(Utils.isValidServerUri(srv) ? null : getString(R.string.error_server_invalid));
        if (userLayout.getError() != null || passwordLayout.getError() != null || serverLayout.getError() != null) {
            return;
        }

        final String serverUri = srv;
        onRequestStarted();
        // Key derivation is deliberately slow and the local network check needs DNS
        Async.run(
                () -> new Setup(
                        Credentials.fromPassword(usr, pw, serverUri, selfSigned),
                        LocalNetwork.isPermissionMissing(this) && LocalNetwork.isLocalServer(serverUri)),
                (setup, error) -> {
                    onRequestFinished();
                    if (error != null) {
                        Log.e(
                                TAG,
                                "Could not create credentials: "
                                        + error.getClass().getSimpleName());
                        Toast.makeText(this, R.string.error_key_derivation, Toast.LENGTH_LONG)
                                .show();
                    } else if (setup.needsLocalNetwork) {
                        pendingCredentials = setup.credentials;
                        pendingIsLogin = isLogin;
                        requestPermissions(new String[] {LocalNetwork.PERMISSION}, LOCAL_NETWORK_REQUEST);
                    } else {
                        sendSetupRequest(setup.credentials, isLogin);
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCAL_NETWORK_REQUEST && pendingCredentials != null) {
            // If denied, the request fails and explains that the permission is needed
            sendSetupRequest(pendingCredentials, pendingIsLogin);
            pendingCredentials = null;
        }
    }

    private void sendSetupRequest(Credentials credentials, boolean isLogin) {
        NetClient client = new NetClient(this, credentials);
        if (isLogin) {
            client.login();
        } else {
            client.register();
        }
    }

    private static String text(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }

    @Override
    public void onRequestStarted() {
        runningTasks++;
        updateLoading();
    }

    @Override
    public void onRequestFinished() {
        runningTasks = Math.max(0, runningTasks - 1);
        updateLoading();
    }

    private void updateLoading() {
        boolean loading = runningTasks > 0;
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        login.setEnabled(!loading);
        register.setEnabled(!loading);
    }
}
