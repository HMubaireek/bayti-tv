"""The heavy-screensaver advice and optimize/saver.sh off / on / show (no TV needed).

    python3 tests/saver_off_test.py

heavy.py is called with tables like the ones measure.sh writes. saver.sh runs with PY pointed at a stand-in for
optimize/tv.py that logs the commands it gets (one per line, batches too) and answers like a TV.
"""
import importlib.util, os, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("heavy", os.path.join(here, "..", "optimize", "heavy.py"))
heavy = importlib.util.module_from_spec(spec); spec.loader.exec_module(heavy)
saver = os.path.join(here, "..", "optimize", "saver.sh")

failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

# ---- the thresholds, on tables in measure.sh's format
HEAD = "| state | free RAM (MB of 1683) | swap used (MB) | processes | CPU idle | disabled packages |\n|---|---|---|---|---|---|\n"
def table(stock, ours):   # (ram, idle) each; None = not measured
    def row(label, v):
        if v is None:
            return f"| {label} | not measured (Android didn't start the screensaver from ADB) | | | | |\n"
        return f"| {label} | {v[0]} | 0 | 250 | {v[1]}% | 85 |\n"
    return HEAD + "| before | 600 | 0 | 300 | 80% | 0 |\n" + row("before-screensaver", stock) + "| after | 700 | 0 | 280 | 85% | 85 |\n" + row("after-screensaver", ours)

def says(stock, ours):
    return heavy.verdict(table(stock, ours))

check("light: 700 MB vs 680 MB, idle 90 vs 88", says((700, 90), (680, 88)) is None)
check("normal cost, top of the field range: 150 MB less, still 550 free", says((700, 90), (550, 88)) is None)
check("200 MB below is the limit, not over it", says((800, 90), (600, 90)) is None)
check("201 MB below: heavy", says((800, 90), (599, 90)) is not None)
check("below 400 MB and clearly below the stock one: heavy", says((480, 90), (390, 90)) is not None)
check("below 400 MB but only noise below the stock one: not heavy", says((420, 90), (390, 90)) is None)
check("the stock one is low too, ours no lower: not heavy", says((350, 90), (350, 90)) is None)
check("exactly 400 free is not below the floor", says((480, 90), (400, 90)) is None)
check("CPU 30 points lower is the limit", says((700, 90), (700, 60)) is None)
check("CPU 31 points lower: heavy", says((700, 90), (700, 59)) is not None)
check("the report that started this: idle 92% vs 34%", says((700, 92), (700, 34)) is not None)
check("ours idler than the stock one: not heavy", says((700, 40), (700, 90)) is None)
line = says((900, 92), (500, 34))
check("the line: both numbers, the command, starts with !!",
      line is not None and line.startswith("!! the screensaver may slow this TV: ") and "500 MB" in line and "900 MB" in line
      and "34%" in line and "92%" in line and line.endswith("; tell the person they can turn it off: optimize/saver.sh off"), str(line))
check("one line only", line is not None and "\n" not in line)
check("only the CPU part is named when only the CPU is heavy",
      "RAM" not in (says((700, 92), (700, 34)) or "") and "CPU" in (says((700, 92), (700, 34)) or ""))
check("the stock screensaver not measured: no verdict", says(None, (100, 1)) is None)
check("ours not measured: no verdict", says((700, 90), None) is None)
check("no table at all: no verdict", heavy.verdict("") is None)
bad = HEAD + "| before-screensaver | not measured (memory read failed) | 0 | 1 | 90% | 1 |\n| after-screensaver | 300 | 0 | 1 | 30% | 1 |\n"
check("an unreadable RAM cell: the CPU alone decides", "CPU" in (heavy.verdict(bad) or "") and "RAM" not in (heavy.verdict(bad) or ""))
tmp = tempfile.mkdtemp()
tfile = os.path.join(tmp, "summary.md"); open(tfile, "w").write(table((900, 92), (500, 34)))
r = subprocess.run([sys.executable, "-I", os.path.join(here, "..", "optimize", "heavy.py"), tfile], capture_output=True, text=True)
check("heavy.py prints the line", r.returncode == 0 and r.stdout.strip() == line, r.stdout + r.stderr)
r = subprocess.run([sys.executable, "-I", os.path.join(here, "..", "optimize", "heavy.py"), os.path.join(tmp, "none.md")], capture_output=True, text=True)
check("heavy.py: no file, no output, exit 0", r.returncode == 0 and not r.stdout, r.stdout + r.stderr)

# ---- saver.sh off / on / show
log = os.path.join(tmp, "sent.txt")
fake = os.path.join(tmp, "fakepy")
open(fake, "w").write(r'''#!/usr/bin/env bash
if [ "$3" = --batch ]; then
  while IFS= read -r l; do
    echo "$l" >> "$SENT"
    echo "\$ $l"
    case "$l" in
      "settings get secure screensaver_components") echo "$COMP" ;;
      "settings get secure screensaver_enabled") echo "$ENABLED" ;;
    esac
  done
  exit 0
fi
echo "$3" >> "$SENT"
case "$3" in
  "pm list packages sa.bayti.screensaver") [ "${INSTALLED:-1}" = 1 ] && echo "package:sa.bayti.screensaver" ;;
  *query-services*) printf '%s' "$STOCK" ;;
esac
exit 0
''')
os.chmod(fake, 0o755)

def backup(comp="com.google.android.apps.tv.dreamx/.service.Dream", enabled="1"):
    p = os.path.join(tmp, "backup.txt")
    open(p, "w").write(f"== model\nTCL\n11\n== screensaver\n{comp}\n{enabled}\n300000\n== animation\n1.0\n")
    return p

def run(cmd, bk=None, comp="null", enabled="1", stock="", installed="1"):
    open(log, "w").close()
    r = subprocess.run(["bash", saver, cmd], capture_output=True, text=True,
                       env={**os.environ, "PY": fake, "SENT": log, "COMP": comp, "ENABLED": enabled, "STOCK": stock,
                            "INSTALLED": installed, "SAVER_BACKUP": bk or os.path.join(tmp, "no-backup.txt")})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().splitlines()

OURS = "sa.bayti.screensaver/.ScreenDream"
puts = lambda sent: [l for l in sent if l.startswith("settings put") or l.startswith("settings delete")]

rc, out, sent = run("off", bk=backup())
check("off with a backup: the saved screensaver comes back, switch on, the default we set removed",
      rc == 0 and puts(sent) == ["settings put secure screensaver_enabled 1",
      "settings put secure screensaver_components 'com.google.android.apps.tv.dreamx/.service.Dream'",
      "settings delete secure screensaver_default_component"], str(sent))
check("off: says ours stays installed and how to turn it on", "optimize/saver.sh on" in out and "installed" in out, out)
check("off: never uninstalls or disables the app", not any("pm " in l and ("uninstall" in l or "disable" in l) for l in sent), str(sent))
rc, out, sent = run("off", bk=backup(enabled="0"))
check("off: a switch that was off before setup is put back off", "settings put secure screensaver_enabled 0" in sent, str(sent))
rc, out, sent = run("off", bk=backup(enabled="null"))
check("off: a switch the backup never knew is turned on", "settings put secure screensaver_enabled 1" in sent, str(sent))
rc, out, sent = run("off", bk=backup(comp="a.b/.C,d.e/.F"))
check("off: the first of a list", "settings put secure screensaver_components 'a.b/.C'" in sent, str(sent))
rc, out, sent = run("off", bk=backup(comp="a.b/.C$Inner"))
check("off: an inner class keeps its $ inside single quotes", "settings put secure screensaver_components 'a.b/.C$Inner'" in sent, str(sent))
rc, out, sent = run("off", bk=backup(comp="x; reboot"))
check("off: a backup line that is not a component is never sent", rc == 0 and not any("reboot" in l for l in sent), str(sent))

stock = "priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false\ncom.android.systemui/.Colors\ncom.google.android.backdrop/.Dream\nsa.bayti.screensaver/.ScreenDream\n"
rc, out, sent = run("off", stock=stock)
check("off, no backup: a stock dream the TV lists (never ours), preferring a known one",
      "settings put secure screensaver_components 'com.google.android.backdrop/.Dream'" in sent
      and "settings put secure screensaver_enabled 1" in sent, str(sent))
rc, out, sent = run("off", bk=backup(comp="null"), stock="com.android.systemui/.Colors\n")
check("off, backup without a screensaver: the stock one", "settings put secure screensaver_components 'com.android.systemui/.Colors'" in sent, str(sent))
rc, out, sent = run("off", bk=backup(comp=OURS), stock="com.android.systemui/.Colors\n")
check("off, a backup taken with ours already set: not used", "settings put secure screensaver_components 'com.android.systemui/.Colors'" in sent, str(sent))
rc, out, sent = run("off", stock="sa.bayti.screensaver/.ScreenDream\n")
check("off, nothing else listed: screensavers off",
      puts(sent) == ["settings put secure screensaver_enabled 0", "settings delete secure screensaver_components",
                     "settings delete secure screensaver_default_component"] and "off" in out, str(sent))

rc, out, sent = run("on")
check("on: ours selected, as install.sh does",
      rc == 0 and puts(sent) == ["settings put secure screensaver_enabled 1", f"settings put secure screensaver_components {OURS}",
                                 f"settings put secure screensaver_default_component {OURS}"], str(sent))
rc, out, sent = run("on", installed="0")
check("on: not installed says so and changes nothing", rc != 0 and "!!" in out and not puts(sent), out + str(sent))

for comp, en, want in [(OURS, "1", "ours"), ("a.b/.C", "1", "a.b/.C"), ("null", "1", "default"), (OURS, "0", "off")]:
    rc, out, sent = run("show", comp=comp, enabled=en)
    first = out.strip().splitlines()[-1] if out.strip() else ""
    check(f"show: {comp} enabled={en} says «{want}» last", first.startswith("screensaver now: ") and want in first, out)
sys.exit(1 if failed else 0)
