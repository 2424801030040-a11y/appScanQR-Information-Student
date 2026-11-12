package com.example.studentattendance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Draws a dimmed mask with a transparent rounded viewfinder cut-out and white corner brackets.
 * The cut-out is centered and FRAME_SIZE_DP wide; it must match the laser container in activity_scan.xml.
 */
public class ScannerOverlayView extends View {
    public static final int FRAME_SIZE_DP = 280;

    private final Paint dimPaint = new Paint();
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final float radius, cornerLength, frameSize;

    public ScannerOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        frameSize = FRAME_SIZE_DP * d;
        radius = 24 * d;
        cornerLength = 36 * d;

        dimPaint.setColor(getResources().getColor(R.color.overlay_dim, null));
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(5 * d);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_HARDWARE, null); // required for the CLEAR xfermode cut-out
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float left = (w - frameSize) / 2f, top = (h - frameSize) / 2f;
        frame.set(left, top, left + frameSize, top + frameSize);
    }

    @Override protected void onDraw(Canvas canvas) {
        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        canvas.drawRoundRect(frame, radius, radius, clearPaint);
        canvas.restoreToCount(layer);

        float l = frame.left, t = frame.top, r = frame.right, b = frame.bottom, c = cornerLength;
        canvas.drawLine(l, t, l + c, t, cornerPaint); canvas.drawLine(l, t, l, t + c, cornerPaint);
        canvas.drawLine(r, t, r - c, t, cornerPaint); canvas.drawLine(r, t, r, t + c, cornerPaint);
        canvas.drawLine(l, b, l + c, b, cornerPaint); canvas.drawLine(l, b, l, b - c, cornerPaint);
        canvas.drawLine(r, b, r - c, b, cornerPaint); canvas.drawLine(r, b, r, b - c, cornerPaint);
    }
}
