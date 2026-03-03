package com.dro.lathe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ThreadHelperActivity extends AppCompatActivity {

    private LinearLayout tabsContainer;
    private ListView threadList;

    private String currentType = "metric";

    private ThreadData[] metricThreads = {
            new ThreadData("M3", 0.5, 0.307),
            new ThreadData("M4", 0.7, 0.429),
            new ThreadData("M5", 0.8, 0.491),
            new ThreadData("M6", 1.0, 0.613),
            new ThreadData("M8", 1.25, 0.767),
            new ThreadData("M10", 1.5, 0.920),
            new ThreadData("M12", 1.75, 1.073),
            new ThreadData("M14", 2.0, 1.227),
            new ThreadData("M16", 2.0, 1.227),
            new ThreadData("M18", 2.5, 1.533),
            new ThreadData("M20", 2.5, 1.533),
            new ThreadData("M22", 2.5, 1.533),
            new ThreadData("M24", 3.0, 1.840)
    };

    private ThreadData[] inchThreads = {
            new ThreadData("1/4\"-20", 1.270, 0.779),
            new ThreadData("5/16\"-18", 1.411, 0.866),
            new ThreadData("3/8\"-16", 1.587, 0.974),
            new ThreadData("1/2\"-13", 1.954, 1.199),
            new ThreadData("5/8\"-11", 2.309, 1.417),
            new ThreadData("3/4\"-10", 2.540, 1.558),
            new ThreadData("7/8\"-9", 2.822, 1.731),
            new ThreadData("1\"-8", 3.175, 1.948)
    };

    private ThreadData[] pipeThreads = {
            new ThreadData("G1/8\"", 0.907, 0.581),
            new ThreadData("G1/4\"", 1.337, 0.856),
            new ThreadData("G3/8\"", 1.337, 0.856),
            new ThreadData("G1/2\"", 1.814, 1.162),
            new ThreadData("G5/8\"", 1.814, 1.162),
            new ThreadData("G3/4\"", 1.814, 1.162),
            new ThreadData("G7/8\"", 1.814, 1.162),
            new ThreadData("G1\"", 2.309, 1.479),
            new ThreadData("G1 1/4\"", 2.309, 1.479),
            new ThreadData("G1 1/2\"", 2.309, 1.479),
            new ThreadData("G2\"", 2.309, 1.479)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_helper);

        tabsContainer = findViewById(R.id.tabs_container);
        threadList = findViewById(R.id.thread_list);

        // Tab buttons
        Button btnMetric = findViewById(R.id.tab_metric);
        Button btnInch = findViewById(R.id.tab_inch);
        Button btnPipe = findViewById(R.id.tab_pipe);

        btnMetric.setOnClickListener(v -> selectTab("metric"));
        btnInch.setOnClickListener(v -> selectTab("inch"));
        btnPipe.setOnClickListener(v -> selectTab("pipe"));

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        selectTab("metric");
    }

    private void selectTab(String type) {
        currentType = type;

        // Update tab buttons
        int[] ids = {R.id.tab_metric, R.id.tab_inch, R.id.tab_pipe};
        String[] types = {"metric", "inch", "pipe"};

        for (int i = 0; i < 3; i++) {
            Button btn = findViewById(ids[i]);
            btn.setSelected(types[i].equals(type));
        }

        // Update list
        ThreadData[] threads;
        switch (type) {
            case "metric": threads = metricThreads; break;
            case "inch": threads = inchThreads; break;
            case "pipe": threads = pipeThreads; break;
            default: threads = metricThreads;
        }

        ThreadAdapter adapter = new ThreadAdapter(threads);
        threadList.setAdapter(adapter);
    }

    private static class ThreadData {
        String name;
        double pitch;
        double depth;

        ThreadData(String name, double pitch, double depth) {
            this.name = name;
            this.pitch = pitch;
            this.depth = depth;
        }

        int getPasses() {
            // Approximate number of passes
            if (depth <= 0.3) return 3;
            if (depth <= 0.5) return 5;
            if (depth <= 0.8) return 8;
            if (depth <= 1.0) return 10;
            if (depth <= 1.5) return 15;
            return (int) (depth / 0.1);
        }
    }

    private class ThreadAdapter extends ArrayAdapter<ThreadData> {
        ThreadAdapter(ThreadData[] threads) {
            super(ThreadHelperActivity.this, R.layout.item_thread, threads);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_thread, parent, false);
            }

            ThreadData t = getItem(position);

            TextView tvName = convertView.findViewById(R.id.tv_thread_name);
            TextView tvPitch = convertView.findViewById(R.id.tv_thread_pitch);
            TextView tvDepth = convertView.findViewById(R.id.tv_thread_depth);
            TextView tvPasses = convertView.findViewById(R.id.tv_thread_passes);

            tvName.setText(t.name);
            tvPitch.setText(String.format(Locale.US, "%.3f мм", t.pitch));
            tvDepth.setText(String.format(Locale.US, "%.3f мм", t.depth));
            tvPasses.setText(String.valueOf(t.getPasses()));

            return convertView;
        }
    }
}
