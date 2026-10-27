package sa.bayti.home;

import android.content.Context;

import sa.bayti.home.tilawah.DayParts;

/**
 * The slim bar at the top of the home screen before the adhan: «الأذان بعد ٨ دقائق» in the last 10 minutes before each
 * prayer, «الإقامة بعد ١٢ دقيقة» from the adhan to the iqama. The times are the adhan's own (DayParts), the words
 * AdhanBarText's; «تنبيه قبل الأذان» in the screensaver's settings turns it off (its ConfigProvider, same signing key).
 */
final class AdhanBar {
    private AdhanBar() { }

    /** What the bar says now, or null: it is off, nothing is near, or the times are out of reach. */
    static String text(Context c, ConfigSnapshot snap) {
        if (!snap.adhanBarOn) return null;
        try {
            DayParts d = DayParts.now(c, snap.fragment);
            return AdhanBarText.text(System.currentTimeMillis(), new long[]{d.fajr, d.dhuhr, d.hideAsr ? 0 : d.asr, d.maghrib, d.hideIsha ? 0 : d.isha}, d.friday, snap.latin());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
