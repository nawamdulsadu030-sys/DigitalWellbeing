package com.nawam.digitalwellbeing.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nawam.digitalwellbeing.R;
import com.nawam.digitalwellbeing.db.AppUsageData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows a list of all installed apps.
 * User checks which apps to monitor with floating timer + limits.
 */
public class AppSelectorActivity extends AppCompatActivity {

    private ListView listView;
    private AppUsageData usageData;
    private List<AppInfo> appList = new ArrayList<>();
    private Set<String> selectedPackages = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selector);

        usageData = new AppUsageData(this);
        selectedPackages = new HashSet<>(usageData.getMonitoredApps());

        listView = findViewById(R.id.list_apps);
        Button btnSave = findViewById(R.id.btn_save);

        btnSave.setOnClickListener(v -> {
            usageData.setMonitoredApps(selectedPackages);
            Toast.makeText(this, selectedPackages.size() + " apps selected", Toast.LENGTH_SHORT).show();
            finish();
        });

        loadApps();
    }

    private void loadApps() {
        // Load apps in background
        new AsyncTask<Void, Void, List<AppInfo>>() {
            @Override
            protected List<AppInfo> doInBackground(Void... v) {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                List<AppInfo> result = new ArrayList<>();

                for (ApplicationInfo info : installed) {
                    // Only show user-installed apps and launchable apps
                    boolean isLaunchable = pm.getLaunchIntentForPackage(info.packageName) != null;
                    boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    if (isLaunchable && !info.packageName.equals(getPackageName())) {
                        String label = pm.getApplicationLabel(info).toString();
                        result.add(new AppInfo(label, info.packageName));
                    }
                }
                Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
                return result;
            }

            @Override
            protected void onPostExecute(List<AppInfo> result) {
                appList = result;
                setupList();
            }
        }.execute();
    }

    private void setupList() {
        String[] labels = new String[appList.size()];
        boolean[] checked = new boolean[appList.size()];

        for (int i = 0; i < appList.size(); i++) {
            labels[i] = appList.get(i).label;
            checked[i] = selectedPackages.contains(appList.get(i).packageName);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_multiple_choice, labels);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        for (int i = 0; i < checked.length; i++) {
            listView.setItemChecked(i, checked[i]);
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = appList.get(position).packageName;
            if (selectedPackages.contains(pkg)) {
                selectedPackages.remove(pkg);
            } else {
                selectedPackages.add(pkg);
            }
        });
    }

    static class AppInfo {
        String label, packageName;
        AppInfo(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }
}
