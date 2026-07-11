package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class ScanPulseView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pulseProgress = 0f;
    private ValueAnimator pulseAnimator;

    private static final int MAX_RADIUS_PX = 180;
    private static final long CYCLE_DURATION = 3000;
    private static final long INITIAL_DELAY = 1500;

    public ScanPulseView(Context context) {
        super(context);
        init();
    }

    public ScanPulseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScanPulseView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setColor(Color.argb(60, 0, 229, 255));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(CYCLE_DURATION);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.setStartDelay(INITIAL_DELAY);
        pulseAnimator.addUpdateListener(a -> {
            pulseProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnimator != null) pulseAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = MAX_RADIUS_PX * density;

        float radius = pulseProgress * maxRadius;
        int alpha = (int) ((1f - pulseProgress) * 60);
        ringPaint.setAlpha(alpha);

        float strokeWidth = (1f - pulseProgress) * 4f * density;
        if (strokeWidth > 0.5f) {
            ringPaint.setStrokeWidth(strokeWidth);
            canvas.drawCircle(cx, cy, radius, ringPaint);
        }
    }
}
