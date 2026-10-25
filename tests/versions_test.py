#!/usr/bin/env python3
"""The screensaver app's version comparison (android/app .../Versions.java) on a plain JVM: major first, then minor, as numbers.
Needs javac; without it, skipped with a note.   Run: python3 tests/versions_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if not shutil.which("javac"):
    print("SKIP versions: no javac here")
    sys.exit(0)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(ROOT / "android/app/src/main/java/sa/bayti/screensaver/Versions.java"),
                    str(ROOT / "tests/VersionsRun.java")], check=True)
    run = subprocess.run(["java", "-cp", tmp, "sa.bayti.screensaver.VersionsRun"], capture_output=True)
out = run.stdout.decode("utf-8")
print(out, end="")
sys.exit(1 if run.returncode != 0 or "FAIL" in out else 0)
