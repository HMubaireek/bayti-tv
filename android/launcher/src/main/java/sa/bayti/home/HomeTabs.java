package sa.bayti.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The tabs in the middle of the top bar: «الرئيسية» (the home screen itself), «أفلام» and «مسلسلات» (src/tabs.html, rows from TMDB
 * with the owner's own key) and «يومي» (the screensaver's page as a board of every card, #board=1). Which of the last three show, and
 * the TMDB key, come from the screensaver's settings (its ConfigProvider .../home). With only «الرئيسية» left there is no tab row.
 * Without a key «أفلام» and «مسلسلات» are dimmed and the remote passes them by.
 *
 * One WebView, made the first time a tab other than «الرئيسية» opens and kept, shows the page of the tab; it has the remote while the
 * focus is in it, and the page gives it back to the tab row through window.Bayti (Up from its first row).
 */
final class HomeTabs {
    static final Uri HOME = Uri.parse("content://sa.bayti.screensaver.config/home");
    private static final String FONTS = "https://raw.githubusercontent.com/HMubaireek/bayti-tv/master/assets/fonts/";
    static final String HOME_TAB = "home";
    private static final String[][] ALL = {{HOME_TAB, "الرئيسية"}, {"movies", "أفلام"}, {"shows", "مسلسلات"}, {"today", "يومي"}};

    /** What the home screen does when the tab changes, and where the focus goes down from the tab row. */
    interface Host {
        void showHome(boolean home);
        void downFromTabs();
        String fragment();   // the screensaver's settings, for «يومي»
        String digits();     // hindi or latin, for the TMDB pages
    }

    private final Activity a;
    private final Host host;
    private final Typeface bold, regular;
    private final LinearLayout row;
    private final List<TextView> views = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final FrameLayout webBox;
    private WebView web;
    private String current = HOME_TAB, loaded, tmdb = "", shown = "";

    HomeTabs(Activity a, Host host, Typeface bold, Typeface regular, FrameLayout webBox) {
        this.a = a;
        this.host = host;
        this.bold = bold;
        this.regular = regular;
        this.webBox = webBox;
        row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
    }

    /** The tab row, for the top bar. */
    View row() { return row; }

    List<TextView> views() { return views; }

    boolean any() { return views.size() > 1; }

    String current() { return current; }

    /** The tab view of the tab shown now, for the focus coming back up. */
    View currentView() {
        int i = keys.indexOf(current);
        return i < 0 ? null : views.get(i);
    }

    /** onResume: read the settings; true when the tabs changed (the focus wiring has to be done again). */
    boolean refresh() {
        String tabs = "movies,shows,today", key = "", app = "auto";
        try (Cursor c = a.getContentResolver().query(HOME, null, null, null, null)) {
            if (c != null && c.getColumnIndex("tabs") == 0 && c.moveToFirst()) {
                tabs = c.getString(0) == null ? "" : c.getString(0);
                key = c.getString(1) == null ? "" : c.getString(1);
                int t = c.getColumnIndex("title_app");
                if (t >= 0 && c.getString(t) != null) app = c.getString(t);
            }
        } catch (RuntimeException ignored) {
            // an older screensaver app: every tab, no key
        }
        titleApp = resolveApp(app);
        String sig = tabs + "|" + (key.isEmpty() ? "" : "k");
        boolean keyChanged = !key.equals(tmdb);
        tmdb = key;
        if (keyChanged && web != null && loaded != null && !loaded.startsWith("today")) loaded = null;   // a new key: the page again
        if (sig.equals(shown)) return false;
        shown = sig;
        build(tabs);
        return true;
    }

    private void build(String tabs) {
        row.removeAllViews();
        views.clear();
        keys.clear();
        List<String> on = new ArrayList<>();
        Collections.addAll(on, tabs.split(","));
        for (String[] t : ALL) {
            if (!HOME_TAB.equals(t[0]) && !on.contains(t[0])) continue;
            keys.add(t[0]);
            views.add(tab(t[0], t[1]));
        }
        if (views.size() <= 1) { row.setVisibility(View.GONE); if (!HOME_TAB.equals(current)) select(HOME_TAB); return; }
        row.setVisibility(View.VISIBLE);
        for (int i = 0; i < views.size(); i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(34));
            if (i > 0) p.setMarginStart(dp(6));
            row.addView(views.get(i), p);
        }
        if (!keys.contains(current)) select(HOME_TAB);
        paint();
    }

    // «فتح العناوين في»: the apps a title can open in at its own page, {setting, package, name}; auto = the first installed
    private static final String[][] TITLE_APPS = {{"nuvio", "com.nuvio.tv", "Nuvio"}, {"stremio", "com.stremio.one", "Stremio"}};
    private String[] titleApp;   // {setting, package, name} of the app chosen and installed, or null: no «شاهد في …» button

    private String[] resolveApp(String setting) {
        for (String[] t : TITLE_APPS) {
            if (!"auto".equals(setting) && !t[0].equals(setting)) continue;
            try { a.getPackageManager().getPackageInfo(t[1], 0); return t; }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        }
        return null;
    }

    /** The title's own page in the chosen app: Nuvio's nuvio://meta (its IMDb id, else its TMDB id), Stremio's stremio:///detail. */
    private void watch(String kind, String tmdbId, String imdbId) {
        String[] app = titleApp;
        if (app == null) return;
        boolean tv = "tv".equals(kind);
        boolean imdb = imdbId != null && imdbId.matches("tt\\d{3,12}");
        String link = null;
        if ("nuvio".equals(app[0])) {
            link = "nuvio://meta?type=" + (tv ? "series" : "movie") + (imdb ? "&id=" + imdbId : tmdbId.matches("\\d{1,10}") ? "&tmdb=" + tmdbId : "");
            if (!imdb && !tmdbId.matches("\\d{1,10}")) link = null;
        } else if ("stremio".equals(app[0]) && imdb) {
            link = "stremio:///detail/" + (tv ? "series" : "movie") + "/" + imdbId;
        }
        if (link != null) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(app[1]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (RuntimeException ignored) {
                // an older version without the link: the app itself
            }
        }
        Intent open = a.getPackageManager().getLeanbackLaunchIntentForPackage(app[1]);
        if (open == null) open = a.getPackageManager().getLaunchIntentForPackage(app[1]);
        if (open != null) a.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private boolean enabled(String key) { return !("movies".equals(key) || "shows".equals(key)) || !tmdb.isEmpty(); }

    private TextView tab(String key, String label) {
        TextView t = new TextView(a);
        t.setId(View.generateViewId());
        t.setText(label);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        boolean on = enabled(key);
        t.setFocusable(on);
        t.setAlpha(on ? 1f : .35f);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(17));
        bg.setColor(Color.TRANSPARENT);
        t.setBackground(bg);
        t.setOnClickListener(v -> { select(key); if (!HOME_TAB.equals(key)) focusPage(); else host.downFromTabs(); });
        t.setOnFocusChangeListener((v, has) -> paint());
        return t;
    }

    /** the tab shown: bold and white with a gold underline; the one with the focus: a light pill */
    private void paint() {
        for (int i = 0; i < views.size(); i++) {
            TextView t = views.get(i);
            boolean sel = keys.get(i).equals(current), focus = t.hasFocus();
            ((GradientDrawable) t.getBackground()).setColor(focus ? 0xF2F3EBD9 : sel ? 0x26FFFFFF : Color.TRANSPARENT);
            t.setTextColor(focus ? 0xFF14110C : sel ? Color.WHITE : 0xB3FFFFFF);
            t.setTypeface(sel || focus ? bold : regular);
        }
    }

    /** Shows that tab: «الرئيسية» brings the home screen back, any other its page. */
    void select(String key) {
        if (!keys.contains(key) || !enabled(key)) key = HOME_TAB;
        current = key;
        paint();
        boolean home = HOME_TAB.equals(key);
        host.showHome(home);
        if (home) {
            if (web != null) { webBox.setVisibility(View.GONE); web.onPause(); }
            return;
        }
        ensureWeb();
        String want = key + "|" + tmdb.length() + "|" + host.digits() + "|" + ("today".equals(key) ? host.fragment() : "");
        if (!want.equals(loaded)) {
            loaded = want;
            web.setBackgroundColor("today".equals(key) ? Color.TRANSPARENT : 0xFF0B0B0C);
            web.loadUrl("today".equals(key)
                ? "file:///android_asset/web/tv.html#" + (host.fragment() == null ? "" : host.fragment()) + "&board=1"
                : "file:///android_asset/web/tabs.html#kind=" + ("shows".equals(key) ? "tv" : "movie") + "&digits=" + host.digits());
        }
        web.onResume();
        webBox.setVisibility(View.VISIBLE);
        webBox.setAlpha(0f);
        webBox.animate().alpha(1f).setDuration(260).start();
    }

    /** The remote into the tab's page. */
    void focusPage() {
        if (web == null || webBox.getVisibility() != View.VISIBLE) return;
        web.requestFocus();
        web.evaluateJavascript("window.baytiActive&&baytiActive(true)", null);
    }

    /** Back: the page's own first (it closes its details), else from the page to the tab row, else from another tab to «الرئيسية». */
    boolean back() {
        if (HOME_TAB.equals(current)) return false;
        if (web != null && web.hasFocus()) {
            web.evaluateJavascript("window.baytiBack?baytiBack():false", r -> {
                if (!"true".equals(r)) toTabs();
            });
            return true;
        }
        select(HOME_TAB);
        View v = currentView();
        if (v != null) v.requestFocus();
        return true;
    }

    void toTabs() {
        if (web != null) web.evaluateJavascript("window.baytiActive&&baytiActive(false)", null);
        View v = currentView();
        if (v != null) v.requestFocus();
    }

    void pause() { if (web != null) web.onPause(); }
    void resume() { if (web != null && webBox.getVisibility() == View.VISIBLE) web.onResume(); }
    void destroy() { if (web != null) { web.destroy(); web = null; } }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void ensureWeb() {
        if (web != null) return;
        web = new WebView(a) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                // Back is the activity's (HomeTabs.back asks the page first); every other key is the page's
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) return false;
                return super.dispatchKeyEvent(e);
            }
        };
        web.setFocusable(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new Bridge(), "Bayti");
        web.setWebViewClient(new WebViewClient() {
            // «يومي» is the screensaver's page: it asks GitHub for its fonts, answered from the APK as for the home card
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith(FONTS)) {
                    String name = u.substring(FONTS.length());
                    if (name.matches("[A-Za-z0-9_-]+\\.woff2")) {
                        try {
                            return new WebResourceResponse("font/woff2", null, 200, "OK",
                                Collections.singletonMap("Access-Control-Allow-Origin", "*"), a.getAssets().open("fonts/" + name));
                        } catch (java.io.IOException ignored) { }
                    }
                }
                return super.shouldInterceptRequest(v, r);
            }
        });
        web.setOnFocusChangeListener((v, has) -> {
            if (has) web.evaluateJavascript("window.baytiActive&&baytiActive(true)", null);
        });
        webBox.addView(web, new FrameLayout.LayoutParams(-1, -1));
    }

    /** window.Bayti in the tab pages */
    private final class Bridge {
        @JavascriptInterface public String key() { return tmdb; }
        /** the name on the «شاهد في …» button, or "" for none */
        @JavascriptInterface public String appName() { String[] t = titleApp; return t == null ? "" : t[2]; }
        @JavascriptInterface public void watch(String kind, String tmdbId, String imdbId) {
            a.runOnUiThread(() -> HomeTabs.this.watch(kind, tmdbId == null ? "" : tmdbId, imdbId));
        }
        @JavascriptInterface public void toTabs() { a.runOnUiThread(HomeTabs.this::toTabs); }
        /** a YouTube trailer: the TV's YouTube app (or what answers YouTube links, like SmartTube) */
        @JavascriptInterface public void trailer(String id) {
            if (id == null || !id.matches("[A-Za-z0-9_-]{6,20}")) return;
            a.runOnUiThread(() -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    try {
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) { }
                }
            });
        }
    }

    private int dp(float v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
}
