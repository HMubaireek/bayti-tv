#!/usr/bin/env python3
"""The home screen's «يُتلى الآن» card: its words (NowPlayingText.java, on tests/now_playing_cases.tsv) and how it is wired (source checks:
MusicService says when the state changes, the card listens, nothing polls; OK, ◀ ▶ and a long OK; its place in the pills row; it goes
with the playback). Needs javac for the words; without it they are skipped with a note.   Run: python3 tests/now_playing_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/sa/bayti/home"
TIL = HOME / "tilawah"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok

main = (HOME / "MainActivity.java").read_text(encoding="utf-8")
card = (HOME / "NowCard.java").read_text(encoding="utf-8")
service = (TIL / "MusicService.java").read_text(encoding="utf-8")
now = (TIL / "NowPlaying.java").read_text(encoding="utf-8")

check("MusicService: announces a change to this app only, once per change (a signature of what the card shows), never on the clock's tick",
      'sendBroadcast(new Intent(NowPlaying.CHANGED).setPackage(getPackageName()))' in service and "if (now.equals(announced)) return;" in service)
check("MusicService: announce() is called from updateSession (every change goes through it) and when playback stops",
      re.search(r"private void updateSession\(\) \{\s*announce\(\);", service) is not None
      and re.search(r"sleepUntil = 0;\s*announce\(\);\s*foregroundStarted = false;", service) is not None)
check("MainActivity: the card listens for that broadcast and re-reads on resume; nothing polls (no postDelayed for it, no new timer)",
      "registerReceiver(nowPlayingChanged, new IntentFilter(NowPlaying.CHANGED))" in main and "unregisterReceiver(nowPlayingChanged)" in main
      and "updateNowPlaying();\n        homeCard.refresh();" in main and "postDelayed" not in card and "Handler" not in card)
check("MainActivity: the sleep timer's minutes follow the clock's existing minute tick, not a timer of their own",
      "updateNowPlaying();   // the minutes left of its sleep timer" in main)
check("NowPlaying: shown only for a surah or a station, never for the adhan or nothing",
      "source != Prefs.SOURCE_SURAH && !radio) return new State(false" in now)
check("MainActivity: OK pauses or plays, a long OK opens the player",
      "nowCard.setOnClickListener(v -> NowPlaying.toggle(this))" in main and "nowCard.setOnLongClickListener(v -> { startActivity(NowPlaying.player(this)); return true; })" in main)
check("MainActivity: ◀ is the previous surah and ▶ the next while the card has the focus, for a surah only (a station: ◀ goes on to the pills)",
      "NowPlaying.prev(this); else NowPlaying.next(this)" in main and "if (s.show && !s.radio) {" in main and "KEYCODE_DPAD_LEFT) NowPlaying.prev" in main)
check("MainActivity: it is the first child of the pills row, a pill's height, hidden until something plays: the pills do not move",
      'right.addView(nowCard, 0, llp(-2, dp(Pill.HEIGHT), dp(10), 0, 0, 0))' in main and "nowCard.setVisibility(View.GONE);" in main)
check("MainActivity: it goes with the playback, and the focus goes back to the first pill if it had it",
      "nowCard.hasFocus() && !pills.isEmpty()) pills.get(0).requestFocus()" in main)
check("MainActivity: it takes the focus like a pill (D-pad order: ▶ from the first pill reaches it; down goes to the cards or the dock)",
      "nowCard.setNextFocusDownId(card != null ? card.getId()" in main and "nowCard.setNextFocusLeftId(pills.get(0).getId())" in main)
# the screensaver's own «يُتلى الآن» card (item 6): the launcher answers a signed-only provider, the app gives the page one read-only call
manifest_h = (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
manifest_s = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
provider = (TIL / "NowPlayingProvider.java").read_text(encoding="utf-8")
bridge = (ROOT / "android/app/src/main/java/sa/bayti/screensaver/NowPlayingBridge.java").read_text(encoding="utf-8")
screenview = (ROOT / "android/app/src/main/java/sa/bayti/screensaver/ScreenView.java").read_text(encoding="utf-8")
check("screen card: the launcher's provider is read-only, one row only while a surah or a station PLAYS (not paused, not the adhan)",
      "if (s.show && s.playing) out.addRow" in provider and "return null;" in provider.split("getType")[1].split("}")[0]
      and "insert(Uri uri, ContentValues v) { return null; }" in provider)
check("screen card: the provider needs a signature permission only the screensaver (same key) holds",
      'android:name="sa.bayti.home.READ_NOW_PLAYING"' in manifest_h and 'android:protectionLevel="signature"' in manifest_h
      and 'android:authorities="sa.bayti.home.nowplaying"' in manifest_h and 'android:readPermission="sa.bayti.home.READ_NOW_PLAYING"' in manifest_h
      and 'uses-permission android:name="sa.bayti.home.READ_NOW_PLAYING"' in manifest_s)
check("screen card: the page gets one call, window.UrtvNative.nowPlaying(), on the screen only (not on the settings' previews); errors give an empty answer",
      '@JavascriptInterface' in bridge and bridge.count("@JavascriptInterface") == 1 and "catch (Exception ignored)" in bridge
      and 'if (screen) w.addJavascriptInterface(new NowPlayingBridge(c), "UrtvNative")' in screenview)
check("screen card: the page asks once at the start and then once a minute, no faster",
      "nowPlayingTick();\n  setInterval(nowPlayingTick, 60*1000);" in (ROOT / "src/template.html").read_text(encoding="utf-8"))
check("NowCard: a pill's height (Pill.HEIGHT) is kept by the row, not the card; its focus shows the keys on its second line",
      "sub.setText(focused ? hintText : subText)" in card and "playing ? pause : play" in card)

if not shutil.which("javac"):
    print("SKIP now playing text: no javac here")
    sys.exit(1 if failed else 0)
cases = [l.split("\t") for l in (ROOT / "tests/now_playing_cases.tsv").read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(TIL / "NowPlayingText.java"), str(HOME / "DigitsText.java"), str(ROOT / "tests/NowPlayingTextRun.java")], check=True, capture_output=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.bayti.home.tilawah.NowPlayingTextRun", str(ROOT / "tests/now_playing_cases.tsv")],
                         check=True, capture_output=True).stdout
got = out.decode("utf-8").split("\n")
for c, g in zip(cases, got):
    check(f"now playing {c[0]}: {c[5]}", g == c[4], f"{g!r}, wanted {c[4]!r}")
check("every case answered", len(got) > len(cases), (len(got), len(cases)))
sys.exit(1 if failed else 0)
