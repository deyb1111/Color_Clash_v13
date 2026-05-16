 package com.example.colorclash.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.example.colorclash.database.DatabaseManager;
import com.example.colorclash.models.CosmeticUtil;
import com.example.colorclash.models.Player;
import com.example.colorclash.models.PowerUp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;


public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final float PANEL_W_RATIO = 0.14f;
    private static final int   P1_COLOR      = 0xFFFFCC00; // gold
    private static final int   P2_COLOR      = 0xFF3399FF; // blue
    private static final float FRAME_INSET   = 16f;

    private Thread        gameThread;
    private SurfaceHolder holder;
    private volatile boolean running = false;
    private long lastTime = 0;

    public Player       player1, player2;
    public List<PowerUp> powerUps = new ArrayList<>();
    private Random       random   = new Random();

    private final Paint paint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap bmpRed, bmpGreen, bmpBlue, bmpYellow;
    private Bitmap bmpShield, bmpSpeed, bmpLife, bmpDash;
    private int    bmpSize = 0;   // set in surfaceCreated

    private long  nextBgChangeTime    = 0;
    private int   currentBgIndex      = 0;
    private float timerCountdown      = 5f;
    private long  currentBgIntervalMs = 5000L;
    private static final long BG_MIN = 3500L, BG_MAX = 7500L;
    private final int[] bgColors   = { Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW };
    private final int[] bgOverlays = {
            Color.argb(110, 60, 0, 0), Color.argb(110, 0, 50, 0),
            Color.argb(110, 0, 0, 60), Color.argb(110, 60, 55, 0) };

    //joystick
    private static final float JOY_R  = 115f;
    private static final float JOY_KNOB = 42f;
    private float p1JoyX, p1JoyY, p1CX, p1CY;
    private float p2JoyX, p2JoyY, p2CX, p2CY;
    private boolean p1Touch = false, p2Touch = false;
    private int     p1Ptr   = -1,    p2Ptr   = -1;

    // Screen
    private int   screenWidth = 1920, screenHeight = 1080;
    private float panelW = 0f;
    private float btnR   = 60f;

    //button pos
    private float p1EmptyX, p1EmptyY, p1DashX, p1DashY;
    private float p2DashX, p2DashY, p2EmptyX, p2EmptyY;
    private float exitCX, exitCY, exitW, exitH;
    private boolean exitPressed = false;

    private String pendingP1Name = "P1", pendingP2Name = "P2";
    private int    pendingP1Color = Color.RED, pendingP2Color = Color.BLUE;
    private boolean gameOver = false;

    private GameListener  listener;
    private DatabaseManager dbManager;

    public interface GameListener {
        void onGameOver(Player winner, Player loser);
        void onBgColorChanged(int newColor);
        void onExit();
    }


    private Context context;

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        holder = getHolder();
        holder.addCallback(this);
        paint.setAntiAlias(true);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);
        dbManager = DatabaseManager.getInstance(context.getApplicationContext());
    }

    public void setGameListener(GameListener l) { this.listener = l; }
    public void setPlayerData(String p1n, int p1c, String p2n, int p2c) {
        pendingP1Name = p1n; pendingP1Color = p1c;
        pendingP2Name = p2n; pendingP2Color = p2c;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        screenWidth  = getWidth();
        screenHeight = getHeight();
        panelW = screenWidth * PANEL_W_RATIO;
        btnR   = panelW * 0.23f;

        float gameL = panelW, gameR = screenWidth - panelW;
        // Load and scale asset bitmaps
        bmpSize = (int)(panelW * 0.46f);
        bmpRed    = loadScaled(context, com.example.colorclash.R.drawable.redp,    bmpSize);
        bmpGreen  = loadScaled(context, com.example.colorclash.R.drawable.greenp,  bmpSize);
        bmpBlue   = loadScaled(context, com.example.colorclash.R.drawable.bluep,   bmpSize);
        bmpYellow = loadScaled(context, com.example.colorclash.R.drawable.yellowp, bmpSize);
        int puSize = (int)(screenHeight * 0.09f);
        bmpShield = loadScaled(context, com.example.colorclash.R.drawable.shield3, puSize);
        bmpSpeed  = loadScaled(context, com.example.colorclash.R.drawable.speed,   puSize);
        bmpLife   = loadScaled(context, com.example.colorclash.R.drawable.life1,   puSize);
        int dashSize = (int)(btnR * 2.2f);
        bmpDash   = loadScaled(context, com.example.colorclash.R.drawable.btn_dash, dashSize);

        player1 = new Player(pendingP1Name, pendingP1Color,
                gameL + (gameR - gameL) * 0.25f, screenHeight / 2f);
        player2 = new Player(pendingP2Name, pendingP2Color,
                gameL + (gameR - gameL) * 0.75f, screenHeight / 2f);
        player1.radius = 52f; player2.radius = 52f;
        applyEquippedCosmetics(player1); applyEquippedCosmetics(player2);

        p1CX = panelW / 2f;            p1CY = screenHeight * 0.16f;
        p1JoyX = p1CX;                 p1JoyY = p1CY;

        p2CX = screenWidth - panelW / 2f; p2CY = screenHeight * 0.84f;
        p2JoyX = p2CX;                    p2JoyY = p2CY;

        p1EmptyX = panelW / 2f; p1EmptyY = screenHeight * 0.70f;
        p1DashX  = panelW / 2f; p1DashY  = screenHeight * 0.87f;

        p2DashX  = screenWidth - panelW / 2f; p2DashY  = screenHeight * 0.13f;
        p2EmptyX = screenWidth - panelW / 2f; p2EmptyY = screenHeight * 0.30f;

        exitW  = screenHeight * 0.09f; exitH = screenHeight * 0.09f;
        exitCX = screenWidth / 2f;    exitCY = screenHeight * 0.935f;

        currentBgIntervalMs = randBg();
        nextBgChangeTime    = System.currentTimeMillis() + currentBgIntervalMs;
        lastTime = System.currentTimeMillis();
        running = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    private void applyEquippedCosmetics(Player p) {
        if (p == null || p.name == null || dbManager == null) return;
        try {
            dbManager.ensurePlayer(p.name);
            p.equippedCosmeticColor = CosmeticUtil.parseStaticColor(
                    dbManager.getEquippedAsset(p.name, "cosmetic"));
            p.equippedTrailAsset = dbManager.getEquippedAsset(p.name, "trail");
        } catch (Exception ignored) {}
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {
        screenWidth = w; screenHeight = ht;
    }
    @Override public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        try { if (gameThread != null) gameThread.join(); }
        catch (InterruptedException e) { e.printStackTrace(); }
    }

    @Override public void run() {
        while (running) {
            if (!holder.getSurface().isValid()) continue;
            long now = System.currentTimeMillis(), delta = now - lastTime;
            lastTime = now; if (delta > 100) delta = 16;
            update(delta); draw();
            try { Thread.sleep(16); } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    private void update(long dt) {
        if (gameOver || player1 == null || player2 == null) return;
        player1.update(dt, screenWidth, screenHeight);
        player2.update(dt, screenWidth, screenHeight);
        clamp(player1); clamp(player2);

        long now = System.currentTimeMillis();
        if (now > nextBgChangeTime) {
            currentBgIndex      = pickBg(currentBgIndex);
            currentBgIntervalMs = randBg();
            nextBgChangeTime    = now + currentBgIntervalMs;
            timerCountdown      = currentBgIntervalMs / 1000f;
            final int col = bgColors[currentBgIndex];
            post(() -> { if (listener != null) listener.onBgColorChanged(col); });
        } else { timerCountdown = (nextBgChangeTime - now) / 1000f; }

        checkCollisions();

        if (random.nextFloat() < 0.003f && powerUps.size() < 3) {
            float px = panelW + 150 + random.nextFloat()*(screenWidth - panelW*2 - 300);
            float py = 80 + random.nextFloat()*(screenHeight - 160);
            powerUps.add(new PowerUp(px, py));
        }
        Iterator<PowerUp> it = powerUps.iterator();
        while (it.hasNext()) { if (it.next().isExpired()) it.remove(); }

        if (player1.lives <= 0 || player2.lives <= 0) {
            gameOver = true;
            Player w = player1.lives > 0 ? player1 : player2;
            Player l = player1.lives > 0 ? player2 : player1;
            post(() -> { if (listener != null) listener.onGameOver(w, l); });
        }
    }

    private void clamp(Player p) {
        float minX = panelW + FRAME_INSET + p.radius, maxX = screenWidth - panelW - FRAME_INSET - p.radius;
        float minY = FRAME_INSET + p.radius,           maxY = screenHeight - FRAME_INSET - p.radius;
        if (p.x < minX) p.x = minX; if (p.x > maxX) p.x = maxX;
        if (p.y < minY) p.y = minY; if (p.y > maxY) p.y = maxY;
    }

    private int pickBg(int cur) {
        int n; do { n = random.nextInt(bgColors.length); } while (n == cur); return n;
    }
    private long randBg() { return BG_MIN + (long)(random.nextFloat()*(BG_MAX - BG_MIN)); }

    private void checkCollisions() {
        int ac = bgColors[currentBgIndex];
        if (player1.collidesWith(player2)) {
            if      (player1.color == ac) { if (player2.takeDamage()) { player1.score+=100; pushBack(player2,player1); } }
            else if (player2.color == ac) { if (player1.takeDamage()) { player2.score+=100; pushBack(player1,player2); } }
        }
        Iterator<PowerUp> it = powerUps.iterator();
        while (it.hasNext()) {
            PowerUp p = it.next();
            if      (p.collidesWith(player1)) { collectPU(player1,p); it.remove(); }
            else if (p.collidesWith(player2)) { collectPU(player2,p); it.remove(); }
        }
    }
    private void collectPU(Player p, PowerUp pu) {
        p.pendingPowerUp=pu.type; p.pendingPowerUpColor=pu.color;
        p.pendingPowerUpExpiry=System.currentTimeMillis()+Player.PENDING_POWERUP_TTL_MS;
        p.score+=50;
    }
    private void pushBack(Player def, Player att) {
        float dx=def.x-att.x, dy=def.y-att.y, d=(float)Math.sqrt(dx*dx+dy*dy);
        if (d>0){def.x+=(dx/d)*150; def.y+=(dy/d)*150;} clamp(def);
    }


    private void draw() {
        if (player1==null||player2==null) return;
        Canvas canvas = holder.lockCanvas();
        if (canvas==null) return;
        try {
            canvas.drawColor(Color.BLACK);
            drawGameField(canvas);
            for (PowerUp p : new ArrayList<>(powerUps)) {
                Bitmap puBmp = powerupBitmapFor(p.type);
                if (puBmp != null) {
                    float pr = p.radius * 1.8f + (float)Math.sin(System.currentTimeMillis()*0.008)*6f;
                    paint.setAlpha(255);
                    canvas.save();
                    Path puClip = new Path();
                    puClip.addCircle(p.x, p.y, pr, Path.Direction.CW);
                    canvas.clipPath(puClip);
                    canvas.drawBitmap(puBmp, null,
                        new android.graphics.RectF(p.x-pr, p.y-pr, p.x+pr, p.y+pr), paint);
                    canvas.restore();
                    paint.setAlpha(255);
                } else {
                    paint.setAlpha(255);
                    p.draw(canvas, paint);
                }
            }
            drawTrail(canvas, player1); drawTrail(canvas, player2);
            drawPlayer(canvas, player1); drawPlayer(canvas, player2);
            drawCenterHUD(canvas);
            drawLeftPanel(canvas);
            drawRightPanel(canvas);
            drawExitButton(canvas);
        } finally { holder.unlockCanvasAndPost(canvas); }
    }

    private Bitmap loadScaled(Context ctx, int resId, int size) {
        try {
            Bitmap b = BitmapFactory.decodeResource(ctx.getResources(), resId);
            if (b == null) return null;
            return Bitmap.createScaledBitmap(b, size, size, true);
        } catch (Exception e) { return null; }
    }

    private void drawGameField(Canvas canvas) {
        RectF f = new RectF(panelW, 0, screenWidth-panelW, screenHeight);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bgColors[currentBgIndex]); canvas.drawRect(f, paint);
        paint.setColor(bgOverlays[currentBgIndex]); canvas.drawRect(f, paint);

        // Center line
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3);
        paint.setColor(Color.argb(55,255,255,255));
        canvas.drawLine(screenWidth/2f,0,screenWidth/2f,screenHeight,paint);

        // Center circle
        float cx=screenWidth/2f, cy=screenHeight/2f;
        float cr=Math.min(screenWidth-panelW*2,screenHeight)*0.21f;
        paint.setStrokeWidth(2); paint.setColor(Color.argb(45,255,255,255));
        canvas.drawCircle(cx,cy,cr,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(65,255,255,255));
        canvas.drawCircle(cx,cy,18,paint);
        paint.setColor(Color.argb(38,255,255,255));
        canvas.drawCircle(cx,cy,46,paint);
    }

    private void drawCenterHUD(Canvas canvas) {
        float cx  = screenWidth / 2f;
        float topY = screenHeight * 0.01f;

        // Timer number
        String t = String.format("%.1f", Math.max(0, timerCountdown));
        textPaint.setTextSize(screenHeight * 0.065f);
        textPaint.setColor(Color.WHITE);
        textPaint.setShadowLayer(6f, 1f, 1f, Color.argb(200, 0, 0, 0));
        canvas.drawText(t, cx - textPaint.measureText(t)/2,
                topY + screenHeight * 0.065f, textPaint);

        textPaint.setTextSize(screenHeight * 0.030f);
        textPaint.setColor(Color.WHITE);
        textPaint.setShadowLayer(4f, 1f, 1f, Color.argb(180, 0, 0, 0));
        String lbl = bgName(bgColors[currentBgIndex]) + " ATTACKS";
        canvas.drawText(lbl, cx - textPaint.measureText(lbl)/2,
                topY + screenHeight * 0.105f, textPaint);

        textPaint.clearShadowLayer();
    }


    private void drawLeftPanel(Canvas canvas) {
        drawPanelBg(canvas, true);
        float cx = panelW/2f;
        int pc1 = p1PanelColor();

        drawJoystick(canvas, p1Touch, p1CX, p1CY, p1JoyX, p1JoyY, pc1);

        canvas.save();
        canvas.rotate(90f, cx, screenHeight*0.47f);
        drawInfoBlock(canvas, player1, cx, screenHeight*0.46f, true, pc1);
        canvas.restore();

        canvas.save();
        canvas.rotate(90f, p1EmptyX, p1EmptyY);
        drawActivateBtn(canvas, player1, p1EmptyX, p1EmptyY);
        canvas.restore();

        canvas.save();
        canvas.rotate(90f, p1DashX, p1DashY);
        drawDashBtn(canvas, player1, p1DashX, p1DashY);
        canvas.restore();
    }


    private void drawRightPanel(Canvas canvas) {
        drawPanelBg(canvas, false);
        float cx = screenWidth - panelW/2f;
        int pc2 = p2PanelColor();

        canvas.save();
        canvas.rotate(-90f, p2DashX, p2DashY);
        drawDashBtn(canvas, player2, p2DashX, p2DashY);
        canvas.restore();

        canvas.save();
        canvas.rotate(-90f, p2EmptyX, p2EmptyY);
        drawActivateBtn(canvas, player2, p2EmptyX, p2EmptyY);
        canvas.restore();

        canvas.save();
        canvas.rotate(-90f, cx, screenHeight*0.53f);
        drawInfoBlock(canvas, player2, cx, screenHeight*0.52f, false, pc2);
        canvas.restore();

        // 4. JOYSTICK (bottom)
        drawJoystick(canvas, p2Touch, p2CX, p2CY, p2JoyX, p2JoyY, pc2);
    }

    private int p1PanelColor() { return player1!=null ? player1.color : P1_COLOR; }
    private int p2PanelColor() { return player2!=null ? player2.color : P2_COLOR; }

    private void drawPanelBg(Canvas canvas, boolean isLeft) {
        float pl = isLeft ? 0 : screenWidth - panelW;
        float pr = isLeft ? panelW : screenWidth;
        int pc    = isLeft ? p1PanelColor() : p2PanelColor();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(240, 6, 8, 18));
        canvas.drawRect(pl, 0, pr, screenHeight, paint);

        float ex = isLeft ? pr : pl;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(18);
        paint.setColor(Color.argb(50,Color.red(pc),Color.green(pc),Color.blue(pc)));
        canvas.drawLine(ex,0,ex,screenHeight,paint);
        paint.setStrokeWidth(3); paint.setColor(pc);
        canvas.drawLine(ex,0,ex,screenHeight,paint);
    }

    private void drawInfoBlock(Canvas canvas, Player p, float cx, float centreY,
                                boolean isLeft, int pColor) {
        float blockW = panelW * 0.82f;
        float blockH = screenHeight * 0.18f;
        float bx = cx - blockW/2f;
        float by = centreY - blockH/2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(140, 16, 18, 32));
        canvas.drawRoundRect(new RectF(bx, by, bx+blockW, by+blockH), 12, 12, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
        paint.setColor(Color.argb(130,Color.red(pColor),Color.green(pColor),Color.blue(pColor)));
        canvas.drawRoundRect(new RectF(bx, by, bx+blockW, by+blockH), 12, 12, paint);

        // HP bar
        float hpBarW = blockW * 0.15f;
        float hpBarH = blockH * 0.80f;
        float hpBx = isLeft ? (bx + blockW - hpBarW - blockW*0.04f) : (bx + blockW*0.04f);
        float hpBy = by + (blockH - hpBarH)/2f;
        drawHpBar(canvas, p, hpBx, hpBy, hpBarW, hpBarH, pColor);

        // Text area
        float textAreaW = blockW - hpBarW - blockW*0.10f;
        float textCX = isLeft ? (bx + textAreaW/2f + blockW*0.04f)
                              : (bx + hpBarW + blockW*0.10f + textAreaW/2f);

        // Player name
        textPaint.setTextSize(blockH * 0.24f);
        textPaint.setColor(Color.WHITE);
        String name = p.name != null ? p.name : "?";
        while (textPaint.measureText(name) > textAreaW*0.9f && textPaint.getTextSize()>14)
            textPaint.setTextSize(textPaint.getTextSize()-2);
        canvas.drawText(name, textCX - textPaint.measureText(name)/2, by + blockH*0.32f, textPaint);

        // Score label
        textPaint.setTextSize(blockH * 0.17f);
        textPaint.setColor(Color.argb(160,200,200,255));
        canvas.drawText("SCORE", textCX - textPaint.measureText("SCORE")/2, by+blockH*0.55f, textPaint);

        // Score value
        textPaint.setTextSize(blockH * 0.28f);
        textPaint.setColor(Color.WHITE);
        String sc = String.valueOf(p.score);
        canvas.drawText(sc, textCX - textPaint.measureText(sc)/2, by+blockH*0.85f, textPaint);
    }

    private void drawHpBar(Canvas canvas, Player p, float x, float y,
                           float w, float h, int pColor) {
        int maxLives = 5;
        float segH = (h - (maxLives-1)*3f) / maxLives;

        for (int i = 0; i < maxLives; i++) {
            float segY = y + h - (i+1)*(segH+3f) + 3f;
            boolean filled = i < p.lives;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(filled
                    ? Color.argb(220,Color.red(pColor),Color.green(pColor),Color.blue(pColor))
                    : Color.argb(60,80,80,80));
            canvas.drawRoundRect(new RectF(x,segY,x+w,segY+segH),4,4,paint);
            if (filled) {
                paint.setColor(Color.argb(100,255,255,255));
                canvas.drawRoundRect(new RectF(x,segY,x+w,segY+segH*0.4f),4,4,paint);
            }
        }
    }

    private void drawJoystick(Canvas canvas, boolean active,
                              float cx, float cy, float kx, float ky, int pColor) {
        int pr=Color.red(pColor), pg=Color.green(pColor), pb=Color.blue(pColor);

        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4);
        paint.setColor(Color.argb(active?200:85, pr,pg,pb));
        canvas.drawCircle(cx,cy,JOY_R,paint);
        paint.setColor(Color.argb(active?70:25, pr,pg,pb));
        canvas.drawCircle(cx,cy,JOY_R*0.5f,paint);

        float ad=JOY_R*0.73f, al=JOY_R*0.16f;
        paint.setStrokeWidth(3);
        paint.setColor(Color.argb(active?160:65, pr,pg,pb));
        canvas.drawLine(cx,cy-ad, cx-al,cy-ad+al*1.1f,paint);
        canvas.drawLine(cx,cy-ad, cx+al,cy-ad+al*1.1f,paint);
        canvas.drawLine(cx,cy+ad, cx-al,cy+ad-al*1.1f,paint);
        canvas.drawLine(cx,cy+ad, cx+al,cy+ad-al*1.1f,paint);
        canvas.drawLine(cx-ad,cy, cx-ad+al*1.1f,cy-al,paint);
        canvas.drawLine(cx-ad,cy, cx-ad+al*1.1f,cy+al,paint);
        canvas.drawLine(cx+ad,cy, cx+ad-al*1.1f,cy-al,paint);
        canvas.drawLine(cx+ad,cy, cx+ad-al*1.1f,cy+al,paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(active?225:110, pr,pg,pb));
        canvas.drawCircle(kx,ky,JOY_KNOB,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3);
        paint.setColor(Color.argb(active?190:75, 255,255,255));
        canvas.drawCircle(kx,ky,JOY_KNOB,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(active?100:35, 255,255,255));
        canvas.drawCircle(kx-JOY_KNOB*0.26f,ky-JOY_KNOB*0.26f,JOY_KNOB*0.4f,paint);
    }

    private void drawDashBtn(Canvas canvas, Player p, float cx, float cy) {
        float cd = p.dashCooldownRatio();
        float r  = btnR * 1.1f;
        if (bmpDash != null) {
            paint.setAlpha(cd > 0 ? 90 : 255);
            canvas.save();
            Path dclip = new Path();
            dclip.addCircle(cx, cy, r, Path.Direction.CW);
            canvas.clipPath(dclip);
            canvas.drawBitmap(bmpDash, null,
                new android.graphics.RectF(cx-r, cy-r, cx+r, cy+r), paint);
            canvas.restore();
            paint.setAlpha(255);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(cd>0?Color.argb(130,80,80,110):Color.argb(200,255,170,20));
            canvas.drawCircle(cx,cy,r,paint);
            textPaint.setTextSize(r*0.40f); textPaint.setColor(Color.WHITE);
            String lbl="DASH"; canvas.drawText(lbl,cx-textPaint.measureText(lbl)/2,cy+r*0.15f,textPaint);
        }
        if (cd > 0) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(140,0,0,0));
            canvas.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-90f,360f*cd,true,paint);
        }
    }

    private void drawActivateBtn(Canvas canvas, Player p, float cx, float cy) {
        boolean has = p.pendingPowerUp != null;
        float r = btnR * 1.1f;
        if (has) {
            Bitmap bmp = powerupBitmapFor(p.pendingPowerUp);
            if (bmp != null) {
                paint.setAlpha(255);
                int s = (int)(r * 2);
                canvas.drawBitmap(bmp, null,
                    new android.graphics.Rect((int)(cx-r),(int)(cy-r),(int)(cx+r),(int)(cy+r)), paint);
                return;
            }
            // Fallback colored circle
            int c=p.pendingPowerUpColor!=0?p.pendingPowerUpColor:PowerUp.colorForType(p.pendingPowerUp);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(220,Color.red(c),Color.green(c),Color.blue(c)));
            canvas.drawCircle(cx,cy,r,paint);
            textPaint.setTextSize(r*0.36f); textPaint.setColor(Color.WHITE);
            String lbl=p.pendingPowerUp;
            canvas.drawText(lbl,cx-textPaint.measureText(lbl)/2,cy+r*0.14f,textPaint);
        } else {
            // Empty slot — subtle dark circle
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(70,60,60,80));
            canvas.drawCircle(cx,cy,r,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
            paint.setColor(Color.argb(100,180,180,200));
            canvas.drawCircle(cx,cy,r,paint);
            textPaint.setTextSize(r*0.32f); textPaint.setColor(Color.argb(140,200,200,220));
            String lbl="EMPTY";
            canvas.drawText(lbl,cx-textPaint.measureText(lbl)/2,cy+r*0.12f,textPaint);
        }
    }

    private Bitmap powerupBitmapFor(String type) {
        if (type == null) return null;
        switch (type.toUpperCase()) {
            case "SHIELD": return bmpShield;
            case "SPEED":  return bmpSpeed;
            case "LIFE":   return bmpLife;
            default:       return null;
        }
    }

    private void drawExitButton(Canvas canvas) {
        // Small circular icon button only — no text
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(exitPressed?Color.argb(220,200,40,40):Color.argb(160,40,40,80));
        canvas.drawCircle(exitCX,exitCY,exitH/2f,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
        paint.setColor(Color.argb(160,200,200,255));
        canvas.drawCircle(exitCX,exitCY,exitH/2f,paint);
        // Door/exit icon: ⏏ symbol
        textPaint.setTextSize(exitH*0.55f); textPaint.setColor(Color.WHITE);
        String icon="⏏"; float iw=textPaint.measureText(icon);
        canvas.drawText(icon,exitCX-iw/2,exitCY+exitH*0.20f,textPaint);
    }

    // player ball

    private void drawTrail(Canvas canvas, Player player) {
        if (player==null||player.equippedTrailAsset==null||player.equippedTrailAsset.isEmpty()) return;
        if (player.trailPoints.isEmpty()) return;
        long now=System.currentTimeMillis();
        boolean rainbow=CosmeticUtil.isRainbow(player.equippedTrailAsset);
        int base=rainbow?0:CosmeticUtil.parseStaticColor(player.equippedTrailAsset);
        paint.setStyle(Paint.Style.FILL);
        int i=0, n=player.trailPoints.size();
        for (float[] pt:player.trailPoints) {
            float t=1f-(i/(float)n); int alpha=(int)(180*t);
            float rad=player.radius*(0.85f-0.6f*(1f-t));
            int col=rainbow?CosmeticUtil.rainbowAt(now-i*35L):base;
            paint.setColor(Color.argb(alpha,Color.red(col),Color.green(col),Color.blue(col)));
            canvas.drawCircle(pt[0],pt[1],rad,paint); i++;
        }
        paint.setAlpha(255);
    }

    private void drawPlayer(Canvas canvas, Player p) {
        if (p.equippedCosmeticColor!=0) {
            int c=p.equippedCosmeticColor;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(80,Color.red(c),Color.green(c),Color.blue(c)));
            canvas.drawCircle(p.x,p.y,p.radius+26,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5);
            paint.setColor(Color.argb(220,Color.red(c),Color.green(c),Color.blue(c)));
            canvas.drawCircle(p.x,p.y,p.radius+26,paint);
        }
        if (p.isDashing()) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(110,255,255,120));
            canvas.drawCircle(p.x,p.y,p.radius+36,paint);
        }
        if (p.speedMultiplier>1f) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(50,0,255,255));
            canvas.drawCircle(p.x,p.y,p.radius+28,paint);
        }
        if (p.hasShield) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(80,0,220,255));
            canvas.drawCircle(p.x,p.y,p.radius+22,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3);
            paint.setColor(Color.argb(200,0,220,255));
            canvas.drawCircle(p.x,p.y,p.radius+22,paint);
        }
        if (p.hitFlash) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(200,255,60,60));
            canvas.drawCircle(p.x,p.y,p.radius+12,paint);
        }

        paint.setAlpha(255);
        Bitmap bmp = ballBitmapFor(p.color);
        if (bmp != null) {
            float r = p.radius * 1.1f;
            canvas.save();
            Path clip = new Path();
            clip.addCircle(p.x, p.y, r, Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(bmp, null,
                new android.graphics.RectF(p.x-r, p.y-r, p.x+r, p.y+r), paint);
            canvas.restore();
        } else {
            paint.setStyle(Paint.Style.FILL); paint.setColor(p.color);
            canvas.drawCircle(p.x,p.y,p.radius,paint);
        }
        paint.setAlpha(255);

        textPaint.setTextSize(34f); textPaint.setColor(Color.argb(100,0,0,0));
        float nw=textPaint.measureText(p.name);
        canvas.drawText(p.name,p.x-nw/2+2,p.y-p.radius-14,textPaint);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(p.name,p.x-nw/2,p.y-p.radius-16,textPaint);
    }

    private Bitmap ballBitmapFor(int color) {
        if (color == Color.RED)    return bmpRed;
        if (color == Color.GREEN)  return bmpGreen;
        if (color == Color.BLUE)   return bmpBlue;
        if (color == Color.YELLOW) return bmpYellow;
        return null;
    }

    private boolean inBtn(float ex, float ey, float cx, float cy) {
        float dx=ex-cx,dy=ey-cy; return dx*dx+dy*dy<=btnR*btnR;
    }
    private boolean inExit(float ex, float ey) {
        return ex>=exitCX-exitW/2&&ex<=exitCX+exitW/2
                &&ey>=exitCY-exitH/2&&ey<=exitCY+exitH/2;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (player1==null||player2==null) return true;
        int action=ev.getActionMasked(), pIdx=ev.getActionIndex();
        int pId=(int)ev.getPointerId(pIdx);
        float ex=ev.getX(pIdx), ey=ev.getY(pIdx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (inExit(ex,ey)) { exitPressed=true; post(()->{if(listener!=null)listener.onExit();}); break; }
                if (inBtn(ex,ey,p1DashX,p1DashY))  { player1.tryDash(); break; }
                if (inBtn(ex,ey,p1EmptyX,p1EmptyY)) { player1.activatePowerUp(); break; }
                if (inBtn(ex,ey,p2DashX,p2DashY))  { player2.tryDash(); break; }
                if (inBtn(ex,ey,p2EmptyX,p2EmptyY)) { player2.activatePowerUp(); break; }

                if (ex < screenWidth/2f && !p1Touch) {
                    p1Touch=true; p1Ptr=pId;
                    updateJoy(player1,ex,ey,p1CX,p1CY,1);
                } else if (ex>=screenWidth/2f && !p2Touch) {
                    p2Touch=true; p2Ptr=pId;
                    updateJoy(player2,ex,ey,p2CX,p2CY,2);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                for (int i=0;i<ev.getPointerCount();i++) {
                    int id=ev.getPointerId(i); float px=ev.getX(i),py=ev.getY(i);
                    if (id==p1Ptr) updateJoy(player1,px,py,p1CX,p1CY,1);
                    if (id==p2Ptr) updateJoy(player2,px,py,p2CX,p2CY,2);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                exitPressed=false;
                if (action == MotionEvent.ACTION_CANCEL) {
                    p1Touch=false; p1Ptr=-1; player1.vx=0; player1.vy=0; p1JoyX=p1CX; p1JoyY=p1CY;
                    p2Touch=false; p2Ptr=-1; player2.vx=0; player2.vy=0; p2JoyX=p2CX; p2JoyY=p2CY;
                } else {
                    if (pId==p1Ptr){p1Touch=false;p1Ptr=-1;player1.vx=0;player1.vy=0;p1JoyX=p1CX;p1JoyY=p1CY;}
                    if (pId==p2Ptr){p2Touch=false;p2Ptr=-1;player2.vx=0;player2.vy=0;p2JoyX=p2CX;p2JoyY=p2CY;}
                }
                if (ev.getPointerCount() == 1 && action == MotionEvent.ACTION_POINTER_UP) {
                    p1Touch=false; p1Ptr=-1; player1.vx=0; player1.vy=0; p1JoyX=p1CX; p1JoyY=p1CY;
                    p2Touch=false; p2Ptr=-1; player2.vx=0; player2.vy=0; p2JoyX=p2CX; p2JoyY=p2CY;
                }
                break;
        }
        return true;
    }

    private void updateJoy(Player p, float tx, float ty, float cx, float cy, int which) {
        float dx=tx-cx, dy=ty-cy, d=(float)Math.sqrt(dx*dx+dy*dy);
        if (d>JOY_R){dx=(dx/d)*JOY_R;dy=(dy/d)*JOY_R;tx=cx+dx;ty=cy+dy;d=JOY_R;}
        if (which==1){p1JoyX=tx;p1JoyY=ty;}else{p2JoyX=tx;p2JoyY=ty;}
        if (d>20){p.vx=(dx/JOY_R)*Player.MAX_SPEED;p.vy=(dy/JOY_R)*Player.MAX_SPEED;}
        else{p.vx=0;p.vy=0;}
    }

    private String bgName(int c){
        if(c==Color.RED)return"RED";if(c==Color.GREEN)return"GREEN";
        if(c==Color.BLUE)return"BLUE";if(c==Color.YELLOW)return"YELLOW";return"?";
    }

    public void pauseGame()  { running=false; }
    public void resumeGame() {
        if (player1==null||player2==null) return;
        if (!running){running=true;lastTime=System.currentTimeMillis();gameThread=new Thread(this);gameThread.start();}
    }
}
