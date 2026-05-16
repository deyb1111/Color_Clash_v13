package com.example.colorclash;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.colorclash.network.GameServer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class HostActivity extends AppCompatActivity {

    private static final String TAG = "HostActivity";
    private static final int    PERM_REQUEST = 200;

    private WifiP2pManager         p2pManager;
    private WifiP2pManager.Channel p2pChannel;
    private BroadcastReceiver      p2pReceiver;

    private GameServer gameServer;
    private String     playerName;
    private TextView   statusText;
    private TextView   ipText;
    private Handler    mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        playerName = getIntent().getStringExtra("player_name");
        statusText = findViewById(R.id.host_status);
        ipText     = findViewById(R.id.host_ip_text);

        setStatus("WAITING_FOR_PLAYER");
        startTcpServer();
        showDeviceIp();

        p2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (p2pManager != null) {
            p2pChannel = p2pManager.initialize(this, getMainLooper(), null);
            requestWifiDirectPermissions();
        }
        findViewById(R.id.btn_back_host).setOnClickListener(v -> finish());
    }

//tcp server
    private void startTcpServer() {
        gameServer = new GameServer(new GameServer.ServerListener() {
            @Override public void onClientConnected() {
                mainHandler.post(() -> {
                    setStatus("CONNECTED — launching game…");
                    launchGame();
                });
            }
            @Override public void onInputReceived(float vx, float vy) {
            }
            @Override public void onClientDisconnected() {
                mainHandler.post(() -> setStatus("DISCONNECTED"));
            }
            @Override public void onError(String message) {
                mainHandler.post(() -> setStatus("TCP error: " + message));
            }
        });
        gameServer.startAsync();
    }

//permission handling
    private void requestWifiDirectPermissions() {
        java.util.List<String> needed = new java.util.ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        }

        if (needed.isEmpty()) {
            removeGroupThenCreate();
        } else {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERM_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQUEST) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                removeGroupThenCreate();
            } else {
                Toast.makeText(this,
                        "Location permission denied — Wi-Fi Direct unavailable.\n"
                                + "Share your LAN IP with the joiner instead.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

//wifi direct
    private void removeGroupThenCreate() {
        if (!hasWifiDirectPermission()) return;
        try {
            p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess()      { createWifiDirectGroup(); }
                @Override public void onFailure(int r) { createWifiDirectGroup(); }
            });
        } catch (SecurityException e) {
            setStatus("Wi-Fi Direct permission denied");
        }
    }

    private void createWifiDirectGroup() {
        if (!hasWifiDirectPermission()) return;
        try {
            p2pManager.createGroup(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { requestGroupInfo(); }
                @Override public void onFailure(int reason) {
                    mainHandler.post(() ->
                            setStatus("Wi-Fi Direct unavailable (err " + reason
                                    + ") — share your LAN IP below"));
                }
            });
        } catch (SecurityException e) {
            setStatus("Wi-Fi Direct permission denied");
        }
    }

    private void requestGroupInfo() {
        if (!hasWifiDirectPermission()) return;
        try {
            p2pManager.requestGroupInfo(p2pChannel, group -> {
                if (group == null) return;
                mainHandler.post(() -> {
                    String current = ipText.getText() != null
                            ? ipText.getText().toString() : "";
                    ipText.setText(current + "\nWi-Fi Direct IP: 192.168.49.1");
                    ipText.setVisibility(android.view.View.VISIBLE);
                });
            });
        } catch (SecurityException e) {
            setStatus("Wi-Fi Direct permission denied");
        }
    }

    private boolean hasWifiDirectPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void showDeviceIp() {
        new Thread(() -> {
            String ip = "unknown";
            try {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            ip = addr.getHostAddress();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            final String displayIp = ip;
            mainHandler.post(() -> {
                if (ipText != null) {
                    String existing = ipText.getText() != null ? ipText.getText().toString() : "";
                    if (existing.isEmpty()) {
                        ipText.setText("Your IP: " + displayIp);
                    } else {
                        ipText.setText(existing + "\nLAN IP: " + displayIp);
                    }
                    ipText.setVisibility(android.view.View.VISIBLE);
                }
            });
        }, "IpResolver").start();
    }


    private void launchGame() {

        MultiplayerGameActivity.sharedServer = gameServer;
        Intent intent = new Intent(this, MultiplayerGameActivity.class);
        intent.putExtra("player_name", playerName);
        intent.putExtra("role", "HOST");
        startActivity(intent);
        finish();
    }


    private void setStatus(String status) {
        if (statusText != null) statusText.setText("Status: " + status);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (p2pManager == null) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        p2pReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) { /* reserved */ }
        };
        registerReceiver(p2pReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (p2pReceiver != null) unregisterReceiver(p2pReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (p2pManager != null && p2pChannel != null && hasWifiDirectPermission()) {
            try { p2pManager.removeGroup(p2pChannel, null); }
            catch (SecurityException ignored) {}
        }
    }
}