package com.dro.lathe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * Custom view для визуализации измерения угла
 */
public class AngleView extends View {

    // Paints
    private Paint paintAxis;        // Пунктирная ось Z
    private Paint paintStartPoint;  // Начальная точка
    private Paint paintEndPoint;    // Конечная точка
    private Paint paintCurrentPos;  // Текущая позиция
    private Paint paintLine;        // Линия между точками
    private Paint paintAngleArc;    // Дуга угла
    private Paint paintAngleText;   // Текст угла
    private Paint paintLabels;      // Подписи

    // Data
    private double startX = Double.NaN, startZ = Double.NaN;
    private double endX = Double.NaN, endZ = Double.NaN;
    private double currentX = 0, currentZ = 0;
    private double angle = Double.NaN;
    private double distance = 0;

    // Scale and offset for drawing
    private float scale = 1.0f;
    private float offsetX = 0, offsetY = 0;
    private float centerX, centerY;  // Center of the view (represents Z axis)

    // View dimensions
    private int viewWidth, viewHeight;

    public AngleView(Context context) {
        super(context);
        init(context);
    }

    public AngleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AngleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Axis paint (dashed line for Z axis)
        paintAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAxis.setColor(ContextCompat.getColor(context, R.color.text_dim));
        paintAxis.setStrokeWidth(2);
        paintAxis.setStyle(Paint.Style.STROKE);
        paintAxis.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        // Start point paint
        paintStartPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStartPoint.setColor(ContextCompat.getColor(context, R.color.coord_x));
        paintStartPoint.setStyle(Paint.Style.FILL);

        // End point paint
        paintEndPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintEndPoint.setColor(ContextCompat.getColor(context, R.color.coord_z));
        paintEndPoint.setStyle(Paint.Style.FILL);

        // Current position paint
        paintCurrentPos = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCurrentPos.setColor(ContextCompat.getColor(context, R.color.yellow));
        paintCurrentPos.setStyle(Paint.Style.FILL);

        // Line paint
        paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLine.setColor(ContextCompat.getColor(context, R.color.coord_d));
        paintLine.setStrokeWidth(3);
        paintLine.setStyle(Paint.Style.STROKE);

        // Angle arc paint
        paintAngleArc = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleArc.setColor(ContextCompat.getColor(context, R.color.coord_l));
        paintAngleArc.setStrokeWidth(2);
        paintAngleArc.setStyle(Paint.Style.STROKE);

        // Angle text paint
        paintAngleText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleText.setColor(ContextCompat.getColor(context, R.color.coord_l));
        paintAngleText.setTextSize(64);
        paintAngleText.setTextAlign(Paint.Align.CENTER);
        paintAngleText.setFakeBoldText(true);

        // Labels paint
        paintLabels = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabels.setColor(ContextCompat.getColor(context, R.color.text_bright));
        paintLabels.setTextSize(24);
        paintLabels.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        centerX = w / 2f;
        centerY = h / 2f;
        calculateScale();
    }

    private void calculateScale() {
        if (Double.isNaN(startX) || Double.isNaN(startZ)) {
            scale = 10; // Default scale in mm
            return;
        }

        // Calculate scale based on the range of values
        double minX = Double.isNaN(endX) ? Math.min(startX, currentX) : Math.min(startX, endX);
        double maxX = Double.isNaN(endX) ? Math.max(startX, currentX) : Math.max(startX, endX);
        double minZ = Double.isNaN(endZ) ? Math.min(startZ, currentZ) : Math.min(startZ, endZ);
        double maxZ = Double.isNaN(endZ) ? Math.max(startZ, currentZ) : Math.max(startZ, endZ);

        double rangeX = maxX - minX;
        double rangeZ = maxZ - minZ;
        double maxRange = Math.max(rangeX, rangeZ);

        // Ensure minimum range
        maxRange = Math.max(maxRange, 10);

        // Scale to fit 80% of view
        float availableWidth = viewWidth * 0.8f;
        float availableHeight = viewHeight * 0.8f;
        float availableSize = Math.min(availableWidth, availableHeight);

        scale = availableSize / (float) maxRange;

        // Ensure minimum scale
        scale = Math.max(scale, 1);
    }

    public void setStartPoint(double x, double z) {
        this.startX = x;
        this.startZ = z;
        calculateScale();
        invalidate();
    }

    public void setEndPoint(double x, double z) {
        this.endX = x;
        this.endZ = z;
        calculateAngle();
        calculateScale();
        invalidate();
    }

    public void setCurrentPosition(double x, double z) {
        this.currentX = x;
        this.currentZ = z;
        if (Double.isNaN(endX) && !Double.isNaN(startX)) {
            calculateScale();
        }
        invalidate();
    }

    public void setAngle(double angle) {
        this.angle = angle;
        invalidate();
    }

    public void setDistance(double distance) {
        this.distance = distance;
        invalidate();
    }

    public void reset() {
        startX = startZ = endX = endZ = Double.NaN;
        angle = Double.NaN;
        distance = 0;
        calculateScale();
        invalidate();
    }

    private void calculateAngle() {
        if (Double.isNaN(startX) || Double.isNaN(startZ) ||
                Double.isNaN(endX) || Double.isNaN(endZ)) {
            angle = Double.NaN;
            return;
        }

        double deltaX = endX - startX;
        double deltaZ = endZ - startZ;

        // Distance between points
        distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        // Angle from Z-axis (always positive)
        if (deltaZ == 0 && deltaX == 0) {
            angle = 0;
        } else {
            // atan2 gives angle from positive Z axis
            // We want angle relative to Z axis, always positive
            angle = Math.abs(Math.toDegrees(Math.atan2(deltaX, deltaZ)));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background
        canvas.drawColor(ContextCompat.getColor(getContext(), R.color.bg_darker));

        // Draw Z axis (dashed vertical line)
        canvas.drawLine(centerX, 0, centerX, viewHeight, paintAxis);

        // Draw axis labels
        paintLabels.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Z+", viewWidth - 40, centerY - 10, paintLabels);
        paintLabels.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("X+", centerX + 10, 30, paintLabels);

        // Draw reference lines (grid)
        drawGrid(canvas);

        // Draw start point
        if (!Double.isNaN(startX) && !Double.isNaN(startZ)) {
            float sx = centerX + (float) (startX * scale);
            float sy = centerY - (float) (startZ * scale);

            canvas.drawCircle(sx, sy, 12, paintStartPoint);
            canvas.drawText("S", sx + 20, sy + 5, paintLabels);

            // Draw line to current position if end point not set
            if (Double.isNaN(endX)) {
                float cx = centerX + (float) (currentX * scale);
                float cy = centerY - (float) (currentZ * scale);

                // Draw line
                paintLine.setColor(ContextCompat.getColor(getContext(), R.color.yellow));
                canvas.drawLine(sx, sy, cx, cy, paintLine);

                // Draw current position
                canvas.drawCircle(cx, cy, 8, paintCurrentPos);

                // Draw angle preview
                double previewAngle = Math.abs(Math.toDegrees(
                        Math.atan2(currentX - startX, currentZ - startZ)));
                drawAngleDisplay(canvas, sx, sy, previewAngle, true);
            }
        }

        // Draw end point and angle
        if (!Double.isNaN(endX) && !Double.isNaN(endZ)) {
            float sx = centerX + (float) (startX * scale);
            float sy = centerY - (float) (startZ * scale);
            float ex = centerX + (float) (endX * scale);
            float ey = centerY - (float) (endZ * scale);

            // Draw line from start to end
            paintLine.setColor(ContextCompat.getColor(getContext(), R.color.coord_d));
            paintLine.setStrokeWidth(4);
            canvas.drawLine(sx, sy, ex, ey, paintLine);
            paintLine.setStrokeWidth(3);

            // Draw end point
            canvas.drawCircle(ex, ey, 12, paintEndPoint);
            canvas.drawText("E", ex + 20, ey + 5, paintLabels);

            // Draw angle arc and value
            drawAngleDisplay(canvas, sx, sy, angle, false);
        }
    }

    private void drawGrid(Canvas canvas) {
        Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGrid.setColor(ContextCompat.getColor(getContext(), R.color.button_border));
        paintGrid.setStrokeWidth(1);
        paintGrid.setAlpha(100);

        // Draw grid lines every 10mm (scaled)
        float gridStep = 10 * scale;

        // Vertical lines
        for (float x = centerX; x < viewWidth; x += gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }
        for (float x = centerX; x > 0; x -= gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }

        // Horizontal lines
        for (float y = centerY; y < viewHeight; y += gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
        for (float y = centerY; y > 0; y -= gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
    }

    private void drawAngleDisplay(Canvas canvas, float sx, float sy, double angleValue, boolean isPreview) {
        if (Double.isNaN(angleValue)) return;

        // Draw angle arc
        float arcRadius = 60;

        // Angle from Z axis (vertical up)
        float startAngle = -90; // Z axis points up

        // The measured angle
        float sweepAngle = (float) -angleValue;

        // Determine which side to draw arc
        if (!Double.isNaN(endX) && endX < startX) {
            // End point is to the left of start point - draw arc on left
            sweepAngle = (float) angleValue;
        } else if (Double.isNaN(endX) && currentX < startX) {
            // Current position is to the left - draw arc on left
            sweepAngle = (float) angleValue;
        }

        RectF arcRect = new RectF(sx - arcRadius, sy - arcRadius, sx + arcRadius, sy + arcRadius);
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, paintAngleArc);

        // Draw angle text in corner
        paintAngleText.setColor(isPreview ?
                ContextCompat.getColor(getContext(), R.color.yellow) :
                ContextCompat.getColor(getContext(), R.color.coord_l));

        String angleText = String.format("%.2f°", angleValue);
        float textX = viewWidth - 120;
        float textY = 80;

        // Background for text
        Paint bgPaint = new Paint();
        bgPaint.setColor(ContextCompat.getColor(getContext(), R.color.bg_darker));
        bgPaint.setAlpha(200);
        canvas.drawRoundRect(textX - 100, textY - 50, textX + 100, textY + 30, 10, 10, bgPaint);

        // Label
        paintLabels.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Угол к Z:", textX, textY - 20, paintLabels);
        canvas.drawText(angleText, textX, textY + 25, paintAngleText);

        // Draw distance if available
        if (distance > 0) {
            String distText = String.format("L: %.2f мм", distance);
            canvas.drawText(distText, textX, textY + 60, paintLabels);
        }
    }
}
