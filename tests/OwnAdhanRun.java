package sa.bayti.home.tilawah;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * For tests/own_adhan_test.py: OwnAdhanText.java on tests/own_adhan_cases.tsv. A line is
 *   container  HEX-of-the-first-bytes            -> mp4 | ogg | (empty)
 *   refuse     BYTES  MS                         -> the reason, or empty when it may be kept
 *   size       BYTES                             -> "1.7 MB" / "340 KB"
 */
public final class OwnAdhanRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "container": {
                    byte[] b = new byte[f[1].length() / 2];
                    for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(f[1].substring(2 * i, 2 * i + 2), 16);
                    out.append(OwnAdhanText.container(b, b.length));
                    break;
                }
                case "refuse":
                    out.append(OwnAdhanText.refuse(Long.parseLong(f[1]), Long.parseLong(f[2])));
                    break;
                default:
                    out.append(OwnAdhanText.size(Long.parseLong(f[1])));
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
