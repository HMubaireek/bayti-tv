package sa.bayti.home.tilawah;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * What the home screen's greeting and its bar before the adhan need from the prayer times (PrayerTimes stays private to this
 * package): the moments of the five prayers today, and the next sunrise or sunset. The same numbers as the adhan.
 */
public final class DayParts {
    /** Fajr, Dhuhr, Asr, Maghrib and Isha today, in epoch millis */
    public final long fajr, dhuhr, asr, maghrib, isha;
    /** «الغروب» until Maghrib, then «الشروق» (the coming one); sunClock is "17:57" in the city's time */
    public final String sunLabel, sunClock;
    /** it is Friday in the city: its Dhuhr is the Jumu'ah, with no iqama countdown */
    public final boolean friday;
    /** «إخفاء العصر والعشاء»: no bar before their adhan (their times still divide the greeting's day) */
    public final boolean hideAsr, hideIsha;

    private DayParts(long fajr, long dhuhr, long asr, long maghrib, long isha, String sunLabel, String sunClock, boolean friday,
                     boolean hideAsr, boolean hideIsha) {
        this.fajr = fajr; this.dhuhr = dhuhr; this.asr = asr; this.maghrib = maghrib; this.isha = isha;
        this.sunLabel = sunLabel; this.sunClock = sunClock; this.friday = friday;
        this.hideAsr = hideAsr; this.hideIsha = hideIsha;
    }

    public static DayParts now(Context c) { return now(c, Place.get(c)); }

    /** From the screensaver's settings fragment the caller has read already (null: it is missing): one read per tick. */
    public static DayParts now(Context c, String fragment) { return now(c, Place.get(c, fragment)); }

    private static String sunriseKey, sunriseClock;   // tomorrow's sunrise, worked out once a day

    private static DayParts now(Context c, Place place) {
        PrayerTimes t = PrayerTimes.today(c, place);
        long now = System.currentTimeMillis();
        String label, clock;
        // the sunset itself: Maghrib is the sunset only for the methods that call it so (not the Shia ones, «طريقة حساب المواقيت»)
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone(place.tz), Locale.US);
        long sunset = place.method.maghrib < 1 ? t.at[PrayerTimes.MAGHRIB]
                : PrayerMath.sunset(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH), place.lat, place.lng, place.tz);
        if (now < t.at[PrayerTimes.SUNRISE]) {            // before dawn: today's sunrise is next
            label = "الشروق"; clock = t.clock[PrayerTimes.SUNRISE];
        } else if (now < sunset) {
            label = "الغروب"; clock = place.method.maghrib < 1 ? t.clock[PrayerTimes.MAGHRIB] : PrayerMath.clockOf(sunset, place.tz);
        } else {                                          // after sunset: tomorrow's sunrise
            Calendar tomorrow = Calendar.getInstance(TimeZone.getTimeZone(place.tz), Locale.US);
            tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            String key = PrayerTimes.dayKey(tomorrow, place);
            synchronized (DayParts.class) {
                if (!key.equals(sunriseKey)) { sunriseClock = PrayerTimes.of(tomorrow, place).clock[PrayerTimes.SUNRISE]; sunriseKey = key; }
                label = "الشروق"; clock = sunriseClock;
            }
        }
        boolean friday = Calendar.getInstance(TimeZone.getTimeZone(place.tz), Locale.US).get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
        return new DayParts(t.at[PrayerTimes.FAJR], t.at[PrayerTimes.DHUHR], t.at[PrayerTimes.ASR],
                t.at[PrayerTimes.MAGHRIB], t.at[PrayerTimes.ISHA], label, clock, friday, place.hideAsr, place.hideIsha);
    }
}
