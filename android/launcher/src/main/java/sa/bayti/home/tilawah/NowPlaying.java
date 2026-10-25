package sa.bayti.home.tilawah;

import android.content.Context;
import android.content.Intent;

/**
 * What the home screen's «يُتلى الآن» card needs from «تلاوة», in one place the home package can reach: whether a surah or a station
 * is on (read() is false for nothing and for the adhan), the card's words, and the actions OK, ◀ ▶ and a long OK do. MusicService sends
 * CHANGED (to this app only) whenever what the card shows may have changed; the card listens, nothing polls.
 */
public final class NowPlaying {
    /** sent by MusicService, to this app, when the source, the play/pause state, the surah or station, an error or the sleep timer changes */
    public static final String CHANGED = "sa.bayti.home.tilawah.NOW_PLAYING_CHANGED";

    public static final class State {
        /** a surah or a station is on (playing or paused); false for nothing, and for the adhan */
        public final boolean show;
        public final boolean playing, radio;
        public final String title, sub, hint;
        /** who recites it («ماهر المعيقلي»), or «بث مباشر» for a station: the screensaver's card says it after the title */
        public final String who;

        State(boolean show, boolean playing, boolean radio, String title, String sub, String hint, String who) {
            this.show = show;
            this.playing = playing;
            this.radio = radio;
            this.title = title;
            this.sub = sub;
            this.hint = hint;
            this.who = who;
        }
    }

    private NowPlaying() {}

    public static State read(Context c) {
        int source = MusicService.currentSource();
        boolean radio = source == Prefs.SOURCE_RADIO;
        if (source != Prefs.SOURCE_SURAH && !radio) return new State(false, false, false, "", "", "", "");
        Catalog cat = Catalog.get(c);
        int reciter = MusicService.reciterIndex(), station = MusicService.currentStation();
        String stationName = station >= 0 && station < cat.stations.size() ? cat.stations.get(station).name : "";
        String reciterName = reciter >= 0 && reciter < cat.reciters.size() ? cat.reciters.get(reciter).name : "";
        long until = MusicService.sleepAt();
        int minutes = until > 0 ? (int) Math.max(1, (until - System.currentTimeMillis() + 59_999) / 60_000) : 0;
        return new State(true, MusicService.isPlaying(), radio,
                NowPlayingText.title(radio, cat.surahName(MusicService.currentSurah()), stationName),
                NowPlayingText.sub(radio, reciterName, minutes), NowPlayingText.hint(radio),
                NowPlayingText.who(radio, reciterName));
    }

    public static void toggle(Context c) { MusicService.toggle(c); }

    public static void next(Context c) { MusicService.nextAction(c); }

    public static void prev(Context c) { MusicService.prevAction(c); }

    /** the full player: the long OK on the card */
    public static Intent player(Context c) { return new Intent(c, PlayerActivity.class); }
}
