package sa.bayti.screensaver;

/** The apps' version numbers ("3.12": major.minor), compared as numbers, major first. Nothing of Android in it (tests/VersionsRun.java). */
final class Versions {
    private Versions() { }

    /** {major, minor} of "3.12" (a bare "4" is 4.0); null when it does not parse. */
    static int[] parse(String v) {
        if (v == null) return null;
        String[] part = v.trim().split("\\.", -1);
        if (part.length < 1 || part.length > 3) return null;
        try {
            int major = Integer.parseInt(part[0]), minor = part.length > 1 ? Integer.parseInt(part[1]) : 0;
            return major < 0 || minor < 0 ? null : new int[]{major, minor};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** >0 when a is newer than b, 0 when equal, <0 when older; null when either does not parse. */
    static Integer compare(String a, String b) {
        int[] x = parse(a), y = parse(b);
        if (x == null || y == null) return null;
        return x[0] != y[0] ? Integer.compare(x[0], y[0]) : Integer.compare(x[1], y[1]);
    }
}
