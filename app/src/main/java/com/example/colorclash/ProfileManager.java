package com.example.colorclash;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages player profiles using SharedPreferences.
 *
 * - Stores the primary player name (set on first launch)
 * - Maintains a list of all known player names (auto-saved when used in
 *   offline mode, multiplayer, or the store)
 */
public class ProfileManager {

    private static final String PREFS_NAME    = "color_clash_profiles";
    private static final String KEY_PRIMARY   = "primary_player_name";
    private static final String KEY_ALL_NAMES = "all_player_names";

    private final SharedPreferences prefs;

    public ProfileManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns true if the first-launch name prompt has NOT been completed yet. */
    public boolean isFirstLaunch() {
        return !prefs.contains(KEY_PRIMARY);
    }

    /** Saves the primary player name (called once on first launch). */
    public void setPrimaryName(String name) {
        prefs.edit().putString(KEY_PRIMARY, name).apply();
        addProfile(name);
    }

    /** Returns the primary player name, or "Guest" if not set. */
    public String getPrimaryName() {
        return prefs.getString(KEY_PRIMARY, "Guest");
    }

    /** Adds a player name to the known profiles list. */
    public void addProfile(String name) {
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        Set<String> names = new HashSet<>(getProfileNames());
        names.add(name);
        prefs.edit().putStringSet(KEY_ALL_NAMES, names).apply();
    }

    /** Returns all known player names, sorted alphabetically. */
    public List<String> getProfileNames() {
        Set<String> raw = prefs.getStringSet(KEY_ALL_NAMES, new HashSet<>());
        List<String> sorted = new ArrayList<>(raw);
        java.util.Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }
}
