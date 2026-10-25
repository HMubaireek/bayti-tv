package sa.bayti.screensaver;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * «مصادر الصور»: where the background photos come from. Every source is free and needs no key: our own Saudi set (photos/index.json in
 * the repo, as always), Wikimedia Commons' featured pictures (free licences, the author and the licence go in the credit) and Bing's
 * photo of the day. The chosen ones are merged into one list in the page's own format, a JSON array of {f, c, d}: f is a file of photos/
 * or a full https address, c the credit, d how bright it may show. The page gets it in place of photos/index.json (PhotoCache) and the
 * launcher through ConfigProvider .../photos, so both show the same photos. Each source is fetched at most once a day; a source that
 * fails keeps its last list, so offline nothing changes.
 */
final class PhotoSources {
    static final String SAUDI = "saudi";
    /** key, Arabic label: the order of the settings' list */
    static final String[][] ALL = {
        {SAUDI, "صور السعودية"},
        {"nature", "طبيعة ومناظر (ويكيميديا)"},
        {"mountains", "جبال (ويكيميديا)"},
        {"sunsets", "غروب وشروق (ويكيميديا)"},
        {"space", "الفضاء والفلك (ويكيميديا)"},
        {"potd", "صورة اليوم (ويكيميديا)"},
        {"bing", "صورة اليوم (Bing)"},
    };
    /** the Wikimedia Commons category of each source that is one */
    private static final String[][] CATEGORIES = {
        {"nature", "Featured_pictures_of_landscapes"}, {"mountains", "Featured_pictures_of_mountains"},
        {"sunsets", "Featured_pictures_of_sunsets"}, {"space", "Featured_pictures_of_astronomy"},
    };
    /** where a photo of a source may come from: PhotoCache keeps these on disk, nothing else from the outside */
    static final String[] HOSTS = {"https://upload.wikimedia.org/", "https://thumb.wikimedia.org/", "https://www.bing.com/"};
    private static final String UA = "BaytiTV/1.0 (https://github.com/HMubaireek/bayti-tv)";
    private static final long DAY_MS = 24 * 3600 * 1000L;
    /** at most this many from one source: a category has hundreds */
    static final int PER_SOURCE = 60;

    private PhotoSources() {}

    static boolean isSource(String key) {
        for (String[] s : ALL) if (s[0].equals(key)) return true;
        return false;
    }

    static String label(String key) {
        for (String[] s : ALL) if (s[0].equals(key)) return s[1];
        return key;
    }

    /** a photo address of an allowed source host */
    static boolean allowed(String url) {
        for (String h : HOSTS) if (url.startsWith(h)) return true;
        return false;
    }

    /** Only our own Saudi set: the page and the launcher then read photos/index.json as they always did. */
    static boolean onlySaudi(List<String> chosen) { return chosen.size() == 1 && SAUDI.equals(chosen.get(0)); }

    /**
     * The merged list of the chosen sources, in that order. Blocking (network when a source is a day old): call it off the main thread.
     * Never null; "[]" when no source has given anything yet.
     */
    static synchronized String index(Context c, List<String> chosen) {
        JSONArray out = new JSONArray();
        for (String key : chosen) {
            JSONArray one = list(c, key);
            for (int i = 0; i < one.length(); i++) out.put(one.opt(i));
        }
        return out.toString();
    }

    /** One source's list: its copy on disk while it is less than a day old, else fetched again (a failure keeps the old copy). */
    private static JSONArray list(Context c, String key) {
        File f = new File(new File(c.getFilesDir(), "sources"), key + ".json");
        JSONArray kept = read(f);
        if (kept != null && System.currentTimeMillis() - f.lastModified() < DAY_MS) return kept;
        try {
            JSONArray fresh = fetch(key);
            if (fresh.length() > 0) {
                write(f, fresh.toString());
                return fresh;
            }
        } catch (Exception ignored) {
            // offline, or the service is down: the last list stays
        }
        if (kept != null) f.setLastModified(System.currentTimeMillis() - DAY_MS + 3600_000L);   // try again in an hour, not at once
        return kept != null ? kept : new JSONArray();
    }

    static JSONArray fetch(String key) throws Exception {
        if (SAUDI.equals(key)) return saudi();
        if ("bing".equals(key)) return bing();
        if ("potd".equals(key)) return potd();
        for (String[] cat : CATEGORIES) if (cat[0].equals(key)) return category(cat[1]);
        return new JSONArray();
    }

    /** our own set, as the repo lists it */
    private static JSONArray saudi() throws Exception {
        JSONArray all = new JSONArray(get(PhotoCache.REPO + "photos/index.json"));
        JSONArray out = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject p = all.optJSONObject(i);
            if (p != null && p.optString("f").matches("[A-Za-z0-9_-]+\\.jpg")) out.put(p);
        }
        return out;
    }

    /** Bing's photo of the day, the last 8 days, at 1920 x 1080 */
    private static JSONArray bing() throws Exception {
        JSONArray images = new JSONObject(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US"))
            .getJSONArray("images");
        JSONArray out = new JSONArray();
        for (int i = 0; i < images.length(); i++) {
            JSONObject im = images.getJSONObject(i);
            String base = im.optString("urlbase");
            if (!base.startsWith("/th?id=")) continue;
            out.put(entry("https://www.bing.com" + base + "_1920x1080.jpg", credit(im.optString("copyright"), "Bing")));
        }
        return out;
    }

    /** Wikimedia Commons' picture of the day, the last 30 days (the ones wide enough for a TV) */
    private static JSONArray potd() throws Exception {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        day.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        StringBuilder titles = new StringBuilder();
        for (int d = 0; d < 30; d++) {
            if (titles.length() > 0) titles.append('|');
            titles.append("Template:Potd/").append(day.format(cal.getTime()));
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        return commons("generator=images&gimlimit=50&titles=" + URLEncoder.encode(titles.toString(), "UTF-8"));
    }

    /** the newest featured pictures of a Commons category (50: Commons makes thumbnails for at most 50 files a request) */
    private static JSONArray category(String name) throws Exception {
        return commons("generator=categorymembers&gcmtype=file&gcmlimit=50&gcmsort=timestamp&gcmdir=desc&gcmtitle=Category:" + name);
    }

    private static JSONArray commons(String generator) throws Exception {
        JSONObject q = new JSONObject(get("https://commons.wikimedia.org/w/api.php?action=query&format=json&" + generator
            + "&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&iiurlwidth=1920&iiextmetadatafilter=Artist%7CLicenseShortName"))
            .optJSONObject("query");
        JSONArray out = new JSONArray();
        if (q == null || q.optJSONObject("pages") == null) return out;
        JSONObject pages = q.getJSONObject("pages");
        for (java.util.Iterator<String> it = pages.keys(); it.hasNext() && out.length() < PER_SOURCE; ) {
            JSONArray info = pages.getJSONObject(it.next()).optJSONArray("imageinfo");
            if (info == null || info.length() == 0) continue;
            JSONObject im = info.getJSONObject(0);
            String url = im.optString("thumburl").replaceFirst("\\?.*$", "");   // without the ?utm_… Wikimedia adds
            if (!wide(im.optInt("width"), im.optInt("height")) || !allowed(url) || !url.matches("(?i).+\\.jpe?g")) continue;
            JSONObject meta = im.optJSONObject("extmetadata");
            String artist = meta == null ? "" : plain(meta.optJSONObject("Artist") == null ? "" : meta.getJSONObject("Artist").optString("value"));
            String licence = meta == null || meta.optJSONObject("LicenseShortName") == null ? "" : meta.getJSONObject("LicenseShortName").optString("value");
            out.put(entry(url, credit(artist + (licence.isEmpty() ? "" : " · " + licence), "Wikimedia Commons")));
        }
        return out;
    }

    /** a landscape photo a TV can fill: not a portrait, not a long panorama */
    static boolean wide(int w, int h) { return h > 0 && w >= 1600 && w >= h * 1.3 && w <= h * 2.3; }

    /** HTML of an author field to its text */
    static String plain(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replaceAll("\\s+", " ").trim();
    }

    private static String credit(String who, String where) {
        String w = who == null ? "" : who.trim();
        if (w.length() > 80) w = w.substring(0, 80).trim() + "…";
        return w.isEmpty() ? where : w + " · " + where;
    }

    private static JSONObject entry(String url, String credit) throws Exception {
        return new JSONObject().put("f", url).put("c", credit).put("d", 0.9);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);
        con.setRequestProperty("User-Agent", UA);   // Wikimedia asks every client to say who it is
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    private static JSONArray read(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new JSONArray(new String(b.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(File f, String s) throws java.io.IOException {
        File dir = f.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        File tmp = new File(dir, f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(s.getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(f)) tmp.delete();
    }

    /** the chosen sources of a saved "saudi,nature" (unknown keys dropped, each once); our Saudi set when none is left */
    static List<String> parse(String saved) {
        List<String> out = new ArrayList<>();
        for (String k : (saved == null ? "" : saved).split(",")) {
            String key = k.trim();
            if (isSource(key) && !out.contains(key)) out.add(key);
        }
        if (out.isEmpty()) out.add(SAUDI);
        return out;
    }
}
