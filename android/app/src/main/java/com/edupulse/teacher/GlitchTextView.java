package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

public class GlitchTextView extends View {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glitchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glitchPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    private String text = "EDUPULSE";
    private String glitchText = "";
    private float textSize = 28f;
    private int textColor = Color.parseColor("#00ffff");
    private Typeface typeface;

    private boolean glitching = false;
    private float glitchOffsetX = 3f;
    private float glitchOffsetY = 2f;
    private int glitchSliceY = 0;
    private int glitchSliceHeight = 0;
    private boolean showGlitchSlice = false;
    private boolean showGlitchCopy = false;
    private int glitchR = 255, glitchG = 0, glitchB = 255;

    public GlitchTextView(Context context) {
        super(context);
        init();
    }
    public GlitchTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public GlitchTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
        textPaint.setSubpixelText(true);
        textPaint.setLetterSpacing(0.15f);

        glitchPaint.setStyle(Paint.Style.FILL);
        glitchPaint.setTextSize(textSize);
        glitchPaint.setSubpixelText(true);
        glitchPaint.setAlpha(120);

        glitchPaint2.setStyle(Paint.Style.FILL);
        glitchPaint2.setTextSize(textSize);
        glitchPaint2.setSubpixelText(true);
        glitchPaint2.setAlpha(100);

        startGlitchAnimation();
    }

    public void setText(String t) {
        this.text = t;
        requestLayout();
        invalidate();
    }

    public void setTextColor(int color) {
        this.textColor = color;
        textPaint.setColor(color);
        invalidate();
    }

    public void setTextSize(float size) {
        this.textSize = size;
        textPaint.setTextSize(size);
        glitchPaint.setTextSize(size);
        glitchPaint2.setTextSize(size);
        requestLayout();
        invalidate();
    }

    public void setTypeface(Typeface tf) {
        this.typeface = tf;
        textPaint.setTypeface(tf);
        glitchPaint.setTypeface(tf);
        glitchPaint2.setTypeface(tf);
        invalidate();
    }

    private void startGlitchAnimation() {
        ValueAnimator glitchTrigger = ValueAnimator.ofFloat(0f, 1f);
        glitchTrigger.setDuration(2500);
        glitchTrigger.setRepeatCount(ValueAnimator.INFINITE);
        glitchTrigger.setInterpolator(new LinearInterpolator());
        glitchTrigger.addUpdateListener(a -> {
            float v = a.getAnimatedFraction();

            boolean shouldGlitch = v > 0.92f || (v > 0.3f && v < 0.35f) || (v > 0.7f && v < 0.73f);

            if (shouldGlitch && !glitching) {
                glitching = true;
                triggerGlitch();
            } else if (!shouldGlitch) {
                glitching = false;
                showGlitchCopy = false;
                showGlitchSlice = false;
                invalidate();
            }
        });
        glitchTrigger.start();
    }

    private void triggerGlitch() {
        int textHeight = getTextHeight();

        glitchOffsetX = 2f + random.nextFloat() * 6f;
        glitchOffsetY = 1f + random.nextFloat() * 3f;
        glitchSliceY = random.nextInt(Math.max(1, textHeight));
        glitchSliceHeight = 3 + random.nextInt(Math.max(2, textHeight / 4));
        showGlitchSlice = true;
        showGlitchCopy = random.nextBoolean();
        glitchR = 0;
        glitchG = random.nextBoolean() ? 255 : 0;
        glitchB = 255;

        StringBuilder sb = new StringBuilder(text);
        if (random.nextFloat() < 0.3f) {
            int pos = random.nextInt(text.length());
            sb.setCharAt(pos, CHARS.charAt(random.nextInt(CHARS.length())));
        }
        glitchText = sb.toString();

        invalidate();

        postDelayed(() -> {
            showGlitchCopy = false;
            showGlitchSlice = false;
            invalidate();
        }, 80 + random.nextInt(120));
    }

    private static final String CHARS = "!@#$%^&*()_+-={}[]|\\:;\"'<>,.?/~`";

    private int getTextHeight() {
        Rect bounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), bounds);
        return bounds.height();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Rect bounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), bounds);
        int w = bounds.width() + getPaddingLeft() + getPaddingRight() + 20;
        int h = bounds.height() + getPaddingTop() + getPaddingBottom() + 10;
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int startX = getPaddingLeft();
        int startY = getPaddingTop() + Math.abs((int) textPaint.ascent());

        if (showGlitchSlice) {
            float sliceStart = startY + glitchSliceY;

            glitchPaint.setColor(Color.argb(100, glitchR, glitchG, glitchB));
            glitchPaint.setTextSize(textSize - 1f);
            float lx = startX + (random.nextBoolean() ? -glitchOffsetX : glitchOffsetX);
            float ly = sliceStart;
            canvas.save();
            canvas.clipRect(0, sliceStart, getWidth(), sliceStart + glitchSliceHeight);
            canvas.drawText(text, lx, startY, glitchPaint);
            canvas.restore();
        }

        if (showGlitchCopy) {
            glitchPaint2.setColor(Color.argb(80, 0, 255, 255));
            glitchPaint2.setTextSize(textSize + 1f);
            float gx = startX + (random.nextBoolean() ? -glitchOffsetX * 1.5f : glitchOffsetX * 1.5f);
            float gy = startY + (random.nextBoolean() ? -glitchOffsetY : glitchOffsetY);
            canvas.drawText(text, gx, gy, glitchPaint2);

            glitchPaint2.setColor(Color.argb(60, 255, 0, 255));
            float gx2 = startX + (random.nextBoolean() ? -glitchOffsetX * 2f : glitchOffsetX * 2f);
            float gy2 = startY + (random.nextBoolean() ? -glitchOffsetY * 2f : glitchOffsetY * 2f);
            canvas.drawText(text, gx2, gy2, glitchPaint2);
        }

        textPaint.setColor(textColor);
        canvas.drawText(text, startX, startY, textPaint);
    }
}
