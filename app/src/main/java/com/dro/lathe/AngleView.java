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
    private double startX = Double.NaN, startZ = Double.NaN;

    // Scale for drawing
    private float scale = 10.0f;

    // View dimensions
    private int viewWidth = 1, viewHeight = 1;

    // Minimal padding for maximum triangle size
    private static final float PADDING = 20f;
    private static final float LABEL_MARGIN = 60f;

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
        paintAngleArcZ.setStrokeWidth(3);
        paintAngleArcZ.setStyle(Paint.Style.STROKE);

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

        // Maximum triangle size - minimal margins
        float availableWidth = viewWidth - 2 * PADDING;
        float availableHeight = viewHeight - 2 * PADDING;

        float availableSize = Math.min(availableWidth, availableHeight);
        scale = (float) (availableSize / maxRange);
        scale = Math.max(scale, 1);
        scale = Math.min(scale, 300);
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

        // Draw Z axis
        canvas.drawLine(0, centerY, viewWidth, centerY, paintAxis);

        if (Double.isNaN(relX) || Double.isNaN(relZ)) {
            paintLabelText.setTextAlign(Paint.Align.CENTER);
            paintLabelText.setAlpha(150);
            canvas.drawText("Установите начальную точку", centerX, centerY - 20, paintLabelText);
            paintLabelText.setAlpha(255);
            return;
        }

        // Calculate triangle vertices - centered
        float halfZ = (float)(relZ * scale / 2);
        float halfX = (float)(relX * scale / 2);

        float sX = centerX - halfZ;
        float sY = centerY;
        float eX = centerX + halfZ;
        float eY = centerY + halfX;
        float pX = eX;
        float pY = sY;

        boolean triangleAbove = relX < 0;

        // Draw triangle sides
        if (Math.abs(relZ) > 0.01) {
            canvas.drawLine(sX, sY, pX, pY, paintSideGray);
        }
        if (Math.abs(relX) > 0.01) {
            canvas.drawLine(pX, pY, eX, eY, paintSideGray);
        }
        canvas.drawLine(sX, sY, eX, eY, paintHypotenuse);

        // Draw angle arcs
        drawAngleArcs(canvas, sX, sY, pX, pY, eX, eY, triangleAbove);

        // Draw vertices
        canvas.drawCircle(sX, sY, 14, paintStartPoint);
        canvas.drawCircle(eX, eY, 14, paintEndPoint);

        // Draw dimension labels with smart positioning
        drawDimensionLabels(canvas, sX, sY, pX, pY, eX, eY, triangleAbove);
    }

    private void drawAngleArcs(Canvas canvas, float sX, float sY, float pX, float pY, float eX, float eY, boolean triangleAbove) {
        float arcRadius = 50;

        double hypAngle = Math.atan2(eY - sY, eX - sX);
        double angleZ = Math.abs(Math.toDegrees(hypAngle));
        if (angleZ > 90) angleZ = 180 - angleZ;

        RectF arcRectZ = new RectF(sX - arcRadius, sY - arcRadius, sX + arcRadius, sY + arcRadius);

        if (Math.abs(relZ) > 0.1 && Math.abs(relX) > 0.1) {
            if (relZ > 0) {
                if (triangleAbove) {
                    canvas.drawArc(arcRectZ, 180 - (float) angleZ, (float) angleZ, false, paintAngleArcZ);
                } else {
                    canvas.drawArc(arcRectZ, 0, (float) angleZ, false, paintAngleArcZ);
                }
            } else {
                if (triangleAbove) {
                    canvas.drawArc(arcRectZ, 180, (float) angleZ, false, paintAngleArcZ);
                } else {
                    canvas.drawArc(arcRectZ, -(float)angleZ, (float) angleZ, false, paintAngleArcZ);
                }
            }

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
        // Measure text height
        float labelHeight = 32f;
        
        // Horizontal side (SP) - check if outside fits
        String zLabel = String.format("%.2f", Math.abs(relZ));
        float spMidX = (sX + pX) / 2;
        float spOutsideY = sY + (triangleAbove ? 40 : -40);
        float spInsideY = sY + (triangleAbove ? -40 : 40);
        
        // Check if outside position is within bounds
        boolean spUseInside = (triangleAbove && spOutsideY + labelHeight/2 > viewHeight - PADDING) ||
                              (!triangleAbove && spOutsideY - labelHeight/2 < PADDING);
        
        float spLabelY = spUseInside ? spInsideY : spOutsideY;
        drawLabelWithBackground(canvas, zLabel, spMidX, spLabelY, paintSideGray.getColor());

        // Vertical side (PE) - check if outside fits
        String xLabel = String.format("%.2f", Math.abs(relX));
        float peMidY = (pY + eY) / 2;
        float peOutsideX = pX + (relZ >= 0 ? 55 : -55);
        float peInsideX = pX + (relZ >= 0 ? -55 : 55);
        
        // Check if outside position is within bounds
        boolean peUseInside = (relZ >= 0 && peOutsideX + 40 > viewWidth - PADDING) ||
                              (relZ < 0 && peOutsideX - 40 < PADDING);
        
        float peLabelX = peUseInside ? peInsideX : peOutsideX;
        drawLabelWithBackground(canvas, xLabel, peLabelX, peMidY, paintSideGray.getColor());

        // Hypotenuse (SE) - always outside (opposite to right angle at P)
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
            
            // Outside direction (away from P)
            float offsetMult = (dot > 0) ? -50 : 50;
            
            float hypLabelX = seMidX + nx * offsetMult;
            float hypLabelY = seMidY + ny * offsetMult;
            
            // Check if hypotenuse label fits, if not put inside
            if (hypLabelX < PADDING + 30 || hypLabelX > viewWidth - PADDING - 30 ||
                hypLabelY < PADDING + 20 || hypLabelY > viewHeight - PADDING - 20) {
                // Move to inside
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
