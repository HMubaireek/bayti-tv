package sa.bayti.screensaver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** For tests/city_match_test.py: CityMatch.find on the built-in cities. Args: queries.txt (one per line). Prints one
 *  line per query: the names found, separated by "|"; then the number of built-in names. */
public final class CityMatchRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String q : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            List<String> names = new ArrayList<>();
            for (String[] c : CityMatch.find(q)) names.add(c[0]);
            out.append(String.join("|", names)).append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
