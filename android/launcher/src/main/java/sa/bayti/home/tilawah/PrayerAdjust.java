package sa.bayti.home.tilawah;

/**
 * «تعديل وقت الصلاة»: whole minutes added to each of the five prayers (الفجر الظهر العصر المغرب العشاء), from −20 to +20, as the
 * screensaver's settings hand them over in the fragment: {@code adj=0,2,0,0,-1}. Nothing there (the setting is off, an older
 * screensaver, anything that is not five whole numbers) means no change. The page reads the same text (CONFIG.prayerAdj).
 * The sunrise is not a prayer and is never moved.
 */
final class PrayerAdjust {
    static final int MAX = 20;
    static final int COUNT = 5;

    private PrayerAdjust() { }

    /** The five offsets of "0,2,0,0,-1"; zeros when it is not exactly five whole numbers; each is clamped to −20..20. */
    static int[] parse(String csv) {
        int[] out = new int[COUNT];
        if (csv == null) return out;
        String[] parts = csv.split(",", -1);
        if (parts.length != COUNT) return new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            String p = parts[i].trim();
            if (!p.matches("-?\\d{1,2}")) return new int[COUNT];
            out[i] = Math.max(-MAX, Math.min(MAX, Integer.parseInt(p)));
        }
        return out;
    }

    /** The value of {@code adj=} in a settings fragment ("city=..&adj=0,2,0,0,-1&.."), or null. */
    static String of(String fragment) {
        if (fragment == null) return null;
        for (String part : fragment.split("&")) {
            if (part.startsWith("adj=")) return part.substring(4);
        }
        return null;
    }

    static int[] fromFragment(String fragment) { return parse(of(fragment)); }

    static String text(int[] v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < COUNT; i++) b.append(i == 0 ? "" : ",").append(v[i]);
        return b.toString();
    }

    /**
     * Moves the five prayers in {@code at} (FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA: the six moments PrayerTimes keeps) by their
     * offsets; the sunrise (index 1) stays. A moment is an instant: an Isha moved past midnight is still that instant (its clock text
     * wraps, PrayerMath.clockOf), and a Friday's Dhuhr is moved like any other.
     */
    static void shift(long[] at, int[] adj) {
        final int[] slot = {0, 2, 3, 4, 5};
        for (int k = 0; k < COUNT; k++) at[slot[k]] += adj[k] * 60_000L;
    }

    static boolean any(int[] v) {
        for (int x : v) if (x != 0) return true;
        return false;
    }
}
