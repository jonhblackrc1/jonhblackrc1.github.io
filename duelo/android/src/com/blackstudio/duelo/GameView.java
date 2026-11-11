package com.blackstudio.duelo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Random;

/**
 * Duelo de Barras: pong vertical para dos personas en un mismo teléfono.
 * Jugador 0 = barra de arriba (cian), jugador 1 = barra de abajo (rosa).
 * Todo lo que mira cada jugador se dibuja en el "marco" del jugador de abajo
 * y, para el de arriba, se gira 180° alrededor del centro de la pantalla.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // ---------- Estados ----------
    private static final int MENU = 0, SERVE = 1, PLAY = 2, POINT = 3, PAUSED = 4, OVER = 5;

    private static final int TOP = 0, BOTTOM = 1;
    private static final int C_BG = 0xFF07080F;
    private static final int[] C_PLAYER = {0xFF00E5FF, 0xFFFF2E88};
    private static final int C_EDGE = 0xFFFFD23F;
    private static final String[] NAME = {"ARRIBA", "ABAJO"};

    private static final float MAX_ANGLE = (float) Math.toRadians(62);
    private static final float EDGE_ZONE = 0.72f;   // |offset| desde el que es "filo"
    private static final float SERVE_AUTO = 4.0f;   // segundos hasta sacar solo

    private final Object lock = new Object();
    private final Random rnd = new Random();
    private Thread thread;
    private volatile boolean running;
    private volatile boolean surfaceReady;

    // ---------- Sonido / vibración ----------
    private SoundPool sounds;
    private int sHit, sEdge, sWall, sServe, sPoint, sWin;
    private Vibrator vibrator;
    private float wallSoundCooldown;

    // ---------- Dimensiones ----------
    private int W, H;
    private float u;              // 1% del ancho
    private float padW, padH, ballR;
    private final float[] padY = new float[2];
    private float baseSpeed, maxSpeed;

    // ---------- Estado de juego ----------
    private int state = MENU, pausedFrom = PLAY;
    private int target = 7;
    private final int[] score = new int[2];
    private int server = BOTTOM;
    private int lastHitter = -1;
    private int rally;
    private float stateTime;

    private final float[] padX = new float[2];
    private final float[] padTarget = new float[2];
    private final float[] padVx = new float[2];
    private final float[] padFlash = new float[2];
    private final int[] pointer = {-1, -1};

    private float bx, by, bvx, bvy, speed;
    private float sideFlash; private int flashSide;
    private float shake;
    private int winner = -1;

    // Estela
    private static final int TRAIL = 16;
    private final float[] trailX = new float[TRAIL], trailY = new float[TRAIL];
    private int trailHead, trailCount;
    private float trailTimer;

    // Partículas
    private static final int MAXP = 220;
    private final float[] px = new float[MAXP], py = new float[MAXP], pvx = new float[MAXP],
            pvy = new float[MAXP], plife = new float[MAXP], pmax = new float[MAXP], psize = new float[MAXP];
    private final int[] pcol = new int[MAXP];

    // Carteles flotantes ("¡FILO!", etc.)
    private static final int MAXPOP = 6;
    private final float[] popX = new float[MAXPOP], popY = new float[MAXPOP], popLife = new float[MAXPOP];
    private final String[] popText = new String[MAXPOP];
    private final int[] popSide = new int[MAXPOP], popCol = new int[MAXPOP];

    // Pelota decorativa del menú
    private float mbx, mby, mvx, mvy;

    // ---------- Botones ----------
    private final RectF[] btnPoints = {new RectF(), new RectF(), new RectF()};
    private static final int[] POINT_OPTIONS = {5, 7, 11};
    private final RectF btnServeTop = new RectF(), btnServeBottom = new RectF(), btnServeRandom = new RectF();
    private final RectF btnPause = new RectF();
    private final RectF btnA = new RectF(), btnB = new RectF();   // diálogos (pausa / final)

    // ---------- Pinceles ----------
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface faceBlack = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private final Typeface faceBold = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Shader[] padShader = new Shader[2];
    private final Shader[] glowShader = new Shader[3];
    private final RectF tmp = new RectF();

    public GameView(Context ctx) {
        super(ctx);
        getHolder().addCallback(this);
        setFocusable(true);
        setKeepScreenOn(true);
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);

        sounds = new SoundPool(8, AudioManager.STREAM_MUSIC, 0);
        sHit = sounds.load(ctx, R.raw.hit, 1);
        sEdge = sounds.load(ctx, R.raw.edge, 1);
        sWall = sounds.load(ctx, R.raw.wall, 1);
        sServe = sounds.load(ctx, R.raw.serve, 1);
        sPoint = sounds.load(ctx, R.raw.point, 1);
        sWin = sounds.load(ctx, R.raw.win, 1);
        vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }

    // =====================================================================
    // Ciclo de vida
    // =====================================================================

    public void resume() {
        if (running) return;
        running = true;
        thread = new Thread(this, "duelo-loop");
        thread.start();
    }

    public void pause() {
        synchronized (lock) {
            if (state == PLAY || state == SERVE || state == POINT) goPause();
        }
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    public void release() {
        if (sounds != null) { sounds.release(); sounds = null; }
    }

    /** @return true si el botón atrás fue consumido por el juego. */
    public boolean onBack() {
        synchronized (lock) {
            switch (state) {
                case MENU: return false;
                case PAUSED: state = pausedFrom; clearPointers(); return true;
                case OVER: goMenu(); return true;
                default: goPause(); return true;
            }
        }
    }

    @Override public void surfaceCreated(SurfaceHolder h) { surfaceReady = true; }

    @Override public void surfaceChanged(SurfaceHolder h, int format, int w, int hgt) {
        synchronized (lock) { layout(w, hgt); }
    }

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        synchronized (lock) {
            if (state == PLAY || state == SERVE || state == POINT) goPause();
        }
    }

    private void layout(int w, int h) {
        boolean first = W == 0;
        W = w; H = h;
        u = W / 100f;
        padW = 24 * u;
        padH = 2.8f * u;
        ballR = 2.1f * u;
        padY[TOP] = H * 0.085f;
        padY[BOTTOM] = H * 0.915f;
        baseSpeed = H * 0.50f;
        maxSpeed = H * 1.45f;

        for (int s = 0; s < 2; s++) {
            padShader[s] = new LinearGradient(0, 0, padW, 0,
                    new int[]{C_EDGE, C_EDGE, C_PLAYER[s], 0xFFFFFFFF, C_PLAYER[s], C_EDGE, C_EDGE},
                    new float[]{0f, (1 - EDGE_ZONE) / 2f, (1 - EDGE_ZONE) / 2f + 0.05f, 0.5f,
                            1 - (1 - EDGE_ZONE) / 2f - 0.05f, 1 - (1 - EDGE_ZONE) / 2f, 1f},
                    Shader.TileMode.CLAMP);
        }
        int[] glowCols = {C_PLAYER[0], C_PLAYER[1], 0xFFFFFFFF};
        for (int i = 0; i < 3; i++) {
            glowShader[i] = new RadialGradient(0, 0, ballR * 4.2f,
                    new int[]{withAlpha(glowCols[i], 150), withAlpha(glowCols[i], 40), 0},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }

        // Botones del menú
        float bw = 74 * u, bh = 11 * u, cx = W / 2f;
        btnServeTop.set(cx - bw / 2, H * 0.60f, cx + bw / 2, H * 0.60f + bh);
        btnServeBottom.set(cx - bw / 2, H * 0.60f + bh * 1.3f, cx + bw / 2, H * 0.60f + bh * 2.3f);
        btnServeRandom.set(cx - bw / 2, H * 0.60f + bh * 2.6f, cx + bw / 2, H * 0.60f + bh * 3.6f);
        float cw = 18 * u, gap = 5 * u, cy = H * 0.48f;
        for (int i = 0; i < 3; i++) {
            float l = cx - (cw * 3 + gap * 2) / 2 + i * (cw + gap);
            btnPoints[i].set(l, cy, l + cw, cy + 11 * u);
        }
        float pr = 5.5f * u;
        btnPause.set(W - 3 * u - pr * 2, H / 2f - pr, W - 3 * u, H / 2f + pr);
        btnA.set(cx - bw / 2, H / 2f - bh * 1.15f, cx + bw / 2, H / 2f - bh * 0.15f);
        btnB.set(cx - bw / 2, H / 2f + bh * 0.15f, cx + bw / 2, H / 2f + bh * 1.15f);

        if (first) {
            padX[TOP] = padX[BOTTOM] = padTarget[TOP] = padTarget[BOTTOM] = W / 2f;
            mbx = W * 0.3f; mby = H * 0.3f;
            mvx = H * 0.22f; mvy = H * 0.33f;
        } else {
            for (int s = 0; s < 2; s++) {
                padX[s] = clampPad(padX[s]);
                padTarget[s] = clampPad(padTarget[s]);
            }
        }
    }

    // =====================================================================
    // Bucle
    // =====================================================================

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            if (!surfaceReady || W == 0) {
                sleep(16);
                last = System.nanoTime();
                continue;
            }
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1e9f, 1f / 30f);
            last = now;

            SurfaceHolder holder = getHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) { sleep(10); continue; }
                synchronized (lock) {
                    update(dt);
                    render(c);
                }
            } catch (Exception e) {
                // La superficie puede desaparecer en cualquier momento: se reintenta.
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) { }
                }
            }
            long spent = (System.nanoTime() - now) / 1000000L;
            if (spent < 8) sleep(8 - spent);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    // =====================================================================
    // Lógica
    // =====================================================================

    private void update(float dt) {
        stateTime += dt;
        wallSoundCooldown -= dt;
        shake = Math.max(0, shake - dt * 2.6f);
        sideFlash = Math.max(0, sideFlash - dt * 1.8f);
        for (int s = 0; s < 2; s++) padFlash[s] = Math.max(0, padFlash[s] - dt * 4f);
        updateParticles(dt);
        for (int i = 0; i < MAXPOP; i++) if (popLife[i] > 0) popLife[i] -= dt;

        if (state == PAUSED) return;

        if (state == MENU || state == OVER) {
            updateMenuBall(dt);
            if (state == OVER && rnd.nextFloat() < dt * 20) {
                float x = rnd.nextFloat() * W;
                float y = winner == TOP ? H * 0.02f : H * 0.98f;
                spawn(x, y, (rnd.nextFloat() - 0.5f) * 30 * u, (winner == TOP ? 1 : -1) * (20 + rnd.nextFloat() * 40) * u,
                        rnd.nextBoolean() ? C_PLAYER[winner] : C_EDGE, 1.6f, 0.9f * u);
            }
            return;
        }

        updatePaddles(dt);

        if (state == SERVE) {
            float dir = server == TOP ? 1 : -1;
            bx = padX[server];
            by = padY[server] + dir * (padH / 2 + ballR + 0.6f * u);
            if (stateTime >= SERVE_AUTO) launch();
            return;
        }

        // PLAY y POINT: la pelota se mueve (en POINT sigue volando fuera)
        moveBall(dt);
        trailTimer -= dt;
        if (trailTimer <= 0) {
            trailTimer = 0.012f;
            trailX[trailHead] = bx; trailY[trailHead] = by;
            trailHead = (trailHead + 1) % TRAIL;
            trailCount = Math.min(TRAIL, trailCount + 1);
        }

        if (state == POINT && stateTime > 1.15f) {
            if (score[0] >= target || score[1] >= target) {
                winner = score[0] >= target ? TOP : BOTTOM;
                state = OVER;
                stateTime = 0;
                play(sWin);
                vibrate(new long[]{0, 80, 70, 80, 70, 220});
            } else {
                startServe();
            }
        }
    }

    private void updatePaddles(float dt) {
        float k = Math.min(1f, dt * 28f);
        for (int s = 0; s < 2; s++) {
            float prev = padX[s];
            padX[s] += (padTarget[s] - padX[s]) * k;
            padX[s] = clampPad(padX[s]);
            float v = (padX[s] - prev) / Math.max(dt, 1e-4f);
            padVx[s] = padVx[s] * 0.6f + v * 0.4f;
        }
    }

    private float clampPad(float x) {
        return Math.max(padW / 2, Math.min(W - padW / 2, x));
    }

    private void moveBall(float dt) {
        float dist = speed * dt;
        int steps = Math.max(1, Math.min(16, (int) Math.ceil(dist / (ballR * 0.5f))));
        float sdt = dt / steps;
        for (int i = 0; i < steps; i++) {
            bx += bvx * sdt;
            by += bvy * sdt;

            // Paredes laterales
            if (bx < ballR) { bx = ballR; bvx = Math.abs(bvx); wallHit(); }
            else if (bx > W - ballR) { bx = W - ballR; bvx = -Math.abs(bvx); wallHit(); }

            if (state != PLAY) continue;

            // Barra de arriba
            float faceTop = padY[TOP] + padH / 2;
            if (bvy < 0 && by - ballR <= faceTop && by >= padY[TOP] - padH / 2
                    && Math.abs(bx - padX[TOP]) <= padW / 2 + ballR * 0.8f) {
                by = faceTop + ballR;
                bounce(TOP);
            }
            // Barra de abajo
            float faceBottom = padY[BOTTOM] - padH / 2;
            if (bvy > 0 && by + ballR >= faceBottom && by <= padY[BOTTOM] + padH / 2
                    && Math.abs(bx - padX[BOTTOM]) <= padW / 2 + ballR * 0.8f) {
                by = faceBottom - ballR;
                bounce(BOTTOM);
            }

            // ¿Traspasó la barra? Punto para el rival
            if (by + ballR < padY[TOP] - padH / 2) { scorePoint(BOTTOM); }
            else if (by - ballR > padY[BOTTOM] + padH / 2) { scorePoint(TOP); }
        }
    }

    /** El rebote depende de en qué parte de la barra pega la pelota. */
    private void bounce(int side) {
        float offset = (bx - padX[side]) / (padW / 2);
        offset = Math.max(-1f, Math.min(1f, offset));
        boolean edge = Math.abs(offset) >= EDGE_ZONE;

        rally++;
        speed = Math.min(maxSpeed, speed * 1.04f);
        if (edge) speed = Math.min(maxSpeed, speed * 1.10f);

        // Centro = sale recta; hacia las puntas = cada vez más inclinada.
        float angle = offset * MAX_ANGLE;
        // "Efecto": mover la barra al pegar tuerce un poco la salida.
        float spin = Math.max(-1f, Math.min(1f, padVx[side] / (W * 1.5f)));
        angle += spin * (float) Math.toRadians(12);
        angle = Math.max(-MAX_ANGLE - 0.08f, Math.min(MAX_ANGLE + 0.08f, angle));

        float dir = side == TOP ? 1 : -1;
        bvx = speed * (float) Math.sin(angle);
        bvy = dir * speed * (float) Math.cos(angle);

        lastHitter = side;
        padFlash[side] = 1f;
        int col = edge ? C_EDGE : C_PLAYER[side];
        int n = edge ? 26 : 14;
        for (int i = 0; i < n; i++) {
            float a = (float) (rnd.nextFloat() * Math.PI);
            float sp = (15 + rnd.nextFloat() * (edge ? 55 : 35)) * u;
            spawn(bx, by, (float) Math.cos(a) * sp, dir * (float) Math.sin(a) * sp, col, 0.45f + rnd.nextFloat() * 0.3f, (0.5f + rnd.nextFloat() * 0.6f) * u);
        }
        if (edge) {
            popup(side, bx, by, "¡FILO!", C_EDGE);
            play(sEdge);
        } else {
            if (Math.abs(offset) < 0.2f) popup(side, bx, by, "RECTO", withAlpha(C_PLAYER[side], 200));
            play(sHit);
        }
        if (rally > 0 && rally % 10 == 0) popup(side, W / 2f, side == TOP ? H / 2f - 14 * u : H / 2f + 14 * u, rally + " GOLPES", 0xFFFFFFFF);
    }

    private void wallHit() {
        for (int i = 0; i < 5; i++) {
            float sgn = bx < W / 2f ? 1 : -1;
            spawn(bx, by, sgn * (5 + rnd.nextFloat() * 20) * u, (rnd.nextFloat() - 0.5f) * 25 * u,
                    withAlpha(0xFFFFFFFF, 180), 0.3f, 0.45f * u);
        }
        if (wallSoundCooldown <= 0) { play(sWall); wallSoundCooldown = 0.08f; }
    }

    private void scorePoint(int who) {
        if (state != PLAY) return;
        score[who]++;
        state = POINT;
        stateTime = 0;
        flashSide = 1 - who;
        sideFlash = 1f;
        shake = 1f;
        server = 1 - who;   // saca quien recibió el punto
        play(sPoint);
        vibrate(140);
        float ey = who == BOTTOM ? 0 : H;
        for (int i = 0; i < 40; i++) {
            spawn(bx, ey, (rnd.nextFloat() - 0.5f) * 90 * u, (who == BOTTOM ? 1 : -1) * rnd.nextFloat() * 60 * u,
                    rnd.nextBoolean() ? C_PLAYER[who] : 0xFFFFFFFF, 0.7f + rnd.nextFloat() * 0.5f, (0.5f + rnd.nextFloat()) * u);
        }
        popup(who, W / 2f, who == TOP ? H * 0.32f : H * 0.68f, "¡PUNTO!", C_PLAYER[who]);
    }

    private void startServe() {
        state = SERVE;
        stateTime = 0;
        rally = 0;
        lastHitter = -1;
        trailCount = 0;
        speed = baseSpeed;
    }

    private void launch() {
        if (state != SERVE) return;
        float dir = server == TOP ? 1 : -1;
        float angle = (float) Math.toRadians((rnd.nextFloat() * 2 - 1) * 16);
        float spin = Math.max(-1f, Math.min(1f, padVx[server] / (W * 1.5f)));
        angle += spin * (float) Math.toRadians(20);
        speed = baseSpeed;
        bvx = speed * (float) Math.sin(angle);
        bvy = dir * speed * (float) Math.cos(angle);
        lastHitter = server;
        state = PLAY;
        stateTime = 0;
        trailCount = 0;
        padFlash[server] = 0.6f;
        play(sServe);
    }

    private void newMatch(int firstServer) {
        score[0] = score[1] = 0;
        winner = -1;
        server = firstServer;
        padX[TOP] = padX[BOTTOM] = padTarget[TOP] = padTarget[BOTTOM] = W / 2f;
        clearPointers();
        startServe();
    }

    private void goPause() {
        pausedFrom = state;
        state = PAUSED;
        clearPointers();
    }

    private void goMenu() {
        state = MENU;
        stateTime = 0;
        clearPointers();
    }

    private void clearPointers() { pointer[0] = pointer[1] = -1; }

    private void updateMenuBall(float dt) {
        mbx += mvx * dt; mby += mvy * dt;
        float r = ballR;
        if (mbx < r) { mbx = r; mvx = Math.abs(mvx); }
        if (mbx > W - r) { mbx = W - r; mvx = -Math.abs(mvx); }
        if (mby < r) { mby = r; mvy = Math.abs(mvy); }
        if (mby > H - r) { mby = H - r; mvy = -Math.abs(mvy); }
        trailTimer -= dt;
        if (trailTimer <= 0) {
            trailTimer = 0.016f;
            trailX[trailHead] = mbx; trailY[trailHead] = mby;
            trailHead = (trailHead + 1) % TRAIL;
            trailCount = Math.min(TRAIL, trailCount + 1);
        }
    }

    // ---------- Partículas y carteles ----------

    private void spawn(float x, float y, float vx, float vy, int col, float life, float size) {
        for (int i = 0; i < MAXP; i++) {
            if (plife[i] <= 0) {
                px[i] = x; py[i] = y; pvx[i] = vx; pvy[i] = vy;
                plife[i] = pmax[i] = life; pcol[i] = col; psize[i] = size;
                return;
            }
        }
    }

    private void updateParticles(float dt) {
        float drag = (float) Math.pow(0.06, dt);
        for (int i = 0; i < MAXP; i++) {
            if (plife[i] <= 0) continue;
            plife[i] -= dt;
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
            pvx[i] *= drag; pvy[i] *= drag;
        }
    }

    private void popup(int side, float x, float y, String t, int col) {
        int best = 0;
        for (int i = 0; i < MAXPOP; i++) if (popLife[i] < popLife[best]) best = i;
        popX[best] = x; popY[best] = y; popText[best] = t; popSide[best] = side;
        popCol[best] = col; popLife[best] = 0.9f;
    }

    // ---------- Audio ----------

    private void play(int id) {
        if (sounds != null && id != 0) sounds.play(id, 1f, 1f, 1, 0, 1f);
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        try { if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(ms); } catch (Exception ignored) { }
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long[] pattern) {
        try { if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(pattern, -1); } catch (Exception ignored) { }
    }

    // =====================================================================
    // Toques (multitáctil: cada jugador controla su mitad)
    // =====================================================================

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        synchronized (lock) {
            if (W == 0) return true;
            int action = e.getActionMasked();
            int idx = e.getActionIndex();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    onDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                    break;
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        int id = e.getPointerId(i);
                        for (int s = 0; s < 2; s++) if (pointer[s] == id) padTarget[s] = clampPad(e.getX(i));
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    onUp(e.getPointerId(idx));
                    break;
                case MotionEvent.ACTION_CANCEL:
                    clearPointers();
                    break;
            }
        }
        return true;
    }

    private void onDown(int id, float x, float y) {
        switch (state) {
            case MENU:
                for (int i = 0; i < 3; i++) if (hit(btnPoints[i], x, y)) { target = POINT_OPTIONS[i]; play(sWall); }
                if (hit(btnServeTop, x, y)) newMatch(TOP);
                else if (hit(btnServeBottom, x, y)) newMatch(BOTTOM);
                else if (hit(btnServeRandom, x, y)) newMatch(rnd.nextBoolean() ? TOP : BOTTOM);
                return;
            case PAUSED:
                if (hit(btnA, x, y)) { state = pausedFrom; clearPointers(); }
                else if (hit(btnB, x, y)) goMenu();
                return;
            case OVER:
                if (stateTime < 0.8f) return;   // evita tocar sin querer
                if (hit(btnA, x, y)) newMatch(1 - winner);  // revancha: saca quien perdió
                else if (hit(btnB, x, y)) goMenu();
                return;
            default:
                if (hitPause(x, y)) { goPause(); return; }
                int side = y < H / 2f ? TOP : BOTTOM;
                if (pointer[side] == -1) {
                    pointer[side] = id;
                    padTarget[side] = clampPad(x);
                }
        }
    }

    private void onUp(int id) {
        for (int s = 0; s < 2; s++) {
            if (pointer[s] != id) continue;
            pointer[s] = -1;
            if (state == SERVE && s == server && stateTime > 0.35f) launch();
        }
    }

    private boolean hit(RectF r, float x, float y) {
        return x >= r.left - u && x <= r.right + u && y >= r.top - u && y <= r.bottom + u;
    }

    private boolean hitPause(float x, float y) {
        float cx = btnPause.centerX(), cy = btnPause.centerY(), r = btnPause.width() * 0.75f;
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
    }

    // =====================================================================
    // Dibujo
    // =====================================================================

    private void render(Canvas c) {
        c.drawColor(C_BG);
        c.save();
        if (shake > 0) {
            float m = shake * shake * 2.2f * u;
            c.translate((rnd.nextFloat() - 0.5f) * m, (rnd.nextFloat() - 0.5f) * m);
        }
        drawField(c);

        if (state == MENU) {
            drawTrail(c, mbx, mby, 2);
            drawParticles(c);
            c.restore();
            drawMenu(c);
            return;
        }

        drawScores(c);
        for (int s = 0; s < 2; s++) drawPaddle(c, s);
        if (state != OVER) {
            int colIdx = lastHitter < 0 ? 2 : lastHitter;
            if (state == SERVE) drawServeHint(c);
            drawTrail(c, bx, by, colIdx);
        }
        drawParticles(c);
        drawPopups(c);
        c.restore();

        if (state == PLAY || state == SERVE || state == POINT) drawPauseButton(c);
        if (state == PAUSED) drawPaused(c);
        if (state == OVER) drawOver(c);
    }

    private void drawField(Canvas c) {
        // Mitades teñidas con el color de cada jugador
        fill.setShader(null);
        fill.setColor(withAlpha(C_PLAYER[TOP], 14));
        c.drawRect(0, 0, W, H / 2f, fill);
        fill.setColor(withAlpha(C_PLAYER[BOTTOM], 14));
        c.drawRect(0, H / 2f, W, H, fill);

        // Destello rojo en la mitad que recibió el punto
        if (sideFlash > 0 && (state == POINT || state == SERVE)) {
            fill.setColor(withAlpha(0xFFFF3B3B, (int) (90 * sideFlash)));
            if (flashSide == TOP) c.drawRect(0, 0, W, H / 2f, fill);
            else c.drawRect(0, H / 2f, W, H, fill);
        }

        // Zona de gol detrás de cada barra
        for (int s = 0; s < 2; s++) {
            float y0 = s == TOP ? 0 : padY[BOTTOM] + padH / 2;
            float y1 = s == TOP ? padY[TOP] - padH / 2 : H;
            fill.setColor(withAlpha(C_PLAYER[s], 22));
            c.drawRect(0, y0, W, y1, fill);
            fill.setColor(withAlpha(C_PLAYER[s], 120));
            float ly = s == TOP ? 0.6f * u : H - 0.6f * u;
            c.drawRect(0, ly - 0.3f * u, W, ly + 0.3f * u, fill);
        }

        // Línea central punteada y círculo
        fill.setColor(0x33FFFFFF);
        float dash = 3.5f * u, gap = 2.5f * u, th = 0.5f * u;
        for (float x = gap / 2; x < W; x += dash + gap) c.drawRect(x, H / 2f - th, Math.min(W, x + dash), H / 2f + th, fill);
        stroke.setColor(0x22FFFFFF);
        stroke.setStrokeWidth(0.6f * u);
        c.drawCircle(W / 2f, H / 2f, 14 * u, stroke);
    }

    private void drawPaddle(Canvas c, int s) {
        float x = padX[s], y = padY[s];
        float flash = padFlash[s];
        float grow = 1f + flash * 0.08f;
        float w = padW * grow, h = padH * (1f + flash * 0.4f);

        // Halo
        for (int i = 3; i >= 1; i--) {
            float e = i * 1.1f * u + flash * 1.5f * u;
            fill.setColor(withAlpha(C_PLAYER[s], (int) ((18 + flash * 30) / i * 1.6f)));
            tmp.set(x - w / 2 - e, y - h / 2 - e, x + w / 2 + e, y + h / 2 + e);
            c.drawRoundRect(tmp, h / 2 + e, h / 2 + e, fill);
        }
        // Cuerpo con zonas: puntas amarillas (filo), centro blanco (recto)
        c.save();
        c.translate(x - w / 2, y - h / 2);
        c.scale(grow, 1f);
        shaderPaint.setShader(padShader[s]);
        tmp.set(0, 0, padW, h);
        c.drawRoundRect(tmp, h / 2, h / 2, shaderPaint);
        c.restore();
        if (flash > 0) {
            fill.setColor(withAlpha(0xFFFFFFFF, (int) (160 * flash)));
            tmp.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2);
            c.drawRoundRect(tmp, h / 2, h / 2, fill);
        }
    }

    private void drawTrail(Canvas c, float x, float y, int colIdx) {
        int col = colIdx == 2 ? 0xFFFFFFFF : C_PLAYER[colIdx];
        for (int i = 0; i < trailCount; i++) {
            int k = (trailHead - 1 - i + TRAIL * 2) % TRAIL;
            float t = 1f - (float) i / TRAIL;
            fill.setColor(withAlpha(col, (int) (110 * t * t)));
            c.drawCircle(trailX[k], trailY[k], ballR * (0.35f + 0.6f * t), fill);
        }
        c.save();
        c.translate(x, y);
        shaderPaint.setShader(glowShader[colIdx]);
        c.drawCircle(0, 0, ballR * 4.2f, shaderPaint);
        c.restore();
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, ballR, fill);
        fill.setColor(withAlpha(col, 200));
        c.drawCircle(x, y, ballR * 0.55f, fill);
    }

    private void drawParticles(Canvas c) {
        for (int i = 0; i < MAXP; i++) {
            if (plife[i] <= 0) continue;
            float t = plife[i] / pmax[i];
            fill.setColor(withAlpha(pcol[i], (int) (Color.alpha(pcol[i]) * t)));
            c.drawCircle(px[i], py[i], psize[i] * (0.4f + 0.6f * t), fill);
        }
    }

    private void drawPopups(Canvas c) {
        text.setTypeface(faceBlack);
        for (int i = 0; i < MAXPOP; i++) {
            if (popLife[i] <= 0 || popText[i] == null) continue;
            float t = popLife[i] / 0.9f;
            beginSide(c, popSide[i]);
            // En el marco del jugador, su barra siempre está abajo: el cartel sube.
            float x = popSide[i] == TOP ? W - popX[i] : popX[i];
            float y = popSide[i] == TOP ? H - popY[i] : popY[i];
            float rise = (1 - t) * 9 * u;
            text.setTextSize((5.5f + (1 - t) * 1.5f) * u);
            text.setColor(withAlpha(popCol[i], (int) (255 * Math.min(1f, t * 1.6f))));
            c.drawText(popText[i], clampX(x, 14 * u), y - 5 * u - rise, text);
            c.restore();
        }
    }

    private float clampX(float x, float m) { return Math.max(m, Math.min(W - m, x)); }

    private void drawScores(Canvas c) {
        text.setTypeface(faceBlack);
        for (int s = 0; s < 2; s++) {
            beginSide(c, s);
            boolean justScored = state == POINT && score[s] > 0 && server != s;
            float pop = justScored ? Math.max(0, 1 - stateTime * 1.4f) : 0;
            text.setTextSize((17 + pop * 7) * u);
            text.setColor(withAlpha(C_PLAYER[s], (int) (85 + pop * 170)));
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText(String.valueOf(score[s]), W * 0.16f, H / 2f + 23 * u, text);
            text.setTypeface(faceBold);
            text.setTextSize(3.2f * u);
            text.setColor(0x66FFFFFF);
            c.drawText("RIVAL " + score[1 - s] + "  ·  A " + target, W * 0.16f, H / 2f + 29 * u, text);
            text.setTypeface(faceBlack);
            c.restore();
        }
    }

    private void drawServeHint(Canvas c) {
        beginSide(c, server);
        float pulse = 0.6f + 0.4f * (float) Math.sin(stateTime * 6);
        text.setTypeface(faceBlack);
        text.setTextSize(7 * u);
        text.setColor(withAlpha(C_PLAYER[server], (int) (255 * pulse)));
        c.drawText("TU SAQUE", W / 2f, H * 0.70f, text);
        text.setTypeface(faceBold);
        text.setTextSize(3.8f * u);
        text.setColor(0xCCFFFFFF);
        c.drawText("Mueve la barra y suelta el dedo para sacar", W / 2f, H * 0.70f + 6 * u, text);
        int left = (int) Math.ceil(SERVE_AUTO - stateTime);
        text.setColor(0x77FFFFFF);
        c.drawText("Sale solo en " + left, W / 2f, H * 0.70f + 11 * u, text);
        c.restore();

        beginSide(c, 1 - server);
        text.setTypeface(faceBold);
        text.setTextSize(4.2f * u);
        text.setColor(withAlpha(C_PLAYER[1 - server], 170));
        c.drawText("¡Prepárate! Saca " + (server == TOP ? "arriba" : "abajo"), W / 2f, H * 0.70f, text);
        c.restore();
    }

    private void drawPauseButton(Canvas c) {
        float cx = btnPause.centerX(), cy = btnPause.centerY(), r = btnPause.width() / 2;
        fill.setColor(0x33FFFFFF);
        c.drawCircle(cx, cy, r, fill);
        fill.setColor(0xCCFFFFFF);
        float bw = r * 0.22f, bh = r * 0.9f;
        c.drawRect(cx - bw * 1.8f, cy - bh / 2, cx - bw * 0.6f, cy + bh / 2, fill);
        c.drawRect(cx + bw * 0.6f, cy - bh / 2, cx + bw * 1.8f, cy + bh / 2, fill);
    }

    private void drawMenu(Canvas c) {
        float cx = W / 2f;
        // Barras decorativas
        for (int s = 0; s < 2; s++) {
            float old = padX[s];
            padX[s] = cx + (float) Math.sin(stateTime * 1.3f + s * 2.1f) * 22 * u;
            drawPaddle(c, s);
            padX[s] = old;
        }

        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(faceBlack);
        text.setTextSize(13 * u);
        text.setColor(C_PLAYER[TOP]);
        c.drawText("DUELO", cx, H * 0.22f, text);
        text.setColor(C_PLAYER[BOTTOM]);
        c.drawText("DE BARRAS", cx, H * 0.22f + 13 * u, text);

        text.setTypeface(faceBold);
        text.setTextSize(3.9f * u);
        text.setColor(0xDDFFFFFF);
        String[] rules = {
                "2 jugadores · un teléfono · cada uno en su lado",
                "Mueve tu barra con el dedo en tu mitad.",
                "Centro de la barra = la pelota sale recta.",
                "Puntas amarillas = ángulo cerrado y ¡más rápida!",
                "Si la pelota traspasa tu barra, punto del rival."
        };
        for (int i = 0; i < rules.length; i++) {
            if (i == 3) text.setColor(C_EDGE); else text.setColor(i == 0 ? 0x99FFFFFF : 0xDDFFFFFF);
            c.drawText(rules[i], cx, H * 0.32f + i * 5.2f * u, text);
        }

        text.setTextSize(4.2f * u);
        text.setColor(0xAAFFFFFF);
        c.drawText("PUNTOS PARA GANAR", cx, btnPoints[0].top - 2.5f * u, text);
        for (int i = 0; i < 3; i++) {
            boolean sel = target == POINT_OPTIONS[i];
            drawButton(c, btnPoints[i], String.valueOf(POINT_OPTIONS[i]), sel ? 0xFFFFFFFF : 0x55FFFFFF, sel);
        }

        text.setTextSize(4.2f * u);
        text.setColor(0xAAFFFFFF);
        c.drawText("¿QUIÉN SACA PRIMERO?", cx, btnServeTop.top - 2.5f * u, text);
        drawButton(c, btnServeTop, "▲  SACA ARRIBA", C_PLAYER[TOP], true);
        drawButton(c, btnServeBottom, "▼  SACA ABAJO", C_PLAYER[BOTTOM], true);
        drawButton(c, btnServeRandom, "SORTEO", C_EDGE, false);
    }

    private void drawButton(Canvas c, RectF r, String label, int col, boolean solid) {
        float rad = r.height() / 2;
        if (solid) {
            fill.setColor(withAlpha(col, 40));
            c.drawRoundRect(r, rad, rad, fill);
        }
        stroke.setColor(col);
        stroke.setStrokeWidth(0.7f * u);
        c.drawRoundRect(r, rad, rad, stroke);
        text.setTypeface(faceBlack);
        text.setTextSize(r.height() * 0.42f);
        text.setColor(col);
        c.drawText(label, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text);
    }

    private void drawPaused(Canvas c) {
        fill.setShader(null);
        fill.setColor(0xCC07080F);
        c.drawRect(0, 0, W, H, fill);
        text.setTypeface(faceBlack);
        text.setTextSize(12 * u);
        for (int s = 0; s < 2; s++) {
            beginSide(c, s);
            text.setColor(C_PLAYER[s]);
            c.drawText("PAUSA", W / 2f, H * 0.80f, text);
            c.restore();
        }
        drawButton(c, btnA, "CONTINUAR", 0xFFFFFFFF, true);
        drawButton(c, btnB, "MENÚ", 0x99FFFFFF, false);
    }

    private void drawOver(Canvas c) {
        fill.setShader(null);
        fill.setColor(0x9907080F);
        c.drawRect(0, 0, W, H, fill);
        float appear = Math.min(1f, stateTime * 2.5f);
        for (int s = 0; s < 2; s++) {
            beginSide(c, s);
            boolean won = s == winner;
            text.setTypeface(faceBlack);
            text.setTextSize((won ? 14 : 10) * u * (0.7f + 0.3f * appear));
            text.setColor(withAlpha(won ? C_PLAYER[s] : 0xFF888899, (int) (255 * appear)));
            c.drawText(won ? "¡GANASTE!" : "PERDISTE", W / 2f, H * 0.78f, text);
            text.setTypeface(faceBold);
            text.setTextSize(5 * u);
            text.setColor(withAlpha(0xFFFFFFFF, (int) (200 * appear)));
            c.drawText(score[s] + "  –  " + score[1 - s], W / 2f, H * 0.78f + 8 * u, text);
            c.restore();
        }
        if (stateTime > 0.8f) {
            drawButton(c, btnA, "REVANCHA", C_PLAYER[1 - winner], true);
            drawButton(c, btnB, "MENÚ", 0x99FFFFFF, false);
        }
    }

    /** Gira el lienzo para que el texto mire al jugador indicado. Llamar c.restore() al terminar. */
    private void beginSide(Canvas c, int side) {
        c.save();
        if (side == TOP) c.rotate(180, W / 2f, H / 2f);
    }

    private static int withAlpha(int col, int a) {
        a = Math.max(0, Math.min(255, a));
        return (col & 0x00FFFFFF) | (a << 24);
    }
}
