package sa.bayti.home.tilawah;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * The way in for «أذان من ملفي»: `content write --uri content://sa.bayti.home.adhan/own-in` from the shell
 * (optimize/music.sh adhan add) streams the recording into this app's private folder; MusicReceiver ADHAN_OWN then
 * checks and keeps it. Only the shell may use it (the manifest asks for DUMP, as for the receivers), it only
 * writes, and nothing can be read back from here.
 */
public class OwnAdhanProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (getContext() == null || seg.size() != 1 || !"own-in".equals(seg.get(0)) || !mode.contains("w") || mode.contains("a"))
            throw new FileNotFoundException();
        return ParcelFileDescriptor.open(OwnAdhan.incoming(getContext()), ParcelFileDescriptor.MODE_WRITE_ONLY
                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
