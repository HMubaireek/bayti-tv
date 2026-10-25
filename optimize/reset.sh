#!/usr/bin/env bash
# Put a TV that was set up with these tools back the way it came, so it can be set up again "like new":
# every package from our lists re-enabled, the stock home and screensaver back, our apps (old and new names)
# removed, and TV Bro and SmartTube if the setup installed them, tweaks undone. Your own apps and data are not touched.
#   optimize/reset.sh
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
tv() { "$PY" -I optimize/tv.py "$@"; }

echo "1/4 Stock home first (never leave the TV without one)…"
lists=(optimize/lists/tcl-safe.txt optimize/lists/tcl-optional.txt)
for extra in optimize/lists/mine*.txt; do   # local lists, e.g. mine-bloat.txt; absent on a fresh clone
  [ -f "$extra" ] && lists+=("$extra")
done
[ -s optimize/run/community-applied.txt ] && lists+=(optimize/run/community-applied.txt)   # a community list the setup used
optimize/restore.sh "${lists[@]}" > /dev/null
stock=no
for _ in 1 2 3 4; do   # a cold stock home can take a while to come up
  tv "input keyevent KEYCODE_HOME" > /dev/null
  sleep 6
  home=$(tv "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN" | grep -E 'launcherx|tvlauncher' | tail -n 1 || true)
  here=$(tv "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'" | head -n 1 || true)
  if [ -n "$home" ] && grep -qE 'launcherx|tvlauncher' <<< "$here"; then
    stock=yes; break
  fi
done
if [ "$stock" = no ]; then
  echo "!! The stock home is not on screen. Stopping before removing anything: press Home on the remote,"
  echo "   then run optimize/reset.sh again."
  exit 1
fi

optimize/keep.sh || true   # a TV optimized before may have voice, mirroring or updates off
echo "2/4 The TV's own screensaver…"
printf '%s\n' "settings delete secure screensaver_components" "settings delete secure screensaver_default_component" \
  | tv --batch > /dev/null
# install.sh switched screensaver_enabled on and set ours; backup.sh saved the values from before (null: it was never set)
if [ -f optimize/run/backup.txt ]; then
  vals=$(tr -d '\r' < optimize/run/backup.txt | awk '/^== /{s=($2=="screensaver")} s&&!/^==/{print}')
  { read -r comps; read -r enabled; read -r timeout; } <<< "$vals"
  { # the screensaver the TV had (a component list; none then, or ours from an earlier setup: left deleted, the TV picks its own)
    if [[ "${comps:-}" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.\$]+(,[A-Za-z0-9_.]+/[A-Za-z0-9_.\$]+)*$ ]] && [[ "$comps" != *sa.bayti.screensaver/* ]]; then
      echo "settings put secure screensaver_components '$comps'"
    fi
    for kv in "secure screensaver_enabled ${enabled:-}" "system screen_off_timeout ${timeout:-}"; do
      read -r ns key val <<< "$kv"
      [ -n "$val" ] || continue
      if [ "$val" = null ]; then echo "settings delete $ns $key"; else echo "settings put $ns $key $val"; fi
    done
  } | tv --batch > /dev/null
else
  echo "   (no optimize/run/backup.txt: screensaver_enabled and screen_off_timeout left as they are)"
fi

echo "3/4 Removing our apps (old and new names; missing ones are skipped)…"
# the appop the launcher was given for the adhan (it vanishes with the app anyway)
tv "appops set sa.bayti.home SCHEDULE_EXACT_ALARM default" > /dev/null 2>&1 || true
for p in sa.bayti.home sa.bayti.screensaver sa.bayti.listening sa.yourtv.home sa.yourtv.screensaver com.abdullah.tvhome com.abdullah.tvsaver; do
  tv "pm uninstall $p" | grep -q Success && echo "   removed $p" || true
done
# TV Bro and SmartTube: only if the setup put them there (not in the TV's package list saved before it)
for p in com.phlox.tvwebbrowser org.smarttube.stable; do
  if [ -f optimize/run/backup.txt ] && ! grep -qx "package:$p" optimize/run/backup.txt; then
    tv "pm uninstall $p" | grep -q Success && echo "   removed $p" || true
  fi
done

echo "4/4 Tweaks back to Android's defaults…"
optimize/tweaks.sh restore > /dev/null
[ -d optimize/run ] && mv optimize/run "optimize/run-$(date +%Y%m%d-%H%M%S)"   # keep the old results apart

"$PY" -I optimize/offlist.py clear 2> /dev/null || true   # the apps kept on by request: a reset starts like new
echo "Done. Still disabled on this TV (made so by the manufacturer, or by something else):"
tv "pm list packages -d" | sed 's/^package:/   /'
