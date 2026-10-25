package sa.bayti.home.tilawah;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Prayer times for the TV, by the method picked in the screensaver («طريقة حساب المواقيت», Umm al-Qura unless changed): the same
 * numbers the screensaver's page computes and Aladhan gives for that method. Everything is computed on the TV with no network; once
 * a day the times are quietly replaced by Aladhan's. With Umm al-Qura (and Gulf, Qatar) Isha stays a delay after Maghrib (90 minutes,
 * 120 in Ramadan for Umm al-Qura); with the other methods it is Aladhan's own Isha.
 */
final class PrayerTimes {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    /** the five the TV shows and plays the adhan for — sunrise is not a prayer */
    static final int[] PRAYERS = {FAJR, DHUHR, ASR, MAGHRIB, ISHA};
    static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final String[] KEYS = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};

    final long[] at = new long[6];
    final String[] clock = new String[6];
    /** the city's zone these times belong to, local midnight of their day, and whether it is Ramadan */
    private String tz = "Asia/Riyadh";
    private long day0;
    private boolean ramadan;
    private PrayerMath.Method method = PrayerMath.Method.UMM_ALQURA;
    /** «تعديل وقت الصلاة»: minutes added to PRAYERS[k] (PrayerAdjust); the sunrise is never moved */
    private int[] adj = new int[PrayerAdjust.COUNT];

    static final class Next {
        final int index;
        final long at;
        final String clock;
        Next(int index, long at, String clock) { this.index = index; this.at = at; this.clock = clock; }
        String name() { return NAMES[index]; }
    }

    // ---------------------------------------------------------------- the times

    static PrayerTimes of(Calendar when, Place place) {
        TimeZone zone = TimeZone.getTimeZone(place.tz);
        Calendar local = Calendar.getInstance(zone, Locale.US);
        local.setTimeInMillis(when.getTimeInMillis());
        long day0 = PrayerMath.midnight(local.get(Calendar.YEAR), local.get(Calendar.MONTH) + 1,
                local.get(Calendar.DAY_OF_MONTH), place.tz);
        return of(local, place, hijriMonth(day0) == 9);
    }

    private static PrayerTimes of(Calendar local, Place place, boolean ramadan) {
        long[] times = PrayerMath.compute(local.get(Calendar.YEAR), local.get(Calendar.MONTH) + 1,
                local.get(Calendar.DAY_OF_MONTH), place.lat, place.lng, place.tz, ramadan, place.method);
        PrayerTimes t = new PrayerTimes();
        t.tz = place.tz;
        t.ramadan = ramadan;
        t.method = place.method;
        t.day0 = PrayerMath.midnight(local.get(Calendar.YEAR), local.get(Calendar.MONTH) + 1, local.get(Calendar.DAY_OF_MONTH), place.tz);
        for (int i = 0; i < 6; i++) t.set(i, times[i]);
        t.adj = place.adj;
        t.shift();
        return t;
    }

    /**
     * «تعديل وقت الصلاة»: each of the five prayers moved by its own minutes, once, on the times as computed or as Aladhan gave them
     * (Isha is its own raw moment plus its own offset, not the moved Maghrib plus a delay). An Isha moved past midnight is still the
     * moment it is: the clock text wraps, the instant does not.
     */
    private void shift() {
        if (!PrayerAdjust.any(adj)) return;
        long[] moved = at.clone();
        PrayerAdjust.shift(moved, adj);
        for (int i = 0; i < 6; i++) if (moved[i] != at[i]) set(i, moved[i]);
    }

    /** Today's times: the ones fetched from Aladhan when they are in, the computed ones otherwise. */
    static PrayerTimes today(Context c) { return today(c, Place.get(c)); }

    /**
     * The same for a place the caller already has. Kept in memory for the day (and the city): the home screen asks every minute,
     * and the sun arithmetic and the preferences need not be gone through each time. New times from Aladhan drop the copy.
     */
    static synchronized PrayerTimes today(Context c, Place place) {
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone(place.tz), Locale.US);
        String key = key(day, place);
        String copyKey = key + "|" + PrayerAdjust.text(place.adj);   // a change of the adjustments is a different day's copy
        if (todayCopy != null && copyKey.equals(todayKey)) return todayCopy;
        PrayerTimes t = of(day, place);
        String saved = prefs(c).getString(key, null);
        if (saved != null) t.apply(saved);   // Aladhan's, when they came; a bad copy is ignored
        todayCopy = t;
        todayKey = copyKey;
        return t;
    }

    private static PrayerTimes todayCopy;
    private static String todayKey;

    private static synchronized void forgetToday() { todayCopy = null; todayKey = null; }

    /** The next of the five prayers, looking at tomorrow when today's Isha has passed. */
    static Next next(Context c) {
        long now = System.currentTimeMillis();
        Place place = Place.get(c);
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone(place.tz), Locale.US);
        for (int d = 0; d < 3; d++) {
            PrayerTimes t = d == 0 ? today(c) : of(day, place);
            for (int i : PRAYERS) {
                if (t.at[i] > now + 30_000) return new Next(i, t.at[i], t.clock[i]);
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    /** "بعد ساعتين و١٥ دقيقة" — the time left, in the project's Arabic. */
    static String remaining(long millis) {
        long minutes = Math.max(0, (millis + 30_000) / 60_000);
        long h = minutes / 60, m = minutes % 60;
        String out = null;
        if (h > 0) out = hours((int) h);
        if (m > 0) out = out == null ? minutes((int) m) : out + " و" + minutes((int) m);
        return out == null ? "الآن" : "بعد " + out;
    }

    private static String hours(int n) {
        if (n == 1) return "ساعة";
        if (n == 2) return "ساعتين";
        return Ui.arabic(n) + (n >= 3 && n <= 10 ? " ساعات" : " ساعة");
    }

    private static String minutes(int n) {
        if (n == 1) return "دقيقة";
        if (n == 2) return "دقيقتين";
        return Ui.arabic(n) + (n >= 3 && n <= 10 ? " دقائق" : " دقيقة");
    }

    /** ٠٥:١٢ → "٥:١٢ ص", ١٨:٤٠ → "٦:٤٠ م" */
    static String clock12(String hhmm) {
        int colon = hhmm.indexOf(':');
        int h = Integer.parseInt(hhmm.substring(0, colon));
        String m = hhmm.substring(colon + 1);
        String suffix = h < 12 ? "ص" : "م";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return Ui.digits(h12 + ":" + m) + " " + suffix;
    }

    // ---------------------------------------------------------------- Aladhan

    /**
     * Replaces today's computed times with Aladhan's (the place's method) once they arrive, and puts the alarm back for the
     * new times. Runs off the main thread; `done` runs on the main thread when something changed.
     */
    static void refresh(final Context c, final Runnable done) {
        new Thread(() -> {
            if (refreshNow(c) && done != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(done);
        }, "adhan-times").start();
    }

    /** The same, on the caller's thread (which must not be the main one). True when new times were kept. */
    static boolean refreshNow(Context c) {
        final Place place = Place.get(c);
        final Calendar day = Calendar.getInstance(TimeZone.getTimeZone(place.tz), Locale.US);
        final String key = key(day, place);
        if (prefs(c).getBoolean("fetched_" + key, false)) return false;
        try {
            String url = "https://api.aladhan.com/v1/timings/" + PrayerMath.aladhanDate(day.getTimeInMillis(), place.tz)
                    + "?latitude=" + place.lat + "&longitude=" + place.lng
                    + "&method=" + place.method.aladhan + "&timezonestring=" + URLEncoder.encode(place.tz, "UTF-8");
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(8000);
            con.setRequestProperty("User-Agent", "urtv-home");
            StringBuilder body = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) body.append(line);
            } finally {
                con.disconnect();
            }
            JSONObject timings = new JSONObject(body.toString()).getJSONObject("data").getJSONObject("timings");
            StringBuilder out = new StringBuilder();
            int last = place.method.ishaIsDelay() ? MAGHRIB : ISHA;   // a delayed Isha stays ours: 90/120 minutes after Maghrib
            for (int i = 0; i <= last; i++) {
                int[] hm = PrayerMath.parseClock(timings.optString(KEYS[i]));   // «5:12 (+03)» is 5:12
                if (hm == null) throw new IllegalStateException("no " + KEYS[i]);
                if (out.length() > 0) out.append(',');
                out.append(String.format(Locale.US, "%02d:%02d", hm[0], hm[1]));
            }
            SharedPreferences.Editor edit = prefs(c).edit();
            for (String k : prefs(c).getAll().keySet()) {   // yesterday's and another city's copies are not kept
                if ((k.startsWith("times_") || k.startsWith("fetched_")) && !k.endsWith(key.substring("times_".length()))) edit.remove(k);
            }
            edit.putString(key, out.toString()).putBoolean("fetched_" + key, true).apply();
            forgetToday();
            AdhanScheduler.schedule(c);   // the alarm was set for the computed times: the new ones may differ by minutes
            return true;
        } catch (Exception ignored) {
            // offline or Aladhan is down: the computed times are already right
            return false;
        }
    }

    /**
     * Aladhan's times, "HH:mm,..." as saved: five, and Isha is Maghrib's moment plus its delay, never wrapped into the day; or, for a
     * method with an Isha angle, six, and Isha is Aladhan's, as a moment after Maghrib (past midnight: the next day's).
     */
    private void apply(String saved) {
        String[] values = saved.split(",");
        int last = method.ishaIsDelay() ? MAGHRIB : ISHA;
        if (values.length < last + 1) return;
        long[] parsed = new long[last + 1];
        for (int i = 0; i <= last; i++) {
            int[] hm = PrayerMath.parseClock(values[i]);
            if (hm == null) return;   // not a time: keep the computed ones
            parsed[i] = day0 + (hm[0] * 60 + hm[1]) * 60_000L;
        }
        if (last == ISHA) while (parsed[ISHA] < parsed[MAGHRIB]) parsed[ISHA] += 86_400_000L;
        for (int i = 0; i <= last; i++) set(i, parsed[i]);
        if (last == MAGHRIB) set(ISHA, parsed[MAGHRIB] + PrayerMath.ishaDelayMillis(method, ramadan));
        shift();   // Aladhan's times are the raw ones: the person's minutes go on top of them
    }

    private void set(int i, long moment) {
        at[i] = moment;
        clock[i] = PrayerMath.clockOf(moment, tz);
    }

    // ---------------------------------------------------------------- the Islamic month

    /** 9 in Ramadan, from the Umm al-Qura calendar the whole project uses. */
    private static int hijriMonth(long millis) {
        try {
            android.icu.util.Calendar cal = android.icu.util.Calendar.getInstance(
                    new android.icu.util.ULocale("en-u-ca-islamic-umalqura"));
            cal.setTimeInMillis(millis);
            return cal.get(android.icu.util.Calendar.MONTH) + 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- prefs

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("tilawah", Context.MODE_PRIVATE);
    }

    static String dayKey(Calendar day, Place place) { return key(day, place); }

    /** The day's key in the city's zone, for that city and method: a new city or method the same day asks Aladhan again. */
    private static String key(Calendar day, Place place) {
        return PrayerMath.key(day.getTimeInMillis(), place.lat, place.lng, place.tz, place.method);
    }
}
