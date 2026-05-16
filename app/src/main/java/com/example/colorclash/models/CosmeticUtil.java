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

    public static boolean isRainbow(String asset) {
        return asset != null && asset.equalsIgnoreCase("#RAINBOW");
    }

    public static int parseStaticColor(String asset) {
        if (asset == null || asset.isEmpty()) return 0;
        if (isRainbow(asset)) return 0;
        try {
            return Color.parseColor(asset);
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }


    public static int resolveColor(String asset, long timeMs) {
        if (asset == null || asset.isEmpty()) return 0;
        if (isRainbow(asset)) return rainbowAt(timeMs);
        return parseStaticColor(asset);
    }
    public static int rainbowAt(long timeMs) {
        float hue = (timeMs % 3000L) / 3000f * 360f;
        return Color.HSVToColor(new float[]{hue, 1f, 1f});
    }
}
