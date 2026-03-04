package com.dro.lathe;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

public class SettingsActivity extends AppCompatActivity implements BluetoothService.Callback {

    private SharedPreferences prefs;

    private Spinner spinnerResolutionX, spinnerResolutionZ;
    private Switch switchSound;
    private SeekBar seekProximity;
    private TextView tvProximity, tvDevice;
    private Button btnBack, btnSelectDevice, btnDisconnect;

    private BluetoothService bluetoothService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("DRO_PREFS", MODE_PRIVATE);
        bluetoothService = BluetoothService.getInstance();
        if (bluetoothService == null) {
            bluetoothService = new BluetoothService(this);
        }
        bluetoothService.setCallback(this);

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
        btnBack = findViewById(R.id.btn_back);
        btnSelectDevice = findViewById(R.id.btn_select_device);
        btnDisconnect = findViewById(R.id.btn_disconnect);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Select device button
        btnSelectDevice.setOnClickListener(v -> showDeviceDialog());

        // Disconnect button
        btnDisconnect.setOnClickListener(v -> {
            if (bluetoothService != null && bluetoothService.isConnected()) {
                bluetoothService.disconnect();
            }
        });

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
        if (bluetoothService == null) {
            Toast.makeText(this, "Bluetooth недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> devices = bluetoothService.getPairedDevices();
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

                    // Подключаемся
                    bluetoothService.connect(device);
                    updateDeviceDisplay(deviceName, deviceAddr, false);
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
        updateDeviceDisplay();
    }

    private void updateDeviceDisplay() {
        String deviceAddr = prefs.getString("last_device", "");
        String deviceName = prefs.getString("last_device_name", "");
        boolean connected = bluetoothService != null && bluetoothService.isConnected();
        updateDeviceDisplay(deviceName, deviceAddr, connected);
    }

    private void updateDeviceDisplay(String name, String address, boolean connected) {
        if (!address.isEmpty()) {
            String displayText = (name.isEmpty() ? "DRO" : name) + "\n" + address;
            tvDevice.setText(displayText);
            tvDevice.setTextColor(connected ? 0xFF00FF00 : 0xFF808080);
            btnDisconnect.setVisibility(connected ? View.VISIBLE : View.GONE);
        } else {
            tvDevice.setText("Не выбрано");
            tvDevice.setTextColor(0xFF808080);
            btnDisconnect.setVisibility(View.GONE);
        }
    }

    private int getResolutionIndex(float value) {
        if (value <= 0.001) return 0;
        if (value <= 0.005) return 1;
        if (value <= 0.01) return 2;
        return 3; // 0.5
    }

    @Override
    public void onConnected() {
        runOnUiThread(this::updateDeviceDisplay);
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(this::updateDeviceDisplay);
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDataReceived(double x, double z) {
        // Not needed in settings
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDeviceDisplay();
    }
}
