package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

public class DigitalLoader extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint charPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanlinePaint = new Paint();
    private final Random random = new Random();
    private final RectF arcRect = new RectF();

    private float rotation = 0f;
    private float pulseScale = 1f;
    private float innerProgress = 0f;
    private char currentChar = '>';

    private static final String CHARS = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ{}[]<>/\\|!@#$%^&*()_+-=";

    public DigitalLoader(Context context) {
        super(context);
        init();
    }
    public DigitalLoader(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public DigitalLoader(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4f);
        ringPaint.setColor(Color.parseColor("#00E5FF"));
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        ringGlowPaint.setStyle(Paint.Style.STROKE);
        ringGlowPaint.setStrokeWidth(8f);
        ringGlowPaint.setColor(Color.parseColor("#2200E5FF"));
        ringGlowPaint.setStrokeCap(Paint.Cap.ROUND);

        innerArcPaint.setStyle(Paint.Style.STROKE);
        innerArcPaint.setStrokeWidth(3f);
        innerArcPaint.setColor(Color.parseColor("#007BFF"));
        innerArcPaint.setStrokeCap(Paint.Cap.ROUND);

        charPaint.setStyle(Paint.Style.FILL);
        charPaint.setColor(Color.parseColor("#00E5FF"));
        charPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        charPaint.setTextAlign(Paint.Align.CENTER);

        scanlinePaint.setStyle(Paint.Style.STROKE);
        scanlinePaint.setStrokeWidth(1f);
        scanlinePaint.setColor(Color.parseColor("#08000000"));

        ValueAnimator rotAnim = ValueAnimator.ofFloat(0f, 360f);
        rotAnim.setDuration(2000);
        rotAnim.setRepeatCount(ValueAnimator.INFINITE);
        rotAnim.setInterpolator(new LinearInterpolator());
        rotAnim.addUpdateListener(a -> {
            rotation = (float) a.getAnimatedValue();
            invalidate();
        });
        rotAnim.start();

        ValueAnimator pulseAnim = ValueAnimator.ofFloat(0.9f, 1.1f);
        pulseAnim.setDuration(800);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnim.setInterpolator(new LinearInterpolator());
        pulseAnim.addUpdateListener(a -> {
            pulseScale = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnim.start();

        ValueAnimator progressAnim = ValueAnimator.ofFloat(0f, 1f);
        progressAnim.setDuration(3000);
        progressAnim.setRepeatCount(ValueAnimator.INFINITE);
        progressAnim.setInterpolator(new LinearInterpolator());
        progressAnim.addUpdateListener(a -> {
            innerProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        progressAnim.start();

        postDelayed(new Runnable() {
            @Override
            public void run() {
                currentChar = CHARS.charAt(random.nextInt(CHARS.length()));
                postDelayed(this, 100 + random.nextInt(300));
            }
        }, 200);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        int radius = Math.min(w, h) / 2 - 12;

        canvas.save();
        canvas.scale(pulseScale, pulseScale, cx, cy);

        // Glow ring
        ringGlowPaint.setAlpha(15);
        canvas.drawCircle(cx, cy, radius, ringGlowPaint);

        // Outer ring with gap
        float startAngle = rotation;
        float sweepAngle = 320f + (float) (Math.sin(innerProgress * Math.PI * 2) * 30f);
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        ringPaint.setAlpha(200);
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, ringPaint);

        // Inner progress arc (opposite direction)
        float innerStart = -rotation * 0.7f;
        float innerSweep = 60f + innerProgress * 180f;
        innerArcPaint.setAlpha(120);
        int innerR = (int) (radius * 0.7f);
        RectF innerRect = new RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR);
        canvas.drawArc(innerRect, innerStart, innerSweep, false, innerArcPaint);

        // Matrix character in center
        float charSize = radius * 0.5f;
        charPaint.setTextSize(charSize);
        charPaint.setAlpha(180);
        canvas.drawText(String.valueOf(currentChar), cx, cy + charSize * 0.35f, charPaint);

        // Small secondary chars around ring
        charPaint.setTextSize(9f);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(rotation * 0.5f + i * 45f);
            float cx2 = cx + (float) (radius * 1.25f * Math.cos(angle));
            float cy2 = cy + (float) (radius * 1.25f * Math.sin(angle));
            charPaint.setAlpha(40 + (int) (Math.sin(innerProgress * Math.PI * 2 + i) * 30));
            canvas.drawText(
                String.valueOf(CHARS.charAt((int) (System.currentTimeMillis() / 200 + i * 7) % CHARS.length())),
                cx2, cy2, charPaint);
        }

        canvas.restore();

        // Scan lines overlay
        scanlinePaint.setAlpha(6);
        for (int i = 0; i < h; i += 4) {
            canvas.drawLine(0, i, w, i, scanlinePaint);
        }
    }
}
