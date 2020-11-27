package com.data_dive.com.clipster;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    public final static  String APP_NAME = "Clipster";
    private final static String logtag = "MainActivity";
    private final static int BUTTON_DELAY = 1000;
    boolean cred_exist = false;
    int num_clicks = 0;
    boolean throttle_clicks = false;

    private String SERVER_URI;
    EditText password, user, server;
    TextView register, login;
    String usr, pw, srv;

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(logtag,"on Resume: Check for creds");
        checkForCreds();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Utils.clearCreds(this); // For debugging
        Log.d(logtag, "Oncreate");
        super.onCreate(savedInstanceState);

        SERVER_URI = getResources().getString(R.string.default_host);

        checkForCreds();

        setContentView(R.layout.activity_main);

        user = findViewById(R.id.user);
        password = findViewById(R.id.pw);
        server = findViewById(R.id.server);
        login = findViewById(R.id.login);
        register = findViewById(R.id.register);

        login.setOnClickListener(btnListener);
        login.setTag("login");
        register.setOnClickListener(btnListener);
        register.setTag("register");
    }

    private View.OnClickListener btnListener = new View.OnClickListener() {
        public void onClick(View v) {
            // Handle CLicks - Increasing delay excessive tries
            String action_tag = v.getTag().toString();
            Log.d(logtag, "Clicked Button: " + action_tag);
            num_clicks += 1;

            if(num_clicks % 3 != 0 && !throttle_clicks) {
                prepareSetupRequest(action_tag);
            } else {
                throttle_clicks = true;
                Toast.makeText(getBaseContext(), APP_NAME + " - " + getString(R.string.button_delay_msg),
                        Toast.LENGTH_LONG).show();
                login.setEnabled(false);
                register.setEnabled(false);
                login.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        login.setEnabled(true);
                        register.setEnabled(true);
                        throttle_clicks = false;
                    }
                }, BUTTON_DELAY * num_clicks);
            }
        }
    };

    private void checkForCreds() {
        // Get intent and action -> Edit Creds from ReadyActivity calls this
        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            // Allow user to edit credentials
            Log.d(logtag, "Received ACTION_EDIT Intent. Allow to edit credentials.");
        } else {
            // if Creds are saved skip to ReadyActivity
            Log.d(logtag, "Skip to check for Creds");
            if(!Utils.areCredsSaved(this)) {
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

        if(srv.isEmpty()) {
            srv = SERVER_URI;
        }

        if(!usr.isEmpty() && !pw.isEmpty()) {
            // Instance of class with Application context so its decoupled from Activity
            NetClient client = new NetClient(this, srv, usr, pw);
            if(action.equals("login")) {
                Log.d(logtag,"Calling login function");
                client.Login();
            } else if(action.equals("register")) {
                Log.d(logtag, "Calling register function");
                client.Register(usr, pw);
            }
        } else {
            Toast.makeText(this, getString(R.string.app_name) +
                    " - Please enter an username and password", Toast.LENGTH_LONG).show();
        }
    }

}
