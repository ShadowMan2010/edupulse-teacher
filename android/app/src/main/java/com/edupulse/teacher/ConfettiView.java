package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ConfettiView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Confetti> pieces = new ArrayList<>();
    private long startTime;

    private static final int[] COLORS = {
        0xFF00FFFF, 0xFFFF00FF, 0xFF9B59B6, 0xFF3498DB,
        0xFF00FF88, 0xFFFF6600, 0xFFFFFF00
    };

    private static class Confetti {
        float x, y, vx, vy, size, rotation, rotSpeed;
        int color;
    }

    public ConfettiView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
    }

    public void burst() {
        pieces.clear();
        for (int i = 0; i < 60; i++) {
            Confetti c = new Confetti();
            c.x = getWidth() / 2f;
            c.y = getHeight() / 2f;
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 300 + random.nextFloat() * 600;
            c.vx = (float) (Math.cos(angle) * speed);
            c.vy = (float) (Math.sin(angle) * speed) - 200;
            c.size = 6 + random.nextFloat() * 10;
            c.color = COLORS[random.nextInt(COLORS.length)];
            c.rotation = random.nextFloat() * 360;
            c.rotSpeed = (random.nextFloat() - 0.5f) * 360;
            pieces.add(c);
        }
        startTime = System.currentTimeMillis();

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2000);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> invalidate());
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float elapsed = (System.currentTimeMillis() - startTime) / 1000f;
        if (elapsed > 2f) return;

        for (Confetti c : pieces) {
            float t = elapsed;
            float x = c.x + c.vx * t;
            float y = c.y + c.vy * t + 400 * t * t;
            float alpha = Math.max(0, 1 - t / 2f);
            int color = (c.color & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
            paint.setColor(color);
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(c.rotation + c.rotSpeed * t);
            canvas.drawRoundRect(-c.size / 2, -c.size / 4, c.size / 2, c.size / 4, 3, 3, paint);
            canvas.restore();
        }
        invalidate();
    }
}
