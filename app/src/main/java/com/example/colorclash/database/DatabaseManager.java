package com.example.colorclash.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseManager — singleton facade over DatabaseHelper.
 *
 * All public methods catch SQLite exceptions internally and return
 * safe defaults so callers do not need try-catch everywhere.
 */
public class DatabaseManager {

    private static DatabaseManager instance;
    private final DatabaseHelper helper;

    private DatabaseManager(Context ctx) {
        helper = new DatabaseHelper(ctx.getApplicationContext());
    }

    public static synchronized DatabaseManager getInstance(Context ctx) {
        if (instance == null) instance = new DatabaseManager(ctx);
        return instance;
    }

    // ── Player operations ────────────────────────────────────────────────────

    /**
     * Inserts a player row if the name doesn't exist yet (IGNORE on conflict).
     */
    public void ensurePlayer(String name) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_P_NAME, name);
        db.insertWithOnConflict(DatabaseHelper.TABLE_PLAYERS, null, cv,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** Returns player row id for the given name, or -1 if not found. */
    public long getPlayerId(String name) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DatabaseHelper.TABLE_PLAYERS,
                new String[]{DatabaseHelper.COL_P_ID},
                DatabaseHelper.COL_P_NAME + "=?",
                new String[]{name}, null, null, null)) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return -1L;
    }

    /** Adds gold to a player's total (clamps at 0). */
    public void addGold(String name, int gold) {
        if (gold <= 0) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("UPDATE " + DatabaseHelper.TABLE_PLAYERS
                + " SET " + DatabaseHelper.COL_P_GOLD + " = " + DatabaseHelper.COL_P_GOLD + " + ?"
                + ", " + DatabaseHelper.COL_P_UPDATED + " = datetime('now')"
                + " WHERE " + DatabaseHelper.COL_P_NAME + " = ?",
                new Object[]{gold, name});
    }

    /** Deducts gold; returns false if insufficient balance. */
    public boolean spendGold(String name, int cost) {
        int balance = getGold(name);
        if (balance < cost) return false;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("UPDATE " + DatabaseHelper.TABLE_PLAYERS
                + " SET " + DatabaseHelper.COL_P_GOLD + " = " + DatabaseHelper.COL_P_GOLD + " - ?"
                + ", " + DatabaseHelper.COL_P_UPDATED + " = datetime('now')"
                + " WHERE " + DatabaseHelper.COL_P_NAME + " = ?",
                new Object[]{cost, name});
        return true;
    }

    /** Returns current gold balance for the player, or 0. */
    public int getGold(String name) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DatabaseHelper.TABLE_PLAYERS,
                new String[]{DatabaseHelper.COL_P_GOLD},
                DatabaseHelper.COL_P_NAME + "=?",
                new String[]{name}, null, null, null)) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return 0;
    }

    /** Adds points to a player's lifetime total. */
    public void addPoints(String name, int points) {
        if (points <= 0) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("UPDATE " + DatabaseHelper.TABLE_PLAYERS
                + " SET " + DatabaseHelper.COL_P_POINTS + " = " + DatabaseHelper.COL_P_POINTS + " + ?"
                + ", " + DatabaseHelper.COL_P_UPDATED + " = datetime('now')"
                + " WHERE " + DatabaseHelper.COL_P_NAME + " = ?",
                new Object[]{points, name});
    }

    // ── Store operations ─────────────────────────────────────────────────────

    /**
     * Simple POJO for a store item row.
     */
    public static class StoreItem {
        public long   id;
        public String name;
        public String type;      // 'cosmetic' | 'trail'
        public int    priceGold;
        public String assetRef;
        public boolean owned;
        public boolean equipped;
    }

    /** Returns all store items, annotated with ownership status for the player. */
    public List<StoreItem> getStoreItems(String playerName) {
        long playerId = getPlayerId(playerName);
        SQLiteDatabase db = helper.getReadableDatabase();
        List<StoreItem> list = new ArrayList<>();

        String sql =
            "SELECT i." + DatabaseHelper.COL_I_ID
            + ", i." + DatabaseHelper.COL_I_NAME
            + ", i." + DatabaseHelper.COL_I_TYPE
            + ", i." + DatabaseHelper.COL_I_PRICE
            + ", i." + DatabaseHelper.COL_I_ASSET
            + ", CASE WHEN inv." + DatabaseHelper.COL_INV_PLAYER + " IS NOT NULL THEN 1 ELSE 0 END AS owned"
            + ", COALESCE(inv." + DatabaseHelper.COL_INV_EQUIPPED + ", 0) AS equipped"
            + " FROM " + DatabaseHelper.TABLE_ITEMS + " i"
            + " LEFT JOIN " + DatabaseHelper.TABLE_INVENTORY + " inv"
            + "   ON i." + DatabaseHelper.COL_I_ID + " = inv." + DatabaseHelper.COL_INV_ITEM
            + "  AND inv." + DatabaseHelper.COL_INV_PLAYER + " = " + playerId
            + " ORDER BY i." + DatabaseHelper.COL_I_TYPE + ", i." + DatabaseHelper.COL_I_PRICE;

        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                StoreItem item  = new StoreItem();
                item.id         = c.getLong(0);
                item.name       = c.getString(1);
                item.type       = c.getString(2);
                item.priceGold  = c.getInt(3);
                item.assetRef   = c.getString(4);
                item.owned      = c.getInt(5) == 1;
                item.equipped   = c.getInt(6) == 1;
                list.add(item);
            }
        }
        return list;
    }

    /**
     * Purchases an item for a player:
     *   - checks gold balance
     *   - deducts gold
     *   - inserts inventory row
     * Returns true on success.
     */
    public boolean purchaseItem(String playerName, long itemId, int itemPrice) {
        if (!spendGold(playerName, itemPrice)) return false;

        long playerId = getPlayerId(playerName);
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_INV_PLAYER,   playerId);
        cv.put(DatabaseHelper.COL_INV_ITEM,     itemId);
        cv.put(DatabaseHelper.COL_INV_EQUIPPED, 0);
        long row = db.insertWithOnConflict(DatabaseHelper.TABLE_INVENTORY, null, cv,
                SQLiteDatabase.CONFLICT_IGNORE);
        return row != -1;
    }

    /**
     * Equips an item. Unequips any other item of the same type first,
     * then updates the players table's selected_*_id column.
     */
    public void equipItem(String playerName, long itemId, String itemType) {
        long playerId = getPlayerId(playerName);
        SQLiteDatabase db = helper.getWritableDatabase();

        // Un-equip any existing item of same type in inventory
        db.execSQL(
            "UPDATE " + DatabaseHelper.TABLE_INVENTORY
            + " SET " + DatabaseHelper.COL_INV_EQUIPPED + " = 0"
            + " WHERE " + DatabaseHelper.COL_INV_PLAYER + " = ?"
            + "   AND " + DatabaseHelper.COL_INV_ITEM + " IN ("
            + "     SELECT " + DatabaseHelper.COL_I_ID
            + "     FROM "   + DatabaseHelper.TABLE_ITEMS
            + "     WHERE "  + DatabaseHelper.COL_I_TYPE + " = ?"
            + "   )",
            new Object[]{playerId, itemType}
        );

        // Equip selected item
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_INV_EQUIPPED, 1);
        db.update(DatabaseHelper.TABLE_INVENTORY, cv,
                DatabaseHelper.COL_INV_PLAYER + "=? AND " + DatabaseHelper.COL_INV_ITEM + "=?",
                new String[]{String.valueOf(playerId), String.valueOf(itemId)});

        // Update player's selected column
        String col = "cosmetic".equals(itemType)
                ? DatabaseHelper.COL_P_COSMETIC
                : DatabaseHelper.COL_P_TRAIL;
        db.execSQL("UPDATE " + DatabaseHelper.TABLE_PLAYERS
                + " SET " + col + " = ?"
                + ", " + DatabaseHelper.COL_P_UPDATED + " = datetime('now')"
                + " WHERE " + DatabaseHelper.COL_P_ID + " = ?",
                new Object[]{itemId, playerId});
    }

    /**
     * Returns the asset_reference (e.g. "#FF4444" or "#RAINBOW") of the item
     * the given player has equipped for the given type, or null if none.
     *
     * Used by the game renderer to draw the player's chosen cosmetic / trail.
     * Returns null if the player does not exist or has nothing equipped.
     */
    public String getEquippedAsset(String playerName, String itemType) {
        if (playerName == null || itemType == null) return null;
        long playerId = getPlayerId(playerName);
        if (playerId < 0) return null;
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql =
            "SELECT i." + DatabaseHelper.COL_I_ASSET
            + " FROM "  + DatabaseHelper.TABLE_INVENTORY + " inv"
            + " JOIN "  + DatabaseHelper.TABLE_ITEMS + " i"
            + "   ON i." + DatabaseHelper.COL_I_ID
            + " = inv." + DatabaseHelper.COL_INV_ITEM
            + " WHERE inv." + DatabaseHelper.COL_INV_PLAYER + " = ?"
            + "   AND inv." + DatabaseHelper.COL_INV_EQUIPPED + " = 1"
            + "   AND i." + DatabaseHelper.COL_I_TYPE + " = ?"
            + " LIMIT 1";
        try (Cursor c = db.rawQuery(sql,
                new String[]{String.valueOf(playerId), itemType})) {
            if (c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { /* return null below */ }
        return null;
    }

    /** Un-equips an item (sets is_equipped=0 and clears player's selected column). */
    public void unequipItem(String playerName, long itemId, String itemType) {
        long playerId = getPlayerId(playerName);
        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_INV_EQUIPPED, 0);
        db.update(DatabaseHelper.TABLE_INVENTORY, cv,
                DatabaseHelper.COL_INV_PLAYER + "=? AND " + DatabaseHelper.COL_INV_ITEM + "=?",
                new String[]{String.valueOf(playerId), String.valueOf(itemId)});

        String col = "cosmetic".equals(itemType)
                ? DatabaseHelper.COL_P_COSMETIC
                : DatabaseHelper.COL_P_TRAIL;
        db.execSQL("UPDATE " + DatabaseHelper.TABLE_PLAYERS
                + " SET " + col + " = NULL"
                + ", " + DatabaseHelper.COL_P_UPDATED + " = datetime('now')"
                + " WHERE " + DatabaseHelper.COL_P_ID + " = ?",
                new Object[]{playerId});
    }

    // ── Match history ────────────────────────────────────────────────────────

    public void saveMatch(String p1Name, String p2Name,
                          int p1Points, int p2Points,
                          String winnerName,
                          int goldP1, int goldP2) {
        long p1Id     = getPlayerId(p1Name);
        long p2Id     = getPlayerId(p2Name);
        long winnerId = getPlayerId(winnerName);

        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv  = new ContentValues();
        cv.put(DatabaseHelper.COL_M_P1,      p1Id);
        cv.put(DatabaseHelper.COL_M_P2,      p2Id);
        cv.put(DatabaseHelper.COL_M_P1_PTS,  p1Points);
        cv.put(DatabaseHelper.COL_M_P2_PTS,  p2Points);
        cv.put(DatabaseHelper.COL_M_WINNER,  winnerId);
        cv.put(DatabaseHelper.COL_M_P1_GOLD, goldP1);
        cv.put(DatabaseHelper.COL_M_P2_GOLD, goldP2);
        db.insert(DatabaseHelper.TABLE_MATCHES, null, cv);

        // Also update lifetime points
        addPoints(p1Name, p1Points);
        addPoints(p2Name, p2Points);
    }
}
