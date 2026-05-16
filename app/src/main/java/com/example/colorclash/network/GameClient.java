package com.example.colorclash.network;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class GameClient {

    private static final String TAG = "GameClient";
    public  static final int    PORT = GameServer.PORT;

    public interface ClientListener {
        void onConnected();
        void onStateReceived(JSONObject state);
        void onGameOver(String winnerName, String loserName, int winnerScore, int loserScore);
        void onDisconnected();
        void onError(String message);
    }


    private static final String MSG_INPUT    = "INPUT";
    private static final String MSG_PROFILE  = "PROFILE";
    private static final String MSG_DASH     = "DASH";
    private static final String MSG_ACTIVATE = "ACTIVATE";

    private Socket  socket;
    private Thread  connectThread;
    private Thread  readThread;
    private Thread  writeThread;

    private final BlockingQueue<String> writeQueue = new LinkedBlockingQueue<>(4);
    private static final String POISON = "__STOP__";

    private volatile boolean    running = false;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean disconnectedFired = new AtomicBoolean(false);

    private volatile ClientListener listener;

    public GameClient(ClientListener listener) {
        this.listener = listener;
    }

    public void setListener(ClientListener listener) {
        this.listener = listener;
    }

    public boolean isStopped() { return stopped.get(); }

    public void connectAsync(String hostIp) {
        connectThread = new Thread(() -> {
            try {
                Log.d(TAG, "Connecting to " + hostIp + ":" + PORT);
                socket = new Socket();
                socket.connect(new InetSocketAddress(hostIp, PORT), 10_000);
                socket.setTcpNoDelay(true);
                running = true;

                Log.d(TAG, "Connected to host");
                startWriteThread(socket);
                ClientListener l = listener;
                if (l != null) l.onConnected();
                startReadLoop();

            } catch (IOException e) {
                Log.e(TAG, "Connect failed: " + e.getMessage());
                ClientListener l = listener;
                if (l != null) l.onError("Cannot reach host: " + e.getMessage());
            }
        }, "GameClient-Connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

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
                        Log.w(TAG, "Write thread: checkError — host unreachable");
                        break;
                    }
                }
            } catch (IOException e) {
                if (running) Log.w(TAG, "Write thread IO: " + e.getMessage());
            } finally {
                if (out != null) { try { out.close(); } catch (Exception ignored) {} }
                Log.d(TAG, "Client write thread exiting");
                fireDisconnected();
                stop();
            }
        }, "GameClient-Write");
        writeThread.setDaemon(true);
        writeThread.start();
    }


    public void sendInput(float vx, float vy) {
        if (stopped.get()) return;
        try {
            JSONObject j = new JSONObject();
            j.put("type", MSG_INPUT);
            j.put("vx",   vx);
            j.put("vy",   vy);
            boolean offered = writeQueue.offer(j.toString());
            if (!offered) Log.v(TAG, "Write queue full — INPUT dropped");
        } catch (JSONException e) {
            Log.e(TAG, "sendInput JSON error", e);
        }
    }


    public void sendDash() {
        sendSimple(MSG_DASH);
    }

    public void sendActivate() {
        sendSimple(MSG_ACTIVATE);
    }

    public void sendProfile(String name, String cosmeticAsset, String trailAsset) {
        if (stopped.get()) return;
        try {
            JSONObject j = new JSONObject();
            j.put("type", MSG_PROFILE);
            j.put("name",     name          != null ? name          : "");
            j.put("cosmetic", cosmeticAsset != null ? cosmeticAsset : "");
            j.put("trail",    trailAsset    != null ? trailAsset    : "");

            try { writeQueue.put(j.toString()); }
            catch (InterruptedException ignored) {}
        } catch (JSONException e) {
            Log.e(TAG, "sendProfile JSON error", e);
        }
    }

    private void sendSimple(String type) {
        if (stopped.get()) return;
        try {
            JSONObject j = new JSONObject();
            j.put("type", type);
            boolean offered = writeQueue.offer(j.toString());
            if (!offered) Log.v(TAG, "Write queue full — " + type + " dropped");
        } catch (JSONException e) {
            Log.e(TAG, type + " JSON error", e);
        }
    }


    private void startReadLoop() {
        readThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleMessage(line.trim());
                }
            } catch (IOException e) {
                if (running) Log.d(TAG, "Read loop ended: " + e.getMessage());
            } finally {
                Log.d(TAG, "Client read loop exiting");
                stop();
            }
        }, "GameClient-Read");
        readThread.setDaemon(true);
        readThread.start();
    }

    private void handleMessage(String raw) {
        try {
            JSONObject j    = new JSONObject(raw);
            String     type = j.optString("type", "");
            ClientListener l = listener;
            switch (type) {
                case "STATE":
                    if (l != null) l.onStateReceived(j);
                    break;
                case "GAMEOVER":
                    if (l != null)
                        l.onGameOver(
                                j.optString("winnerName", ""),
                                j.optString("loserName",  ""),
                                j.optInt("winnerScore", 0),
                                j.optInt("loserScore",  0));
                    break;
                case "PONG":
                    break;
                default:
                    Log.d(TAG, "Unknown message: " + type);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Bad JSON from server: " + raw);
        }
    }


    private void fireDisconnected() {
        if (!disconnectedFired.compareAndSet(false, true)) return;
        ClientListener l = listener;
        if (l != null) {
            Log.d(TAG, "Firing onDisconnected");
            l.onDisconnected();
        }
    }


    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        running = false;
        Log.d(TAG, "GameClient stopping");
        writeQueue.offer(POISON);
        try { if (socket != null && !socket.isClosed()) socket.close(); }
        catch (IOException ignored) {}
    }
}