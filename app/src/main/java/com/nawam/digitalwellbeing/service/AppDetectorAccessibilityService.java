package com.nawam.digitalwellbeing.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Detects when the foreground app changes using AccessibilityService.
 * Sends broadcast to TimerTrackingService with the new package name.
 * This is the most reliable method on Android 14 / MagicUI 8.
 */
public class AppDetectorAccessibilityService extends AccessibilityService {

    public static final String ACTION_APP_CHANGED = "com.nawam.digitalwellbeing.APP_CHANGED";
    public static final String EXTRA_PACKAGE_NAME = "package_name";

    private String lastPackage = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence packageNameChar = event.getPackageName();
        if (packageNameChar == null) return;

        String packageName = packageNameChar.toString();

        // Ignore our own app and system UI
        if (packageName.equals(getPackageName())) return;
        if (packageName.equals("com.android.systemui")) return;

        // Only broadcast if app actually changed
        if (packageName.equals(lastPackage)) return;

        lastPackage = packageName;

        // Send broadcast to TimerTrackingService
        Intent intent = new Intent(ACTION_APP_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        sendBroadcast(intent);
    }

    @Override
    public void onInterrupt() {
        // Required override - nothing to do
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Start the timer tracking foreground service when accessibility connects
        Intent serviceIntent = new Intent(this, TimerTrackingService.class);
        startForegroundService(serviceIntent);
    }
}
