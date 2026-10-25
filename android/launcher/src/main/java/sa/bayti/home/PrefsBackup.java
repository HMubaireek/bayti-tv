package sa.bayti.home;

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
 * «نسخة الإعدادات»: what the owner chose on the home screen as one text, and back (optimize/settings.sh save / restore, through
 * DockReceiver EXPORT / IMPORT): the favourites in order, the Arabic names given, the apps hidden or shown from ADB, and the adhan and
 * recitation choices. Caches (photos, prayer times, the weather) are not in it. Base64 of JSON, as the screensaver's PrefsBackup.
 */
final class PrefsBackup {
    static final int VERSION = 1;
    /** the preference file and the keys of it that are the owner's choices */
    private static final String[][] FILES = {
        {"MainActivity", "dock", "names", "hidden", "shown"},
        {"tilawah", "adhan_on", "adhan_own", "adhan_voice", "adhan_volume", "reciter", "source", "station", "surah", "position"},
    };

    private PrefsBackup() {}

    static String export(Context c) throws Exception {
        JSONObject files = new JSONObject();
        for (String[] f : FILES) {
            Map<String, ?> all = c.getSharedPreferences(f[0], Context.MODE_PRIVATE).getAll();
            JSONObject values = new JSONObject();
            for (int i = 1; i < f.length; i++) {
                Object v = all.get(f[i]);
                if (v != null) values.put(f[i], typed(v));
            }
            files.put(f[0], values);
        }
        JSONObject out = new JSONObject().put("app", "home").put("v", VERSION).put("files", files);
        return Base64.encodeToString(out.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    /** Puts back what a backup holds, only the keys of FILES; others stay. Returns how many were set, or throws on a bad backup. */
    static int restore(Context c, String b64) throws Exception {
        JSONObject in = new JSONObject(new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8));
        if (!"home".equals(in.optString("app"))) throw new IllegalArgumentException("not a backup of the home screen's settings");
        JSONObject files = in.getJSONObject("files");
        int n = 0;
        for (String[] f : FILES) {
            JSONObject values = files.optJSONObject(f[0]);
            if (values == null) continue;
            Set<String> allowed = new HashSet<>(java.util.Arrays.asList(f).subList(1, f.length));
            SharedPreferences.Editor ed = c.getSharedPreferences(f[0], Context.MODE_PRIVATE).edit();
            for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                String k = it.next();
                if (!allowed.contains(k)) continue;
                put(ed, k, values.getJSONObject(k));
                n++;
            }
            ed.apply();
        }
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
