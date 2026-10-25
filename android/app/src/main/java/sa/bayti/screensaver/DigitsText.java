package sa.bayti.screensaver;

/**
 * The one place that turns digits, with no Android in it (tests/DigitsTextRun.java runs it): the screensaver's «الأرقام» setting is
 * hindi (٠١٢٣, the default) or latin (0123), and every screen of this app follows it the way the page does. It converts both ways, so
 * a string built with either kind of digit comes out right: latin turns ٠-٩ into 0-9 (and «٫» into «.», «٪» into «%»), hindi the other way.
 */
final class DigitsText {
    private DigitsText() {}

    private static final char HINDI_ZERO = '٠';

    static String convert(String s, boolean latin) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (latin) b.append(c >= HINDI_ZERO && c <= HINDI_ZERO + 9 ? (char) ('0' + (c - HINDI_ZERO)) : c == '٫' ? '.' : c == '٪' ? '%' : c);
            else b.append(c >= '0' && c <= '9' ? (char) (HINDI_ZERO + (c - '0')) : c == '%' ? '٪' : c);
        }
        return b.toString();
    }

    /** the setting's value as the page reads it: «latin», anything else is hindi */
    static boolean isLatin(String setting) { return "latin".equalsIgnoreCase(setting); }
}
