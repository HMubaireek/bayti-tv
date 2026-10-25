package sa.bayti.home.tilawah;

/**
 * What the home screen's «يُتلى الآن» card says, with no Android in it (tests/NowPlayingTextRun.java runs it): two lines, the
 * surah or station and then who recites it (and the sleep timer when one is set); a hint instead of the second line while the card
 * has the focus.
 */
final class NowPlayingText {
    private NowPlayingText() {}

    /** «سورة الفاتحة», or the station's name */
    static String title(boolean radio, String surahName, String stationName) {
        return radio ? stationName : "سورة " + surahName;
    }

    /** who recites: the reciter, or «بث مباشر» for a station */
    static String who(boolean radio, String reciter) { return radio ? "بث مباشر" : reciter; }

    /** «يُتلى الآن · عبدالرحمن السديس», «يُتلى الآن · بث مباشر»; with a sleep timer set: «… · نوم ٢٣ د» (minutes left, 0 = none) */
    static String sub(boolean radio, String reciter, int sleepMinutes) {
        String s = "يُتلى الآن  ·  " + (radio ? "بث مباشر" : reciter);
        return sleepMinutes > 0 ? s + "  ·  نوم " + digits(String.valueOf(sleepMinutes)) + " د" : s;
    }

    /** What the keys do while the card has the focus: OK pauses or plays, a long OK opens the player, ◀ ▶ the previous and next surah (not for a station) */
    static String hint(boolean radio) {
        return radio ? "اختيار: إيقاف/تشغيل  ·  مطولًا: فتح"
                : "اختيار: إيقاف/تشغيل  ·  يمين/يسار: السورة  ·  مطولًا: فتح";
    }

    /** 0-9 as ٠-٩ */
    static String digits(String s) {
        return sa.bayti.home.DigitsText.convert(s, false);
    }
}
