package sa.bayti.home;

/** For tests/home_card_test.py: HomeCardChoice on the cases given as arguments, four per case:
 *  frag panel savedFrag savedPanel ("-" = null). Prints one line per case: the page, or "off". */
public final class HomeCardChoiceRun {
    private static String arg(String s) { return "-".equals(s) ? null : s; }

    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 3 < args.length; i += 4) {
            String url = HomeCardChoice.liveUrl(HomeCardChoice.pick(arg(args[i]), arg(args[i + 1]), arg(args[i + 2]), arg(args[i + 3])));
            out.append(url == null ? "off" : url).append('\n');
        }
        System.out.print(out);
    }
}
