package sa.bayti.screensaver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** For tests/page_stamp_test.py: PageStamp on tests/page_stamp_cases.tsv. Lines: of TAB text -> the stamp; newer TAB a TAB b -> 1 or 0. */
public final class PageStampRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f[0].equals("of")) out.append(PageStamp.of(f[1].replace("\\n", "\n")));
            else out.append(PageStamp.newer(f[1], f[2]) ? "1" : "0");
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
