package sa.bayti.screensaver;

import java.util.Arrays;

/** For tests/prayer_adj_test.py: the screensaver's side of «تعديل وقت الصلاة» (PrayerAdj.java) on a plain JVM. */
public final class PrayerAdjRun {
    static int failed;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    public static void main(String[] args) {
        check("receiver: +2, 2, -1, 0, +20, -20 are accepted",
                PrayerAdj.parse("+2") == 2 && PrayerAdj.parse("2") == 2 && PrayerAdj.parse("-1") == -1 && PrayerAdj.parse("0") == 0
                && PrayerAdj.parse("+20") == 20 && PrayerAdj.parse("-20") == -20, "");
        check("receiver: +21, -21, 100, 1.5, abc, '', ' 2', '+', '--1', '+-1', '٢' are refused",
                PrayerAdj.parse("+21") == null && PrayerAdj.parse("-21") == null && PrayerAdj.parse("100") == null && PrayerAdj.parse("1.5") == null
                && PrayerAdj.parse("abc") == null && PrayerAdj.parse("") == null && PrayerAdj.parse(" 2") == null && PrayerAdj.parse("+") == null
                && PrayerAdj.parse("--1") == null && PrayerAdj.parse("+-1") == null && PrayerAdj.parse("٢") == null && PrayerAdj.parse(null) == null, "");
        check("saved text: five numbers come back, anything else is no change, each held to −20..20",
                Arrays.equals(PrayerAdj.decode("0,2,0,0,-1"), new int[]{0, 2, 0, 0, -1}) && Arrays.equals(PrayerAdj.decode("1,2,3"), new int[5])
                && Arrays.equals(PrayerAdj.decode("99,0,0,0,0"), new int[5]) && Arrays.equals(PrayerAdj.decode(""), new int[5]) && Arrays.equals(PrayerAdj.decode(null), new int[5]), "");
        check("saved text: encode and decode agree", PrayerAdj.encode(new int[]{-20, 20, 0, 5, -5}).equals("-20,20,0,5,-5") && Arrays.equals(PrayerAdj.decode("-20,20,0,5,-5"), new int[]{-20, 20, 0, 5, -5}), "");
        check("keys: + / − stop at −20 and +20", PrayerAdj.step(19, 1) == 20 && PrayerAdj.step(20, 1) == 20 && PrayerAdj.step(-19, -1) == -20 && PrayerAdj.step(-20, -1) == -20 && PrayerAdj.step(0, -1) == -1, "");
        check("fragment: nothing while off, nothing while every offset is zero, adj= while on",
                PrayerAdj.fragment(false, new int[]{0, 2, 0, 0, -1}).isEmpty() && PrayerAdj.fragment(true, new int[5]).isEmpty()
                && PrayerAdj.fragment(true, new int[]{0, 2, 0, 0, -1}).equals("&adj=0,2,0,0,-1"), PrayerAdj.fragment(true, new int[]{0, 2, 0, 0, -1}));
        check("label: «الفجر +٢ د», «العشاء −١ د», «الظهر ٠ د», «المغرب +٢٠ د»",
                PrayerAdj.label(0, 2).equals("الفجر +٢ د") && PrayerAdj.label(4, -1).equals("العشاء −١ د") && PrayerAdj.label(1, 0).equals("الظهر ٠ د")
                && PrayerAdj.label(3, 20).equals("المغرب +٢٠ د"), PrayerAdj.label(0, 2) + " | " + PrayerAdj.label(4, -1));
        check("show: for the agent", PrayerAdj.describe(new int[]{2, 0, 0, -1, 0}).equals("fajr=+2 dhuhr=0 asr=0 maghrib=-1 isha=0"), PrayerAdj.describe(new int[]{2, 0, 0, -1, 0}));
        System.exit(failed == 0 ? 0 : 1);
    }
}
