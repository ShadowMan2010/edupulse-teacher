package com.edupulse.teacher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

public class GlassCardView extends FrameLayout {

    private static final float CORNER_RADIUS_DP = 16f;
    private static final float BORDER_WIDTH_DP = 1f;
    private static final float HIGHLIGHT_HEIGHT_DP = 0.5f;
    private static final float SHADOW_OFFSET_DP = 8f;
    private static final float SHADOW_RADIUS_DP = 20f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsRect = new RectF();
    private final Path clipPath = new Path();
    private final Path highlightPath = new Path();

    private float cornerRadius;
    private float borderWidth;
    private float sheenProgress = 0f;
    private boolean showTopBarAccent = false;
    private int accentColor = 0xFF00E5FF;

    public GlassCardView(Context context) {
        super(context);
        init(context, null);
    }

    public GlassCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public GlassCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        float density = getResources().getDisplayMetrics().density;
        cornerRadius = CORNER_RADIUS_DP * density;
        borderWidth = BORDER_WIDTH_DP * density;

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.argb(13, 255, 255, 255));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        borderPaint.setColor(Color.argb(20, 255, 255, 255));

        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(Color.argb(12, 255, 255, 255));

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(64, 0, 0, 0));

        setElevation(8f * density);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GlassCardView);
            showTopBarAccent = a.getBoolean(R.styleable.GlassCardView_showTopBarAccent, false);
            a.recycle();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        float shadowOffset = SHADOW_OFFSET_DP * density;
        float shadowRadius = SHADOW_RADIUS_DP * density;

        boundsRect.set(0, 0, w, h);

        clipPath.reset();
        clipPath.addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW);

        highlightPath.reset();
        float highlightHeight = HIGHLIGHT_HEIGHT_DP * density;
        RectF highlightRect = new RectF(borderWidth, borderWidth, w - borderWidth, borderWidth + highlightHeight);
        highlightPath.addRoundRect(highlightRect, cornerRadius, cornerRadius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float shadowOffset = SHADOW_OFFSET_DP * density;

        canvas.save();
        canvas.clipPath(clipPath);

        if (showTopBarAccent && getHeight() > 0) {
            Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            accentPaint.setStyle(Paint.Style.FILL);
            accentPaint.setColor(accentColor);
            float accentHeight = 1.5f * density;
            RectF accentRect = new RectF(0, 0, getWidth(), accentHeight);
            canvas.drawRoundRect(accentRect, cornerRadius, cornerRadius, accentPaint);
        }

        canvas.drawColor(Color.argb(13, 255, 255, 255));

        canvas.restore();

        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, borderPaint);

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawPath(highlightPath, highlightPaint);

        if (sheenProgress > 0f && sheenProgress < 1f) {
            drawSheen(canvas);
        }

        canvas.restore();

        super.onDraw(canvas);
    }

    private void drawSheen(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float startX = -w + sheenProgress * (w * 2 + w);
        float endX = startX + w * 2;

        LinearGradient sheenGradient = new LinearGradient(
                startX, 0, endX, h,
                new int[]{Color.TRANSPARENT, Color.argb(10, 255, 255, 255), Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        sheenPaint.setShader(sheenGradient);
        sheenPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, w, h, sheenPaint);
        sheenPaint.setShader(null);
    }

    public void startSheenAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(600);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            sheenProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setShowTopBarAccent(boolean show) {
        this.showTopBarAccent = show;
        invalidate();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        invalidate();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
