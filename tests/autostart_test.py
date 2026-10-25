#!/usr/bin/env python3
"""optimize/autostart.sh: on a TCL the two apps are allowed to start at boot (AUTO_START on G08 builds, APP_AUTO_START on G10),
the grant is written to disk, and the report line says what the TV answers; other TVs are left alone. A fake tv.py; no TV needed.

    python3 tests/autostart_test.py
"""
import os, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
tmp = Path(tempfile.mkdtemp())
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

fake = tmp / "fakepy"
fake.write_text(r'''#!/usr/bin/env python3
import os, re, sys, json
cmd = sys.argv[3]
S = json.load(open(os.environ["TVSTATE"]))
open(os.environ["LOG"], "a").write(cmd + "\n")
def out(t): sys.stdout.write(t)
if cmd.startswith("getprop"):
    out(S["maker"] + "\n" + S["maker"] + "\n" + "".join("package:%s\n" % p for p in S.get("tclpkgs", [])))
elif cmd.startswith("pm list packages com.tcl."):
    out("".join("package:%s\n" % p for p in S.get("tclpkgs", [])))
elif cmd.startswith("pm list packages "):
    p = cmd.split()[-1]
    if p in S["installed"]: out("package:%s\n" % p)
elif cmd.startswith("appops get "):
    _, _, p, op = cmd.split()[:4]
    if op in S["ops"] and p in S["ops"][op]: out("%s: %s; time=+1d\n" % (op, S["ops"][op][p]))
    else: out("Error: Unknown operation string: %s\n" % op if op not in S["ops"] else "No operations.\n")
else:
    for m in re.finditer(r"(?:cmd )?appops set (\S+) (\S+) allow", cmd):
        p, op = m.groups()
        if op in S["ops"] and S["honour"]: S["ops"][op][p] = "allow"
    if "appops write-settings" in cmd: S["written"] = True
    json.dump(S, open(os.environ["TVSTATE"], "w"))
''')
fake.chmod(0o755)
real = tmp / "py"
real.write_text('#!/bin/bash\nif [ "$2" = optimize/tv.py ]; then exec ' + str(fake) + ' "$@"; else exec python3 "$@"; fi\n'); real.chmod(0o755)
import json
def run(*args, **state):
    base = {"maker": "TCL", "installed": ["sa.bayti.home", "sa.bayti.screensaver"], "ops": {"AUTO_START": {}, "APP_AUTO_START": {}}, "honour": True, "written": False}
    base.update(state)
    (tmp / "state.json").write_text(json.dumps(base)); (tmp / "log").write_text("")
    r = subprocess.run(["bash", str(ROOT / "optimize/autostart.sh"), *args], capture_output=True, text=True, cwd=ROOT,
                       env={**os.environ, "PY": str(real), "TVSTATE": str(tmp / "state.json"), "LOG": str(tmp / "log")})
    return r.returncode, (r.stdout + r.stderr).strip(), (tmp / "log").read_text(), json.loads((tmp / "state.json").read_text())

rc, out, log, st = run(ops={"AUTO_START": {"sa.bayti.home": "ignore", "sa.bayti.screensaver": "ignore"}, "APP_AUTO_START": {}})
check("a TCL G08 build (AUTO_START): both apps allowed, written to disk, the line says so",
      rc == 0 and out == "Auto-start (TCL): sa.bayti.home allow, sa.bayti.screensaver allow" and st["written"], out)
check("both op names are tried and the grant is written (G08 and G10 name it differently)", "APP_AUTO_START allow" in log and "AUTO_START allow" in log and "write-settings" in log, log)
rc, out, log, st = run(ops={"AUTO_START": {}, "APP_AUTO_START": {"sa.bayti.home": "ignore", "sa.bayti.screensaver": "ignore"}})
check("a TCL G10 build (APP_AUTO_START): allowed too", rc == 0 and "sa.bayti.home allow" in out and "!!" not in out, out)
rc, out, log, st = run(maker="Sony")
check("not a TCL: nothing is set, said in one line", rc == 0 and out == "Auto-start: not a TCL, nothing to allow" and "appops set" not in log, out + log)
rc, out, log, st = run(maker="Haier", tclpkgs=["com.tcl.bi"], ops={"AUTO_START": {"sa.bayti.home": "ignore", "sa.bayti.screensaver": "ignore"}, "APP_AUTO_START": {}})
check("a TCL-based set whose maker says otherwise (com.tcl.* packages): treated as TCL", "Auto-start (TCL)" in out, out)
rc, out, log, st = run(honour=False, ops={"AUTO_START": {"sa.bayti.home": "ignore", "sa.bayti.screensaver": "ignore"}, "APP_AUTO_START": {}})
check("a TCL that keeps refusing: a !! line that names the app and what to do", "!! Auto-start (TCL): not allowed for" in out and "sa.bayti.home(ignore)" in out, out)
rc, out, log, st = run(ops={"AUTO_START": {}, "APP_AUTO_START": {}})
check("a TCL with neither op: nothing to allow, no !! line", "!!" not in out, out)
rc, out, log, st = run("check", ops={"AUTO_START": {"sa.bayti.home": "ignore", "sa.bayti.screensaver": "allow"}, "APP_AUTO_START": {}})
check("check only reads: no appops set, and a refused app is reported", "appops set" not in log and "!! Auto-start (TCL)" in out and "sa.bayti.home(ignore)" in out, out + log)
rc, out, log, st = run(installed=["sa.bayti.screensaver"], ops={"AUTO_START": {"sa.bayti.screensaver": "ignore"}, "APP_AUTO_START": {}})
check("the home app is not installed (SKIP_LAUNCHER): only the screensaver", out == "Auto-start (TCL): sa.bayti.screensaver allow" and "appops set sa.bayti.home" not in log, out)
print("\nall auto-start checks passed" if not failed else f"\n{failed} failed")
sys.exit(1 if failed else 0)
