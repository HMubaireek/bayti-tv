"""optimize/reset.sh step 2: the screensaver settings optimize/backup.sh saved (components, enabled, screen_off_timeout) are put
back, the components too. The step's own lines are cut out of the script and run against a stand-in `tv`. No TV needed.

    python3 tests/reset_screensaver_test.py
"""
import os, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(here, "..", "optimize", "reset.sh"), encoding="utf-8").read()
step = src[src.index("# install.sh switched"):src.index('echo "3/4')]
tmp = tempfile.mkdtemp()
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

def run(backup):
    os.makedirs(os.path.join(tmp, "optimize", "run"), exist_ok=True)
    open(os.path.join(tmp, "optimize", "run", "backup.txt"), "w", encoding="utf-8", newline="").write(backup)
    out = os.path.join(tmp, "sent.txt"); open(out, "w").close()
    script = f'tv() {{ cat >> "{out}"; }}\ncd "{tmp}"\n' + step
    r = subprocess.run(["bash", "-c", script], capture_output=True, text=True)
    return r.returncode, open(out, encoding="utf-8").read().splitlines(), r.stderr

B = "== model\r\nTV\r\n13\r\n== screensaver\r\n{}\r\n{}\r\n{}\r\n== animation\r\n1\r\n"
rc, sent, err = run(B.format("com.google.android.apps.tv.dreamx/.DreamService", "1", "600000"))
check("the saved screensaver component is put back", "settings put secure screensaver_components 'com.google.android.apps.tv.dreamx/.DreamService'" in sent, str(sent) + err)
check("and enabled and the timeout with it", "settings put secure screensaver_enabled 1" in sent and "settings put system screen_off_timeout 600000" in sent, str(sent))
rc, sent, err = run(B.format("null", "null", "null"))
check("null (never set): nothing is put back for the components, the others are deleted", not any("screensaver_components" in l for l in sent)
      and "settings delete secure screensaver_enabled" in sent and "settings delete system screen_off_timeout" in sent, str(sent))
rc, sent, err = run(B.format("sa.bayti.screensaver/.ScreenDream", "1", "300000"))
check("a backup taken with ours already set does not put ours back", not any("screensaver_components" in l for l in sent), str(sent))
rc, sent, err = run(B.format("x'; reboot; '", "1", "300000"))
check("a value that is not a component is not sent", not any("screensaver_components" in l for l in sent) and rc == 0, str(sent) + err)
rc, sent, err = run(B.format("a.b/.C,d.e/.F", "1", "300000"))
check("a list of components is put back whole", "settings put secure screensaver_components 'a.b/.C,d.e/.F'" in sent, str(sent))
sys.exit(1 if failed else 0)
