package com.example.colorclash;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.example.colorclash.ProfileManager;

public class MultiplayerMenuActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST = 100;
    private EditText playerNameField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_menu);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        playerNameField = findViewById(R.id.multiplayer_player_name);
        Button btnHost  = findViewById(R.id.btn_host);
        Button btnJoin  = findViewById(R.id.btn_join);

        String savedName = new ProfileManager(this).getPrimaryName();
        if (savedName != null && !savedName.isEmpty() && !savedName.equals("Guest")) {
            playerNameField.setText(savedName);
        }

        btnHost.setOnClickListener(v -> proceed(true));
        btnJoin.setOnClickListener(v -> proceed(false));

        // Back to Main Menu — closes this activity and returns to MainActivity.
        Button btnBack = findViewById(R.id.btn_back_mp);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        requestRequiredPermissions();
    }

    private void proceed(boolean host) {
        String name = playerNameField.getText() == null
                ? "" : playerNameField.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your player name!", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, host ? HostActivity.class : JoinActivity.class);
        intent.putExtra("player_name", name);
        startActivity(intent);
    }

    private void requestRequiredPermissions() {
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

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                            "Location permission required for Wi-Fi Direct multiplayer.",
                            Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }
    }
}
