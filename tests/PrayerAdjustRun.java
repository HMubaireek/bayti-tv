package sa.bayti.home.tilawah;

import java.util.Arrays;

/** For tests/prayer_adj_test.py: the launcher's side of «تعديل وقت الصلاة» (PrayerAdjust.java, with PrayerMath.java for the times and the clock text) on a plain JVM. */
public final class PrayerAdjustRun {
    static int failed;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    static String hm(long at, String tz) { return PrayerMath.clockOf(at, tz); }

    public static void main(String[] args) {
        check("parse: five whole numbers", Arrays.equals(PrayerAdjust.parse("0,2,0,0,-1"), new int[]{0, 2, 0, 0, -1}), "");
        check("parse: the edges −20 and +20 stay", Arrays.equals(PrayerAdjust.parse("-20,20,-20,20,0"), new int[]{-20, 20, -20, 20, 0}), "");
        check("parse: beyond the edges is held to them", Arrays.equals(PrayerAdjust.parse("-99,99,21,-21,20"), new int[]{-20, 20, 20, -20, 20}), "");
        check("parse: four numbers, six numbers, empty, null are no change",
                Arrays.equals(PrayerAdjust.parse("1,2,3,4"), new int[5]) && Arrays.equals(PrayerAdjust.parse("1,2,3,4,5,6"), new int[5])
                && Arrays.equals(PrayerAdjust.parse(""), new int[5]) && Arrays.equals(PrayerAdjust.parse(null), new int[5]), "");
        check("parse: a fraction, a plus sign, letters, a space inside are no change",
                Arrays.equals(PrayerAdjust.parse("1.5,0,0,0,0"), new int[5]) && Arrays.equals(PrayerAdjust.parse("+2,0,0,0,0"), new int[5])
                && Arrays.equals(PrayerAdjust.parse("a,0,0,0,0"), new int[5]) && Arrays.equals(PrayerAdjust.parse("1 2,0,0,0,0"), new int[5]), "");
        check("fragment: adj= is found among the other settings, and only whole",
                "0,2,0,0,-1".equals(PrayerAdjust.of("city=%D8%A7&tz=Asia%2FRiyadh&adj=0,2,0,0,-1&digits=latin")) && PrayerAdjust.of("city=x&digits=latin") == null
                && PrayerAdjust.of("badj=1,1,1,1,1") == null && PrayerAdjust.of(null) == null, "");
        check("fragment: the setting off (no adj=) is zeros", Arrays.equals(PrayerAdjust.fromFragment("city=x&tz=Asia/Riyadh"), new int[5]), "");

        // Riyadh, Thursday 22 October 2026: the times, and the same moved
        long[] raw = PrayerMath.compute(2026, 10, 22, 24.6877, 46.7219, "Asia/Riyadh", false);
        long[] moved = raw.clone();
        PrayerAdjust.shift(moved, new int[]{0, 2, 0, 0, -1});
        check("shift: Dhuhr +2 and Isha −1 minute, exactly", moved[2] - raw[2] == 2 * 60_000L && moved[5] - raw[5] == -60_000L, Arrays.toString(moved));
        check("shift: Fajr, Asr and Maghrib (0) and the sunrise stay", moved[0] == raw[0] && moved[3] == raw[3] && moved[4] == raw[4] && moved[1] == raw[1], "");
        long[] sun = raw.clone();
        PrayerAdjust.shift(sun, new int[]{20, 20, 20, 20, 20});
        check("shift: the sunrise never moves, whatever the others do", sun[1] == raw[1], "");
        long[] edge = raw.clone();
        PrayerAdjust.shift(edge, new int[]{-20, 20, -20, 20, -20});
        check("shift: −20 / +20 at the edges",
                edge[0] == raw[0] - 20 * 60_000L && edge[2] == raw[2] + 20 * 60_000L && edge[3] == raw[3] - 20 * 60_000L && edge[4] == raw[4] + 20 * 60_000L
                && edge[5] == raw[5] - 20 * 60_000L, Arrays.toString(edge));
        check("shift: even at the edges Isha (−20) stays after Maghrib (+20): 90 − 40 minutes", edge[5] > edge[4], "");
        long[] none = raw.clone();
        PrayerAdjust.shift(none, new int[5]);
        check("shift: zeros change nothing", Arrays.equals(none, raw), "");

        // Oslo midsummer: Isha is past midnight; moved by +20 it is still the same kind of instant, and its clock text wraps to 00:xx
        long[] o = PrayerMath.compute(2026, 6, 21, 59.91, 10.75, "Europe/Oslo", false);
        long day0 = PrayerMath.midnight(2026, 6, 21, "Europe/Oslo");
        long[] om = o.clone();
        PrayerAdjust.shift(om, new int[]{0, 0, 0, 0, 20});
        check("midnight: Isha +20 after midnight is 20 minutes later, still after Maghrib, still the next day's instant",
                om[5] - o[5] == 20 * 60_000L && om[5] > om[4] && om[5] - day0 >= 24 * 3600_000L, hm(o[5], "Europe/Oslo") + " -> " + hm(om[5], "Europe/Oslo"));
        long[] om2 = o.clone();
        PrayerAdjust.shift(om2, new int[]{0, 0, 0, 0, -20});
        check("midnight: Isha −20 may come back before midnight: the clock reads 23:xx and it is the same day's instant",
                om2[5] - day0 < 24 * 3600_000L ? hm(om2[5], "Europe/Oslo").startsWith("23:") : hm(om2[5], "Europe/Oslo").startsWith("00:"), hm(om2[5], "Europe/Oslo"));
        // Riyadh Isha at 18:xx +20: nothing odd; Friday 23 October: Dhuhr +20 is a normal moment
        long[] fri = PrayerMath.compute(2026, 10, 23, 24.6877, 46.7219, "Asia/Riyadh", false);
        long[] fm = fri.clone();
        PrayerAdjust.shift(fm, new int[]{0, 20, 0, 0, 0});
        check("Friday: Dhuhr +20 is a plain 20 minutes later", fm[2] - fri[2] == 20 * 60_000L && hm(fm[2], "Asia/Riyadh").compareTo(hm(fri[2], "Asia/Riyadh")) > 0, hm(fri[2], "Asia/Riyadh") + " -> " + hm(fm[2], "Asia/Riyadh"));
        check("text: for the cache of the day, a change of the minutes is a different text", !PrayerAdjust.text(new int[]{0, 2, 0, 0, -1}).equals(PrayerAdjust.text(new int[5])), "");
        System.exit(failed == 0 ? 0 : 1);
    }
}
