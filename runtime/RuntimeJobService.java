package dev.poc.filemanager.p05executor;

// POC-ONLY — NOT PRODUCTION AUTHORITY

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import java.util.concurrent.locks.ReentrantLock;

public final class RuntimeJobService extends JobService {
    private static final class JobInvocation {
        final RuntimeEvidence evidence;
        final boolean uidt;
        final ReentrantLock lock = new ReentrantLock();
        volatile Thread worker;
        volatile boolean stopped;

        JobInvocation(RuntimeEvidence evidence, boolean uidt) {
            this.evidence = evidence;
            this.uidt = uidt;
        }
    }

    private final Object invocationLock = new Object();
    private volatile JobInvocation activeInvocation;
    static void schedule(Context context, boolean uidt) {
        JobInfo.Builder builder = new JobInfo.Builder(uidt ? 902 : 901,
            new ComponentName(context, RuntimeJobService.class));
        if (uidt && Build.VERSION.SDK_INT >= 34) {
            builder.setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(1L, 1L);
        } else {
            builder.setMinimumLatency(0).setOverrideDeadline(1000).setPersisted(false);
        }
        int result = context.getSystemService(JobScheduler.class).schedule(builder.build());
        new RuntimeEvidence(context).record("SCHEDULE", uidt ? "UIDT" : "JOB_SCHEDULER", Integer.toString(result), "schedule_return");
    }
    @Override public boolean onStartJob(JobParameters params) {
        boolean uidt = params.getJobId() == 902;
        JobInvocation invocation = new JobInvocation(new RuntimeEvidence(this), uidt);
        synchronized (invocationLock) {
            cancel(activeInvocation);
            activeInvocation = invocation;
        }
        invocation.worker = new Thread(() -> {
            invocation.evidence.run(uidt ? "UIDT" : "JOB_SCHEDULER", "job_started", probe(invocation));
            finishIfActive(invocation, params);
        }, "p05-job");
        invocation.worker.start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        JobInvocation invocation = activeInvocation;
        if (invocation != null) {
            cancel(invocation);
            invocation.evidence.record("JOB_STOP", invocation.uidt ? "UIDT" : "JOB_SCHEDULER", "RUNNING", "stopReason=" + params.getStopReason());
        }
        return true;
    }

    private boolean isActive(JobInvocation invocation) {
        invocation.lock.lock();
        try {
            return activeInvocation == invocation
                && !invocation.stopped
                && !Thread.currentThread().isInterrupted();
        } finally {
            invocation.lock.unlock();
        }
    }

    private void cancel(JobInvocation invocation) {
        if (invocation == null) return;
        Thread current;
        invocation.lock.lock();
        try {
            invocation.stopped = true;
            current = invocation.worker;
        } finally {
            invocation.lock.unlock();
        }
        if (current != null) current.interrupt();
    }

    private void finishIfActive(JobInvocation invocation, JobParameters params) {
        invocation.lock.lock();
        try {
            if (activeInvocation == invocation && !invocation.stopped && !Thread.currentThread().isInterrupted()) {
                jobFinished(params, false);
            }
        } finally {
            invocation.lock.unlock();
        }
    }

    private RuntimeEvidence.CancellationProbe probe(JobInvocation invocation) {
        return new RuntimeEvidence.CancellationProbe() {
            @Override public boolean isActive() { return RuntimeJobService.this.isActive(invocation); }

            @Override public RuntimeEvidence.FinalizationGuard beginFinalization() {
                invocation.lock.lock();
                if (activeInvocation != invocation || invocation.stopped || Thread.currentThread().isInterrupted()) {
                    invocation.lock.unlock();
                    return null;
                }
                return () -> invocation.lock.unlock();
            }
        };
    }
}
