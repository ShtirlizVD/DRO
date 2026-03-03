package com.dro.lathe;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
        markers = parseMarkers(json);
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
                array.put(obj);
            }
            prefs.edit().putString("markers", array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showAddDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_marker, null);
        RadioGroup rgAxis = view.findViewById(R.id.rg_axis);
        EditText etName = view.findViewById(R.id.et_marker_name);
        EditText etPosition = view.findViewById(R.id.et_marker_position);
        Button btnCurrent = view.findViewById(R.id.btn_current_pos);

        // Get current position
        double currentX = prefs.getFloat("current_x", 0);
        double currentZ = prefs.getFloat("current_z", 0);

        btnCurrent.setOnClickListener(v -> {
            RadioButton rbX = view.findViewById(R.id.rb_axis_x);
            if (rbX.isChecked()) {
                etPosition.setText(String.format(Locale.US, "%.3f", currentX));
            } else {
                etPosition.setText(String.format(Locale.US, "%.3f", currentZ));
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_marker)
                .setView(view)
                .setPositiveButton("Добавить", (dialog, which) -> {
                    String name = etName.getText().toString();
                    if (name.isEmpty()) name = "Засечка";

                    RadioButton rbX = view.findViewById(R.id.rb_axis_x);
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
                })
                .setNegativeButton("Отмена", null)
                .show();
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

            TextView tvAxis = convertView.findViewById(R.id.tv_axis);
            TextView tvName = convertView.findViewById(R.id.tv_name);
            TextView tvPosition = convertView.findViewById(R.id.tv_position);
            TextView tvDistance = convertView.findViewById(R.id.tv_distance);
            Button btnDelete = convertView.findViewById(R.id.btn_delete);

            tvAxis.setText(m.getAxis() == Marker.Axis.X ? "X" : "Z");
            int color = m.getAxis() == Marker.Axis.X ?
                    getResources().getColor(R.color.coord_x) :
                    getResources().getColor(R.color.coord_z);
            tvAxis.setTextColor(color);

            tvName.setText(m.getName());
            tvPosition.setText(String.format(Locale.US, "%.3f мм", m.getPosition()));

            // Calculate distance to current position
            float currentPos = m.getAxis() == Marker.Axis.X ?
                    prefs.getFloat("current_x", 0) :
                    prefs.getFloat("current_z", 0);
            double distance = m.getPosition() - currentPos;
            tvDistance.setText(String.format(Locale.US, "Δ %.3f", distance));

            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("Удалить засечку?")
                        .setMessage(m.getName())
                        .setPositiveButton("Удалить", (dialog, which) -> {
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
}
