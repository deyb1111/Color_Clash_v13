package com.example.colorclash;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.colorclash.database.DatabaseManager;

public class GameOverActivity extends AppCompatActivity {

    private static final int GOLD_PER_POINT_DIVISOR = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        String winner     = getIntent().getStringExtra("winner_name");
        String loser      = getIntent().getStringExtra("loser_name");
        int    winnerScore = getIntent().getIntExtra("winner_score", 0);
        int    loserScore  = getIntent().getIntExtra("loser_score",  0);
        int    winnerColor = getIntent().getIntExtra("winner_color", 0xFFFFFFFF);

        int winnerGold = winnerScore / GOLD_PER_POINT_DIVISOR;
        int loserGold  = loserScore  / GOLD_PER_POINT_DIVISOR;

        try {
            DatabaseManager db = DatabaseManager.getInstance(this);
            // Ensure player profiles exist
            db.ensurePlayer(winner);
            db.ensurePlayer(loser);
            // Persist match + gold
            db.saveMatch(winner, loser, winnerScore, loserScore, winner, winnerGold, loserGold);
            db.addGold(winner, winnerGold);
            db.addGold(loser,  loserGold);
        } catch (Exception e) {
            // Non-fatal — game still shows result even if DB write fails
            e.printStackTrace();
        }

        // ── Update UI ────────────────────────────────────────────────────────
        TextView winnerText = findViewById(R.id.winner_text);
        TextView scoreText  = findViewById(R.id.score_text);
        TextView goldText   = findViewById(R.id.gold_text);
        Button   playAgain  = findViewById(R.id.play_again);

        winnerText.setText(winner + " Wins!");
        winnerText.setTextColor(winnerColor);

        scoreText.setText(winner + ":  " + winnerScore + " pts\n"
                + loser  + ":  " + loserScore  + " pts");

        goldText.setText("Gold earned — "
                + winner + ": +" + winnerGold + "  |  "
                + loser  + ": +" + loserGold);

        playAgain.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}