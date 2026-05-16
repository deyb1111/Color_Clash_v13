package com.example.colorclash.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * DatabaseHelper — creates and upgrades the Color Clash SQLite database.
 *
 * Tables
 * ──────
 *   players          — profile, total_points, total_gold, equipped items
 *   store_items      — catalogue of buyable cosmetics and trails
 *   player_inventory — which items a player owns / has equipped
 *   matches          — match history with gold earned per player
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME    = "colorclash.db";
    public static final int    DB_VERSION = 1;

    // ── Table names ──────────────────────────────────────────────────────────
    public static final String TABLE_PLAYERS    = "players";
    public static final String TABLE_ITEMS      = "store_items";
    public static final String TABLE_INVENTORY  = "player_inventory";
    public static final String TABLE_MATCHES    = "matches";

    // ── players columns ──────────────────────────────────────────────────────
    public static final String COL_P_ID          = "id";
    public static final String COL_P_NAME        = "player_name";
    public static final String COL_P_POINTS      = "total_points";
    public static final String COL_P_GOLD        = "total_gold";
    public static final String COL_P_COSMETIC    = "selected_cosmetic_id";
    public static final String COL_P_TRAIL       = "selected_trail_id";
    public static final String COL_P_CREATED     = "created_at";
    public static final String COL_P_UPDATED     = "updated_at";

    // ── store_items columns ──────────────────────────────────────────────────
    public static final String COL_I_ID          = "id";
    public static final String COL_I_NAME        = "item_name";
    public static final String COL_I_TYPE        = "item_type";      // 'cosmetic' | 'trail'
    public static final String COL_I_PRICE       = "price_gold";
    public static final String COL_I_ASSET       = "asset_reference"; // hex color or resource name
    public static final String COL_I_CREATED     = "created_at";

    // ── player_inventory columns ─────────────────────────────────────────────
    public static final String COL_INV_ID        = "id";
    public static final String COL_INV_PLAYER    = "player_id";
    public static final String COL_INV_ITEM      = "item_id";
    public static final String COL_INV_PURCHASED = "purchased_at";
    public static final String COL_INV_EQUIPPED  = "is_equipped";

    // ── matches columns ──────────────────────────────────────────────────────
    public static final String COL_M_ID          = "id";
    public static final String COL_M_P1          = "player1_id";
    public static final String COL_M_P2          = "player2_id";
    public static final String COL_M_P1_PTS      = "player1_points";
    public static final String COL_M_P2_PTS      = "player2_points";
    public static final String COL_M_WINNER      = "winner_id";
    public static final String COL_M_P1_GOLD     = "gold_earned_player1";
    public static final String COL_M_P2_GOLD     = "gold_earned_player2";
    public static final String COL_M_PLAYED      = "played_at";

    // ── CREATE statements ────────────────────────────────────────────────────
    private static final String CREATE_PLAYERS =
        "CREATE TABLE " + TABLE_PLAYERS + " ("
        + COL_P_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + COL_P_NAME     + " TEXT NOT NULL UNIQUE, "
        + COL_P_POINTS   + " INTEGER DEFAULT 0, "
        + COL_P_GOLD     + " INTEGER DEFAULT 0, "
        + COL_P_COSMETIC + " INTEGER DEFAULT NULL, "
        + COL_P_TRAIL    + " INTEGER DEFAULT NULL, "
        + COL_P_CREATED  + " TEXT DEFAULT (datetime('now')), "
        + COL_P_UPDATED  + " TEXT DEFAULT (datetime('now'))"
        + ");";

    private static final String CREATE_STORE_ITEMS =
        "CREATE TABLE " + TABLE_ITEMS + " ("
        + COL_I_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + COL_I_NAME    + " TEXT NOT NULL, "
        + COL_I_TYPE    + " TEXT NOT NULL, "
        + COL_I_PRICE   + " INTEGER NOT NULL, "
        + COL_I_ASSET   + " TEXT NOT NULL, "
        + COL_I_CREATED + " TEXT DEFAULT (datetime('now'))"
        + ");";

    private static final String CREATE_INVENTORY =
        "CREATE TABLE " + TABLE_INVENTORY + " ("
        + COL_INV_ID        + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + COL_INV_PLAYER    + " INTEGER NOT NULL REFERENCES " + TABLE_PLAYERS + "(id), "
        + COL_INV_ITEM      + " INTEGER NOT NULL REFERENCES " + TABLE_ITEMS   + "(id), "
        + COL_INV_PURCHASED + " TEXT DEFAULT (datetime('now')), "
        + COL_INV_EQUIPPED  + " INTEGER DEFAULT 0, "
        + "UNIQUE(" + COL_INV_PLAYER + ", " + COL_INV_ITEM + ")"
        + ");";

    private static final String CREATE_MATCHES =
        "CREATE TABLE " + TABLE_MATCHES + " ("
        + COL_M_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + COL_M_P1      + " INTEGER REFERENCES " + TABLE_PLAYERS + "(id), "
        + COL_M_P2      + " INTEGER REFERENCES " + TABLE_PLAYERS + "(id), "
        + COL_M_P1_PTS  + " INTEGER DEFAULT 0, "
        + COL_M_P2_PTS  + " INTEGER DEFAULT 0, "
        + COL_M_WINNER  + " INTEGER REFERENCES " + TABLE_PLAYERS + "(id), "
        + COL_M_P1_GOLD + " INTEGER DEFAULT 0, "
        + COL_M_P2_GOLD + " INTEGER DEFAULT 0, "
        + COL_M_PLAYED  + " TEXT DEFAULT (datetime('now'))"
        + ");";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PLAYERS);
        db.execSQL(CREATE_STORE_ITEMS);
        db.execSQL(CREATE_INVENTORY);
        db.execSQL(CREATE_MATCHES);
        seedStoreItems(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MATCHES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVENTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYERS);
        onCreate(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ── Seed store items ─────────────────────────────────────────────────────
    private void seedStoreItems(SQLiteDatabase db) {
        // 4 cosmetics (aura/glow colour around player ball)
        insertItem(db, "Crimson Aura",   "cosmetic", 50,  "#FF4444");
        insertItem(db, "Emerald Aura",   "cosmetic", 75,  "#44FF88");
        insertItem(db, "Sapphire Aura",  "cosmetic", 100, "#4488FF");
        insertItem(db, "Golden Aura",    "cosmetic", 150, "#FFDD22");

        // 4 trails (particle colour behind moving player)
        insertItem(db, "Fire Trail",     "trail", 80,  "#FF6600");
        insertItem(db, "Ice Trail",      "trail", 80,  "#88DDFF");
        insertItem(db, "Shadow Trail",   "trail", 120, "#9900FF");
        insertItem(db, "Rainbow Trail",  "trail", 200, "#RAINBOW"); // special flag handled in GameView
    }

    private void insertItem(SQLiteDatabase db, String name, String type, int price, String asset) {
        ContentValues cv = new ContentValues();
        cv.put(COL_I_NAME,  name);
        cv.put(COL_I_TYPE,  type);
        cv.put(COL_I_PRICE, price);
        cv.put(COL_I_ASSET, asset);
        db.insert(TABLE_ITEMS, null, cv);
    }
}
