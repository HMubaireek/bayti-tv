package sa.bayti.home.tilawah;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import sa.bayti.home.R;

/**
 * The 21 adhan recordings: OK picks one and plays a few seconds of it. When the person has put a recording of their
 * own on this TV (optimize/music.sh adhan add), «أذان من ملفي» is the first row.
 */
public class AdhanActivity extends Activity {

    private Prefs prefs;
    private ListView list;
    private Voices adapter;
    private View lastSelected;
    /** 1 when the list starts with the person's own recording, else 0 */
    private int own;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        sa.bayti.home.Digits.init(this);   // the screensaver's «الأرقام» setting
        setContentView(R.layout.activity_adhan);
        prefs = new Prefs(this);
        own = OwnAdhan.exists(this) ? 1 : 0;

        Ui.text((TextView) findViewById(R.id.title), "Tajawal-Bold.ttf", 40, R.color.sand);
        Ui.text((TextView) findViewById(R.id.hint), "Tajawal-Regular.ttf", 14, R.color.dim);

        list = findViewById(R.id.list);
        adapter = new Voices();
        list.setAdapter(adapter);
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                highlight(view);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            int voice = voiceAt(position);
            if (voice == Prefs.OWN) prefs.setOwnChosen(true);
            else { prefs.setVoice(voice); prefs.setOwnChosen(false); }
            MusicService.adhan(this, voice, Adhan.name(voice), 12);
            finish();
        });

        int selected = prefs.playVoice(this) == Prefs.OWN ? 0 : prefs.voice() - 1 + own;
        list.setSelection(selected);
        list.post(() -> {
            list.requestFocus();
            highlight(list.getSelectedView());
        });
    }

    /** the voice a row stands for: 0 (the person's own), or 1..21 */
    private int voiceAt(int position) { return own == 1 && position == 0 ? Prefs.OWN : position - own + 1; }

    private void highlight(View view) {
        if (lastSelected != null && lastSelected != view) resetRow(lastSelected);
        if (view == null) { lastSelected = null; return; }
        view.setBackgroundResource(R.drawable.row_selected);
        View cursor = view.findViewById(R.id.cursor);
        if (cursor != null) cursor.setVisibility(View.VISIBLE);
        lastSelected = view;
    }

    private void resetRow(View view) {
        view.setBackgroundResource(R.drawable.row_bg);
        View cursor = view.findViewById(R.id.cursor);
        if (cursor != null) cursor.setVisibility(View.INVISIBLE);
    }

    private final class Voices extends BaseAdapter {
        @Override public int getCount() { return Adhan.VOICES + own; }
        @Override public Object getItem(int position) { return voiceAt(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
            resetRow(view);
            int voice = voiceAt(position);
            TextView name = view.findViewById(R.id.name);
            TextView detail = view.findViewById(R.id.detail);
            name.setText(Adhan.name(voice));
            name.setTypeface(Ui.font(AdhanActivity.this, "Tajawal-Medium.ttf"));
            int playing = prefs.playVoice(AdhanActivity.this);
            boolean chosen = voice == playing;
            detail.setText(chosen
                    ? getString(R.string.adhan_chosen, Adhan.place(voice))
                    : Adhan.place(voice));
            detail.setTypeface(Ui.font(AdhanActivity.this, "Tajawal-Light.ttf"));
            detail.setTextColor(getResources().getColor(chosen ? R.color.gold : R.color.dim));
            return view;
        }
    }
}
