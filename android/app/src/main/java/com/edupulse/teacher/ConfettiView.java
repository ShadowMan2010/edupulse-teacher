package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

public class ConfettiView extends View {

    private static final int PARTICLE_COUNT = 60;
    private static final long DURATION = 2000;
    private static final float GRAVITY = 400f;

    private final Paint[] particlePaints;
    private final float[][] particles;
    private final int[] colors;
    private final Random random = new Random();
    private float progress = 0f;
    private boolean running = false;
    private ValueAnimator animator;

    public ConfettiView(Context context) {
        super(context);
        particlePaints = new Paint[PARTICLE_COUNT];
        particles = new float[PARTICLE_COUNT][8];
        colors = new int[]{0xFF00E5FF, 0xFF00FF88, 0xFFBB00FF, 0xFFFF3B3B, 0xFFf59e0b, 0xFFFFFFFF};
        initPaints();
    }

    public ConfettiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        particlePaints = new Paint[PARTICLE_COUNT];
        particles = new float[PARTICLE_COUNT][8];
        colors = new int[]{0xFF00E5FF, 0xFF00FF88, 0xFFBB00FF, 0xFFFF3B3B, 0xFFf59e0b, 0xFFFFFFFF};
        initPaints();
    }

    public ConfettiView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        particlePaints = new Paint[PARTICLE_COUNT];
        particles = new float[PARTICLE_COUNT][8];
        colors = new int[]{0xFF00E5FF, 0xFF00FF88, 0xFFBB00FF, 0xFFFF3B3B, 0xFFf59e0b, 0xFFFFFFFF};
        initPaints();
    }

    private void initPaints() {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particlePaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            particlePaints[i].setStyle(Paint.Style.FILL);
            particlePaints[i].setColor(colors[random.nextInt(colors.length)]);
        }
    }

    public void burst() {
        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float angle = (float) Math.toRadians(random.nextFloat() * 360);
            float speed = 100 + random.nextFloat() * 300;
            particles[i][0] = cx;
            particles[i][1] = cy;
            particles[i][2] = (float) Math.cos(angle) * speed;
            particles[i][3] = (float) Math.sin(angle) * speed;
            particles[i][4] = 4 + random.nextFloat() * 6;
            particles[i][5] = 2 + random.nextFloat() * 4;
            particles[i][6] = random.nextFloat() * 720;
            particles[i][7] = random.nextFloat() * 200 - 100;
            particlePaints[i].setColor(colors[random.nextInt(colors.length)]);
        }

        progress = 0f;
        running = true;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running) return;

        float dt = 0.016f;
        progress += dt / (DURATION / 1000f);

        if (progress >= 1f) {
            running = false;
            return;
        }

        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float px = particles[i][0] + particles[i][2] * dt;
            float py = particles[i][1] + particles[i][3] * dt + 0.5f * GRAVITY * dt * dt * density;
            particles[i][3] += GRAVITY * dt * density;
            particles[i][0] = px;
            particles[i][1] = py;
            particles[i][6] += particles[i][7] * dt;

            int alpha = (int) ((1f - progress) * 255);
            particlePaints[i].setAlpha(alpha);

            canvas.save();
            canvas.rotate(particles[i][6], px, py);
            float rw = particles[i][4] * density;
            float rh = particles[i][5] * density;
            canvas.drawRect(px - rw / 2, py - rh / 2, px + rw / 2, py + rh / 2, particlePaints[i]);
            canvas.restore();
        }

        postInvalidateDelayed(16);
    }
}
