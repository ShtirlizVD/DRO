package com.dro.lathe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * Custom view для визуализации угла в виде прямоугольного треугольника
 *
 * Треугольник центрируется на экране так, что линия SE проходит через центр.
 * Гипотенуза - жёлтая, катеты - серые.
 * Все стороны подписаны размерами.
 */
public class AngleView extends View {

    // Paints
    private Paint paintAxis;
    private Paint paintSideGray;      // Gray sides (legs)
    private Paint paintHypotenuse;    // Yellow hypotenuse
    private Paint paintVertex;
    private Paint paintLabelBg;
    private Paint paintLabelText;
    private Paint paintAngleText;

    // Data
    private double relX = Double.NaN;  // Relative X (delta from start to end)
    private double relZ = Double.NaN;  // Relative Z (delta from start to end)
    private double angle = Double.NaN;
    private double startX = Double.NaN, startZ = Double.NaN;

    // Scale for drawing
    private float scale = 10.0f;

    // View dimensions
    private int viewWidth = 1, viewHeight = 1;

    // Padding from edges
    private static final float PADDING = 80f;

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
        // Z axis paint (dashed horizontal line through center)
        paintAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAxis.setColor(ContextCompat.getColor(context, R.color.text_dim));
        paintAxis.setStrokeWidth(2);
        paintAxis.setAlpha(120);

        // Gray sides (legs of triangle)
        paintSideGray = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSideGray.setColor(Color.parseColor("#808080")); // Gray
        paintSideGray.setStrokeWidth(4);
        paintSideGray.setStyle(Paint.Style.STROKE);

        // Yellow hypotenuse
        paintHypotenuse = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHypotenuse.setColor(Color.parseColor("#FFD700")); // Gold/Yellow
        paintHypotenuse.setStrokeWidth(5);
        paintHypotenuse.setStyle(Paint.Style.STROKE);

        // Vertex points
        paintVertex = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintVertex.setColor(ContextCompat.getColor(context, R.color.text_bright));
        paintVertex.setStyle(Paint.Style.FILL);

        // Label background
        paintLabelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabelBg.setColor(ContextCompat.getColor(context, R.color.bg_dark));
        paintLabelBg.setAlpha(230);
        paintLabelBg.setStyle(Paint.Style.FILL);

        // Label text
        paintLabelText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabelText.setColor(ContextCompat.getColor(context, R.color.text_bright));
        paintLabelText.setTextSize(22);
        paintLabelText.setTextAlign(Paint.Align.CENTER);
        paintLabelText.setFakeBoldText(true);

        // Angle text
        paintAngleText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAngleText.setColor(ContextCompat.getColor(context, R.color.coord_l));
        paintAngleText.setTextSize(48);
        paintAngleText.setTextAlign(Paint.Align.CENTER);
        paintAngleText.setFakeBoldText(true);
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

        // Determine max range for scaling
        double maxRange = Math.max(Math.abs(relX), Math.abs(relZ));
        maxRange = Math.max(maxRange, 1); // Minimum range

        // Calculate scale to fit in view with padding
        float availableWidth = viewWidth - 2 * PADDING - 150; // Extra for labels
        float availableHeight = viewHeight - 2 * PADDING - 100;

        float availableSize = Math.min(availableWidth, availableHeight);
        scale = (float) (availableSize / maxRange);
        scale = Math.max(scale, 1);
        scale = Math.min(scale, 100);
    }

    public void setStartPoint(double x, double z) {
        this.startX = x;
        this.startZ = z;
        invalidate();
    }

    public void setCurrentPosition(double x, double z) {
        // Not used directly - we use setMeasurements instead
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

        // Draw background
        canvas.drawColor(ContextCompat.getColor(getContext(), R.color.bg_darker));

        // Draw Z axis (horizontal line through center)
        float centerX = viewWidth / 2f;
        float centerY = viewHeight / 2f;
        canvas.drawLine(0, centerY, viewWidth, centerY, paintAxis);

        // Draw Z axis label
        paintLabelText.setAlpha(150);
        paintLabelText.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Z", viewWidth - 20, centerY - 15, paintLabelText);
        paintLabelText.setAlpha(255);

        // If no measurements, show instruction
        if (Double.isNaN(relX) || Double.isNaN(relZ)) {
            paintLabelText.setTextAlign(Paint.Align.CENTER);
            paintLabelText.setAlpha(150);
            canvas.drawText("Установите начальную точку", centerX, centerY - 20, paintLabelText);
            paintLabelText.setAlpha(255);
            return;
        }

        // Calculate triangle vertices
        // S = Start point (on Z axis)
        // E = End point
        // P = Point on Z axis at same X as E (forms right angle)

        // For centering: midpoint of SE is at center of screen
        // S_screen = center - half_vector
        // E_screen = center + half_vector

        float halfZ = (float)(relZ * scale / 2);
        float halfX = (float)(relX * scale / 2);

        // S is on Z axis (horizontal line), so its Y = centerY
        // E is at (centerX + halfZ*2, centerY + halfX*2) -- wait, that's wrong

        // Let me recalculate:
        // The vector from S to E is (relZ, relX) in screen coords (Z is horizontal, X is vertical)
        // For centering, midpoint of SE should be at (centerX, centerY)
        // So S = (centerX - relZ*scale/2, centerY - relX*scale/2)
        // And E = (centerX + relZ*scale/2, centerY + relX*scale/2)

        // But we want S to be ON the Z axis (Y = centerY)
        // So we need to adjust...

        // Actually, let's think differently:
        // S should be on Z axis (horizontal line through center)
        // So S has Y = centerY
        // E is at (S_x + relZ*scale, S_y + relX*scale) = (S_x + relZ*scale, centerY + relX*scale)
        // For centering, we want midpoint at center:
        // midpoint = ((S_x + E_x)/2, (centerY + E_y)/2) = (centerX, centerY)
        // (S_x + S_x + relZ*scale)/2 = centerX => S_x = centerX - relZ*scale/2
        // (centerY + centerY + relX*scale)/2 = centerY => this is automatically satisfied

        float sX = centerX - halfZ;  // S screen X
        float sY = centerY;          // S screen Y (on Z axis)
        float eX = centerX + halfZ;  // E screen X
        float eY = centerY + halfX;  // E screen Y
        float pX = eX;               // P screen X (same as E)
        float pY = sY;               // P screen Y (same as S, on Z axis)

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

        // Draw vertices
        canvas.drawCircle(sX, sY, 10, paintVertex);  // S
        canvas.drawCircle(eX, eY, 10, paintVertex);  // E
        canvas.drawCircle(pX, pY, 8, paintVertex);   // P (smaller, right angle)

        // Labels for vertices
        paintLabelText.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("S", sX, sY - 25, paintLabelText);
        canvas.drawText("E", eX, eY - 25, paintLabelText);
        canvas.drawText("P", pX + (relZ >= 0 ? 25 : -25), pY + 25, paintLabelText);

        // Draw dimension labels on sides
        drawDimensionLabel(canvas, sX, sY, pX, pY, String.format("%.2f", Math.abs(relZ)), true);
        drawDimensionLabel(canvas, pX, pY, eX, eY, String.format("%.2f", Math.abs(relX)), false);

        // Hypotenuse length
        double hypLen = Math.sqrt(relX * relX + relZ * relZ);
        drawHypotenuseLabel(canvas, sX, sY, eX, eY, String.format("%.2f", hypLen));

        // Draw angle display box
        drawAngleBox(canvas, centerX, centerY);
    }

    private void drawDimensionLabel(Canvas canvas, float x1, float y1, float x2, float y2, String text, boolean isHorizontal) {
        float midX = (x1 + x2) / 2;
        float midY = (y1 + y2) / 2;

        // Offset perpendicular to the line
        float offsetX = 0, offsetY = 0;
        if (isHorizontal) {
            offsetY = 35; // Below horizontal line
        } else {
            offsetX = relZ >= 0 ? 45 : -45; // Right or left of vertical line
        }

        // Draw background
        float textWidth = paintLabelText.measureText(text) + 20;
        float left = midX + offsetX - textWidth / 2;
        float top = midY + offsetY - 18;
        float right = midX + offsetX + textWidth / 2;
        float bottom = midY + offsetY + 18;

        drawRoundedRect(canvas, left, top, right, bottom, 6, paintLabelBg);

        // Draw text
        paintLabelText.setColor(Color.parseColor("#808080")); // Gray for legs
        canvas.drawText(text, midX + offsetX, midY + offsetY + 7, paintLabelText);
        paintLabelText.setColor(ContextCompat.getColor(getContext(), R.color.text_bright));
    }

    private void drawHypotenuseLabel(Canvas canvas, float x1, float y1, float x2, float y2, String text) {
        float midX = (x1 + x2) / 2;
        float midY = (y1 + y2) / 2;

        // Offset perpendicular to hypotenuse (toward center)
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) len = 1;

        // Perpendicular direction
        float perpX = -dy / len;
        float perpY = dx / len;

        // Offset toward "inside" of triangle (toward P)
        // P is at (eX, sY), which is at one corner
        float pX = x2; // = eX
        float pY = y1; // = sY

        // Vector from midpoint to P
        float toPx = pX - midX;
        float toPy = pY - midY;
        float toPlen = (float) Math.sqrt(toPx * toPx + toPy * toPy);
        if (toPlen < 1) toPlen = 1;
        toPx /= toPlen;
        toPy /= toPlen;

        // Use this direction for offset
        float offsetX = toPx * 40;
        float offsetY = toPy * 40;

        float labelX = midX + offsetX;
        float labelY = midY + offsetY;

        // Draw background
        float textWidth = paintLabelText.measureText(text) + 20;
        float left = labelX - textWidth / 2;
        float top = labelY - 18;
        float right = labelX + textWidth / 2;
        float bottom = labelY + 18;

        drawRoundedRect(canvas, left, top, right, bottom, 6, paintLabelBg);

        // Draw text in yellow
        paintLabelText.setColor(Color.parseColor("#FFD700")); // Yellow for hypotenuse
        canvas.drawText(text, labelX, labelY + 7, paintLabelText);
        paintLabelText.setColor(ContextCompat.getColor(getContext(), R.color.text_bright));
    }

    private void drawAngleBox(Canvas canvas, float centerX, float centerY) {
        if (Double.isNaN(angle)) return;

        // Position angle box at top right
        float boxX = viewWidth - 120;
        float boxY = 80;

        // Background
        float boxWidth = 200;
        float boxHeight = 130;
        float left = boxX - boxWidth / 2;
        float top = boxY - boxHeight / 2;
        float right = boxX + boxWidth / 2;
        float bottom = boxY + boxHeight / 2;

        Paint paintBoxBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBoxBg.setColor(ContextCompat.getColor(getContext(), R.color.bg_dark));
        paintBoxBg.setAlpha(230);
        paintBoxBg.setStyle(Paint.Style.FILL);

        Paint paintBoxBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBoxBorder.setColor(ContextCompat.getColor(getContext(), R.color.button_border));
        paintBoxBorder.setStyle(Paint.Style.STROKE);
        paintBoxBorder.setStrokeWidth(2);

        drawRoundedRect(canvas, left, top, right, bottom, 12, paintBoxBg);
        drawRoundedRect(canvas, left, top, right, bottom, 12, paintBoxBorder);

        // Title
        paintLabelText.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Угол", boxX, boxY - 30, paintLabelText);

        // Angle value
        String angleText = String.format("%.2f°", angle);
        canvas.drawText(angleText, boxX, boxY + 20, paintAngleText);

        // Hypotenuse length
        if (!Double.isNaN(relX) && !Double.isNaN(relZ)) {
            double hypLen = Math.sqrt(relX * relX + relZ * relZ);
            String hypText = String.format("Гип.: %.2f мм", hypLen);
            paintLabelText.setAlpha(200);
            canvas.drawText(hypText, boxX, boxY + 55, paintLabelText);
            paintLabelText.setAlpha(255);
        }
    }

    private void drawRoundedRect(Canvas canvas, float left, float top, float right, float bottom, float radius, Paint paint) {
        if (Build.VERSION.SDK_INT >= 21) {
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
        } else {
            android.graphics.RectF rect = new android.graphics.RectF(left, top, right, bottom);
            Path path = new Path();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
    }
}
