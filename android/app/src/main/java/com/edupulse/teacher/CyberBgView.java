package com.edupulse.teacher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

public class CyberBgView extends View {

    private static final int PARTICLE_COUNT = 40;
    private static final int HEXAGON_COUNT = 8;
    private static final int GRID_SPACING_PX = 40;

    private final Paint gradientPaint = new Paint();
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hexagonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();

    private final Random random = new Random();
    private final float[][] particles = new float[PARTICLE_COUNT][4];
    private final float[][] hexagons = new float[HEXAGON_COUNT][4];
    private final int[] hexagonSides = new int[HEXAGON_COUNT];
    private float gradientPhase = 0f;
    private ValueAnimator gradientAnimator;

    private static final int[][] COLOR_PAIRS = {
            {0xFF000000, 0xFF001a1a},
            {0xFF001a1a, 0xFF001a33},
            {0xFF001a33, 0xFF000000},
            {0xFF000000, 0xFF0a001a},
    };

    public CyberBgView(Context context) {
        super(context);
        init();
    }

    public CyberBgView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CyberBgView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        particlePaint.setStyle(Paint.Style.FILL);
        particlePaint.setColor(Color.argb(60, 0, 229, 255));

        hexagonPaint.setStyle(Paint.Style.STROKE);
        hexagonPaint.setStrokeWidth(1 * density);
        hexagonPaint.setColor(Color.argb(20, 0, 229, 255));
        hexagonPaint.setPathEffect(new CornerPathEffect(2 * density));

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.5f * density);
        gridPaint.setColor(Color.argb(5, 0, 229, 255));

        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initParticles(w, h);
        initHexagons(w, h);
    }

    private void initParticles(int w, int h) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i][0] = random.nextFloat() * w;
            particles[i][1] = random.nextFloat() * h;
            particles[i][2] = (random.nextFloat() - 0.5f) * 0.5f;
            particles[i][3] = (random.nextFloat() - 0.5f) * 0.5f;
        }
    }

    private void initHexagons(int w, int h) {
        for (int i = 0; i < HEXAGON_COUNT; i++) {
            hexagons[i][0] = random.nextFloat() * w;
            hexagons[i][1] = random.nextFloat() * h;
            hexagons[i][2] = 20 + random.nextFloat() * 40;
            hexagons[i][3] = random.nextFloat() * 360;
            hexagonSides[i] = 6;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        gradientAnimator = ValueAnimator.ofFloat(0f, 360f);
        gradientAnimator.setDuration(8000);
        gradientAnimator.setRepeatCount(ValueAnimator.INFINITE);
        gradientAnimator.setInterpolator(new LinearInterpolator());
        gradientAnimator.addUpdateListener(a -> {
            gradientPhase = (float) a.getAnimatedValue();
            updateGradient();
            invalidate();
        });
        gradientAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (gradientAnimator != null) gradientAnimator.cancel();
    }

    private void updateGradient() {
        float density = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        int pairIndex = (int) ((gradientPhase / 360f) * COLOR_PAIRS.length) % COLOR_PAIRS.length;
        int nextPairIndex = (pairIndex + 1) % COLOR_PAIRS.length;
        float t = ((gradientPhase / 360f) * COLOR_PAIRS.length) % 1f;

        int c1 = lerpColor(COLOR_PAIRS[pairIndex][0], COLOR_PAIRS[nextPairIndex][0], t);
        int c2 = lerpColor(COLOR_PAIRS[pairIndex][1], COLOR_PAIRS[nextPairIndex][1], t);

        gradientPaint.setShader(new RadialGradient(
                w / 2f, h / 2f, Math.max(w, h) * 0.7f,
                c1, c2, Shader.TileMode.CLAMP
        ));
    }

    private int lerpColor(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.rgb(
                (int) (ar + (br - ar) * t),
                (int) (ag + (bg - ag) * t),
                (int) (ab + (bb - ab) * t)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();

        canvas.drawRect(0, 0, w, h, gradientPaint);

        for (int x = 0; x < w; x += GRID_SPACING_PX) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = 0; y < h; y += GRID_SPACING_PX) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float px = particles[i][0];
            float py = particles[i][1];
            float size = 2 * density;
            canvas.drawCircle(px, py, size, particlePaint);

            particles[i][0] += particles[i][2] * density;
            particles[i][1] += particles[i][3] * density;

            if (particles[i][0] < 0) particles[i][0] = w;
            if (particles[i][0] > w) particles[i][0] = 0;
            if (particles[i][1] < 0) particles[i][1] = h;
            if (particles[i][1] > h) particles[i][1] = 0;
        }

        for (int i = 0; i < HEXAGON_COUNT; i++) {
            float cx = hexagons[i][0];
            float cy = hexagons[i][1];
            float radius = hexagons[i][2] * density;
            float rotation = hexagons[i][3] + gradientPhase * 0.5f;

            Path hexPath = createHexagon(cx, cy, radius, rotation);
            canvas.drawPath(hexPath, hexagonPaint);
        }
    }

    private Path createHexagon(float cx, float cy, float radius, float rotationDeg) {
        Path path = new Path();
        for (int i = 0; i < 6; i++) {
            float angle = (float) Math.toRadians(rotationDeg + i * 60);
            float x = cx + radius * (float) Math.cos(angle);
            float y = cy + radius * (float) Math.sin(angle);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }
}
