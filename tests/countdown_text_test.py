#!/usr/bin/env python3
"""The countdown's row in the settings (CountdownText.java): «رحلة (٣١ ديسمبر ٢٠٢٦)», no «·» beside a digit, on
tests/countdown_text_cases.tsv. Needs javac; without it, skipped with a note.   Run: python3 tests/countdown_text_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if not shutil.which("javac"):
    print("SKIP countdown text: no javac here")
    sys.exit(0)
cases = [l.split("\t") for l in (ROOT / "tests/countdown_text_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(ROOT / "android/app/src/main/java/sa/bayti/screensaver/CountdownText.java"),
                    str(ROOT / "android/app/src/main/java/sa/bayti/screensaver/DigitsText.java"),
                    str(ROOT / "tests/CountdownTextRun.java")], check=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.bayti.screensaver.CountdownTextRun", str(ROOT / "tests/countdown_text_cases.tsv")],
                         check=True, capture_output=True).stdout
got = out.decode("utf-8").split("\n")
failed = 0
for c, g in zip(cases, got):
    ok = g == c[2]
    print(("PASS " if ok else "FAIL ") + f"countdown text: {c[3]}" + ("" if ok else f"  -> {g!r}, wanted {c[2]!r}"))
    failed += not ok
if len(got) < len(cases):
    print("FAIL fewer answers than cases")
    failed += 1
sys.exit(1 if failed else 0)
