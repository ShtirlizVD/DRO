package com.dro.lathe;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * Измерение угла между начальной точкой и текущей позицией
 * Угол всегда острый (0-90°)
 */
public class AngleActivity extends AppCompatActivity {

    private TextView tvStartX, tvStartZ;
    private TextView tvAngleZ, tvAngleX;
    private TextView tvTaperZ, tvTaperX;
    private TextView tvCurrentX, tvCurrentZ;
    private Button btnSetStart;
    private AngleView angleView;

    private double startX = Double.NaN, startZ = Double.NaN;

    private SharedPreferences prefs;

    private Handler handler;
    private Runnable positionUpdater;
    private static final int UPDATE_INTERVAL_MS = 100;

    private int colorButtonNormal;
    private int colorButtonActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_angle);

        enableFullscreenMode();

        prefs = getSharedPreferences("DRO_PREFS", MODE_PRIVATE);

        colorButtonNormal = ContextCompat.getColor(this, R.color.button_bg);
        colorButtonActive = Color.parseColor("#2E7D32");

        initViews();
        setupListeners();
        setupPositionUpdater();
    }

    private void initViews() {
        tvCurrentX = findViewById(R.id.tv_current_x);
        tvCurrentZ = findViewById(R.id.tv_current_z);
        tvStartX = findViewById(R.id.tv_start_x);
        tvStartZ = findViewById(R.id.tv_start_z);
        tvAngleZ = findViewById(R.id.tv_angle_z);
        tvAngleX = findViewById(R.id.tv_angle_x);
        tvTaperZ = findViewById(R.id.tv_taper_z);
        tvTaperX = findViewById(R.id.tv_taper_x);
        btnSetStart = findViewById(R.id.btn_set_start);
        angleView = findViewById(R.id.angle_view);
    }

    private void setupListeners() {
        btnSetStart.setOnClickListener(v -> toggleStartPoint());
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
    }

    private void setupPositionUpdater() {
        handler = new Handler(Looper.getMainLooper());
        positionUpdater = new Runnable() {
            @Override
            public void run() {
                updateCurrentPosition();
                handler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(positionUpdater);
        IntentFilter filter = new IntentFilter("com.dro.lathe.POSITION_UPDATE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(positionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(positionReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(positionUpdater);
        try {
            unregisterReceiver(positionReceiver);
        } catch (Exception e) {
            // Ignore
        }
    }

    private final android.content.BroadcastReceiver positionReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.hasExtra("x") && intent.hasExtra("z")) {
                double x = intent.getDoubleExtra("x", 0);
                double z = intent.getDoubleExtra("z", 0);
                updatePositionDisplay(x, z);
            }
        }
    };

    private void updateCurrentPosition() {
        double x = prefs.getFloat("current_x", 0);
        double z = prefs.getFloat("current_z", 0);
        updatePositionDisplay(x, z);
    }

    private void updatePositionDisplay(double x, double z) {
        tvCurrentX.setText(String.format(Locale.US, "%.3f", x));
        tvCurrentZ.setText(String.format(Locale.US, "%.3f", z));

        if (angleView != null) {
            angleView.setCurrentPosition(x, z);
        }

        if (!Double.isNaN(startX)) {
            updateRelativePosition(x, z);
        }
    }

    private void updateRelativePosition(double currentX, double currentZ) {
        double relX = currentX - startX;
        double relZ = currentZ - startZ;

        // Calculate angles
        double angleZ;
        if (Math.abs(relZ) < 0.001 && Math.abs(relX) < 0.001) {
            angleZ = 0;
        } else {
            angleZ = Math.toDegrees(Math.atan2(Math.abs(relX), Math.abs(relZ)));
            angleZ = Math.min(angleZ, 90);
        }
        double angleX = 90 - angleZ;

        // Update left panel
        tvAngleZ.setText(String.format(Locale.US, "%.2f°", angleZ));
        tvAngleX.setText(String.format(Locale.US, "%.2f°", angleX));

        // Calculate and display taper
        // Taper = tan(angle) or 1:n format
        double tanZ = Math.tan(Math.toRadians(angleZ));
        double tanX = Math.tan(Math.toRadians(angleX));

        // Format as "K=0.xxx" or "1:n"
        tvTaperZ.setText(formatTaper(tanZ));
        tvTaperX.setText(formatTaper(tanX));

        // Update view
        angleView.setMeasurements(relX, relZ, angleZ);
    }

    private void toggleStartPoint() {
        if (Double.isNaN(startX)) {
            startX = prefs.getFloat("current_x", 0);
            startZ = prefs.getFloat("current_z", 0);

            tvStartX.setText(String.format(Locale.US, "%.3f", startX));
            tvStartZ.setText(String.format(Locale.US, "%.3f", startZ));

            btnSetStart.setText("Точка установлена");
            btnSetStart.setBackgroundColor(colorButtonActive);

            angleView.setStartPoint(startX, startZ);
            updateCurrentPosition();
        } else {
            reset();
        }
    }

    private void reset() {
        startX = startZ = Double.NaN;

        tvStartX.setText("—");
        tvStartZ.setText("—");
        tvAngleZ.setText("—°");
        tvAngleX.setText("—°");
        tvTaperZ.setText("—");
        tvTaperX.setText("—");

        btnSetStart.setText("Установить точку");
        btnSetStart.setBackgroundColor(colorButtonNormal);

        angleView.reset();
    }

    /**
     * Format taper value as "1:n"
     * Регулируемое количество знаков после запятой:
     * - n < 10: 2 знака (1:3.44)
     * - n < 100: 1 знак (1:52.1)
     * - n >= 100: без знаков (1:542)
     */
    private String formatTaper(double tan) {
        if (tan < 0.001) {
            return "—";
        } else {
            double n = 1.0 / tan;
            if (n < 10) {
                return String.format(Locale.US, "1:%.2f", n);
            } else if (n < 100) {
                return String.format(Locale.US, "1:%.1f", n);
            } else {
                return String.format(Locale.US, "1:%.0f", n);
            }
        }
    }

    private void enableFullscreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableFullscreenMode();
        }
    }
}
