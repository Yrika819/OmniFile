package dev.poc.filemanager.p05executor;

// POC-ONLY — NOT PRODUCTION AUTHORITY

import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

final class RuntimeEvidence {
    interface FinalizationGuard { void close(); }
    interface CancellationProbe {
        boolean isActive();
        FinalizationGuard beginFinalization();
    }

    private static final CancellationProbe ALWAYS_ACTIVE = new CancellationProbe() {
        @Override public boolean isActive() { return true; }
        @Override public FinalizationGuard beginFinalization() { return () -> { }; }
    };
    private final Context context;
    private final File report;
    private final File state;
    private volatile String lifecycleOverride;

    RuntimeEvidence(Context context) {
        this.context = context.getApplicationContext();
        // The target ROM's seapp context table rejects run-as for this manually
        // signed disposable package. External app files remain fixture-scoped
        // and are readable by the shell collector without weakening app policy.
        File dir = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
            ? this.context.getFilesDir()
            : this.context.getExternalFilesDir(null);
        if (dir == null || !dir.exists()) {
            // Some API31 emulator boots leave emulated storage unavailable;
            // retain the same evidence contract in app-private storage so the
            // emulator run can be collected with run-as.
            dir = this.context.getFilesDir();
        }
        report = new File(dir, "runtime-report.jsonl");
        state = new File(dir, "runtime-state.properties");
    }

    synchronized void record(String event, String executor, String durableState, String lifecycle) {
        try (FileOutputStream out = new FileOutputStream(report, true)) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("ts", Long.toString(System.currentTimeMillis()));
            values.put("sdkInt", Integer.toString(Build.VERSION.SDK_INT));
            values.put("model", Build.MODEL);
            values.put("build", Build.DISPLAY);
            values.put("pid", Integer.toString(Process.myPid()));
            values.put("event", event);
            values.put("executor", executor);
            values.put("durableState", durableState);
            values.put("lifecycle", lifecycle);
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (!first) json.append(',');
                first = false;
                json.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
            }
            out.write((json.append("}\n").toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException ignored) {
            throw new IllegalStateException("runtime evidence append failed", ignored);
        }
    }

    void setLifecycle(String lifecycle) {
        lifecycleOverride = lifecycle;
    }

    private String lifecycle(String fallback) {
        String value = lifecycleOverride;
        return value == null ? fallback : value;
    }

    void run(String executor, String lifecycle) {
        run(executor, lifecycle, ALWAYS_ACTIVE);
    }

    void run(String executor, String lifecycle, CancellationProbe probe) {
        Properties p = load();
        if (!probe.isActive()) return;
        int start = Integer.parseInt(p.getProperty("completedUnits", "0"));
        p.setProperty("executor", executor);
        p.setProperty("phase", "RUNNING");
        save(p);
        if (!probe.isActive()) return;
        record("START", executor, "RUNNING", lifecycle(lifecycle));
        for (int unit = start; unit < 20; unit++) {
            try { Thread.sleep(150L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            if (!probe.isActive()) return;
            p.setProperty("completedUnits", Integer.toString(unit + 1));
            save(p);
            if (!probe.isActive()) return;
            record("CHECKPOINT", executor, "RUNNING", lifecycle(lifecycle));
        }
        FinalizationGuard guard = probe.beginFinalization();
        if (guard == null) return;
        try {
            p.setProperty("phase", "COMPLETE");
            save(p);
            record("COMPLETE", executor, "COMPLETE", lifecycle(lifecycle));
        } finally {
            guard.close();
        }
    }

    void recover() {
        Properties p = load();
        String phase = p.getProperty("phase", "ABSENT");
        String executor = p.getProperty("executor", "RECOVERY");
        record("REDISCOVER", executor, phase, "app_restart");
        if (!"COMPLETE".equals(phase)) {
            setLifecycle("app_restart_recreated_executor");
            run(executor, "app_restart_recreated_executor");
        }
    }

    private Properties load() {
        Properties p = new Properties();
        if (!state.exists()) return p;
        try (FileInputStream in = new FileInputStream(state)) {
            p.load(in);
        } catch (IOException failure) {
            throw new IllegalStateException("durable state read failed", failure);
        }
        return p;
    }

    private synchronized void save(Properties p) {
        File tmp = new File(state.getParentFile(), state.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, "POC-ONLY — NOT PRODUCTION AUTHORITY");
            out.getFD().sync();
            if (!tmp.renameTo(state)) {
                tmp.delete();
                throw new IOException("atomic state replace failed: " + state);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("durable state write failed", failure);
        }
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
