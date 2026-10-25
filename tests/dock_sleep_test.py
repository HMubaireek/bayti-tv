"""optimize/dock.sh sleep / sleep-setup: what they send to the TV and how they refuse bad arguments (no TV needed).

    python3 tests/dock_sleep_test.py

PY is pointed at a stand-in for optimize/tv.py that writes the shell command it was given to a file and answers like `am broadcast`
(or like `settings get`, with what GETS says).
"""
import os, subprocess, tempfile

here = os.path.dirname(os.path.abspath(__file__))
dock = os.path.join(here, "..", "optimize", "dock.sh")
tmp = tempfile.mkdtemp()
log = os.path.join(tmp, "sent.txt")
fake = os.path.join(tmp, "fakepy")
with open(fake, "w") as f:
    f.write('#!/usr/bin/env bash\nprintf "%s\\n" "$3" >> "$SENT"\ncase "$3" in "settings get"*) echo "${GETS:-null}" ;; *) echo \'Broadcast completed: result=0, data="sleep: ok"\' ;; esac\n')
os.chmod(fake, 0o755)

failed = 0
def run(*args, gets="null"):
    open(log, "w").close()
    r = subprocess.run(["bash", dock, *args], capture_output=True, text=True, env={**os.environ, "PY": fake, "SENT": log, "GETS": gets})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().strip().split("\n")

def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok

A = "am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.SLEEP_TV"
rc, out, sent = run("sleep")
check("sleep: no argument asks how it stands", rc == 0 and sent == [A] and out.strip() == "sleep: ok", (rc, out, sent))
for m in ("15", "30", "60", "90", "1", "600"):
    rc, out, sent = run("sleep", m)
    check(f"sleep {m}: starts it", rc == 0 and sent == [A + " --ei minutes " + m], sent)
rc, out, sent = run("sleep", "cancel")
check("sleep cancel: stops it", rc == 0 and sent == [A + " --ei minutes 0"], sent)
for bad, why in [("abc", "words"), ("-5", "a negative number"), ("1.5", "a fraction"), ("601", "over 600 minutes"), ("30; reboot", "a shell command"), ("", "nothing")]:
    rc, out, sent = run("sleep", bad)
    check(f"sleep: refuses {why}, sends nothing", rc != 0 and sent == [""], (rc, out, sent))
SVC = "sa.bayti.home/sa.bayti.home.SleepService"
rc, out, sent = run("sleep-setup")
check("sleep-setup: nothing enabled yet: turns on the sleep service alone, and accessibility",
      rc == 0 and sent == ["settings get secure enabled_accessibility_services", "settings put secure enabled_accessibility_services " + SVC,
                           "settings put secure accessibility_enabled 1"], sent)
rc, out, sent = run("sleep-setup", gets="com.other/com.other.Svc")
check("sleep-setup: another accessibility service is kept, ours added after it",
      rc == 0 and sent[1] == "settings put secure enabled_accessibility_services com.other/com.other.Svc:" + SVC, sent)
rc, out, sent = run("sleep-setup", gets="com.other/com.other.Svc:" + SVC)
check("sleep-setup: already on: the list is left as it is", rc == 0 and "already" in out and not any("enabled_accessibility_services com" in x for x in sent[1:]), (out, sent))

print("\n" + (f"{failed} failed" if failed else "dock sleep: all passed"))
raise SystemExit(1 if failed else 0)
