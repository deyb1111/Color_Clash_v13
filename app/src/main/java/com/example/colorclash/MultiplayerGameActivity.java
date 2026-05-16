package com.example.colorclash;

import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import com.example.colorclash.database.DatabaseManager;
import com.example.colorclash.models.CosmeticUtil;
import com.example.colorclash.models.Player;
import com.example.colorclash.models.PowerUp;
import com.example.colorclash.network.GameClient;
import com.example.colorclash.network.GameServer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MultiplayerGameActivity extends AppCompatActivity
        implements SurfaceHolder.Callback {

    private static final String TAG = "MPGameActivity";

    public static volatile GameServer sharedServer;
    public static volatile GameClient sharedClient;

    private static final float WORLD_W    = 1220f;
    private static final float WORLD_H    =  686f;
    private static final float HUD_TOP    =  48f;
    private static final float HUD_SIDE   =  24f;
    private static final float JOY_CX     = 180f;
    private static final float JOY_CY     = WORLD_H - 150f;
    private static final float JOY_RADIUS = 130f;
    private static final float JOY_KNOB   =  48f;
    private static final float PLAYER_R   =  55f;


    private static final float BTN_RADIUS = 60f;
    private static final float BTN_DASH_CX = WORLD_W - 110f;
    private static final float BTN_DASH_CY = WORLD_H - 110f;
    private static final float BTN_ACT_CX  = WORLD_W - 250f;
    private static final float BTN_ACT_CY  = WORLD_H - 110f;


    private static final int PLAYER1_DRAW_COLOR = 0xFFFF4444;  // reddish
    private static final int PLAYER2_DRAW_COLOR = 0xFF4488FF;  // bluish

    private boolean isHost;
    private String  myName;
    private volatile String opponentName = "Opponent";

    private String myCosmetic = "";
    private String myTrail    = "";

    private volatile String opponentCosmetic = "";
    private volatile String opponentTrail    = "";

    private GameServer server;
    private GameClient client;
    private static final int STATE_SEND_MS = 50;
    private long lastStateSend = 0;

    private Player        hostP1, hostP2;
    private List<PowerUp> powerUps = new ArrayList<>();
    private Random        random   = new Random();

    private final int[] bgColors  = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
    private int         bgIndex   = 0;
    private long        nextBgChange;
    private float       bgTimer   = 10f;

    private volatile float   clientVx = 0, clientVy = 0;
    private volatile boolean gameOver = false;

    private final AtomicBoolean gameOverNavigated = new AtomicBoolean(false);

    private volatile float r_p1nx   = 0.25f, r_p1ny = 0.5f;
    private volatile float r_p2nx   = 0.75f, r_p2ny = 0.5f;
    private volatile int   r_p1lives = 5,     r_p2lives = 5;
    private volatile int   r_p1score = 0,     r_p2score = 0;
    private volatile int   r_bgIndex = 0;
    private volatile float r_timer   = 10f;

    private volatile String r_p1name  = "";
    private volatile String r_p2name  = "";
    private volatile String r_p1cos   = "";
    private volatile String r_p2cos   = "";
    private volatile String r_p1trail = "";
    private volatile String r_p2trail = "";

    private volatile String r_p1pending     = "";
    private volatile int    r_p1pendingCol  = 0;
    private volatile String r_p2pending     = "";
    private volatile int    r_p2pendingCol  = 0;
    private volatile boolean r_p1dashing    = false;
    private volatile boolean r_p2dashing    = false;

    private volatile long localDashCooldownEnd = 0;


    private static final class PUSnapshot {
        float x, y;
        String type;
        int color;
        PUSnapshot(float x, float y, String type, int color) {
            this.x = x; this.y = y; this.type = type; this.color = color;
        }
    }
    private final CopyOnWriteArrayList<PUSnapshot> clientPowerUps = new CopyOnWriteArrayList<>();

    private float   joyX = JOY_CX, joyY = JOY_CY;
    private float   joyCX = JOY_CX, joyCY = JOY_CY;
    private boolean joying = false;
    private int     joyPtr = -1;
    private float   myVx = 0, myVy = 0;

    private float scale = 1f, offsetX = 0f, offsetY = 0f;

    private SurfaceView   surface;
    private SurfaceHolder holder;
    private Thread        gameThread;
    private volatile boolean running      = false;
    private volatile boolean surfaceReady = false;
    private final Object     threadLock   = new Object();
    private int canvasW = 0, canvasH = 0;

    private final Paint paint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int[]    fullBg     = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
    private final int[]    overlays   = {
            Color.argb(110, 60, 0,  0),  Color.argb(110, 0, 50, 0),
            Color.argb(110, 0, 0,  60),  Color.argb(110, 60, 55, 0) };
    private final String[] colorNames = {"RED", "GREEN", "BLUE", "YELLOW"};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        setContentView(R.layout.activity_multiplayer_game);

        String nameExtra = getIntent().getStringExtra("player_name");
        myName = (nameExtra != null && !nameExtra.trim().isEmpty()) ? nameExtra.trim() : "Player";
        isHost = "HOST".equals(getIntent().getStringExtra("role"));
        Log.d(TAG, "onCreate role=" + (isHost ? "HOST" : "CLIENT") + " name=" + myName);

        try {
            DatabaseManager db = DatabaseManager.getInstance(getApplicationContext());
            db.ensurePlayer(myName);
            String cos  = db.getEquippedAsset(myName, "cosmetic");
            String tr   = db.getEquippedAsset(myName, "trail");
            myCosmetic = cos != null ? cos : "";
            myTrail    = tr  != null ? tr  : "";
        } catch (Exception ignored) {}

        textPaint.setFakeBoldText(true);
        surface = findViewById(R.id.mp_game_surface);
        holder  = surface.getHolder();
        holder.addCallback(this);
        surface.setOnTouchListener((v, event) -> { handleTouch(event); return true; });

        if (isHost) setupHost();
        else        setupClient();
    }


    private void setupHost() {
        server = sharedServer;
        sharedServer = null;
        if (server == null) {
            Log.e(TAG, "setupHost: sharedServer is null!");
            mainHandler.post(() -> { if (!isFinishing()) finish(); });
            return;
        }
        server.setListener(new GameServer.ServerListener() {
            @Override public void onClientConnected() {}
            @Override public void onInputReceived(float vx, float vy) {
                clientVx = vx;
                clientVy = vy;
            }
            @Override public void onDashRequest() {
                if (hostP2 != null) hostP2.tryDash();
            }
            @Override public void onActivateRequest() {
                if (hostP2 != null) hostP2.activatePowerUp();
            }
            @Override public void onProfileReceived(String name, String cosmetic, String trail) {
                if (name != null && !name.trim().isEmpty()) {
                    opponentName = name.trim();
                    if (hostP2 != null) hostP2.name = opponentName;
                }
                opponentCosmetic = cosmetic != null ? cosmetic : "";
                opponentTrail    = trail    != null ? trail    : "";
                if (hostP2 != null) {
                    hostP2.equippedCosmeticColor = CosmeticUtil.parseStaticColor(opponentCosmetic);
                    hostP2.equippedTrailAsset    = opponentTrail.isEmpty() ? null : opponentTrail;
                }
                Log.d(TAG, "Opponent profile: " + opponentName
                        + " cos=" + opponentCosmetic + " trail=" + opponentTrail);
            }
            @Override public void onClientDisconnected() {
                Log.d(TAG, "Host: client disconnected");
                mainHandler.post(() -> {
                    if (!isFinishing() && !gameOverNavigated.get()) finish();
                });
            }
            @Override public void onError(String message) {
                Log.e(TAG, "Host server error: " + message);
            }
        });
        Log.d(TAG, "Host setup done");
    }


    private void setupClient() {
        client = sharedClient;
        sharedClient = null;
        if (client == null) {
            Log.e(TAG, "setupClient: sharedClient is null!");
            mainHandler.post(() -> { if (!isFinishing()) finish(); });
            return;
        }
        client.setListener(new GameClient.ClientListener() {
            @Override public void onConnected() {
                client.sendProfile(myName, myCosmetic, myTrail);
            }
            @Override public void onStateReceived(JSONObject state) { applyState(state); }
            @Override public void onGameOver(String w, String l, int ws, int ls) {
                mainHandler.post(() -> navigateToGameOver(w, l, ws, ls));
            }
            @Override public void onDisconnected() {
                Log.d(TAG, "Client: host disconnected");
                mainHandler.post(() -> {
                    if (!isFinishing() && !gameOverNavigated.get()) finish();
                });
            }
            @Override public void onError(String msg) {
                Log.e(TAG, "Client error: " + msg);
            }
        });
        Log.d(TAG, "Client setup done");
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        canvasW = surface.getWidth();
        canvasH = surface.getHeight();
        if (canvasW == 0 || canvasH == 0) { canvasW = 1220; canvasH = 686; }
        Log.d(TAG, "surfaceCreated " + canvasW + "x" + canvasH);

        if (isHost && hostP1 == null) {
            // Use the same draw colors so collision color-matching works
            hostP1 = new Player(myName,       PLAYER1_DRAW_COLOR, WORLD_W * 0.25f, WORLD_H * 0.5f);
            hostP2 = new Player(opponentName, PLAYER2_DRAW_COLOR, WORLD_W * 0.75f, WORLD_H * 0.5f);
            hostP1.radius = PLAYER_R;
            hostP2.radius = PLAYER_R;
            // Apply the host's own equipped cosmetics (opponent's are filled in
            // when the client sends its PROFILE packet).
            hostP1.equippedCosmeticColor = CosmeticUtil.parseStaticColor(myCosmetic);
            hostP1.equippedTrailAsset    = myTrail.isEmpty() ? null : myTrail;
            if (!opponentCosmetic.isEmpty()) {
                hostP2.equippedCosmeticColor = CosmeticUtil.parseStaticColor(opponentCosmetic);
            }
            if (!opponentTrail.isEmpty()) {
                hostP2.equippedTrailAsset = opponentTrail;
            }
            nextBgChange  = System.currentTimeMillis() + 10000;
        }

        surfaceReady = true;
        startGameThread();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int ht) {
        canvasW = w; canvasH = ht;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        Log.d(TAG, "surfaceDestroyed");
        surfaceReady = false;
        stopGameThread();
    }

    private void startGameThread() {
        synchronized (threadLock) {
            if (running) return;
            running  = true;
            lastTime = System.currentTimeMillis();
            gameThread = new Thread(this::gameLoop, "MPGameLoop");
            gameThread.setDaemon(true);
            gameThread.start();
            Log.d(TAG, "Game thread started");
        }
    }

    private void stopGameThread() {
        synchronized (threadLock) { running = false; }
        if (gameThread != null) {
            try { gameThread.join(500); } catch (InterruptedException ignored) {}
            gameThread = null;
        }
    }

    private long lastTime = 0;

    private void gameLoop() {
        lastTime = System.currentTimeMillis();
        while (running) {
            long now   = System.currentTimeMillis();
            long delta = Math.min(now - lastTime, 100);
            lastTime   = now;

            if (isHost) {
                hostUpdate(delta);
                syncRenderState();
                if (now - lastStateSend >= STATE_SEND_MS) {
                    sendStateToClient();
                    lastStateSend = now;
                }
            }

            render();
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
        Log.d(TAG, "gameLoop exited");
    }

    private void hostUpdate(long dt) {
        if (gameOver || hostP1 == null || hostP2 == null) return;

        hostP1.vx = myVx;     hostP1.vy = myVy;
        hostP2.vx = clientVx; hostP2.vy = clientVy;

        hostP1.update(dt, (int) WORLD_W, (int) WORLD_H);
        hostP2.update(dt, (int) WORLD_W, (int) WORLD_H);

        long now = System.currentTimeMillis();
        if (now > nextBgChange) {
            bgIndex      = pickNextBgIndex(bgIndex);
            nextBgChange = now + 10000;
            bgTimer      = 10f;
            Log.d(TAG, "bgIndex changed to " + bgIndex + " (" + colorNames[bgIndex] + ")");
        } else {
            bgTimer = (nextBgChange - now) / 1000f;
        }

        checkHostCollisions();
        spawnPowerUps();

        if (hostP1.lives <= 0 || hostP2.lives <= 0) {
            gameOver = true;
            Player winner = hostP1.lives > 0 ? hostP1 : hostP2;
            Player loser  = hostP1.lives > 0 ? hostP2 : hostP1;
            Log.d(TAG, "GAME OVER — winner=" + winner.name);
            if (server != null)
                server.sendGameOver(winner.name, loser.name, winner.score, loser.score);
            mainHandler.post(() -> navigateToGameOver(
                    winner.name, loser.name, winner.score, loser.score));
        }
    }

    private int pickNextBgIndex(int current) {
        int next;
        do { next = random.nextInt(bgColors.length); } while (next == current);
        return next;
    }

    private void checkHostCollisions() {
        if (hostP1.collidesWith(hostP2)) {
            boolean p1Attacks = (bgIndex == 0); // RED bg → P1 (red circle) attacks
            boolean p2Attacks = (bgIndex == 2); // BLUE bg → P2 (blue circle) attacks

            if (p1Attacks) {
                if (hostP2.takeDamage()) {
                    hostP1.score += 100;
                    pushBack(hostP2, hostP1);
                    Log.d(TAG, "P1 damages P2 | P1score=" + hostP1.score
                            + " P2lives=" + hostP2.lives);
                }
            } else if (p2Attacks) {
                if (hostP1.takeDamage()) {
                    hostP2.score += 100;
                    pushBack(hostP1, hostP2);
                    Log.d(TAG, "P2 damages P1 | P2score=" + hostP2.score
                            + " P1lives=" + hostP1.lives);
                }
            }
        }


        Iterator<PowerUp> it = powerUps.iterator();
        while (it.hasNext()) {
            PowerUp p = it.next();
            if (p.collidesWith(hostP1)) {
                collectPowerUp(hostP1, p);
                Log.d(TAG, "P1 stored powerup " + p.type);
                it.remove();
            } else if (p.collidesWith(hostP2)) {
                collectPowerUp(hostP2, p);
                Log.d(TAG, "P2 stored powerup " + p.type);
                it.remove();
            }
        }
        Iterator<PowerUp> exp = powerUps.iterator();
        while (exp.hasNext()) {
            if (exp.next().isExpired()) exp.remove();
        }
    }

    private void collectPowerUp(Player p, PowerUp pu) {
        p.pendingPowerUp       = pu.type;
        p.pendingPowerUpColor  = pu.color;
        p.pendingPowerUpExpiry = System.currentTimeMillis() + Player.PENDING_POWERUP_TTL_MS;
        p.score               += 50;
    }

    private void spawnPowerUps() {
        if (random.nextFloat() < 0.003f && powerUps.size() < 3) {
            float px = 100 + random.nextFloat() * (WORLD_W - 200);
            float py =  80 + random.nextFloat() * (WORLD_H - 160);
            PowerUp pu = new PowerUp(px, py);
            powerUps.add(pu);
            Log.d(TAG, "Spawned powerup " + pu.type + " at (" + px + "," + py + ")");
        }
    }

    private void pushBack(Player defender, Player attacker) {
        float dx = defender.x - attacker.x, dy = defender.y - attacker.y;
        float d  = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > 0) { defender.x += (dx / d) * 150; defender.y += (dy / d) * 150; }
        defender.x = Math.max(PLAYER_R, Math.min(WORLD_W - PLAYER_R, defender.x));
        defender.y = Math.max(PLAYER_R, Math.min(WORLD_H - PLAYER_R, defender.y));
    }

    private void syncRenderState() {
        r_p1nx    = hostP1.x / WORLD_W;  r_p1ny    = hostP1.y / WORLD_H;
        r_p2nx    = hostP2.x / WORLD_W;  r_p2ny    = hostP2.y / WORLD_H;
        r_p1lives = hostP1.lives;         r_p2lives = hostP2.lives;
        r_p1score = hostP1.score;         r_p2score = hostP2.score;
        r_bgIndex = bgIndex;              r_timer   = bgTimer;
        if (hostP2.name != null && !hostP2.name.isEmpty()) opponentName = hostP2.name;
        r_p1name  = hostP1.name != null ? hostP1.name : "";
        r_p2name  = hostP2.name != null ? hostP2.name : "";
        r_p1cos   = myCosmetic;
        r_p2cos   = opponentCosmetic;
        r_p1trail = myTrail;
        r_p2trail = opponentTrail;
        r_p1pending    = hostP1.pendingPowerUp != null ? hostP1.pendingPowerUp : "";
        r_p1pendingCol = hostP1.pendingPowerUpColor;
        r_p2pending    = hostP2.pendingPowerUp != null ? hostP2.pendingPowerUp : "";
        r_p2pendingCol = hostP2.pendingPowerUpColor;
        r_p1dashing    = hostP1.isDashing();
        r_p2dashing    = hostP2.isDashing();
    }


    private void sendStateToClient() {
        if (server == null) return;
        server.sendFullGameState(
                r_p1nx, r_p1ny, r_p2nx, r_p2ny,
                r_p1lives, r_p2lives, r_p1score, r_p2score,
                r_bgIndex, r_timer, powerUps,
                r_p1name, r_p2name,
                r_p1cos,  r_p2cos,
                r_p1trail, r_p2trail,
                r_p1pending, r_p1pendingCol,
                r_p2pending, r_p2pendingCol,
                r_p1dashing, r_p2dashing);
    }
    private void applyState(JSONObject j) {
        try {
            r_p1nx    = (float) j.optDouble("p1nx",   r_p1nx);
            r_p1ny    = (float) j.optDouble("p1ny",   r_p1ny);
            r_p2nx    = (float) j.optDouble("p2nx",   r_p2nx);
            r_p2ny    = (float) j.optDouble("p2ny",   r_p2ny);
            r_p1lives = j.optInt("p1lives", r_p1lives);
            r_p2lives = j.optInt("p2lives", r_p2lives);
            r_p1score = j.optInt("p1score", r_p1score);
            r_p2score = j.optInt("p2score", r_p2score);
            int bg    = j.optInt("bgIndex", r_bgIndex);
            r_bgIndex = (bg >= 0 && bg < fullBg.length) ? bg : 0;
            r_timer   = (float) j.optDouble("timer",  r_timer);

            JSONArray puArray = j.optJSONArray("powerups");
            List<PUSnapshot> newList = new ArrayList<>();
            if (puArray != null) {
                for (int i = 0; i < puArray.length(); i++) {
                    JSONObject o = puArray.optJSONObject(i);
                    if (o == null) continue;
                    float  px    = (float) o.optDouble("x",    0);
                    float  py    = (float) o.optDouble("y",    0);
                    String ptype = o.optString("type",  "?");
                    int    pcol  = o.optInt("color",   Color.WHITE);
                    newList.add(new PUSnapshot(px, py, ptype, pcol));
                }
                Log.v(TAG, "Client received " + newList.size() + " powerups");
            }
            clientPowerUps.clear();
            clientPowerUps.addAll(newList);

            String s;
            s = j.optString("p1name",  ""); if (!s.isEmpty()) r_p1name  = s;
            s = j.optString("p2name",  ""); if (!s.isEmpty()) r_p2name  = s;
            r_p1cos        = j.optString("p1cos",       r_p1cos);
            r_p2cos        = j.optString("p2cos",       r_p2cos);
            r_p1trail      = j.optString("p1trail",     r_p1trail);
            r_p2trail      = j.optString("p2trail",     r_p2trail);
            r_p1pending    = j.optString("p1pending",   "");
            r_p2pending    = j.optString("p2pending",   "");
            r_p1pendingCol = j.optInt(   "p1pendingCol", 0);
            r_p2pendingCol = j.optInt(   "p2pendingCol", 0);
            r_p1dashing    = j.optBoolean("p1dashing", false);
            r_p2dashing    = j.optBoolean("p2dashing", false);

            if (!r_p1name.isEmpty() && !r_p1name.equals(opponentName)) {
                opponentName = r_p1name;
            }

        } catch (Exception e) {
            Log.w(TAG, "applyState: " + e.getMessage());
        }
    }


    private boolean canSendInput() {
        return !isFinishing() && client != null && !client.isStopped();
    }

    private void handleTouch(MotionEvent ev) {
        if (isFinishing()) return;
        if (!isHost && (client == null || client.isStopped())) return;

        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pIdx = ev.getActionIndex();
                if (pIdx < 0 || pIdx >= ev.getPointerCount()) break;
                float wx0 = screenToWorldX(ev.getX(pIdx));
                float wy0 = screenToWorldY(ev.getY(pIdx));

                if (inButton(wx0, wy0, BTN_DASH_CX, BTN_DASH_CY)) {
                    requestLocalDash();
                    break;
                }
                if (inButton(wx0, wy0, BTN_ACT_CX,  BTN_ACT_CY)) {
                    requestLocalActivate();
                    break;
                }

                if (!joying) {
                    joying = true;
                    joyPtr = ev.getPointerId(pIdx);
                    joyCX = wx0; joyCY = wy0; joyX = wx0; joyY = wy0;
                    updateJoy(wx0, wy0);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    if (ev.getPointerId(i) == joyPtr) {
                        updateJoy(screenToWorldX(ev.getX(i)), screenToWorldY(ev.getY(i)));
                        break;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int pIdx = ev.getActionIndex();
                if (pIdx < 0 || pIdx >= ev.getPointerCount()) break;
                if (ev.getPointerId(pIdx) == joyPtr) releaseJoy();
                break;
            }
        }
    }

    private float screenToWorldX(float sx) { return (sx - offsetX) / scale; }
    private float screenToWorldY(float sy) { return (sy - offsetY) / scale; }

    private static boolean inButton(float wx, float wy, float cx, float cy) {
        float dx = wx - cx, dy = wy - cy;
        return (dx * dx + dy * dy) <= BTN_RADIUS * BTN_RADIUS;
    }

    private void requestLocalDash() {
        if (isHost) {
            if (hostP1 != null && hostP1.tryDash()) {
                localDashCooldownEnd = hostP1.dashCooldownEnd;
            }
        } else if (client != null && canSendInput()) {
            client.sendDash();
            long now = System.currentTimeMillis();
            if (now >= localDashCooldownEnd) {
                localDashCooldownEnd = now + Player.DASH_COOLDOWN_MS;
            }
        }
    }

    private void requestLocalActivate() {
        if (isHost) {
            if (hostP1 != null) hostP1.activatePowerUp();
        } else if (client != null && canSendInput()) {
            client.sendActivate();
        }
    }

    private void updateJoy(float wx, float wy) {
        float dx = wx - joyCX, dy = wy - joyCY;
        float d  = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > JOY_RADIUS) { dx = dx / d * JOY_RADIUS; dy = dy / d * JOY_RADIUS; d = JOY_RADIUS; }
        joyX = joyCX + dx;
        joyY = joyCY + dy;
        myVx = (d > 15) ? dx / JOY_RADIUS * Player.MAX_SPEED : 0;
        myVy = (d > 15) ? dy / JOY_RADIUS * Player.MAX_SPEED : 0;
        if (!isHost && canSendInput()) client.sendInput(myVx, myVy);
    }

    private void releaseJoy() {
        joying = false; joyPtr = -1;
        myVx = 0; myVy = 0;
        joyX = JOY_CX; joyY = JOY_CY; joyCX = JOY_CX; joyCY = JOY_CY;
        if (!isHost && canSendInput()) client.sendInput(0, 0);
    }

    private void render() {
        if (!surfaceReady || holder == null || canvasW == 0 || canvasH == 0) return;
        int bg = (r_bgIndex >= 0 && r_bgIndex < fullBg.length) ? r_bgIndex : 0;

        float p1wx = r_p1nx * WORLD_W, p1wy = r_p1ny * WORLD_H;
        float p2wx = r_p2nx * WORLD_W, p2wy = r_p2ny * WORLD_H;

        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas == null) return;

            scale   = Math.min((float) canvasW / WORLD_W, (float) canvasH / WORLD_H);
            offsetX = (canvasW - WORLD_W * scale) / 2f;
            offsetY = (canvasH - WORLD_H * scale) / 2f;

            canvas.drawColor(Color.BLACK);
            canvas.save();
            canvas.translate(offsetX, offsetY);
            canvas.scale(scale, scale);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fullBg[bg]);
            canvas.drawRect(0, 0, WORLD_W, WORLD_H, paint);
            paint.setColor(overlays[bg]);
            canvas.drawRect(0, 0, WORLD_W, WORLD_H, paint);

            if (isHost) {
                for (PowerUp pu : powerUps) pu.draw(canvas, paint);
            } else {
                for (PUSnapshot pu : clientPowerUps) {
                    drawPowerUpSnapshot(canvas, pu);
                }
            }

            String p1TrailAsset = !r_p1trail.isEmpty() ? r_p1trail : (isHost ? myTrail : opponentTrail);
            String p2TrailAsset = !r_p2trail.isEmpty() ? r_p2trail : (isHost ? opponentTrail : myTrail);
            updateTrail(p1TrailHistory, p1wx, p1wy);
            updateTrail(p2TrailHistory, p2wx, p2wy);
            drawTrail(canvas, p1TrailHistory, p1TrailAsset);
            drawTrail(canvas, p2TrailHistory, p2TrailAsset);

            String p1Name = !r_p1name.isEmpty() ? r_p1name : (isHost ? myName : opponentName);
            String p2Name = !r_p2name.isEmpty() ? r_p2name : (isHost ? opponentName : myName);
            String p1CosAsset = !r_p1cos.isEmpty() ? r_p1cos : (isHost ? myCosmetic : opponentCosmetic);
            String p2CosAsset = !r_p2cos.isEmpty() ? r_p2cos : (isHost ? opponentCosmetic : myCosmetic);
            int    p1CosCol   = CosmeticUtil.parseStaticColor(p1CosAsset);
            int    p2CosCol   = CosmeticUtil.parseStaticColor(p2CosAsset);

            drawPlayer(canvas, p1wx, p1wy, PLAYER_R, PLAYER1_DRAW_COLOR,
                    p1Name, isHost, p1CosCol, r_p1dashing);
            drawPlayer(canvas, p2wx, p2wy, PLAYER_R, PLAYER2_DRAW_COLOR,
                    p2Name, !isHost, p2CosCol, r_p2dashing);

            drawHUD(canvas, bg);
            drawJoystick(canvas);
            drawActionButtons(canvas);

            canvas.restore();

        } finally {
            if (canvas != null) {
                try { holder.unlockCanvasAndPost(canvas); }
                catch (Exception e) { Log.e(TAG, "unlockCanvas: " + e.getMessage()); }
            }
        }
    }

    private void drawPowerUpSnapshot(Canvas canvas, PUSnapshot pu) {
        float pulse = (float) Math.sin(System.currentTimeMillis() * 0.008) * 8;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pu.color);
        canvas.drawCircle(pu.x, pu.y, 40f + pulse, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(28f);
        paint.setFakeBoldText(true);
        float tw = paint.measureText(pu.type);
        canvas.drawText(pu.type, pu.x - tw / 2, pu.y + 10, paint);
    }

    private void drawPlayer(Canvas c, float x, float y, float r,
                            int color, String name, boolean isMe,
                            int cosmeticColor, boolean dashing) {
        if (cosmeticColor != 0) {
            int cr = Color.red(cosmeticColor);
            int cg = Color.green(cosmeticColor);
            int cb = Color.blue(cosmeticColor);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(80, cr, cg, cb));
            c.drawCircle(x, y, r + 22, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            paint.setColor(Color.argb(220, cr, cg, cb));
            c.drawCircle(x, y, r + 22, paint);
        }
        if (dashing) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(110, 255, 255, 120));
            c.drawCircle(x, y, r + 30, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(x, y, r, paint);

        paint.setColor(Color.argb(60, 255, 255, 255));
        c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.4f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(isMe ? Color.WHITE : Color.argb(120, 0, 0, 0));
        c.drawCircle(x, y, r, paint);

        String label = (name != null && !name.isEmpty()) ? name : "?";
        textPaint.setTextSize(28f);
        textPaint.setColor(Color.argb(100, 0, 0, 0));
        float nw = textPaint.measureText(label);
        c.drawText(label, x - nw / 2 + 2, y - r - 12, textPaint);
        textPaint.setColor(Color.WHITE);
        c.drawText(label, x - nw / 2,     y - r - 14, textPaint);
    }

    private final ArrayDeque<float[]> p1TrailHistory = new ArrayDeque<>();
    private final ArrayDeque<float[]> p2TrailHistory = new ArrayDeque<>();
    private long lastP1TrailSample = 0, lastP2TrailSample = 0;
    private static final int  TRAIL_MAX = 16;
    private static final long TRAIL_SAMPLE_MS = 38;

    private void updateTrail(ArrayDeque<float[]> history, float x, float y) {
        long now = System.currentTimeMillis();
        long lastSample;
        if (history == p1TrailHistory) { lastSample = lastP1TrailSample; }
        else                           { lastSample = lastP2TrailSample; }
        if (now - lastSample < TRAIL_SAMPLE_MS) return;
        if (history == p1TrailHistory) lastP1TrailSample = now;
        else                            lastP2TrailSample = now;
        history.addFirst(new float[]{x, y});
        while (history.size() > TRAIL_MAX) history.removeLast();
    }

    private void drawTrail(Canvas c, ArrayDeque<float[]> history, String asset) {
        if (asset == null || asset.isEmpty() || history.isEmpty()) return;
        boolean rainbow = CosmeticUtil.isRainbow(asset);
        int baseCol = rainbow ? 0 : CosmeticUtil.parseStaticColor(asset);
        long now = System.currentTimeMillis();
        int i = 0, n = history.size();
        paint.setStyle(Paint.Style.FILL);
        for (float[] pt : history) {
            float t = 1f - (i / (float) n);
            int alpha = (int) (170 * t);
            float rr = PLAYER_R * (0.85f - 0.55f * (1f - t));
            int col = rainbow ? CosmeticUtil.rainbowAt(now - i * 38L) : baseCol;
            paint.setColor(Color.argb(alpha,
                    Color.red(col), Color.green(col), Color.blue(col)));
            c.drawCircle(pt[0], pt[1], rr, paint);
            i++;
        }
    }

    private void drawHUD(Canvas c, int bg) {
        String p1Name = isHost ? myName : opponentName;
        String p2Name = isHost ? opponentName : myName;

        textPaint.setTextSize(32f); textPaint.setColor(0xFFFF8888);
        c.drawText(p1Name + ": " + r_p1score + " pts", HUD_SIDE, HUD_TOP, textPaint);
        textPaint.setTextSize(26f); textPaint.setColor(Color.argb(230, 255, 80, 80));
        c.drawText(repeat("♥ ", Math.max(0, r_p1lives)), HUD_SIDE, HUD_TOP + 34, textPaint);

        textPaint.setTextSize(32f); textPaint.setColor(0xFF8888FF);
        String sc2 = p2Name + ": " + r_p2score + " pts";
        c.drawText(sc2, WORLD_W - HUD_SIDE - textPaint.measureText(sc2), HUD_TOP, textPaint);
        textPaint.setTextSize(26f); textPaint.setColor(Color.argb(230, 255, 80, 80));
        String h2 = repeat("♥ ", Math.max(0, r_p2lives));
        c.drawText(h2, WORLD_W - HUD_SIDE - textPaint.measureText(h2), HUD_TOP + 34, textPaint);

        String ts = String.format("%.1f", Math.max(0f, r_timer));
        textPaint.setTextSize(60f); textPaint.setColor(Color.WHITE);
        c.drawText(ts, WORLD_W / 2f - textPaint.measureText(ts) / 2f, HUD_TOP + 8, textPaint);

        textPaint.setTextSize(22f); textPaint.setColor(Color.argb(210, 255, 240, 80));
        String lbl = colorNames[bg] + " ATTACKS";
        c.drawText(lbl, WORLD_W / 2f - textPaint.measureText(lbl) / 2f, HUD_TOP + 42, textPaint);
    }

    private void drawJoystick(Canvas c) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(joying ? Color.argb(180, 255, 255, 255) : Color.argb(60, 255, 255, 255));
        c.drawCircle(joyCX, joyCY, JOY_RADIUS, paint);
        paint.setColor(joying ? Color.argb(60, 255, 255, 255) : Color.argb(22, 255, 255, 255));
        c.drawCircle(joyCX, joyCY, JOY_RADIUS * 0.5f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(joying ? Color.argb(220, 255, 255, 255) : Color.argb(70, 255, 255, 255));
        c.drawCircle(joyX, joyY, JOY_KNOB, paint);
        paint.setColor(joying ? Color.argb(130, 255, 255, 255) : Color.argb(30, 255, 255, 255));
        c.drawCircle(joyX - JOY_KNOB * 0.25f, joyY - JOY_KNOB * 0.25f, JOY_KNOB * 0.4f, paint);
    }

    private String localPendingType()  { return isHost ? r_p1pending    : r_p2pending; }
    private int    localPendingColor() { return isHost ? r_p1pendingCol : r_p2pendingCol; }

    private void drawActionButtons(Canvas c) {
        // Dash
        long now = System.currentTimeMillis();
        long cdEnd = (isHost && hostP1 != null) ? hostP1.dashCooldownEnd : localDashCooldownEnd;
        long remaining = cdEnd - now;
        float cd = remaining <= 0 ? 0f
                : Math.min(1f, remaining / (float) Player.DASH_COOLDOWN_MS);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(cd > 0 ? Color.argb(120, 90, 90, 120)
                              : Color.argb(190, 255, 200, 60));
        c.drawCircle(BTN_DASH_CX, BTN_DASH_CY, BTN_RADIUS, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.argb(220, 255, 255, 255));
        c.drawCircle(BTN_DASH_CX, BTN_DASH_CY, BTN_RADIUS, paint);
        if (cd > 0) {
            RectF arc = new RectF(BTN_DASH_CX - BTN_RADIUS, BTN_DASH_CY - BTN_RADIUS,
                                  BTN_DASH_CX + BTN_RADIUS, BTN_DASH_CY + BTN_RADIUS);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(160, 0, 0, 0));
            c.drawArc(arc, -90f, 360f * cd, true, paint);
        }
        textPaint.setTextSize(22f);
        textPaint.setColor(Color.WHITE);
        String dl = "DASH";
        c.drawText(dl, BTN_DASH_CX - textPaint.measureText(dl) / 2f, BTN_DASH_CY + 8f, textPaint);

        // Activate (power-up slot)
        String pType = localPendingType();
        boolean has  = pType != null && !pType.isEmpty();
        paint.setStyle(Paint.Style.FILL);
        if (has) {
            int col = localPendingColor() != 0
                    ? localPendingColor()
                    : PowerUp.colorForType(pType);
            paint.setColor(Color.argb(220,
                    Color.red(col), Color.green(col), Color.blue(col)));
        } else {
            paint.setColor(Color.argb(90, 100, 100, 130));
        }
        c.drawCircle(BTN_ACT_CX, BTN_ACT_CY, BTN_RADIUS, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.argb(220, 255, 255, 255));
        c.drawCircle(BTN_ACT_CX, BTN_ACT_CY, BTN_RADIUS, paint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(18f);
        String al = has ? pType : "EMPTY";
        c.drawText(al, BTN_ACT_CX - textPaint.measureText(al) / 2f, BTN_ACT_CY + 6f, textPaint);
    }

    private String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString().trim();
    }


    private void navigateToGameOver(String winner, String loser, int ws, int ls) {
        if (!gameOverNavigated.compareAndSet(false, true)) return;
        if (isFinishing()) return;
        running = false;
        Log.d(TAG, "navigateToGameOver winner=" + winner);
        Intent intent = new Intent(this, MultiplayerGameOverActivity.class);
        intent.putExtra("winner_name",  winner != null ? winner : "");
        intent.putExtra("loser_name",   loser  != null ? loser  : "");
        intent.putExtra("winner_score", ws);
        intent.putExtra("loser_score",  ls);
        intent.putExtra("my_name",      myName);
        startActivity(intent);
        finish();
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause — pausing render, keeping connection alive");
        running = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume surfaceReady=" + surfaceReady);
        if (surfaceReady && !running) startGameThread();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy — stopping network");
        running = false;
        if (server != null) server.stop();
        if (client != null) client.stop();
    }
}