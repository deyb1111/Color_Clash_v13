package com.example.colorclash.models;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Player — runtime state for one player.
 *
 * Adds (this revision):
 *   - Dash mechanic: tryDash() applies a short speed burst with cooldown.
 *   - Pending power-up slot: power-ups are no longer applied on contact;
 *     they go into pendingPowerUp and the player chooses when to activate.
 *   - Cosmetic / trail visuals: equippedCosmeticColor + equippedTrailAsset
 *     are read by the renderer to draw the player's purchased cosmetics.
 *   - Trail history points for trail rendering.
 */
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

    // ── Dash ──────────────────────────────────────────────────────────────────
    /** Multiplier applied on top of speedMultiplier while dashing. */
    public static final float DASH_MULTIPLIER       = 3.0f;
    /** How long a dash lasts. */
    public static final long  DASH_DURATION_MS      = 220;
    /** Cooldown between dashes (from dash start). */
    public static final long  DASH_COOLDOWN_MS      = 2000;
    public long dashEndTime      = 0;
    public long dashCooldownEnd  = 0;

    // ── Pending power-up slot ────────────────────────────────────────────────
    /** Type string of the held-but-not-yet-activated power-up, or null. */
    public String pendingPowerUp = null;
    /** Slot color (mirrors the powerup color) for HUD rendering. */
    public int    pendingPowerUpColor = 0;
    /** When the held power-up expires if not activated. */
    public long   pendingPowerUpExpiry = 0;
    public static final long PENDING_POWERUP_TTL_MS = 12_000;

    // ── Equipped cosmetics (purely visual) ───────────────────────────────────
    /** Aura color (parsed from store asset_reference like "#FF4444"). 0 = none. */
    public int    equippedCosmeticColor = 0;
    /**
     * Trail asset reference: "#RRGGBB" hex string, "#RAINBOW", or null.
     * Kept as the raw string so the renderer can interpret "#RAINBOW".
     */
    public String equippedTrailAsset = null;

    /** Recent (x,y) samples used to draw a fading trail behind the player. */
    public final Deque<float[]> trailPoints = new ArrayDeque<>();
    private static final int    TRAIL_MAX_POINTS = 18;
    private static final long   TRAIL_SAMPLE_MS  = 35;
    private long lastTrailSampleTime = 0;

    // Damage cooldown: player cannot be damaged again for 1 second after a hit
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

        // Effective speed accounts for active dash burst.
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

        // Pending powerup expires if not activated in time
        if (pendingPowerUp != null && now > pendingPowerUpExpiry) {
            pendingPowerUp = null;
            pendingPowerUpColor = 0;
        }

        // Sample trail points
        if (now - lastTrailSampleTime >= TRAIL_SAMPLE_MS) {
            lastTrailSampleTime = now;
            trailPoints.addFirst(new float[]{x, y});
            while (trailPoints.size() > TRAIL_MAX_POINTS) trailPoints.removeLast();
        }
    }

    /**
     * Returns true if damage was actually applied.
     * Respects shield and the 1-second damage cooldown.
     */
    public boolean takeDamage() {
        if (hasShield) return false;
        long now = System.currentTimeMillis();
        if (now < damageCooldownEndTime) return false;   // still invulnerable
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

    // ── Dash & power-up actions ──────────────────────────────────────────────

    /**
     * Attempt to dash. Succeeds only if cooldown has elapsed AND the player has
     * a non-zero movement vector to dash along. Returns true if started.
     */
    public boolean tryDash() {
        long now = System.currentTimeMillis();
        if (now < dashCooldownEnd) return false;
        if (vx == 0 && vy == 0) return false;
        dashEndTime     = now + DASH_DURATION_MS;
        dashCooldownEnd = now + DASH_COOLDOWN_MS;
        return true;
    }

    /** True while a dash burst is currently in effect. */
    public boolean isDashing() {
        return System.currentTimeMillis() < dashEndTime;
    }

    /** Remaining dash cooldown as a 0..1 ratio (1 = full cooldown). */
    public float dashCooldownRatio() {
        long now = System.currentTimeMillis();
        long remaining = dashCooldownEnd - now;
        if (remaining <= 0) return 0f;
        return Math.min(1f, remaining / (float) DASH_COOLDOWN_MS);
    }

    /**
     * Activates the held power-up (if any). Returns the activated type, or null
     * if the slot was empty.
     */
    public String activatePowerUp() {
        if (pendingPowerUp == null) return null;
        String t = pendingPowerUp;
        PowerUp.applyType(this, t);
        pendingPowerUp      = null;
        pendingPowerUpColor = 0;
        return t;
    }
}
