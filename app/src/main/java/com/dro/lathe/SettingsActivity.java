package com.dro.lathe;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    private Spinner spinnerResolutionX, spinnerResolutionZ;
    private Switch switchSound;
    private SeekBar seekProximity;
    private TextView tvProximity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("DRO_PREFS", MODE_PRIVATE);

        initViews();
        loadSettings();
    }

    private void initViews() {
        spinnerResolutionX = findViewById(R.id.spinner_resolution_x);
        spinnerResolutionZ = findViewById(R.id.spinner_resolution_z);
        switchSound = findViewById(R.id.switch_sound);
        seekProximity = findViewById(R.id.seek_proximity);
        tvProximity = findViewById(R.id.tv_proximity);

        // Resolution spinners
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.resolutions, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerResolutionX.setAdapter(adapter);
        spinnerResolutionZ.setAdapter(adapter);

        spinnerResolutionX.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                double[] values = {0.001, 0.005, 0.01};
                prefs.edit().putFloat("resolution_x", (float) values[position]).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerResolutionZ.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                double[] values = {0.001, 0.005, 0.01};
                prefs.edit().putFloat("resolution_z", (float) values[position]).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Sound
        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("sound", isChecked).apply();
        });

        // Proximity
        seekProximity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvProximity.setText(progress + " мм");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putFloat("proximity_distance", seekBar.getProgress()).apply();
            }
        });
    }

    private void loadSettings() {
        // Resolution X
        float resX = prefs.getFloat("resolution_x", 0.005f);
        int idxX = resX <= 0.001 ? 0 : (resX <= 0.005 ? 1 : 2);
        spinnerResolutionX.setSelection(idxX);

        // Resolution Z
        float resZ = prefs.getFloat("resolution_z", 0.005f);
        int idxZ = resZ <= 0.001 ? 0 : (resZ <= 0.005 ? 1 : 2);
        spinnerResolutionZ.setSelection(idxZ);

        // Sound
        switchSound.setChecked(prefs.getBoolean("sound", true));

        // Proximity
        int proximity = (int) prefs.getFloat("proximity_distance", 5.0f);
        seekProximity.setProgress(proximity);
        tvProximity.setText(proximity + " мм");
    }
}
