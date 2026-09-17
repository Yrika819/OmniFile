package dev.poc.filemanager.p05executor;

// POC-ONLY — NOT PRODUCTION AUTHORITY

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private RuntimeEvidence evidence;
    private boolean recoveryMode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        evidence = new RuntimeEvidence(this);
        setContentView(new TextView(this));
        String mode = getIntent().getStringExtra("mode");
        if (mode == null) return;
        recoveryMode = "resume".equals(mode);
        evidence.record("ACTIVITY_CREATE", mode, "RUNNING", "foreground_activity");
        if ("foreground".equals(mode)) {
            new Thread(() -> evidence.run("FOREGROUND_APP", "foreground_activity"), "p05-foreground").start();
        } else if ("fgs".equals(mode)) {
            Intent i = new Intent(this, RuntimeService.class).putExtra("executor", "FOREGROUND_SERVICE");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } else if ("job".equals(mode) || "uidt".equals(mode)) {
            RuntimeJobService.schedule(this, "uidt".equals(mode));
        } else if ("resume".equals(mode)) {
            new Thread(() -> evidence.recover(), "p05-recover").start();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (evidence != null && !recoveryMode) evidence.setLifecycle("foreground_activity");
    }

    @Override protected void onStop() {
        if (evidence != null) {
            if (!recoveryMode) evidence.setLifecycle("activity_not_foreground");
            evidence.record("ACTIVITY_STOP", "lifecycle", "OBSERVED", "activity_not_foreground");
        }
        super.onStop();
    }
}
