#!/usr/bin/env python3
"""The home screen's pills (PillText.java): what a pill shows with and without the focus, the sleep pill's name with the minutes
left, the network pill's name, on tests/pill_text_cases.tsv. Also the shape of Pill.java and its use in MainActivity: every pill
keeps its place in the row (same order, same D-pad ends), a fixed height, a ~150 ms width animation, its name as the
content description. Needs javac; without it the Java cases are skipped with a note.   Run: python3 tests/pill_text_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/sa/bayti/home"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

pill = (HOME / "Pill.java").read_text(encoding="utf-8")
main = (HOME / "MainActivity.java").read_text(encoding="utf-8")
check("Pill: the width animation takes about 150 ms", re.search(r"MS = 150", pill) is not None)
check("Pill: the name is the content description (a screen reader hears it though only the icon shows)", "setContentDescription(text)" in pill)
check("Pill: a focused pill is its own focus target (focusable, children are not)", "setFocusable(true)" in pill and "setFocusable" not in pill.split("setFocusable(true)", 1)[1])
check("Pill: only the padding and the name's window change; the pill's own size params (its height) are never touched",
      pill.count("getLayoutParams()") == 2 and "clip.getLayoutParams()" in pill and "name.getLayoutParams()" in pill)
# Bayti TV: three buttons at the left of the top bar (sources, settings, «المزيد»), the rest are tiles of the control center
check("MainActivity: the top bar's three buttons, in order: sources, settings, «المزيد»",
      "for (Pill p : new Pill[]{inputs, settings, more})" in main and 'pill("المزيد", R.drawable.ic_more)' in main)
check("MainActivity: every button is a Pill with a fixed row height (the row's height is reserved)",
      "left.addView(p, llp(-2, dp(Pill.HEIGHT), dp(10), 0, 0, 0))" in main and "TextView tasks" not in main and "TextView saver" not in main)
check("MainActivity: «المزيد» holds the network, the screensaver, «المهام», «تلاوة وأذان», the sleep timer and the favourites",
      all(f'"{t}"' in main for t in ("الشبكة", "شاشة التوقف", "المهام", "تلاوة وأذان", "مؤقت النوم", "ترتيب المفضلة")) and "ControlCenter.show(" in main)
check("MainActivity: the top bar is walked as one row across the tabs and the buttons; the other rows by moveFocusSideways",
      "int ti = topItems.indexOf(getCurrentFocus());" in main and "int next = right ? index - 1 : index + 1;" in main)
check("MainActivity: the sleep timer's minutes are on its control-center tile", "Digits.of(PillText.sleep(m))" in main
      and "PillText.network(wired, wifi, ssid)" in main)

if not shutil.which("javac"):
    print("SKIP pill text: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/pill_text_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(HOME / "PillText.java"), str(HOME / "DigitsText.java"), str(ROOT / "tests/PillTextRun.java")], check=True,
                   capture_output=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.bayti.home.PillTextRun", str(ROOT / "tests/pill_text_cases.tsv")],
                         check=True, capture_output=True).stdout
got = out.decode("utf-8").split("\n")
for c, g in zip(cases, got):
    check(f"pill text {c[0]}: {c[5]}", g == c[4], f"{g!r}, wanted {c[4]!r}")
check("every case answered", len(got) > len(cases), (len(got), len(cases)))
sys.exit(1 if failed else 0)
