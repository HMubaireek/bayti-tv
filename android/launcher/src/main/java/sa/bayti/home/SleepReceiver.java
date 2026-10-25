package sa.bayti.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The sleep timer's alarm went off (SleepTimer set it): the TV goes to sleep. Not exported: only our own alarm reaches it. */
public class SleepReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (SleepTimer.ACTION.equals(i.getAction())) SleepTimer.fire(c);
    }
}
