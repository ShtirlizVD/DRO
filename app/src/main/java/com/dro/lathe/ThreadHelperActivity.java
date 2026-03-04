package com.dro.lathe;

import android.content.Intent;
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

/**
 * Помощник резьбы - главный экран
 * Показывает список резьб с возможностью перехода к детальному расчёту проходов
 */
public class ThreadHelperActivity extends AppCompatActivity {

    private ListView threadList;
    private Button btnMetric;
    private Button btnInch;
    private Button btnPipe;
    private Button btnExternal;
    private Button btnInternal;

    private ThreadDatabase.ThreadCategory currentCategory = ThreadDatabase.ThreadCategory.METRIC;
    private ThreadDatabase.ThreadType currentType = ThreadDatabase.ThreadType.EXTERNAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_helper);

        initViews();
        setupListeners();
        updateList();
    }

    private void initViews() {
        threadList = findViewById(R.id.thread_list);
        btnMetric = findViewById(R.id.tab_metric);
        btnInch = findViewById(R.id.tab_inch);
        btnPipe = findViewById(R.id.tab_pipe);

        // External/Internal buttons (add to layout)
        btnExternal = findViewById(R.id.btn_type_external);
        btnInternal = findViewById(R.id.btn_type_internal);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        // Category tabs
        btnMetric.setOnClickListener(v -> {
            currentCategory = ThreadDatabase.ThreadCategory.METRIC;
            updateTabs();
            updateList();
        });

        btnInch.setOnClickListener(v -> {
            currentCategory = ThreadDatabase.ThreadCategory.INCH;
            updateTabs();
            updateList();
        });

        btnPipe.setOnClickListener(v -> {
            currentCategory = ThreadDatabase.ThreadCategory.PIPE;
            updateTabs();
            updateList();
        });

        // Type buttons
        if (btnExternal != null) {
            btnExternal.setOnClickListener(v -> {
                currentType = ThreadDatabase.ThreadType.EXTERNAL;
                updateTypeButtons();
            });
        }

        if (btnInternal != null) {
            btnInternal.setOnClickListener(v -> {
                currentType = ThreadDatabase.ThreadType.INTERNAL;
                updateTypeButtons();
            });
        }

        // Click on thread item - open detail
        threadList.setOnItemClickListener((parent, view, position, id) -> {
            ThreadDatabase.ThreadInfo thread = (ThreadDatabase.ThreadInfo) parent.getItemAtPosition(position);
            openThreadDetail(thread);
        });
    }

    private void updateTabs() {
        btnMetric.setSelected(currentCategory == ThreadDatabase.ThreadCategory.METRIC);
        btnInch.setSelected(currentCategory == ThreadDatabase.ThreadCategory.INCH);
        btnPipe.setSelected(currentCategory == ThreadDatabase.ThreadCategory.PIPE);
    }

    private void updateTypeButtons() {
        if (btnExternal != null) {
            btnExternal.setSelected(currentType == ThreadDatabase.ThreadType.EXTERNAL);
        }
        if (btnInternal != null) {
            btnInternal.setSelected(currentType == ThreadDatabase.ThreadType.INTERNAL);
        }
    }

    private void updateList() {
        ThreadDatabase.ThreadInfo[] threads = ThreadDatabase.getThreads(currentCategory);
        ThreadAdapter adapter = new ThreadAdapter(threads);
        threadList.setAdapter(adapter);
    }

    private void openThreadDetail(ThreadDatabase.ThreadInfo thread) {
        Intent intent = new Intent(this, ThreadDetailActivity.class);
        intent.putExtra(ThreadDetailActivity.EXTRA_THREAD_NAME, thread.name);
        intent.putExtra(ThreadDetailActivity.EXTRA_THREAD_TYPE,
                currentType == ThreadDatabase.ThreadType.INTERNAL ? 1 : 0);
        startActivity(intent);
    }

    /**
     * Адаптер для списка резьб
     */
    private class ThreadAdapter extends ArrayAdapter<ThreadDatabase.ThreadInfo> {

        ThreadAdapter(ThreadDatabase.ThreadInfo[] threads) {
            super(ThreadHelperActivity.this, R.layout.item_thread, threads);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_thread, parent, false);
            }

            ThreadDatabase.ThreadInfo t = getItem(position);

            TextView tvName = convertView.findViewById(R.id.tv_thread_name);
            TextView tvPitch = convertView.findViewById(R.id.tv_thread_pitch);
            TextView tvDepth = convertView.findViewById(R.id.tv_thread_depth);
            TextView tvPasses = convertView.findViewById(R.id.tv_thread_passes);

            tvName.setText(t.name);

            // Format pitch
            if (t.category == ThreadDatabase.ThreadCategory.INCH) {
                tvPitch.setText(String.format(Locale.US, "%.3f мм\n(%d TPI)", t.pitch, (int)t.threadsPerInch));
            } else {
                tvPitch.setText(String.format(Locale.US, "%.3f мм", t.pitch));
            }

            tvDepth.setText(String.format(Locale.US, "%.3f мм", t.threadDepth));

            // Calculate number of passes
            int passes = calculatePassesCount(t.pitch);
            tvPasses.setText(String.valueOf(passes));

            // Show diameter info
            TextView tvDiameter = convertView.findViewById(R.id.tv_thread_diameter);
            if (tvDiameter != null) {
                tvDiameter.setText(String.format(Locale.US, "%.1f", t.majorDiameter));
            }

            return convertView;
        }

        private int calculatePassesCount(double pitch) {
            if (pitch <= 0.5) return 3;
            if (pitch <= 0.7) return 5;
            if (pitch <= 0.8) return 6;
            if (pitch <= 1.0) return 7;
            if (pitch <= 1.25) return 9;
            if (pitch <= 1.5) return 11;
            if (pitch <= 1.75) return 13;
            if (pitch <= 2.0) return 15;
            if (pitch <= 2.5) return 18;
            if (pitch <= 3.0) return 21;
            if (pitch <= 3.5) return 24;
            return 27;
        }
    }
}
