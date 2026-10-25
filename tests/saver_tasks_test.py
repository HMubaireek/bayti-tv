"""optimize/saver.sh tasks: what it sends to the TV and how it refuses bad arguments (no TV needed).

    python3 tests/saver_tasks_test.py

PY is pointed at a stand-in for optimize/tv.py that writes the shell command it was given to a file and
answers like `am broadcast` does.
"""
import os, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
saver = os.path.join(here, "..", "optimize", "saver.sh")
tmp = tempfile.mkdtemp()
log = os.path.join(tmp, "sent.txt")
fake = os.path.join(tmp, "fakepy")
with open(fake, "w") as f:
    f.write('#!/usr/bin/env bash\nprintf "%s\\n" "$3" >> "$SENT"\necho \'Broadcast completed: result=0, data="saved\'\necho \'1. x"\'\n')
os.chmod(fake, 0o755)

failed = 0
def run(*args):
    open(log, "w").close()
    r = subprocess.run(["bash", saver, "tasks", *args], capture_output=True, text=True,
                       env={**os.environ, "PY": fake, "SENT": log})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().strip()

def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

A = "am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.TASKS "
rc, out, sent = run("list")
check("list", rc == 0 and sent == A + "--es op list", sent)
check("answer is the receiver's data", out.startswith("saved") and out.strip().endswith("1. x"), out)
rc, out, sent = run("add", "اشتري خبز وحليب")
check("add: text in single quotes", rc == 0 and sent == A + "--es op add --es text 'اشتري خبز وحليب'", sent)
rc, out, sent = run("add", "a; reboot")
check("add: shell characters stay inside the quotes", sent == A + "--es op add --es text 'a; reboot'", sent)
rc, out, sent = run("add", "it's")
check("add: apostrophe refused, nothing sent", rc != 0 and not sent, out + sent)
rc, out, sent = run("add")
check("add without text: usage, nothing sent", rc != 0 and "usage" in out and not sent, out + sent)
rc, out, sent = run("done", "2")
check("done N", rc == 0 and sent == A + "--es op done --ei n 2", sent)
rc, out, sent = run("del", "3")
check("del N", rc == 0 and sent == A + "--es op del --ei n 3", sent)
for bad in ("x", "1; reboot", "", "-1"):
    rc, out, sent = run("done", bad) if bad else run("done")
    check(f"done {bad!r}: usage, nothing sent", rc != 0 and "usage" in out and not sent, out + sent)
rc, out, sent = run("clear")
check("clear", rc == 0 and sent == A + "--es op clear", sent)
rc, out, sent = run("bogus")
check("unknown word: usage, nothing sent", rc != 0 and "usage" in out and not sent, out + sent)
sys.exit(1 if failed else 0)
