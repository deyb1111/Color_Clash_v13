package com.example.colorclash.models;

import java.util.ArrayDeque;
import java.util.Deque;


public class Player {
    public float x, y;
    public float vx, vy;
    public int color;
    public String name;
    public int lives = 5;
    public int score = 0;
    public float radius = 60f;
    public boolean hasShield = false;
    public long shieldEndTime = 0;
    public boolean hitFlash = false;
    public long hitFlashTime = 0;
    public float speedMultiplier = 1f;
    public long speedBoostEndTime = 0;


    public static final float DASH_MULTIPLIER       = 3.0f;
    public static final long  DASH_DURATION_MS      = 220;
    public static final long  DASH_COOLDOWN_MS      = 2000;
    public long dashEndTime      = 0;
    public long dashCooldownEnd  = 0;

    public String pendingPowerUp = null;
    public int    pendingPowerUpColor = 0;
    public long   pendingPowerUpExpiry = 0;
    public static final long PENDING_POWERUP_TTL_MS = 12_000;

    public int    equippedCosmeticColor = 0;

    public String equippedTrailAsset = null;

    public final Deque<float[]> trailPoints = new ArrayDeque<>();
    private static final int    TRAIL_MAX_POINTS = 18;
    private static final long   TRAIL_SAMPLE_MS  = 35;
    private long lastTrailSampleTime = 0;

    private long damageCooldownEndTime = 0;
    private static final long DAMAGE_COOLDOWN_MS = 1000;

    public static final float MAX_SPEED = 400f;

    public Player(String name, int color, float startX, float startY) {
        this.name = name;
        this.color = color;
        this.x = startX;
        this.y = startY;
    }

    public void update(long deltaTime, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();

        float dashBoost = (now < dashEndTime) ? DASH_MULTIPLIER : 1f;
        float effective = speedMultiplier * dashBoost;

        x += vx * effective * (deltaTime / 1000f);
        y += vy * effective * (deltaTime / 1000f);

        if (x - radius < 0) x = radius;
        if (x + radius > screenWidth) x = screenWidth - radius;
        if (y - radius < 0) y = radius;
        if (y + radius > screenHeight) y = screenHeight - radius;

        if (hasShield && now > shieldEndTime) hasShield = false;
        if (hitFlash && now > hitFlashTime) hitFlash = false;
        if (speedMultiplier > 1f && now > speedBoostEndTime) speedMultiplier = 1f;

        if (pendingPowerUp != null && now > pendingPowerUpExpiry) {
            pendingPowerUp = null;
            pendingPowerUpColor = 0;
        }

        if (now - lastTrailSampleTime >= TRAIL_SAMPLE_MS) {
            lastTrailSampleTime = now;
            trailPoints.addFirst(new float[]{x, y});
            while (trailPoints.size() > TRAIL_MAX_POINTS) trailPoints.removeLast();
        }
    }


    public boolean takeDamage() {
        if (hasShield) return false;
        long now = System.currentTimeMillis();
        if (now < damageCooldownEndTime) return false;
        lives--;
        hitFlash = true;
        hitFlashTime = now + 300;
        damageCooldownEndTime = now + DAMAGE_COOLDOWN_MS;
        return true;
    }

    public boolean collidesWith(Player other) {
        float dx = x - other.x;
        float dy = y - other.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < (radius + other.radius);
    }

    public boolean tryDash() {
        long now = System.currentTimeMillis();
        if (now < dashCooldownEnd) return false;
        if (vx == 0 && vy == 0) return false;
        dashEndTime     = now + DASH_DURATION_MS;
        dashCooldownEnd = now + DASH_COOLDOWN_MS;
        return true;
    }

    public boolean isDashing() {
        return System.currentTimeMillis() < dashEndTime;
    }

    public float dashCooldownRatio() {
        long now = System.currentTimeMillis();
        long remaining = dashCooldownEnd - now;
        if (remaining <= 0) return 0f;
        return Math.min(1f, remaining / (float) DASH_COOLDOWN_MS);
    }


    public String activatePowerUp() {
        if (pendingPowerUp == null) return null;
        String t = pendingPowerUp;
        PowerUp.applyType(this, t);
        pendingPowerUp      = null;
        pendingPowerUpColor = 0;
        return t;
    }
}
