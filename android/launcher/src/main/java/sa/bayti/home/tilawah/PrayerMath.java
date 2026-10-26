package sa.bayti.home.tilawah;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arithmetic of the prayer times and of their cache, with nothing of Android in it, so a plain JVM can run it
 * (tests/PrayerMathRun.java). PrayerTimes holds the rest: the place, the preferences, Aladhan.
 */
final class PrayerMath {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    private static final double RAD = Math.PI / 180;
    private static final Pattern CLOCK = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})");

    private PrayerMath() {}

    /**
     * How the times are calculated (the screensaver's «طريقة حساب المواقيت», #method= in its fragment): Aladhan's method number, the
     * sun's angle for Fajr, Isha either at an angle or minutes after Maghrib, and Maghrib's angle (.833 = sunset; the Shia methods wait
     * for the redness to go). The same table as the page's PRAYER_METHODS.
     */
    static final class Method {
        final String key;
        final int aladhan;
        final double fajr, isha, maghrib;
        /** minutes after Maghrib, and in Ramadan; 0 = Isha has its own angle */
        final int ishaMin, ramadanMin;

        private Method(String key, int aladhan, double fajr, double isha, int ishaMin, int ramadanMin, double maghrib) {
            this.key = key; this.aladhan = aladhan; this.fajr = fajr; this.isha = isha;
            this.ishaMin = ishaMin; this.ramadanMin = ramadanMin; this.maghrib = maghrib;
        }

        /** Isha is a delay after Maghrib (Umm al-Qura, Gulf, Qatar), not an angle of its own */
        boolean ishaIsDelay() { return ishaMin > 0; }

        static final Method UMM_ALQURA = new Method("umm_alqura", 4, 18.5, 0, 90, 120, .833);
        static final Method[] ALL = {
            UMM_ALQURA,
            new Method("jafari", 0, 16, 14, 0, 0, 4),
            new Method("tehran", 7, 17.7, 14, 0, 0, 4.5),
            new Method("gulf", 8, 19.5, 0, 90, 0, .833),
            new Method("kuwait", 9, 18, 17.5, 0, 0, .833),
            new Method("qatar", 10, 18, 0, 90, 0, .833),
            new Method("dubai", 16, 18.2, 18.2, 0, 0, .833),
            new Method("mwl", 3, 18, 17, 0, 0, .833),
            new Method("egypt", 5, 19.5, 17.5, 0, 0, .833),
            new Method("karachi", 1, 18, 18, 0, 0, .833),
            new Method("isna", 2, 15, 15, 0, 0, .833),
        };

        /** the method of that key; anything else is Umm al-Qura, what it always used */
        static Method of(String key) {
            for (Method m : ALL) if (m.key.equals(key)) return m;
            return UMM_ALQURA;
        }
    }

    /** Isha comes this long after Maghrib (Umm al-Qura): 90 minutes, 120 in Ramadan. */
    static long ishaDelayMillis(boolean ramadan) { return ishaDelayMillis(Method.UMM_ALQURA, ramadan); }

    /** The same for a method whose Isha is a delay; a method with an Isha angle has none. */
    static long ishaDelayMillis(Method m, boolean ramadan) {
        return (ramadan && m.ramadanMin > 0 ? m.ramadanMin : m.ishaMin) * 60_000L;
    }

    /** Local midnight of that day in that zone. */
    static long midnight(int y, int mo, int d, String tz) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US);
        c.clear();
        c.set(y, mo - 1, d, 0, 0, 0);
        return c.getTimeInMillis();
    }

    /**
     * The six times of that local day (Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha) as moments. Isha is an absolute
     * time, Maghrib plus its delay: at a high latitude, where Maghrib is late, it falls after midnight, on the next
     * day, instead of wrapping back to an hour of this day that has already gone.
     */
    static long[] compute(int y, int mo, int d, double lat, double lng, String tz, boolean ramadan) {
        return compute(y, mo, d, lat, lng, tz, ramadan, Method.UMM_ALQURA);
    }

    /** The same by that method. An Isha by angle is never before Maghrib (at a high latitude in summer the angle is never reached). */
    static long[] compute(int y, int mo, int d, double lat, double lng, String tz, boolean ramadan, Method m) {
        TimeZone zone = TimeZone.getTimeZone(tz);
        long day0 = midnight(y, mo, d, tz);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        utc.clear();
        utc.set(y, mo - 1, d, 0, 0, 0);
        double jd0 = utc.getTimeInMillis() / 86400000.0 + 2440587.5 - lng / 360.0;
        double tzHours = zone.getOffset(day0 + 12 * 3600_000L) / 3600000.0;

        double fajr = adj(angleTime(jd0, lat, m.fajr, 5, true), tzHours, lng);
        double sunrise = adj(angleTime(jd0, lat, .833, 6, true), tzHours, lng);
        double dhuhr = adj(noon(jd0, 12), tzHours, lng);
        double asr = adj(asrTime(jd0, lat, 13), tzHours, lng);
        double maghrib = adj(angleTime(jd0, lat, m.maghrib, 18, false), tzHours, lng);

        long[] at = new long[6];
        at[FAJR] = day0 + minutesOfDay(fajr) * 60_000L;
        at[SUNRISE] = day0 + minutesOfDay(sunrise) * 60_000L;
        at[DHUHR] = day0 + minutesOfDay(dhuhr) * 60_000L;
        at[ASR] = day0 + minutesOfDay(asr) * 60_000L;
        at[MAGHRIB] = day0 + minutesOfDay(maghrib) * 60_000L;
        if (m.ishaIsDelay()) {
            at[ISHA] = at[MAGHRIB] + ishaDelayMillis(m, ramadan);
        } else {
            // the angle's hour, as a moment after Maghrib's: past midnight it is the next day's, like the delayed Isha
            long isha = day0 + Math.round(fix(adj(angleTime(jd0, lat, m.isha, 18, false), tzHours, lng), 24) * 60) * 60_000L;
            while (isha < at[MAGHRIB]) isha += 86_400_000L;
            at[ISHA] = isha;
        }
        return at;
    }

    /**
     * The sunset of that local day, whatever the method: the Shia methods put Maghrib some 15 minutes after it, so the greeting's
     * «الغروب» can't be Maghrib's time. Umm al-Qura's Maghrib is the sunset (.833°).
     */
    static long sunset(int y, int mo, int d, double lat, double lng, String tz) {
        return compute(y, mo, d, lat, lng, tz, false, Method.UMM_ALQURA)[MAGHRIB];
    }

    /** The preference key of the day's times: the date in the city's zone and the city itself, so a new city refetches. */
    static String key(long millis, double lat, double lng, String tz) {
        return "times_" + format("yyyyMMdd", millis, tz) + "_" + String.format(Locale.US, "%.3f_%.3f", lat, lng) + "_" + tz;
    }

    /** The same for a method: Umm al-Qura keeps the key it always had, another method is another key, so a new method refetches. */
    static String key(long millis, double lat, double lng, String tz, Method m) {
        return m == Method.UMM_ALQURA ? key(millis, lat, lng, tz) : key(millis, lat, lng, tz) + "_" + m.key;
    }

    /** The date Aladhan is asked for: the city's own day, not the TV's. */
    static String aladhanDate(long millis, String tz) { return format("dd-MM-yyyy", millis, tz); }

    /** "5:12" or "05:12 (+03)" -> {5, 12}; null when it is not a time. Aladhan puts a zone after the time on some days. */
    static int[] parseClock(String v) {
        if (v == null) return null;
        Matcher m = CLOCK.matcher(v);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        return h < 24 && min < 60 ? new int[]{h, min} : null;
    }

    /** "HH:mm" of that moment in that zone. */
    static String clockOf(long at, String tz) { return format("HH:mm", at, tz); }

    private static String format(String pattern, long millis, String tz) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(tz));
        return f.format(millis);
    }

    private static int minutesOfDay(double hours) { return (int) Math.round(fix(hours, 24) * 60) % 1440; }

    private static double adj(double h, double tzHours, double lng) { return h + tzHours - lng / 15.0; }

    private static double noon(double jd0, double t) { return fix(12 - sun(jd0, t / 24)[1], 24); }

    private static double angleTime(double jd0, double lat, double angle, double t, boolean before) {
        double decl = sun(jd0, t / 24)[0];
        double n = noon(jd0, t);
        double cosT = (-Math.sin(angle * RAD) - Math.sin(decl * RAD) * Math.sin(lat * RAD))
                / (Math.cos(decl * RAD) * Math.cos(lat * RAD));
        double T = Math.acos(Math.max(-1, Math.min(1, cosT))) / RAD / 15;
        return n + (before ? -T : T);
    }

    private static double asrTime(double jd0, double lat, double t) {
        double decl = sun(jd0, t / 24)[0];
        double a = -Math.atan(1 / (1 + Math.tan(Math.abs(lat - decl) * RAD))) / RAD;
        return angleTime(jd0, lat, a, t, false);
    }

    /** declination and equation of time */
    private static double[] sun(double jd0, double t) {
        double D = jd0 + t - 2451545;
        double g = fix(357.529 + .98560028 * D, 360);
        double q = fix(280.459 + .98564736 * D, 360);
        double L = fix(q + 1.915 * Math.sin(g * RAD) + .020 * Math.sin(2 * g * RAD), 360);
        double e = 23.439 - .00000036 * D;
        double ra = fix(Math.atan2(Math.cos(e * RAD) * Math.sin(L * RAD), Math.cos(L * RAD)) / RAD / 15, 24);
        double decl = Math.asin(Math.sin(e * RAD) * Math.sin(L * RAD)) / RAD;
        return new double[]{decl, q / 15 - ra};
    }

    private static double fix(double a, double b) {
        a = a - b * Math.floor(a / b);
        return a < 0 ? a + b : a;
    }
}
