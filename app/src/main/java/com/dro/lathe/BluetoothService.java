package com.dro.lathe;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Bluetooth service for HC-06 connection
 */
public class BluetoothService {
    private static final String TAG = "BluetoothService";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface Callback {
        void onConnected();
        void onDisconnected();
        void onError(String message);
        void onDataReceived(double x, double z);
    }

    private final BluetoothAdapter bluetoothAdapter;
    private final Callback callback;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
    private volatile boolean running = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BluetoothService(Callback callback) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.callback = callback;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null) return null;
        return bluetoothAdapter.getBondedDevices();
    }

    public void connect(BluetoothDevice device) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Connecting to " + device.getName());
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                running = true;

                mainHandler.post(callback::onConnected);

                startReading();
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                mainHandler.post(() -> callback.onError("Ошибка подключения: " + e.getMessage()));
                disconnect();
            }
        }).start();
    }

    private void startReading() {
        readThread = new Thread(() -> {
            StringBuilder buffer = new StringBuilder();
            byte[] readBuffer = new byte[1024];

            while (running && socket != null && socket.isConnected()) {
                try {
                    int bytes = inputStream.read(readBuffer);
                    if (bytes > 0) {
                        String chunk = new String(readBuffer, 0, bytes);
                        buffer.append(chunk);

                        // Process complete lines
                        int newlineIndex;
                        while ((newlineIndex = buffer.indexOf("\n")) >= 0) {
                            String line = buffer.substring(0, newlineIndex).trim();
                            buffer.delete(0, newlineIndex + 1);

                            if (!line.isEmpty()) {
                                parseLine(line);
                            }
                        }
                    }
                } catch (IOException e) {
                    if (running) {
                        Log.e(TAG, "Read error", e);
                        mainHandler.post(() -> callback.onError("Ошибка чтения: " + e.getMessage()));
                        mainHandler.post(callback::onDisconnected);
                    }
                    break;
                }
            }
        });
        readThread.start();
    }

    private void parseLine(String line) {
        try {
            // Format: ValX|ValZ
            String[] parts = line.split("\\|");
            if (parts.length >= 2) {
                double x = Double.parseDouble(parts[0].trim());
                double z = Double.parseDouble(parts[1].trim());
                mainHandler.post(() -> callback.onDataReceived(x, z));
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Parse error: " + line);
        }
    }

    public void disconnect() {
        running = false;

        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }

        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {}
            inputStream = null;
        }

        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {}
            outputStream = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
            socket = null;
        }

        mainHandler.post(callback::onDisconnected);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    // === Двусторонняя связь (для будущего использования) ===

    public boolean send(String data) {
        if (socket == null || !socket.isConnected() || outputStream == null) {
            return false;
        }
        try {
            outputStream.write(data.getBytes());
            outputStream.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean sendZeroX() {
        return send("ZERO_X\n");
    }

    public boolean sendZeroZ() {
        return send("ZERO_Z\n");
    }

    public boolean sendZeroAll() {
        return send("ZERO_ALL\n");
    }
}
