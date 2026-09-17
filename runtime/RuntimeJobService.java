package dev.poc.filemanager.p05executor;

// POC-ONLY — NOT PRODUCTION AUTHORITY

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

public final class RuntimeJobService extends JobService {
    private RuntimeEvidence evidence;
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
        evidence = new RuntimeEvidence(this);
        boolean uidt = params.getJobId() == 902;
        new Thread(() -> {
            evidence.run(uidt ? "UIDT" : "JOB_SCHEDULER", "job_started");
            jobFinished(params, false);
        }, "p05-job").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        if (evidence != null) evidence.record("JOB_STOP", params.getJobId() == 902 ? "UIDT" : "JOB_SCHEDULER", "RUNNING", "stopReason=" + params.getStopReason());
        return true;
    }
}
