package io.szp.soundmessage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PlotView extends View {
    private static final float timeScale = 500;
    private static final float xcorrScale = 5;

    private int width, height;
    private Paint baseLinePaint, timeLinePaint, startXcorrPaint, endXcorrPaint;

    private float[] timeData;
    private float[] startXcorrData;
    private float[] endXcorrData;

    public PlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        baseLinePaint = new Paint();
        baseLinePaint.setAntiAlias(true);
        baseLinePaint.setStyle(Paint.Style.STROKE);
        baseLinePaint.setColor(Color.GRAY);
        baseLinePaint.setStrokeWidth(2);
        timeLinePaint = new Paint();
        timeLinePaint.setAntiAlias(true);
        timeLinePaint.setStyle(Paint.Style.STROKE);
        timeLinePaint.setColor(Color.RED);
        timeLinePaint.setStrokeWidth(2);
        startXcorrPaint = new Paint();
        startXcorrPaint.setAntiAlias(true);
        startXcorrPaint.setStyle(Paint.Style.STROKE);
        startXcorrPaint.setColor(Color.BLUE);
        startXcorrPaint.setStrokeWidth(2);
        endXcorrPaint = new Paint();
        endXcorrPaint.setAntiAlias(true);
        endXcorrPaint.setStyle(Paint.Style.STROKE);
        endXcorrPaint.setColor(Color.GREEN);
        endXcorrPaint.setStrokeWidth(2);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        height = MeasureSpec.getSize(heightMeasureSpec);
        width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    synchronized protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float baseHeight = (float) height / 2;
        if (timeData != null || startXcorrData != null || endXcorrData != null)
            canvas.drawLine(0, baseHeight, width, baseHeight, baseLinePaint);
        if (timeData != null && timeData.length != 0) {
            float stepWidth = (float) width / timeData.length;
            for (int i = 0; i < timeData.length - 1; ++i) {
                canvas.drawLine(stepWidth * i, baseHeight - timeScale * timeData[i],
                        stepWidth * (i + 1), baseHeight - timeScale * timeData[i + 1],
                        timeLinePaint);
            }
        }
        if (startXcorrData != null && startXcorrData.length != 0) {
            float stepWidth = (float) width / startXcorrData.length;
            for (int i = 0; i < startXcorrData.length - 1; ++i) {
                canvas.drawLine(stepWidth * i, baseHeight + xcorrScale * startXcorrData[i],
                        stepWidth * (i + 1), baseHeight + xcorrScale * startXcorrData[i + 1],
                        startXcorrPaint);
            }
        }
        if (endXcorrData != null && endXcorrData.length != 0) {
            float stepWidth = (float) width / endXcorrData.length;
            for (int i = 0; i < endXcorrData.length - 1; ++i) {
                canvas.drawLine(stepWidth * i, baseHeight + xcorrScale * endXcorrData[i],
                        stepWidth * (i + 1), baseHeight + xcorrScale * endXcorrData[i + 1],
                        endXcorrPaint);
            }
        }
    }

    synchronized void setTimeData(float[] data) {
        timeData = data;
    }

    synchronized void setStartXcorrData(float[] data) {
        startXcorrData = data;
    }

    synchronized void setEndXcorrData(float[] data) {
        endXcorrData = data;
    }
}
