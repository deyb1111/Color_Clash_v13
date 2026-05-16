package com.example.colorclash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.colorclash.network.GameClient;


public class JoinActivity extends AppCompatActivity {

    private static final String TAG = "JoinActivity";
    private static final int    MAX_RETRIES = 2;

    private GameClient gameClient;
    private String     playerName;
    private int        retryCount = 0;

    private TextView statusText;
    private EditText hostIpField;
    private Handler  mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        playerName  = getIntent().getStringExtra("player_name");
        statusText  = findViewById(R.id.join_status);
        hostIpField = findViewById(R.id.host_ip_input);
        hostIpField.setText("192.168.49.1"); // default Wi-Fi Direct group owner IP

        Button btnConnect = findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(v -> attemptConnect());

        findViewById(R.id.btn_back_join).setOnClickListener(v -> finish());

        setStatus("WAITING_FOR_PLAYER");
    }

    private void attemptConnect() {
        String ip = hostIpField.getText() == null
                ? "" : hostIpField.getText().toString().trim();
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter the host's IP address", Toast.LENGTH_SHORT).show();
            return;
        }
        setStatus("WAITING_FOR_PLAYER — connecting to " + ip + "…");
        connectToHost(ip);
    }

    private void connectToHost(String ip) {
        if (gameClient != null) gameClient.stop();

        gameClient = new GameClient(new GameClient.ClientListener() {
            @Override public void onConnected() {
                mainHandler.post(() -> {
                    setStatus("CONNECTED");
                    retryCount = 0;
                    launchGame(ip);
                });
            }
            @Override public void onStateReceived(org.json.JSONObject state)  {}
            @Override public void onGameOver(String w, String l, int ws, int ls) {}
            @Override public void onDisconnected() {
                mainHandler.post(() -> handleDisconnect(ip));
            }
            @Override public void onError(String message) {
                mainHandler.post(() -> {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        setStatus("RECONNECTING (attempt " + retryCount + ")…");
                        mainHandler.postDelayed(() -> connectToHost(ip), 2000);
                    } else {
                        setStatus("CONNECTION_FAILED: " + message);
                    }
                });
            }
        });
        gameClient.connectAsync(ip);
    }

    private void handleDisconnect(String ip) {
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            setStatus("DISCONNECTED — RECONNECTING (attempt " + retryCount + ")…");
            mainHandler.postDelayed(() -> connectToHost(ip), 2000);
        } else {
            setStatus("DISCONNECTED");
        }
    }

    private void launchGame(String hostIp) {
        Intent intent = new Intent(this, MultiplayerGameActivity.class);
        intent.putExtra("player_name", playerName);
        intent.putExtra("role",        "CLIENT");
        intent.putExtra("host_ip",     hostIp);
        MultiplayerGameActivity.sharedClient = gameClient;
        startActivity(intent);
        finish();
    }

    private void setStatus(String status) {
        if (statusText != null) statusText.setText("Status: " + status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
