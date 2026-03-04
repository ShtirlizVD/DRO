package com.dro.lathe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Детальный экран резьбы с пошаговым списком проходов
 */
public class ThreadDetailActivity extends AppCompatActivity {

    public static final String EXTRA_THREAD_NAME = "thread_name";
    public static final String EXTRA_THREAD_TYPE = "thread_type"; // 0 = external, 1 = internal

    private static final String PREFS_NAME = "thread_progress";
    private static final String KEY_PREFIX = "pass_";

    private ThreadDatabase.ThreadInfo threadInfo;
    private ThreadDatabase.ThreadType threadType;
    private ThreadPassCalculator.CalculationResult calculation;
    private boolean[] completedPasses;

    private TextView tvThreadName;
    private TextView tvPitch;
    private TextView tvDepth;
    private TextView tvPassesCount;
    private TextView tvStartDiameter;
    private TextView tvFinalDiameter;
    private TextView tvTapDrill;
    private TextView tvProgressPercent;
    private ProgressBar progressBar;
    private ListView passesList;
    private Button btnExternal;
    private Button btnInternal;
    private Button btnReset;
    private Button btnClose;

    private PassAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_detail);

        // Get thread info from intent
        String threadName = getIntent().getStringExtra(EXTRA_THREAD_NAME);
        int typeIndex = getIntent().getIntExtra(EXTRA_THREAD_TYPE, 0);
        threadType = typeIndex == 1 ? ThreadDatabase.ThreadType.INTERNAL : ThreadDatabase.ThreadType.EXTERNAL;

        threadInfo = ThreadDatabase.findThread(threadName);
        if (threadInfo == null) {
            finish();
            return;
        }

        initViews();
        loadProgress();
        updateCalculation();
        setupListeners();
    }

    private void initViews() {
        tvThreadName = findViewById(R.id.tv_thread_name);
        tvPitch = findViewById(R.id.tv_pitch);
        tvDepth = findViewById(R.id.tv_depth);
        tvPassesCount = findViewById(R.id.tv_passes_count);
        tvStartDiameter = findViewById(R.id.tv_start_diameter);
        tvFinalDiameter = findViewById(R.id.tv_final_diameter);
        tvTapDrill = findViewById(R.id.tv_tap_drill);
        tvProgressPercent = findViewById(R.id.tv_progress_percent);
        progressBar = findViewById(R.id.progress_bar);
        passesList = findViewById(R.id.passes_list);
        btnExternal = findViewById(R.id.btn_external);
        btnInternal = findViewById(R.id.btn_internal);
        btnReset = findViewById(R.id.btn_reset);
        btnClose = findViewById(R.id.btn_close);

        // Set static values
        tvThreadName.setText(threadInfo.name);
        tvPitch.setText(String.format(Locale.US, "%.3f мм", threadInfo.pitch));
        tvDepth.setText(String.format(Locale.US, "%.3f мм", threadInfo.threadDepth));
        tvTapDrill.setText(String.format(Locale.US, "%.2f", threadInfo.tapDrillSize));
    }

    private void loadProgress() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int passCount = prefs.getInt(getProgressKey() + "_count", 0);
        completedPasses = new boolean[50]; // Max possible passes
        for (int i = 0; i < passCount; i++) {
            completedPasses[i] = prefs.getBoolean(getProgressKey() + "_" + i, false);
        }
    }

    private void saveProgress() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(getProgressKey() + "_count", calculation.passes.size());
        for (int i = 0; i < calculation.passes.size(); i++) {
            editor.putBoolean(getProgressKey() + "_" + i, calculation.passes.get(i).isCompleted);
        }
        editor.apply();
    }

    private String getProgressKey() {
        return KEY_PREFIX + threadInfo.name + "_" + threadType.name();
    }

    private void clearProgress() {
        for (int i = 0; i < completedPasses.length; i++) {
            completedPasses[i] = false;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    private void updateCalculation() {
        calculation = ThreadPassCalculator.calculate(threadInfo, threadType,
                ThreadPassCalculator.InfeedMethod.RADIAL, completedPasses);

        // Update UI
        tvPassesCount.setText(String.valueOf(calculation.totalPasses));
        tvStartDiameter.setText(String.format(Locale.US, "%.3f",
                calculation.startDiameter));
        tvFinalDiameter.setText(String.format(Locale.US, "%.3f",
                calculation.finalDiameter));

        // Update progress
        int progress = (int) calculation.getProgressPercent();
        progressBar.setProgress(progress);
        tvProgressPercent.setText(String.format(Locale.US, "%d%%", progress));

        // Update type buttons
        btnExternal.setSelected(threadType == ThreadDatabase.ThreadType.EXTERNAL);
        btnInternal.setSelected(threadType == ThreadDatabase.ThreadType.INTERNAL);

        // Update list
        adapter = new PassAdapter(calculation);
        passesList.setAdapter(adapter);
    }

    private void setupListeners() {
        btnExternal.setOnClickListener(v -> {
            if (threadType != ThreadDatabase.ThreadType.EXTERNAL) {
                threadType = ThreadDatabase.ThreadType.EXTERNAL;
                clearProgress();
                updateCalculation();
            }
        });

        btnInternal.setOnClickListener(v -> {
            if (threadType != ThreadDatabase.ThreadType.INTERNAL) {
                threadType = ThreadDatabase.ThreadType.INTERNAL;
                clearProgress();
                updateCalculation();
            }
        });

        btnReset.setOnClickListener(v -> {
            clearProgress();
            for (ThreadPassCalculator.PassInfo pass : calculation.passes) {
                pass.isCompleted = false;
            }
            updateCalculation();
        });

        btnClose.setOnClickListener(v -> finish());

        passesList.setOnItemClickListener((parent, view, position, id) -> {
            ThreadPassCalculator.PassInfo pass = calculation.passes.get(position);

            // Toggle completion
            pass.isCompleted = !pass.isCompleted;

            // Update completed passes array
            recalculateProgress();

            saveProgress();
            updateCalculation();
        });
    }

    private void recalculateProgress() {
        calculation.completedPasses = 0;
        calculation.completedDepth = 0;

        double cumulative = 0;
        for (ThreadPassCalculator.PassInfo pass : calculation.passes) {
            cumulative += pass.depthIncrement;
            if (pass.isCompleted) {
                calculation.completedPasses++;
                calculation.completedDepth = cumulative;
            }
        }
    }

    /**
     * Адаптер для списка проходов
     */
    private class PassAdapter extends ArrayAdapter<ThreadPassCalculator.PassInfo> {

        PassAdapter(ThreadPassCalculator.CalculationResult result) {
            super(ThreadDetailActivity.this, R.layout.item_pass, result.passes);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_pass, parent, false);
            }

            ThreadPassCalculator.PassInfo pass = getItem(position);

            TextView tvPassNumber = convertView.findViewById(R.id.tv_pass_number);
            TextView tvDepthInc = convertView.findViewById(R.id.tv_depth_inc);
            TextView tvTotalDepth = convertView.findViewById(R.id.tv_total_depth);
            TextView tvDiameter = convertView.findViewById(R.id.tv_diameter);
            ImageView ivStatus = convertView.findViewById(R.id.iv_status);

            // Determine if this is a spring pass (depth increment is 0)
            boolean isSpringPass = pass.depthIncrement == 0 && position > 0;

            tvPassNumber.setText(String.valueOf(pass.passNumber));
            tvDepthInc.setText(String.format(Locale.US, "%.3f", pass.depthIncrement));
            tvTotalDepth.setText(String.format(Locale.US, "%.3f", pass.totalDepth));
            tvDiameter.setText(String.format(Locale.US, "%.3f", pass.diameterTarget));

            // Set colors based on state
            int bgColor = Color.TRANSPARENT;
            int textColor = getResources().getColor(R.color.text_bright);

            if (pass.isCompleted) {
                if (isSpringPass) {
                    bgColor = getResources().getColor(R.color.pass_spring);
                } else {
                    bgColor = getResources().getColor(R.color.pass_completed);
                }
                ivStatus.setImageResource(android.R.drawable.checkbox_on_background);
                ivStatus.setColorFilter(getResources().getColor(R.color.pass_completed));
            } else {
                bgColor = getResources().getColor(R.color.pass_pending);
                ivStatus.setImageResource(android.R.drawable.checkbox_off_background);
                ivStatus.setColorFilter(getResources().getColor(R.color.text_dim));
            }

            convertView.setBackgroundColor(bgColor);

            // Highlight next pass to do
            if (!pass.isCompleted && isNextPass(position)) {
                convertView.setBackgroundColor(getResources().getColor(R.color.pass_current));
            }

            return convertView;
        }

        private boolean isNextPass(int position) {
            // Check if all previous passes are completed
            for (int i = 0; i < position; i++) {
                if (!calculation.passes.get(i).isCompleted) {
                    return false;
                }
            }
            return true;
        }
    }
}
