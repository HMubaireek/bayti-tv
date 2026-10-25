package sa.bayti.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The favourites row from ADB, for agents and scripts. Only the shell can send these: the manifest requires
 * android.permission.DUMP, which apps can't hold.
 *
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.GET_DOCK
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.LIST_APPS
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.SET_DOCK --es apps pkg1,pkg2,...
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.RESET_DOCK
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.HIDDEN                 left out, and why
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.HIDE --es apps pkg1,pkg2,...
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.SHOW --es apps pkg1,pkg2,...
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.NAMES                  pkg | shown | own | from
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.SET_NAMES --es names 'pkg|الاسم;pkg2|'   (empty: back)
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.CLEAR_NAMES
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.EXPORT                 the owner's choices: "backup: <base64>"
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.IMPORT --es b64 <base64>   (optimize/settings.sh)
 *   am broadcast -n sa.bayti.home/.DockReceiver -a sa.bayti.home.SLEEP_TV [--ei minutes 30 | 0 = cancel]   «مؤقت النوم» for the whole TV
 *
 * The answer is in the "data" of am's output. optimize/dock.sh wraps them all. People use «ترتيب المفضلة»
 * (DockActivity) instead.
 */
public class DockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String action = String.valueOf(i.getAction());
        Map<String, String> apps = Dock.launchable(c);
        switch (action) {
            case "sa.bayti.home.LIST_APPS": {
                StringBuilder b = new StringBuilder();
                for (Map.Entry<String, String> e : apps.entrySet()) b.append(e.getKey()).append(" | ").append(e.getValue()).append('\n');
                setResultData(b.toString().trim());
                return;
            }
            case "sa.bayti.home.NAMES": {   // for the owner's agent: which names are still not Arabic, and their own name
                Map<String, String> given = AppNames.given(c);
                StringBuilder b = new StringBuilder();
                for (ResolveInfo ri : Dock.catalogue(c)) {
                    String own = String.valueOf(ri.loadLabel(c.getPackageManager()));
                    b.append(ri.activityInfo.packageName).append(" | ").append(AppNames.label(c, ri, given)).append(" | ")
                     .append(own).append(" | ").append(AppNames.source(c, ri, given)).append('\n');
                }
                setResultData(b.toString().trim());
                return;
            }
            case "sa.bayti.home.SET_NAMES": {
                String raw = i.getStringExtra("names");
                if (raw == null) { setResultData("error: add --es names 'pkg|الاسم;pkg2|الاسم'"); return; }
                Map<String, String> given = AppNames.given(c);
                List<String> done = new ArrayList<>(), skipped = new ArrayList<>();
                for (String pair : raw.split(";")) {
                    int bar = pair.indexOf('|');
                    if (bar <= 0) { if (!pair.trim().isEmpty()) skipped.add(pair.trim()); continue; }
                    String pkg = pair.substring(0, bar).trim(), name = pair.substring(bar + 1).trim();
                    if (!apps.containsKey(pkg)) { skipped.add(pkg + " (not on the home screen)"); continue; }
                    if (name.isEmpty()) { given.remove(pkg); done.add(pkg + " (its usual name again)"); continue; }
                    if (!AppNames.valid(name)) { skipped.add(pkg + " (Arabic, one line, up to " + AppNames.MAX_LEN + " characters)"); continue; }
                    given.put(pkg, name);
                    done.add(pkg + " = " + name);
                }
                AppNames.save(c, given);
                c.sendBroadcast(new Intent(Dock.CHANGED).setPackage(c.getPackageName()));
                setResultData("named: " + (done.isEmpty() ? "none" : String.join(", ", done))
                    + (skipped.isEmpty() ? "" : "\nskipped: " + String.join(", ", skipped)));
                return;
            }
            case "sa.bayti.home.CLEAR_NAMES":
                AppNames.save(c, new java.util.HashMap<String, String>());
                c.sendBroadcast(new Intent(Dock.CHANGED).setPackage(c.getPackageName()));
                setResultData("names: back to each app's usual name");
                return;
            case "sa.bayti.home.SLEEP_TV": {   // the sleep timer: no extra = how it stands; minutes = start it; 0 = cancel
                if (i.hasExtra("minutes")) {
                    int m = i.getIntExtra("minutes", -1);
                    if (m < 0 || m > 600) { setResultData("error: minutes is 1 to 600, or 0 to cancel"); return; }
                    SleepTimer.start(c, m);
                }
                setResultData(SleepTimer.status(c));
                return;
            }
            case "sa.bayti.home.GET_DOCK":
                setResultData("dock: " + String.join(",", Dock.shown(c, apps)));
                return;
            case "sa.bayti.home.EXPORT":   // «نسخة الإعدادات» (optimize/settings.sh save): the owner's choices, base64 of JSON
                try { setResultData("backup: " + PrefsBackup.export(c)); }
                catch (Exception e) { setResultData("error: backup failed: " + e.getMessage()); }
                return;
            case "sa.bayti.home.IMPORT": {   // --es b64 <what EXPORT gave>
                String b64 = i.getStringExtra("b64");
                if (b64 == null || b64.isEmpty()) { setResultData("error: IMPORT needs --es b64 <backup>"); return; }
                try {
                    int n = PrefsBackup.restore(c, b64);
                    c.sendBroadcast(new Intent(Dock.CHANGED).setPackage(c.getPackageName()));   // the home screen redraws its row
                    setResultData("restored: " + n + " settings");
                } catch (Exception e) {
                    setResultData("error: not a backup of these settings (" + e.getMessage() + ")");
                }
                return;
            }
            case "sa.bayti.home.RESET_DOCK":
                Dock.reset(c);
                setResultData("dock: " + String.join(",", Dock.shown(c, apps)) + " (defaults)");
                return;
            case "sa.bayti.home.SET_DOCK": {
                String raw = i.getStringExtra("apps");
                if (raw == null) { setResultData("error: add --es apps pkg1,pkg2,..."); return; }
                List<String> row = new ArrayList<>(), skipped = new ArrayList<>();
                for (String p : raw.split(",")) {
                    p = p.trim();
                    if (p.isEmpty() || row.contains(p)) continue;
                    if (apps.containsKey(p) && row.size() < Dock.MAX) row.add(p); else skipped.add(p);
                }
                if (row.isEmpty()) { setResultData("error: none of these apps are on this TV's home screen"); return; }
                Dock.save(c, row);
                setResultData("dock: " + String.join(",", Dock.shown(c, apps))
                    + (skipped.isEmpty() ? "" : "  skipped (not installed, hidden or over " + Dock.MAX + "): "
                       + String.join(",", skipped)));
                return;
            }
            case "sa.bayti.home.HIDDEN": {
                StringBuilder b = new StringBuilder();
                for (Map.Entry<String, String> e : Dock.leftOut(c).entrySet()) b.append(e.getKey()).append(" | ").append(e.getValue()).append('\n');
                setResultData(b.length() == 0 ? "nothing left out" : b.toString().trim());
                return;
            }
            case "sa.bayti.home.HIDE":
            case "sa.bayti.home.SHOW": {
                String raw = i.getStringExtra("apps");
                if (raw == null) { setResultData("error: add --es apps pkg1,pkg2,..."); return; }
                List<String> pkgs = new ArrayList<>();
                for (String p : raw.split(",")) if (!p.trim().isEmpty()) pkgs.add(p.trim());
                boolean hide = action.endsWith("HIDE");
                Dock.hideOrShow(c, pkgs, hide);
                setResultData((hide ? "hidden: " : "shown: ") + String.join(",", pkgs));
                return;
            }
            default:
                setResultData("error: unknown action " + action);
        }
    }
}
