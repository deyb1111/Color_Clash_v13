package com.example.colorclash.network;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.example.colorclash.models.PowerUp;

/**
 * GameServer — runs on the HOST device.
 *
 * ROOT DISCONNECT FIX:
 * Dedicated write thread owns the PrintWriter. Game loop enqueues into a
 * bounded BlockingQueue via offer() — never blocks, never causes false
 * checkError() disconnects.
 *
 * POWERUP FIX:
 * sendGameState() now accepts the live powerup list and serialises each
 * powerup's x, y, type, and color into a JSON array in the STATE packet.
 */
public class GameServer {

    public static final int PORT = 9001;
    private static final String TAG = "GameServer";

    public interface ServerListener {
        void onClientConnected();
        void onInputReceived(float vx, float vy);
        void onClientDisconnected();
        void onError(String message);

        /**
         * Fires when the joining player asks to dash. Default no-op for
         * backward compat with older listeners that don't implement it.
         */
        default void onDashRequest() {}

        /**
         * Fires when the joining player asks to activate their held power-up.
         */
        default void onActivateRequest() {}

        /**
         * Fires once on connect when the client sends its profile so the host
         * can render the joiner's equipped cosmetic / trail.
         */
        default void onProfileReceived(String name, String cosmeticAsset, String trailAsset) {}
    }

    private ServerSocket           serverSocket;
    private Socket                 clientSocket;
    private Thread                 acceptThread;
    private Thread                 readThread;
    private Thread                 writeThread;

    private final BlockingQueue<String> writeQueue = new LinkedBlockingQueue<>(4);
    private static final String POISON = "__STOP__";

    private volatile boolean      running = false;
    private final AtomicBoolean   stopped = new AtomicBoolean(false);
    private volatile ServerListener listener;

    public GameServer(ServerListener listener) {
        this.listener = listener;
    }

    public void setListener(ServerListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() { return running; }

    public void startAsync() {
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                serverSocket.setSoTimeout(60_000);
                Log.d(TAG, "Waiting for client on port " + PORT);

                clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                running = true;

                Log.d(TAG, "Client connected: " + clientSocket.getInetAddress());
                startWriteThread(clientSocket);
                ServerListener l = listener;
                if (l != null) l.onClientConnected();
                startReadLoop();

            } catch (SocketTimeoutException e) {
                Log.w(TAG, "Accept timed out");
                ServerListener l = listener;
                if (l != null) l.onError("No client connected within 60 seconds.");
            } catch (IOException e) {
                if (!stopped.get()) Log.w(TAG, "Accept error: " + e.getMessage());
                ServerListener l = listener;
                if (running && l != null) l.onError(e.getMessage());
            }
        }, "GameServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    // ── Write thread ─────────────────────────────────────────────────────────

    private void startWriteThread(Socket sock) {
        writeThread = new Thread(() -> {
            PrintWriter out = null;
            try {
                out = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(sock.getOutputStream())),
                        false);

                while (running) {
                    String packet;
                    try {
                        packet = writeQueue.take();
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (POISON.equals(packet)) break;

                    out.println(packet);
                    out.flush();

                    if (out.checkError()) {
                        Log.w(TAG, "Write thread: checkError — client unreachable");
                        break;
                    }
                }
            } catch (IOException e) {
                if (running) Log.w(TAG, "Write thread IO error: " + e.getMessage());
            } finally {
                if (out != null) { try { out.close(); } catch (Exception ignored) {} }
                Log.d(TAG, "Write thread exiting");
                if (!stopped.get()) {
                    ServerListener l = listener;
                    if (l != null) l.onClientDisconnected();
                    stop();
                }
            }
        }, "GameServer-Write");
        writeThread.setDaemon(true);
        writeThread.start();
    }

    // ── Enqueue helpers ───────────────────────────────────────────────────────

    public void sendState(JSONObject state) {
        if (stopped.get() || !running) return;
        boolean offered = writeQueue.offer(state.toString());
        if (!offered) {
            Log.v(TAG, "Write queue full — dropping STATE packet");
        }
    }

    /**
     * Serialises the full game state including the live powerup list.
     *
     * Powerup array format (each element):
     *   { "x": float, "y": float, "type": String, "color": int }
     *
     * @param powerUps  The host's live powerup list — may be null or empty.
     */
    public void sendGameState(float p1nx, float p1ny, float p2nx, float p2ny,
                              int p1lives, int p2lives, int p1score, int p2score,
                              int bgIndex, float timer,
                              List<PowerUp> powerUps) {
        sendFullGameState(p1nx, p1ny, p2nx, p2ny,
                p1lives, p2lives, p1score, p2score,
                bgIndex, timer, powerUps,
                "", "", "", "", "", "",
                "", 0, "", 0,
                false, false);
    }

    /**
     * Extended STATE packet that also carries:
     *  - both players' names and equipped cosmetic / trail (so the client can render them)
     *  - each player's currently-held (pending) power-up + slot color
     *  - whether each player is currently dashing
     */
    public void sendFullGameState(float p1nx, float p1ny, float p2nx, float p2ny,
                                  int p1lives, int p2lives, int p1score, int p2score,
                                  int bgIndex, float timer,
                                  List<PowerUp> powerUps,
                                  String p1name, String p2name,
                                  String p1cos,  String p2cos,
                                  String p1trail, String p2trail,
                                  String p1pending, int p1pendingCol,
                                  String p2pending, int p2pendingCol,
                                  boolean p1dashing, boolean p2dashing) {
        try {
            JSONObject j = new JSONObject();
            j.put("type",    "STATE");
            j.put("p1nx",    p1nx);    j.put("p1ny",    p1ny);
            j.put("p2nx",    p2nx);    j.put("p2ny",    p2ny);
            j.put("p1lives", p1lives); j.put("p2lives", p2lives);
            j.put("p1score", p1score); j.put("p2score", p2score);
            j.put("bgIndex", bgIndex); j.put("timer",   timer);

            // ── Powerup list ──────────────────────────────────────────────────
            JSONArray puArray = new JSONArray();
            if (powerUps != null) {
                for (PowerUp pu : powerUps) {
                    JSONObject puObj = new JSONObject();
                    puObj.put("x",     pu.x);
                    puObj.put("y",     pu.y);
                    puObj.put("type",  pu.type);
                    puObj.put("color", pu.color);
                    puArray.put(puObj);
                }
            }
            j.put("powerups", puArray);

            // ── Profile / cosmetics / pending / dash ──────────────────────────
            j.put("p1name",         p1name        != null ? p1name        : "");
            j.put("p2name",         p2name        != null ? p2name        : "");
            j.put("p1cos",          p1cos         != null ? p1cos         : "");
            j.put("p2cos",          p2cos         != null ? p2cos         : "");
            j.put("p1trail",        p1trail       != null ? p1trail       : "");
            j.put("p2trail",        p2trail       != null ? p2trail       : "");
            j.put("p1pending",      p1pending     != null ? p1pending     : "");
            j.put("p2pending",      p2pending     != null ? p2pending     : "");
            j.put("p1pendingCol",   p1pendingCol);
            j.put("p2pendingCol",   p2pendingCol);
            j.put("p1dashing",      p1dashing);
            j.put("p2dashing",      p2dashing);

            sendState(j);
        } catch (JSONException e) {
            Log.e(TAG, "sendFullGameState JSON error", e);
        }
    }

    public void sendGameOver(String winnerName, String loserName,
                             int winnerScore, int loserScore) {
        try {
            JSONObject j = new JSONObject();
            j.put("type",        "GAMEOVER");
            j.put("winnerName",  winnerName  != null ? winnerName  : "");
            j.put("loserName",   loserName   != null ? loserName   : "");
            j.put("winnerScore", winnerScore);
            j.put("loserScore",  loserScore);
            if (!stopped.get()) {
                try { writeQueue.put(j.toString()); }
                catch (InterruptedException ignored) {}
            }
        } catch (JSONException e) {
            Log.e(TAG, "sendGameOver JSON error", e);
        }
    }

    // ── Read thread ───────────────────────────────────────────────────────────

    private void startReadLoop() {
        readThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleMessage(line.trim());
                }
            } catch (IOException e) {
                if (running) Log.d(TAG, "Read loop ended: " + e.getMessage());
            } finally {
                Log.d(TAG, "GameServer read loop exiting");
                stop();
            }
        }, "GameServer-Read");
        readThread.setDaemon(true);
        readThread.start();
    }

    private void handleMessage(String raw) {
        try {
            JSONObject j    = new JSONObject(raw);
            String     type = j.optString("type", "");
            ServerListener l = listener;
            switch (type) {
                case "INPUT":
                    float vx = (float) j.optDouble("vx", 0.0);
                    float vy = (float) j.optDouble("vy", 0.0);
                    Log.v(TAG, "INPUT vx=" + vx + " vy=" + vy);
                    if (l != null) l.onInputReceived(vx, vy);
                    break;
                case "DASH":
                    if (l != null) l.onDashRequest();
                    break;
                case "ACTIVATE":
                    if (l != null) l.onActivateRequest();
                    break;
                case "PROFILE":
                    if (l != null) l.onProfileReceived(
                            j.optString("name",     ""),
                            j.optString("cosmetic", ""),
                            j.optString("trail",    ""));
                    break;
                case "PING":
                    try {
                        JSONObject pong = new JSONObject();
                        pong.put("type", "PONG");
                        sendState(pong);
                    } catch (JSONException ignored) {}
                    break;
                default:
                    Log.d(TAG, "Unknown msg: " + type);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Bad JSON from client: " + raw);
        }
    }

    // ── Stop ─────────────────────────────────────────────────────────────────

    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        running = false;
        Log.d(TAG, "GameServer stopping");
        writeQueue.offer(POISON);
        try { if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close(); }
        catch (IOException ignored) {}
        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); }
        catch (IOException ignored) {}
    }
}