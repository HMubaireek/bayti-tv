package sa.urtv.home.tilawah;

import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

/** For tests/prayer_math_test.py: PrayerMath.java (the times and their cache keys) on a plain JVM. Prints PASS/FAIL lines. */
public final class PrayerMathRun {
    static int failed;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    static String hm(long at, String tz) { return PrayerMath.clockOf(at, tz); }

    public static void main(String[] args) {
        // Riyadh, 23 October 2026: the five times come in order and Isha is 90 minutes after Maghrib
        long[] r = PrayerMath.compute(2026, 10, 23, 24.6877, 46.7219, "Asia/Riyadh", false);
        boolean ordered = true;
        for (int i = 1; i < 6; i++) ordered &= r[i] > r[i - 1];
        check("Riyadh: fajr < sunrise < dhuhr < asr < maghrib < isha", ordered, java.util.Arrays.toString(r));
        check("Riyadh: Fajr 04:3x, Dhuhr 11:2x-11:3x, Maghrib 17:0x-17:1x", hm(r[0], "Asia/Riyadh").startsWith("04:") && hm(r[2], "Asia/Riyadh").startsWith("11:")
                && hm(r[4], "Asia/Riyadh").startsWith("17:"), hm(r[0], "Asia/Riyadh") + " " + hm(r[2], "Asia/Riyadh") + " " + hm(r[4], "Asia/Riyadh"));
        check("Riyadh: Isha is Maghrib + 90 minutes", r[5] - r[4] == 90 * 60_000L, String.valueOf(r[5] - r[4]));
        long[] ram = PrayerMath.compute(2026, 10, 23, 24.6877, 46.7219, "Asia/Riyadh", true);
        check("Ramadan: Isha is Maghrib + 120 minutes", ram[5] - ram[4] == 120 * 60_000L, String.valueOf(ram[5] - ram[4]));

        // Oslo, midsummer: Maghrib at 22:4x, so Isha is after midnight: the next day's, not 00:1x of the day that began 24 hours before
        long[] o = PrayerMath.compute(2026, 6, 21, 59.91, 10.75, "Europe/Oslo", false);
        long day0 = PrayerMath.midnight(2026, 6, 21, "Europe/Oslo");
        check("Oslo midsummer: Maghrib is late evening", o[4] - day0 > 22 * 3600_000L && o[4] - day0 < 24 * 3600_000L, hm(o[4], "Europe/Oslo"));
        check("Oslo midsummer: Isha is after Maghrib, past midnight, a day-1 moment (not wrapped back into the morning)",
                o[5] > o[4] && o[5] - o[4] == 90 * 60_000L && o[5] - day0 >= 24 * 3600_000L, hm(o[5], "Europe/Oslo") + " +" + (o[5] - day0) / 60000 + " min");
        check("Oslo midsummer: its clock reads 00:xx", hm(o[5], "Europe/Oslo").startsWith("00:"), hm(o[5], "Europe/Oslo"));

        // «طريقة حساب المواقيت»: Al-Qatif, 25 October 2026. Umm al-Qura by name is the same as before; the Shia method (Jafari) waits for
        // Maghrib at 4° (some 15 minutes after sunset), puts Isha at 14° (no fixed delay) and Fajr at 16° (later than 18.5°)
        double qLat = 26.5654, qLng = 50.0089;
        long[] uq = PrayerMath.compute(2026, 10, 25, qLat, qLng, "Asia/Riyadh", false);
        long[] uqByName = PrayerMath.compute(2026, 10, 25, qLat, qLng, "Asia/Riyadh", false, PrayerMath.Method.of("umm_alqura"));
        check("method: umm_alqura by name = the old default", java.util.Arrays.equals(uq, uqByName), java.util.Arrays.toString(uqByName));
        check("method: an unknown key is Umm al-Qura", PrayerMath.Method.of("nope") == PrayerMath.Method.UMM_ALQURA
                && PrayerMath.Method.of(null) == PrayerMath.Method.UMM_ALQURA, "");
        long[] ja = PrayerMath.compute(2026, 10, 25, qLat, qLng, "Asia/Riyadh", false, PrayerMath.Method.of("jafari"));
        boolean jaOrdered = true;
        for (int i = 1; i < 6; i++) jaOrdered &= ja[i] > ja[i - 1];
        check("jafari: the six come in order", jaOrdered, java.util.Arrays.toString(ja));
        long late = (ja[4] - uq[4]) / 60_000L;
        check("jafari: Maghrib 12-20 minutes after sunset", late >= 12 && late <= 20, late + " min");
        check("jafari: Fajr later than Umm al-Qura's", ja[0] > uq[0], hm(ja[0], "Asia/Riyadh") + " vs " + hm(uq[0], "Asia/Riyadh"));
        long ishaGap = (ja[5] - ja[4]) / 60_000L;
        check("jafari: Isha by its angle, 45-75 minutes after its Maghrib (not a fixed 90)", ishaGap >= 45 && ishaGap <= 75, ishaGap + " min");
        check("jafari: Dhuhr and Asr as Umm al-Qura", ja[2] == uq[2] && ja[3] == uq[3], hm(ja[2], "Asia/Riyadh") + " " + hm(ja[3], "Asia/Riyadh"));
        long[] mwl = PrayerMath.compute(2026, 10, 25, qLat, qLng, "Asia/Riyadh", true, PrayerMath.Method.of("mwl"));
        check("mwl: no Ramadan delay (Isha by angle)", mwl[5] - mwl[4] != 120 * 60_000L && mwl[5] > mwl[4], String.valueOf((mwl[5] - mwl[4]) / 60000));
        long[] gulfRam = PrayerMath.compute(2026, 10, 25, qLat, qLng, "Asia/Riyadh", true, PrayerMath.Method.of("gulf"));
        check("gulf: Isha 90 minutes after Maghrib, also in Ramadan", gulfRam[5] - gulfRam[4] == 90 * 60_000L, String.valueOf((gulfRam[5] - gulfRam[4]) / 60000));
        long[] oJa = PrayerMath.compute(2026, 6, 21, 59.91, 10.75, "Europe/Oslo", false, PrayerMath.Method.of("jafari"));
        check("jafari, Oslo midsummer: Isha (14° never reached) is not before Maghrib", oJa[5] >= oJa[4], hm(oJa[4], "Europe/Oslo") + " " + hm(oJa[5], "Europe/Oslo"));
        long kt = Instant.parse("2026-10-25T09:00:00Z").toEpochMilli();
        check("key: Umm al-Qura keeps its old key, another method is another key",
                PrayerMath.key(kt, qLat, qLng, "Asia/Riyadh", PrayerMath.Method.UMM_ALQURA).equals(PrayerMath.key(kt, qLat, qLng, "Asia/Riyadh"))
                && !PrayerMath.key(kt, qLat, qLng, "Asia/Riyadh", PrayerMath.Method.of("jafari")).equals(PrayerMath.key(kt, qLat, qLng, "Asia/Riyadh")), "");

        // the cache key and Aladhan's date go by the city's zone and name the city
        long t = Instant.parse("2026-10-23T21:30:00Z").toEpochMilli();   // Saturday 00:30 in Riyadh, Friday evening in New York
        check("key: Riyadh's date, not the TV's", PrayerMath.key(t, 24.6877, 46.7219, "Asia/Riyadh").startsWith("times_20261024_"), PrayerMath.key(t, 24.6877, 46.7219, "Asia/Riyadh"));
        check("key: New York's date", PrayerMath.key(t, 40.71, -74.0, "America/New_York").startsWith("times_20261023_"), PrayerMath.key(t, 40.71, -74.0, "America/New_York"));
        check("key: another city the same day is another key", !PrayerMath.key(t, 24.6877, 46.7219, "Asia/Riyadh").equals(PrayerMath.key(t, 21.4858, 39.1925, "Asia/Riyadh")), "same");
        check("key: the same city is the same key", PrayerMath.key(t, 24.6877, 46.7219, "Asia/Riyadh").equals(PrayerMath.key(t + 3600_000L, 24.6877, 46.7219, "Asia/Riyadh")), "differs");
        check("key: the same instant in another zone of the same place is another key", !PrayerMath.key(t, 24.6877, 46.7219, "Asia/Riyadh").equals(PrayerMath.key(t, 24.6877, 46.7219, "Asia/Kuwait")), "same");
        check("Aladhan's date is the city's day: 24-10-2026 for Riyadh, 23-10-2026 for New York",
                PrayerMath.aladhanDate(t, "Asia/Riyadh").equals("24-10-2026") && PrayerMath.aladhanDate(t, "America/New_York").equals("23-10-2026"),
                PrayerMath.aladhanDate(t, "Asia/Riyadh") + " " + PrayerMath.aladhanDate(t, "America/New_York"));
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));   // the TV's own zone must not leak in
        check("key and date ignore the machine's zone", PrayerMath.aladhanDate(t, "Asia/Riyadh").equals("24-10-2026")
                && PrayerMath.key(t, 24.6877, 46.7219, "Asia/Riyadh").startsWith("times_20261024_"), PrayerMath.aladhanDate(t, "Asia/Riyadh"));

        // Aladhan's «5:12 (+03)» must not crash the receiver
        check("parse: 05:12", ok(PrayerMath.parseClock("05:12"), 5, 12), "");
        check("parse: 5:12", ok(PrayerMath.parseClock("5:12"), 5, 12), "");
        check("parse: «5:12 (+03)»", ok(PrayerMath.parseClock("5:12 (+03)"), 5, 12), "");
        check("parse: «17:42 (AST)»", ok(PrayerMath.parseClock("17:42 (AST)"), 17, 42), "");
        check("parse: not a time -> null, no exception", PrayerMath.parseClock("--") == null && PrayerMath.parseClock("") == null
                && PrayerMath.parseClock(null) == null && PrayerMath.parseClock("24:00") == null && PrayerMath.parseClock("12:7") == null && PrayerMath.parseClock("x5:12") == null, "");
        System.exit(failed == 0 ? 0 : 1);
    }

    static boolean ok(int[] hm, int h, int m) { return hm != null && hm[0] == h && hm[1] == m; }
}
