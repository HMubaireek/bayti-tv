package sa.bayti.home;

/**
 * The words of the slim bar before the adhan: «الأذان بعد ٨ دقائق» in the last 10 minutes before each prayer, and from the
 * adhan until the iqama «الإقامة بعد ١٢ دقيقة». The page does the same on the screensaver (adhanBarText in template.html). No
 * Android in here, so tests/adhan_bar_test.py runs it on tests/adhan_bar_cases.tsv.
 */
final class AdhanBarText {
    /** how many minutes before the adhan the bar shows */
    static final int BEFORE_MIN = 10;
    /** minutes from adhan to iqama for Fajr, Dhuhr, Asr, Maghrib and Isha: what the page's «الإقامة بعد» has always used */
    static final int[] IQAMA = {25, 20, 20, 10, 20};

    private AdhanBarText() { }

    /**
     * @param now    the moment, in millis
     * @param adhan  Fajr, Dhuhr, Asr, Maghrib and Isha today, in millis
     * @param friday a Friday's Dhuhr has no iqama countdown (the khutba comes first)
     * @return the bar's words, or null when it is not showing
     */
    static String text(long now, long[] adhan, boolean friday, boolean latin) {
        for (int i = 0; i < 5; i++) {
            double toAdhan = (adhan[i] - now) / 60000.0;
            if (toAdhan > 0 && toAdhan <= BEFORE_MIN) return "الأذان بعد " + minutes((int) Math.ceil(toAdhan), latin);
            int gap = i == 1 && friday ? 0 : IQAMA[i];
            double toIqama = (adhan[i] + gap * 60000L - now) / 60000.0;
            if (now >= adhan[i] && toIqama > 0) return "الإقامة بعد " + minutes((int) Math.ceil(toIqama), latin);
        }
        return null;
    }

    /** «١ دقيقة», «دقيقتين», «٨ دقائق», «١٢ دقيقة»: as the page says them. */
    static String minutes(int n, boolean latin) {
        if (n == 2) return "دقيقتين";
        return GreetingText.digits(String.valueOf(n), latin) + (n >= 3 && n <= 10 ? " دقائق" : " دقيقة");
    }
}
