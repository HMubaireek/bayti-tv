package sa.bayti.screensaver;

import java.util.ArrayList;
import java.util.List;

/**
 * A short form of a built-in city's name ("مكة" for "مكة المكرمة"), for `saver.sh city`. The spelling is the club
 * search's (TeamSearch.normalize: no harakat, أ إ آ = ا, ة = ه, ى = ي, no «ال»). The first of these that finds
 * anything wins: the same name, a name that starts with it, a name that contains it.
 */
final class CityMatch {
    private CityMatch() {}

    private static String key(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : TeamSearch.normalize(name)) b.append(b.length() > 0 ? " " : "").append(w);
        return b.toString();
    }

    /** The built-in cities that the typed name stands for: none, one, or several (the caller lists them). */
    static List<String[]> find(String typed) {
        String q = key(typed);
        List<String[]> out = new ArrayList<>();
        if (q.length() < 2) return out;
        for (int tier = 0; tier < 3 && out.isEmpty(); tier++) {
            for (Cities.Region r : Cities.REGIONS) {
                for (String[] city : r.cities) {
                    String k = key(city[0]);
                    boolean hit = tier == 0 ? k.equals(q) : tier == 1 ? k.startsWith(q) : k.contains(q);
                    if (hit && !out.contains(city)) out.add(city);
                }
            }
        }
        return out;
    }
}
