package com.dro.lathe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * Custom view для визуализации угла в виде прямоугольного треугольника
 *
 * Треугольник центрируется на экране.
 * Гипотенуза - жёлтая, катеты - серые.
 * Все стороны подписаны размерами снаружи треугольника.
 */
public class AngleView extends View {

    // Paints
    private Paint paintAxis;
    private Paint paintSideGray;      // Gray sides (legs)
    private Paint paintHypotenuse;    // Yellow hypotenuse
    private Paint paintStartPoint;    // Green start point
    private Paint paintEndPoint;
    private Paint paintLabelBg;
    private Paint paintLabelText;
    private Paint paintAngleArcZ;     // Arc for Z angle (cyan)
    private Paint paintAngleArcX;     // Arc for X angle (orange)

    // Data
    private double relX = Double.NaN;
    private double relZ = Double.NaN;
    private double angle = Double.NaN;
    private double startX = Double.NaN, startZ = Double.NaN;

    // Scale for drawing
    private float scale = 10.0f;

    // View dimensions
    private int viewWidth = 1, viewHeight = 1;

    // Reduced padding for larger triangle
    private static final float PADDING = 40f;

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
        // Z axis paint
        paintAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAxis.setColor(ContextCompat.getColor(context, R.color.text_dim));
        paintAxis.setStrokeWidth(1);
        paintAxis.setAlpha(100);

        // Gray sides (legs)
        paintSideGray = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSideGray.setColor(Color.parseColor("#808080"));
        paintSideGray.setStrokeWidth(4);
        paintSideGray.setStyle(Paint.Style.STROKE);

        // Yellow hypotenuse
        paintHypotenuse = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHypotenuse.setColor(Color.parseColor("#FFD700"));
        paintHypotenuse.setStrokeWidth(5);
        paintHypotenuse.setStyle(Paint.Style.STROKE);

        // Start point - GREEN
        paintStartPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStartPoint.setColor(Color.parseColor("#4CAF50"));
        paintStartPoint.setStyle(Paint.Style.FILL);

        // End point
        paintEndPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintEndPoint.setColor(ContextCompat.getColor(context, R.color.coord_z));
        paintEndPoint.setStyle(Paint.Style.FILL);

        // Label background
        paintLabelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabelBg.setColor(ContextCompat.getColor(context, R.color.bg_dark));
        paintLabelBg.setAlpha(240);
        paintLabelBg.setStyle(Paint.Style.FILL);

        // Label text
        paintLabelText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabelText.setColor(ContextCompat.getColor(context, R.color.text_bright));
        paintLabelText.setTextSize(24);
        paintLabelText.setTextAlign(Paint.Align.CENTER);
        paintLabelText.setFakeBoldText(true);

        // Angle Z arc (cyan)
        paintAngleArcZ = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleArcZ.setColor(Color.parseColor("#00BCD4"));
        paintAngleArcZ.setStrokeWidth(3);
        paintAngleArcZ.setStyle(Paint.Style.STROKE);

        // Angle X arc (orange)
        paintAngleArcX = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleArcX.setColor(Color.parseColor("#FF9800"));
        paintAngleArcX.setStrokeWidth(3);
        paintAngleArcX.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = Math.max(w, 1);
        viewHeight = Math.max(h, 1);
        calculateScale();
    }

    private void calculateScale() {
        if (viewWidth <= 1 || viewHeight <= 1 || Double.isNaN(relX) || Double.isNaN(relZ)) {
            scale = 10;
            return;
        }

        double maxRange = Math.max(Math.abs(relX), Math.abs(relZ));
        maxRange = Math.max(maxRange, 1);

        // Larger triangle - use most of the available space
        float availableWidth = viewWidth - 2 * PADDING - 100;
        float availableHeight = viewHeight - 2 * PADDING - 80;

        float availableSize = Math.min(availableWidth, availableHeight);
        scale = (float) (availableSize / maxRange);
        scale = Math.max(scale, 1);
        scale = Math.min(scale, 200);
    }

    public void setStartPoint(double x, double z) {
        this.startX = x;
        this.startZ = z;
        invalidate();
    }

    public void setCurrentPosition(double x, double z) {
        invalidate();
    }

    public void setMeasurements(double relX, double relZ, double angle) {
        this.relX = relX;
        this.relZ = relZ;
        this.angle = angle;
        calculateScale();
        invalidate();
    }

    public void reset() {
        relX = relZ = Double.NaN;
        angle = Double.NaN;
        startX = startZ = Double.NaN;
        scale = 10;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (viewWidth <= 1 || viewHeight <= 1) return;

        canvas.drawColor(ContextCompat.getColor(getContext(), R.color.bg_darker));

        float centerX = viewWidth / 2f;
        float centerY = viewHeight / 2f;

        // Draw Z axis (horizontal line through center)
        canvas.drawLine(0, centerY, viewWidth, centerY, paintAxis);

        // If no measurements, show instruction
        if (Double.isNaN(relX) || Double.isNaN(relZ)) {
            paintLabelText.setTextAlign(Paint.Align.CENTER);
            paintLabelText.setAlpha(150);
            canvas.drawText("Установите начальную точку", centerX, centerY - 20, paintLabelText);
            paintLabelText.setAlpha(255);
            return;
        }

        // Calculate triangle vertices
        float halfZ = (float)(relZ * scale / 2);
        float halfX = (float)(relX * scale / 2);

        float sX = centerX - halfZ;
        float sY = centerY;
        float eX = centerX + halfZ;
        float eY = centerY + halfX;
        float pX = eX;
        float pY = sY;

        // Determine if triangle is "above" or "below" the Z axis
        boolean triangleAbove = relX < 0;

        // Draw triangle sides
        // SP - horizontal leg (gray)
        if (Math.abs(relZ) > 0.01) {
            canvas.drawLine(sX, sY, pX, pY, paintSideGray);
        }

        // PE - vertical leg (gray)
        if (Math.abs(relX) > 0.01) {
            canvas.drawLine(pX, pY, eX, eY, paintSideGray);
        }

        // SE - hypotenuse (yellow)
        canvas.drawLine(sX, sY, eX, eY, paintHypotenuse);

        // Draw angle arcs
        drawAngleArcs(canvas, sX, sY, pX, pY, eX, eY, triangleAbove);

        // Draw vertices (no labels)
        canvas.drawCircle(sX, sY, 12, paintStartPoint);  // S - green
        canvas.drawCircle(eX, eY, 12, paintEndPoint);    // E

        // Draw dimension labels OUTSIDE the triangle
        drawDimensionLabels(canvas, sX, sY, pX, pY, eX, eY, triangleAbove);
    }

    private void drawAngleArcs(Canvas canvas, float sX, float sY, float pX, float pY, float eX, float eY, boolean triangleAbove) {
        float arcRadius = 50;

        // Calculate angle of hypotenuse from horizontal (Z axis)
        double hypAngle = Math.atan2(eY - sY, eX - sX);
        double angleZ = Math.abs(Math.toDegrees(hypAngle));
        if (angleZ > 90) angleZ = 180 - angleZ;

        // Draw Z angle arc at point S
        RectF arcRectZ = new RectF(sX - arcRadius, sY - arcRadius, sX + arcRadius, sY + arcRadius);

        if (Math.abs(relZ) > 0.1 && Math.abs(relX) > 0.1) {
            if (relZ > 0) {
                // E is to the right of S
                if (triangleAbove) {
                    canvas.drawArc(arcRectZ, 180 - (float) angleZ, (float) angleZ, false, paintAngleArcZ);
                } else {
                    canvas.drawArc(arcRectZ, 0, (float) angleZ, false, paintAngleArcZ);
                }
            } else {
                // E is to the left of S
                if (triangleAbove) {
                    canvas.drawArc(arcRectZ, 180, (float) angleZ, false, paintAngleArcZ);
                } else {
                    canvas.drawArc(arcRectZ, -(float)angleZ, (float) angleZ, false, paintAngleArcZ);
                }
            }

            // X angle arc at point P
            float arcRadiusX = 40;
            RectF arcRectX = new RectF(pX - arcRadiusX, pY - arcRadiusX, pX + arcRadiusX, pY + arcRadiusX);

            double angleX = 90 - angleZ;

            if (triangleAbove) {
                canvas.drawArc(arcRectX, 270 - (float) angleX, (float) angleX, false, paintAngleArcX);
            } else {
                canvas.drawArc(arcRectX, 90, (float) angleX, false, paintAngleArcX);
            }
        }
    }

    private void drawDimensionLabels(Canvas canvas, float sX, float sY, float pX, float pY, float eX, float eY, boolean triangleAbove) {
        // Horizontal side (SP) - always outside (below if triangle above, above if triangle below)
        String zLabel = String.format("%.2f", Math.abs(relZ));
        float spMidX = (sX + pX) / 2;
        float spOffsetY = triangleAbove ? 35 : -35;

        drawLabelWithBackground(canvas, zLabel, spMidX, sY + spOffsetY, paintSideGray.getColor());

        // Vertical side (PE) - always outside (right if E is right of S, left otherwise)
        String xLabel = String.format("%.2f", Math.abs(relX));
        float peMidY = (pY + eY) / 2;
        float peOffsetX = relZ >= 0 ? 50 : -50;

        drawLabelWithBackground(canvas, xLabel, pX + peOffsetX, peMidY, paintSideGray.getColor());

        // Hypotenuse (SE) - OUTSIDE the triangle (opposite side from the right angle at P)
        String hypLabel = String.format("%.2f", Math.sqrt(relX * relX + relZ * relZ));
        float seMidX = (sX + eX) / 2;
        float seMidY = (sY + eY) / 2;

        // Direction from S to E
        float dx = eX - sX;
        float dy = eY - sY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (len > 0) {
            // Normal vector perpendicular to hypotenuse
            float nx = -dy / len;  // perpendicular X
            float ny = dx / len;   // perpendicular Y

            // Point P is at (pX, pY) - the right angle corner
            // The "inside" of the triangle is where P is located relative to the hypotenuse
            // We want the label on the OUTSIDE, so we go opposite to P

            // Vector from hypotenuse midpoint to P
            float toPx = pX - seMidX;
            float toPy = pY - seMidY;
            
            // Determine if normal points toward P or away from P
            // If dot product > 0, normal points toward P, so we flip it
            float dot = nx * toPx + ny * toPy;
            
            // We want to go AWAY from P (outside the triangle)
            float offsetMult = (dot > 0) ? -45 : 45;
            
            float labelX = seMidX + nx * offsetMult;
            float labelY = seMidY + ny * offsetMult;

            drawLabelWithBackground(canvas, hypLabel, labelX, labelY, paintHypotenuse.getColor());
        }
    }

    private void drawLabelWithBackground(Canvas canvas, String text, float x, float y, int color) {
        float textWidth = paintLabelText.measureText(text) + 16;
        float left = x - textWidth / 2;
        float top = y - 16;
        float right = x + textWidth / 2;
        float bottom = y + 16;

        drawRoundedRect(canvas, left, top, right, bottom, 5, paintLabelBg);

        paintLabelText.setColor(color);
        canvas.drawText(text, x, y + 8, paintLabelText);
    }

    private void drawRoundedRect(Canvas canvas, float left, float top, float right, float bottom, float radius, Paint paint) {
        if (Build.VERSION.SDK_INT >= 21) {
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
        } else {
            RectF rect = new RectF(left, top, right, bottom);
            Path path = new Path();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
    }
}
