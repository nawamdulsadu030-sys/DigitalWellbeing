package com.nawam.digitalwellbeing.db;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages per-app usage data using SharedPreferences.
 * Stores: elapsed seconds today, per-app limits, overall daily limit.
 * Resets daily at midnight.
 */
public class AppUsageData {

    private static final String PREFS_USAGE = "prefs_usage";
    private static final String PREFS_LIMITS = "prefs_limits";
    private static final String KEY_LAST_RESET_DAY = "last_reset_day";
    private static final String KEY_OVERALL_LIMIT_SECONDS = "overall_limit_seconds";
    private static final String KEY_USAGE_MAP = "usage_map_json";

    private final SharedPreferences usagePrefs;
    private final SharedPreferences limitsPrefs;

    public AppUsageData(Context context) {
        usagePrefs = context.getSharedPreferences(PREFS_USAGE, Context.MODE_PRIVATE);
        limitsPrefs = context.getSharedPreferences(PREFS_LIMITS, Context.MODE_PRIVATE);
        checkDailyReset();
    }

    // ─── Daily Reset ───────────────────────────────────────────────────────────

    private void checkDailyReset() {
        int today = getTodayDay();
        int lastReset = usagePrefs.getInt(KEY_LAST_RESET_DAY, -1);
        if (lastReset != today) {
            usagePrefs.edit()
                    .putInt(KEY_LAST_RESET_DAY, today)
                    .putString(KEY_USAGE_MAP, "{}")
                    .apply();
        }
    }

    private int getTodayDay() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR);
    }

    // ─── Usage Tracking ────────────────────────────────────────────────────────

    /** Get elapsed seconds today for a package */
    public long getElapsedSeconds(String packageName) {
        Map<String, Long> map = loadUsageMap();
        Long val = map.get(packageName);
        return val != null ? val : 0L;
    }

    /** Save elapsed seconds for a package */
    public void saveElapsedSeconds(String packageName, long seconds) {
        Map<String, Long> map = loadUsageMap();
        map.put(packageName, seconds);
        saveUsageMap(map);
    }

    /** Add seconds to existing usage for a package */
    public void addElapsedSeconds(String packageName, long secondsToAdd) {
        long current = getElapsedSeconds(packageName);
        saveElapsedSeconds(packageName, current + secondsToAdd);
    }

    /** Get total usage across ALL tracked apps today */
    public long getTotalElapsedSeconds() {
        Map<String, Long> map = loadUsageMap();
        long total = 0;
        for (long val : map.values()) total += val;
        return total;
    }

    // ─── Limit Settings ────────────────────────────────────────────────────────

    /** Set per-app limit in seconds. 0 = no limit */
    public void setAppLimitSeconds(String packageName, long limitSeconds) {
        limitsPrefs.edit().putLong("limit_" + packageName, limitSeconds).apply();
    }

    /** Get per-app limit in seconds. Returns 0 if no limit set */
    public long getAppLimitSeconds(String packageName) {
        return limitsPrefs.getLong("limit_" + packageName, 0L);
    }

    /** Set overall daily limit across all apps in seconds. 0 = no limit */
    public void setOverallLimitSeconds(long limitSeconds) {
        limitsPrefs.edit().putLong(KEY_OVERALL_LIMIT_SECONDS, limitSeconds).apply();
    }

    /** Get overall daily limit. Returns 0 if not set */
    public long getOverallLimitSeconds() {
        return limitsPrefs.getLong(KEY_OVERALL_LIMIT_SECONDS, 0L);
    }

    // ─── Monitored Apps ────────────────────────────────────────────────────────

    /** Save the set of apps to monitor (JSON array of package names) */
    public void setMonitoredApps(java.util.Set<String> packages) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String p : packages) arr.put(p);
        limitsPrefs.edit().putString("monitored_apps", arr.toString()).apply();
    }

    /** Get the set of apps to monitor */
    public java.util.Set<String> getMonitoredApps() {
        java.util.Set<String> result = new java.util.HashSet<>();
        String json = limitsPrefs.getString("monitored_apps", "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) result.add(arr.getString(i));
        } catch (JSONException e) { /* ignore */ }
        return result;
    }

    /** Check if a package is being monitored */
    public boolean isMonitored(String packageName) {
        return getMonitoredApps().contains(packageName);
    }

    // ─── Limit Check ───────────────────────────────────────────────────────────

    public enum LimitStatus { OK, APP_LIMIT_REACHED, OVERALL_LIMIT_REACHED }

    public LimitStatus checkLimitStatus(String packageName) {
        long appLimit = getAppLimitSeconds(packageName);
        if (appLimit > 0 && getElapsedSeconds(packageName) >= appLimit) {
            return LimitStatus.APP_LIMIT_REACHED;
        }
        long overallLimit = getOverallLimitSeconds();
        if (overallLimit > 0 && getTotalElapsedSeconds() >= overallLimit) {
            return LimitStatus.OVERALL_LIMIT_REACHED;
        }
        return LimitStatus.OK;
    }

    // ─── JSON helpers ──────────────────────────────────────────────────────────

    private Map<String, Long> loadUsageMap() {
        Map<String, Long> map = new HashMap<>();
        String json = usagePrefs.getString(KEY_USAGE_MAP, "{}");
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, obj.getLong(k));
            }
        } catch (JSONException e) { /* return empty map */ }
        return map;
    }

    private void saveUsageMap(Map<String, Long> map) {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, Long> e : map.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
        } catch (JSONException e) { /* ignore */ }
        usagePrefs.edit().putString(KEY_USAGE_MAP, obj.toString()).apply();
    }

    // ─── Format helper ─────────────────────────────────────────────────────────

    public static String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long mins = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm", hours, mins);
        } else {
            return String.format("%d:%02d", mins, secs);
        }
    }
}
