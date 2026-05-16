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

        // Original setup data, forwarded by GameActivity so Play Again can
        // relaunch the match without re-entering names/colors.
        final String p1Name  = getIntent().getStringExtra("player1_name");
        final String p2Name  = getIntent().getStringExtra("player2_name");
        final int    p1Color = getIntent().getIntExtra("player1_color", 0xFFFF0000);
        final int    p2Color = getIntent().getIntExtra("player2_color", 0xFF0000FF);

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
        Button   mainMenu   = findViewById(R.id.btn_main_menu);

        winnerText.setText(winner + " Wins!");
        winnerText.setTextColor(winnerColor);

        scoreText.setText(winner + ":  " + winnerScore + " pts\n"
                + loser  + ":  " + loserScore  + " pts");

        goldText.setText("Gold earned — "
                + winner + ": +" + winnerGold + "  |  "
                + loser  + ": +" + loserGold);

        // Play Again — relaunch the match with the same player setup.
        // Falls back to the main menu if the setup data was not forwarded
        // (e.g. older code path).
        playAgain.setOnClickListener(v -> {
            if (p1Name != null && !p1Name.isEmpty()
                    && p2Name != null && !p2Name.isEmpty()) {
                Intent rematch = new Intent(this, GameActivity.class);
                rematch.putExtra("player1_name",  p1Name);
                rematch.putExtra("player2_name",  p2Name);
                rematch.putExtra("player1_color", p1Color);
                rematch.putExtra("player2_color", p2Color);
                startActivity(rematch);
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        });

        if (mainMenu != null) {
            mainMenu.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        }
    }
}
