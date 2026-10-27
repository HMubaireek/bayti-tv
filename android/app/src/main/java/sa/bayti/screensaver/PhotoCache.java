package sa.bayti.screensaver;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Answers the page's font and photo requests from the device instead of the network, so the screensaver
 * opens at once. Fonts and bundled photos come from the APK; hosted photos are downloaded once and kept.
 */
final class PhotoCache {
    static final String REPO = "https://raw.githubusercontent.com/HMubaireek/bayti-tv/" + BuildConfig.BRANCH + "/";
    private static final String BUNDLED = REPO + "assets/backgrounds/", HOSTED = REPO + "photos/",
        FONTS = REPO + "assets/fonts/";
    /** the owner's own photos (Own): not a real address, the page asks for N.jpg here and only this app answers */
    static final String OWN = "https://own.urtv.invalid/";
    private static final int KEEP = 12;   // the 5-photo set plus room for the next one

    private PhotoCache() {}

    /** null lets the WebView load the URL itself */
    static WebResourceResponse serve(Context c, String url) {
        try {
            if (url.startsWith(FONTS)) {
                String name = url.substring(FONTS.length());
                if (!name.matches("[A-Za-z0-9_-]+\\.woff2")) return null;
                // fonts are fetched with CORS, and the page's origin is file://
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Access-Control-Allow-Origin", "*");
                return new WebResourceResponse("font/woff2", null, 200, "OK", h, c.getAssets().open("fonts/" + name));
            }
            if (url.startsWith(OWN)) {
                String name = url.substring(OWN.length());
                if (!name.matches("([1-9]|[1-5][0-9]|60)\\.jpg")) return null;   // 1 to Own.MAX (60)
                File f = Own.file(c, Integer.parseInt(name.substring(0, name.indexOf('.'))));
                return f.isFile() ? jpeg(new FileInputStream(f)) : null;
            }
            if (url.startsWith(BUNDLED)) {
                String name = safeName(url.substring(BUNDLED.length()));
                return name == null ? null : jpeg(c.getAssets().open("backgrounds/" + name));
            }
            if (url.equals(HOSTED + "index.json")) {
                // «مصادر الصور»: other sources than our Saudi set answer in its place, merged; ours alone go to the network as always
                List<String> chosen = new Prefs(c).photoSources();
                if (PhotoSources.onlySaudi(chosen)) return null;
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Access-Control-Allow-Origin", "*");   // the page's origin is file://
                byte[] body = PhotoSources.index(c, chosen).getBytes(StandardCharsets.UTF_8);
                return new WebResourceResponse("application/json", "utf-8", 200, "OK", h, new ByteArrayInputStream(body));
            }
            if (url.startsWith(HOSTED)) {
                String name = safeName(url.substring(HOSTED.length()));
                if (name == null) return null;   // anything else goes to the network
                File f = hosted(c, url, name);
                return f == null ? null : jpeg(new FileInputStream(f));
            }
            if (PhotoSources.allowed(url)) {   // a photo of another source: kept on disk like ours, under a name made from its address
                File f = hosted(c, url, cacheName(url));
                return f == null ? null : jpeg(new FileInputStream(f));
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String safeName(String s) {
        return s.matches("[A-Za-z0-9_-]+\\.jpg") ? s : null;
    }

    /** the file a photo of another source is kept in: "src-" and its address's SHA-1 (the launcher names them the same way) */
    static String cacheName(String url) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder("src-");
            for (int i = 0; i < 10; i++) s.append(String.format(java.util.Locale.US, "%02x", d[i]));
            return s.append(".jpg").toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "src-" + Integer.toHexString(url.hashCode()) + ".jpg";
        }
    }

    private static WebResourceResponse jpeg(InputStream in) {
        return new WebResourceResponse("image/jpeg", null, in);
    }

    /** Runs on the WebView's network thread, so a blocking download is fine. */
    private static synchronized File hosted(Context c, String url, String name) throws java.io.IOException {
        File dir = new File(c.getFilesDir(), "photos");
        File f = new File(dir, name);
        if (f.isFile() && f.length() > 0) {
            f.setLastModified(System.currentTimeMillis());   // served now: the next prune keeps it (it keeps the newest, by this time)
            return f;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setRequestProperty("User-Agent", "BaytiTV/1.0 (https://github.com/HMubaireek/bayti-tv)");   // Wikimedia asks for one
        File tmp = new File(dir, name + ".tmp");
        try {
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            InputStream in = con.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
                in.close();
            }
            if (!tmp.renameTo(f)) return null;
        } finally {
            con.disconnect();
            tmp.delete();
        }
        prune(dir);
        return f;
    }

    /** Keep the newest few; older sets are not shown again for months. */
    private static void prune(File dir) {
        File[] all = dir.listFiles((d, n) -> n.endsWith(".jpg"));
        if (all == null || all.length <= KEEP) return;
        Arrays.sort(all, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        for (int i = KEEP; i < all.length; i++) all[i].delete();
    }
}
