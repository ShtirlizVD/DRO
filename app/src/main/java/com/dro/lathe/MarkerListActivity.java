package com.dro.lathe;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarkerListActivity extends AppCompatActivity {

    private ListView listView;
    private MarkerAdapter adapter;
    private List<Marker> markers = new ArrayList<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marker_list);

        // Включить полноэкранный режим
        enableFullscreenMode();

        prefs = getSharedPreferences("DRO_PREFS", MODE_PRIVATE);

        listView = findViewById(R.id.list_markers);
        adapter = new MarkerAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btn_add_marker).setOnClickListener(v -> showAddDialog());
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        loadMarkers();
    }

    private void loadMarkers() {
        String json = prefs.getString("markers", "[]");
        List<Marker> loaded = parseMarkers(json);
        markers.clear();
        markers.addAll(loaded);
        adapter.notifyDataSetChanged();
    }

    public static List<Marker> parseMarkers(String json) {
        List<Marker> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Marker m = new Marker();
                m.setId(obj.getLong("id"));
                m.setName(obj.getString("name"));
                m.setAxis(obj.getString("axis").equals("X") ? Marker.Axis.X : Marker.Axis.Z);
                m.setPosition(obj.getDouble("position"));
                m.setActive(obj.optBoolean("active", false));
                list.add(m);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    private void saveMarkers() {
        try {
            JSONArray array = new JSONArray();
            for (Marker m : markers) {
                JSONObject obj = new JSONObject();
                obj.put("id", m.getId());
                obj.put("name", m.getName());
                obj.put("axis", m.getAxis() == Marker.Axis.X ? "X" : "Z");
                obj.put("position", m.getPosition());
                obj.put("active", m.isActive());
                array.put(obj);
            }
            prefs.edit().putString("markers", array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showAddDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(R.layout.dialog_marker)
                .setPositiveButton("Добавить", null)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();

        // Get views after dialog is shown
        EditText etName = dialog.findViewById(R.id.et_marker_name);
        EditText etPosition = dialog.findViewById(R.id.et_marker_position);
        Button btnCurrent = dialog.findViewById(R.id.btn_current_pos);
        RadioButton rbX = dialog.findViewById(R.id.rb_axis_x);

        // Get current ABSOLUTE coordinates
        double currentAbsX = prefs.getFloat("current_abs_x", 0);
        double currentAbsZ = prefs.getFloat("current_abs_z", 0);

        btnCurrent.setOnClickListener(v -> {
            if (rbX.isChecked()) {
                etPosition.setText(String.format(Locale.US, "%.3f", currentAbsX));
            } else {
                etPosition.setText(String.format(Locale.US, "%.3f", currentAbsZ));
            }
        });

        // Override positive button to not dismiss on error
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString();
            if (name.isEmpty()) name = "Засечка";

            Marker.Axis axis = rbX.isChecked() ? Marker.Axis.X : Marker.Axis.Z;

            double position = 0;
            try {
                position = Double.parseDouble(etPosition.getText().toString());
            } catch (NumberFormatException ignored) {}

            Marker m = new Marker(name, axis, position);
            m.setId(System.currentTimeMillis());
            markers.add(m);
            saveMarkers();
            adapter.notifyDataSetChanged();
            dialog.dismiss();
        });
    }

    private class MarkerAdapter extends ArrayAdapter<Marker> {
        MarkerAdapter() {
            super(MarkerListActivity.this, R.layout.item_marker, markers);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_marker, parent, false);
            }

            Marker m = getItem(position);

            CheckBox cbActive = convertView.findViewById(R.id.cb_active);
            TextView tvAxis = convertView.findViewById(R.id.tv_axis);
            TextView tvName = convertView.findViewById(R.id.tv_name);
            TextView tvPosition = convertView.findViewById(R.id.tv_position);
            Button btnDelete = convertView.findViewById(R.id.btn_delete);

            // Setup checkbox
            cbActive.setOnCheckedChangeListener(null);
            cbActive.setChecked(m.isActive());
            cbActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                m.setActive(isChecked);
                saveMarkers();
            });

            tvAxis.setText(m.getAxis() == Marker.Axis.X ? "X" : "Z");
            int color = m.getAxis() == Marker.Axis.X ?
                    getResources().getColor(R.color.coord_x) :
                    getResources().getColor(R.color.coord_z);
            tvAxis.setTextColor(color);

            tvName.setText(m.getName());
            tvPosition.setText(String.format(Locale.US, "%.3f мм", m.getPosition()));

            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("Удалить засечку?")
                        .setMessage(m.getName())
                        .setPositiveButton("Удалить", (d, which) -> {
                            markers.remove(position);
                            saveMarkers();
                            notifyDataSetChanged();
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            });

            return convertView;
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
