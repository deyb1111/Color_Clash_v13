package com.example.colorclash;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * OfflineSetupActivity — player name + colour entry before an offline match.
 *
 * Works with the new design layout that uses:
 *   - EditText for player names  (NOT TextInputEditText)
 *   - ImageView balls for colour picking  (NOT RadioGroup)
 *   - ImageView for the Start Battle button  (NOT Button)
 *
 * Crash fix: the old version looked for RadioGroup / TextInputEditText which
 * no longer exist in the new layout, causing instant NullPointerException.
 */
public class OfflineSetupActivity extends AppCompatActivity {

    private static final String TAG = "OfflineSetup";

    // ── Views ─────────────────────────────────────────────────────────────────
    private EditText    player1Name, player2Name;
    private LinearLayout player1Card, player2Card;

    private ImageView p1Red, p1Green, p1Blue, p1Yellow;
    private ImageView p2Red, p2Green, p2Blue, p2Yellow;

    // ── State ─────────────────────────────────────────────────────────────────
    private int player1Color = 0;   // 0 = nothing selected yet
    private int player2Color = 0;

    // ── Colour tables ─────────────────────────────────────────────────────────
    private static final int[] COLORS = {
            0xFFFF0000,   // Red
            0xFF00FF00,   // Green
            0xFF0000FF,   // Blue
            0xFFFFFF00    // Yellow
    };
    private static final int[] BORDER_COLORS = {
            0xFFFF2E2E, 0xFF2EFF2E, 0xFF2E8CFF, 0xFFFFDD2E
    };
    private static final int[] EDIT_BORDER_COLORS = {
            0xFFFF3A3A, 0xFF3AFF3A, 0xFF3A8AFF, 0xFFFFDD3A
    };

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_setup);

        // Full-screen immersive
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        initViews();
        setupColorPickers();
        setupStartButton();
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void initViews() {
        player1Name = findViewById(R.id.player1_name);
        player2Name = findViewById(R.id.player2_name);
        player1Card = findViewById(R.id.player1_card);
        player2Card = findViewById(R.id.player2_card);

        p1Red    = findViewById(R.id.p1_red);
        p1Green  = findViewById(R.id.p1_green);
        p1Blue   = findViewById(R.id.p1_blue);
        p1Yellow = findViewById(R.id.p1_yellow);

        p2Red    = findViewById(R.id.p2_red);
        p2Green  = findViewById(R.id.p2_green);
        p2Blue   = findViewById(R.id.p2_blue);
        p2Yellow = findViewById(R.id.p2_yellow);

        // Pre-fill Player 1 with the saved primary player name (if any)
        String savedName = new ProfileManager(this).getPrimaryName();
        if (savedName != null && !savedName.isEmpty() && !savedName.equals("Guest")) {
            player1Name.setText(savedName);
        }
    }

    // ── Colour picker ─────────────────────────────────────────────────────────

    private void setupColorPickers() {
        ImageView[] p1Balls = { p1Red, p1Green, p1Blue, p1Yellow };
        ImageView[] p2Balls = { p2Red, p2Green, p2Blue, p2Yellow };

        for (int i = 0; i < 4; i++) {
            final int index = i;

            // Guard: ball view may be null if layout IDs don't match
            if (p1Balls[i] != null) {
                addPressEffect(p1Balls[i]);
                p1Balls[i].setOnClickListener(v -> {
                    player1Color = COLORS[index];
                    highlightSelected(p1Balls, (ImageView) v);
                    updateCardBorder(player1Card, BORDER_COLORS[index]);
                    updateEditTextBorder(player1Name, EDIT_BORDER_COLORS[index]);
                });
            }

            if (p2Balls[i] != null) {
                addPressEffect(p2Balls[i]);
                p2Balls[i].setOnClickListener(v -> {
                    player2Color = COLORS[index];
                    highlightSelected(p2Balls, (ImageView) v);
                    updateCardBorder(player2Card, BORDER_COLORS[index]);
                    updateEditTextBorder(player2Name, EDIT_BORDER_COLORS[index]);
                });
            }
        }
    }

    /**
     * Highlights the selected ball by scaling it up.
     * No ring/oval drawn — the scale difference makes the selection clear.
     */
    private void highlightSelected(ImageView[] balls, ImageView selected) {
        for (ImageView ball : balls) {
            if (ball == null) continue;
            if (ball == selected) {
                ball.animate().alpha(1.0f).scaleX(1.2f).scaleY(1.2f).setDuration(140).start();
                ball.setBackground(null);
            } else {
                ball.animate().alpha(0.5f).scaleX(0.85f).scaleY(0.85f).setDuration(140).start();
                ball.setBackground(null);
            }
        }
    }

    private void updateCardBorder(LinearLayout card, int color) {
        if (card == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        bg.setColor(0x14101020);
        bg.setStroke((int) (2 * getResources().getDisplayMetrics().density), color);
        card.setBackground(bg);
    }

    private void updateEditTextBorder(EditText editText, int color) {
        if (editText == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        bg.setColor(0xFF1A1A2E);
        bg.setStroke((int) (1.5f * getResources().getDisplayMetrics().density), color);
        editText.setBackground(bg);
    }

    // ── Start button ──────────────────────────────────────────────────────────

    private void setupStartButton() {
        ImageView startButton = findViewById(R.id.start_game);
        if (startButton == null) {
            Log.e(TAG, "start_game ImageView not found in layout");
            return;
        }

        addPressEffect(startButton);

        startButton.setOnClickListener(v -> {
            String name1 = safeGetText(player1Name);
            String name2 = safeGetText(player2Name);

            if (name1.isEmpty() || name2.isEmpty()) {
                Toast.makeText(this, "Please enter both player names!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (name1.equals(name2)) {
                Toast.makeText(this, "Players must have different names!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (player1Color == 0) {
                Toast.makeText(this, "Please pick a color for Player 1!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (player2Color == 0) {
                Toast.makeText(this, "Please pick a color for Player 2!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (player1Color == player2Color) {
                Toast.makeText(this, "Players must choose different colors!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save profiles
            ProfileManager pm = new ProfileManager(this);
            pm.addProfile(name1);
            pm.addProfile(name2);

            try {
                Intent intent = new Intent(OfflineSetupActivity.this, GameActivity.class);
                intent.putExtra("player1_name",  name1);
                intent.putExtra("player2_name",  name2);
                intent.putExtra("player1_color", player1Color);
                intent.putExtra("player2_color", player2Color);
                startActivity(intent);
                finish();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start GameActivity", e);
                Toast.makeText(this, "Could not start game. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String safeGetText(EditText field) {
        if (field == null) return "";
        CharSequence text = field.getText();
        if (text == null) return "";
        return text.toString().trim();
    }

    /** Subtle press animation for any tappable view. */
    private void addPressEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animateScale(v, 0.92f, 0.75f, 90);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animateScale(v, 1.0f, 1.0f, 140);
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
                ObjectAnimator.ofFloat(v, "alpha",  alpha)
        );
        set.setDuration(duration);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }
}
