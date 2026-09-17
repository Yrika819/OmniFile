package dev.poc.filemanager.p05executor;

// POC-ONLY — NOT PRODUCTION AUTHORITY

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class RuntimeService extends Service {
    private RuntimeEvidence evidence;
    @Override public void onCreate() {
        super.onCreate();
        evidence = new RuntimeEvidence(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("p05", "P05 runtime", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, "p05") : new Notification.Builder(this);
        builder.setContentTitle("P05 executor runtime").setContentText("POC transfer running").setSmallIcon(android.R.drawable.ic_menu_upload);
        startForeground(42, builder.build());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> { evidence.run("FOREGROUND_SERVICE", "service_started"); stopSelfResult(startId); }, "p05-fgs").start();
        return START_NOT_STICKY;
    }
    @Override public void onTaskRemoved(Intent rootIntent) { evidence.record("TASK_REMOVED", "FOREGROUND_SERVICE", "RUNNING", "task_swipe_observed"); super.onTaskRemoved(rootIntent); }
    @Override public void onDestroy() { if (evidence != null) evidence.record("SERVICE_DESTROY", "FOREGROUND_SERVICE", "RUNNING", "service_destroyed"); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
