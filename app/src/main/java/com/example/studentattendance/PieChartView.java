package com.example.studentattendance;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Animated donut chart showing Có mặt (green) vs Vắng mặt (red) with the percentage in the middle. */
public class PieChartView extends View {
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int present, absent;
    private float progress = 1f;

    public PieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(ContextCompat.getColor(context, R.color.accent_text));
    }

    /** Sets new values and plays the sweep animation. */
    public void setData(int present, int absent) {
        this.present = present;
        this.absent = absent;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(900);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(anim -> { progress = (float) anim.getAnimatedValue(); invalidate(); });
        a.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float stroke = size * 0.16f;
        float pad = stroke / 2f + 2;
        oval.set((getWidth() - size) / 2f + pad, (getHeight() - size) / 2f + pad,
                (getWidth() + size) / 2f - pad, (getHeight() + size) / 2f - pad);
        arcPaint.setStrokeWidth(stroke);

        int total = present + absent;
        if (total == 0) {
            arcPaint.setColor(ContextCompat.getColor(getContext(), R.color.avatar_bg));
            canvas.drawArc(oval, 0, 360, false, arcPaint);
        } else {
            float presentSweep = 360f * present / total * progress;
            float absentSweep = 360f * absent / total * progress;
            arcPaint.setColor(ContextCompat.getColor(getContext(), R.color.present));
            canvas.drawArc(oval, -90, presentSweep, false, arcPaint);
            arcPaint.setColor(ContextCompat.getColor(getContext(), R.color.absent));
            canvas.drawArc(oval, -90 + presentSweep, absentSweep, false, arcPaint);
        }

        int pct = total == 0 ? 0 : Math.round(100f * present / total * progress);
        textPaint.setTextSize(size * 0.2f);
        canvas.drawText(pct + "%", getWidth() / 2f, getHeight() / 2f + textPaint.getTextSize() / 3f, textPaint);
    }
}
