package com.example.colorclash;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.colorclash.models.Player;
import com.example.colorclash.views.GameView;

public class GameActivity extends AppCompatActivity implements GameView.GameListener {

    private GameView  gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Hide navigation bar and status bar
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        setContentView(R.layout.activity_game);

        String p1Name  = getIntent().getStringExtra("player1_name");
        String p2Name  = getIntent().getStringExtra("player2_name");
        int    p1Color = getIntent().getIntExtra("player1_color", 0xFFFF0000);
        int    p2Color = getIntent().getIntExtra("player2_color", 0xFF0000FF);

        // FIX: Find the GameView from the layout and configure it
        gameView = findViewById(R.id.game_view);
        gameView.setPlayerData(p1Name, p1Color, p2Name, p2Color);
        gameView.setGameListener(this);
    }

    @Override
    public void onExit() {
        // Return to main menu
        finish();
    }

    @Override
    public void onBgColorChanged(int newColor) {
        // Frame removed — no tinting needed
    }

    @Override
    public void onGameOver(Player winner, Player loser) {
        Intent intent = new Intent(this, GameOverActivity.class);
        intent.putExtra("winner_name",  winner.name);
        intent.putExtra("loser_name",   loser.name);
        intent.putExtra("winner_score", winner.score);
        intent.putExtra("loser_score",  loser.score);
        intent.putExtra("winner_color", winner.color);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) gameView.pauseGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resumeGame();
    }
}