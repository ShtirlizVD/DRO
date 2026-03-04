package com.dro.lathe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * Custom view для визуализации измерения угла
 *
 * Система координат токарного станка:
 * - Ось Z горизонтальная (вдоль шпинделя, вправо - положительная)
 * - Ось X вертикальная (поперечная, вверх - положительная)
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
    private Paint paintGrid;        // Сетка

    // Data
    private double startX = Double.NaN, startZ = Double.NaN;
    private double endX = Double.NaN, endZ = Double.NaN;
    private double currentX = 0, currentZ = 0;
    private double angle = Double.NaN;
    private double distance = 0;

    // Scale and offset for drawing
    private float scale = 10.0f;

    // Origin point (where Z axis starts from left side)
    private float originX, originY;

    // View dimensions
    private int viewWidth = 1, viewHeight = 1;

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
        paintAxis.setPathEffect(new DashPathEffect(new float[]{15, 10}, 0));

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
        paintAngleArc.setStrokeWidth(3);
        paintAngleArc.setStyle(Paint.Style.STROKE);

        // Angle text paint
        paintAngleText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleText.setColor(ContextCompat.getColor(context, R.color.coord_l));
        paintAngleText.setTextSize(48);
        paintAngleText.setTextAlign(Paint.Align.CENTER);
        paintAngleText.setFakeBoldText(true);

        // Labels paint
        paintLabels = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabels.setColor(ContextCompat.getColor(context, R.color.text_bright));
        paintLabels.setTextSize(20);
        paintLabels.setTextAlign(Paint.Align.CENTER);

        // Grid paint
        paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGrid.setColor(ContextCompat.getColor(context, R.color.button_border));
        paintGrid.setStrokeWidth(1);
        paintGrid.setAlpha(80);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = Math.max(w, 1);
        viewHeight = Math.max(h, 1);
        // Origin is at left-center (Z starts from left)
        originX = 50;
        originY = viewHeight / 2f;
        calculateScale();
    }

    private void calculateScale() {
        if (viewWidth <= 1 || viewHeight <= 1) {
            scale = 10;
            return;
        }

        if (Double.isNaN(startX) || Double.isNaN(startZ)) {
            scale = 10; // Default scale
            return;
        }

        // Calculate scale based on the range of values
        double minX = Double.isNaN(endX) ? Math.min(startX, currentX) : Math.min(startX, Math.min(endX, currentX));
        double maxX = Double.isNaN(endX) ? Math.max(startX, currentX) : Math.max(startX, Math.max(endX, currentX));
        double minZ = Double.isNaN(endZ) ? Math.min(startZ, currentZ) : Math.min(startZ, Math.min(endZ, currentZ));
        double maxZ = Double.isNaN(endZ) ? Math.max(startZ, currentZ) : Math.max(startZ, Math.max(endZ, currentZ));

        double rangeX = Math.abs(maxX - minX);
        double rangeZ = Math.abs(maxZ - minZ);
        double maxRange = Math.max(rangeX, rangeZ);

        // Ensure minimum range
        maxRange = Math.max(maxRange, 20);

        // Scale to fit 70% of view
        float availableWidth = viewWidth * 0.7f;
        float availableHeight = viewHeight * 0.7f;
        float availableSize = Math.min(availableWidth, availableHeight);

        scale = availableSize / (float) maxRange;

        // Clamp scale
        scale = Math.max(scale, 1);
        scale = Math.min(scale, 50);
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
            angle = Math.abs(Math.toDegrees(Math.atan2(deltaX, deltaZ)));
        }
    }

    /**
     * Convert machine coordinates to screen coordinates
     * Z axis: horizontal (left to right)
     * X axis: vertical (up is positive)
     */
    private float toScreenX(double z) {
        return originX + (float) (z * scale);
    }

    private float toScreenY(double x) {
        return originY - (float) (x * scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (viewWidth <= 1 || viewHeight <= 1) return;

        // Draw background
        canvas.drawColor(ContextCompat.getColor(getContext(), R.color.bg_darker));

        // Draw grid
        drawGrid(canvas);

        // Draw Z axis (horizontal dashed line through origin)
        canvas.drawLine(0, originY, viewWidth, originY, paintAxis);

        // Draw axis labels
        paintLabels.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Z+", viewWidth - 40, originY - 10, paintLabels);
        paintLabels.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("X+", originX + 10, 30, paintLabels);
        canvas.drawText("X-", originX + 10, viewHeight - 15, paintLabels);

        // Draw start point
        if (!Double.isNaN(startX) && !Double.isNaN(startZ)) {
            float sx = toScreenX(startZ);
            float sy = toScreenY(startX);

            canvas.drawCircle(sx, sy, 14, paintStartPoint);
            paintLabels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("S", sx + 18, sy + 6, paintLabels);

            // Draw line to current position if end point not set
            if (Double.isNaN(endX)) {
                float cx = toScreenX(currentZ);
                float cy = toScreenY(currentX);

                // Draw line
                paintLine.setColor(ContextCompat.getColor(getContext(), R.color.yellow));
                paintLine.setStrokeWidth(2);
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
        if (!Double.isNaN(endX) && !Double.isNaN(endZ) && !Double.isNaN(startX)) {
            float sx = toScreenX(startZ);
            float sy = toScreenY(startX);
            float ex = toScreenX(endZ);
            float ey = toScreenY(endX);

            // Draw line from start to end
            paintLine.setColor(ContextCompat.getColor(getContext(), R.color.coord_d));
            paintLine.setStrokeWidth(4);
            canvas.drawLine(sx, sy, ex, ey, paintLine);

            // Draw end point
            canvas.drawCircle(ex, ey, 14, paintEndPoint);
            paintLabels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("E", ex + 18, ey + 6, paintLabels);

            // Draw angle arc and value
            drawAngleDisplay(canvas, sx, sy, angle, false);
        }
    }

    private void drawGrid(Canvas canvas) {
        // Draw grid lines every 10mm (scaled)
        float gridStep = 10 * scale;
        if (gridStep < 20) gridStep = 20; // Minimum step

        // Vertical lines (Z axis direction)
        for (float x = originX; x < viewWidth; x += gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }
        for (float x = originX; x > 0; x -= gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }

        // Horizontal lines (X axis direction)
        for (float y = originY; y < viewHeight; y += gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
        for (float y = originY; y > 0; y -= gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
    }

    private void drawAngleDisplay(Canvas canvas, float sx, float sy, double angleValue, boolean isPreview) {
        if (Double.isNaN(angleValue)) return;

        // Draw angle arc around start point
        float arcRadius = 50;

        // Calculate arc angles
        // Z axis is horizontal to the right (0 degrees in canvas = right, 90 = down)
        // We want angle from Z axis (horizontal right)
        // X positive is up, so angle from Z to point

        double endAngle;
        if (!Double.isNaN(endX)) {
            endAngle = Math.toDegrees(Math.atan2(-(endX - startX), endZ - startZ));
        } else {
            endAngle = Math.toDegrees(Math.atan2(-(currentX - startX), currentZ - startZ));
        }

        // Draw arc from Z axis (0 degrees) to the measured angle
        RectF arcRect = new RectF(sx - arcRadius, sy - arcRadius, sx + arcRadius, sy + arcRadius);

        // Arc starts from Z axis (0 = right direction on canvas)
        // Sweep angle is the measured angle
        float sweepAngle = (float) angleValue;

        // Determine direction of arc based on which side the end point is
        if (!Double.isNaN(endX) && endX < startX) {
            // End point has smaller X (higher on screen) - arc goes up
            sweepAngle = -(float) angleValue;
        } else if (Double.isNaN(endX) && currentX < startX) {
            sweepAngle = -(float) angleValue;
        } else {
            sweepAngle = (float) angleValue;
        }

        canvas.drawArc(arcRect, 0, sweepAngle, false, paintAngleArc);

        // Draw angle text in upper right corner
        paintAngleText.setColor(isPreview ?
                ContextCompat.getColor(getContext(), R.color.yellow) :
                ContextCompat.getColor(getContext(), R.color.coord_l));

        String angleText = String.format("%.2f°", angleValue);
        float textX = viewWidth - 100;
        float textY = 70;

        // Background for text
        Paint bgPaint = new Paint();
        bgPaint.setColor(ContextCompat.getColor(getContext(), R.color.bg_dark));
        bgPaint.setAlpha(220);
        float boxLeft = textX - 80;
        float boxTop = textY - 45;
        float boxRight = textX + 80;
        float boxBottom = textY + 50;
        canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 10, 10, bgPaint);

        // Draw border
        Paint borderPaint = new Paint();
        borderPaint.setColor(ContextCompat.getColor(getContext(), R.color.button_border));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 10, 10, borderPaint);

        // Label
        paintLabels.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Угол к Z:", textX, textY - 15, paintLabels);
        canvas.drawText(angleText, textX, textY + 25, paintAngleText);

        // Draw distance if available
        if (distance > 0) {
            String distText = String.format("L: %.2f мм", distance);
            canvas.drawText(distText, textX, textY + 50, paintLabels);
        }
    }
}
