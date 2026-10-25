"""optimize/dock.sh names/name: what it sends to the TV and how it refuses bad arguments (no TV needed).

    python3 tests/dock_names_test.py

PY is pointed at a stand-in for optimize/tv.py that writes the shell command it was given to a file and
answers like `am broadcast` does.
"""
import os, subprocess, tempfile

here = os.path.dirname(os.path.abspath(__file__))
dock = os.path.join(here, "..", "optimize", "dock.sh")
tmp = tempfile.mkdtemp()
log = os.path.join(tmp, "sent.txt")
fake = os.path.join(tmp, "fakepy")
with open(fake, "w") as f:
    f.write('#!/usr/bin/env bash\nprintf "%s\\n" "$3" >> "$SENT"\necho \'Broadcast completed: result=0, data="named: x"\'\n')
os.chmod(fake, 0o755)

failed = 0
def run(*args):
    open(log, "w").close()
    r = subprocess.run(["bash", dock, *args], capture_output=True, text=True, env={**os.environ, "PY": fake, "SENT": log})
    return r.returncode, r.stdout + r.stderr, open(log, encoding="utf-8").read().strip()

def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

A = "am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home."
rc, out, sent = run("names")
check("names: lists them", rc == 0 and sent == A + "NAMES", sent)
rc, out, sent = run("names-clear")
check("names-clear", rc == 0 and sent == A + "CLEAR_NAMES", sent)
rc, out, sent = run("name", "com.disney.disneyplus", "ديزني +", "org.example.tv", "مثال تي في")
check("name: pairs in one broadcast, in single quotes",
      rc == 0 and sent == A + "SET_NAMES --es names 'com.disney.disneyplus|ديزني +;org.example.tv|مثال تي في'", sent)
check("name: the receiver's answer is printed", out.strip() == "named: x", out)
rc, out, sent = run("name", "com.x.tv", "")
check("name: '' puts its usual name back", rc == 0 and sent == A + "SET_NAMES --es names 'com.x.tv|'", sent)
for bad, why in [(("com.x.tv",), "a package without a name"), (("com.x tv", "اسم"), "not a package name"),
                 (("com.x.tv", "it's"), "an apostrophe"), (("com.x.tv", "a;reboot"), "a semicolon"),
                 (("com.x.tv", "a|b"), "a bar")]:
    rc, out, sent = run("name", *bad)
    check(f"name: refuses {why}, sends nothing", rc != 0 and not sent, out + sent)

print("\n" + (f"{failed} failed" if failed else "dock names: all passed"))
raise SystemExit(1 if failed else 0)
