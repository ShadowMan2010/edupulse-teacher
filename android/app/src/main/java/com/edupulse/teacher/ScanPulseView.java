package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public class ScanPulseView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float ringRadius = 0f;
    private float ringAlpha = 1f;

    public ScanPulseView(Context context) { super(context); init(); }
    public ScanPulseView(Context context, android.util.AttributeSet attrs) { super(context, attrs); init(); }
    public ScanPulseView(Context context, android.util.AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
        ringPaint.setColor(0xFF00FFFF);
        startPulsing();
    }

    private void startPulsing() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(3000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            ringRadius = t * 180f;
            ringAlpha = 1f - t;
            invalidate();
        });
        anim.setStartDelay(1500);
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        ringPaint.setAlpha((int) (ringAlpha * 120));
        canvas.drawCircle(cx, cy, ringRadius, ringPaint);

        ringPaint.setAlpha((int) (ringAlpha * 60));
        canvas.drawCircle(cx, cy, ringRadius * 0.7f, ringPaint);
    }
}
