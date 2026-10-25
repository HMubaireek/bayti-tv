#!/usr/bin/env bash
# The whole setup in one command, made for slow or flaky ADB: every finished step is remembered, so after
# any failure just run it again and it carries on where it stopped.
#   optimize/run.sh            do the next steps
#   optimize/run.sh status     what is done
#   optimize/run.sh again      redo every step on the same TV, as after a TV system update put things back
#                              (the apps' settings stay: installing over keeps them; the old summary.md
#                              becomes summary-<n>.md so the new run's before/after starts clean)
#   SKIP_LAUNCHER=1 optimize/run.sh   keep the TV's own home screen (firmware that won't let another app be Home)
#   SKIP_EXTRAS=1 optimize/run.sh     without TV Bro and SmartTube
#   (a different TV than the one the setup started on is refused; for a new TV: optimize/venv/bin/python -I optimize/tv.py --new-tv)
# Before: optimize/setup.sh and optimize/find-tv.sh. Optional packages the household gave up go in
# optimize/lists/mine.txt (one per line, picked from lists/tcl-optional.txt) before running.
# Results in optimize/run/: summary.md (before/after), ram-*.txt, backup.txt, report.md, steps.log (times), run.log.
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
RUN=optimize/run
mkdir -p "$RUN"
tv() { "$PY" -I optimize/tv.py "$@"; }

# One run at a time: a second one would take the first for another TV and redo everything. The lock is a file with
# the PID of the run; a PID that is gone means it died, and the lock is taken over.
LOCK="$RUN/.lock"
if [ "${1:-}" != status ]; then
  for _ in 1 2; do
    if ( set -C; echo $$ > "$LOCK" ) 2> /dev/null; then break; fi
    other=$(cat "$LOCK" 2> /dev/null || true)
    if [ -n "$other" ] && kill -0 "$other" 2> /dev/null; then
      echo "run.sh: not started: another run is working on this TV (process $other). Wait for it to end, then run the same command again."
      echo "(Its progress: optimize/run.sh status. If it is surely gone, delete $LOCK.)"
      exit 1
    fi
    echo "(a lock left by a run that is gone: taken over)"
    rm -f "$LOCK"
  done
  [ "$(cat "$LOCK" 2> /dev/null || true)" = "$$" ] || { echo "run.sh: not started: could not take the lock $LOCK"; exit 1; }
# when each step started, ended or stopped: optimize/share.sh turns it into how long each took
stamp() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$RUN/steps.log"; }
current=start
finish() {
  local rc=$?
  trap - EXIT
  [ "$(cat "$LOCK" 2> /dev/null || true)" = "$$" ] && rm -f "$LOCK"
  if [ "$rc" -eq 0 ]; then echo "run.sh: done"
  elif [ "$rc" -eq 3 ] && [ "$current" = precheck ]; then stamp "stopped $current (not an Android TV)"; echo "run.sh: stopped at precheck: this device is not an Android TV, nothing was changed (see above)"
  else stamp "stopped $current (exit $rc)"; echo "run.sh: stopped at $current, run the same command again"; fi
  exit "$rc"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
fi

STEPS=(precheck probe backup keep before before-saver safe mine community tweaks screensaver launcher extras autostart after after-saver report)
title() {
  case "$1" in
    precheck)    echo "Check it is an Android TV with a WebView the screensaver can run on" ;;
    probe)       echo "Connect and identify the TV" ;;
    backup)      echo "Save the TV's current state" ;;
    keep)        echo "Turn protected features back on if anything turned them off" ;;
    before)      echo "Measure before (restart + 3 min)" ;;
    before-saver) echo "Measure the TV's own screensaver (1 min)" ;;
    safe)        echo "Disable telemetry, ads and demos (lists/tcl-safe.txt)" ;;
    mine)        echo "Disable the household's optional picks (lists/mine.txt)" ;;
    community)   echo "A community list, if this model has one and ours doesn't know it" ;;
    tweaks)      echo "Speed and memory tweaks" ;;
    screensaver) echo "Install شاشة بيتي" ;;
    launcher)    echo "Install the home screen (safety switch on)" ;;
    extras)      echo "Install TV Bro (web browser) and SmartTube (YouTube without ads)" ;;
    autostart)   echo "Let TCL start our apps at boot (auto-start)" ;;
    after)       echo "Measure after (restart + 3 min)" ;;
    after-saver) echo "Measure our screensaver (1 min)" ;;
    report)      echo "Write the report" ;;
  esac
}

if [ "${1:-}" = again ]; then   # a TV system update can re-enable what we turned off and take Home back
  rm -f "$RUN"/.done-* "$RUN/steps.log"
  kept=
  if [ -f "$RUN/summary.md" ]; then   # measure.sh appends: a second run's before/after lines would double up
    n=1; while [ -e "$RUN/summary-$n.md" ]; do n=$((n + 1)); done
    mv "$RUN/summary.md" "$RUN/summary-$n.md"; kept=" The last run's summary.md is now summary-$n.md."
  fi
  echo "Marks cleared: every step runs again (the backup from the first run is kept).$kept"
  shift
fi
if [ "${1:-}" = status ]; then
  for s in "${STEPS[@]}"; do printf '%s %s\n' "$([ -f "$RUN/.done-$s" ] && echo '✓' || echo '·')" "$(title "$s")"; done
  if [ -f "$RUN/report.md" ]; then   # the report's !! lines, so nobody has to grep it by hand
    grep '!!' "$RUN/report.md" | sed 's/^/report: /' || echo "report: no !! lines"
  fi
  exit 0
fi

exec > >(tee -a "$RUN/run.log") 2>&1   # everything it says, for optimize/share.sh: its !! lines and dropped connections

# Is this the TV the setup started on? Two devices can answer on one Wi-Fi (a box and the TV). The first TV seen is
# remembered in $RUN/tv-id.json; another one stops here, before the backup (which would overwrite the first TV's).
tv --check

check_list() {   # never disable what UAD-ng rates Unsafe; show everything that is not "Recommended"
  local out
  out=$("$PY" -I optimize/check-packages.py "$1")
  echo "$out" | grep -v '^Recommended' | sed 's/^/   /' || true
  if echo "$out" | grep -q '^Unsafe'; then echo "!! $1 has a package rated Unsafe. Take it out and run again."; exit 1; fi
}

report() {
  echo "# TV setup report — $(date '+%Y-%m-%d %H:%M')"
  echo
  grep -E 'model|version.release|display.id' "$RUN/tv.txt" || true
  echo
  optimize/precheck.sh report 2> /dev/null || true   # «Device:» and «WebView:» (a !! line if the WebView is too old)
  echo
  cat "$RUN/summary.md"
  "$PY" -I optimize/heavy.py "$RUN/summary.md" || true   # the TV's own screensaver against ours: a !! advice line if ours is heavy
  echo
  echo "Features that must stay on:"
  for p in com.tcl.airplay2 com.google.android.apps.mediashell com.tcl.miracast com.google.android.katniss \
           com.tcl.tvinput com.android.vending com.google.android.gms; do
    if tv "pm list packages -d $p" | grep -qx "package:$p"; then echo "- !! $p is DISABLED"
    elif tv "pm list packages $p" | grep -qx "package:$p"; then echo "- ok: $p"
    fi
  done
  echo
  echo "Home: $(tv "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN" | tail -1)"
  optimize/autostart.sh check 2> /dev/null || true   # TCL: «Auto-start (TCL): …» or a !! line
  [ ! -f "$RUN/home-skipped.txt" ] || cat "$RUN/home-skipped.txt"   # a TV that won't let its home be switched (Nokia Streaming Box 8000)
  echo "Screensaver: $(tv "settings get secure screensaver_components")"
  echo "Versions: screensaver $(tv "dumpsys package sa.bayti.screensaver 2> /dev/null | grep -m1 versionName" | sed 's/.*=//'), home $(tv "dumpsys package sa.bayti.home 2> /dev/null | grep -m1 versionName" | sed 's/.*=//')"
  echo "Extras: TV Bro $([ -n "$(tv "pm list packages com.phlox.tvwebbrowser")" ] && echo yes || echo no), SmartTube $([ -n "$(tv "pm list packages org.smarttube.stable")" ] && echo yes || echo no)"
  local picks
  picks=$(grep -cvE '^\s*(#|$)' optimize/lists/mine.txt 2>/dev/null || true)
  echo "Optional picks (lists/mine.txt): ${picks:-0}"
  echo
  echo "Packages from our lists (off: disabled, on: still on, absent: not on this TV):"
  local disabled installed l pkg state
  disabled=$(tv "pm list packages -d" | tr -d '\r')
  installed=$(tv "pm list packages" | tr -d '\r')
  for l in tcl-safe tcl-optional keep home-replaced; do
    for pkg in $(grep -vE '^\s*(#|$)' "optimize/lists/$l.txt" | awk '{print $1}'); do
      if grep -qx "package:$pkg" <<< "$disabled"; then state=off
      elif grep -qx "package:$pkg" <<< "$installed"; then state=on
      else state=absent; fi
      echo "- pkg $state: $pkg ($l)"
    done
  done
  echo "Turned off by the setup (name | package | list). One the person wants back: optimize/keep.sh restore <package>"
  optimize/keep.sh off 2> /dev/null | sed 's/^/- off-name: /' || true
  if [ -s "$RUN/community-applied.txt" ]; then   # the community list (CleanYourTV, MIT) for this model, if one was used
    echo "Community list: $(cat "$RUN/community-name.txt" 2> /dev/null) (CleanYourTV, MIT)"
    for pkg in $(grep -vE '^\s*(#|$)' "$RUN/community-applied.txt" | awk '{print $1}'); do
      if grep -qx "package:$pkg" <<< "$disabled"; then state=off
      elif grep -qx "package:$pkg" <<< "$installed"; then state=on
      else state=absent; fi
      echo "- pkg $state: $pkg (community)"
    done
  fi
  echo "Left off the home screen:"
  optimize/dock.sh hidden 2> /dev/null | sed 's/^/- home hides: /' || true
  echo "Undo everything: optimize/reset.sh"
}

do_step() {
  case "$1" in
    precheck)    rc=0; optimize/precheck.sh > "$RUN/precheck.txt" 2>&1 || rc=$?   # 3: not an Android TV, stop (nothing was changed)
                 cat "$RUN/precheck.txt"; [ "$rc" = 0 ] || exit "$rc" ;;
    probe)       tv --probe | tee "$RUN/tv.txt" ;;
    backup)      optimize/backup.sh && cp "$(ls -t optimize/backup-*.txt | head -1)" "$RUN/backup.txt" ;;
    keep)        optimize/keep.sh | tee "$RUN/keep.txt" ;;
    before)      optimize/measure.sh before ;;
    before-saver) optimize/measure.sh before-screensaver --screensaver ;;
    safe)        check_list optimize/lists/tcl-safe.txt
                 optimize/disable.sh optimize/lists/tcl-safe.txt > /dev/null ;;
    mine)        if [ -s optimize/lists/mine.txt ]; then
                   check_list optimize/lists/mine.txt
                   optimize/disable.sh optimize/lists/mine.txt > /dev/null
                 else
                   echo "   (no lists/mine.txt: nothing optional)"
                 fi ;;
    community)   optimize/community.sh ;;
    tweaks)      optimize/tweaks.sh apply ;;
    screensaver) optimize/install.sh saver ;;
    launcher)    if [ "${SKIP_LAUNCHER:-}" = 1 ]; then echo "   skipped (SKIP_LAUNCHER=1): the TV keeps its own home screen"
                 else optimize/install.sh launcher; fi ;;
    extras)      if [ "${SKIP_EXTRAS:-}" = 1 ]; then echo "   skipped (SKIP_EXTRAS=1)"; else optimize/install.sh extras; fi ;;
    autostart)   optimize/autostart.sh ;;
    after)       optimize/measure.sh after ;;
    after-saver) optimize/measure.sh after-screensaver --screensaver ;;
    report)      report > "$RUN/report.md"; cat "$RUN/report.md" ;;
  esac
}

# The .done-* marks belong to one TV: a different one (its serial, else its address) starts from the top.
tv_id=$(tv "getprop ro.serialno" 2> /dev/null | tr -d '\r\n ' || true)
[ -n "$tv_id" ] || tv_id="${TV_HOST:-$(cat optimize/.tv_host 2> /dev/null || true)}"
if [ -n "$tv_id" ] && [ "$(cat "$RUN/.tv" 2> /dev/null || true)" != "$tv_id" ]; then
  [ -f "$RUN/.tv" ] && echo "A different TV than the last run: starting from the first step."
  rm -f "$RUN"/.done-* "$RUN/steps.log"
  echo "$tv_id" > "$RUN/.tv"
fi

for s in "${STEPS[@]}"; do
  [ -f "$RUN/.done-$s" ] && continue
  current=$s
  echo
  echo "▶ $(title "$s")"
  stamp "start $s"
  do_step "$s"
  touch "$RUN/.done-$s"
  stamp "done $s"
done
# the heavy-screensaver line is advice for the owner, not a failure: it is shown but does not stop the run
ADVICE='the screensaver may slow this TV'
if grep '!!' "$RUN/report.md" | grep -qv "$ADVICE"; then
  current=report-check   # the stop below is no step's fault
  echo
  echo "Done, with problems:"
  grep '!!' "$RUN/report.md"
  rm -f "$RUN/.done-report"   # the next run writes the report again, so a fixed problem stops showing
  exit 1
fi
if grep -q "$ADVICE" "$RUN/report.md"; then
  echo
  grep "$ADVICE" "$RUN/report.md"
fi
echo
echo "All steps done. Next: settings (optimize/saver.sh), favourites (optimize/dock.sh), photos (optimize/shot.sh)."
