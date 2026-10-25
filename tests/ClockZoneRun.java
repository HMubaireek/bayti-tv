package sa.bayti.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;

/** For tests/clock_zone_test.py: ClockZone.java on tests/clock_zone_cases.tsv. One answer per case line.
 *  at: instant, tv zone, city zone -> "H:MM day-of-month differs"; parse: a fragment -> "city|tz"; label: city -> the line. */
public final class ClockZoneRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f[0].equals("at")) {
                long ms = java.time.Instant.parse(f[1]).toEpochMilli();
                Calendar c = ClockZone.at(ms, f[3]);
                out.append(String.format("%d:%02d %d %s", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                        c.get(Calendar.DAY_OF_MONTH), ClockZone.differs(f[2], f[3], ms) ? "differs" : "same"));
            } else if (f[0].equals("parse")) {
                String[] p = ClockZone.parse(f[1]);
                out.append(p[0]).append('|').append(p[1]);
            } else {
                out.append(ClockZone.label(f[1]));
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
