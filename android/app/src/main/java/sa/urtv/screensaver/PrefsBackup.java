package sa.urtv.screensaver;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * «نسخة الإعدادات»: the screensaver's settings as one text, and back (optimize/settings.sh save / restore, through SaverReceiver EXPORT /
 * IMPORT). Every setting the owner can change is in it, by its type; what only tracks the page's updates is not (a restore must not make
 * the app think it already has a newer page). Base64 of JSON, so Arabic and quotes cross the TV's shell untouched.
 */
final class PrefsBackup {
    static final int VERSION = 1;
    /** update bookkeeping and counters, not settings */
    private static final Set<String> SKIP = new HashSet<>(java.util.Arrays.asList("etag", "lastCheck", "latestCheck", "latestVersion", "ownRev"));

    private PrefsBackup() {}

    static String export(Context c) throws Exception {
        SharedPreferences sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (SKIP.contains(e.getKey()) || e.getValue() == null) continue;
            values.put(e.getKey(), typed(e.getValue()));
        }
        JSONObject all = new JSONObject().put("app", "screensaver").put("v", VERSION).put("settings", values);
        return Base64.encodeToString(all.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    /** Puts back what a backup holds; settings it does not name stay as they are. Returns how many were set, or throws on a bad backup. */
    static int restore(Context c, String b64) throws Exception {
        JSONObject all = new JSONObject(new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8));
        if (!"screensaver".equals(all.optString("app"))) throw new IllegalArgumentException("not a backup of the screensaver's settings");
        JSONObject values = all.getJSONObject("settings");
        SharedPreferences.Editor ed = c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit();
        int n = 0;
        for (Iterator<String> it = values.keys(); it.hasNext(); ) {
            String k = it.next();
            if (SKIP.contains(k)) continue;
            put(ed, k, values.getJSONObject(k));
            n++;
        }
        ed.apply();
        return n;
    }

    static JSONObject typed(Object v) throws Exception {
        JSONObject o = new JSONObject();
        if (v instanceof Boolean) return o.put("t", "b").put("v", v);
        if (v instanceof Integer) return o.put("t", "i").put("v", v);
        if (v instanceof Long) return o.put("t", "l").put("v", v);
        if (v instanceof Float) return o.put("t", "f").put("v", ((Float) v).doubleValue());
        if (v instanceof Set) {
            JSONArray a = new JSONArray();
            for (Object x : (Set<?>) v) a.put(String.valueOf(x));
            return o.put("t", "set").put("v", a);
        }
        return o.put("t", "s").put("v", String.valueOf(v));
    }

    static void put(SharedPreferences.Editor ed, String k, JSONObject o) throws Exception {
        switch (o.getString("t")) {
            case "b": ed.putBoolean(k, o.getBoolean("v")); break;
            case "i": ed.putInt(k, o.getInt("v")); break;
            case "l": ed.putLong(k, o.getLong("v")); break;
            case "f": ed.putFloat(k, (float) o.getDouble("v")); break;
            case "set": {
                Set<String> s = new HashSet<>();
                JSONArray a = o.getJSONArray("v");
                for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                ed.putStringSet(k, s);
                break;
            }
            default: ed.putString(k, o.getString("v"));
        }
    }
}
