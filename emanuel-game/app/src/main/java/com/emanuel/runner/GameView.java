package com.emanuel.runner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final SharedPreferences prefs;

    private float density;
    private float groundY;
    private float playerX;
    private float playerY;
    private float playerW;
    private float playerH;
    private float velocityY;
    private boolean onGround = true;
    private boolean started = false;
    private boolean gameOver = false;
    private long lastFrame = 0L;
    private long nextSpawn = 0L;
    private float speed;
    private int score = 0;
    private int best = 0;
    private float worldOffset = 0f;

    public GameView(Context context) {
        super(context);
        setFocusable(true);
        density = getResources().getDisplayMetrics().density;
        prefs = context.getSharedPreferences("emanuel_runner", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        groundY = h * 0.80f;
        playerW = Math.max(52f * density, h * 0.11f);
        playerH = playerW * 1.65f;
        playerX = w * 0.18f;
        playerY = groundY - playerH;
        speed = Math.max(260f * density, w * 0.28f);
        resetGame(false);
    }

    private void resetGame(boolean startNow) {
        obstacles.clear();
        score = 0;
        velocityY = 0f;
        onGround = true;
        gameOver = false;
        started = startNow;
        playerY = groundY - playerH;
        nextSpawn = System.currentTimeMillis() + 1200;
        lastFrame = 0;
        speed = Math.max(260f * density, getWidth() * 0.28f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = System.currentTimeMillis();
        if (lastFrame == 0L) lastFrame = now;
        float dt = Math.min(0.033f, (now - lastFrame) / 1000f);
        lastFrame = now;

        if (started && !gameOver) update(dt, now);
        drawBackground(c);
        drawGround(c);
        for (Obstacle o : obstacles) drawObstacle(c, o);
        drawPlayer(c);
        drawHud(c);
        if (!started) drawStart(c);
        if (gameOver) drawGameOver(c);

        postInvalidateOnAnimation();
    }

    private void update(float dt, long now) {
        worldOffset += speed * dt;
        speed += 4.2f * density * dt;
        score += (int) (40f * dt);

        float gravity = 1700f * density;
        velocityY += gravity * dt;
        playerY += velocityY * dt;
        if (playerY >= groundY - playerH) {
            playerY = groundY - playerH;
            velocityY = 0f;
            onGround = true;
        }

        if (now >= nextSpawn) {
            spawnObstacle();
            long gap = 980 + random.nextInt(900);
            gap = Math.max(720, gap - score * 2L);
            nextSpawn = now + gap;
        }

        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle o = it.next();
            o.x -= speed * dt;
            if (o.x + o.w < 0) {
                it.remove();
                score += 10;
            }
        }

        RectF playerHit = new RectF(playerX + playerW * 0.18f, playerY + playerH * 0.10f,
                playerX + playerW * 0.82f, playerY + playerH * 0.96f);
        for (Obstacle o : obstacles) {
            RectF hit = new RectF(o.x + o.w * 0.08f, groundY - o.h + o.h * 0.08f,
                    o.x + o.w * 0.92f, groundY);
            if (RectF.intersects(playerHit, hit)) {
                gameOver = true;
                if (score > best) {
                    best = score;
                    prefs.edit().putInt("best", best).apply();
                }
                break;
            }
        }
    }

    private void spawnObstacle() {
        Obstacle o = new Obstacle();
        o.type = random.nextInt(3);
        o.w = (42 + random.nextInt(30)) * density;
        o.h = (42 + random.nextInt(38)) * density;
        if (o.type == 2) {
            o.w *= 1.20f;
            o.h *= 0.78f;
        }
        o.x = getWidth() + 30 * density;
        obstacles.add(o);
    }

    private void jump() {
        if (onGround && started && !gameOver) {
            velocityY = -720f * density;
            onGround = false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (!started) {
                resetGame(true);
                jump();
            } else if (gameOver) {
                resetGame(true);
            } else {
                jump();
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    private void drawBackground(Canvas c) {
        c.drawColor(Color.rgb(155, 218, 250));

        p.setColor(Color.rgb(247, 235, 159));
        c.drawCircle(getWidth() * 0.83f, getHeight() * 0.16f, getHeight() * 0.07f, p);

        p.setColor(Color.rgb(245, 250, 252));
        float cloudShift = -(worldOffset * 0.12f) % (getWidth() + 280 * density);
        for (int i = -1; i < 4; i++) {
            float x = cloudShift + i * getWidth() * 0.42f;
            float y = getHeight() * (0.14f + (i & 1) * 0.09f);
            c.drawCircle(x, y, 28 * density, p);
            c.drawCircle(x + 30 * density, y - 10 * density, 34 * density, p);
            c.drawCircle(x + 65 * density, y, 27 * density, p);
        }

        p.setColor(Color.rgb(117, 190, 133));
        float hillShift = -(worldOffset * 0.23f) % (340 * density);
        for (int i = -1; i < 7; i++) {
            float x = hillShift + i * 340 * density;
            RectF hill = new RectF(x, groundY - 150 * density, x + 300 * density, groundY + 130 * density);
            c.drawOval(hill, p);
        }

        p.setColor(Color.rgb(78, 159, 101));
        float bushShift = -(worldOffset * 0.44f) % (170 * density);
        for (int i = -1; i < 10; i++) {
            float x = bushShift + i * 170 * density;
            c.drawCircle(x, groundY - 22 * density, 42 * density, p);
            c.drawCircle(x + 45 * density, groundY - 27 * density, 52 * density, p);
            c.drawCircle(x + 90 * density, groundY - 20 * density, 38 * density, p);
        }
    }

    private void drawGround(Canvas c) {
        p.setColor(Color.rgb(106, 184, 84));
        c.drawRect(0, groundY - 12 * density, getWidth(), groundY + 14 * density, p);
        p.setColor(Color.rgb(151, 104, 64));
        c.drawRect(0, groundY + 14 * density, getWidth(), getHeight(), p);

        p.setColor(Color.rgb(126, 83, 53));
        float tile = 42 * density;
        float shift = -(worldOffset % tile);
        for (float x = shift; x < getWidth(); x += tile) {
            c.drawRect(x, groundY + 14 * density, x + 2 * density, getHeight(), p);
        }
    }

    private void drawPlayer(Canvas c) {
        float x = playerX;
        float y = playerY;
        float w = playerW;
        float h = playerH;

        // Legs and brown pants
        p.setColor(Color.rgb(69, 55, 50));
        RectF pants = new RectF(x + w * 0.20f, y + h * 0.58f, x + w * 0.80f, y + h * 0.88f);
        c.drawRoundRect(pants, w * 0.12f, w * 0.12f, p);
        c.drawRect(x + w * 0.22f, y + h * 0.78f, x + w * 0.46f, y + h * 0.97f, p);
        c.drawRect(x + w * 0.54f, y + h * 0.78f, x + w * 0.78f, y + h * 0.97f, p);

        // Black socks/shoes with yellow toes from the photo
        p.setColor(Color.rgb(26, 26, 28));
        c.drawRoundRect(new RectF(x + w * 0.13f, y + h * 0.91f, x + w * 0.47f, y + h), 14, 14, p);
        c.drawRoundRect(new RectF(x + w * 0.53f, y + h * 0.91f, x + w * 0.87f, y + h), 14, 14, p);
        p.setColor(Color.rgb(229, 220, 106));
        c.drawCircle(x + w * 0.16f, y + h * 0.965f, w * 0.07f, p);
        c.drawCircle(x + w * 0.84f, y + h * 0.965f, w * 0.07f, p);

        // Green sweatshirt
        p.setColor(Color.rgb(158, 181, 137));
        RectF body = new RectF(x + w * 0.15f, y + h * 0.30f, x + w * 0.85f, y + h * 0.69f);
        c.drawRoundRect(body, w * 0.18f, w * 0.18f, p);
        c.drawRoundRect(new RectF(x + w * 0.02f, y + h * 0.34f, x + w * 0.27f, y + h * 0.63f), w * 0.12f, w * 0.12f, p);
        c.drawRoundRect(new RectF(x + w * 0.73f, y + h * 0.34f, x + w * 0.98f, y + h * 0.63f), w * 0.12f, w * 0.12f, p);

        // Neck
        p.setColor(Color.rgb(238, 192, 157));
        c.drawRect(x + w * 0.40f, y + h * 0.25f, x + w * 0.60f, y + h * 0.35f, p);

        // Head
        p.setColor(Color.rgb(244, 199, 164));
        c.drawOval(new RectF(x + w * 0.23f, y + h * 0.02f, x + w * 0.77f, y + h * 0.34f), p);

        // Hair
        p.setColor(Color.rgb(86, 63, 49));
        c.drawArc(new RectF(x + w * 0.20f, y, x + w * 0.80f, y + h * 0.27f), 190, 160, true, p);
        for (int i = 0; i < 6; i++) {
            float hx = x + w * (0.28f + i * 0.075f);
            c.drawCircle(hx, y + h * (0.045f + (i % 2) * 0.012f), w * 0.055f, p);
        }

        // Eyes
        p.setColor(Color.rgb(52, 43, 39));
        c.drawCircle(x + w * 0.39f, y + h * 0.17f, w * 0.028f, p);
        c.drawCircle(x + w * 0.61f, y + h * 0.17f, w * 0.028f, p);

        // Smile
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, density * 1.5f));
        c.drawArc(new RectF(x + w * 0.40f, y + h * 0.18f, x + w * 0.60f, y + h * 0.27f), 15, 150, false, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawObstacle(Canvas c, Obstacle o) {
        float y = groundY - o.h;
        if (o.type == 0) {
            p.setColor(Color.rgb(177, 113, 62));
            c.drawRoundRect(new RectF(o.x, y, o.x + o.w, groundY), 8 * density, 8 * density, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4 * density);
            p.setColor(Color.rgb(117, 74, 42));
            c.drawLine(o.x + 8, y + 8, o.x + o.w - 8, groundY - 8, p);
            c.drawLine(o.x + o.w - 8, y + 8, o.x + 8, groundY - 8, p);
            p.setStyle(Paint.Style.FILL);
        } else if (o.type == 1) {
            p.setColor(Color.rgb(99, 108, 118));
            c.drawOval(new RectF(o.x, y + o.h * 0.18f, o.x + o.w, groundY), p);
            p.setColor(Color.rgb(129, 139, 148));
            c.drawCircle(o.x + o.w * 0.62f, y + o.h * 0.40f, o.w * 0.18f, p);
        } else {
            p.setColor(Color.rgb(208, 83, 63));
            c.drawRoundRect(new RectF(o.x + o.w * 0.14f, y + o.h * 0.25f, o.x + o.w * 0.86f, groundY),
                    10 * density, 10 * density, p);
            p.setColor(Color.rgb(238, 105, 73));
            c.drawRoundRect(new RectF(o.x, y, o.x + o.w, y + o.h * 0.36f), 12 * density, 12 * density, p);
        }
    }

    private void drawHud(Canvas c) {
        p.setColor(Color.WHITE);
        p.setTextSize(Math.max(22 * density, getHeight() * 0.047f));
        p.setShadowLayer(5, 2, 2, Color.rgb(40, 70, 90));
        c.drawText("PUNTOS  " + score, 24 * density, 45 * density, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("RÉCORD  " + best, getWidth() - 24 * density, 45 * density, p);
        p.setTextAlign(Paint.Align.LEFT);
        p.clearShadowLayer();
    }

    private void drawStart(Canvas c) {
        p.setColor(Color.argb(150, 20, 45, 60));
        c.drawRoundRect(new RectF(getWidth() * 0.22f, getHeight() * 0.20f,
                getWidth() * 0.78f, getHeight() * 0.66f), 32 * density, 32 * density, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE);
        p.setTextSize(getHeight() * 0.105f);
        c.drawText("EMANUEL RUN", getWidth() / 2f, getHeight() * 0.36f, p);
        p.setTextSize(getHeight() * 0.048f);
        c.drawText("Tocá la pantalla para saltar", getWidth() / 2f, getHeight() * 0.49f, p);
        p.setTextSize(getHeight() * 0.038f);
        c.drawText("Esquivá los obstáculos y llegá cada vez más lejos", getWidth() / 2f, getHeight() * 0.57f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawGameOver(Canvas c) {
        p.setColor(Color.argb(180, 27, 33, 40));
        c.drawRoundRect(new RectF(getWidth() * 0.29f, getHeight() * 0.23f,
                getWidth() * 0.71f, getHeight() * 0.66f), 28 * density, 28 * density, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE);
        p.setTextSize(getHeight() * 0.085f);
        c.drawText("¡UY, EMANUEL!", getWidth() / 2f, getHeight() * 0.38f, p);
        p.setTextSize(getHeight() * 0.052f);
        c.drawText("Puntos: " + score, getWidth() / 2f, getHeight() * 0.49f, p);
        p.setTextSize(getHeight() * 0.038f);
        c.drawText("Tocá para volver a jugar", getWidth() / 2f, getHeight() * 0.59f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private static class Obstacle {
        float x, w, h;
        int type;
    }
}
