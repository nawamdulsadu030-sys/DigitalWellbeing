package com.nawam.digitalwellbeing.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.nawam.digitalwellbeing.R;
import com.nawam.digitalwellbeing.db.AppUsageData;
import com.nawam.digitalwellbeing.ui.BlockActivity;
import com.nawam.digitalwellbeing.ui.MainActivity;

/**
 * Foreground service that:
 * 1. Receives app-change broadcasts from AccessibilityService
 * 2. Tracks elapsed time per app (pauses when app leaves foreground)
 * 3. Shows/hides floating timer bubble overlay
 * 4. Launches BlockActivity when limit is reached
 */
public class TimerTrackingService extends Service {

    private static final String CHANNEL_ID = "wellbeing_channel";
    private static final int NOTIF_ID = 1001;
    private static final long TICK_INTERVAL_MS = 1000L;

    // Current state
    private String currentPackage = null;
    private long sessionStartMs = 0;
    private boolean isTracking = false;

    private AppUsageData usageData;
    private Handler handler;
    private Runnable timerTick;

    // Floating overlay
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvTimer;
    private TextView tvAppName;
    private View dotIndicator;
    private WindowManager.LayoutParams overlayParams;

    // Overlay drag state
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    // Broadcast receiver for app changes
    private final BroadcastReceiver appChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newPackage = intent.getStringExtra(
                    AppDetectorAccessibilityService.EXTRA_PACKAGE_NAME);
            if (newPackage != null) {
                onAppChanged(newPackage);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        usageData = new AppUsageData(this);
        handler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Monitoring apps..."));

        // Register receiver for app-change events
        IntentFilter filter = new IntentFilter(
                AppDetectorAccessibilityService.ACTION_APP_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(appChangedReceiver, filter);
        }

        setupTimerTick();
    }

    private void setupTimerTick() {
        timerTick = new Runnable() {
            @Override
            public void run() {
                if (isTracking && currentPackage != null) {
                    long sessionSecs = (System.currentTimeMillis() - sessionStartMs) / 1000;
                    long saved = usageData.getElapsedSeconds(currentPackage);
                    long total = saved + sessionSecs;

                    updateOverlayDisplay(currentPackage, total);

                    // Check limits
                    AppUsageData.LimitStatus status = checkCurrentLimits(currentPackage, total);
                    if (status != AppUsageData.LimitStatus.OK) {
                        // Save before blocking
                        usageData.saveElapsedSeconds(currentPackage, total);
                        sessionStartMs = System.currentTimeMillis();
                        launchBlockScreen(currentPackage, status);
                    }
                }
                handler.postDelayed(this, TICK_INTERVAL_MS);
            }
        };
        handler.postDelayed(timerTick, TICK_INTERVAL_MS);
    }

    private void onAppChanged(String newPackage) {
        // Pause & save current session
        if (isTracking && currentPackage != null) {
            long sessionSecs = (System.currentTimeMillis() - sessionStartMs) / 1000;
            usageData.addElapsedSeconds(currentPackage, sessionSecs);
            isTracking = false;
        }

        currentPackage = newPackage;

        if (usageData.isMonitored(newPackage)) {
            // Check if already blocked before resuming
            AppUsageData.LimitStatus status =
                    usageData.checkLimitStatus(newPackage);
            if (status != AppUsageData.LimitStatus.OK) {
                launchBlockScreen(newPackage, status);
                removeOverlay();
                return;
            }

            // Start new session for this app
            sessionStartMs = System.currentTimeMillis();
            isTracking = true;

            long elapsed = usageData.getElapsedSeconds(newPackage);
            showOverlay(newPackage, elapsed);
        } else {
            removeOverlay();
        }
    }

    // ─── Floating Overlay ──────────────────────────────────────────────────────

    private void showOverlay(String packageName, long initialSeconds) {
        if (!Settings.canDrawOverlays(this)) return;

        removeOverlay(); // remove if already showing

        floatingView = LayoutInflater.from(this)
                .inflate(R.layout.overlay_timer, null);
        tvTimer = floatingView.findViewById(R.id.tv_timer);
        tvAppName = floatingView.findViewById(R.id.tv_app_name);
        dotIndicator = floatingView.findViewById(R.id.dot_indicator);

        // Set app label
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            String label = pm.getApplicationLabel(
                    pm.getApplicationInfo(packageName, 0)).toString();
            tvAppName.setText(label);
        } catch (Exception e) {
            tvAppName.setText(packageName);
        }

        updateOverlayDisplay(packageName, initialSeconds);

        // Window params — top-right, draggable
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.END;
        overlayParams.x = 16;
        overlayParams.y = 80;

        // Drag support
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = overlayParams.x;
                        initialY = overlayParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        overlayParams.x = initialX - (int)(event.getRawX() - initialTouchX);
                        overlayParams.y = initialY + (int)(event.getRawY() - initialTouchY);
                        if (floatingView.getParent() != null) {
                            windowManager.updateViewLayout(floatingView, overlayParams);
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, overlayParams);
    }

    private void updateOverlayDisplay(String packageName, long totalSeconds) {
        if (floatingView == null || tvTimer == null) return;

        // Format time
        long mins = totalSeconds / 60;
        long secs = totalSeconds % 60;
        tvTimer.setText(String.format("%d:%02d", mins, secs));

        // Color based on limit proximity
        long limit = usageData.getAppLimitSeconds(packageName);
        int color;
        if (limit > 0) {
            double pct = (double) totalSeconds / limit;
            if (pct >= 0.9) color = Color.parseColor("#F87171");      // red
            else if (pct >= 0.7) color = Color.parseColor("#FBBF24"); // yellow
            else color = Color.parseColor("#4ADE80");                   // green
        } else {
            color = Color.parseColor("#A78BFA"); // purple default
        }

        dotIndicator.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color));
        tvTimer.setTextColor(color);
    }

    private void removeOverlay() {
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {}
            floatingView = null;
            tvTimer = null;
            dotIndicator = null;
        }
    }

    // ─── Block Screen ──────────────────────────────────────────────────────────

    private void launchBlockScreen(String packageName, AppUsageData.LimitStatus status) {
        removeOverlay();
        Intent blockIntent = new Intent(this, BlockActivity.class);
        blockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        blockIntent.putExtra(BlockActivity.EXTRA_PACKAGE, packageName);
        blockIntent.putExtra(BlockActivity.EXTRA_STATUS, status.name());
        startActivity(blockIntent);
    }

    private AppUsageData.LimitStatus checkCurrentLimits(String pkg, long totalSecs) {
        long appLimit = usageData.getAppLimitSeconds(pkg);
        if (appLimit > 0 && totalSecs >= appLimit) {
            return AppUsageData.LimitStatus.APP_LIMIT_REACHED;
        }
        long overallLimit = usageData.getOverallLimitSeconds();
        if (overallLimit > 0) {
            long sessionSecs = (System.currentTimeMillis() - sessionStartMs) / 1000;
            long grandTotal = usageData.getTotalElapsedSeconds() + sessionSecs;
            if (grandTotal >= overallLimit) {
                return AppUsageData.LimitStatus.OVERALL_LIMIT_REACHED;
            }
        }
        return AppUsageData.LimitStatus.OK;
    }

    // ─── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Digital Wellbeing",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("App usage tracking");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Digital Wellbeing")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerTick);
        removeOverlay();
        try { unregisterReceiver(appChangedReceiver); } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
