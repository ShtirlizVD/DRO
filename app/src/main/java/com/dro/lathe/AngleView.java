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

    // Data
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

    // Scale and offset
    private float scale = 10.0f;
    private float originX = 50, originY = 100;

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
        updateOrigin();
        calculateScale();
    }

    private void updateOrigin() {
        // Origin is always at center of view
        originX = viewWidth / 2f;
        originY = viewHeight / 2f;
    }

    private void calculateScale() {
        if (viewWidth <= 1 || viewHeight <= 1) {
            scale = 10;
            return;
        }

        if (Double.isNaN(startX) || Double.isNaN(startZ)) {
            scale = 10;
            return;
        }

        double minX = Double.isNaN(endX) ? Math.min(startX, currentX) : Math.min(startX, Math.min(endX, currentX));
        double maxX = Double.isNaN(endX) ? Math.max(startX, currentX) : Math.max(startX, Math.max(endX, currentX));
        double minZ = Double.isNaN(endZ) ? Math.min(startZ, currentZ) : Math.min(startZ, Math.min(endZ, currentZ));
        double maxZ = Double.isNaN(endZ) ? Math.max(startZ, currentZ) : Math.max(startZ, Math.max(endZ, currentZ));

        double rangeX = Math.abs(maxX - minX);
        double rangeZ = Math.abs(maxZ - minZ);
        double maxRange = Math.max(rangeX, rangeZ);
        maxRange = Math.max(maxRange, 20);

        float availableWidth = viewWidth * 0.7f;
        float availableHeight = viewHeight * 0.7f;
        float availableSize = Math.min(availableWidth, availableHeight);

        scale = availableSize / (float) maxRange;
        scale = Math.max(scale, 1);
        scale = Math.min(scale, 50);
    }

    public void setStartPoint(double x, double z) {
        this.startX = x;
        this.startZ = z;
        // Reset direction tracking for new measurement
        this.previousX = Double.NaN;
        this.previousZ = Double.NaN;
        this.primaryAxis = 0;
        this.zDirection = 0;
        calculateScale();
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
        calculateScale();
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
        previousX = previousZ = Double.NaN;
        primaryAxis = 0;
        zDirection = 0;
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

        distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (deltaZ == 0 && deltaX == 0) {
            angle = 0;
        } else {
            // Angle from Z axis
            angle = Math.abs(Math.toDegrees(Math.atan2(deltaX, deltaZ)));
        }
    }

    // Convert Z coordinate to screen X (horizontal)
    private float toScreenX(double z) {
        return originX + (float) (z * scale);
    }

    // Convert X coordinate to screen Y (vertical, inverted because screen Y goes down)
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

        // Draw Z axis (horizontal dashed line through center)
        canvas.drawLine(0, originY, viewWidth, originY, paintAxis);

        // Draw axis labels
        paintLabels.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Z+", viewWidth - 15, originY - 10, paintLabels);
        paintLabels.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Z-", 15, originY - 10, paintLabels);
        
        paintLabels.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("X+", originX + 10, 25, paintLabels);
        canvas.drawText("X-", originX + 10, viewHeight - 15, paintLabels);

        // Draw start point
        if (!Double.isNaN(startX) && !Double.isNaN(startZ)) {
            // Calculate start point position based on primary axis and direction
            float sx, sy;
            
            if (primaryAxis == 2 && zDirection != 0) {
                // Z axis moved first - position S based on Z direction
                float margin = viewWidth * 0.15f;
                if (zDirection == 1) {
                    // Moving right (Z increasing) - S on the left
                    sx = margin;
                } else {
                    // Moving left (Z decreasing) - S on the right
                    sx = viewWidth - margin;
                }
                sy = originY;
            } else {
                // X axis moved first or unknown - S at center
                sx = originX;
                sy = originY;
            }

            canvas.drawCircle(sx, sy, 14, paintStartPoint);
            paintLabels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("S", sx + 18, sy + 6, paintLabels);

            // Draw line to current position if end point not set
            if (Double.isNaN(endX)) {
                float cx = toScreenX(currentZ - startZ);
                float cy = toScreenY(currentX - startX);
                
                // Offset from S position
                if (primaryAxis == 2 && zDirection != 0) {
                    // When Z first, offset from S position
                    float baseZ = (zDirection == 1) ? -viewWidth * 0.35f : viewWidth * 0.35f;
                    cx = sx + (float)((currentZ - startZ) * scale);
                }

                paintLine.setColor(ContextCompat.getColor(getContext(), R.color.yellow));
                paintLine.setStrokeWidth(2);
                canvas.drawLine(sx, sy, cx, cy, paintLine);

                canvas.drawCircle(cx, cy, 10, paintCurrentPos);

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

                double previewAngle = Math.abs(Math.toDegrees(
                        Math.atan2(currentX - startX, currentZ - startZ)));
                drawAngleDisplay(canvas, sx, sy, previewAngle, true);
            }
        }

        // Draw end point and angle
        if (!Double.isNaN(endX) && !Double.isNaN(endZ) && !Double.isNaN(startX)) {
            // Use same positioning logic for S
            float sx, sy;
            float margin = viewWidth * 0.15f;
            
            double deltaZ = endZ - startZ;
            if (Math.abs(deltaZ) > Math.abs(endX - startX) && Math.abs(deltaZ) > DIRECTION_THRESHOLD) {
                // Z movement dominant
                if (deltaZ > 0) {
                    sx = margin; // Moving right - S on left
                } else {
                    sx = viewWidth - margin; // Moving left - S on right  
                }
                sy = originY;
            } else {
                // X movement dominant or equal
                sx = originX;
                sy = originY;
            }

            float ex = sx + (float)((endZ - startZ) * scale);
            float ey = sy - (float)((endX - startX) * scale);

            paintLine.setColor(ContextCompat.getColor(getContext(), R.color.coord_d));
            paintLine.setStrokeWidth(4);
            canvas.drawLine(sx, sy, ex, ey, paintLine);

            canvas.drawCircle(ex, ey, 14, paintEndPoint);
            paintLabels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("E", ex + 18, ey + 6, paintLabels);

            drawAngleDisplay(canvas, sx, sy, angle, false);
        }
    }

    private void drawGrid(Canvas canvas) {
        float gridStep = 10 * scale;
        if (gridStep < 20) gridStep = 20;

        for (float x = originX; x < viewWidth; x += gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }
        for (float x = originX; x > 0; x -= gridStep) {
            canvas.drawLine(x, 0, x, viewHeight, paintGrid);
        }

        for (float y = originY; y < viewHeight; y += gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
        for (float y = originY; y > 0; y -= gridStep) {
            canvas.drawLine(0, y, viewWidth, y, paintGrid);
        }
    }

    private void drawAngleDisplay(Canvas canvas, float sx, float sy, double angleValue, boolean isPreview) {
        if (Double.isNaN(angleValue)) return;

        // Draw angle arc from Z axis (horizontal) toward the line
        float arcRadius = 50;
        RectF arcRect = new RectF(sx - arcRadius, sy - arcRadius, sx + arcRadius, sy + arcRadius);

        // Arc starts from Z axis (0 degrees = right) and sweeps toward the line
        float sweepAngle;
        if (!Double.isNaN(endX)) {
            double deltaX = endX - startX;
            double deltaZ = endZ - startZ;
            // Angle in degrees from positive Z direction
            sweepAngle = (float) Math.toDegrees(Math.atan2(deltaX, deltaZ));
        } else {
            double deltaX = currentX - startX;
            double deltaZ = currentZ - startZ;
            sweepAngle = (float) Math.toDegrees(Math.atan2(deltaX, deltaZ));
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

        // Draw rounded rect using Path (compatible with old API)
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
