package com.dro.lathe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * Custom view для визуализации измерения угла
 *
 * Система координат токарного станка:
 * - Ось X - поперечная подача (от себя/на себя) - отображается вертикально
 * - Ось Z - продольная подача (влево/вправо) - отображается горизонтально
 *   
 * Угол всегда острый (0-90°) - угол между линией и осью Z
 * 
 * Позиция S определяется по направлению линии:
 * - Линия идёт вправо (relZ > 0) → S слева
 * - Линия идёт влево (relZ < 0) → S справа
 */
public class AngleView extends View {

    // Paints
    private Paint paintAxis;
    private Paint paintStartPoint;
    private Paint paintEndPoint;
    private Paint paintCurrentPos;
    private Paint paintLine;
    private Paint paintAngleArc;
    private Paint paintAngleText;
    private Paint paintLabels;
    private Paint paintGrid;
    private Paint paintBoxBg;
    private Paint paintBoxBorder;

    // Data (absolute coordinates from DRO)
    private double startX = Double.NaN, startZ = Double.NaN;
    private double endX = Double.NaN, endZ = Double.NaN;
    private double currentX = 0, currentZ = 0;
    private double angle = Double.NaN;
    private double distance = 0;

    // Scale
    private float scale = 10.0f;

    // View dimensions
    private int viewWidth = 1, viewHeight = 1;

    // Screen position of point S (calculated based on line direction)
    private float screenSX, screenSY;
    private boolean sPositionSet = false; // Whether S position has been determined

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
        // Axis paint (dashed line for Z axis - spans full width)
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

        // Box background
        paintBoxBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBoxBg.setColor(ContextCompat.getColor(context, R.color.bg_dark));
        paintBoxBg.setAlpha(220);
        paintBoxBg.setStyle(Paint.Style.FILL);

        // Box border
        paintBoxBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBoxBorder.setColor(ContextCompat.getColor(context, R.color.button_border));
        paintBoxBorder.setStyle(Paint.Style.STROKE);
        paintBoxBorder.setStrokeWidth(2);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = Math.max(w, 1);
        viewHeight = Math.max(h, 1);
        
        // Default S position at center
        if (!sPositionSet) {
            screenSX = viewWidth / 2f;
            screenSY = viewHeight / 2f;
        }
        
        calculateScale();
    }

    private void calculateScale() {
        if (viewWidth <= 1 || viewHeight <= 1 || Double.isNaN(startX) || Double.isNaN(startZ)) {
            scale = 10;
            return;
        }

        // Calculate relative deltas
        double relX = Double.isNaN(endX) ? (currentX - startX) : (endX - startX);
        double relZ = Double.isNaN(endZ) ? (currentZ - startZ) : (endZ - startZ);

        // Determine max range for scaling
        double maxRange = Math.max(Math.abs(relX), Math.abs(relZ));
        maxRange = Math.max(maxRange, 10); // Minimum range

        // Calculate scale to fit in 35% of view (so points don't go off screen)
        float availableSize = Math.min(viewWidth, viewHeight) * 0.35f;
        scale = (float) (availableSize / maxRange);
        scale = Math.max(scale, 1);
        scale = Math.min(scale, 50);
    }

    /**
     * Determine and set S screen position based on line direction
     * Called when line direction becomes clear
     */
    private void determineSPosition(double relZ) {
        if (sPositionSet) return; // Already set, don't change
        
        float margin = Math.min(viewWidth, viewHeight) * 0.20f;
        
        if (relZ > 0) {
            // Line goes RIGHT (positive Z) - S on the LEFT
            screenSX = margin + 60;
        } else if (relZ < 0) {
            // Line goes LEFT (negative Z) - S on the RIGHT
            screenSX = viewWidth - margin - 60;
        } else {
            // No horizontal movement - S at center
            screenSX = viewWidth / 2f;
        }
        
        screenSY = viewHeight / 2f;
        sPositionSet = true;
    }

    public void setStartPoint(double x, double z) {
        this.startX = x;
        this.startZ = z;
        this.sPositionSet = false; // Reset S position for new measurement
        calculateScale();
        invalidate();
    }

    public void setEndPoint(double x, double z) {
        this.endX = x;
        this.endZ = z;
        
        // Set S position based on final line direction if not yet set
        double relZ = endZ - startZ;
        if (!sPositionSet) {
            determineSPosition(relZ);
        }
        
        calculateAngle();
        calculateScale();
        invalidate();
    }

    public void setCurrentPosition(double x, double z) {
        this.currentX = x;
        this.currentZ = z;
        
        // Set S position when direction becomes clear (preview mode)
        if (!Double.isNaN(startX) && Double.isNaN(endX) && !sPositionSet) {
            double relZ = currentZ - startZ;
            double relX = currentX - startX;
            // Only set position when there's significant movement
            if (Math.abs(relZ) > 1 || Math.abs(relX) > 1) {
                determineSPosition(relZ);
            }
        }
        
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
        sPositionSet = false;
        screenSX = viewWidth / 2f;
        screenSY = viewHeight / 2f;
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

        distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (deltaZ == 0 && deltaX == 0) {
            angle = 0;
        } else {
            // Angle from Z axis - always acute (0-90)
            double rawAngle = Math.toDegrees(Math.atan2(Math.abs(deltaX), Math.abs(deltaZ)));
            angle = Math.min(rawAngle, 90);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (viewWidth <= 1 || viewHeight <= 1) return;

        // Draw background
        canvas.drawColor(ContextCompat.getColor(getContext(), R.color.bg_darker));

        // Draw grid
        drawGrid(canvas);

        // Draw Z axis (dashed horizontal line through S - spans full width)
        canvas.drawLine(0, screenSY, viewWidth, screenSY, paintAxis);

        // Draw axis labels
        paintLabels.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Z+", viewWidth - 15, screenSY - 10, paintLabels);
        paintLabels.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Z-", 15, screenSY - 10, paintLabels);

        // Draw start point S
        if (!Double.isNaN(startX) && !Double.isNaN(startZ)) {
            canvas.drawCircle(screenSX, screenSY, 14, paintStartPoint);
            paintLabels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("S", screenSX + 18, screenSY + 6, paintLabels);

            // Draw line to current position if end point not set (preview)
            if (Double.isNaN(endX)) {
                double relX = currentX - startX;
                double relZ = currentZ - startZ;

                float cx = screenSX + (float)(relZ * scale);
                float cy = screenSY + (float)(relX * scale);

                paintLine.setColor(ContextCompat.getColor(getContext(), R.color.yellow));
                paintLine.setStrokeWidth(2);
                canvas.drawLine(screenSX, screenSY, cx, cy, paintLine);

                canvas.drawCircle(cx, cy, 10, paintCurrentPos);

                // Calculate and show preview angle (always acute)
                if (Math.abs(relZ) > 0.01 || Math.abs(relX) > 0.01) {
                    double previewAngle = Math.toDegrees(Math.atan2(Math.abs(relX), Math.abs(relZ)));
                    previewAngle = Math.min(previewAngle, 90);
                    drawAngleArc(canvas, relX, relZ, previewAngle);
                    drawAngleDisplay(canvas, previewAngle, true);
                }
            }
        }

        // Draw end point and final angle
        if (!Double.isNaN(endX) && !Double.isNaN(endZ) && !Double.isNaN(startX)) {
            double relX = endX - startX;
            double relZ = endZ - startZ;

            float ex = screenSX + (float)(relZ * scale);
            float ey = screenSY + (float)(relX * scale);

            paintLine.setColor(ContextCompat.getColor(getContext(), R.color.coord_d));
            paintLine.setStrokeWidth(4);
            canvas.drawLine(screenSX, screenSY, ex, ey, paintLine);

            canvas.drawCircle(ex, ey, 14, paintEndPoint);
            paintLabels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("E", ex + 18, ey + 6, paintLabels);

            drawAngleArc(canvas, relX, relZ, angle);
            drawAngleDisplay(canvas, angle, false);
        }
    }

    private void drawGrid(Canvas canvas) {
        float gridStep = 10 * scale;
        if (gridStep < 20) gridStep = 20;

        // Vertical grid lines
        for (float x = screenSX; x < viewWidth; x += gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }
        for (float x = screenSX; x > 0; x -= gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }

        // Horizontal grid lines
        for (float y = screenSY; y < viewHeight; y += gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
        for (float y = screenSY; y > 0; y -= gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
    }

    private void drawAngleArc(Canvas canvas, double relX, double relZ, double angleValue) {
        if (Double.isNaN(angleValue)) return;

        float arcRadius = 50;
        RectF arcRect = new RectF(screenSX - arcRadius, screenSY - arcRadius, 
                                   screenSX + arcRadius, screenSY + arcRadius);

        // Arc from Z axis (horizontal) toward the line
        float sweepAngle = (float) angleValue;
        
        if (relX >= 0) {
            // Point is below Z axis (positive X) - sweep clockwise
            sweepAngle = (float) angleValue;
        } else {
            // Point is above Z axis (negative X) - sweep counter-clockwise
            sweepAngle = -(float) angleValue;
        }
        
        // Adjust arc direction based on which side E is
        if (relZ < 0) {
            // E is to the left - draw arc on left side
            sweepAngle = -sweepAngle;
        }

        canvas.drawArc(arcRect, relZ >= 0 ? 0 : 180 - (float)angleValue, 
                       relZ >= 0 ? sweepAngle : (float)angleValue, false, paintAngleArc);
    }

    private void drawAngleDisplay(Canvas canvas, double angleValue, boolean isPreview) {
        if (Double.isNaN(angleValue)) return;

        paintAngleText.setColor(isPreview ?
                ContextCompat.getColor(getContext(), R.color.yellow) :
                ContextCompat.getColor(getContext(), R.color.coord_l));

        String angleText = String.format("%.2f°", angleValue);
        float textX = viewWidth - 100;
        float textY = 70;

        float boxLeft = textX - 80;
        float boxTop = textY - 45;
        float boxRight = textX + 80;
        float boxBottom = textY + 50;

        drawRoundedRect(canvas, boxLeft, boxTop, boxRight, boxBottom, 10, paintBoxBg);
        drawRoundedRect(canvas, boxLeft, boxTop, boxRight, boxBottom, 10, paintBoxBorder);

        paintLabels.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Угол к Z:", textX, textY - 15, paintLabels);
        canvas.drawText(angleText, textX, textY + 25, paintAngleText);

        if (distance > 0) {
            String distText = String.format("L: %.2f мм", distance);
            canvas.drawText(distText, textX, textY + 50, paintLabels);
        }
    }

    private void drawRoundedRect(Canvas canvas, float left, float top, float right, float bottom, float radius, Paint paint) {
        RectF rect = new RectF(left, top, right, bottom);
        
        if (Build.VERSION.SDK_INT >= 21) {
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            Path path = new Path();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
    }
}
