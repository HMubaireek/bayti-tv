package sa.bayti.home.tilawah;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * What the screensaver's «يُتلى الآن» card asks: one row (playing, title, who) while a surah or a station is playing, no row for
 * anything else (nothing on, paused, the adhan). Read-only; only the screensaver, signed with the same key, may ask (the manifest's
 * READ_NOW_PLAYING). The screensaver asks when it starts and then once a minute, so nothing is pushed from here.
 */
public class NowPlayingProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor out = new MatrixCursor(new String[]{"playing", "title", "who"});
        if (getContext() == null) return out;
        NowPlaying.State s = NowPlaying.read(getContext());
        if (s.show && s.playing) out.addRow(new Object[]{1, s.title, s.who});
        return out;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
