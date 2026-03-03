package com.dro.lathe;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import yuku.ambilwarna.AmbilWarnaDialog;

public class MainActivity extends AppCompatActivity implements BluetoothService.Callback {

    private static final int REQUEST_PERMISSIONS = 1;

    // Views - StrokeTextView для координат с контуром
    private StrokeTextView valueX, valueD, valueZ, valueL;
    private TextView labelX, labelD, labelZ, labelL;
    private TextView modeX;
    private TextView tvStatus, tvTool;
    private Button btnConnect;

    // Data
    private DROData droData;
    private Tool[] tools = new Tool[4];
    private int currentTool = 0;
    private List<Marker> markers = new ArrayList<>();

    // Connection
    private BluetoothService bluetoothService;

    // Demo mode
    private Handler demoHandler = new Handler();
    private double demoX = 50, demoZ = 200, dirX = 1, dirZ = 1;
    private boolean demoMode = true;

    // Settings
    private SharedPreferences prefs;
    private boolean soundEnabled = true;
    private double proximityDistance = 5.0;
    private int[] coordColors = new int[4];
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initData();
        loadSettings();
        setupColorPickers();
        checkPermissions();
        startDemo();
        
        // Установить подсветку первого инструмента при старте
        selectTool(0);
    }

    private void initViews() {
        valueX = findViewById(R.id.value_x);
        valueD = findViewById(R.id.value_d);
        valueZ = findViewById(R.id.value_z);
        valueL = findViewById(R.id.value_l);
        labelX = findViewById(R.id.label_x);
        labelD = findViewById(R.id.label_d);
        labelZ = findViewById(R.id.label_z);
        labelL = findViewById(R.id.label_l);
        modeX = findViewById(R.id.mode_x);
        tvStatus = findViewById(R.id.tv_status);
        tvTool = findViewById(R.id.tv_tool);
        btnConnect = findViewById(R.id.btn_connect);

        // Button listeners
        btnConnect.setOnClickListener(v -> onConnectClick());
        findViewById(R.id.btn_zero_x).setOnClickListener(v -> onZeroX());
        findViewById(R.id.btn_zero_z).setOnClickListener(v -> onZeroZ());
        findViewById(R.id.btn_set_d).setOnClickListener(v -> onSetDiameter());
        findViewById(R.id.btn_set_l).setOnClickListener(v -> onSetLength());

        // Tool buttons
        int[] toolIds = {R.id.btn_tool_1, R.id.btn_tool_2, R.id.btn_tool_3, R.id.btn_tool_4};
        for (int i = 0; i < 4; i++) {
            final int toolIndex = i;
            findViewById(toolIds[i]).setOnClickListener(v -> selectTool(toolIndex));
            findViewById(toolIds[i]).setOnLongClickListener(v -> {
                editToolOffset(toolIndex);
                return true;
            });
        }

        findViewById(R.id.btn_markers).setOnClickListener(v ->
                startActivity(new Intent(this, MarkerListActivity.class)));

        findViewById(R.id.btn_thread).setOnClickListener(v ->
                startActivity(new Intent(this, ThreadHelperActivity.class)));

        findViewById(R.id.btn_angle).setOnClickListener(v ->
                startActivity(new Intent(this, AngleActivity.class)));

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Double tap on X to toggle mode
        setupDoubleTap(valueX, () -> toggleMode());

        applyColors();
    }

    private void setupDoubleTap(View view, Runnable action) {
        final long[] lastClick = {0};
        view.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastClick[0] < 300) {
                action.run();
            }
            lastClick[0] = now;
        });
    }

    private void initData() {
        droData = new DROData();
        bluetoothService = new BluetoothService(this);
        prefs = getSharedPreferences("DRO_PREFS", MODE_PRIVATE);

        for (int i = 0; i < 4; i++) {
            tools[i] = new Tool(i + 1);
            tools[i].setOffsetX(prefs.getFloat("tool_" + i + "_x", 0));
            tools[i].setOffsetD(prefs.getFloat("tool_" + i + "_d", 0));
            tools[i].setOffsetZ(prefs.getFloat("tool_" + i + "_z", 0));
            tools[i].setOffsetL(prefs.getFloat("tool_" + i + "_l", 0));
        }

        coordColors[0] = ContextCompat.getColor(this, R.color.coord_x);
        coordColors[1] = ContextCompat.getColor(this, R.color.coord_d);
        coordColors[2] = ContextCompat.getColor(this, R.color.coord_z);
        coordColors[3] = ContextCompat.getColor(this, R.color.coord_l);

        toneGenerator = new ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void loadSettings() {
        droData.setResolutionX(prefs.getFloat("resolution_x", 0.005f));
        droData.setResolutionZ(prefs.getFloat("resolution_z", 0.005f));
        droData.setDiameterMode(prefs.getBoolean("diameter_mode", true));

        soundEnabled = prefs.getBoolean("sound", true);
        proximityDistance = prefs.getFloat("proximity_distance", 5.0f);

        coordColors[0] = prefs.getInt("color_x", coordColors[0]);
        coordColors[1] = prefs.getInt("color_d", coordColors[1]);
        coordColors[2] = prefs.getInt("color_z", coordColors[2]);
        coordColors[3] = prefs.getInt("color_l", coordColors[3]);

        applyColors();
        updateModeLabel();
    }

    private void applyColors() {
        valueX.setTextColor(coordColors[0]);
        labelX.setTextColor(coordColors[0]);
        modeX.setTextColor(coordColors[0]);

        valueD.setTextColor(coordColors[1]);
        labelD.setTextColor(coordColors[1]);

        valueZ.setTextColor(coordColors[2]);
        labelZ.setTextColor(coordColors[2]);

        valueL.setTextColor(coordColors[3]);
        labelL.setTextColor(coordColors[3]);
    }

    private void setupColorPickers() {
        setupLongPressColor(valueX, 0, "color_x");
        setupLongPressColor(valueD, 1, "color_d");
        setupLongPressColor(valueZ, 2, "color_z");
        setupLongPressColor(valueL, 3, "color_l");
    }

    private void setupLongPressColor(View view, int index, String prefKey) {
        view.setOnLongClickListener(v -> {
            showColorPicker(index, prefKey);
            return true;
        });
    }

    // AmbilWarnaDialog для выбора цвета
    private void showColorPicker(int index, String prefKey) {
        int currentColor = coordColors[index];
        
        AmbilWarnaDialog dialog = new AmbilWarnaDialog(this, currentColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                coordColors[index] = color;
                prefs.edit().putInt(prefKey, color).apply();
                applyColors();
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
                // Отмена
            }
        });
        dialog.show();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };

            List<String> needed = new ArrayList<>();
            for (String p : permissions) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(p);
                }
            }

            if (!needed.isEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_PERMISSIONS);
            }
        }
    }

    private void startDemo() {
        demoHandler.postDelayed(demoRunnable, 200);
    }

    private Runnable demoRunnable = new Runnable() {
        @Override
        public void run() {
            if (demoMode) {
                updateDemo();
            }
            demoHandler.postDelayed(this, 200);
        }
    };

    private void updateDemo() {
        if (Math.random() < 0.03) dirX *= -1;
        if (Math.random() < 0.03) dirZ *= -1;

        demoX += dirX * (0.03 + Math.random() * 0.07);
        demoZ += dirZ * (0.08 + Math.random() * 0.15);

        demoX = Math.max(0, Math.min(85, demoX));
        demoZ = Math.max(0, Math.min(800, demoZ));

        if (demoX <= 0 || demoX >= 85) dirX *= -1;
        if (demoZ <= 0 || demoZ >= 800) dirZ *= -1;

        onDataReceived(demoX, demoZ);
    }

    private void onConnectClick() {
        if (bluetoothService.isConnected()) {
            bluetoothService.disconnect();
            demoMode = true;
            return;
        }

        showBluetoothDialog();
    }

    private void showBluetoothDialog() {
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
                    bluetoothService.connect(deviceArray[which]);
                })
                .show();
    }

    private void selectTool(int index) {
        if (currentTool >= 0 && currentTool < 4) {
            tools[currentTool].setOffsetX(droData.getOffsetX());
            tools[currentTool].setOffsetD(droData.getOffsetD());
            tools[currentTool].setOffsetZ(droData.getOffsetZ());
            tools[currentTool].setOffsetL(droData.getOffsetL());
            saveToolOffset(currentTool);
        }

        currentTool = index;

        // Подсветка выбранного инструмента через setSelected()
        int[] toolIds = {R.id.btn_tool_1, R.id.btn_tool_2, R.id.btn_tool_3, R.id.btn_tool_4};
        for (int i = 0; i < 4; i++) {
            Button btn = findViewById(toolIds[i]);
            btn.setSelected(i == index);
        }

        droData.setOffsetX(tools[index].getOffsetX());
        droData.setOffsetD(tools[index].getOffsetD());
        droData.setOffsetZ(tools[index].getOffsetZ());
        droData.setOffsetL(tools[index].getOffsetL());

        tvTool.setText("Инстр. " + (index + 1));
        updateDisplay();
    }

    private void editToolOffset(int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_tool_offset, null);
        EditText etX = view.findViewById(R.id.et_offset_x);
        EditText etD = view.findViewById(R.id.et_offset_d);
        EditText etZ = view.findViewById(R.id.et_offset_z);
        EditText etL = view.findViewById(R.id.et_offset_l);

        etX.setText(String.format(Locale.US, "%.3f", tools[index].getOffsetX()));
        etD.setText(String.format(Locale.US, "%.3f", tools[index].getOffsetD()));
        etZ.setText(String.format(Locale.US, "%.3f", tools[index].getOffsetZ()));
        etL.setText(String.format(Locale.US, "%.3f", tools[index].getOffsetL()));

        new AlertDialog.Builder(this)
                .setTitle("Смещения инструмента " + (index + 1))
                .setView(view)
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        tools[index].setOffsetX(Double.parseDouble(etX.getText().toString()));
                        tools[index].setOffsetD(Double.parseDouble(etD.getText().toString()));
                        tools[index].setOffsetZ(Double.parseDouble(etZ.getText().toString()));
                        tools[index].setOffsetL(Double.parseDouble(etL.getText().toString()));
                        saveToolOffset(index);
                        if (currentTool == index) {
                            selectTool(index);
                        }
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveToolOffset(int index) {
        prefs.edit()
                .putFloat("tool_" + index + "_x", (float) tools[index].getOffsetX())
                .putFloat("tool_" + index + "_d", (float) tools[index].getOffsetD())
                .putFloat("tool_" + index + "_z", (float) tools[index].getOffsetZ())
                .putFloat("tool_" + index + "_l", (float) tools[index].getOffsetL())
                .apply();
    }

    private void onZeroX() {
        droData.zeroX();
        tools[currentTool].setOffsetX(droData.getOffsetX());
        saveToolOffset(currentTool);
        updateDisplay();
    }

    private void onZeroZ() {
        droData.zeroZ();
        tools[currentTool].setOffsetZ(droData.getOffsetZ());
        saveToolOffset(currentTool);
        updateDisplay();
    }

    private void onSetDiameter() {
        showInputDialog("Диаметр", droData.getD(), value -> {
            droData.setDiameter(value);
            tools[currentTool].setOffsetD(droData.getOffsetD());
            saveToolOffset(currentTool);
            updateDisplay();
        });
    }

    private void onSetLength() {
        showInputDialog("Длина", droData.getL(), value -> {
            droData.setLength(value);
            tools[currentTool].setOffsetL(droData.getOffsetL());
            saveToolOffset(currentTool);
            updateDisplay();
        });
    }

    private void toggleMode() {
        droData.toggleMode();
        prefs.edit().putBoolean("diameter_mode", droData.isDiameterMode()).apply();
        updateModeLabel();
        updateDisplay();
    }

    private void updateModeLabel() {
        if (droData.isDiameterMode()) {
            modeX.setText("");
        } else {
            modeX.setText("÷2");
        }
    }

    // Диалог ввода с кнопкой 0
    private void showInputDialog(String title, double currentValue, InputCallback callback) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_input_value);
        
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        TextView tvTitle = dialog.findViewById(R.id.dialog_title);
        TextView tvCurrentValue = dialog.findViewById(R.id.tv_current_value);
        EditText etValue = dialog.findViewById(R.id.et_value);
        Button btnOk = dialog.findViewById(R.id.btn_ok);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnZero = dialog.findViewById(R.id.btn_preset_0);
        
        tvTitle.setText(title);
        tvCurrentValue.setText("Было: " + String.format(Locale.US, "%.3f", currentValue));
        etValue.setText("");
        etValue.setHint("0.000");
        
        // Кнопка 0 - обнуляет поле
        btnZero.setOnClickListener(v -> {
            etValue.setText("0");
            etValue.selectAll();
        });

        btnOk.setOnClickListener(v -> {
            String text = etValue.getText().toString().trim();
            if (!text.isEmpty()) {
                try {
                    double value = Double.parseDouble(text);
                    callback.onValue(value);
                } catch (NumberFormatException ignored) {}
            }
            dialog.dismiss();
        });
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        
        etValue.requestFocus();
    }

    private interface InputCallback {
        void onValue(double value);
    }

    private void updateDisplay() {
        valueX.setText(formatValue(droData.getX()));
        valueD.setText(formatValue(droData.getD()));
        valueZ.setText(formatValue(droData.getZ()));
        valueL.setText(formatValue(droData.getL()));

        prefs.edit()
                .putFloat("current_x", (float) droData.getX())
                .putFloat("current_z", (float) droData.getZ())
                .apply();
    }

    private String formatValue(double value) {
        return String.format(Locale.US, "%7.3f", value);
    }

    private void checkMarkers() {
        if (!soundEnabled || markers.isEmpty()) return;

        double x = droData.getD();
        double z = droData.getZ();

        for (Marker m : markers) {
            double currentPos = m.getAxis() == Marker.Axis.X ? x : z;
            if (Math.abs(currentPos - m.getPosition()) < proximityDistance) {
                playAlert();
                break;
            }
        }
    }

    private void playAlert() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(50);
        }
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            demoMode = false;
            tvStatus.setText("BT");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            btnConnect.setText(R.string.disconnect);
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            demoMode = true;
            tvStatus.setText("Демо");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            btnConnect.setText(R.string.connect);
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDataReceived(double x, double z) {
        runOnUiThread(() -> {
            droData.setRawX(x);
            droData.setRawZ(z);
            updateDisplay();
            checkMarkers();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        loadMarkers();
        updateDisplay();
    }

    private void loadMarkers() {
        String json = prefs.getString("markers", "[]");
        markers = MarkerListActivity.parseMarkers(json);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        demoHandler.removeCallbacks(demoRunnable);
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}
