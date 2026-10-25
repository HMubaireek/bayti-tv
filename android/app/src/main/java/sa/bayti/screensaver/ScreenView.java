package sa.bayti.screensaver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** The WebView shared by the preview activity and the dream. */
final class ScreenView {
    private ScreenView() {}

    static WebView create(final Context c) { return build(c, new Prefs(c).dashboardUrl(c), true); }

    /** For the settings' pickers: the page drawn live for one card (url from Prefs.previewUrl), nothing else started. */
    static WebView createPreview(final Context c, String url) { return build(c, url, false); }

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    private static WebView build(final Context c, String url, boolean screen) {
        WebView w = new WebView(c);
        w.setBackgroundColor(screen ? 0xFF040E09 : 0);   // no white flash before the page paints (a preview is the card on the picker's own background)
        w.setFocusable(false);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final Context app = c.getApplicationContext();   // the client must not keep the dream or activity alive
        w.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                WebResourceResponse res = PhotoCache.serve(app, r.getUrl().toString());
                return res != null ? res : super.shouldInterceptRequest(v, r);
            }
        });
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        // lets the page call Yahoo and other APIs directly, without a CORS gateway
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);   // photos are cached by PhotoCache, data must be fresh
        // The page sizes its text to the screen; the TV's font size setting (Android scales WebView text by it) made
        // it larger than its boxes, so the left column spilled off screen and moved as the clock and the matches changed.
        s.setTextZoom(100);
        if (android.os.Build.VERSION.SDK_INT >= 23 && screen) s.setOffscreenPreRaster(true);   // draw before it is shown
        if (screen) w.addJavascriptInterface(new NowPlayingBridge(c), "UrtvNative");   // the «يُتلى الآن» card
        w.loadUrl(url);
        if (screen) {
            UpdateJob.schedule(c);
            Updater.checkIfDue(c);   // the page and version check, at most every 6 hours, here and not on a timer
        }
        return w;
    }

    /** Takes the view out of its window and frees the WebView and everything it holds. */
    static void release(WebView w) {
        if (w == null) return;
        w.stopLoading();
        if (w.getParent() instanceof ViewGroup) ((ViewGroup) w.getParent()).removeView(w);
        w.destroy();
    }
}
