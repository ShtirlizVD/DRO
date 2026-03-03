package com.dro.lathe;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * TextView с контуром (stroke) вокруг текста
 * Цифры выглядят более объёмно и читаемо на планшете
 */
public class StrokeTextView extends AppCompatTextView {

    private float strokeWidth;
    private int strokeColor;
    private static Typeface courierBold;

    public StrokeTextView(Context context) {
        super(context);
        init(context);
    }

    public StrokeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StrokeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        strokeWidth = 5f; // Толстый контур для планшета
        strokeColor = 0xFF303030; // Светло-серый контур
        
        // Загружаем шрифт один раз
        if (courierBold == null) {
            try {
                courierBold = Typeface.createFromAsset(context.getAssets(), "fonts/courierb.otf");
            } catch (Exception e) {
                courierBold = Typeface.MONOSPACE;
            }
        }
        setTypeface(courierBold);
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
        invalidate();
    }

    public void setStrokeColor(int color) {
        this.strokeColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = getPaint();
        
        // Рисуем контур (stroke)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        int originalColor = getCurrentTextColor();
        setTextColor(strokeColor);
        super.onDraw(canvas);
        
        // Рисуем основной текст (fill)
        paint.setStyle(Paint.Style.FILL);
        setTextColor(originalColor);
        super.onDraw(canvas);
    }
}
