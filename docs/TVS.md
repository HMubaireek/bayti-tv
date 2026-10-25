# TVs seen in the field

If your TV isn't here, add a row from your run.

What the first reports from real TVs showed. «Home switch» is `optimize/install.sh launcher`;
«Screensaver from ADB» is whether Android starts our screensaver over ADB (where it doesn't, `shot.sh` shows our
app's preview of the same page and `measure.sh` writes «not measured»). Times are the whole `optimize/run.sh`.
«N reports» is how many setup reports that model appeared in; every one of them switched the home screen.

| TV | Android | Home switch | Screensaver from ADB | ADB | Watch out for | Typical time |
|---|---|---|---|---|---|---|
| TCL Smart TV Pro (G05) · 8 reports | 11 | works | yes | 5555 | `com.tcl.smartalexa` ships off outside Alexa's regions, expected | 14 min |
| TCL Smart TV Pro (G07) · 8 reports | 11 | works | yes | 5555 | sometimes slow to come back after a restart; `com.tcl.smartalexa` ships off outside Alexa's regions, expected | 14 min |
| TCL Smart TV Pro (C05) · 2 reports | 11 | works | not reported | not reported | the remote's Guard (cleaner) button opens only `com.tcl.guard`, so that package stays on unless picked | not reported |
| TCL Smart TV Pro (BeyondTV4) · 9 reports | 11 | works | yes | 5555 | `com.tcl.smartalexa` ships off outside Alexa's regions, expected | 14 min |
| TCL BeyondTV · 6 reports | 9 and 11 | works | not reported | 5555 | – | not reported |
| TCL BeyondTV2 | 9 | works | not reported | not reported | – | not reported |
| TCL UnionTV | 9 | works | yes | 5555 | – | 15 min |
| TCL Smart TV Pro (G08) · 18 reports | 14 | works (launcherx + setupwraith off) | no, preview only | 5555; the first probe may hang, run it again | a Wi-Fi drop during the switch: the safety switch restores the stock home and `optimize/install.sh launcher` retries the switch by itself (once); Play Protect refused the home APK over ADB at first (`install.sh` now turns that check off for the install only and puts it back) | 11 min |
| TCL Smart TV Pro (G08) | 12 | works | not reported | not reported | SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras) | not reported |
| TCL Smart TV Pro (G10) | 12 | works | not reported | not reported | SmartTube install timed out on every run (see the G08 row, Android 12); Play Protect refused the home APK over ADB at first (`install.sh` now turns that check off for the install only and puts it back) | not reported |
| Xiaomi Mi Box S (MIBOX4), stock Android TV home · 6 reports | 9 | works | not reported | 5555 | – | not reported |
| Xiaomi MiTV-AESP0 · 4 reports | 9 and 10 | works | not reported | not reported | – | not reported |
| Xiaomi MiTV-MOOR2 · 3 reports | 11 | works | no | 5555 | screenshots may fail | 17 min |
| Xiaomi MiTV-AFKR0 · 3 reports | 11 | works | not reported | not reported | – | not reported |
| Xiaomi MiTV-AYFR0 | 11 | works | not reported | not reported | – | not reported |
| Xiaomi MiTV-AFMU0 | 14 | works | not reported | not reported | Play Protect refused the home APK over ADB at first (`install.sh` now turns that check off for the install only and puts it back); SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras) | not reported |
| Xiaomi MiTV-AXTU0 | 14 | works | not reported | not reported | – | not reported |
| Xiaomi MiTV-MZTU0 · 2 reports | 14 | works | not reported | not reported | – | not reported |
| Sony BRAVIA 4K UR3 (BRAVIA_UR3) · 5 reports | 10 | works, first time | yes | turn on USB debugging and network debugging first | one TV showed up only as a Cast device until it was unplugged and plugged in again; SmartTube upload over Wi-Fi is slow but completes | 14 min |
| Sony BRAVIA VH1 | 12 | works | not reported | not reported | SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras). On a KD-65X9000H the TV ends background jobs, so the decode ran silent in the foreground and timed out at 60 s on every try: the direct run now prints a heartbeat (Bayti TV). The CleanYourTV list for this model also turned Netflix and AirPlay/HomeKit off: streaming and casting apps are never taken from a community list now | not reported |
| Sony BRAVIA 4K GB ATV3 | 9 | works | not reported | not reported | – | not reported |
| Sony BRAVIA 4K GB ATV2 | 9 | works | not reported | not reported | – | not reported |
| Skyworth SWTV-20AE-PRO | 11 | works (tvlauncher off) | no | 5555, an occasional timeout that retries by itself | – | 14 min |
| Skyworth SWTV-20AE | 10 | works | not reported | not reported | – | not reported |
| Skyworth SWTV-22AE | 11 | works | not reported | not reported | – | not reported |
| Skyworth SWTV-24AE-4K NF | 14 | works | no | port 5555 closed: pair once through Wireless debugging, then `adb tcpip 5555`; the port disappears after every reboot | reconnect after each restart; APK uploads slow | 39 min |
| Skyworth TORNADO 4K | 11 | works | not reported | not reported | – | not reported |
| SHARP Android TV (Skyworth SW4H_FF) · 2 reports | 11 | works | not reported | not reported | – | not reported |
| Skyworth JAWWY TV 2.0 (HP40A, JAWWY-TV-2.0) | 12 | works | not reported | 5555 | operator image: a system service removed every app installed with a plain `pm install` about 2 s later, so `install.sh` installs it again with the Play Store as the installer when that happens; its own launcher keeps coming back; SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras) | not reported |
| Skyworth, Google TV (model not given) | 14 | not reported | not reported | port 5555 closed: pair through Wireless debugging; the port disappears after every reboot | reconnect after each restart | not reported |
| Changhong AI PONT (Wansa / Classpro) | 11 | works | not reported | not reported | seen on several sites | not reported |
| Changhong AI PONT (central) | 14 | works | not reported | not reported | SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras) | not reported |
| Haier Android TV FF Pro | 14 | works | not reported | not reported | – | not reported |
| Haier MatrixTV BE · 2 reports | 14 | works | not reported | not reported | Play Protect refused the home APK over ADB at first (`install.sh` now turns that check off for the install only and puts it back); SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras) | not reported |
| Hisense HiSmartTV A4 (Toshiba) | 9 | works | not reported | not reported | – | not reported |
| Chromecast with Google TV (sabrina) · 2 reports | 14 | works | not reported | no Network debugging, only Wireless debugging: `adb pair`, then `adb tcpip 5555`; every restart turns it off and closes 5555 | pair and run `adb tcpip 5555` again after each restart (wireless debugging must be paired again after a reboot); SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras) | not reported |
| Chromecast HD (boreal) | 14 | works | not reported | same as the sabrina: Wireless debugging only, `adb pair`, then `adb tcpip 5555`; closes at every restart | pair again after each restart; SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras) | not reported |
| SCBC R4 | 11 | works | not reported | 5555 | the TV's IP changed mid-setup: rediscovery (`optimize/find-tv.sh`) fixed it | not reported |
| JPE at30a7 (DANSAT) | 13 | works | not reported | not reported | SmartTube (about 25 MB) uploaded to 100%, then the install ended in a read timeout on the TV (slow steps now run in the background on the TV; `SKIP_EXTRAS=1` skips the extras) | not reported |
| Philips 4K (Funai guui) | 11 | works | not reported | not reported | – | not reported |

## On the computer

- **macOS:** Homebrew's Python 3.12 or 3.13 (`brew install python@3.12`). python.org's Python has no certificates
  and the system 3.9 can't load the ADB package; `optimize/setup.sh` picks a good one and sets up the certificates.
- **Windows:** clone into a short folder such as `C:\UrTV`; Git and Python come from winget
  (`winget install -e --id Git.Git --source winget`, `winget install -e --id Python.Python.3.12 --source winget`); run every script in Git Bash.
