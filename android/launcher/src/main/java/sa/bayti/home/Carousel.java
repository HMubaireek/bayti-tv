package sa.bayti.home;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * «العرض المتحرك»: the photos of the home screen's carousel, from the sources chosen in the screensaver's settings (its ConfigProvider
 * .../home says which, .../carousel gives the merged list of the free sources, .../own/N the owner's own). None chosen: no carousel.
 * A photo is downloaded once into files/carousel and decoded at the screen's width, off the main thread (load, bitmap).
 */
final class Carousel {
    static final Uri BASE = Uri.parse("content://sa.bayti.screensaver.config");
    private static final String UA = "BaytiTV/1.0 (https://github.com/HMubaireek/bayti-tv)";
    private static final String[] HOSTS = {"https://upload.wikimedia.org/", "https://thumb.wikimedia.org/", "https://www.bing.com/",
        "https://raw.githubusercontent.com/HMubaireek/bayti-tv/"};

    /** an address of a free source, or "own:N" for the owner's photo N */
    final List<String> items = new ArrayList<>();
    /** how often it moves on by itself, in minutes; 0 = only with the remote */
    int minutes = 1;

    private Carousel() {}

    /** Off the main thread. Empty when the carousel is off or nothing could be read. */
    static Carousel load(Context c) {
        Carousel out = new Carousel();
        String sources = "";
        try (Cursor cur = c.getContentResolver().query(BASE.buildUpon().appendPath("home").build(), null, null, null, null)) {
            if (cur != null && cur.getColumnIndex("carousel") == 2 && cur.moveToFirst()) {
                sources = cur.getString(2) == null ? "" : cur.getString(2);
                int show = cur.getColumnIndex("home_shows");   // «الرئيسية تعرض»: the cards or only the photo: no carousel
                if (show >= 0 && cur.getString(show) != null && !"carousel".equals(cur.getString(show))) sources = "";
                int m = cur.getColumnIndex("carousel_minutes");
                if (m >= 0 && cur.getString(m) != null && cur.getString(m).matches("[0-9]{1,2}")) out.minutes = Integer.parseInt(cur.getString(m));
            }
        } catch (RuntimeException ignored) { return out; }
        if (sources.isEmpty()) return out;
        try (Cursor cur = c.getContentResolver().query(BASE.buildUpon().appendPath("carousel").build(), null, null, null, null)) {
            if (cur != null && cur.getColumnIndex("json") == 0 && cur.moveToFirst()) {
                JSONArray a = new JSONArray(cur.getString(0));
                for (int i = 0; i < a.length() && out.items.size() < 40; i++) {
                    String f = a.getJSONObject(i).optString("f");
                    if (!f.startsWith("https://")) f = "https://raw.githubusercontent.com/HMubaireek/bayti-tv/master/photos/" + f;
                    if (allowed(f)) out.items.add(f);
                }
            }
        } catch (Exception ignored) { }
        if (("," + sources + ",").contains(",own,")) {
            try (Cursor cur = c.getContentResolver().query(BASE.buildUpon().appendPath("own").build(), null, null, null, null)) {
                if (cur != null && cur.getColumnIndex("n") == 0 && cur.moveToFirst())
                    for (int n = 1; n <= cur.getInt(0); n++) out.items.add("own:" + n);
            } catch (RuntimeException ignored) { }
        }
        return out;
    }

    private static boolean allowed(String u) {
        for (String h : HOSTS) if (u.startsWith(h) && u.indexOf('\n') < 0) return true;
        return false;
    }

    /** Off the main thread: photo i at about the screen's width, or null. */
    static Bitmap bitmap(Context c, String item, int width) {
        try {
            if (item.startsWith("own:")) {
                Uri u = BASE.buildUpon().appendPath("own").appendPath(item.substring(4)).build();
                return decode(() -> c.getContentResolver().openInputStream(u), width);
            }
            File dir = new File(c.getFilesDir(), "carousel");
            File f = new File(dir, MainActivity.cacheName(item));
            if (!f.isFile()) {
                if (!dir.isDirectory() && !dir.mkdirs()) return null;
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(item).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(30000);
                con.setRequestProperty("User-Agent", UA);
                File tmp = new File(dir, f.getName() + ".tmp");
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    con.disconnect();
                }
                if (!tmp.renameTo(f)) { tmp.delete(); return null; }
                prune(dir);
            }
            return decode(() -> new FileInputStream(f), width);
        } catch (Exception e) {
            return null;
        }
    }

    /** keep the 30 newest: the sources change their lists daily */
    private static void prune(File dir) {
        File[] all = dir.listFiles((d, n) -> n.endsWith(".jpg"));
        if (all == null || all.length <= 30) return;
        java.util.Arrays.sort(all, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
        for (int i = 30; i < all.length; i++) all[i].delete();
    }

    private interface Source { InputStream open() throws java.io.IOException; }

    private static Bitmap decode(Source s, int want) throws java.io.IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = s.open()) { if (in == null) return null; BitmapFactory.decodeStream(in, null, o); }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= want) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = s.open()) { return in == null ? null : BitmapFactory.decodeStream(in, null, o); }
    }
}
