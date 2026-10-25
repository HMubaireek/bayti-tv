#!/usr/bin/env python3
"""Which page the screensaver loads: the newer by build stamp, not by file time (PageStamp.java on tests/page_stamp_cases.tsv), the stamp is in
dist/tv.html and in the page the app carries, and Updater / Prefs use it.   Run: python3 tests/page_stamp_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "android/app/src/main/java/sa/bayti/screensaver"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

html = (ROOT / "dist/tv.html").read_text(encoding="utf-8")
m = re.search(r'<meta name="urtv-build" content="(\d{14})"', html[:4096])
check("dist/tv.html carries its build stamp in the first 4 KB (14 digits, UTC)", bool(m), html[:300])
prefs, upd = (APP / "Prefs.java").read_text(encoding="utf-8"), (APP / "Updater.java").read_text(encoding="utf-8")
check("Prefs.pageBase picks the downloaded page by stamp, not by file time", "PageStamp.newer(Updater.fileStamp(remote), Updater.assetStamp(c))" in prefs and "lastModified" not in prefs and "lastUpdateTime" not in prefs)
check("Updater does not keep a download whose stamp is older than the app's own page", "PageStamp.newer(assetStamp(c), PageStamp.of(head))" in upd)
check("the stamp is in the first bytes the Updater reads (4 KB) and the app reads (8 KB)", bool(m) and html.index("urtv-build") < 4000)

if not shutil.which("javac"):
    print("SKIP page stamp: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/page_stamp_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(APP / "PageStamp.java"), str(ROOT / "tests/PageStampRun.java")], check=True, capture_output=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.bayti.screensaver.PageStampRun", str(ROOT / "tests/page_stamp_cases.tsv")],
                         check=True, capture_output=True).stdout
for c, g in zip(cases, out.decode("utf-8").split("\n")):
    want = c[2] if c[0] == "of" else c[3]
    check(f"PageStamp {c[0]}: {c[-1]}", g == want, f"{g!r} wanted {want!r}")
sys.exit(1 if failed else 0)
