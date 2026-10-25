package sa.bayti.screensaver;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/**
 * The screen's «يُتلى الآن» card: the page asks window.UrtvNative.nowPlaying() when it starts and once a minute; the answer is what the
 * launcher's NowPlayingProvider says, as {"title":"سورة الكهف","who":"ماهر المعيقلي"}, or "" when nothing is playing (or the launcher is
 * not installed). Nothing else is reachable from the page through this, and it is given to the screen only (not to the settings' previews).
 */
final class NowPlayingBridge {
    private final Context app;

    NowPlayingBridge(Context c) { app = c.getApplicationContext(); }

    @JavascriptInterface
    public String nowPlaying() {
        Cursor c = null;
        try {
            c = app.getContentResolver().query(Uri.parse("content://sa.bayti.home.nowplaying/now"), null, null, null, null);
            if (c != null && c.moveToFirst()) {
                JSONObject o = new JSONObject();
                o.put("title", c.getString(c.getColumnIndexOrThrow("title")));
                o.put("who", c.getString(c.getColumnIndexOrThrow("who")));
                return o.toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) { }
        }
        return "";
    }
}
