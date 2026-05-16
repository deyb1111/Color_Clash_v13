package com.example.colorclash;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.colorclash.database.DatabaseManager;

/**
 * MultiplayerGameOverActivity
 *
 * Displayed on BOTH devices after a multiplayer match ends.
 * Saves the match result and gold conversion to the local SQLite database.
 */
public class MultiplayerGameOverActivity extends AppCompatActivity {

    private static final int GOLD_DIVISOR = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_game_over);

        String winner      = getIntent().getStringExtra("winner_name");
        String loser       = getIntent().getStringExtra("loser_name");
        int    winnerScore = getIntent().getIntExtra("winner_score", 0);
        int    loserScore  = getIntent().getIntExtra("loser_score",  0);
        String myName      = getIntent().getStringExtra("my_name");

        int winnerGold = winnerScore / GOLD_DIVISOR;
        int loserGold  = loserScore  / GOLD_DIVISOR;

        // Determine what gold "I" earned
        boolean iWon    = myName != null && myName.equals(winner);
        int     myGold  = iWon ? winnerGold : loserGold;
        int     myScore = iWon ? winnerScore : loserScore;

        // ── Save to DB (each device saves its own side) ──────────────────────
        try {
            DatabaseManager db = DatabaseManager.getInstance(this);
            db.ensurePlayer(myName);
            db.addGold(myName, myGold);
            db.addPoints(myName, myScore);
        } catch (Exception e) { e.printStackTrace(); }

        // ── UI ───────────────────────────────────────────────────────────────
        TextView resultText = findViewById(R.id.mp_result_text);
        TextView scoreText  = findViewById(R.id.mp_score_text);
        TextView goldText   = findViewById(R.id.mp_gold_text);
        Button   btnMenu    = findViewById(R.id.mp_btn_menu);

        resultText.setText(iWon ? "🏆 You Win!" : "You Lose");
        resultText.setTextColor(iWon ? 0xFFFFDD22 : 0xFF9999AA);

        scoreText.setText(winner + ": " + winnerScore + " pts\n"
                        + loser  + ": " + loserScore  + " pts");

        goldText.setText("Gold earned this match: +" + myGold + " 🪙");

        btnMenu.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
