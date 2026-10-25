package sa.bayti.home;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import sa.bayti.home.tilawah.NowPlaying;

/**
 * «يُتلى الآن»: the small card at the right end of the pills row while a surah or a station is on. A pill's height (Pill.HEIGHT), so the
 * row does not change; a play/pause mark, the surah or station on its first line and who recites it (and the sleep timer) on its second.
 * It takes the focus like a pill; then the second line says what the keys do. MainActivity binds it from NowPlaying and does the keys.
 */
final class NowCard extends LinearLayout {
    private static final int ON = 0xE6FFFFFF, FOCUSED = 0xFF111111, GOLD = 0xFFD4AF6A, DIM = 0xFFC6BBA4;

    private final GradientDrawable bg = new GradientDrawable();
    private final Drawable play, pause;
    private final ImageView mark;
    private final TextView title, sub;
    private String subText = "", hintText = "";
    private boolean playing;

    NowCard(Context c, Typeface strong, Typeface plain, Drawable playIcon, Drawable pauseIcon) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setPadding(Math.round(10 * d), 0, Math.round(12 * d), 0);
        bg.setCornerRadius(15 * d);
        bg.setColor(0x66000000);
        bg.setStroke(Math.round(d), 0x66D4AF6A);
        setBackground(bg);

        play = playIcon.mutate();
        pause = pauseIcon.mutate();
        mark = new ImageView(c);
        addView(mark, new LayoutParams(Math.round(14 * d), Math.round(14 * d)));

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        title = line(c, strong, 13, d);
        sub = line(c, plain, 10.5f, d);
        words.addView(title, new LayoutParams(-2, -2));
        words.addView(sub, new LayoutParams(-2, -2));
        LayoutParams wp = new LayoutParams(-2, -2);
        wp.setMargins(0, 0, Math.round(8 * d), 0);   // the gap to the mark, on the words' right
        addView(words, wp);
        paint(false);
    }

    private static TextView line(Context c, Typeface face, float sp, float d) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setMaxWidth(Math.round(240 * d));   // the row stays clear of the greeting block at the right
        t.setGravity(Gravity.RIGHT);
        return t;
    }

    /** what the card says now (MainActivity, on a change and once a minute for the sleep timer) */
    void bind(NowPlaying.State s) {
        playing = s.playing;
        title.setText(Digits.of(s.title));
        subText = Digits.of(s.sub);
        hintText = Digits.of(s.hint);
        setContentDescription(Digits.of(s.title + "، " + s.sub));
        paint(hasFocus());
    }

    @Override protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gained, direction, previouslyFocusedRect);
        paint(gained);
    }

    private void paint(boolean focused) {
        bg.setColor(focused ? 0xF2FFFFFF : 0x66000000);
        title.setTextColor(focused ? FOCUSED : ON);
        sub.setTextColor(focused ? FOCUSED : DIM);
        sub.setText(focused ? hintText : subText);   // the keys, while it has the focus
        Drawable icon = playing ? pause : play;   // the mark says what OK does: pause while it plays, play while it is paused
        icon.setTint(focused ? FOCUSED : GOLD);
        mark.setImageDrawable(icon);
    }
}
