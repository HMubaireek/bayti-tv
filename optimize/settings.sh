#!/usr/bin/env bash
# «نسخة الإعدادات»: the screensaver's and the home screen's settings saved on this computer, and put back on the TV.
#   optimize/settings.sh save [FILE]      both apps' settings into FILE (default: ~/.urtv/settings/settings-<date>.txt)
#   optimize/settings.sh restore [FILE]   put them back (default: the newest saved one); the apps you kept on (keep.sh restore) too
#   optimize/settings.sh list             the saved ones, newest first
# What is in it: every screensaver setting (city, prayer method, teams, cards, markets, photo sources, tasks…), the favourites in
# order, the Arabic names given to apps, the apps hidden or shown, the adhan and recitation choices. Not in it: your own photos
# (photos.sh add puts them back from your files) and the TV's own settings. It lives outside this folder, next to the ADB key, so a new
# clone or a deleted folder keeps it. Restore after a reset: optimize/run.sh first (it installs the apps), then this.
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
DIR="${URTV_HOME:-$HOME/.urtv}/settings"
SAVER=(sa.urtv.screensaver/.SaverReceiver sa.urtv.screensaver)
HOME_APP=(sa.urtv.home/.DockReceiver sa.urtv.home)

answer() {   # $1 receiver, $2 action, $3 extras: the "data" of am broadcast
  "$PY" -I optimize/tv.py "am broadcast -n $1 -a $2 ${3:-}" | tr -d '\r' | sed -n '/data="/,$p' | sed '1s/.*data="//; $s/"$//'
}

newest() { ls -1t "$DIR"/settings-*.txt 2> /dev/null | head -1 || true; }

case "${1:-}" in
  save)
    file="${2:-$DIR/settings-$(date +%Y%m%d-%H%M).txt}"
    saver=$(answer "${SAVER[0]}" "${SAVER[1]}.EXPORT")
    [[ "$saver" == "backup: "* ]] || { echo "!! The screensaver did not answer with its settings: ${saver:-nothing} (is it installed? optimize/install.sh saver)"; exit 1; }
    home=$(answer "${HOME_APP[0]}" "${HOME_APP[1]}.EXPORT")
    [[ "$home" == "backup: "* ]] || { echo "   (the home screen did not answer: only the screensaver's settings are saved)"; home=; }
    kept=$("$PY" -I optimize/offlist.py kept 2> /dev/null | tr '\n' ' ' || true)
    mkdir -p "$(dirname "$file")"
    { echo "# Bayti TV settings, saved $(date '+%Y-%m-%d %H:%M'). Put back with: optimize/settings.sh restore '$file'"
      echo "screensaver ${saver#backup: }"
      [ -z "$home" ] || echo "home ${home#backup: }"
      [ -z "${kept// /}" ] || echo "kept $kept"
    } > "$file"
    echo "Saved: $file"
    ;;
  restore)
    file="${2:-$(newest)}"
    [ -n "$file" ] && [ -f "$file" ] || { echo "!! No saved settings${2:+ at $2}. optimize/settings.sh save makes one."; exit 1; }
    saver=$(awk '$1 == "screensaver" {print $2}' "$file"); home=$(awk '$1 == "home" {print $2}' "$file")
    kept=$(awk '$1 == "kept" {$1 = ""; print}' "$file")
    for v in "$saver" "$home"; do
      [[ "$v" =~ ^[A-Za-z0-9+/=]*$ ]] || { echo "!! $file is not a settings file of these tools"; exit 1; }
    done
    [ -n "$saver" ] || { echo "!! $file has no screensaver settings"; exit 1; }
    out=$(answer "${SAVER[0]}" "${SAVER[1]}.IMPORT" "--es b64 $saver"); echo "screensaver: $out"
    [[ "$out" == restored:* ]] || exit 1
    if [ -n "$home" ]; then out=$(answer "${HOME_APP[0]}" "${HOME_APP[1]}.IMPORT" "--es b64 $home"); echo "home screen: $out"; fi
    # shellcheck disable=SC2086   # one package per word
    if [ -n "${kept// /}" ]; then optimize/keep.sh restore $kept; fi
    optimize/saver.sh update > /dev/null || true
    echo "Restored from $file"
    ;;
  list)
    ls -1t "$DIR"/settings-*.txt 2> /dev/null || echo "(none saved yet: optimize/settings.sh save)"
    ;;
  *)
    sed -n '2,4p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
