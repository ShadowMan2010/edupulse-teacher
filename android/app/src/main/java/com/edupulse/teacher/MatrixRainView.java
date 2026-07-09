package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MatrixRainView extends View {

    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanlinePaint = new Paint();
    private final Random random = new Random();
    private final List<Drop> drops = new ArrayList<>();
    private int columnCount = 0;
    private float charSize;

    private static final String CHARS = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ{}[]<>/\\|!@#$%^&*()";

    private static class Drop {
        int col;
        float y;
        float speed;
        int length;
        float alpha;
        char[] chars;
    }

    public MatrixRainView(Context context) {
        super(context);
        init();
    }
    public MatrixRainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public MatrixRainView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        rainPaint.setStyle(Paint.Style.FILL);
        rainPaint.setColor(Color.parseColor("#00E5FF"));
        rainPaint.setTypeface(Typeface.MONOSPACE);

        headPaint.setStyle(Paint.Style.FILL);
        headPaint.setColor(Color.parseColor("#CCFFFFFF"));
        headPaint.setTypeface(Typeface.MONOSPACE);

        scanlinePaint.setStyle(Paint.Style.STROKE);
        scanlinePaint.setStrokeWidth(1f);
        scanlinePaint.setColor(Color.parseColor("#05000000"));

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(50);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            updateDrops();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        charSize = Math.max(12, w / 40f);
        rainPaint.setTextSize(charSize);
        headPaint.setTextSize(charSize);
        columnCount = (int) (w / (charSize * 0.8f));
        drops.clear();
        for (int i = 0; i < columnCount; i++) {
            drops.add(createDrop(h));
        }
    }

    private Drop createDrop(int h) {
        Drop d = new Drop();
        d.col = random.nextInt(columnCount);
        d.y = -random.nextFloat() * h;
        d.speed = 4f + random.nextFloat() * 10f;
        d.length = 5 + random.nextInt(15);
        d.alpha = 0.15f + random.nextFloat() * 0.25f;
        d.chars = new char[d.length];
        for (int j = 0; j < d.length; j++) {
            d.chars[j] = CHARS.charAt(random.nextInt(CHARS.length()));
        }
        return d;
    }

    private void updateDrops() {
        int h = getHeight();
        if (h <= 0) return;
        for (Drop d : drops) {
            d.y += d.speed;
            if (random.nextFloat() < 0.05f) {
                int pos = random.nextInt(d.length);
                d.chars[pos] = CHARS.charAt(random.nextInt(CHARS.length()));
            }
            if (d.y - d.length * charSize > h) {
                d.y = -d.length * charSize;
                d.speed = 4f + random.nextFloat() * 10f;
                d.length = 5 + random.nextInt(15);
                d.alpha = 0.15f + random.nextFloat() * 0.25f;
                d.chars = new char[d.length];
                for (int j = 0; j < d.length; j++) {
                    d.chars[j] = CHARS.charAt(random.nextInt(CHARS.length()));
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || drops.isEmpty()) return;

        float xStep = (float) w / columnCount;

        for (Drop d : drops) {
            float x = d.col * xStep;
            for (int i = 0; i < d.length; i++) {
                float y = d.y - i * charSize;
                if (y < -charSize || y > h + charSize) continue;

                char c = d.chars[i % d.chars.length];
                float fade = 1f - (float) i / d.length;
                int alpha = (int) (d.alpha * fade * 255);
                alpha = Math.max(0, Math.min(255, alpha));

                if (i == 0) {
                    headPaint.setAlpha(220);
                    headPaint.setColor(Color.parseColor("#FFFFFF"));
                    canvas.drawText(String.valueOf(c), x, y, headPaint);
                } else {
                    rainPaint.setAlpha(alpha);
                    canvas.drawText(String.valueOf(c), x, y, rainPaint);
                }
            }
        }

        // CRT scan lines overlay
        scanlinePaint.setAlpha(8);
        for (int i = 0; i < h; i += 3) {
            canvas.drawLine(0, i, w, i, scanlinePaint);
        }
    }
}
