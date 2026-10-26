package sa.bayti.home;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * «المزيد»: the control center over the home screen. A frosted panel under the top bar with a tile for each thing that used to be a
 * pill (Wi-Fi, the screensaver, «المهام», «تلاوة وأذان», the sleep timer, «ترتيب المفضلة»): its icon, its name and a line of state.
 * The remote moves tile to tile (the grid reads from the right, like the page), OK runs it and closes the panel, Back closes it.
 */
final class ControlCenter {
    /** One tile: what it shows and what OK does. `state` is read each time the panel opens. */
    static final class Tile {
        final int icon;
        final String title;
        final State state;
        final Action action;
        Tile(int icon, String title, State state, Action action) { this.icon = icon; this.title = title; this.state = state; this.action = action; }
    }
    interface State { String now(); }
    /** OK on a tile: `anchor` is the tile, for a menu that opens beside it */
    interface Action { void run(View anchor); }

    static final int COLS = 3;

    private ControlCenter() {}

    static Dialog show(Activity a, List<Tile> tiles, Typeface bold, Typeface regular, Runnable dismissed) {
        float d = a.getResources().getDisplayMetrics().density;
        Dialog dlg = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(a);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> dlg.dismiss());

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(d, 22), dp(d, 18), dp(d, 22), dp(d, 22));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xE6262A27, 0xE618201B});
        glass.setCornerRadius(dp(d, 28));
        glass.setStroke(dp(d, 1), 0x33FFFFFF);
        panel.setBackground(glass);
        panel.setElevation(dp(d, 24));

        TextView head = new TextView(a);
        head.setText("مركز التحكم");
        head.setTypeface(bold);
        head.setTextSize(14);
        head.setTextColor(0xFFD6AF6B);
        head.setGravity(Gravity.RIGHT);
        panel.addView(head, new LinearLayout.LayoutParams(-1, -2));

        List<View> cells = new ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(a);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
                rp.topMargin = dp(d, 12);
                panel.addView(row, rp);
            }
            Tile t = tiles.get(i);
            View cell = tile(a, d, t, bold, regular);
            cell.setOnClickListener(v -> { dlg.dismiss(); t.action.run(v); });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(d, 168), dp(d, 104));
            if (i % COLS != 0) cp.setMarginStart(dp(d, 12));
            row.addView(cell, cp);
            cells.add(cell);
        }
        // one tile at a time, no wrapping at the ends of a row; Up from the first row stays (the panel is the whole screen's focus)
        for (int i = 0; i < cells.size(); i++) {
            View c = cells.get(i);
            int col = i % COLS;
            c.setNextFocusRightId(col == 0 ? c.getId() : cells.get(i - 1).getId());   // the right is the row's start (RTL)
            c.setNextFocusLeftId(col == COLS - 1 || i == cells.size() - 1 ? c.getId() : cells.get(i + 1).getId());
            c.setNextFocusUpId(i >= COLS ? cells.get(i - COLS).getId() : c.getId());
            c.setNextFocusDownId(i + COLS < cells.size() ? cells.get(i + COLS).getId() : c.getId());
        }

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        pp.leftMargin = dp(d, 48);
        pp.topMargin = dp(d, 78);
        root.addView(panel, pp);
        panel.setOnClickListener(v -> { });   // a click inside the panel's gaps does not close it

        dlg.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, -1);
            if (Build.VERSION.SDK_INT >= 31) {   // Android 12: the home screen frosts behind the panel
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                w.getAttributes().setBlurBehindRadius(dp(d, 28));
            }
        }
        dlg.setOnDismissListener(x -> { if (dismissed != null) dismissed.run(); });
        dlg.show();
        panel.setAlpha(0f);
        panel.setTranslationY(-dp(d, 18));
        panel.animate().alpha(1f).translationY(0).setInterpolator(new DecelerateInterpolator()).setDuration(220).start();
        if (!cells.isEmpty()) cells.get(0).requestFocus();
        return dlg;
    }

    private static View tile(Activity a, float d, Tile t, Typeface bold, Typeface regular) {
        LinearLayout cell = new LinearLayout(a);
        cell.setId(View.generateViewId());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(d, 16), dp(d, 12), dp(d, 16), dp(d, 12));
        cell.setFocusable(true);
        cell.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(d, 20));
        bg.setColor(0x1FFFFFFF);
        bg.setStroke(dp(d, 1), 0x14FFFFFF);
        cell.setBackground(bg);

        ImageView icon = new ImageView(a);
        icon.setImageResource(t.icon);
        icon.setColorFilter(0xFFF3EBD9);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(d, 26), dp(d, 26)));

        TextView title = new TextView(a);
        title.setText(t.title);
        title.setTypeface(bold);
        title.setTextSize(14);
        title.setTextColor(0xFFF3EBD9);
        title.setSingleLine(true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(d, 8);
        cell.addView(title, tp);

        TextView sub = new TextView(a);
        String now = t.state == null ? null : t.state.now();
        sub.setText(now == null ? "" : now);
        sub.setTypeface(regular);
        sub.setTextSize(11);
        sub.setTextColor(0xB3F3EBD9);
        sub.setSingleLine(true);
        sub.setVisibility(now == null || now.isEmpty() ? View.GONE : View.VISIBLE);
        cell.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        title.setGravity(Gravity.RIGHT);
        sub.setGravity(Gravity.RIGHT);

        cell.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2F3EBD9 : 0x1FFFFFFF);
            icon.setColorFilter(has ? 0xFF14110C : 0xFFF3EBD9);
            title.setTextColor(has ? 0xFF14110C : 0xFFF3EBD9);
            sub.setTextColor(has ? 0xB314110C : 0xB3F3EBD9);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                .setInterpolator(has ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator()).setDuration(220).start();
        });
        return cell;
    }

    private static int dp(float density, float v) { return Math.round(v * density); }
}
