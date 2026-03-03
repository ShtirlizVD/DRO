package com.dro.lathe;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class AngleActivity extends AppCompatActivity {

    private TextView tvStartX, tvStartZ, tvEndX, tvEndZ, tvAngle, tvDistance;
    private TextView tvCurrentX, tvCurrentZ;

    private double startX = Double.NaN, startZ = Double.NaN;
    private double endX = Double.NaN, endZ = Double.NaN;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_angle);

        prefs = getSharedPreferences("DRO_PREFS", MODE_PRIVATE);

        tvCurrentX = findViewById(R.id.tv_current_x);
        tvCurrentZ = findViewById(R.id.tv_current_z);
        tvStartX = findViewById(R.id.tv_start_x);
        tvStartZ = findViewById(R.id.tv_start_z);
        tvEndX = findViewById(R.id.tv_end_x);
        tvEndZ = findViewById(R.id.tv_end_z);
        tvAngle = findViewById(R.id.tv_angle);
        tvDistance = findViewById(R.id.tv_distance);

        findViewById(R.id.btn_set_start).setOnClickListener(v -> setStartPoint());
        findViewById(R.id.btn_set_end).setOnClickListener(v -> setEndPoint());
        findViewById(R.id.btn_reset).setOnClickListener(v -> reset());
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCurrentPosition();
    }

    private void updateCurrentPosition() {
        double x = prefs.getFloat("current_x", 0);
        double z = prefs.getFloat("current_z", 0);
        tvCurrentX.setText(String.format(Locale.US, "%.2f", x));
        tvCurrentZ.setText(String.format(Locale.US, "%.2f", z));
    }

    private void setStartPoint() {
        startX = prefs.getFloat("current_x", 0);
        startZ = prefs.getFloat("current_z", 0);

        tvStartX.setText(String.format(Locale.US, "%.3f", startX));
        tvStartZ.setText(String.format(Locale.US, "%.3f", startZ));

        // Reset end point
        endX = endZ = Double.NaN;
        tvEndX.setText("—");
        tvEndZ.setText("—");
        tvAngle.setText("—°");
        tvDistance.setText("—");
    }

    private void setEndPoint() {
        endX = prefs.getFloat("current_x", 0);
        endZ = prefs.getFloat("current_z", 0);

        tvEndX.setText(String.format(Locale.US, "%.3f", endX));
        tvEndZ.setText(String.format(Locale.US, "%.3f", endZ));

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

        // Angle from Z-axis (spindle axis)
        // tan(angle) = deltaX / deltaZ
        if (deltaZ == 0) {
            tvAngle.setText("90.00°");
        } else {
            double angle = Math.toDegrees(Math.atan2(deltaX, deltaZ));
            tvAngle.setText(String.format(Locale.US, "%.2f°", Math.abs(angle)));
        }

        tvDistance.setText(String.format(Locale.US, "%.2f мм", dist));
    }

    private void reset() {
        startX = startZ = endX = endZ = Double.NaN;
        tvStartX.setText("—");
        tvStartZ.setText("—");
        tvEndX.setText("—");
        tvEndZ.setText("—");
        tvAngle.setText("—°");
        tvDistance.setText("—");
    }
}
