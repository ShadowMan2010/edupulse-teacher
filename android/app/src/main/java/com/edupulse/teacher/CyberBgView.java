package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CyberBgView extends View {

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private float animProgress = 0f;
    private final List<Particle> particles = new ArrayList<>();
    private final List<HexShape> hexagons = new ArrayList<>();

    private static final int[][] COLOR_PAIRS = {
        {0xFF00FFFF, 0xFF9B59B6},
        {0xFF9B59B6, 0xFFFF00FF},
        {0xFFFF00FF, 0xFF3498DB},
        {0xFF3498DB, 0xFF00FFFF},
    };

    private static class Particle {
        float x, y, size, speed, alpha, wave;
        int color;
    }

    private static class HexShape {
        float x, y, size, rotation, rotSpeed, alpha;
        float driftX, driftY;
    }

    public CyberBgView(Context context) {
        super(context);
        init();
    }

    private void init() {
        particlePaint.setStyle(Paint.Style.FILL);
        hexPaint.setStyle(Paint.Style.STROKE);
        hexPaint.setStrokeWidth(1.5f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.5f);
        gridPaint.setColor(0x0800FFFF);

        makeParticles();
        makeHexagons();

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 4f);
        anim.setDuration(16000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            animProgress = a.getAnimatedFraction() * 4f;
            updateParticles();
            updateHexagons();
            invalidate();
        });
        anim.start();
    }

    private void makeParticles() {
        particles.clear();
        for (int i = 0; i < 40; i++) {
            Particle p = new Particle();
            p.x = random.nextFloat() * 2000;
            p.y = random.nextFloat() * 2000;
            p.size = 1.5f + random.nextFloat() * 3.5f;
            p.speed = 0.2f + random.nextFloat() * 0.6f;
            p.alpha = 0.08f + random.nextFloat() * 0.25f;
            p.wave = random.nextFloat() * 100f;
            p.color = random.nextBoolean() ? 0xFF00FFFF : 0xFFFF00FF;
            particles.add(p);
        }
    }

    private void makeHexagons() {
        hexagons.clear();
        for (int i = 0; i < 8; i++) {
            HexShape h = new HexShape();
            h.x = random.nextFloat() * 2000;
            h.y = random.nextFloat() * 2000;
            h.size = 20 + random.nextFloat() * 40;
            h.rotation = random.nextFloat() * 360;
            h.rotSpeed = (random.nextFloat() - 0.5f) * 2f;
            h.alpha = 0.02f + random.nextFloat() * 0.06f;
            h.driftX = (random.nextFloat() - 0.5f) * 0.3f;
            h.driftY = (random.nextFloat() - 0.5f) * 0.3f - 0.2f;
            hexagons.add(h);
        }
    }

    private void updateParticles() {
        for (Particle p : particles) {
            p.y -= p.speed;
            p.x += (float) Math.sin(p.y * 0.02f + p.wave) * 0.6f;
            if (p.y < -20) {
                p.y = getHeight() + 20;
                p.x = random.nextFloat() * getWidth();
            }
        }
    }

    private void updateHexagons() {
        for (HexShape h : hexagons) {
            h.x += h.driftX;
            h.y += h.driftY;
            h.rotation += h.rotSpeed;
            if (h.y < -60) {
                h.y = getHeight() + 60;
                h.x = random.nextFloat() * getWidth();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        int idx = (int) animProgress % 4;
        float frac = animProgress - idx;
        int c1 = COLOR_PAIRS[idx][0];
        int c2 = COLOR_PAIRS[idx][1];
        int c3 = COLOR_PAIRS[(idx + 1) % 4][0];

        int col1 = lerpColor(c1, c2, frac);
        int col2 = lerpColor(c2, c3, frac);

        LinearGradient gradient = new LinearGradient(
            0, 0, w, h,
            new int[]{col1, col2, 0xFF0a0a0b},
            new float[]{0f, 0.35f, 1f},
            Shader.TileMode.CLAMP
        );
        gradientPaint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, gradientPaint);

        for (int i = 0; i < w; i += 40) {
            canvas.drawLine(i, 0, i, h, gridPaint);
        }
        for (int i = 0; i < h; i += 40) {
            canvas.drawLine(0, i, w, i, gridPaint);
        }

        for (HexShape hex : hexagons) {
            hexPaint.setColor((0x08FFFFFF & 0x00FFFFFF) | ((int) (hex.alpha * 255) << 24));
            canvas.save();
            canvas.translate(hex.x, hex.y);
            canvas.rotate(hex.rotation);
            drawHexagon(canvas, hex.size, hexPaint);
            canvas.restore();
        }

        for (Particle p : particles) {
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.size, particlePaint);
        }
    }

    private void drawHexagon(Canvas canvas, float size, Paint paint) {
        Path path = new Path();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 3 * i - Math.PI / 6;
            float x = (float) (size * Math.cos(angle));
            float y = (float) (size * Math.sin(angle));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, paint);
    }

    private int lerpColor(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int b2 = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, b2);
    }
}
