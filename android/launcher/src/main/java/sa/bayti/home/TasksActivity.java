package sa.bayti.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * «المهام»: the tasks kept by the screensaver app (its ConfigProvider, same signing key), listed with OK to mark
 * one done, «حذف» beside each, and a mic that starts Android's speech recognizer. Said in Arabic:
 * «أضف مهمة …» adds, «خلصت …» finishes, «احذف …» deletes (TaskVoice). Nothing changes until «نعم» is pressed.
 * No dialogs, like DockActivity: rows are built right-aligned by hand. `am start -n sa.bayti.home/.TasksActivity`.
 */
public class TasksActivity extends Activity {
    private static final Uri TASKS = Uri.parse("content://sa.bayti.screensaver.config/tasks");
    private static final int SPEECH = 41;
    /** from the home screen's «المهام» card: the list opens with the focus on «🎤 تكلّم», so one more OK starts the voice adding */
    static final String EXTRA_MIC = "mic";
    private static final int GOLD = 0xFFD6AF6B, INK = 0xFF04120C, TEXT = 0xFFF6EFE0, DIM = 0xFFC6BBA4;
    private static final int MAX_CHARS = 60;   // as the screensaver keeps them (Prefs.TASK_CHARS)

    private final List<String> texts = new ArrayList<>();
    private final List<Boolean> dones = new ArrayList<>();
    private LinearLayout list, confirm;
    private TextView status, question;
    private View mic, yes, no;
    private Runnable onYes;
    private boolean micFirst;   // the first render puts the focus on the mic (EXTRA_MIC), later ones on the list as always

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Digits.init(this);
        micFirst = getIntent().getBooleanExtra(EXTRA_MIC, false);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF0A2016, 0xFF04120C, 0xFF020806}));
        scroll.setVerticalScrollBarEnabled(false);
        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(36), 0, dp(48));
        frame.addView(col, new FrameLayout.LayoutParams(dp(600), -2, Gravity.CENTER_HORIZONTAL));

        col.addView(text("المهام", 26, "Medium", TEXT), right(0));
        col.addView(text("اختيار على مهمة لإنجازها. أو اضغط «تكلّم» وقل: أضف مهمة … / خلصت … / احذف …", 14, "Regular", DIM), right(dp(10)));

        mic = pill("🎤 تكلّم", GOLD, v -> listen());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(140), dp(40));
        mp.gravity = Gravity.RIGHT;
        col.addView(mic, mp);
        status = text("", 14, "Medium", GOLD);
        col.addView(status, right(dp(8)));

        confirm = new LinearLayout(this);
        confirm.setOrientation(LinearLayout.VERTICAL);
        confirm.setVisibility(View.GONE);
        confirm.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setCornerRadius(dp(14));
        cbg.setColor(0x26D6AF6B);
        cbg.setStroke(dp(2), GOLD);
        confirm.setBackground(cbg);
        question = text("", 17, "Medium", TEXT);
        confirm.addView(question, right(dp(10)));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.RIGHT);
        yes = pill("نعم", GOLD, v -> answer(true));
        no = pill("لا", DIM, v -> answer(false));
        buttons.addView(yes, new LinearLayout.LayoutParams(dp(90), dp(38)));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(90), dp(38));
        np.rightMargin = dp(10);
        buttons.addView(no, np);
        confirm.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        col.addView(confirm, right(dp(12)));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, new LinearLayout.LayoutParams(-1, -2));
        setContentView(scroll);

        if (!hasRecognizer()) {
            mic.setVisibility(View.GONE);
            status.setText("الإدخال الصوتي غير متاح في هذا التلفزيون");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(0);
    }

    // ---------------------------------------------------------------- list

    /** false when the screensaver app is missing, too old or refuses us */
    private boolean load() {
        texts.clear();
        dones.clear();
        try (Cursor c = getContentResolver().query(TASKS, null, null, null, null)) {
            if (c == null) return false;
            while (c.moveToNext()) {
                texts.add(c.getString(1));
                dones.add(c.getInt(2) != 0);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void render(int focus) {
        boolean ok = load();
        list.removeAllViews();
        if (!ok) status.setText("تطبيق شاشة التوقف غير مثبّت أو قديم");
        else if (texts.isEmpty()) list.addView(text("لا مهام اليوم", 17, "Medium", DIM), right(0));
        View first = null, wanted = null;
        for (int i = 0; i < texts.size(); i++) {
            final String t = texts.get(i);
            final boolean done = dones.get(i);
            LinearLayout r = new LinearLayout(this);
            View del = pill("حذف", 0xFFEE8B70, v -> ask("أحذف «" + t + "»؟", () -> delete(t)));
            r.addView(del, new LinearLayout.LayoutParams(dp(84), -1));
            View card = card((done ? "✓  " : "●  ") + t, done, v -> {
                if (done) toast("هذي المهمة منجزة");
                else markDone(t);
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.leftMargin = dp(10);
            r.addView(card, cp);
            card.setNextFocusLeftId(del.getId());
            del.setNextFocusRightId(card.getId());
            list.addView(r, rowParams());
            if (first == null) first = card;
            if (i == focus) wanted = card;
        }
        View f = wanted != null ? wanted : first != null ? first : mic.getVisibility() == View.VISIBLE ? mic : null;
        if (micFirst && mic.getVisibility() == View.VISIBLE) f = mic;   // from the home card: the mic first
        micFirst = false;
        if (f != null && confirm.getVisibility() != View.VISIBLE) f.post(f::requestFocus);
    }

    // ---------------------------------------------------------------- changes (the list itself lives in the screensaver app)

    private void markDone(String t) {
        int n = numberOf(t, true);
        ContentValues v = new ContentValues();
        v.put("i", n);
        if (n > 0 && write(() -> getContentResolver().update(TASKS, v, null, null) > 0)) toast("تم: " + t);
        else failed();
    }

    private void delete(String t) {
        int n = numberOf(t, false);
        if (n > 0 && write(() -> getContentResolver().delete(TASKS.buildUpon().appendPath(String.valueOf(n)).build(), null, null) > 0))
            toast("حُذفت: " + t);
        else failed();
    }

    private void add(String t) {
        ContentValues v = new ContentValues();
        v.put("t", t);
        if (write(() -> getContentResolver().insert(TASKS, v) != null)) toast("أُضيفت: " + t);
        else toast("ما أضفت المهمة: القائمة ممتلئة (" + ar(12) + ") أو النص فاضي");
    }

    private interface Change { boolean run(); }

    private boolean write(Change c) {
        try {
            boolean ok = c.run();
            render(0);
            return ok;
        } catch (RuntimeException e) {   // SecurityException: not the same signing key; or the app is gone
            return false;
        }
    }

    private void failed() {
        toast("تعذّر التعديل، افتح المهام من جديد");
        render(0);
    }

    /** the number the screensaver gives it now (from 1), found again by its text: the list may have moved */
    private int numberOf(String t, boolean openOnly) {
        if (!load()) return 0;
        for (int i = 0; i < texts.size(); i++)
            if (texts.get(i).equals(t) && !(openOnly && dones.get(i))) return i + 1;
        return 0;
    }

    // ---------------------------------------------------------------- confirm

    private void ask(String q, Runnable yesAction) {
        onYes = yesAction;
        question.setText(q);
        confirm.setVisibility(View.VISIBLE);
        yes.post(yes::requestFocus);
    }

    private void answer(boolean ok) {
        Runnable r = onYes;
        onYes = null;
        confirm.setVisibility(View.GONE);
        if (ok && r != null) r.run();
        else render(0);
    }

    @Override
    public void onBackPressed() {
        if (confirm.getVisibility() == View.VISIBLE) answer(false);
        else super.onBackPressed();
    }

    // ---------------------------------------------------------------- voice

    private Intent speechIntent() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "أضف مهمة … / خلصت … / احذف …");
        return i;
    }

    private boolean hasRecognizer() { return speechIntent().resolveActivity(getPackageManager()) != null; }

    private void listen() {
        status.setText("");
        onYes = null;
        confirm.setVisibility(View.GONE);
        try { startActivityForResult(speechIntent(), SPEECH); }
        catch (ActivityNotFoundException e) {
            mic.setVisibility(View.GONE);
            status.setText("الإدخال الصوتي غير متاح في هذا التلفزيون");
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != SPEECH || result != RESULT_OK || data == null) return;
        ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (heard == null || heard.isEmpty()) return;
        load();
        TaskVoice.Result r = null;
        for (String h : heard) {   // the recognizer's best guess first; the first one that is a command
            r = TaskVoice.understand(h, texts, dones);
            if (r.kind != TaskVoice.Kind.NONE) break;
        }
        status.setText("سمعت: " + heard.get(0));
        switch (r.kind) {
            case ADD: {
                final String t = clip(r.text);
                ask("أضيف «" + t + "»؟", () -> add(t));
                break;
            }
            case DONE: {
                final String t = r.text;
                ask("أعلّم «" + t + "» منجزة؟", () -> markDone(t));
                break;
            }
            case DELETE: {
                final String t = r.text;
                ask("أحذف «" + t + "»؟", () -> delete(t));
                break;
            }
            case NO_MATCH:
                status.setText("ما لقيت مهمة تطابق «" + r.text + "»");
                break;
            case AMBIGUOUS:
                status.setText("أكثر من مهمة تطابق «" + r.text + "»، قل جزءاً أوضح");
                break;
            default:
                status.setText("ما فهمت. قل: أضف مهمة … أو خلصت … أو احذف …");
        }
    }

    private static String clip(String s) {
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS).trim() : s;
    }

    // ---------------------------------------------------------------- views

    private View card(String label, boolean done, View.OnClickListener click) {
        TextView c = text(label, 17, "Medium", done ? DIM : TEXT);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        c.setFocusable(true);
        c.setClickable(true);
        c.setId(View.generateViewId());
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        View.OnFocusChangeListener paint = (v, has) -> {
            bg.setColor(has ? 0x33D6AF6B : 0x14FFFFFF);
            bg.setStroke(dp(2), has ? GOLD : 0x1FD6AF6B);
        };
        c.setOnFocusChangeListener(paint);
        paint.onFocusChange(c, false);
        c.setOnClickListener(click);
        return c;
    }

    private View pill(String label, final int accent, View.OnClickListener click) {
        final TextView p = text(label, 14, "Medium", DIM);
        p.setGravity(Gravity.CENTER);
        p.setFocusable(true);
        p.setClickable(true);
        p.setId(View.generateViewId());
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        p.setBackground(bg);
        View.OnFocusChangeListener paint = (v, has) -> {
            bg.setColor(has ? (accent & 0x00FFFFFF) | 0x33000000 : 0x0DFFFFFF);
            bg.setStroke(dp(2), has ? accent : 0x14FFFFFF);
            p.setTextColor(has ? TEXT : DIM);
        };
        p.setOnFocusChangeListener(paint);
        paint.onFocusChange(p, false);
        p.setOnClickListener(click);
        return p;
    }

    private TextView text(String s, float sp, String weight, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        try { t.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/Tajawal-" + weight + ".ttf")); }
        catch (RuntimeException ignored) { }
        return t;
    }

    private LinearLayout.LayoutParams right(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = bottom;
        return p;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        return p;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static String ar(int n) {
        return Digits.of(String.valueOf(n));
    }
}
