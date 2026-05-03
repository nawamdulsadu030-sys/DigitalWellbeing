package com.nawam.digitalwellbeing.ui;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nawam.digitalwellbeing.R;
import com.nawam.digitalwellbeing.db.AppUsageData;
import com.nawam.digitalwellbeing.service.TimerTrackingService;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private AppUsageData usageData;
    private TextView tvTotalUsage;
    private TextView tvMonitoredCount;
    private TextView tvPermStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usageData = new AppUsageData(this);

        tvTotalUsage = findViewById(R.id.tv_total_usage);
        tvMonitoredCount = findViewById(R.id.tv_monitored_count);
        tvPermStatus = findViewById(R.id.tv_perm_status);

        Button btnSelectApps = findViewById(R.id.btn_select_apps);
        Button btnPermissions = findViewById(R.id.btn_permissions);
        Button btnLimits = findViewById(R.id.btn_limits);

        btnSelectApps.setOnClickListener(v ->
                startActivity(new Intent(this, AppSelectorActivity.class)));

        btnPermissions.setOnClickListener(v -> openPermissionsGuide());

        btnLimits.setOnClickListener(v ->
                startActivity(new Intent(this, LimitSettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
        checkAndStartService();
    }

    private void refreshUI() {
        // Total usage today
        long totalSecs = usageData.getTotalElapsedSeconds();
        tvTotalUsage.setText("Today: " + AppUsageData.formatSeconds(totalSecs));

        // Monitored apps count
        Set<String> monitored = usageData.getMonitoredApps();
        tvMonitoredCount.setText(monitored.size() + " apps monitored");

        // Permission status
        boolean hasUsageStats = hasUsageStatsPermission();
        boolean hasOverlay = Settings.canDrawOverlays(this);
        boolean hasAccessibility = isAccessibilityEnabled();

        if (hasUsageStats && hasOverlay && hasAccessibility) {
            tvPermStatus.setText("All permissions granted");
            tvPermStatus.setTextColor(getColor(android.R.color.holo_green_light));
        } else {
            List<String> missing = new ArrayList<>();
            if (!hasUsageStats) missing.add("Usage Stats");
            if (!hasOverlay) missing.add("Overlay");
            if (!hasAccessibility) missing.add("Accessibility");
            tvPermStatus.setText("Missing: " + String.join(", ", missing));
            tvPermStatus.setTextColor(getColor(android.R.color.holo_red_light));
        }
    }

    private void checkAndStartService() {
        if (hasUsageStatsPermission() && Settings.canDrawOverlays(this)
                && isAccessibilityEnabled()) {
            Intent serviceIntent = new Intent(this, TimerTrackingService.class);
            startForegroundService(serviceIntent);
        }
    }

    private void openPermissionsGuide() {
        String[] steps = {
            "1. USAGE STATS: Settings > Apps > Special app access > Usage access > Digital Wellbeing > Allow",
            "2. OVERLAY: Settings > Apps > Digital Wellbeing > Display over other apps > Allow",
            "3. ACCESSIBILITY: Settings > Accessibility > Installed apps > Digital Wellbeing App Monitor > ON",
            "4. BATTERY (Honor): Settings > Apps > Digital Wellbeing > Battery > No restrictions + Auto-launch ON"
        };

        new AlertDialog.Builder(this)
                .setTitle("Required Permissions")
                .setMessage(String.join("\n\n", steps))
                .setPositiveButton("Open Usage Stats", (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                })
                .setNeutralButton("Open Overlay", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton("Open Accessibility", (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .show();
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<android.accessibilityservice.AccessibilityServiceInfo> services =
                am.getEnabledAccessibilityServiceList(
                        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (android.accessibilityservice.AccessibilityServiceInfo info : services) {
            if (info.getId().contains(getPackageName())) return true;
        }
        return false;
    }
}
