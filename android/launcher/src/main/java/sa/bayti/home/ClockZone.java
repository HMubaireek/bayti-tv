package sa.bayti.home;

import java.net.URLDecoder;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Which time zone the home screen's clock, date and greeting go by: the city's, picked in the screensaver (#tz= in its
 * ConfigProvider), like the prayer times beside them. No Android in here, so tests/clock_zone_test.py runs it.
 */
final class ClockZone {
    static final String DEFAULT = "Asia/Riyadh";

    private ClockZone() { }

    /** A zone id the platform knows; anything else (missing, misspelt) is Riyadh, as the page does. */
    static String valid(String id) {
        if (id == null) return DEFAULT;
        id = id.trim();
        return ids().contains(id) ? id : DEFAULT;
    }

    private static java.util.Set<String> known;   // the platform's ids, listed once: valid() runs every minute

    private static synchronized java.util.Set<String> ids() {
        if (known == null) known = new java.util.HashSet<>(java.util.Arrays.asList(TimeZone.getAvailableIDs()));
        return known;
    }

    /** {city, tz} from the screensaver's "city=..&tz=.." fragment; the city is "" when it names none. */
    static String[] parse(String fragment) {
        String city = "", tz = null;
        if (fragment != null) {
            for (String part : fragment.split("&")) {
                int eq = part.indexOf('=');
                if (eq < 0) continue;
                try {
                    String key = part.substring(0, eq), value = URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                    if ("city".equals(key)) city = value.trim();
                    else if ("tz".equals(key)) tz = value;
                } catch (Exception ignored) { }
            }
        }
        return new String[]{city, valid(tz)};
    }

    /** The calendar of the moment in the zone: its hour, minute and day are the city's. */
    static Calendar at(long millis, String tz) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US);
        c.setTimeInMillis(millis);
        return c;
    }

    /** The city's clock shows a different time than the TV's at that moment (same offset: the same time, nothing to say). */
    static boolean differs(String tvTz, String cityTz, long millis) {
        return TimeZone.getTimeZone(tvTz).getOffset(millis) != TimeZone.getTimeZone(cityTz).getOffset(millis);
    }

    /** The small line under the clock, as the screensaver's: «بتوقيت دبي». */
    static String label(String city) {
        return "بتوقيت " + (city.isEmpty() ? "المدينة" : city);
    }
}
