package sa.bayti.home;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The digits of this app's screens (Java UI): the screensaver's «الأرقام» setting, hindi or latin, read the way the launcher reads its other
 * settings (ConfigProvider, through ConfigSnapshot) and kept for the next start, so a screen opened alone (a «تلاوة وأذان» one) has it too.
 * Every number a screen shows goes through of(); the conversion itself is DigitsText.
 */
public final class Digits {
    private static final String PREFS = "urtv_digits";
    private static volatile boolean latin;

    private Digits() {}

    public static boolean latin() { return latin; }

    /** a screen's start: the last known setting at once, then what the screensaver says now */
    public static void init(Context c) {
        SharedPreferences sp = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        latin = sp.getBoolean("latin", false);
        remember(c, ConfigSnapshot.read(c));
    }

    /** the home screen's minute tick already reads the snapshot: it passes it on */
    static void remember(Context c, ConfigSnapshot snap) {
        if (snap == null || snap.fragment == null) return;   // the screensaver is missing: what was known stays
        boolean now = snap.latin();
        if (now == latin) return;
        latin = now;
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("latin", now).apply();
    }

    /** a text with the setting's digits */
    public static String of(String s) { return DigitsText.convert(s, latin); }
}
