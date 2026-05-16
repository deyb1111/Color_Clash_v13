package com.example.colorclash.models;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.util.Random;


public class PowerUp {
    public float x, y;
    public float radius = 40f;
    public int color;
    public String type;
    private long spawnTime;
    private static final Random random = new Random();

    public PowerUp(float x, float y) {
        this.x = x;
        this.y = y;
        this.spawnTime = System.currentTimeMillis();

        int roll = random.nextInt(3);
        if (roll == 0) { type = "SPEED"; color = Color.CYAN; }
        else if (roll == 1) { type = "SHIELD"; color = Color.MAGENTA; }
        else { type = "LIFE"; color = Color.GREEN; }
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - spawnTime > 10000;
    }


    public void applyTo(Player player) {
        applyType(player, type);
    }


    public static void applyType(Player player, String typeStr) {
        if (player == null || typeStr == null) return;
        long now = System.currentTimeMillis();
        if (typeStr.equals("SPEED")) {
            player.speedMultiplier = 1.8f;
            player.speedBoostEndTime = now + 5000;
        } else if (typeStr.equals("SHIELD")) {
            player.hasShield = true;
            player.shieldEndTime = now + 5000;
        } else if (typeStr.equals("LIFE")) {
            if (player.lives < 5) player.lives++;
        }
    }

    public static int colorForType(String typeStr) {
        if ("SPEED".equals(typeStr))  return Color.CYAN;
        if ("SHIELD".equals(typeStr)) return Color.MAGENTA;
        if ("LIFE".equals(typeStr))   return Color.GREEN;
        return Color.WHITE;
    }

    public boolean collidesWith(Player p) {
        float dx = x - p.x;
        float dy = y - p.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < (radius + p.radius);
    }


    public void draw(Canvas canvas, Paint paint) {
        float pulse = (float) Math.sin((System.currentTimeMillis() - spawnTime) * 0.008) * 8;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(x, y, radius + pulse, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(28f);
        paint.setFakeBoldText(true);
        float textWidth = paint.measureText(type);
        canvas.drawText(type, x - textWidth / 2, y + 10, paint);
    }
}
