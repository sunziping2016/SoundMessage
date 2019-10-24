package io.szp.soundmessage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PlotView extends View {

    private int height, width;
    private int realHeight = 400;
    private Paint baseLinePaint, timeLinePaint;

    private short[] timeData;

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
        if (height == 0 || width == 0)
            throw new AssertionError("123");
        float baseHeight = (float) realHeight / 2;
        float stepHeight = 0.1f;
        canvas.drawLine(0, baseHeight, width, baseHeight, baseLinePaint);
        if (timeData != null && timeData.length != 0) {
            float stepWidth = (float) width / timeData.length;
            for (int i = 0; i < timeData.length - 1; ++i) {
                canvas.drawLine(stepWidth * i, baseHeight - stepHeight * timeData[i],
                        stepWidth * (i + 1), baseHeight - stepHeight * timeData[i + 1],
                        timeLinePaint);
            }
        }
    }

    synchronized void setTimeData(short[] data) {
        timeData = data;
    }

}
