package com.dro.lathe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Измерение угла между двумя точками с визуализацией
 */
public class AngleActivity extends AppCompatActivity {

    private TextView tvStartX, tvStartZ, tvEndX, tvEndZ, tvAngle, tvDistance;
    private TextView tvCurrentX, tvCurrentZ;
    private AngleView angleView;

    private double startX = Double.NaN, startZ = Double.NaN;
    private double endX = Double.NaN, endZ = Double.NaN;

    private SharedPreferences prefs;

    // Handler for position updates
    private Handler handler;
    private Runnable positionUpdater;
    private static final int UPDATE_INTERVAL_MS = 100; // Update every 100ms

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_angle);

        prefs = getSharedPreferences("DRO_PREFS", MODE_PRIVATE);

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
        tvAngle = findViewById(R.id.tv_angle);
        tvDistance = findViewById(R.id.tv_distance);
        angleView = findViewById(R.id.angle_view);
    }

    private void setupListeners() {
        findViewById(R.id.btn_set_start).setOnClickListener(v -> setStartPoint());
        findViewById(R.id.btn_set_end).setOnClickListener(v -> setEndPoint());
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
        // Start position updates
        handler.post(positionUpdater);
        // Register for broadcast updates from MainActivity
        IntentFilter filter = new IntentFilter("com.dro.lathe.POSITION_UPDATE");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(positionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(positionReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop position updates
        handler.removeCallbacks(positionUpdater);
        // Unregister receiver
        try {
            unregisterReceiver(positionReceiver);
        } catch (Exception e) {
            // Ignore if not registered
        }
    }

    private final BroadcastReceiver positionReceiver = new BroadcastReceiver() {
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

        // Update angle view with current position
        if (angleView != null) {
            angleView.setCurrentPosition(x, z);
        }
    }

    private void setStartPoint() {
        startX = prefs.getFloat("current_x", 0);
        startZ = prefs.getFloat("current_z", 0);

        tvStartX.setText(String.format(Locale.US, "%.3f", startX));
        tvStartZ.setText(String.format(Locale.US, "%.3f", startZ));

        // Update angle view
        angleView.setStartPoint(startX, startZ);

        // Reset end point
        endX = endZ = Double.NaN;
        tvEndX.setText("—");
        tvEndZ.setText("—");
        tvAngle.setText("—°");
        tvDistance.setText("—");
        angleView.setEndPoint(Double.NaN, Double.NaN);
    }

    private void setEndPoint() {
        if (Double.isNaN(startX)) {
            // No start point set - can't set end point
            return;
        }

        endX = prefs.getFloat("current_x", 0);
        endZ = prefs.getFloat("current_z", 0);

        tvEndX.setText(String.format(Locale.US, "%.3f", endX));
        tvEndZ.setText(String.format(Locale.US, "%.3f", endZ));

        // Update angle view
        angleView.setEndPoint(endX, endZ);

        calculateAngle();
    }

    private void calculateAngle() {
        if (Double.isNaN(startX) || Double.isNaN(startZ) ||
                Double.isNaN(endX) || Double.isNaN(endZ)) {
            return;
        }

        double deltaX = endX - startX;
        double deltaZ = endZ - startZ;

        // Distance between points
        double dist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        // Angle from Z-axis (always positive)
        double angle;
        if (deltaZ == 0 && deltaX == 0) {
            angle = 0;
        } else {
            // atan2 gives angle from positive Z axis
            angle = Math.abs(Math.toDegrees(Math.atan2(deltaX, deltaZ)));
        }

        tvAngle.setText(String.format(Locale.US, "%.2f°", angle));
        tvDistance.setText(String.format(Locale.US, "%.2f мм", dist));

        // Update angle view
        angleView.setAngle(angle);
        angleView.setDistance(dist);
    }

    private void reset() {
        startX = startZ = endX = endZ = Double.NaN;
        tvStartX.setText("—");
        tvStartZ.setText("—");
        tvEndX.setText("—");
        tvEndZ.setText("—");
        tvAngle.setText("—°");
        tvDistance.setText("—");
        angleView.reset();
    }
}
