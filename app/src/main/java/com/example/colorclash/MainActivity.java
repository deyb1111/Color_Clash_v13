package com.example.colorclash;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        ImageView btnOffline      = findViewById(R.id.btn_offline);
        ImageView btnMultiplayer  = findViewById(R.id.btn_multiplayer);
        ImageView btnStore        = findViewById(R.id.btn_store);

        addPressEffect(btnOffline);
        addPressEffect(btnMultiplayer);
        addPressEffect(btnStore);

        btnOffline.setOnClickListener(v ->
                startActivity(new Intent(this, OfflineSetupActivity.class)));

        btnMultiplayer.setOnClickListener(v ->
                startActivity(new Intent(this, MultiplayerMenuActivity.class)));

        btnStore.setOnClickListener(v ->
                startActivity(new Intent(this, StoreActivity.class)));
    }

    private void addPressEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animateScale(v, 0.92f, 0.7f, 100);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animateScale(v, 1.0f, 1.0f, 150);
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    break;
            }
            return true;
        });
    }

    private void animateScale(View v, float scale, float alpha, int duration) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(v, "scaleX", scale),
                ObjectAnimator.ofFloat(v, "scaleY", scale),
                ObjectAnimator.ofFloat(v, "alpha", alpha)
        );
        set.setDuration(duration);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }
}
