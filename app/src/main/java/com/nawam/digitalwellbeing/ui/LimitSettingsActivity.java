package com.nawam.digitalwellbeing.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nawam.digitalwellbeing.R;
import com.nawam.digitalwellbeing.db.AppUsageData;

import java.util.Set;

/**
 * Set per-app time limits and overall daily limit.
 * Limits are entered in minutes for simplicity.
 */
public class LimitSettingsActivity extends AppCompatActivity {

    private AppUsageData usageData;
    private LinearLayout containerPerApp;
    private EditText etOverallLimit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_limit_settings);

        usageData = new AppUsageData(this);
        containerPerApp = findViewById(R.id.container_per_app);
        etOverallLimit = findViewById(R.id.et_overall_limit);
        Button btnSave = findViewById(R.id.btn_save_limits);

        // Show current overall limit
        long overallSecs = usageData.getOverallLimitSeconds();
        if (overallSecs > 0) {
            etOverallLimit.setText(String.valueOf(overallSecs / 60));
        }

        loadPerAppLimits();

        btnSave.setOnClickListener(v -> saveLimits());
    }

    private void loadPerAppLimits() {
        Set<String> monitored = usageData.getMonitoredApps();
        containerPerApp.removeAllViews();

        for (String pkg : monitored) {
            String label = pkg;
            try {
                label = getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(pkg, 0)).toString();
            } catch (Exception ignored) {}

            // Row: AppName | EditText (minutes)
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 12, 0, 12);

            TextView tvName = new TextView(this);
            tvName.setText(label);
            tvName.setTextColor(0xFFE2E8F0);
            tvName.setTextSize(14f);
            tvName.setTag(pkg); // store package in tag
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(nameParams);

            EditText etLimit = new EditText(this);
            etLimit.setHint("min");
            etLimit.setTextColor(0xFFE2E8F0);
            etLimit.setHintTextColor(0xFF666666);
            etLimit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etLimit.setWidth(160);
            etLimit.setTag("et_" + pkg); // tag to find later

            long savedLimit = usageData.getAppLimitSeconds(pkg);
            if (savedLimit > 0) {
                etLimit.setText(String.valueOf(savedLimit / 60));
            }

            row.addView(tvName, nameParams);
            row.addView(etLimit);
            containerPerApp.addView(row);

            // Divider
            android.view.View divider = new android.view.View(this);
            divider.setBackgroundColor(0xFF222233);
            containerPerApp.addView(divider,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private void saveLimits() {
        // Save overall limit
        String overallStr = etOverallLimit.getText().toString().trim();
        if (!overallStr.isEmpty()) {
            long overallMins = Long.parseLong(overallStr);
            usageData.setOverallLimitSeconds(overallMins * 60);
        } else {
            usageData.setOverallLimitSeconds(0);
        }

        // Save per-app limits
        Set<String> monitored = usageData.getMonitoredApps();
        for (String pkg : monitored) {
            android.view.View row = containerPerApp.findViewWithTag("et_" + pkg);
            if (row instanceof EditText) {
                String val = ((EditText) row).getText().toString().trim();
                if (!val.isEmpty()) {
                    long mins = Long.parseLong(val);
                    usageData.setAppLimitSeconds(pkg, mins * 60);
                } else {
                    usageData.setAppLimitSeconds(pkg, 0);
                }
            }
        }

        Toast.makeText(this, "Limits saved!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
