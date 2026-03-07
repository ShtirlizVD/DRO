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

    private TextView tvStartX, tvStartZ, tvEndX, tvEndZ;
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
        tvEndX = findViewById(R.id.tv_end_x);
        tvEndZ = findViewById(R.id.tv_end_z);
        btnSetStart = findViewById(R.id.btn_set_start);
        angleView = findViewById(R.id.angle_view);
    }

    private void setupListeners() {
        btnSetStart.setOnClickListener(v -> toggleStartPoint());
        findViewById(R.id.btn_reset).setOnClickListener(v -> reset());
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
        tvCurrentX.setText(String.format(Locale.US, "%.2f", x));
        tvCurrentZ.setText(String.format(Locale.US, "%.2f", z));

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

        tvEndX.setText(String.format(Locale.US, "%.3f", relX));
        tvEndZ.setText(String.format(Locale.US, "%.3f", relZ));

        double angle;
        if (Math.abs(relZ) < 0.001 && Math.abs(relX) < 0.001) {
            angle = 0;
        } else {
            angle = Math.toDegrees(Math.atan2(Math.abs(relX), Math.abs(relZ)));
            angle = Math.min(angle, 90);
        }

        angleView.setMeasurements(relX, relZ, angle);
    }

    private void toggleStartPoint() {
        if (Double.isNaN(startX)) {
            startX = prefs.getFloat("current_x", 0);
            startZ = prefs.getFloat("current_z", 0);

            tvStartX.setText(String.format(Locale.US, "%.3f", startX));
            tvStartZ.setText(String.format(Locale.US, "%.3f", startZ));

            btnSetStart.setText("Начальная точка установлена");
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
        tvEndX.setText("—");
        tvEndZ.setText("—");

        btnSetStart.setText("Установить начальную точку");
        btnSetStart.setBackgroundColor(colorButtonNormal);

        angleView.reset();
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
