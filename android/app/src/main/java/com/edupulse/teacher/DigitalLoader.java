package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

public class DigitalLoader extends View {

    private static final String CHARS = "0123456789ABCDEF";

    private final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerCharPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitCharPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanlinePaint = new Paint();

    private float ringScale = 1f;
    private float rotation = 0f;
    private float progress = 0f;
    private char centerChar = '0';
    private final Random random = new Random();
    private ValueAnimator scaleAnimator;
    private ValueAnimator progressAnimator;
    private ValueAnimator charAnimator;

    public DigitalLoader(Context context) {
        super(context);
        init();
    }

    public DigitalLoader(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DigitalLoader(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(2 * density);
        outerRingPaint.setColor(Color.argb(40, 0, 229, 255));
        outerRingPaint.setStrokeCap(Paint.Cap.ROUND);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(2.5f * density);
        arcPaint.setColor(Color.argb(255, 0, 229, 255));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        innerArcPaint.setStyle(Paint.Style.STROKE);
        innerArcPaint.setStrokeWidth(1.5f * density);
        innerArcPaint.setColor(Color.argb(180, 0, 229, 255));
        innerArcPaint.setStrokeCap(Paint.Cap.ROUND);

        centerCharPaint.setTextAlign(Paint.Align.CENTER);
        centerCharPaint.setColor(Color.argb(255, 0, 229, 255));
        centerCharPaint.setTypeface(null);

        orbitCharPaint.setTextAlign(Paint.Align.CENTER);
        orbitCharPaint.setColor(Color.argb(120, 0, 229, 255));
        orbitCharPaint.setTextSize(8 * density);

        scanlinePaint.setStyle(Paint.Style.STROKE);
        scanlinePaint.setStrokeWidth(1);
        scanlinePaint.setColor(Color.argb(8, 255, 255, 255));

        scaleAnimator = ValueAnimator.ofFloat(0.9f, 1.1f);
        scaleAnimator.setDuration(1500);
        scaleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scaleAnimator.setRepeatMode(ValueAnimator.REVERSE);
        scaleAnimator.addUpdateListener(a -> {
            ringScale = (float) a.getAnimatedValue();
            invalidate();
        });

        progressAnimator = ValueAnimator.ofFloat(0f, 360f);
        progressAnimator.setDuration(2000);
        progressAnimator.setRepeatCount(ValueAnimator.INFINITE);
        progressAnimator.setInterpolator(new LinearInterpolator());
        progressAnimator.addUpdateListener(a -> {
            rotation = (float) a.getAnimatedValue();
            invalidate();
        });

        charAnimator = ValueAnimator.ofFloat(0f, 1f);
        charAnimator.setDuration(500);
        charAnimator.setRepeatCount(ValueAnimator.INFINITE);
        charAnimator.addUpdateListener(a -> {
            centerChar = CHARS.charAt(random.nextInt(CHARS.length()));
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scaleAnimator.start();
        progressAnimator.start();
        charAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        scaleAnimator.cancel();
        progressAnimator.cancel();
        charAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float baseRadius = Math.min(getWidth(), getHeight()) / 2f - 4 * density;

        canvas.save();
        canvas.scale(ringScale, ringScale, cx, cy);

        canvas.drawCircle(cx, cy, baseRadius, outerRingPaint);

        RectF arcRect = new RectF(cx - baseRadius, cy - baseRadius, cx + baseRadius, cy + baseRadius);
        canvas.drawArc(arcRect, rotation - 90, 270f, false, arcPaint);

        float innerRadius = baseRadius * 0.7f;
        RectF innerArcRect = new RectF(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius);
        canvas.drawArc(innerArcRect, rotation - 90 + 45, 180f, false, innerArcPaint);

        canvas.restore();

        float centerTextSize = 16 * density;
        centerCharPaint.setTextSize(centerTextSize);
        Paint.FontMetrics fm = centerCharPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.valueOf(centerChar), cx, textY, centerCharPaint);

        for (int i = 0; i < 4; i++) {
            float angle = (rotation + i * 90f);
            float rad = (float) Math.toRadians(angle);
            float orbitRadius = baseRadius + 8 * density;
            float ox = cx + (float) Math.cos(rad) * orbitRadius;
            float oy = cy + (float) Math.sin(rad) * orbitRadius;
            canvas.drawText(String.valueOf(CHARS.charAt(random.nextInt(CHARS.length()))), ox, oy, orbitCharPaint);
        }

        int h = getHeight();
        int w = getWidth();
        int scanlineSpacing = (int) (4 * density);
        for (int y = 0; y < h; y += scanlineSpacing) {
            canvas.drawLine(0, y, w, y, scanlinePaint);
        }
    }
}
