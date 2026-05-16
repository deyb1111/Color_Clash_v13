package com.example.colorclash.models;

import android.graphics.Color;

/**
 * Helpers for turning a store item's asset_reference into a drawable color.
 *
 * The store stores cosmetic / trail assets as one of:
 *   - "#RRGGBB"  → a literal hex color
 *   - "#RAINBOW" → animated rainbow handled by rainbowAt(timeMs)
 *   - null       → nothing equipped
 */
public final class CosmeticUtil {
    private CosmeticUtil() {}

    /** Returns true if the asset references the animated rainbow effect. */
    public static boolean isRainbow(String asset) {
        return asset != null && asset.equalsIgnoreCase("#RAINBOW");
    }

    /**
     * Parses a "#RRGGBB" asset into an ARGB int. Returns 0 if asset is null,
     * empty, the special "#RAINBOW" flag, or unparseable.
     */
    public static int parseStaticColor(String asset) {
        if (asset == null || asset.isEmpty()) return 0;
        if (isRainbow(asset)) return 0;
        try {
            return Color.parseColor(asset);
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }

    /**
     * Resolves an asset reference into an ARGB color at the given time.
     * Rainbow assets sweep the hue wheel.
     */
    public static int resolveColor(String asset, long timeMs) {
        if (asset == null || asset.isEmpty()) return 0;
        if (isRainbow(asset)) return rainbowAt(timeMs);
        return parseStaticColor(asset);
    }

    /** Hue sweep used by the Rainbow trail. Cycle ~3 seconds. */
    public static int rainbowAt(long timeMs) {
        float hue = (timeMs % 3000L) / 3000f * 360f;
        return Color.HSVToColor(new float[]{hue, 1f, 1f});
    }
}
