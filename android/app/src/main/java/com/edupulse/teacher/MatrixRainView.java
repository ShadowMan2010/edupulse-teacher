package com.edupulse.teacher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class MatrixRainView extends View {

    private static final String CHARS = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ{}[]<>/\\|!@#$%^&*()";
    private static final int COLUMN_WIDTH_DP = 20;
    private static final int SPEED = 50;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanlinePaint = new Paint();
    private final Random random = new Random();
    private int columnCount;
    private int columnWidth;
    private int[] columns;
    private float[] speeds;
    private boolean running = true;

    public MatrixRainView(Context context) {
        super(context);
        init();
    }

    public MatrixRainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MatrixRainView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        columnWidth = (int) (COLUMN_WIDTH_DP * density);

        textPaint.setTextSize(12 * density);
        textPaint.setColor(Color.argb(160, 0, 229, 255));
        textPaint.setTypeface(null);
        textPaint.setStyle(Paint.Style.FILL);

        scanlinePaint.setStyle(Paint.Style.STROKE);
        scanlinePaint.setStrokeWidth(1);
        scanlinePaint.setColor(Color.argb(10, 255, 255, 255));

        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        columnCount = w / columnWidth + 1;
        columns = new int[columnCount];
        speeds = new float[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columns[i] = random.nextInt(h);
            speeds[i] = 1f + random.nextFloat() * 2f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running || columns == null) return;

        float density = getResources().getDisplayMetrics().density;
        int h = getHeight();
        int w = getWidth();

        for (int col = 0; col < columnCount; col++) {
            float y = columns[col];
            float x = col * columnWidth;

            int charIndex = random.nextInt(CHARS.length());
            char c = CHARS.charAt(charIndex);

            int alpha = (int) (80 + 175 * (1f - (y / h)));
            textPaint.setColor(Color.argb(Math.min(255, alpha), 0, 229, 255));
            canvas.drawText(String.valueOf(c), x, y, textPaint);

            if (random.nextInt(10) < 1) {
                textPaint.setColor(Color.argb(255, 0, 229, 255));
                canvas.drawText(String.valueOf(CHARS.charAt(random.nextInt(CHARS.length()))), x, y, textPaint);
            }

            columns[col] = (int) (y + speeds[col] * density * 0.5f);
            if (columns[col] > h + columnWidth) {
                columns[col] = -columnWidth;
                speeds[col] = 1f + random.nextFloat() * 2f;
            }
        }

        int scanlineSpacing = (int) (4 * density);
        for (int y = 0; y < h; y += scanlineSpacing) {
            canvas.drawLine(0, y, w, y, scanlinePaint);
        }

        postInvalidateDelayed(SPEED);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        postInvalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
    }
}
