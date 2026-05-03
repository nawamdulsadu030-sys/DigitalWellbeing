package com.nawam.digitalwellbeing.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nawam.digitalwellbeing.R;
import com.nawam.digitalwellbeing.db.AppUsageData;

/**
 * Full-screen block activity shown when an app's time limit is reached.
 * Cannot be skipped. PIN-protected snooze (optional).
 */
public class BlockActivity extends Activity {

    public static final String EXTRA_PACKAGE = "extra_package";
    public static final String EXTRA_STATUS = "extra_status";

    private String blockedPackage;
    private AppUsageData usageData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen if needed
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_block);

        usageData = new AppUsageData(this);
        blockedPackage = getIntent().getStringExtra(EXTRA_PACKAGE);
        String statusStr = getIntent().getStringExtra(EXTRA_STATUS);

        setupUI(statusStr);
    }

    private void setupUI(String statusStr) {
        ImageView ivIcon = findViewById(R.id.iv_app_icon);
        TextView tvAppName = findViewById(R.id.tv_app_name);
        TextView tvUsed = findViewById(R.id.tv_time_used);
        TextView tvLimit = findViewById(R.id.tv_limit);
        TextView tvMessage = findViewById(R.id.tv_message);
        ProgressBar progressBar = findViewById(R.id.progress_bar);
        Button btnGoBack = findViewById(R.id.btn_go_back);

        // App info
        PackageManager pm = getPackageManager();
        try {
            ivIcon.setImageDrawable(pm.getApplicationIcon(blockedPackage));
            String label = pm.getApplicationLabel(
                    pm.getApplicationInfo(blockedPackage, 0)).toString();
            tvAppName.setText(label);
        } catch (Exception e) {
            tvAppName.setText(blockedPackage);
        }

        // Usage info
        long elapsedSecs = usageData.getElapsedSeconds(blockedPackage);
        long limitSecs = usageData.getAppLimitSeconds(blockedPackage);

        tvUsed.setText("Used today: " + AppUsageData.formatSeconds(elapsedSecs));

        AppUsageData.LimitStatus status;
        try {
            status = AppUsageData.LimitStatus.valueOf(statusStr);
        } catch (Exception e) {
            status = AppUsageData.LimitStatus.APP_LIMIT_REACHED;
        }

        if (status == AppUsageData.LimitStatus.OVERALL_LIMIT_REACHED) {
            long overall = usageData.getOverallLimitSeconds();
            tvLimit.setText("Daily limit: " + AppUsageData.formatSeconds(overall));
            tvMessage.setText("You've reached your overall daily screen time limit.");
            long total = usageData.getTotalElapsedSeconds();
            int pct = overall > 0 ? (int)((total * 100) / overall) : 100;
            progressBar.setProgress(Math.min(pct, 100));
        } else {
            tvLimit.setText("App limit: " + AppUsageData.formatSeconds(limitSecs));
            tvMessage.setText("Take a break. You've hit your limit for this app today.");
            int pct = limitSecs > 0 ? (int)((elapsedSecs * 100) / limitSecs) : 100;
            progressBar.setProgress(Math.min(pct, 100));
        }

        btnGoBack.setOnClickListener(v -> goToHome());
    }

    private void goToHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        goToHome();
    }
}
