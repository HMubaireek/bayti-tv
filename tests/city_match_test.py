#!/usr/bin/env python3
"""`saver.sh city NAME` takes a short form of a built-in city: CityMatch.java on the list in Cities.java.
Every full name finds itself alone; short forms, other spellings and ambiguous names behave as listed below.
Needs javac (skipped with a note without).   Run: python3 tests/city_match_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java/sa/bayti/screensaver"
NAMES = re.findall(r'\{"([^"]+)", "[\d.-]+", "[\d.-]+"\}', (SRC / "Cities.java").read_text(encoding="utf-8"))

# typed -> the names it must find (all of them, in the list's order)
CASES = [
    ("مكة", ["مكة المكرمة"]), ("مكه", ["مكة المكرمة"]), ("مكة المكرمة", ["مكة المكرمة"]),
    ("خميس", ["خميس مشيط"]), ("حفر", ["حفر الباطن"]), ("الباطن", ["حفر الباطن"]),
    ("المنورة", ["المدينة المنورة"]), ("منوره", ["المدينة المنورة"]),
    ("رياض", ["الرياض"]), ("الرياض", ["الرياض"]),            # the same name wins over «رياض الخبراء»
    ("ليلى", ["ليلى (الأفلاج)"]), ("ليلى الأفلاج", ["ليلى (الأفلاج)"]),
    ("تنورة", ["رأس تنورة"]), ("راس تنوره", ["رأس تنورة"]), ("رأس", ["رأس تنورة", "رأس الخير"]),
    ("حوطة", ["حوطة بني تميم", "حوطة سدير"]), ("بني تميم", ["حوطة بني تميم"]),
    ("وادي", ["وادي الدواسر", "وادي الفرع", "وادي النخيل"]),
    ("خرج", ["الخرج"]), ("الخرج", ["الخرج"]),
    ("", []), ("ر", []), ("zzz", []), ("مدينة", None),         # None: only that it finds several
]

javac, java = shutil.which("javac"), shutil.which("java")
if not (javac and java):
    print("SKIP CityMatch.java: no javac here (CI's Build APKs compiles it)")
    sys.exit(0)
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    queries = [q for q, _ in CASES] + NAMES
    (tmp / "queries.txt").write_text("\n".join(queries) + "\n", encoding="utf-8")
    r = subprocess.run([javac, "-encoding", "UTF-8", "-d", str(tmp), str(SRC / "CityMatch.java"), str(SRC / "Cities.java"),
                        str(SRC / "TeamSearch.java"), str(ROOT / "tests/CityMatchRun.java")], capture_output=True, text=True)
    check("CityMatch.java compiles", r.returncode == 0, r.stderr[-600:])
    if r.returncode == 0:
        r = subprocess.run([java, "-cp", str(tmp), "sa.bayti.screensaver.CityMatchRun", str(tmp / "queries.txt")], capture_output=True)
        lines = r.stdout.decode("utf-8").split("\n")
        check("CityMatch.java runs", r.returncode == 0 and len(lines) > len(queries), r.stderr.decode()[-600:])
        got = [l.split("|") if l else [] for l in lines[:len(queries)]]
        for (q, want), hit in zip(CASES, got):
            ok = len(hit) > 1 if want is None else hit == want
            check(f"{q!r} -> {want if want is not None else 'several'}", ok, hit)
        alone = [n for n, hit in zip(NAMES, got[len(CASES):]) if hit != [n]]
        check(f"all {len(NAMES)} full names find themselves alone", not alone, alone[:5])
sys.exit(1 if failed else 0)
