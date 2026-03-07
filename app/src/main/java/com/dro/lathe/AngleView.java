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
 *   - Уменьшение Z = движение влево
 *   - Увеличение Z = движение вправо
 *   
 * Угол всегда острый (0-90°) - угол между линией и осью Z
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

    // Direction tracking
    private double previousX = Double.NaN;
    private double previousZ = Double.NaN;
    private int primaryAxis = 0; // 0 = unknown, 1 = X first, 2 = Z first
    private int zDirection = 0; // -1 = left (Z decreasing), 1 = right (Z increasing)
    private static final double DIRECTION_THRESHOLD = 0.1; // Minimum movement to detect direction

    // Scale
    private float scale = 10.0f;

    // View dimensions
    private int viewWidth = 1, viewHeight = 1;

    // Screen position of point S (calculated based on direction)
    private float screenSX, screenSY;

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
        calculateScaleAndPosition();
    }

    private void calculateScaleAndPosition() {
        if (viewWidth <= 1 || viewHeight <= 1 || Double.isNaN(startX) || Double.isNaN(startZ)) {
            scale = 10;
            screenSX = viewWidth / 2f;
            screenSY = viewHeight / 2f;
            return;
        }

        // Calculate relative deltas
        double relX = Double.isNaN(endX) ? (currentX - startX) : (endX - startX);
        double relZ = Double.isNaN(endZ) ? (currentZ - startZ) : (endZ - startZ);

        // Determine max range for scaling
        double maxRange = Math.max(Math.abs(relX), Math.abs(relZ));
        maxRange = Math.max(maxRange, 10); // Minimum range

        // Calculate scale to fit in 70% of view
        float availableSize = Math.min(viewWidth, viewHeight) * 0.35f;
        scale = (float) (availableSize / maxRange);
        scale = Math.max(scale, 1);
        scale = Math.min(scale, 50);

        // Calculate screen position of S based on primary axis and direction
        float margin = Math.min(viewWidth, viewHeight) * 0.15f;

        if (primaryAxis == 2 && zDirection != 0) {
            // Z moved first
            if (zDirection == 1) {
                // Moving right (Z increasing) - S on the LEFT
                screenSX = margin + 50;
            } else {
                // Moving left (Z decreasing) - S on the RIGHT
                screenSX = viewWidth - margin - 50;
            }
            screenSY = viewHeight / 2f;
        } else {
            // X moved first or unknown - S at CENTER
            screenSX = viewWidth / 2f;
            screenSY = viewHeight / 2f;
        }
    }

    public void setStartPoint(double x, double z) {
        this.startX = x;
        this.startZ = z;
        // Reset direction tracking for new measurement
        this.previousX = Double.NaN;
        this.previousZ = Double.NaN;
        this.primaryAxis = 0;
        this.zDirection = 0;
        calculateScaleAndPosition();
        invalidate();
    }

    public void setEndPoint(double x, double z) {
        this.endX = x;
        this.endZ = z;
        // Clear direction tracking when measurement complete
        this.previousX = Double.NaN;
        this.previousZ = Double.NaN;
        this.primaryAxis = 0;
        this.zDirection = 0;
        calculateAngle();
        calculateScaleAndPosition();
        invalidate();
    }

    public void setCurrentPosition(double x, double z) {
        // Detect which axis started moving first after start point is set
        if (!Double.isNaN(startX) && Double.isNaN(endX)) {
            if (!Double.isNaN(previousX) && !Double.isNaN(previousZ)) {
                double deltaX = Math.abs(x - previousX);
                double deltaZ = Math.abs(z - previousZ);

                // Determine primary axis if not yet determined
                if (primaryAxis == 0) {
                    if (deltaX > DIRECTION_THRESHOLD && deltaX > deltaZ) {
                        primaryAxis = 1; // X axis started first
                    } else if (deltaZ > DIRECTION_THRESHOLD && deltaZ > deltaX) {
                        primaryAxis = 2; // Z axis started first
                        zDirection = (z - previousZ) > 0 ? 1 : -1; // 1 = right, -1 = left
                    }
                } else if (primaryAxis == 2) {
                    // Update Z direction if already determined
                    double newZDir = z - previousZ;
                    if (Math.abs(newZDir) > DIRECTION_THRESHOLD) {
                        zDirection = newZDir > 0 ? 1 : -1;
                    }
                }
            }
            previousX = x;
            previousZ = z;
        }

        this.currentX = x;
        this.currentZ = z;
        if (Double.isNaN(endX) && !Double.isNaN(startX)) {
            calculateScaleAndPosition();
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
        previousX = previousZ = Double.NaN;
        primaryAxis = 0;
        zDirection = 0;
        angle = Double.NaN;
        distance = 0;
        calculateScaleAndPosition();
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
            angle = Math.min(rawAngle, 90); // Ensure acute angle
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

        // Draw axis labels
        paintLabels.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Z+", viewWidth - 30, viewHeight / 2f - 10, paintLabels);
        canvas.drawText("Z-", 30, viewHeight / 2f - 10, paintLabels);
        canvas.drawText("X+", viewWidth / 2f + 10, 25, paintLabels);
        canvas.drawText("X-", viewWidth / 2f + 10, viewHeight - 15, paintLabels);

        // Draw start point S
        if (!Double.isNaN(startX) && !Double.isNaN(startZ)) {
            // S is always at (0,0) relative, shown at screenSX, screenSY
            canvas.drawCircle(screenSX, screenSY, 14, paintStartPoint);
            paintLabels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("S", screenSX + 18, screenSY + 6, paintLabels);

            // Draw Z axis through S (dashed horizontal line)
            canvas.drawLine(screenSX - 200, screenSY, screenSX + 200, screenSY, paintAxis);

            // Draw line to current position if end point not set
            if (Double.isNaN(endX)) {
                double relX = currentX - startX;
                double relZ = currentZ - startZ;

                float cx = screenSX + (float)(relZ * scale);
                float cy = screenSY + (float)(relX * scale); // X inverted: positive X = down

                paintLine.setColor(ContextCompat.getColor(getContext(), R.color.yellow));
                paintLine.setStrokeWidth(2);
                canvas.drawLine(screenSX, screenSY, cx, cy, paintLine);

                canvas.drawCircle(cx, cy, 10, paintCurrentPos);

                // Calculate and show preview angle (always acute)
                double previewAngle = Math.toDegrees(Math.atan2(Math.abs(relX), Math.abs(relZ)));
                previewAngle = Math.min(previewAngle, 90);
                drawAngleDisplay(canvas, previewAngle, true);

                // Draw direction indicator
                if (primaryAxis != 0) {
                    String directionText;
                    if (primaryAxis == 1) {
                        directionText = "X: поперечная";
                    } else {
                        directionText = zDirection == 1 ? "Z: вправо →" : "Z: влево ←";
                    }
                    paintLabels.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(directionText, viewWidth / 2f, 30, paintLabels);
                }
            }
        }

        // Draw end point and final angle
        if (!Double.isNaN(endX) && !Double.isNaN(endZ) && !Double.isNaN(startX)) {
            double relX = endX - startX;
            double relZ = endZ - startZ;

            float ex = screenSX + (float)(relZ * scale);
            float ey = screenSY + (float)(relX * scale); // X inverted: positive X = down

            paintLine.setColor(ContextCompat.getColor(getContext(), R.color.coord_d));
            paintLine.setStrokeWidth(4);
            canvas.drawLine(screenSX, screenSY, ex, ey, paintLine);

            canvas.drawCircle(ex, ey, 14, paintEndPoint);
            paintLabels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("E", ex + 18, ey + 6, paintLabels);

            drawAngleDisplay(canvas, angle, false);
        }
    }

    private void drawGrid(Canvas canvas) {
        float gridStep = 10 * scale;
        if (gridStep < 20) gridStep = 20;

        // Draw grid centered at S position
        for (float x = screenSX; x < viewWidth; x += gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }
        for (float x = screenSX; x > 0; x -= gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }

        for (float y = screenSY; y < viewHeight; y += gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
        for (float y = screenSY; y > 0; y -= gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
    }

    private void drawAngleDisplay(Canvas canvas, double angleValue, boolean isPreview) {
        if (Double.isNaN(angleValue)) return;

        // Draw angle arc from Z axis (horizontal) toward the line
        float arcRadius = 50;
        RectF arcRect = new RectF(screenSX - arcRadius, screenSY - arcRadius, 
                                   screenSX + arcRadius, screenSY + arcRadius);

        // Arc from Z axis (0°) to the angle
        float sweepAngle = (float) angleValue;
        
        // Determine sweep direction based on relative position
        double relX = Double.isNaN(endX) ? (currentX - startX) : (endX - startX);
        double relZ = Double.isNaN(endZ) ? (currentZ - startZ) : (endZ - startZ);
        
        if (relX >= 0) {
            // Point is below Z axis (positive X) - sweep clockwise (positive)
            sweepAngle = (float) angleValue;
        } else {
            // Point is above Z axis (negative X) - sweep counter-clockwise (negative)
            sweepAngle = -(float) angleValue;
        }

        canvas.drawArc(arcRect, 0, sweepAngle, false, paintAngleArc);

        // Draw angle text box
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

        // Draw text
        paintLabels.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Угол к Z:", textX, textY - 15, paintLabels);
        canvas.drawText(angleText, textX, textY + 25, paintAngleText);

        if (distance > 0) {
            String distText = String.format("L: %.2f мм", distance);
            canvas.drawText(distText, textX, textY + 50, paintLabels);
        }
    }

    /**
     * Draw rounded rectangle compatible with old API
     */
    private void drawRoundedRect(Canvas canvas, float left, float top, float right, float bottom, float radius, Paint paint) {
        RectF rect = new RectF(left, top, right, bottom);
        
        if (Build.VERSION.SDK_INT >= 21) {
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            // Fallback for old API: use Path
            Path path = new Path();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
    }
}
