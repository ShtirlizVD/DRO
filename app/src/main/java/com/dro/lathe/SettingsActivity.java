package com.dro.lathe;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    private Spinner spinnerResolutionX, spinnerResolutionZ;
    private Switch switchSound;
    private SeekBar seekProximity;
    private TextView tvProximity, tvDevice;
    private Button btnSelectDevice;

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
        tvDevice = findViewById(R.id.tv_device);
        btnSelectDevice = findViewById(R.id.btn_select_device);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Select device button
        btnSelectDevice.setOnClickListener(v -> showDeviceDialog());

        // Resolution spinners
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.resolutions, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerResolutionX.setAdapter(adapter);
        spinnerResolutionZ.setAdapter(adapter);

        spinnerResolutionX.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                double[] values = {0.001, 0.005, 0.01, 0.5};
                prefs.edit().putFloat("resolution_x", (float) values[position]).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerResolutionZ.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                double[] values = {0.001, 0.005, 0.01, 0.5};
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

    private void showDeviceDialog() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth не поддерживается", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth выключен", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
        if (devices == null || devices.isEmpty()) {
            Toast.makeText(this, R.string.no_paired_devices, Toast.LENGTH_SHORT).show();
            return;
        }

        final BluetoothDevice[] deviceArray = devices.toArray(new BluetoothDevice[0]);
        String[] names = new String[deviceArray.length];
        for (int i = 0; i < deviceArray.length; i++) {
            names[i] = deviceArray[i].getName() + "\n" + deviceArray[i].getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_device)
                .setItems(names, (dialog, which) -> {
                    BluetoothDevice device = deviceArray[which];
                    String deviceName = device.getName();
                    String deviceAddr = device.getAddress();

                    // Сохраняем устройство
                    prefs.edit()
                            .putString("last_device", deviceAddr)
                            .putString("last_device_name", deviceName)
                            .apply();

                    updateDeviceDisplay(deviceName, deviceAddr);
                    Toast.makeText(this, "Устройство сохранено", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void loadSettings() {
        // Resolution X
        float resX = prefs.getFloat("resolution_x", 0.005f);
        int idxX = getResolutionIndex(resX);
        spinnerResolutionX.setSelection(idxX);

        // Resolution Z
        float resZ = prefs.getFloat("resolution_z", 0.005f);
        int idxZ = getResolutionIndex(resZ);
        spinnerResolutionZ.setSelection(idxZ);

        // Sound
        switchSound.setChecked(prefs.getBoolean("sound", true));

        // Proximity
        int proximity = (int) prefs.getFloat("proximity_distance", 5.0f);
        seekProximity.setProgress(proximity);
        tvProximity.setText(proximity + " мм");

        // Device info
        String deviceAddr = prefs.getString("last_device", "");
        String deviceName = prefs.getString("last_device_name", "");
        updateDeviceDisplay(deviceName, deviceAddr);
    }

    private void updateDeviceDisplay(String name, String address) {
        if (!address.isEmpty()) {
            tvDevice.setText((name.isEmpty() ? "DRO" : name) + "\n" + address);
            tvDevice.setTextColor(0xFF00FF00);
        } else {
            tvDevice.setText("Не выбрано");
            tvDevice.setTextColor(0xFF808080);
        }
    }

    private int getResolutionIndex(float value) {
        if (value <= 0.001) return 0;
        if (value <= 0.005) return 1;
        if (value <= 0.01) return 2;
        return 3; // 0.5
    }
}
