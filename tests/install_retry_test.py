"""optimize/install.sh launcher when ADB drops during the switch: a stub tv.py, no TV needed.

    python3 tests/install_retry_test.py

Each case copies install.sh into a scratch tree, puts a fake `python` (PY), `curl` and `sleep` first, and checks
what the script printed, how it ended and how often it armed the safety switch.
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

STUB_PY = r'''#!/usr/bin/env bash
# fake: python -I optimize/tv.py <args>; state files live in $T
shift 2
log() { echo "$*" >> "$T/log"; }
[ "$1" = --bg ] && { shift 2; set -- "$*"; case "$1" in "pm install"*) log bg ;; esac; }   # --bg NAME 'cmd': runs on the TV in the background
case "$1" in
  --push-b64) exit 0 ;;
  --batch)
    in=$(cat)
    case "$in" in
      *nohup*) log arm; rm -f "$T/home_ok" ;;
      *"am force-stop com.android.tv.settings"*)
        case "$in" in *"disable-user --user 0 com.google.android.tungsten.setupwraith"*) log wraith-off ;; esac
        n=$(( $(cat "$T/switches" 2>/dev/null || echo 0) + 1 )); echo $n > "$T/switches"
        if [ "$ILLEGAL" = stock ]; then   # Nokia Streaming Box 8000: the stock home cannot be turned off
          log illegal; printf '%s\n' '$ cmd role add-role-holder x' 'ok' '$ pm disable-user --user 0 com.google.android.apps.tv.launcherx' 'This command is illegal' '$ input keyevent KEYCODE_HOME'
          exit 0
        fi
        if [ "$n" -le "$FAIL_SWITCHES" ]; then   # the connection drops in this batch
          [ "$FIRST_DROP" = ours ] && [ "$n" = 1 ] && echo ours > "$T/home"
          [ "$FIRST_DROP" = stock ] && [ "$n" = 1 ] && echo stock > "$T/home"
          log drop; exit 1
        fi
        echo ours > "$T/home" ;;
      *) log other-batch ;;
    esac
    exit 0 ;;
esac
case "$1" in
  *launcherx*) echo "package:com.google.android.apps.tv.launcherx" ;;
  "settings get global verifier_verify_adb_installs") cat "$T/verifier" 2>/dev/null || echo null ;;
  "settings put global verifier_verify_adb_installs"*) log "put-${1##* }"; echo "${1##* }" > "$T/verifier" ;;
  "settings delete global verifier_verify_adb_installs") log delete; rm -f "$T/verifier" ;;
  "pm install -i com.android.vending"*) log vending; touch "$T/stays"; echo Success ;;
  "pm list packages sa.bayti."*)
    pkg=${1##* }
    if [ -n "$VANISH" ] && { [ "$VANISH" = always ] || [ ! -f "$T/stays" ]; }; then log gone; else echo "package:$pkg"; fi ;;
  "pm install"*)
    if [ -n "$VERIFY" ] && { [ "$VERIFY" = always ] || [ "$(cat "$T/verifier" 2>/dev/null)" != 0 ]; }; then
      log refused; echo "Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed]"
    else log installed; echo Success; fi ;;
  "getprop ro.serialno")
    n=$(( $(cat "$T/polls" 2>/dev/null || echo 0) + 1 )); echo $n > "$T/polls"
    [ "$n" -le "$DOWN_POLLS" ] && exit 1
    echo 0123456789 ;;
  "cmd package query-activities"*) [ "$WRAITH_HOME" = yes ] && echo "com.google.android.tungsten.setupwraith/.RecoveryActivity"; echo "$QUERY_EXTRA" ;;
  "dumpsys wifi") case "$WIFI" in owned) echo "cname=com.google.android.tungsten.setupwraith" ;; other) echo "cname=android.uid.system" ;; esac ;;
  "pm disable-user --user 0 com.google.android.tungsten.setupwraith") log wraith-off ;;
  *resolve-activity*) [ "$(cat "$T/home" 2>/dev/null)" = ours ] && echo sa.bayti.home/.MainActivity || echo stock/.Home ;;
  *dumpsys*) [ "$(cat "$T/home" 2>/dev/null)" = ours ] && echo "mResumedActivity: sa.bayti.home" ;;
  "touch /data/local/tmp/home_ok") log disarm; touch "$T/home_ok" ;;
esac
exit 0
'''

STUB_CURL = r'''#!/bin/sh
# fake curl; CURL_MODE=revoke: a Windows (Schannel) curl that fails until --ssl-revoke-best-effort is given
[ "$1" = -V ] && { echo "curl 8.4.0 (x86_64-pc-win32) Schannel"; exit 0; }
[ "$1" = --help ] && { echo "--ssl-revoke-best-effort"; exit 0; }
ok=0; for a in "$@"; do [ "$a" = --ssl-revoke-best-effort ] && ok=1; done
if [ "$CURL_MODE" = revoke ] && [ $ok = 0 ]; then
  echo "curl: (35) schannel: next InitializeSecurityContext failed: CRYPT_E_NO_REVOCATION_CHECK" >&2; exit 35
fi
echo x >> "$T/curls"
while [ $# -gt 0 ]; do [ "$1" = -o ] && : > "$2"; shift; done
'''


def run(name, fail_switches, first_drop, down_polls=0, verify="", verifier=None, curl_mode="", vanish="", illegal="", wraith_home="", wifi=""):
    t = Path(tempfile.mkdtemp())
    tree, bin_ = t / "tree", t / "bin"
    (tree / "optimize" / "lists").mkdir(parents=True); bin_.mkdir()
    shutil.copy(ROOT / "optimize" / "install.sh", tree / "optimize")
    shutil.copy(ROOT / "optimize" / "deadman.sh", tree / "optimize")
    (tree / "optimize" / "lists" / "home-replaced.txt").write_text("# none\n")
    for f, body in (("py", STUB_PY), ("curl", STUB_CURL),
                    ("sleep", "#!/bin/sh\nexit 0\n")):
        (bin_ / f).write_text(body); (bin_ / f).chmod(0o755)
    if verifier is not None:
        (t / "verifier").write_text(verifier + "\n")
    env = dict(os.environ, WRAITH_HOME=wraith_home, WIFI=wifi, ILLEGAL=illegal, VERIFY=verify, VANISH=vanish, CURL_MODE=curl_mode, PATH=f"{bin_}:{os.environ['PATH']}", PY=str(bin_ / "py"), T=str(t),
               FAIL_SWITCHES=str(fail_switches), FIRST_DROP=first_drop, DOWN_POLLS=str(down_polls))
    r = subprocess.run(["bash", str(tree / "optimize" / "install.sh"), "launcher"], env=env, capture_output=True, text=True)
    log = (t / "log").read_text().split() if (t / "log").exists() else []
    if (t / "verifier").exists():
        log.append("final-" + (t / "verifier").read_text().strip())
    shutil.rmtree(t)
    return r.returncode, r.stdout + r.stderr, log

AGAIN = "the TV is back, trying the switch again"
CHECK = "already set: checking it"
cases = [
    ("no drop", run("a", 0, ""), lambda c, o, l: c == 0 and "ADB dropped" not in o and l.count("arm") == 1 and l.count("bg") == 1 and l.count("installed") == 1),
    ("drop, stock home back: switch again", run("b", 1, "stock", down_polls=2),
     lambda c, o, l: c == 0 and AGAIN in o and l.count("arm") == 2 and l.count("drop") == 1 and "Launcher is home" in o),
    ("drop, new home already set: only verify", run("c", 1, "ours"),
     lambda c, o, l: c == 0 and CHECK in o and l.count("arm") == 1 and l.count("disarm") == 1),
    ("two drops: gives up, exits 1", run("d", 2, "stock"),
     lambda c, o, l: c == 1 and l.count("arm") == 2 and "!! The safety switch put the stock home back" in o),
    ("TV never answers: exits 1", run("e", 1, "stock", down_polls=99),
     lambda c, o, l: c == 1 and "did not come back within 6 minutes" in o and l.count("arm") == 1),
    ("Play Protect refuses: check off for the install, old value back", run("f", 0, "", verify="once", verifier="1"),
     lambda c, o, l: c == 0 and "Play Protect refused" in o and "(was: 1)" in o and l.count("refused") == 1
     and l.count("installed") == 1 and l[l.index("put-0"):].count("put-1") == 1 and l[-1] == "final-1"),
    ("Play Protect refuses, setting unset: deleted again", run("g", 0, "", verify="once"),
     lambda c, o, l: c == 0 and "(was: null)" in o and "delete" in l and "final-0" not in l and "final-1" not in l),
    ("Play Protect refuses twice: one retry, old value still restored", run("h", 0, "", verify="always", verifier="1"),
     lambda c, o, l: c == 1 and l.count("refused") == 2 and "Install of urtv-home.apk failed" in o and l[-1] == "final-1"),
    ("Windows revocation error: retried with --ssl-revoke-best-effort", run("j", 0, "", curl_mode="revoke"),
     lambda c, o, l: c == 0 and "without that check" in o and "Play Protect refused" not in o),
    ("the TV removes the app after a plain install: once more as the Play Store", run("k", 0, "", vanish="plain"),
     lambda c, o, l: c == 0 and "installing it again as if from the Play Store" in o and l.count("installed") == 1
     and l.count("vending") == 1 and l.count("gone") == 1 and "Launcher is home" in o),
    ("the TV removes it every time: one retry, then it says so", run("l", 0, "", vanish="always"),
     lambda c, o, l: c == 1 and l.count("vending") == 1 and "Install of urtv-home.apk failed" in o and "removed by this TV" in o),
    ("a normal install: no Play Store installer, no second install", run("m", 0, ""),
     lambda c, o, l: c == 0 and "vending" not in l and l.count("installed") == 1 and "Play Store" not in o),
    ("the TV refuses to turn its own home off («This command is illegal»): the switch is skipped and said", run("n", 0, "", illegal="stock"),
     lambda c, o, l: c == 0 and "Home switch skipped" in o and "Nokia" in o and "Launcher is home" not in o and l.count("illegal") == 1
     and "other-batch" in l and "!!" not in o),
    ("Play Protect refuses: the Arabic words for the person, «تثبيت على أي حال»", run("o", 0, "", verify="once", verifier="1"),
     lambda c, o, l: c == 0 and "تثبيت على أي حال" in o),
    ("Play Protect refuses twice: tells the person what to press and not to switch the verifier off", run("p", 0, "", verify="always", verifier="1"),
     lambda c, o, l: c == 1 and "تثبيت على أي حال" in o and "package_verifier_enable" in o),
    ("the setup wizard does not answer Home: it is not turned off", run("q", 0, ""),
     lambda c, o, l: c == 0 and "wraith-off" not in l and "Launcher is home" in o),
    ("the wizard answers Home and the Wi-Fi is the system's: turned off", run("r", 0, "", wraith_home="yes", wifi="other"),
     lambda c, o, l: c == 0 and l.count("wraith-off") == 1 and "Launcher is home" in o),
    ("the wizard answers Home and owns the Wi-Fi: nothing is changed, the person is told what to do", run("s", 0, "", wraith_home="yes", wifi="owned"),
     lambda c, o, l: c == 1 and "wraith-off" not in l and "arm" not in l and "Forget" in o and "تم" in o and "!!" in o),
    ("the wizard answers Home but the TV does not say who owns the Wi-Fi: the wizard stays on", run("t", 0, "", wraith_home="yes", wifi=""),
     lambda c, o, l: c == 0 and "wraith-off" not in l),
    ("no refusal: the setting is never touched", run("i", 0, "", verifier="1"),
     lambda c, o, l: c == 0 and "put-0" not in l and "Play Protect refused" not in o),
]
bad = [n for n, (c, o, l), ok in cases if not ok(c, o, l)]
for n, (c, o, l), ok in cases:
    print(f"install.sh launcher: {n}: " + ("ok" if ok(c, o, l) else f"WRONG (exit {c}, log {l})\n{o}"))
sys.exit(1 if bad else 0)
