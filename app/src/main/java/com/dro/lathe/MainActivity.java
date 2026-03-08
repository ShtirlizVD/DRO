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
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private ImageView imgMode;
    private Button btnConnectOverlay;
    private LinearLayout activeMarkersContainer;
    private TextView tvActiveMarkers;

    // Data
    private DROData droData;
    private final Tool[] tools = new Tool[4];
    private int currentTool = 0;
    private List<Marker> markers = new ArrayList<>();

    // Connection
    private BluetoothService bluetoothService;
    private boolean isConnected = false;
    private String connectedDeviceName = "";

    // Settings
    private SharedPreferences prefs;
    private boolean soundEnabled = true;
    private double proximityDistance = 5.0;
    private final int[] coordColors = new int[4];
    private ToneGenerator toneGenerator;
    
    // Inversion settings (X is inverted by default)
    private boolean invertX = false; // When true, ADDITIONAL inversion is applied (checkbox)
    private boolean invertZ = false; // When true, Z is inverted (checkbox)
    private static final boolean DEFAULT_INVERT_X = true; // X is always inverted by default

    // Marker alert state
    private Marker lastAlertedMarker = null;
    private long lastAlertTime = 0;
    private static final long ALERT_COOLDOWN = 3000; // 3 seconds between alerts for same marker

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Включить полноэкранный режим (скрыть статус-бар и навигационную панель)
        enableFullscreenMode();

        initViews();
        initData();
        loadSettings();
        setupColorPickers();
        checkPermissions();
        
        // Установить подсветку первого инструмента при старте
        selectTool(0);
        
        // Показать серые координаты при старте (не подключено)
        updateConnectionState();
        
        // Автоматическое подключение
        tryAutoConnect();
    }

    private void enableFullscreenMode() {
        // Полноэкранный режим для скрытия статус-бара и навигационной панели
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Android 10 и ниже
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

    private void initViews() {
        valueX = findViewById(R.id.value_x);
        valueD = findViewById(R.id.value_d);
        valueZ = findViewById(R.id.value_z);
        valueL = findViewById(R.id.value_l);
        labelX = findViewById(R.id.label_x);
        labelD = findViewById(R.id.label_d);
        labelZ = findViewById(R.id.label_z);
        labelL = findViewById(R.id.label_l);
        imgMode = findViewById(R.id.img_mode);
        btnConnectOverlay = findViewById(R.id.btn_connect_overlay);
        activeMarkersContainer = findViewById(R.id.active_markers_container);
        tvActiveMarkers = findViewById(R.id.tv_active_markers);

        // Button listeners
        btnConnectOverlay.setOnClickListener(v -> onConnectClick());
        findViewById(R.id.btn_zero_x).setOnClickListener(v -> onZeroX());
        findViewById(R.id.btn_zero_z).setOnClickListener(v -> onZeroZ());
        findViewById(R.id.btn_set_d).setOnClickListener(v -> onSetDiameter());
        findViewById(R.id.btn_set_l).setOnClickListener(v -> onSetLength());

        // Tool button - shows popup with 4 tools
        Button btnTool = findViewById(R.id.btn_tool);
        btnTool.setOnClickListener(v -> showToolPopup(v));
        btnTool.setOnLongClickListener(v -> {
            editToolOffset(currentTool);
            return true;
        });

        // Markers button - opens MarkerListActivity
        findViewById(R.id.btn_markers).setOnClickListener(v ->
                startActivity(new Intent(this, MarkerListActivity.class)));

        findViewById(R.id.btn_ball).setOnClickListener(v ->
                Toast.makeText(this, "Шар: в разработке", Toast.LENGTH_SHORT).show());

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
        
        // Load saved raw coordinates from last session
        loadSavedCoordinates();
    }
    
    private void loadSavedCoordinates() {
        // Load last known raw coordinates
        double savedRawX = prefs.getFloat("saved_raw_x", 0);
        double savedRawZ = prefs.getFloat("saved_raw_z", 0);
        droData.setRawX(savedRawX);
        droData.setRawZ(savedRawZ);
        
        // Load current tool
        currentTool = prefs.getInt("current_tool", 0);
        
        // Apply tool offsets to droData
        droData.setOffsetX(tools[currentTool].getOffsetX());
        droData.setOffsetD(tools[currentTool].getOffsetD());
        droData.setOffsetZ(tools[currentTool].getOffsetZ());
        droData.setOffsetL(tools[currentTool].getOffsetL());
    }

    private void loadSettings() {
        droData.setResolutionX(prefs.getFloat("resolution_x", 0.005f));
        droData.setResolutionZ(prefs.getFloat("resolution_z", 0.005f));
        droData.setDiameterMode(prefs.getBoolean("diameter_mode", true));

        soundEnabled = prefs.getBoolean("sound", true);
        proximityDistance = prefs.getFloat("proximity_distance", 5.0f);
        
        // Inversion: X is inverted by default, checkbox CANCELS the default inversion
        invertX = prefs.getBoolean("invert_x", false);
        invertZ = prefs.getBoolean("invert_z", false);

        coordColors[0] = prefs.getInt("color_x", coordColors[0]);
        coordColors[1] = prefs.getInt("color_d", coordColors[1]);
        coordColors[2] = prefs.getInt("color_z", coordColors[2]);
        coordColors[3] = prefs.getInt("color_l", coordColors[3]);

        applyColors();
        updateModeLabel();
    }

    private void applyColors() {
        int colorX = isConnected ? coordColors[0] : ContextCompat.getColor(this, R.color.coord_disconnected);
        int colorD = isConnected ? coordColors[1] : ContextCompat.getColor(this, R.color.coord_disconnected);
        int colorZ = isConnected ? coordColors[2] : ContextCompat.getColor(this, R.color.coord_disconnected);
        int colorL = isConnected ? coordColors[3] : ContextCompat.getColor(this, R.color.coord_disconnected);

        valueX.setTextColor(colorX);
        labelX.setTextColor(colorX);
        imgMode.setColorFilter(colorX);

        valueD.setTextColor(colorD);
        labelD.setTextColor(colorD);

        valueZ.setTextColor(colorZ);
        labelZ.setTextColor(colorZ);

        valueL.setTextColor(colorL);
        labelL.setTextColor(colorL);
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

    private void onConnectClick() {
        if (bluetoothService.isConnected()) {
            bluetoothService.disconnect();
            return;
        }

        // Подключаемся к сохранённому устройству
        String lastDeviceAddr = prefs.getString("last_device", "");
        if (lastDeviceAddr.isEmpty()) {
            Toast.makeText(this, "Выберите устройство в настройках", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ищем устройство по адресу
        Set<BluetoothDevice> devices = bluetoothService.getPairedDevices();
        if (devices == null) {
            Toast.makeText(this, "Bluetooth недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        for (BluetoothDevice device : devices) {
            if (device.getAddress().equals(lastDeviceAddr)) {
                connectedDeviceName = device.getName();
                bluetoothService.connect(device);
                return;
            }
        }

        Toast.makeText(this, "Устройство не найдено", Toast.LENGTH_SHORT).show();
    }

    private void tryAutoConnect() {
        String lastDeviceAddr = prefs.getString("last_device", "");
        if (lastDeviceAddr.isEmpty()) {
            return; // Нет сохранённого устройства
        }

        // Небольшая задержка перед подключением
        new android.os.Handler().postDelayed(() -> {
            if (bluetoothService == null || bluetoothService.isConnected()) {
                return;
            }

            Set<BluetoothDevice> devices = bluetoothService.getPairedDevices();
            if (devices == null) return;

            for (BluetoothDevice device : devices) {
                if (device.getAddress().equals(lastDeviceAddr)) {
                    connectedDeviceName = device.getName();
                    bluetoothService.connect(device);
                    return;
                }
            }
        }, 1000);
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

        // Update button text
        Button btnTool = findViewById(R.id.btn_tool);
        btnTool.setText("Инстр. " + (index + 1));

        droData.setOffsetX(tools[index].getOffsetX());
        droData.setOffsetD(tools[index].getOffsetD());
        droData.setOffsetZ(tools[index].getOffsetZ());
        droData.setOffsetL(tools[index].getOffsetL());

        // Save current tool selection
        prefs.edit().putInt("current_tool", currentTool).apply();

        updateDisplay();
    }

    private android.widget.PopupWindow toolPopup;
    
    private void showToolPopup(View anchor) {
        // Dismiss existing popup if showing
        if (toolPopup != null && toolPopup.isShowing()) {
            toolPopup.dismiss();
            return;
        }
        
        // Create popup view
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_tools, null);
        
        int[] btnIds = {R.id.btn_tool_1, R.id.btn_tool_2, R.id.btn_tool_3, R.id.btn_tool_4};
        for (int i = 0; i < 4; i++) {
            Button btn = popupView.findViewById(btnIds[i]);
            btn.setSelected(i == currentTool);
            final int toolIndex = i;
            btn.setOnClickListener(v -> {
                selectTool(toolIndex);
                if (toolPopup != null) toolPopup.dismiss();
            });
            btn.setOnLongClickListener(v -> {
                if (toolPopup != null) toolPopup.dismiss();
                editToolOffset(toolIndex);
                return true;
            });
        }
        
        // Create PopupWindow - NOT focusable to avoid system bars
        toolPopup = new android.widget.PopupWindow(
            popupView,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            false  // NOT focusable - prevents system bars from appearing
        );
        toolPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        toolPopup.setOutsideTouchable(true);
        toolPopup.setTouchable(true);
        toolPopup.setClippingEnabled(false);
        
        // Measure popup to get its size
        popupView.measure(
            android.view.View.MeasureSpec.UNSPECIFIED,
            android.view.View.MeasureSpec.UNSPECIFIED
        );
        int popupWidth = popupView.getMeasuredWidth();
        
        // Get screen size
        android.graphics.Point screenSize = new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getSize(screenSize);
        
        // Position in top-right corner (exact corner, no margin)
        int xOffset = screenSize.x - popupWidth;
        int yOffset = 0;
        
        toolPopup.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, xOffset, yOffset);
        
        // Set touch listener on popup to handle clicks
        popupView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE) {
                toolPopup.dismiss();
                return true;
            }
            return false;
        });
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
            imgMode.setImageResource(R.drawable.ic_diameter);
        } else {
            imgMode.setImageResource(R.drawable.ic_radius);
        }
    }

    // Диалог ввода с кнопкой 0
    private void showInputDialog(String title, double currentValue, InputCallback callback) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(R.layout.dialog_input_value);
        
        // Прозрачный фон окна - убирает голубую полосу!
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
                .putFloat("current_abs_x", (float) droData.getAbsoluteX())
                .putFloat("current_abs_z", (float) droData.getAbsoluteZ())
                .apply();
    }

    private String formatValue(double value) {
        return String.format(Locale.US, "%7.3f", value);
    }

    private void checkMarkers() {
        if (!soundEnabled) return;

        // Get active markers only
        List<Marker> activeMarkers = new ArrayList<>();
        for (Marker m : markers) {
            if (m.isActive()) {
                activeMarkers.add(m);
            }
        }
        if (activeMarkers.isEmpty()) return;

        // Use ABSOLUTE coordinates for markers (independent of zeroing and tool offsets)
        // absoluteX/absoluteZ = raw * resolution (NO offsets at all)
        double absoluteX = droData.getAbsoluteX();
        double absoluteZ = droData.getAbsoluteZ();

        // Find if we're close to any active marker
        Marker closestMarker = null;
        double minDistance = Double.MAX_VALUE;

        for (Marker m : activeMarkers) {
            double currentAbsPos = m.getAxis() == Marker.Axis.X ? absoluteX : absoluteZ;
            double distance = Math.abs(currentAbsPos - m.getPosition());
            if (distance < minDistance) {
                minDistance = distance;
                closestMarker = m;
            }
        }

        // Check if within proximity distance
        if (closestMarker != null && minDistance < proximityDistance) {
            long now = System.currentTimeMillis();
            // Only alert if enough time passed since last alert for this marker
            if (closestMarker != lastAlertedMarker || (now - lastAlertTime) > ALERT_COOLDOWN) {
                playMarkerAlert(closestMarker);
                lastAlertedMarker = closestMarker;
                lastAlertTime = now;
            }
        }
    }

    private void playMarkerAlert(Marker marker) {
        if (toneGenerator == null) return;

        // Play continuous tone for 2 seconds at max volume
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 2000);
    }

    private void updateActiveMarkersDisplay() {
        List<Marker> activeMarkers = new ArrayList<>();
        for (Marker m : markers) {
            if (m.isActive()) {
                activeMarkers.add(m);
            }
        }

        if (activeMarkers.isEmpty()) {
            activeMarkersContainer.setVisibility(View.GONE);
            return;
        }

        activeMarkersContainer.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        for (Marker m : activeMarkers) {
            if (sb.length() > 0) sb.append("\n");
            String axis = m.getAxis() == Marker.Axis.X ? "X" : "Z";
            sb.append(String.format("%s: %s (%.3f)", axis, m.getName(), m.getPosition()));
        }
        tvActiveMarkers.setText(sb.toString());
    }

    private void updateConnectionState() {
        runOnUiThread(() -> {
            if (isConnected) {
                btnConnectOverlay.setVisibility(View.GONE);
            } else {
                btnConnectOverlay.setVisibility(View.VISIBLE);
            }
            applyColors();
        });
    }

    @Override
    public void onConnected() {
        isConnected = true;
        // Сохраняем имя устройства
        prefs.edit()
                .putString("last_device_name", connectedDeviceName)
                .putBoolean("connected", true)
                .apply();
        updateConnectionState();
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
        prefs.edit().putBoolean("connected", false).apply();
        updateConnectionState();
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDataReceived(double x, double z) {
        runOnUiThread(() -> {
            // Apply inversion: X is inverted by default (DEFAULT_INVERT_X = true)
            // Checkbox cancels the default inversion when checked
            // Final X = DEFAULT_INVERT_X XOR invertX (checkbox)
            double finalX = (DEFAULT_INVERT_X != invertX) ? -x : x;
            // Z is not inverted by default, checkbox enables inversion
            double finalZ = invertZ ? -z : z;
            
            droData.setRawX(finalX);
            droData.setRawZ(finalZ);
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
        updateConnectionState();
        updateActiveMarkersDisplay();
        
        // Update tool button text
        Button btnTool = findViewById(R.id.btn_tool);
        btnTool.setText("Инстр. " + (currentTool + 1));
    }

    private void loadMarkers() {
        String json = prefs.getString("markers", "[]");
        List<Marker> loaded = MarkerListActivity.parseMarkers(json);
        markers.clear();
        markers.addAll(loaded);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save current state before pausing
        saveCurrentState();
    }

    private void saveCurrentState() {
        // Save current tool offsets before saving state
        if (currentTool >= 0 && currentTool < 4) {
            tools[currentTool].setOffsetX(droData.getOffsetX());
            tools[currentTool].setOffsetD(droData.getOffsetD());
            tools[currentTool].setOffsetZ(droData.getOffsetZ());
            tools[currentTool].setOffsetL(droData.getOffsetL());
            saveToolOffset(currentTool);
        }
        
        prefs.edit()
                .putFloat("saved_raw_x", (float) droData.getRawX())
                .putFloat("saved_raw_z", (float) droData.getRawZ())
                .putInt("current_tool", currentTool)
                .apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Save state before destroying
        saveCurrentState();
        
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}
