"""optimize/tv.py: which commands count as only reading the TV (the others are checked against the saved TV first), and how
several arguments are put together. No TV needed.

    python3 tests/tv_read_only_test.py
"""
import os, sys, types

for name in ["adb_shell", "adb_shell.adb_device", "adb_shell.auth", "adb_shell.auth.keygen", "adb_shell.auth.sign_pythonrsa"]:
    sys.modules[name] = types.ModuleType(name)
sys.modules["adb_shell.adb_device"].AdbDeviceTcp = object
sys.modules["adb_shell.auth.keygen"].keygen = lambda p: None
sys.modules["adb_shell.auth.sign_pythonrsa"].PythonRSASigner = lambda a, b: None
os.environ["TV_HOST"] = "192.0.2.5"
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "optimize"))
import tv

failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

for cmd in ("wm size", "wm density", "date", "date +%s", "date -u +%Y", "getprop ro.product.model", "settings get secure screensaver_enabled",
            "pm list packages -d", "dumpsys package sa.bayti.home | grep -m1 versionName", "cat /proc/meminfo", "echo hi"):
    check(f"reading: {cmd}", tv.is_read_only(cmd))
for cmd in ("wm size 1280x720", "wm size reset", "wm density 240", "wm density reset", "date -s 20260101.120000", "date 010112002026",
            "date +%s; date -s 20260101", "settings put system screen_off_timeout 60000", "pm disable-user com.x", "reboot",
            "echo hi > /sdcard/x", "wm size 1280x720; getprop a"):
    check(f"changing: {cmd}", not tv.is_read_only(cmd))
check("one argument is a whole command", tv.shell_line(["pm list packages sa.bayti"]) == "pm list packages sa.bayti")
check("several arguments are words, each quoted", tv.shell_line(["pm", "list", "packages", "sa.bayti"]) == "pm list packages sa.bayti")
check("a word with a space or a ; stays one word", tv.shell_line(["settings", "put", "secure", "x", "a b; reboot"]) == "settings put secure x 'a b; reboot'",
      tv.shell_line(["settings", "put", "secure", "x", "a b; reboot"]))
check("and so it is judged as the change it is", not tv.is_read_only(tv.shell_line(["wm", "size", "1280x720"])))
sys.exit(1 if failed else 0)
