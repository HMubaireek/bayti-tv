package sa.bayti.screensaver;

/** For tests/versions_test.py: Versions.java on a plain JVM. Prints PASS/FAIL lines. */
public final class VersionsRun {
    static int failed;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    static boolean newer(String a, String b) { Integer c = Versions.compare(a, b); return c != null && c > 0; }

    public static void main(String[] args) {
        check("3.12 is newer than 3.9 (a number, not text)", newer("3.12", "3.9"), "");
        check("4.0 is newer than 3.12 (major first)", newer("4.0", "3.12"), "");
        check("4.1 is newer than 3.99", newer("4.1", "3.99"), "");
        check("3.9 is not newer than 4.0", !newer("3.9", "4.0"), "");
        check("equal versions are not newer", Versions.compare("3.12", "3.12") == 0, "");
        check("3.5 is older than 3.12", Versions.compare("3.5", "3.12") < 0, "");
        check("a bare 4 is 4.0", Versions.compare("4", "4.0") == 0 && newer("4", "3.30"), "");
        check("what does not parse is null", Versions.parse("abc") == null && Versions.parse(null) == null && Versions.parse("") == null
                && Versions.parse("3.x") == null && Versions.parse("-1.2") == null && Versions.compare("x", "3.1") == null, "");
        System.exit(failed == 0 ? 0 : 1);
    }
}
