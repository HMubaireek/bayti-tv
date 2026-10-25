package sa.bayti.screensaver;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Wakes the app's process, and does nothing else. The job is persisted, so after a reboot the system
 * itself starts the process again, which some TVs (TCL) need for the first screensaver to start.
 * The page and version check no longer runs here: it runs when the screensaver starts
 * (Updater.checkIfDue), so the TV does no network work while it is idle.
 */
public class UpdateJob extends JobService {
    private static final int ID = 1;

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == ID) return;
        js.schedule(new JobInfo.Builder(ID, new ComponentName(c, UpdateJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(Updater.INTERVAL_MS)
            .setPersisted(true)
            .build());
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        return false;   // nothing to do: the process is up, and the job is finished
    }

    @Override
    public boolean onStopJob(JobParameters p) { return true; }
}
