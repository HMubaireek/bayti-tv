package sa.urtv.screensaver;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

import java.util.List;

/**
 * Hands the launcher (same signing key, see READ_CONFIG in the manifest) what its home-screen card needs:
 * the page settings as a URL fragment, which card to show ("none" = off) and whether the greeting is on ("1"/"0").
 *
 * «المهام»: content://sa.urtv.screensaver.config/tasks lists the tasks (i from 1, t, done, at). The launcher
 * may also change them, which needs WRITE_TASKS (signature, same key as READ_CONFIG):
 *   insert  ContentValues t = the text               -> .../tasks/N, or null when refused (empty, full)
 *   update  ContentValues i = N                      -> 1 when task N is marked done
 *   delete  .../tasks/N                              -> 1 when task N is gone
 *
 * «مصادر الصور»: query .../photos gives json (the merged list of the chosen photo sources, PhotoSources; null while only our Saudi set
 * is chosen, so the launcher reads photos/index.json as always) and minutes (how often the photo changes). It may fetch: off the main thread.
 *
 * «صوري»: the owner's own photos (Own). query .../own gives n (how many), mode (mix|only) and rev (changes whenever
 * the photos do); openFile .../own/N opens photo N (1..n) read-only, which READ_CONFIG allows the launcher. Nothing
 * else can be read or written there. .../own-in takes the next photo from the shell (`content write`), which the
 * manifest lets in with DUMP, as for SaverReceiver; SaverReceiver PHOTOS then checks and keeps it.
 */
public class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Prefs p = new Prefs(getContext());
        if (isOwn(uri)) {
            MatrixCursor o = new MatrixCursor(new String[] {"n", "mode", "rev"});
            o.addRow(new Object[] {Own.count(getContext()), Own.mode(getContext()), p.ownRev()});
            return o;
        }
        if (isPhotos(uri)) {   // «مصادر الصور»: the merged list (PhotoSources) when other sources than our Saudi set are chosen, else none
            MatrixCursor o = new MatrixCursor(new String[] {"json", "minutes"});
            List<String> chosen = p.photoSources();
            o.addRow(new Object[] {PhotoSources.onlySaudi(chosen) ? null : PhotoSources.index(getContext(), chosen), p.photoMinutes()});
            return o;
        }
        if (isTasks(uri)) {
            MatrixCursor t = new MatrixCursor(new String[] {"i", "t", "done", "at"});
            List<Prefs.Task> all = p.tasks();
            for (int k = 0; k < all.size(); k++) {
                Prefs.Task x = all.get(k);
                t.addRow(new Object[] {k + 1, x.text, x.done ? 1 : 0, x.at});
            }
            return t;
        }
        // home_panel: the first card alone (what an older launcher reads); home_panels: all of them, "a,b,c" or "none"
        List<String> cards = p.homePanels();
        MatrixCursor c = new MatrixCursor(new String[] {"fragment", "home_panel", "greeting", "home_panels", "adhan_bar"});
        c.addRow(new Object[] {p.fragment(), cards.isEmpty() ? "none" : cards.get(0), p.greeting() ? "1" : "0", p.homePanel(), p.adhanBar() ? "1" : "0"});
        return c;
    }

    @Override public String getType(Uri uri) { return null; }

    private static boolean isPhotos(Uri uri) {
        return uri != null && uri.getPathSegments().size() == 1 && "photos".equals(uri.getPathSegments().get(0));
    }

    private static boolean isOwn(Uri uri) {
        return uri != null && uri.getPathSegments().size() == 1 && "own".equals(uri.getPathSegments().get(0));
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context c = getContext();
        java.util.List<String> seg = uri.getPathSegments();
        if (seg.size() == 2 && "own".equals(seg.get(0)) && "r".equals(mode)) {
            int n;
            try { n = Integer.parseInt(seg.get(1)); } catch (NumberFormatException e) { throw new FileNotFoundException(); }
            if (n < 1 || n > Own.count(c)) throw new FileNotFoundException();
            return ParcelFileDescriptor.open(Own.file(c, n), ParcelFileDescriptor.MODE_READ_ONLY);
        }
        if (seg.size() == 1 && "own-in".equals(seg.get(0)) && mode.contains("w") && !mode.contains("a")) {
            File dir = Own.dir(c);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new FileNotFoundException();
            return ParcelFileDescriptor.open(Own.incoming(c), ParcelFileDescriptor.MODE_WRITE_ONLY
                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        throw new FileNotFoundException();
    }

    private static boolean isTasks(Uri uri) {
        return uri != null && uri.getPathSegments().size() >= 1 && "tasks".equals(uri.getPathSegments().get(0));
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        if (!isTasks(uri) || v == null) return null;
        Prefs p = new Prefs(getContext());
        if (p.addTask(v.getAsString("t")) != null) return null;
        return Uri.withAppendedPath(uri, String.valueOf(p.tasks().size()));
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        if (!isTasks(uri) || uri.getPathSegments().size() < 2) return 0;
        try {
            return new Prefs(getContext()).deleteTask(Integer.parseInt(uri.getLastPathSegment())) ? 1 : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        if (!isTasks(uri) || v == null || v.getAsInteger("i") == null) return 0;
        return new Prefs(getContext()).doneTask(v.getAsInteger("i")) ? 1 : 0;
    }
}
