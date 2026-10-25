package sa.bayti.home.tilawah;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * «أذان من ملفي»: the person's own adhan recording, one file in this app's private folder
 * (files/adhan-own.m4a). It only arrives over ADB (MusicReceiver ADHAN_OWN, optimize/music.sh adhan add) and stays on
 * this TV: nothing reads it out and nothing sends it anywhere. A new one replaces the old.
 *
 * It is checked before it is kept: an m4a or an Ogg, at most OwnAdhanText.MAX_BYTES, readable, at most 5 minutes.
 */
final class OwnAdhan {
    private OwnAdhan() {}

    static File file(Context c) { return new File(c.getFilesDir(), "adhan-own.m4a"); }

    /** where OwnAdhanProvider lets the shell write the next one before MusicReceiver keeps it */
    static File incoming(Context c) { return new File(c.getFilesDir(), "adhan-own-in.tmp"); }

    static boolean exists(Context c) { return file(c).length() > 0; }

    static long bytes(Context c) { return file(c).length(); }

    /** "" when src is a recording we can keep, else why not */
    static String check(File src) {
        byte[] head = new byte[8];
        int n;
        try (InputStream in = new FileInputStream(src)) {
            n = in.read(head);
        } catch (IOException e) {
            return "cannot read it";
        }
        if (OwnAdhanText.container(head, n).isEmpty()) return "not an m4a (AAC) or Ogg (Opus) recording";
        long ms = 0;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(src.getPath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            ms = d == null ? 0 : Long.parseLong(d);
        } catch (RuntimeException e) {
            ms = 0;
        } finally {
            try { r.release(); } catch (Exception ignored) { }   // release() declares IOException from API 29
        }
        return OwnAdhanText.refuse(src.length(), ms);
    }

    /** Keeps src as the one recording, replacing the old. null when done, else why not; src is gone either way if it is in.tmp. */
    static synchronized String keep(Context c, File src) {
        String why = check(src);
        if (why.isEmpty()) {
            File dest = file(c);
            if (dest.exists() && !dest.delete()) why = "no room to keep it";
            else if (src.equals(incoming(c))) {
                if (!src.renameTo(dest)) why = "no room to keep it";
            } else {
                why = copy(src, dest);
            }
        }
        if (src.equals(incoming(c)) && src.exists()) src.delete();   // never leave a refused file to be taken for the next one
        return why.isEmpty() ? null : why;
    }

    private static String copy(File src, File dest) {
        try (InputStream in = new FileInputStream(src); java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return "";
        } catch (IOException e) {
            dest.delete();
            return "cannot copy it (" + e.getMessage() + ")";
        }
    }

    static synchronized void clear(Context c) {
        file(c).delete();
        incoming(c).delete();
    }
}
