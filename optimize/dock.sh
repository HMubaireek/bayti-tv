#!/usr/bin/env bash
# The home screen's favourites row, for agents and scripts: no remote, no long-press.
#   optimize/dock.sh show                  the current order, left to right
#   optimize/dock.sh apps                  every app that can go in it: package | name
#   optimize/dock.sh set PKG1 PKG2 ...     a new order, left to right (up to 10; fewer than 5 is topped up)
#   optimize/dock.sh reset                 back to the defaults in android/launcher/.../config.xml
#   optimize/dock.sh hidden                apps left off the home screen, and why: package | reason
#   optimize/dock.sh hide PKG ...          take these off the home screen (all its lists)
#   optimize/dock.sh show PKG ...          put these on it, even against the rules (the TV's own home doesn't list them)
#   optimize/dock.sh names                 every app's name: package | shown | its own | from (agent, own, table)
#   optimize/dock.sh name PKG 'الاسم' ...   give apps an Arabic name on this TV (pairs; '' = its usual name again)
#   optimize/dock.sh names-clear           every app back to its usual name
#   optimize/dock.sh sleep 30              «مؤقت النوم» for the whole TV: 15 30 60 90, or any 1 to 600 minutes; then the TV goes to sleep
#   optimize/dock.sh sleep                 how the timer stands: minutes left, and what its end will do
#   optimize/dock.sh sleep cancel          stop it
#   optimize/dock.sh sleep-setup           turn on the sleep service (an accessibility service, once) so the end turns the screen off
# The home screen redraws at once; the sleep timer's pill too. Only ADB can do this (sa.bayti.home/.DockReceiver needs the shell's DUMP).
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
send() {   # $1 action, $2 extra arguments; prints the receiver's answer (the "data" of am broadcast)
  "$PY" -I optimize/tv.py "am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.$1 ${2:-}" \
    | sed -n '/data="/,$p' | sed '1s/.*data="//; $s/"$//'
}
case "${1:-}:$#" in
  show:1) send GET_DOCK ;;
  apps:*)  send LIST_APPS ;;
  reset:*) send RESET_DOCK ;;
  hidden:*) send HIDDEN ;;
  hide:*|show:*)
    what=$(tr '[:lower:]' '[:upper:]' <<< "$1")
    shift
    [ $# -gt 0 ] || { echo "usage: optimize/dock.sh hide|show PKG ..."; exit 1; }
    for p in "$@"; do [[ "$p" =~ ^[A-Za-z0-9_.]+$ ]] || { echo "not a package name: $p"; exit 1; }; done
    send "$what" "--es apps $(IFS=,; echo "$*")" ;;
  set:*)
    shift
    [ $# -gt 0 ] || { echo "usage: optimize/dock.sh set PKG1 PKG2 ..."; exit 1; }
    for p in "$@"; do [[ "$p" =~ ^[A-Za-z0-9_.]+$ ]] || { echo "not a package name: $p"; exit 1; }; done
    send SET_DOCK "--es apps $(IFS=,; echo "$*")" ;;
  sleep:1) send SLEEP_TV ;;
  sleep:2)
    case "$2" in
      cancel) minutes=0 ;;
      ""|*[!0-9]*) echo "usage: optimize/dock.sh sleep [MINUTES | cancel]"; exit 1 ;;
      *) minutes=$2; [ "$minutes" -le 600 ] || { echo "minutes: 1 to 600"; exit 1; } ;;
    esac
    send SLEEP_TV "--ei minutes $minutes" ;;
  sleep-setup:1)
    svc="sa.bayti.home/sa.bayti.home.SleepService"
    now=$("$PY" -I optimize/tv.py "settings get secure enabled_accessibility_services" | tail -n 1 | tr -d '\r')
    case "$now" in
      *"$svc"*) echo "the sleep service was already on" ;;
      null|"") "$PY" -I optimize/tv.py "settings put secure enabled_accessibility_services $svc" >/dev/null ;;
      *) "$PY" -I optimize/tv.py "settings put secure enabled_accessibility_services $now:$svc" >/dev/null ;;
    esac
    "$PY" -I optimize/tv.py "settings put secure accessibility_enabled 1" >/dev/null
    echo "the sleep service is on: the end of «مؤقت النوم» turns the screen off" ;;
  names:1) send NAMES ;;
  names-clear:1) send CLEAR_NAMES ;;
  name:*)
    shift
    [ $# -gt 0 ] && [ $(( $# % 2 )) -eq 0 ] || { echo "usage: optimize/dock.sh name PKG 'الاسم' [PKG 'الاسم' ...]"; exit 1; }
    pairs=""
    while [ $# -gt 0 ]; do
      [[ "$1" =~ ^[A-Za-z0-9_.]+$ ]] || { echo "not a package name: $1"; exit 1; }
      case "$2" in *"'"*|*";"*|*"|"*) echo "no ' ; or | in a name: $2"; exit 1 ;; esac
      pairs="$pairs${pairs:+;}$1|$2"
      shift 2
    done
    send SET_NAMES "--es names '$pairs'" ;;
  *) sed -n '2,17p' "$0"; exit 1 ;;
esac
