package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class GlitchTextView extends View {

    private static final int CYCLE_DURATION = 2500;
    private static final String GLITCH_CHARS = "!@#$%^&*0123456789";
    private static final int GLITCH_START_1 = 92;
    private static final int GLITCH_END_1 = 100;
    private static final int GLITCH_START_2 = 30;
    private static final int GLITCH_END_2 = 35;
    private static final int GLITCH_START_3 = 70;
    private static final int GLITCH_END_3 = 73;

    private String text = "EDUPULSE";
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cyanGhostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint magentaGhostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Rect textBounds = new Rect();
    private boolean isGlitching = false;
    private float glitchOffsetX = 0f;
    private String displayText = "";
    private ValueAnimator cycleAnimator;

    public GlitchTextView(Context context) {
        super(context);
        init(context, null);
    }

    public GlitchTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public GlitchTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        float density = getResources().getDisplayMetrics().density;

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.argb(234, 255, 255, 255));
        textPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        textPaint.setTextSize(28 * density);
        textPaint.setTextAlign(Paint.Align.LEFT);

        cyanGhostPaint.setStyle(Paint.Style.FILL);
        cyanGhostPaint.setColor(Color.argb(80, 0, 229, 255));
        cyanGhostPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        cyanGhostPaint.setTextSize(28 * density);
        cyanGhostPaint.setTextAlign(Paint.Align.LEFT);

        magentaGhostPaint.setStyle(Paint.Style.FILL);
        magentaGhostPaint.setColor(Color.argb(80, 187, 0, 255));
        magentaGhostPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        magentaGhostPaint.setTextSize(28 * density);
        magentaGhostPaint.setTextAlign(Paint.Align.LEFT);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GlitchTextView);
            String glitchText = a.getString(R.styleable.GlitchTextView_glitchText);
            if (glitchText != null) {
                text = glitchText;
            }
            a.recycle();
        }

        displayText = text;

        cycleAnimator = ValueAnimator.ofFloat(0f, 100f);
        cycleAnimator.setDuration(CYCLE_DURATION);
        cycleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        cycleAnimator.addUpdateListener(a -> {
            float progress = (float) a.getAnimatedValue();
            updateGlitch(progress);
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        cycleAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cycleAnimator.cancel();
    }

    private void updateGlitch(float progress) {
        boolean inGlitchZone = false;
        float offset = 0f;

        if (progress >= GLITCH_START_1 && progress <= GLITCH_END_1) {
            inGlitchZone = true;
            offset = (progress - GLITCH_START_1) / (GLITCH_END_1 - GLITCH_START_1);
        } else if (progress >= GLITCH_START_2 && progress <= GLITCH_END_2) {
            inGlitchZone = true;
            offset = (progress - GLITCH_START_2) / (GLITCH_END_2 - GLITCH_START_2);
        } else if (progress >= GLITCH_START_3 && progress <= GLITCH_END_3) {
            inGlitchZone = true;
            offset = (progress - GLITCH_START_3) / (GLITCH_END_3 - GLITCH_START_3);
        }

        if (inGlitchZone) {
            isGlitching = true;
            float density = getResources().getDisplayMetrics().density;
            glitchOffsetX = (random.nextFloat() - 0.5f) * 8 * density;

            StringBuilder sb = new StringBuilder(text);
            for (int i = 0; i < sb.length(); i++) {
                if (random.nextFloat() < 0.3f) {
                    sb.setCharAt(i, GLITCH_CHARS.charAt(random.nextInt(GLITCH_CHARS.length())));
                }
            }
            displayText = sb.toString();
        } else {
            isGlitching = false;
            glitchOffsetX = 0f;
            displayText = text;
        }

        invalidate();
    }

    public void setGlitchText(String text) {
        this.text = text;
        this.displayText = text;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        textPaint.setTextAlign(Paint.Align.CENTER);
        cyanGhostPaint.setTextAlign(Paint.Align.CENTER);
        magentaGhostPaint.setTextAlign(Paint.Align.CENTER);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = centerY - (fm.ascent + fm.descent) / 2f;

        if (isGlitching) {
            canvas.drawText(displayText, centerX - 2f + glitchOffsetX, textY, cyanGhostPaint);
            canvas.drawText(displayText, centerX + 2f - glitchOffsetX, textY, magentaGhostPaint);
            canvas.drawText(displayText, centerX, textY, textPaint);
        } else {
            canvas.drawText(displayText, centerX, textY, textPaint);
        }
    }
}
