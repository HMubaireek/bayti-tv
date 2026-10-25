package sa.bayti.screensaver;

/**
 * The countdown's row in the settings: «رحلة (٣١ ديسمبر ٢٠٢٦)». The date has its Arabic month name and the name comes first, with no
 * «·» beside a digit (the TV's system font draws it as a zero). No Android in here, so tests/countdown_text_test.py runs it.
 */
final class CountdownText {
    private static final String[] MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};

    private CountdownText() { }

    /** @param date "2026-12-31" (anything else is shown as it is, in Arabic digits) */
    static String label(String date, String name) {
        String shown = date;
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            int month = Integer.parseInt(date.substring(5, 7)), day = Integer.parseInt(date.substring(8, 10));
            if (month >= 1 && month <= 12) shown = day + " " + MONTHS[month - 1] + " " + date.substring(0, 4);
        }
        shown = digits(shown);
        return name.isEmpty() ? shown : name + " (" + shown + ")";
    }

    static String digits(String s) {
        return DigitsText.convert(s, false);
    }
}
