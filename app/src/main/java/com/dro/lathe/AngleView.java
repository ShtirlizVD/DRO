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
 */
public class AngleView extends View {

    // Paints
    private Paint paintAxis;
    private Paint paintSideGray;
    private Paint paintHypotenuse;
    private Paint paintStartPoint;
    private Paint paintEndPoint;
    private Paint paintLabelBg;
    private Paint paintLabelText;
    private Paint paintAngleArcZ;
    private Paint paintAngleArcX;

    // Data
    private double relX = Double.NaN;
    private double relZ = Double.NaN;
    private double angle = Double.NaN;

    // Scale for drawing
    private float scale = 10.0f;

    // View dimensions
    private int viewWidth = 1, viewHeight = 1;

    // Minimal padding for maximum triangle size
    private static final float PADDING = 25f;

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
        paintAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAxis.setColor(ContextCompat.getColor(context, R.color.text_dim));
        paintAxis.setStrokeWidth(1);
        paintAxis.setAlpha(80);

        paintSideGray = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSideGray.setColor(Color.parseColor("#808080"));
        paintSideGray.setStrokeWidth(4);
        paintSideGray.setStyle(Paint.Style.STROKE);

        paintHypotenuse = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHypotenuse.setColor(Color.parseColor("#FFD700"));
        paintHypotenuse.setStrokeWidth(5);
        paintHypotenuse.setStyle(Paint.Style.STROKE);

        paintStartPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStartPoint.setColor(Color.parseColor("#4CAF50"));
        paintStartPoint.setStyle(Paint.Style.FILL);

        paintEndPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintEndPoint.setColor(ContextCompat.getColor(context, R.color.coord_z));
        paintEndPoint.setStyle(Paint.Style.FILL);

        paintLabelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabelBg.setColor(ContextCompat.getColor(context, R.color.bg_dark));
        paintLabelBg.setAlpha(240);
        paintLabelBg.setStyle(Paint.Style.FILL);

        paintLabelText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabelText.setColor(ContextCompat.getColor(context, R.color.text_bright));
        paintLabelText.setTextSize(22);
        paintLabelText.setTextAlign(Paint.Align.CENTER);
        paintLabelText.setFakeBoldText(true);

        paintAngleArcZ = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleArcZ.setColor(Color.parseColor("#00BCD4"));
        paintAngleArcZ.setStrokeWidth(4);
        paintAngleArcZ.setStyle(Paint.Style.STROKE);

        paintAngleArcX = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleArcX.setColor(Color.parseColor("#FF9800"));
        paintAngleArcX.setStrokeWidth(4);
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

        float availableWidth = viewWidth - 2 * PADDING;
        float availableHeight = viewHeight - 2 * PADDING;

        float availableSize = Math.min(availableWidth, availableHeight);
        scale = (float) (availableSize / maxRange);
        scale = Math.max(scale, 1);
        scale = Math.min(scale, 300);
    }

    public void setStartPoint(double x, double z) {
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

        // Draw Z axis (horizontal)
        canvas.drawLine(0, centerY, viewWidth, centerY, paintAxis);

        if (Double.isNaN(relX) || Double.isNaN(relZ)) {
            paintLabelText.setTextAlign(Paint.Align.CENTER);
            paintLabelText.setAlpha(150);
            canvas.drawText("Установите начальную точку", centerX, centerY - 20, paintLabelText);
            paintLabelText.setAlpha(255);
            return;
        }

        // Triangle vertices
        float halfZ = (float)(relZ * scale / 2);
        float halfX = (float)(relX * scale / 2);

        // S at center on Z axis
        float sX = centerX;
        float sY = centerY;
        
        // E relative to S
        float eX = centerX + (float)(relZ * scale / 2);
        float eY = centerY + (float)(relX * scale / 2);
        
        // P - projection of E onto Z axis through S
        float pX = eX;
        float pY = sY;

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
        drawAngleArcs(canvas, sX, sY, pX, pY, eX, eY);

        // Draw vertices
        canvas.drawCircle(sX, sY, 14, paintStartPoint);
        canvas.drawCircle(eX, eY, 14, paintEndPoint);

        // Draw dimension labels
        drawDimensionLabels(canvas, sX, sY, pX, pY, eX, eY);
    }

    private void drawAngleArcs(Canvas canvas, float sX, float sY, float pX, float pY, float eX, float eY) {
        if (Math.abs(relZ) < 0.1 || Math.abs(relX) < 0.1) return;

        float arcRadiusZ = 60f;
        float arcRadiusX = 50f;
        
        // Angle Z arc at point S
        // Starts from Z axis (horizontal) and goes to hypotenuse
        RectF arcRectZ = new RectF(sX - arcRadiusZ, sY - arcRadiusZ, sX + arcRadiusZ, sY + arcRadiusZ);
        
        // Angle X arc at point P
        // Starts from vertical line and goes to hypotenuse
        RectF arcRectX = new RectF(pX - arcRadiusX, pY - arcRadiusX, pX + arcRadiusX, pY + arcRadiusX);

        float angleDeg = (float) angle;
        float angleXDeg = 90f - angleDeg;

        // Determine direction based on where E is relative to S
        boolean eRight = relZ > 0;  // E is to the right of S
        boolean eBelow = relX > 0;  // E is below the Z axis

        // Z angle arc at S
        // If E is to the right: arc from 0° (right) toward the hypotenuse
        // If E is to the left: arc from 180° (left) toward the hypotenuse
        if (eRight) {
            if (eBelow) {
                // Arc from 0° clockwise by angleDeg
                canvas.drawArc(arcRectZ, 0, angleDeg, false, paintAngleArcZ);
            } else {
                // Arc from 0° counter-clockwise by angleDeg
                canvas.drawArc(arcRectZ, 0, -angleDeg, false, paintAngleArcZ);
            }
        } else {
            if (eBelow) {
                // Arc from 180° counter-clockwise by angleDeg
                canvas.drawArc(arcRectZ, 180, -angleDeg, false, paintAngleArcZ);
            } else {
                // Arc from 180° clockwise by angleDeg
                canvas.drawArc(arcRectZ, 180, angleDeg, false, paintAngleArcZ);
            }
        }

        // X angle arc at P
        // At point P, we have the right angle
        // The arc should show the complementary angle (90° - angleZ)
        if (eRight) {
            if (eBelow) {
                // Arc from 270° (down) counter-clockwise by angleXDeg
                canvas.drawArc(arcRectX, 270, -angleXDeg, false, paintAngleArcX);
            } else {
                // Arc from 90° (up) clockwise by angleXDeg
                canvas.drawArc(arcRectX, 90, angleXDeg, false, paintAngleArcX);
            }
        } else {
            if (eBelow) {
                // Arc from 270° (down) clockwise by angleXDeg
                canvas.drawArc(arcRectX, 270, angleXDeg, false, paintAngleArcX);
            } else {
                // Arc from 90° (up) counter-clockwise by angleXDeg
                canvas.drawArc(arcRectX, 90, -angleXDeg, false, paintAngleArcX);
            }
        }
    }

    private void drawDimensionLabels(Canvas canvas, float sX, float sY, float pX, float pY, float eX, float eY) {
        float labelHeight = 32f;

        // Horizontal side (SP)
        String zLabel = String.format("%.2f", Math.abs(relZ));
        float spMidX = (sX + pX) / 2;
        boolean eBelow = relX > 0;
        float spOutsideY = sY + (eBelow ? 45 : -45);
        float spInsideY = sY + (eBelow ? -45 : 45);

        boolean spUseInside = (eBelow && spOutsideY + labelHeight/2 > viewHeight - PADDING) ||
                              (!eBelow && spOutsideY - labelHeight/2 < PADDING);

        float spLabelY = spUseInside ? spInsideY : spOutsideY;
        drawLabelWithBackground(canvas, zLabel, spMidX, spLabelY, paintSideGray.getColor());

        // Vertical side (PE)
        String xLabel = String.format("%.2f", Math.abs(relX));
        float peMidY = (pY + eY) / 2;
        boolean eRight = relZ > 0;
        float peOutsideX = pX + (eRight ? 55 : -55);
        float peInsideX = pX + (eRight ? -55 : 55);

        boolean peUseInside = (eRight && peOutsideX + 40 > viewWidth - PADDING) ||
                              (!eRight && peOutsideX - 40 < PADDING);

        float peLabelX = peUseInside ? peInsideX : peOutsideX;
        drawLabelWithBackground(canvas, xLabel, peLabelX, peMidY, paintSideGray.getColor());

        // Hypotenuse (SE) - outside (opposite to right angle at P)
        String hypLabel = String.format("%.2f", Math.sqrt(relX * relX + relZ * relZ));
        float seMidX = (sX + eX) / 2;
        float seMidY = (sY + eY) / 2;

        float dx = eX - sX;
        float dy = eY - sY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);

        if (len > 0) {
            float nx = -dy / len;
            float ny = dx / len;

            float toPx = pX - seMidX;
            float toPy = pY - seMidY;
            float dot = nx * toPx + ny * toPy;

            float offsetMult = (dot > 0) ? -55 : 55;

            float hypLabelX = seMidX + nx * offsetMult;
            float hypLabelY = seMidY + ny * offsetMult;

            if (hypLabelX < PADDING + 30 || hypLabelX > viewWidth - PADDING - 30 ||
                hypLabelY < PADDING + 20 || hypLabelY > viewHeight - PADDING - 20) {
                offsetMult = -offsetMult;
                hypLabelX = seMidX + nx * offsetMult;
                hypLabelY = seMidY + ny * offsetMult;
            }

            drawLabelWithBackground(canvas, hypLabel, hypLabelX, hypLabelY, paintHypotenuse.getColor());
        }
    }

    private void drawLabelWithBackground(Canvas canvas, String text, float x, float y, int color) {
        float textWidth = paintLabelText.measureText(text) + 16;
        float left = x - textWidth / 2;
        float top = y - 14;
        float right = x + textWidth / 2;
        float bottom = y + 14;

        drawRoundedRect(canvas, left, top, right, bottom, 5, paintLabelBg);

        paintLabelText.setColor(color);
        canvas.drawText(text, x, y + 7, paintLabelText);
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
