"""optimize/tv.py's APK upload through a flaky fake ADB connection: it drops connections, loses replies after
the command ran, and the upload is killed half way and started again. The file must arrive exactly.

    python3 tests/upload_test.py          (no TV needed; 50 random runs)
"""
import base64, hashlib, os, random, re, shutil, subprocess, sys, tempfile, types

FS = {}
JOB = {}   # name -> {"src", "dst", "left"}: a background job on the TV; it finishes after "left" looks at it
stats = {"drop": 0, "lost": 0, "polls": 0}
J = "/data/local/tmp/urtv-job-"

def job_step(name):
    """One look at a job: running, done or dead. The decode only lands when it is finished, as on a slow TV."""
    j = JOB.get(name)
    if j and j["left"] > 0:
        j["left"] -= 1; stats["polls"] += 1
        return "running %d" % (len(FS.get(j["dst"], b"")) * 100 // 700_000)
    if j:
        FS[j["dst"]] = base64.b64decode(FS[j["src"]]); FS[J + name + ".rc"] = "0"; del JOB[name]
    return "done" if J + name + ".rc" in FS else "dead"

def run(cmd):
    if "nohup sh -c" in cmd:   # start a background job, or join the one still running
        m = re.search(r"\(base64 -d (\S+) > (\S+)\) > " + re.escape(J) + r"(\w+)\.out", cmd)
        src, dst, name = m.groups()
        if name in JOB: return "joined"
        for f in (".rc", ".out"): FS.pop(J + name + f, None)
        JOB[name] = {"src": src, "dst": dst, "left": random.randint(0, 3)}
        return "started"
    m = re.search(r"if grep -q " + re.escape(J) + r"(\w+) ", cmd)
    if m: return job_step(m.group(1))
    m = re.fullmatch(r"cat " + re.escape(J) + r"(\w+)\.rc", cmd)
    if m: return FS[J + m.group(1) + ".rc"]
    m = re.fullmatch(r"cat " + re.escape(J) + r"(\w+)\.out", cmd)
    if m: return ""
    m = re.match(r"if \[ -f (\S+) \]; then head -c (\d+) \S+ > \S+ && \[ -f \S+ \] && mv \S+ \S+; fi; (.*)", cmd)
    if m:   # cut_back for i > 0: only a file that is there is cut
        if m.group(1) in FS: FS[m.group(1)] = FS[m.group(1)][:int(m.group(2))]
        cmd = m.group(3)
    out = []
    for part in [c.strip() for c in cmd.split(";")]:
        m = re.fullmatch(r"echo -n '([^']*)' >> (\S+)", part)
        if m: FS[m.group(2)] = FS.get(m.group(2), "") + m.group(1); continue
        m = re.fullmatch(r"wc -c < (\S+)", part)
        if m: out.append(str(len(FS.get(m.group(1), "")))); continue
        m = re.fullmatch(r"\[ -f (\S+) \] && wc -c < \S+ \|\| echo 0", part)
        if m: out.append(str(len(FS[m.group(1)])) if m.group(1) in FS else "0"); continue
        m = re.fullmatch(r"head -c (\d+) (\S+) > \S+ && mv \S+ \S+", part)
        if m: FS[m.group(2)] = FS[m.group(2)][:int(m.group(1))]; continue
        m = re.fullmatch(r"rm -f (.+)", part)
        if m:
            for f in m.group(1).split(): FS.pop(f, None)
            continue
        m = re.fullmatch(r"\[ -f (\S+) \] && mv (\S+) (\S+)", part)
        if m:
            if m.group(1) in FS: FS[m.group(3)] = FS.pop(m.group(2))
            continue
        m = re.fullmatch(r"\[ -f (\S+) \] && echo y", part)
        if m: out.append("y" if m.group(1) in FS else ""); continue
        m = re.fullmatch(r"\[ -d '(\S+)' \] && echo y", part)
        if m: out.append("y" if m.group(1) == "/data/local/tmp" else ""); continue
        m = re.fullmatch(r"df -h (\S+) \| tail -1", part)
        if m: out.append(m.group(1) + " 4.0G 3.0G 1.0G 75%"); continue
        m = re.fullmatch(r"md5sum (\S+)", part)
        if m:
            v = FS[m.group(1)]
            out.append(hashlib.md5(v if isinstance(v, bytes) else v.encode()).hexdigest() + "  " + m.group(1)); continue
        raise SystemExit("fake: unknown command " + part)
    return "\n".join(out)

class Dev:
    def __init__(self, *a, **k): pass
    def connect(self, **k):
        if random.random() < 0.1: stats["drop"] += 1; raise ConnectionError("refused")
    def shell(self, cmd, read_timeout_s=0):
        if random.random() < 0.08: stats["drop"] += 1; raise ConnectionResetError("dropped before")
        r = run(cmd)
        if random.random() < 0.08: stats["lost"] += 1; raise TimeoutError("reply lost")   # ran, reply lost
        return r
    def close(self): pass

for name in ["adb_shell", "adb_shell.adb_device", "adb_shell.auth", "adb_shell.auth.keygen", "adb_shell.auth.sign_pythonrsa"]:
    sys.modules[name] = types.ModuleType(name)
sys.modules["adb_shell.adb_device"].AdbDeviceTcp = Dev
sys.modules["adb_shell.auth.keygen"].keygen = lambda p: None
sys.modules["adb_shell.auth.sign_pythonrsa"].PythonRSASigner = lambda a, b: None
os.environ["TV_HOST"] = "1.2.3.4"; os.environ["TV_TRIES"] = "6"
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "optimize"))
import tv
tv.time.sleep = lambda s: None
tv.signer = lambda: None

import contextlib, io

def one(seed):
    random.seed(seed)
    FS.clear(); JOB.clear()
    data = os.urandom(700_000)
    src = os.path.join(tempfile.mkdtemp(), "x.apk")
    open(src, "wb").write(data)
    t = tv.TV()
    orig, calls = t.shell, {"n": 0}
    def dying(cmd, timeout=120):   # the process is killed at its 6th call, or (even seeds) at the first look at the decode
        calls["n"] += 1
        if calls["n"] == 6 and seed % 2 or seed % 2 == 0 and "if grep -q" in cmd and not calls.get("polled"):
            calls["polled"] = True
            raise KeyboardInterrupt
        return orig(cmd, timeout)
    t.shell = dying
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        try:
            tv.push_b64(t, src, "/data/local/tmp/x.apk")
        except KeyboardInterrupt:
            pass
        tv.push_b64(tv.TV(), src, "/data/local/tmp/x.apk")   # run again: resumes or restarts
    return FS.get("/data/local/tmp/x.apk") == data

bad = [seed for seed in range(50) if not one(seed)]
print(f"upload: {50 - len(bad)}/50 intact despite {stats['drop']} dropped connections and {stats['lost']} lost replies")
print(f"decode: run in the background and looked at {stats['polls']} times")

# the cut-back line, run by a real sh: with no file yet (0%) it used to end in «mv: bad '….b64.x': No such file or directory»
import subprocess, shutil
cutbad = []
def cut_check(name, ok, detail=""):
    print(f"{name}: " + ("ok" if ok else f"WRONG {detail}"))
    if not ok: cutbad.append(name)
if shutil.which("sh"):
    def sh_cut(i, content):
        d = tempfile.mkdtemp(); tmp = os.path.join(d, "x.apk.b64")
        if content is not None: open(tmp, "w").write(content)
        r = subprocess.run(["sh", "-c", tv.cut_back(tmp, i) + f"; echo -n 'PIECE' >> {tmp}; wc -c < {tmp}"], capture_output=True, text=True)
        return r.returncode, r.stderr.strip(), (open(tmp).read() if os.path.exists(tmp) else None), os.listdir(d)
    rc, err, body, left = sh_cut(0, None)
    cut_check("cut back at 0%, no file yet", rc == 0 and not err and body == "PIECE" and left == ["x.apk.b64"], (rc, err, body, left))
    rc, err, body, left = sh_cut(0, "AAAABBBBAAAABBBB")
    cut_check("cut back at 0%, a doubled first piece", rc == 0 and body == "PIECE" and left == ["x.apk.b64"], (body, left))
    rc, err, body, left = sh_cut(4, "AAAABBBB")
    cut_check("cut back at 4, a piece too many", rc == 0 and body == "AAAAPIECE" and left == ["x.apk.b64"] and not err, (body, err, left))
    rc, err, body, left = sh_cut(4, None)
    cut_check("cut back at 4, the file is gone", rc == 0 and not err and body == "PIECE", (rc, err, body))
cut_check("backoff: 5, 10, 15, 20, 25, 30, 30 s", [tv.backoff(i) for i in range(7)] == [5, 10, 15, 20, 25, 30, 30], [tv.backoff(i) for i in range(7)])
cut_check("tries: 6 for a command, more for an upload", tv.LONG_TRIES >= tv.TRIES)

# run_bg: a long command is started once, polled with short calls, its output and exit code come back;
# a job that never ends is given up on after the limit; a job that died without an answer is run directly.
class Fake:
    def __init__(self, polls, dead=False): self.polls, self.dead, self.calls = polls, dead, []   # dead: True at once, "later" after a while
    def shell(self, cmd, timeout=120):
        self.calls.append(cmd)
        if "nohup sh -c" in cmd: return "started"
        if "while kill -0" in cmd: return ".\n.\n.\n__rc=0\nSuccess\n"   # run directly, beside its heartbeat
        if "if grep -q" in cmd:
            if self.dead == "later" and len([c for c in self.calls if "if grep -q" in c]) > 2: return "dead"
            if self.dead is True: return "dead"
            self.polls -= 1
            return "running" if self.polls > 0 else "done"
        if cmd.startswith("cat") and cmd.endswith(".rc"): return "1\n"
        if cmd.startswith("cat"): return "Failure [INSTALL_FAILED_X]\n"
        return ""
bgbad = []
f = Fake(4)
with contextlib.redirect_stderr(io.StringIO()):
    got = tv.run_bg(f, "install", "pm install -r /data/local/tmp/x.apk")
if got != (1, "Failure [INSTALL_FAILED_X]"): bgbad.append(f"result {got}")
if sum("nohup" in c for c in f.calls) != 1: bgbad.append("started more than once")
if any("pm install" in c and "nohup" not in c for c in f.calls): bgbad.append("ran in the foreground")
# a TV that ends a shell's background jobs when the shell closes: the job is dead at the first look, so it runs directly
f = Fake(5, dead=True)
with contextlib.redirect_stderr(io.StringIO()):
    got = tv.run_bg(f, "install", "pm install -r /data/local/tmp/x.apk")
if got != (0, "Success"): bgbad.append(f"no background jobs: {got}")
if sum("while kill -0" in c for c in f.calls) != 1: bgbad.append("no background jobs: not run directly once")
# the same TV, but the job lives a few seconds first (a TCL on Android 14): still run directly, once
f = Fake(10**9, dead="later")
with contextlib.redirect_stderr(io.StringIO()):
    got = tv.run_bg(f, "install", "pm install -r /data/local/tmp/x.apk")
if got != (0, "Success"): bgbad.append(f"dies after a while: {got}")
if sum("while kill -0" in c for c in f.calls) != 1: bgbad.append("dies after a while: not run directly once")
# run directly, for real in a local shell: the heartbeat comes while the job works (a silent minute broke the connection on a Sony BRAVIA
# VH1), then its exit code and its own output, with no dots in it
class LocalSh:
    def shell(self, cmd, timeout=120):
        return subprocess.run(["sh", "-c", cmd], capture_output=True, text=True, timeout=timeout).stdout
if shutil.which("sh"):
    jd = tempfile.mkdtemp().replace("\\", "/")
    tv.HEARTBEAT_S = 1
    rc, body = tv.run_direct(LocalSh(), jd + "/job", "sleep 3; echo decoded; exit 3", 60)
    if (rc, body) != (3, "decoded"): bgbad.append(f"run directly, local shell: {(rc, body)}")
    raw = LocalSh().shell(f"(sleep 3) > {jd}/j2.out 2>&1 & p=$!; while kill -0 $p 2>/dev/null; do echo .; sleep 1; done; wait $p; echo __rc=$?")
    if raw.count(".") < 2: bgbad.append(f"run directly: no heartbeat while it works: {raw!r}")
for name, fake, limit, word in (("never ends", Fake(10**9), 30, "still running after"),):
    err = io.StringIO()
    try:
        with contextlib.redirect_stderr(err): tv.run_bg(fake, "install", "pm install -r /x.apk", limit_s=limit)
        bgbad.append(name + ": no exit")
    except SystemExit as e:
        if word not in str(e): bgbad.append(f"{name}: said {e}")
print("run_bg: " + ("ok" if not bgbad else f"WRONG {bgbad}"))

# Git Bash on Windows rewrites /data/local/tmp/x into its own folder before Python sees it: tv.py cuts it back off
tv.is_git_folder = lambda f: f in ("C:/Program Files/Git", "D:/data/Git")
real_name, tv.os.name = tv.os.name, "nt"
paths = {
    "C:/Program Files/Git/data/local/tmp/x.apk": "/data/local/tmp/x.apk",
    "C:\\Program Files\\Git\\sdcard\\shot.png": "/sdcard/shot.png",
    "D:/data/Git/data/local/tmp/x.apk": "/data/local/tmp/x.apk",   # a Git folder that itself has /data/ in it
    "C:/Users/me/data/x.apk": "C:/Users/me/data/x.apk",             # a real Windows path stays as it is
    "/data/local/tmp/x.apk": "/data/local/tmp/x.apk",
    "pm install -r /data/local/tmp/x.apk": "pm install -r /data/local/tmp/x.apk",
}
wrong = {k: tv.tv_path(k) for k, v in paths.items() if tv.tv_path(k) != v}
tv.os.name = real_name
print(f"Git Bash paths: {len(paths) - len(wrong)}/{len(paths)} put back" + (f": wrong {wrong}" if wrong else ""))
sys.exit(1 if bad or wrong or bgbad or cutbad else 0)
