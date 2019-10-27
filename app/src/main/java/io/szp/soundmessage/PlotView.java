package io.szp.soundmessage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PlotView extends View {
    private static final int realHeight = 400;
    private static final float timeScale = 500;
    // private static final float frequencyScale = 10;
    private static final float frequencyScale = 50;

    private int height, width;
    private Paint baseLinePaint, timeLinePaint, frequencyLinePaint;

    private float[] timeData;
    private float[] frequencyData;

    public PlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        baseLinePaint = new Paint();
        baseLinePaint.setAntiAlias(true);
        baseLinePaint.setStyle(Paint.Style.STROKE);
        baseLinePaint.setColor(Color.GRAY);
        baseLinePaint.setStrokeWidth(5);
        timeLinePaint = new Paint();
        timeLinePaint.setAntiAlias(true);
        timeLinePaint.setStyle(Paint.Style.STROKE);
        timeLinePaint.setColor(Color.RED);
        timeLinePaint.setStrokeWidth(2);
        frequencyLinePaint = new Paint();
        frequencyLinePaint.setAntiAlias(true);
        frequencyLinePaint.setStyle(Paint.Style.STROKE);
        frequencyLinePaint.setColor(Color.BLUE);
        frequencyLinePaint.setStrokeWidth(2);
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
        float baseHeight = (float) realHeight / 2;
        canvas.drawLine(0, baseHeight, width, baseHeight, baseLinePaint);
        if (timeData != null && timeData.length != 0) {
            float stepWidth = (float) width / timeData.length;
            for (int i = 0; i < timeData.length - 1; ++i) {
                canvas.drawLine(stepWidth * i, baseHeight - timeScale * timeData[i],
                        stepWidth * (i + 1), baseHeight - timeScale * timeData[i + 1],
                        timeLinePaint);
            }
        }
        if (frequencyData != null && frequencyData.length != 0) {
            float stepWidth = (float) width / frequencyData.length;
            for (int i = 0; i < frequencyData.length - 1; ++i) {
                canvas.drawLine(stepWidth * i, baseHeight + frequencyScale * frequencyData[i],
                        stepWidth * (i + 1), baseHeight + frequencyScale * frequencyData[i + 1],
                        frequencyLinePaint);
            }
        }
    }

    synchronized void setTimeData(float[] data) {
        timeData = data;
    }

    synchronized void setFrequencyData(float[] data) {
        frequencyData = data;
    }

}
