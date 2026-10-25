#!/usr/bin/env bash
# TCL firmware (TclAppBoot) keeps apps from starting at boot, and from binding their services while they are not running,
# unless they hold its auto-start app-op. Ours need it: the adhan alarms after a restart, the sleep service and BootReceiver.
# The idea and the op names are from CleanYourTV (github.com/Gathin23/cleanyourtv, MIT; their pull request «keep TCL's
# AUTO_START setting so key helpers and Sleep Timer survive reboot», #1143, and the install scripts of their add-ons): the op
# is AUTO_START on G08 builds and APP_AUTO_START on G10, other TVs do not know it, and without `appops write-settings` the
# grant does not reach the disk before the next restart.
#   optimize/autostart.sh           allow it for sa.bayti.home and sa.bayti.screensaver on a TCL, then say what the TV answers
#   optimize/autostart.sh check     only say it (the lines report.md gets): «Auto-start: …», or a !! line when a TCL refuses
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
tv() { "$PY" -I optimize/tv.py "$@"; }
PKGS=(sa.bayti.home sa.bayti.screensaver)

is_tcl() {
  local who
  who=$(tv "getprop ro.product.manufacturer; getprop ro.product.brand; pm list packages com.tcl." | tr -d '\r' | tr 'A-Z' 'a-z')
  grep -qE '^tcl$|^package:com\.tcl\.' <<< "$who"
}
installed() { tv "pm list packages $1" | tr -d '\r' | grep -qx "package:$1"; }
state() {   # what the TV says for this package: allow, ignore, deny, default… or none when it has no such op
  local out got op
  for op in AUTO_START APP_AUTO_START; do
    out=$(tv "appops get $1 $op 2> /dev/null" | tr -d '\r' || true)
    got=$(sed -n "s/^$op: \([a-z_]*\).*/\1/p" <<< "$out" | head -1)
    [ -z "$got" ] || { echo "$got"; return; }
  done
  echo none
}

mode="${1:-allow}"
if ! is_tcl; then echo "Auto-start: not a TCL, nothing to allow"; exit 0; fi
if [ "$mode" = allow ]; then
  for p in "${PKGS[@]}"; do
    installed "$p" || continue
    tv "appops set $p AUTO_START allow 2> /dev/null; appops set $p APP_AUTO_START allow 2> /dev/null; cmd appops set $p AUTO_START allow 2> /dev/null; cmd appops set $p APP_AUTO_START allow 2> /dev/null; appops write-settings 2> /dev/null; true" > /dev/null
  done
fi
line=; bad=
for p in "${PKGS[@]}"; do
  installed "$p" || continue
  st=$(state "$p")
  line="$line${line:+, }$p $st"
  case "$st" in allow|none) ;; *) bad="$bad $p($st)" ;; esac   # none: this TCL build has no such op
done
if [ -z "$line" ]; then echo "Auto-start (TCL): our apps are not installed"
elif [ -n "$bad" ]; then
  echo "!! Auto-start (TCL): not allowed for$bad: after a restart the adhan alarms and the sleep service may not start. Run optimize/autostart.sh again"
else echo "Auto-start (TCL): $line"; fi
