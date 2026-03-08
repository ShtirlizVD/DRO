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
        paintLabelText.setTextSize(44);
        paintLabelText.setTextAlign(Paint.Align.CENTER);
        paintLabelText.setFakeBoldText(true);

        paintAngleArcZ = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleArcZ.setColor(Color.parseColor("#00BCD4"));
        paintAngleArcZ.setStrokeWidth(5);
        paintAngleArcZ.setStyle(Paint.Style.STROKE);

        paintAngleArcX = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleArcX.setColor(Color.parseColor("#FF9800"));
        paintAngleArcX.setStrokeWidth(5);
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

        if (Double.isNaN(relX) || Double.isNaN(relZ)) {
            // Draw center Z axis when no measurements
            canvas.drawLine(0, centerY, viewWidth, centerY, paintAxis);
            
            paintLabelText.setTextAlign(Paint.Align.CENTER);
            paintLabelText.setAlpha(150);
            canvas.drawText("Установите начальную точку", centerX, centerY - 20, paintLabelText);
            paintLabelText.setAlpha(255);
            return;
        }

        // Calculate triangle vertices - S and E centered on screen
        // Midpoint of SE is at screen center
        float halfZ = (float)(relZ * scale / 2);
        float halfX = (float)(relX * scale / 2);

        // S and E are equidistant from center
        float sX = centerX - halfZ;
        float sY = centerY - halfX;
        float eX = centerX + halfZ;
        float eY = centerY + halfX;
        
        // P is projection of E onto horizontal line through S (Z axis through S)
        float pX = eX;
        float pY = sY;

        boolean triangleAbove = relX < 0;
        
        // Draw Z axis through S (horizontal line)
        canvas.drawLine(0, sY, viewWidth, sY, paintAxis);

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
        if (Math.abs(relZ) < 0.1 || Math.abs(relX) < 0.1) return;

        // Calculate angle of hypotenuse from horizontal (Z axis)
        double hypAngle = Math.atan2(eY - sY, eX - sX);
        float angleDeg = (float) Math.toDegrees(Math.abs(hypAngle));
        if (angleDeg > 90) angleDeg = 180 - angleDeg;
        float angleXDeg = 90 - angleDeg; // Complementary angle

        // Dynamic arc radius - larger for smaller angles
        float baseRadiusZ = 100f;
        float baseRadiusX = 100f;
        
        // Calculate multiplier based on angle
        // At 30°: 1x, at 10°: 4x, at 5°: 5x
        float multZ, multX;
        
        if (angleDeg < 5) {
            multZ = 5f;
        } else if (angleDeg < 10) {
            multZ = 4f + (10 - angleDeg) / 5f;
        } else if (angleDeg < 30) {
            multZ = 1f + (30 - angleDeg) / 20f * 3f;
        } else {
            multZ = 1f;
        }
        
        if (angleXDeg < 5) {
            multX = 5f;
        } else if (angleXDeg < 10) {
            multX = 4f + (10 - angleXDeg) / 5f;
        } else if (angleXDeg < 30) {
            multX = 1f + (30 - angleXDeg) / 20f * 3f;
        } else {
            multX = 1f;
        }
        
        float arcRadiusZ = baseRadiusZ * multZ;
        float arcRadiusX = baseRadiusX * multX;

        // Z angle arc at point S (from Z axis to hypotenuse)
        RectF arcRectZ = new RectF(sX - arcRadiusZ, sY - arcRadiusZ, sX + arcRadiusZ, sY + arcRadiusZ);
        
        boolean eRight = eX > sX;
        boolean eBelow = eY > sY;
        
        if (eRight) {
            if (eBelow) {
                canvas.drawArc(arcRectZ, 0, angleDeg, false, paintAngleArcZ);
            } else {
                canvas.drawArc(arcRectZ, 0, -angleDeg, false, paintAngleArcZ);
            }
        } else {
            if (eBelow) {
                canvas.drawArc(arcRectZ, 180, -angleDeg, false, paintAngleArcZ);
            } else {
                canvas.drawArc(arcRectZ, 180, angleDeg, false, paintAngleArcZ);
            }
        }

        // X angle arc at point E (between vertical PE and hypotenuse ES)
        RectF arcRectX = new RectF(eX - arcRadiusX, eY - arcRadiusX, eX + arcRadiusX, eY + arcRadiusX);

        // Vertical direction from E toward P (on the Y axis)
        // In canvas: 270° = up, 90° = down
        float verticalAngle;
        if (eBelow) {
            // E is below S, so PE goes UP from E
            verticalAngle = -90; // = 270°
        } else {
            // E is above S, so PE goes DOWN from E
            verticalAngle = 90;
        }

        // Hypotenuse direction from E toward S
        float hypAngleDeg = (float) Math.toDegrees(Math.atan2(sY - eY, sX - eX));

        // Draw arc from vertical to hypotenuse
        float startAngle = verticalAngle;
        float sweepAngle = hypAngleDeg - verticalAngle;
        
        // Normalize sweep to be the smaller arc (the angle X)
        if (Math.abs(sweepAngle) > 90) {
            if (sweepAngle > 0) sweepAngle = sweepAngle - 360;
            else sweepAngle = sweepAngle + 360;
        }
        
        canvas.drawArc(arcRectX, startAngle, sweepAngle, false, paintAngleArcX);
    }

    private void drawDimensionLabels(Canvas canvas, float sX, float sY, float pX, float pY, float eX, float eY, boolean triangleAbove) {
        // Determine E position relative to S
        boolean eBelow = eY > sY;
        boolean eRight = eX > sX;
        
        float labelHeight = 50f;
        float labelOffsetH = 55f; // Closer for horizontal
        float labelOffsetV = 80f; // For vertical
        
        // Horizontal side (SP) - OUTSIDE is opposite to where triangle is
        // Triangle is on the side of E (below or above), so outside is opposite
        String zLabel = String.format("%.2f", Math.abs(relZ));
        float spMidX = (sX + pX) / 2;
        // If E is below S, triangle is below, so OUTSIDE is above
        float spOutsideY = sY + (eBelow ? -labelOffsetH : labelOffsetH);
        float spInsideY = sY + (eBelow ? labelOffsetH : -labelOffsetH);
        
        // Check if outside position hits the edge
        boolean spUseInside = (eBelow && spOutsideY - labelHeight/2 < PADDING) ||
                              (!eBelow && spOutsideY + labelHeight/2 > viewHeight - PADDING);
        
        float spLabelY = spUseInside ? spInsideY : spOutsideY;
        drawLabelText(canvas, zLabel, spMidX, spLabelY, paintSideGray.getColor());

        // Vertical side (PE) - OUTSIDE is opposite to where triangle is
        // Triangle is on the side of S (left or right of PE), so outside is opposite
        String xLabel = String.format("%.2f", Math.abs(relX));
        float peMidY = (pY + eY) / 2;
        // If E is right of S, triangle is to the LEFT of PE, so OUTSIDE is to the RIGHT
        // If E is left of S, triangle is to the RIGHT of PE, so OUTSIDE is to the LEFT
        float peOutsideX = pX + (eRight ? labelOffsetV : -labelOffsetV);
        float peInsideX = pX + (eRight ? -labelOffsetV : labelOffsetV);
        
        boolean peUseInside = (eRight && peOutsideX + 60 > viewWidth - PADDING) ||
                              (!eRight && peOutsideX - 60 < PADDING);
        
        float peLabelX = peUseInside ? peInsideX : peOutsideX;
        drawLabelText(canvas, xLabel, peLabelX, peMidY, paintSideGray.getColor());

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
            float offsetMult = (dot > 0) ? -80 : 80;
            
            float hypLabelX = seMidX + nx * offsetMult;
            float hypLabelY = seMidY + ny * offsetMult;
            
            // Check if hypotenuse label fits, if not put inside
            if (hypLabelX < PADDING + 50 || hypLabelX > viewWidth - PADDING - 50 ||
                hypLabelY < PADDING + 40 || hypLabelY > viewHeight - PADDING - 40) {
                offsetMult = -offsetMult;
                hypLabelX = seMidX + nx * offsetMult;
                hypLabelY = seMidY + ny * offsetMult;
            }

            drawLabelText(canvas, hypLabel, hypLabelX, hypLabelY, paintHypotenuse.getColor());
        }
    }

    private void drawLabelText(Canvas canvas, String text, float x, float y, int color) {
        // No background - transparent
        paintLabelText.setColor(color);
        canvas.drawText(text, x, y + 15, paintLabelText);
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
