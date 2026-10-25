package sa.bayti.home.tilawah;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The adhan alarm was set for the prayer times as they were. When «تعديل وقت الصلاة» changes (on the TV's screen or from the
 * shell) the alarm has to be set again, or it rings at the old minute. The home screen calls this once a minute with the
 * screensaver's settings it has read anyway; the shell's {@code ADHAN_RESCHEDULE} does the same at once.
 */
public final class AdhanSync {
    private AdhanSync() { }

    /** Sets the alarm again when the adjustments in this fragment are not the ones it was last set with. */
    public static void onConfig(Context c, String fragment) {
        String now = PrayerAdjust.text(PrayerAdjust.fromFragment(fragment));
        SharedPreferences sp = c.getSharedPreferences("tilawah", Context.MODE_PRIVATE);
        String was = sp.getString("adj_scheduled", "0,0,0,0,0");
        if (now.equals(was)) return;
        sp.edit().putString("adj_scheduled", now).apply();
        AdhanScheduler.schedule(c);
    }

    /** The shell asks: whatever the settings say now, set the alarm for it. */
    static void reschedule(Context c) {
        SharedPreferences sp = c.getSharedPreferences("tilawah", Context.MODE_PRIVATE);
        Place p = Place.get(c);
        sp.edit().putString("adj_scheduled", PrayerAdjust.text(p.adj)).apply();
        AdhanScheduler.schedule(c);
    }
}
