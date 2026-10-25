package sa.bayti.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * For tests/pill_text_test.py: PillText.java on tests/pill_text_cases.tsv. A line is
 *   shown    LABEL  focused(0|1)                  -> what the pill shows
 *   sleep    MINUTES                              -> the sleep pill's name
 *   network  wired(0|1) wifi(0|1) SSID            -> the network pill's name
 */
public final class PillTextRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "shown": out.append(PillText.shown(f[1], "1".equals(f[2]))); break;
                case "sleep": out.append(PillText.sleep(Integer.parseInt(f[1]))); break;
                default: out.append(PillText.network("1".equals(f[1]), "1".equals(f[2]), f[3].equals("null") ? null : f[3]));
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
