package sa.bayti.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import sa.bayti.home.tilawah.NowPlaying;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Apple TV style home screen: clock top-left, pills top-right, the focused dock app's name over an
 * ambient glow, a dock of pinned apps, and every other app in a grid that scrolls up on Down.
 * Plain framework views only (no AndroidX). Network only for hosted wallpapers and the optional card (HomeCard).
 *
 * Adapted from github.com/mihirgupta0900/tv-home (MIT, see android/launcher/LICENSE-tv-home):
 * Arabic text and digits, Hijri date, Tajawal fonts, the screensaver's gold palette and Saudi photos,
 * clock on the right, and a pill that opens the screensaver's settings.
 */
public class MainActivity extends Activity {

    // Dock, hidden apps, Live TV and grid width come from res/values/config.xml.
    private String[] dockApps;
    private String liveTvPkg, inputPickerPkg;
    private int cols;

    static final class App {
        String pkg, label, category;   // category: the one it was found with, LEANBACK_LAUNCHER or LAUNCHER
        ComponentName component;
        Drawable art;
        boolean isBanner;
        int color;
    }

    private final Map<String, Integer> colorCache = new HashMap<>();
    // Banners and icons come from the apps at their own size (often 640x360 or more); the tiles are ~1/5 of the
    // screen wide, so they are kept at that size, and the cache holds a few MB at most (sized in KB).
    private final LruCache<String, Drawable> artCache = new LruCache<String, Drawable>(6 * 1024) {
        @Override protected int sizeOf(String k, Drawable d) {
            return d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap().getByteCount() / 1024 : 256;
        }
    };
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> gridCards = new ArrayList<>();
    private final List<View> pills = new ArrayList<>();
    private final List<View> topItems = new ArrayList<>();   // the top bar from the right: the tabs, «يُتلى الآن», the three buttons

    private HomeScrollView scroll;
    private LinearLayout content, dockRow;
    private FrameLayout hero;
    private LinearLayout grid;
    private View glow;
    private ImageView wallBack, wallFront;
    private View gridShade;
    private String[] wallpapers = new String[0];
    private int wallIndex = -1;
    private long wallChangedAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotateWallpaper = this::nextWallpaper;
    static final long WALLPAPER_INTERVAL_MS = 10 * 60 * 1000;   // until the screensaver app says how often (its «تبديل الصورة كل»)
    private TextView greeting, weatherLine, clock, ampm, date, zoneLine, appsLabel;
    private View topBar;   // the tabs and the three buttons, over everything (fades while the app grid is browsed)
    private HomeTabs tabs;
    private android.app.Dialog controlCenter;
    // «العرض المتحرك»: its focus stop over the photo, its dots, its photos and the one shown; the wallpaper to come back to
    private View heroFocus;
    private LinearLayout dots;
    private Carousel carousel;
    private int carouselIdx;
    private boolean carouselShown;
    private Bitmap lastWall;
    private Pill wifiPill;   // the network's name shows while it has the focus
    private Greeting greetingLine;
    private int glowColor = 0xFF3A3A3A;
    private ValueAnimator glowAnim;
    private int lastFocusedId = View.NO_ID;
    private boolean firstShow = true;
    private int packageSeq;
    private PopupWindow inputsPopup;
    private HomeCard homeCard;
    private NowCard nowCard;   // «يُتلى الآن»: at the right end of the pills row while a surah or a station is on
    private Pill sleepPill;   // «مؤقت النوم», with the minutes left while it runs (shown while it has the focus)
    private TextView adhanBar;   // «الأذان بعد ٨ دقائق», over the top edge
    private View clockBlock, bottomBlock;   // the greeting, clock and date; the app title and the dock under it

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            updateClock();
            updateWifi();
            updateNowPlaying();   // the minutes left of its sleep timer
        }
    };
    // «تلاوة» started, paused, changed or stopped a surah or a station (MusicService says so; nothing polls): the «يُتلى الآن» card follows
    private final BroadcastReceiver nowPlayingChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateNowPlaying(); }
    };
    // the sleep timer started, ended or was cancelled (here, or from the computer): the pill follows at once
    private final BroadcastReceiver sleepChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateSleepPill(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildApps(); }
    };
    // favourites changed in «ترتيب المفضلة» or from ADB while the home screen is up
    private final BroadcastReceiver dockChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { dockApps = loadDock(); buildApps(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Digits.init(this);   // the screensaver's «الأرقام» setting, for every number this screen writes
        dockApps = loadDock();
        liveTvPkg = getString(R.string.live_tv_package);
        inputPickerPkg = getString(R.string.input_picker_package);
        cols = getResources().getInteger(R.integer.columns);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(buildUi());
        buildApps();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        registerReceiver(dockChanged, new IntentFilter(Dock.CHANGED));
        registerReceiver(sleepChanged, new IntentFilter(SleepTimer.CHANGED));
        registerReceiver(nowPlayingChanged, new IntentFilter(NowPlaying.CHANGED));
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(tick, tf);
        updateClock();
        updateWifi();
        updateNowPlaying();
        homeCard.refresh();
        tabs.refresh();   // the tabs and the TMDB key may have changed in the screensaver's settings
        tabs.resume();
        loadCarousel();
        wireFocus();   // the «المهام» card may have come or gone with the settings
        View last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
        if (last == null && !dockCards.isEmpty()) last = dockCards.get(0);
        if (last != null) last.requestFocus();
        // Catch installs/removals that happened while another app was in front.
        if (getPackageManager().getChangedPackages(packageSeq) != null) buildApps();
        if (firstShow) { firstShow = false; playEntrance(); }
        // New photo on return if the current one has been up for the full interval.
        if (wallIndex < 0 || SystemClock.elapsedRealtime() - wallChangedAt >= wallpaperInterval()) nextWallpaper();
        else {
            scheduleWallpaper(wallpaperInterval() - (SystemClock.elapsedRealtime() - wallChangedAt));
            checkOwnChanged();   // the owner added or removed photos while another app was in front
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rotateWallpaper);
        handler.removeCallbacks(carouselNext);
        unregisterReceiver(tick);
        if (inputsPopup != null) inputsPopup.dismiss();
        if (controlCenter != null) controlCenter.dismiss();
        homeCard.pause();
        tabs.pause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        unregisterReceiver(dockChanged);
        unregisterReceiver(sleepChanged);
        unregisterReceiver(nowPlayingChanged);
        homeCard.destroy();
        tabs.destroy();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: «الرئيسية», and the first dock app.
        if (inputsPopup != null) inputsPopup.dismiss();
        if (controlCenter != null) controlCenter.dismiss();
        tabs.select(HomeTabs.HOME_TAB);
        if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
        scroll.smoothScrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        // another tab: its page first (it closes its details), then the tab row, then «الرئيسية»
        if (tabs.back()) return;
        // Home has nowhere to go back to; Back from the grid returns to the dock.
        if (scroll.getScrollY() > 0 && !dockCards.isEmpty()) dockCards.get(0).requestFocus();
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        // The home is built in code, so it inherits no direction from the window: set it here and
        // the pills, the favourites and the app rows all read from right to left.
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Wallpaper: two stacked layers so a new photo can crossfade in over the old one (the first two children: showWallpaper)
        wallBack = wallpaperLayer();
        wallFront = wallpaperLayer();
        root.addView(wallBack, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallFront, new FrameLayout.LayoutParams(-1, -1));
        // Scrim: the top bar's band, a clear middle for the photo, and a deep fade at the bottom right where the clock and the
        // favourites sit (the way a streaming app lays its title over its billboard)
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x14000000, 0x00000000, 0x66000000, 0xE6000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        View side = new View(this);   // and from the right, behind the clock block
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0x8C000000, 0x33000000, 0x00000000}));
        root.addView(side, new FrameLayout.LayoutParams(-1, -1));
        gridShade = new View(this);
        gridShade.setBackgroundColor(0xB3000000);
        gridShade.setAlpha(0f);
        root.addView(gridShade, new FrameLayout.LayoutParams(-1, -1));
        try {
            wallpapers = getAssets().list("wallpapers");
        } catch (java.io.IOException ignored) { }

        glow = new View(this);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        setGlow(glowColor);

        scroll = new HomeScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        // Hero: exactly one screen tall, under the top bar; the clock block and the favourites at its bottom
        hero = new FrameLayout(this);
        hero.setClipChildren(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        content.addView(hero, new LinearLayout.LayoutParams(-1, screenH));

        // «العرض المتحرك»: a focus stop over the photo between the top bar and the clock; Left and Right page through the carousel
        heroFocus = new View(this);
        heroFocus.setId(View.generateViewId());
        heroFocus.setFocusable(false);   // focusable only while the carousel has photos (loadCarousel)
        heroFocus.setOnFocusChangeListener((v, has) -> {
            if (has) { lastFocusedId = v.getId(); scroll.smoothScrollTo(0, 0); fadeGlow(1f); }
        });
        heroFocus.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            // the page reads from the right: Left is the next photo, Right the one before
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { stepCarousel(1); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { stepCarousel(-1); return true; }
            return false;
        });
        hero.addView(heroFocus, lp(-1, -1, Gravity.TOP, 0, dp(90), 0, dp(250)));

        // optional cards under the top bar, the same size as beside the screensaver's clock
        homeCard = new HomeCard(this, hero, lp(dp(461), dp(250), Gravity.LEFT | Gravity.TOP, dp(48), dp(84), 0, 0), this::homeCardBox, new HomeCard.Live() {
            @Override public void focus(View target, boolean has) {   // «المهام» on the card takes the focus like a pill
                if (has) { lastFocusedId = target.getId(); scroll.smoothScrollTo(0, 0); fadeGlow(1f); }
            }
            @Override public void open() {   // OK: the list, with the focus on «🎤 تكلّم» so one more OK starts the voice adding
                launch(new Intent(MainActivity.this, TasksActivity.class).putExtra(TasksActivity.EXTRA_MIC, true));
            }
        });
        hero.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> homeCard.fit());

        // the bottom of the hero, from the right: the clock block (greeting, weather, clock, date) where a billboard has its title,
        // the carousel's dots under it, and the favourites
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        bottom.setGravity(Gravity.RIGHT);
        hero.addView(bottom, lp(-1, -2, Gravity.BOTTOM, dp(48), 0, dp(48), dp(30)));
        bottomBlock = bottom;
        bottom.addView(buildInfo(), new LinearLayout.LayoutParams(-2, -2));

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        dots.setVisibility(View.GONE);
        bottom.addView(dots, llp(-2, dp(10), 0, dp(10), dp(4), 0));

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(dp(16), dp(16), dp(16), dp(16));   // the same all round: the names are inside the cards
        GradientDrawable band = new GradientDrawable();
        band.setCornerRadius(dp(22));
        band.setColor(0x1AFFFFFF);
        band.setStroke(dp(1), 0x14FFFFFF);
        dockRow.setBackground(band);
        bottom.addView(dockRow, llp(-1, -2, 0, dp(18), 0, 0));   // the whole width; its cards share it

        // the app drawer's header: its name and how many, aligned with the grid's right edge
        appsLabel = text(22, "medium", 0xFFF3EBD9);
        appsLabel.setText("كل التطبيقات");
        appsLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        appsLabel.setShadowLayer(dp(8), 0, 0, 0x99000000);
        content.addView(appsLabel, llp(-1, -2, dp(68), dp(26), dp(68), dp(24)));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.addView(grid, llp(-1, -2, dp(68), 0, dp(68), dp(160)));

        // «أفلام»، «مسلسلات»، «يومي»: their page over the home screen, under the top bar (HomeTabs)
        FrameLayout webBox = new FrameLayout(this);
        webBox.setVisibility(View.GONE);
        root.addView(webBox, new FrameLayout.LayoutParams(-1, -1));
        tabs = new HomeTabs(this, new HomeTabs.Host() {
            @Override public void showHome(boolean home) {
                scroll.animate().alpha(home ? 1f : 0f).setDuration(240).start();
                scroll.setDescendantFocusability(home ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                gridShade.animate().alpha(home ? 0f : 1f).setDuration(240).start();
                glow.animate().alpha(home ? 1f : 0f).setDuration(240).start();
                if (home) scroll.scrollTo(0, 0);
                updateCarousel();
            }
            @Override public void downFromTabs() { View d = homeDown(); if (d != null) d.requestFocus(); }
            @Override public String fragment() { return ConfigSnapshot.read(MainActivity.this).fragment; }
            @Override public String digits() { return Digits.latin() ? "latin" : "hindi"; }
        }, font("medium"), font("regular"), webBox);

        // the top bar over everything: the tabs in the middle, the three buttons at the left
        topBar = buildHeader();
        root.addView(topBar, lp(-1, -2, Gravity.TOP, dp(48), dp(30), dp(48), 0));

        // the slim bar before the adhan, over the top edge
        adhanBar = text(12, "sans-serif-medium", 0xFFFFE9B8);
        adhanBar.setGravity(Gravity.CENTER);
        adhanBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00DCB86F, 0x57DCB86F, 0x57DCB86F, 0x00DCB86F}));
        adhanBar.setVisibility(View.GONE);
        root.addView(adhanBar, new FrameLayout.LayoutParams(-1, dp(26), Gravity.TOP));
        return root;
    }

    /** The clock block: greeting, the weather and the next sunrise or sunset, the clock and the date, right-aligned. */
    private View buildInfo() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.RIGHT);
        // Gravity.RIGHT, not END: for Arabic text END is the left. The greeting itself sits at the top bar's right (buildHeader)
        greeting = text(20, "medium", GOLD);
        greeting.setSingleLine(true);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.TOP);
        clock = text(68, "light", 0xFFFFFDF6);
        clock.setIncludeFontPadding(false);
        ampm = text(18, "medium", GOLD);
        // the row reads from the right: the time first, then the ص or the م on its left
        clockRow.addView(clock);
        clockRow.addView(ampm, llp(-2, -2, 0, dp(8), dp(8), 0));
        box.addView(clockRow, llp(-2, -2, 0, dp(2), 0, 0));

        date = text(16, "regular", 0xFFE6DCC4);
        date.setSingleLine(true);
        box.addView(date, llp(-2, -2, 0, dp(2), dp(4), 0));
        // under it: the weather of the city and the next sunrise or sunset (shown only when the greeting is on and there is an answer)
        weatherLine = text(13, "regular", 0xFFC6BBA4);
        weatherLine.setMaxLines(2);
        weatherLine.setVisibility(View.GONE);
        box.addView(weatherLine, llp(-2, -2, 0, dp(4), dp(4), 0));
        greetingLine = new Greeting(this, greeting, weatherLine);
        // «بتوقيت دبي», only when the city's clock is not the TV's (as the screensaver's line under its clock)
        zoneLine = text(12, "regular", GOLD);
        zoneLine.setSingleLine(true);
        zoneLine.setVisibility(View.GONE);
        box.addView(zoneLine, llp(-2, -2, 0, dp(2), dp(4), 0));
        for (TextView t : new TextView[]{greeting, weatherLine, clock, ampm, date, zoneLine}) t.setShadowLayer(dp(10), 0, dp(1), 0x99000000);
        for (TextView t : new TextView[]{greeting, weatherLine, date, zoneLine}) t.setGravity(Gravity.RIGHT);
        clockBlock = box;
        return box;
    }

    /**
     * The WebView's box for that many cards, in pixels: under the top bar, at the left, ending above the clock block and the favourites
     * (the bottom block). Before the screen is laid out the numbers come from the screen's size alone.
     */
    private int[] homeCardBox(int cards) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int bottomTop = bottomBlock != null && bottomBlock.getHeight() > 0 ? bottomBlock.getTop() : 0;
        return CardBox.box(cards, dm.widthPixels, dm.heightPixels, dm.density, 0, bottomTop, bottomTop, 0);
    }

    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setClipChildren(false);

        // at the right: the greeting of the part of the day («صباح الخير»…, the owner's own phrases from «التحية»)
        greeting.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        greeting.setMaxWidth(getResources().getDisplayMetrics().widthPixels / 2 - dp(48) - dp(200));   // clear of the centred tabs
        greeting.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(greeting, new FrameLayout.LayoutParams(-2, dp(Pill.HEIGHT), Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        // in the middle: the tabs (none shown when only «الرئيسية» is left)
        header.addView(tabs.row(), new FrameLayout.LayoutParams(-2, dp(Pill.HEIGHT), Gravity.CENTER));

        // at the left: «المصادر», «الإعدادات» and «المزيد» (everything else, in the control center); «يُتلى الآن» before them while it plays
        LinearLayout left = new LinearLayout(this);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setClipChildren(false);
        Pill inputs = pill("المصادر", R.drawable.ic_input);
        inputs.setOnClickListener(v -> showInputs(v));
        Pill settings = pill("الإعدادات", R.drawable.ic_settings);
        settings.setOnClickListener(v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        Pill more = pill("المزيد", R.drawable.ic_more);
        more.setOnClickListener(v -> showControlCenter(v));
        for (Pill p : new Pill[]{inputs, settings, more}) {
            left.addView(p, llp(-2, dp(Pill.HEIGHT), dp(10), 0, 0, 0));
            pills.add(p);
        }
        // «يُتلى الآن»: the first child of the row, so it sits at its right end and the pills keep their places when it comes
        nowCard = new NowCard(this, font("medium"), font("regular"), getDrawable(R.drawable.ic_play), getDrawable(R.drawable.ic_pause));
        nowCard.setId(View.generateViewId());
        nowCard.setVisibility(View.GONE);
        nowCard.setOnClickListener(v -> NowPlaying.toggle(this));   // OK: pause or play
        nowCard.setOnLongClickListener(v -> { startActivity(NowPlaying.player(this)); return true; });   // a long OK: the player
        nowCard.setOnFocusChangeListener((v, has) -> {
            if (has) { lastFocusedId = v.getId(); scroll.smoothScrollTo(0, 0); fadeGlow(1f); }
        });
        left.addView(nowCard, 0, llp(-2, dp(Pill.HEIGHT), dp(10), 0, 0, 0));
        header.addView(left, new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        return header;
    }

    /** «المزيد»: the control center, a tile for each of Wi-Fi, the screensaver, «المهام», «تلاوة وأذان», the sleep timer, the favourites */
    private void showControlCenter(View anchor) {
        List<ControlCenter.Tile> tiles = new ArrayList<>();
        tiles.add(new ControlCenter.Tile(R.drawable.ic_wifi, "الشبكة", () -> wifiState(),
                v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        if (isInstalled(SAVER_PKG)) {
            tiles.add(new ControlCenter.Tile(R.drawable.ic_screensaver, "شاشة التوقف", () -> "إعدادات بيتي", v -> openPackage(SAVER_PKG)));
            tiles.add(new ControlCenter.Tile(R.drawable.ic_mic, "المهام", () -> "بالصوت أو بالأزرار",
                    v -> launch(new Intent(this, TasksActivity.class))));
        }
        if (getResources().getBoolean(R.bool.show_listening))
            tiles.add(new ControlCenter.Tile(R.drawable.ic_listen, "تلاوة وأذان", () -> "القرآن والإذاعة والأذان",
                    v -> launch(new Intent(this, sa.bayti.home.tilawah.MainActivity.class))));
        tiles.add(new ControlCenter.Tile(R.drawable.ic_sleep, "مؤقت النوم", () -> {
            int m = SleepTimer.minutesLeft(this);
            return m > 0 ? Digits.of(PillText.sleep(m)) : "متوقف";
        }, v -> showSleep(anchor)));
        tiles.add(new ControlCenter.Tile(R.drawable.ic_home, "ترتيب المفضلة", () -> "بالأسهم", v -> launch(new Intent(this, DockActivity.class))));
        controlCenter = ControlCenter.show(this, tiles, font("medium"), font("regular"), () -> { controlCenter = null; if (!isFinishing()) anchor.requestFocus(); });
    }

    /** «متصل: البيت» / «سلكي» / «غير متصل», for the control center's network tile */
    private String wifiState() {
        boolean wired = false, wifi = false;
        String ssid = null;
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            wired = nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
            wifi = nc != null && !wired && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                ssid = wm == null || wm.getConnectionInfo() == null ? null : wm.getConnectionInfo().getSSID();
            }
        } catch (SecurityException ignored) { }
        return PillText.network(wired, wifi, ssid);
    }

    /** Shows or hides the «يُتلى الآن» card by what «تلاوة» is doing: a surah or a station (playing or paused), else nothing. */
    private void updateNowPlaying() {
        if (nowCard == null) return;
        NowPlaying.State s = NowPlaying.read(this);
        if (!s.show) {
            if (nowCard.getVisibility() == View.VISIBLE && nowCard.hasFocus() && !pills.isEmpty()) pills.get(0).requestFocus();   // it went with the playback
            nowCard.setVisibility(View.GONE);
            return;
        }
        nowCard.bind(s);
        nowCard.setVisibility(View.VISIBLE);
    }

    /** Pill button: its icon only; the one with the focus widens to show its name (see Pill). */
    private Pill pill(String label, int icon) {
        Pill p = new Pill(this, label, icon, font("sans-serif-medium"));
        p.setId(View.generateViewId());
        p.setOnFocusChangeListener((v, has) -> {
            if (has) {
                lastFocusedId = v.getId(); scroll.smoothScrollTo(0, 0); fadeGlow(1f);
                View card = homeCard == null ? null : homeCard.live();
                if (card != null) card.setNextFocusUpId(v.getId());   // up from the «المهام» card goes back to the pill it came from
            }
        });
        return p;
    }

    // ---------------------------------------------------------------- apps

    private void buildApps() {
        PackageManager pm = getPackageManager();
        android.content.pm.ChangedPackages changed = pm.getChangedPackages(packageSeq);
        if (changed != null) { packageSeq = changed.getSequenceNumber(); artCache.evictAll(); }   // an app may have a new icon
        Map<String, App> byPkg = new HashMap<>();
        Map<String, String> given = AppNames.given(this);   // names the owner's agent set (dock.sh name)
        for (ResolveInfo ri : Dock.catalogue(this)) {
            ActivityInfo ai = ri.activityInfo;
            App a = new App();
            a.pkg = ai.packageName;
            a.label = AppNames.label(this, ri, given);
            a.component = new ComponentName(ai.packageName, ai.name);
            a.category = Dock.isTv(ri) ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER;
            Drawable banner = artCache.get("b:" + a.pkg);
            if (banner == null) {
                banner = ai.loadBanner(pm);
                if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
                if (banner != null) artCache.put("b:" + a.pkg, banner = fitted(banner, true));
            }
            a.isBanner = banner != null;
            if (banner != null) a.art = banner;
            else {
                a.art = artCache.get("i:" + a.pkg);
                if (a.art == null) artCache.put("i:" + a.pkg, a.art = fitted(ri.loadIcon(pm), false));
            }
            a.color = sampleColor(a.pkg, a.art);
            byPkg.put(a.pkg, a);
        }

        List<App> dock = new ArrayList<>();
        for (String p : dockApps) if (byPkg.containsKey(p)) dock.add(byPkg.remove(p));
        cardByPkg.clear();
        List<App> rest = new ArrayList<>(byPkg.values());
        Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));
        // Fewer than MIN_DOCK of the chosen apps installed: top the dock up from the rest.
        while (dock.size() < MIN_DOCK && !rest.isEmpty()) dock.add(rest.remove(0));

        dockRow.removeAllViews();
        grid.removeAllViews();
        dockCards.clear();
        gridCards.clear();

        // the favourites share the band's whole width, 16:9 cards side by side
        for (int i = 0; i < dock.size(); i++) {
            View tile = tile(dock.get(i), true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) p.setMarginStart(dp(14));
            dockRow.addView(tile, p);
        }

        LinearLayout row = null;
        for (int i = 0; i < rest.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                grid.addView(row, llp(-1, -2, 0, 0, 0, dp(30)));
            }
            View tile = tile(rest.get(i), false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i % cols == 0 ? 0 : dp(10), 0, i % cols == cols - 1 ? 0 : dp(10), 0);
            row.addView(tile, p);
        }
        // Pad the last row so tiles keep their width.
        if (row != null) for (int i = rest.size() % cols; i != 0 && i < cols; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
            p.setMargins(dp(10), 0, i == cols - 1 ? 0 : dp(10), 0);
            row.addView(new View(this), p);
        }
        appsLabel.setVisibility(rest.isEmpty() ? View.GONE : View.VISIBLE);
        android.text.SpannableString head = new android.text.SpannableString("كل التطبيقات  " + arabicDigits(String.valueOf(rest.size())));
        head.setSpan(new android.text.style.ForegroundColorSpan(GOLD), "كل التطبيقات  ".length(), head.length(), 0);
        head.setSpan(new android.text.style.RelativeSizeSpan(0.7f), "كل التطبيقات  ".length(), head.length(), 0);
        appsLabel.setText(head);
        wireFocus();

        if (!dock.isEmpty()) setGlow(dock.get(0).color);
        View last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
        if (last != null) last.requestFocus();
        else if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
    }

    /** A 16:9 card with the app's banner; the name shows under it while focused. */
    private View tile(App app, boolean inDock) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        FrameLayout card = new AspectFrame(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(app);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(inDock ? 12 : 14));
            }
        });
        // a banner fills its card; an icon sits on glass with a soft light of its own colour from the top (not a block of colour)
        GradientDrawable bg;
        if (app.isBanner || inDock) {
            bg = new GradientDrawable();
            bg.setColor(app.isBanner ? 0xFF1C1C1E : darken(app.color, 0.45f));
        } else {
            bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{withAlpha(darken(app.color, 0.8f), 0.55f), 0xD9161A18, 0xF0101211});
            bg.setStroke(dp(1), 0x26FFFFFF);
        }
        bg.setCornerRadius(dp(inDock ? 12 : 14));
        card.setBackground(bg);

        ImageView img = new ImageView(this);
        img.setImageDrawable(app.art);
        if (app.isBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            if (inDock) img.setPadding(dp(22), dp(14), dp(22), dp(14));
            else img.setPadding(dp(40), dp(22), dp(40), dp(22));   // a smaller icon, centred, room around it
        }

        // Focus ring and sheen, drawn above the art.
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(inDock ? 12 : 14));
        ring.setStroke(dp(2), 0xE6FFFFFF);
        ring.setOrientation(GradientDrawable.Orientation.TL_BR);
        ring.setColors(new int[]{0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        View shine = new View(this);
        shine.setBackground(ring);
        shine.setAlpha(0f);
        card.addView(shine, new FrameLayout.LayoutParams(-1, -1));

        box.addView(card, new LinearLayout.LayoutParams(-1, -2));

        // the name: on a favourite inside the card's foot, on a soft shade, only while it has the focus (the band's padding stays
        // even); in the app grid under the card, always (brighter with the focus)
        TextView label = text(inDock ? 12 : 13, "sans-serif-medium", Color.WHITE);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setShadowLayer(dp(6), 0, 0, 0xB3000000);
        label.setAlpha(inDock ? 0f : .7f);
        if (inDock) {
            label.setPadding(dp(8), dp(14), dp(8), dp(6));
            label.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x00000000}));
            card.addView(label, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        } else {
            box.addView(label, llp(-1, -2, 0, dp(12), 0, 0));
        }
        if (Build.VERSION.SDK_INT >= 28) {   // the focused card's shadow takes the app's own colour
            card.setOutlineSpotShadowColor(app.color);
            card.setOutlineAmbientShadowColor(app.color);
        }

        card.setOnClickListener(v -> launch(new Intent(Intent.ACTION_MAIN)
                .addCategory(app.category)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));
        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? (inDock ? 1.1f : 1.08f) : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? dp(18) : 0)
                    .setInterpolator(has ? new OvershootInterpolator(1.8f) : new DecelerateInterpolator())
                    .setDuration(has ? 320 : 200).start();
            shine.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            label.animate().alpha(has ? 1f : (inDock ? 0f : .7f)).translationY(has || inDock ? 0 : 0).setDuration(200).start();
            if (!has) return;
            lastFocusedId = v.getId();
            if (inDock) {
                animateGlow(app.color);
                fadeGlow(1f);
                scroll.smoothScrollTo(0, 0);
            } else {
                fadeGlow(0.35f);
                scrollToRow(box);
            }
        });

        // Long-press OK: add to / remove from / reorder the dock, right from the remote.
        card.setOnLongClickListener(v -> { showAppMenu(v, app.pkg, inDock); return true; });
        cardByPkg.put(app.pkg, card);

        (inDock ? dockCards : gridCards).add(card);
        return box;
    }

    /**
     * Explicit D-pad targets: one row per press, ends of rows don't wrap. The top bar is one row that reads from the right: the tabs,
     * «يُتلى الآن», then the three buttons. Down from it: the carousel's stop (when it has photos), the «المهام» card, the favourites,
     * the app grid; on another tab, its page.
     */
    private void wireFocus() {
        int d = dockCards.size(), g = gridCards.size();
        View card = homeCard == null ? null : homeCard.live();
        int dockId = d > 0 ? dockCards.get(0).getId() : View.NO_ID;
        boolean heroOn = heroFocus != null && heroFocus.isFocusable();

        List<View> top = topItems;
        top.clear();
        if (tabs.any()) for (TextView t : tabs.views()) if (t.isFocusable()) top.add(t);
        if (nowCard != null && nowCard.getVisibility() == View.VISIBLE) top.add(nowCard);
        top.addAll(pills);
        for (int i = 0; i < top.size(); i++) {
            View p = top.get(i);
            // the row's start is at the right: Right goes back toward it, Left on
            p.setNextFocusRightId(i == 0 ? p.getId() : top.get(i - 1).getId());
            p.setNextFocusLeftId(i == top.size() - 1 ? p.getId() : top.get(i + 1).getId());
            p.setNextFocusUpId(p.getId());
        }
        View down = homeDown();
        int downId = down != null ? down.getId() : View.NO_ID;
        for (View p : top) {
            if (tabs.views().contains(p)) {   // a tab: down into its page, or into the home screen
                p.setOnKeyListener((v, code, e) -> {
                    if (code != KeyEvent.KEYCODE_DPAD_DOWN || e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (!HomeTabs.HOME_TAB.equals(tabs.current())) { tabs.focusPage(); return true; }
                    View h = homeDown();
                    if (h != null) h.requestFocus();
                    return true;
                });
            } else p.setNextFocusDownId(downId != View.NO_ID ? downId : p.getId());
        }
        View upTarget = tabs.any() && tabs.currentView() != null ? tabs.currentView() : pills.get(0);
        int upId = upTarget.getId();
        if (heroOn) {
            heroFocus.setNextFocusUpId(upId);
            heroFocus.setNextFocusDownId(card != null ? card.getId() : dockId != View.NO_ID ? dockId : heroFocus.getId());
        }
        if (card != null) {
            card.setNextFocusLeftId(card.getId());   // nothing beside it to move to: stay on it
            card.setNextFocusRightId(card.getId());
            card.setNextFocusUpId(heroOn ? heroFocus.getId() : upId);
            card.setNextFocusDownId(dockId != View.NO_ID ? dockId : card.getId());
        }
        int dockUp = card != null ? card.getId() : heroOn ? heroFocus.getId() : upId;
        for (int i = 0; i < d; i++) {
            View c = dockCards.get(i);
            c.setNextFocusUpId(dockUp);
            c.setNextFocusRightId(i == 0 ? c.getId() : dockCards.get(i - 1).getId());
            c.setNextFocusLeftId(i == d - 1 ? c.getId() : dockCards.get(i + 1).getId());
            c.setNextFocusDownId(g > 0 ? gridCards.get(Math.min(i, g - 1)).getId() : c.getId());
        }
        int lastRow = (g - 1) / cols;
        for (int j = 0; j < g; j++) {
            View c = gridCards.get(j);
            int r = j / cols, col = j % cols;
            c.setNextFocusUpId(r == 0
                    ? (d > 0 ? dockCards.get(Math.min(col, d - 1)).getId() : upId)
                    : gridCards.get(j - cols).getId());
            c.setNextFocusDownId(j + cols < g ? gridCards.get(j + cols).getId()
                    : r < lastRow ? gridCards.get(g - 1).getId() : c.getId());
            c.setNextFocusRightId(col == 0 ? c.getId() : gridCards.get(j - 1).getId());
            c.setNextFocusLeftId(col == cols - 1 || j == g - 1 ? c.getId() : gridCards.get(j + 1).getId());
        }
    }

    /** Where Down from the top bar lands on «الرئيسية»: the carousel's stop, the «المهام» card, or the first favourite. */
    private View homeDown() {
        if (heroFocus != null && heroFocus.isFocusable()) return heroFocus;
        View card = homeCard == null ? null : homeCard.live();
        if (card != null) return card;
        return dockCards.isEmpty() ? null : dockCards.get(0);
    }

    private void scrollToRow(View tileBox) {
        View row = (View) tileBox.getParent();
        int y = grid.getTop() + row.getTop();
        int screenH = getResources().getDisplayMetrics().heightPixels;
        // First grid row: slide the dock band up to the top edge; later rows centre on screen.
        View band = (View) dockRow.getParent();
        int target = grid.indexOfChild(row) == 0
                ? hero.getTop() + band.getTop() + dockRow.getTop() - dp(22)
                : y - (int) (screenH * 0.42f);
        int max = Math.max(0, content.getHeight() - scroll.getHeight());
        scroll.smoothScrollTo(0, Math.max(0, Math.min(target, max)));
    }

    // ---------------------------------------------------------------- inputs

    // ---------------------------------------------------------------- dock editing

    private final Map<String, View> cardByPkg = new HashMap<>();
    static final int MIN_DOCK = Dock.MIN, MAX_DOCK = Dock.MAX;

    /** The user's dock if they edited it, else res/values/config.xml. */
    /** The launcher's settings file, shared with DockReceiver. */
    private android.content.SharedPreferences prefs() { return Dock.prefs(this); }

    private String[] loadDock() {
        String saved = prefs().getString("dock", null);
        return saved != null ? (saved.isEmpty() ? new String[0] : saved.split(","))
                             : getResources().getStringArray(R.array.dock_apps);
    }

    /** Only installed apps are kept, so the dock never holds a removed app's slot. */
    private void saveDock(List<String> dock, String focusPkg) {
        List<String> installed = new ArrayList<>();
        for (String p : dock) if (isInstalled(p)) installed.add(p);
        prefs().edit().putString("dock", android.text.TextUtils.join(",", installed)).apply();
        dockApps = installed.toArray(new String[0]);
        buildApps();
        View c = cardByPkg.get(focusPkg);
        if (c != null) c.post(c::requestFocus);
    }

    private void showAppMenu(View anchor, String pkg, boolean inDock) {
        List<String> dock = new ArrayList<>();
        // The dock as shown, including apps added automatically to reach MIN_DOCK.
        for (View c : dockCards) for (Map.Entry<String, View> e : cardByPkg.entrySet()) if (e.getValue() == c) dock.add(e.getKey());
        int i = dock.indexOf(pkg);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("ترتيب المفضلة…");
        actions.add(() -> launch(new Intent(this, DockActivity.class)));
        if (inDock) {
            if (dock.size() > MIN_DOCK) {
                labels.add("إزالة من المفضلة");
                actions.add(() -> { dock.remove(pkg); saveDock(dock, dock.get(Math.max(0, i - 1))); });
            } else {
                labels.add("المفضلة لا تقل عن " + arabicDigits(String.valueOf(MIN_DOCK)));
                actions.add(() -> { });
            }
        } else if (dock.size() < MAX_DOCK) {
            labels.add("إضافة إلى المفضلة");
            actions.add(() -> { dock.add(pkg); saveDock(dock, pkg); });
        } else {
            labels.add("المفضلة ممتلئة (" + MAX_DOCK + ")");
            actions.add(() -> { });
        }
        labels.add("إعادة المفضلة الافتراضية");
        actions.add(() -> {
            prefs().edit().remove("dock").apply();
            dockApps = getResources().getStringArray(R.array.dock_apps);
            buildApps();
        });
        showMenu(anchor, labels, actions);
    }

    /** Small focusable popup in the same style as the Inputs menu. */
    private void showMenu(View anchor, List<String> labels, List<Runnable> actions) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF02A2A2E);
        bg.setStroke(dp(1), 0x26FFFFFF);
        list.setBackground(bg);
        PopupWindow pw = new PopupWindow(list, dp(230), -2, true);
        List<TextView> rows = new ArrayList<>();
        for (int k = 0; k < labels.size(); k++) rows.add(inputRow(labels.get(k), actions.get(k)));
        for (TextView r : rows) {
            r.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            r.setOnClickListener(v -> { pw.dismiss(); ((Runnable) v.getTag()).run(); });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(36)));
        }
        for (int k = 0; k < rows.size(); k++) {
            TextView r = rows.get(k);
            r.setNextFocusUpId(rows.get(Math.max(0, k - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, k + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(16));
        pw.showAsDropDown(anchor, 0, -anchor.getHeight() - dp(8) * (rows.size() + 2) - dp(36) * rows.size(), Gravity.START);
        rows.get(0).requestFocus();
    }

    private void showInputs(View anchor) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF02A2A2E);
        bg.setStroke(dp(1), 0x26FFFFFF);
        list.setBackground(bg);

        PopupWindow pw = new PopupWindow(list, dp(200), -2, true);
        List<TextView> rows = new ArrayList<>();

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        if (tim != null) for (TvInputInfo t : tim.getTvInputList()) {
            if (t.isPassthroughInput() && !t.isHidden(this)) inputs.add(t);
        }
        Collections.sort(inputs, (x, y) -> inputLabel(x).compareToIgnoreCase(inputLabel(y)));
        for (TvInputInfo t : inputs) {
            rows.add(inputRow(inputLabel(t), () -> openPassthroughInput(t)));
        }
        if (isInstalled(liveTvPkg)) rows.add(inputRow("البث التلفزيوني", () -> openPackage(liveTvPkg)));
        if (inputs.isEmpty() && isInstalled(inputPickerPkg)) rows.add(inputRow("كل المصادر…", () -> openPackage(inputPickerPkg)));

        if (rows.isEmpty()) rows.add(inputRow("الإعدادات", () -> launch(new Intent(Settings.ACTION_SETTINGS))));
        for (TextView r : rows) {
            r.setOnClickListener(v -> { pw.dismiss(); ((Runnable) v.getTag()).run(); });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(36)));
        }
        for (int i = 0; i < rows.size(); i++) {
            TextView r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(16));
        pw.setOnDismissListener(() -> { inputsPopup = null; anchor.requestFocus(); });
        inputsPopup = pw;
        pw.showAsDropDown(anchor, 0, dp(8), Gravity.START);
        rows.get(0).requestFocus();
    }

    /** «مؤقت النوم»: 15, 30, 60, 90 minutes, or cancel; at the end the TV goes to sleep (SleepTimer says how). */
    private void showSleep(View anchor) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF02A2A2E);
        bg.setStroke(dp(1), 0x26FFFFFF);
        list.setBackground(bg);
        PopupWindow pw = new PopupWindow(list, dp(220), -2, true);
        List<TextView> rows = new ArrayList<>();
        for (int m : SleepTimer.MINUTES) rows.add(inputRow(arabicDigits(String.valueOf(m)) + " دقيقة", () -> SleepTimer.start(this, m)));
        if (SleepTimer.minutesLeft(this) > 0) rows.add(inputRow("إلغاء", () -> SleepTimer.start(this, 0)));
        if (!SleepService.enabled()) {   // without it the end only stops the sound and brings the screensaver: say so, and where to turn it on
            rows.add(inputRow("لإطفاء الشاشة: فعّل «بيتي: إطفاء الشاشة»", () -> launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        }
        for (TextView r : rows) {
            r.setOnClickListener(v -> { pw.dismiss(); ((Runnable) v.getTag()).run(); });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(36)));
        }
        for (int i = 0; i < rows.size(); i++) {
            TextView r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(16));
        pw.setOnDismissListener(() -> anchor.requestFocus());
        pw.showAsDropDown(anchor, 0, dp(8), Gravity.START);
        rows.get(0).requestFocus();
    }

    /** The pill says how long is left while the timer runs: «مؤقت النوم ٢٣ د». */
    private void updateSleepPill() {
        if (sleepPill == null) return;
        int left = SleepTimer.minutesLeft(this);
        sleepPill.setLabel(Digits.of(PillText.sleep(left)));
        sleepPill.setAccent(left > 0);   // gold while it runs: the minutes show when the pill has the focus
    }

    private String inputLabel(TvInputInfo t) {
        CharSequence c = t.loadCustomLabel(this);
        if (c == null || c.length() == 0) c = t.loadLabel(this);
        return String.valueOf(c);
    }

    private TextView inputRow(String label, Runnable action) {
        TextView r = text(13, "sans-serif", Color.WHITE);
        r.setText(label);
        r.setTag(action);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.TRANSPARENT);
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : Color.TRANSPARENT);
            r.setTextColor(has ? 0xFF111111 : Color.WHITE);
        });
        return r;
    }

    // ---------------------------------------------------------------- status

    // The two date formats are made once and given the city's zone when it changes: the tick runs every minute.
    private android.icu.text.SimpleDateFormat hijriFormat;
    private SimpleDateFormat gregFormat;
    private String formatZone = "";

    private void updateClock() {
        long ms = System.currentTimeMillis();
        String tvTz = java.util.TimeZone.getDefault().getID();
        // one read of the screensaver's settings (its ConfigProvider, same signing key) for the whole tick; null zone = it is missing: the TV's own
        ConfigSnapshot cfg = ConfigSnapshot.read(this);
        Digits.remember(this, cfg);   // a change of the setting reaches the next tick
        String[] zone = cfg.cityZone();
        String tz = zone == null ? tvTz : zone[1];
        Calendar now = ClockZone.at(ms, tz);   // the city's time, like the prayer times beside it
        int h = now.get(Calendar.HOUR_OF_DAY);
        greetingLine.update(h, cfg);
        int hh = h % 12 == 0 ? 12 : h % 12;   // always 12-hour
        clock.setText(arabicDigits(hh + ":" + String.format(Locale.ROOT, "%02d", now.get(Calendar.MINUTE))));
        ampm.setText(h < 12 ? "ص" : "م");
        boolean other = zone != null && ClockZone.differs(tvTz, tz, ms);
        zoneLine.setVisibility(other ? View.VISIBLE : View.GONE);
        if (other) zoneLine.setText(ClockZone.label(zone[0]));
        if (gregFormat == null) {
            gregFormat = new SimpleDateFormat("d MMMM", new Locale("ar"));
            try {
                hijriFormat = new android.icu.text.SimpleDateFormat("EEEE، d MMMM y", new android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura"));
            } catch (Exception ignored) { }
        }
        if (!tz.equals(formatZone)) {
            gregFormat.setTimeZone(java.util.TimeZone.getTimeZone(tz));
            if (hijriFormat != null) hijriFormat.setTimeZone(android.icu.util.TimeZone.getTimeZone(tz));
            formatZone = tz;
        }
        String hijri = "";
        try {
            if (hijriFormat != null) hijri = hijriFormat.format(now.getTime()).replaceAll("\\s*هـ\\s*$", "") + " هـ";
        } catch (Exception ignored) { }
        String greg = gregFormat.format(now.getTime());
        date.setText(arabicDigits(hijri.isEmpty() ? greg : hijri + "  –  " + greg));
        updateAdhanBar(cfg);
        sa.bayti.home.tilawah.AdhanSync.onConfig(this, cfg.fragment);   // «تعديل وقت الصلاة» changed on the TV: the alarm follows
        updateSleepPill();
    }

    /** Once a minute with the clock: the bar before the adhan shows in the last 10 minutes, and until the iqama. */
    private void updateAdhanBar(ConfigSnapshot cfg) {
        String words = AdhanBar.text(this, cfg);
        adhanBar.setVisibility(words == null ? View.GONE : View.VISIBLE);
        if (words != null) adhanBar.setText(words);
    }

    private static String arabicDigits(String s) { return Digits.of(s); }   // the setting's digits (hindi or latin)

    /** Connected or not comes from ConnectivityManager: WifiManager hides the network (id -1, "<unknown ssid>")
     *  from apps without location access, which looked like "not connected". The name only when readable. */
    private void updateWifi() {
        boolean wired = false, wifi = false;
        String ssid = null;
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            wired = nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
            wifi = nc != null && !wired && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                ssid = wm == null || wm.getConnectionInfo() == null ? null : wm.getConnectionInfo().getSSID();
            }
        } catch (SecurityException ignored) { }
        if (wifiPill != null) wifiPill.setLabel(PillText.network(wired, wifi, ssid));   // the network is a tile of «المزيد» now (wifiState)
    }

    // ---------------------------------------------------------------- glow

    private void setGlow(int color) {
        glowColor = color;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(0.5f, 0.78f);
        g.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.62f);
        g.setColors(new int[]{withAlpha(color, 0.5f), withAlpha(color, 0.14f), Color.TRANSPARENT});
        glow.setBackground(g);
    }

    private void animateGlow(int to) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private boolean inGrid;

    private void fadeGlow(float alpha) {
        glow.animate().alpha(alpha).setDuration(300).start();
        // The grid sits over the photo's busiest middle part, so dim it while browsing apps.
        gridShade.animate().alpha(alpha < 1f ? 1f : 0f).setDuration(300).start();
        boolean grid = alpha < 1f;
        if (grid == inGrid) return;
        inGrid = grid;
        // the app drawer: the top bar steps out of the way, and on Android 12 the photo behind turns to frosted glass
        if (topBar != null) topBar.animate().alpha(grid ? 0f : 1f).setDuration(260).start();
        updateCarousel();
        if (Build.VERSION.SDK_INT >= 31) {
            android.graphics.RenderEffect blur = grid ? android.graphics.RenderEffect.createBlurEffect(dp(26), dp(26), android.graphics.Shader.TileMode.CLAMP) : null;
            wallBack.setRenderEffect(blur);
            wallFront.setRenderEffect(blur);
        }
    }

    // ---------------------------------------------------------------- wallpaper

    private ImageView wallpaperLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    // Hosted photos (photos/index.json in the repo) join the bundled ones. Each is downloaded once into
    // files/photos and reused; offline or on any failure the next bundled photo is shown instead.
    static final String PHOTO_BASE = "https://raw.githubusercontent.com/HMubaireek/bayti-tv/master/photos/";
    static final long INDEX_MAX_AGE_MS = 24 * 3600 * 1000L;

    static final int PHOTO_SET = 5, PHOTO_PERIOD_DAYS = 4;

    /** This 4-day period's 5 hosted photos: the same Park-Miller draw as photoSet() in the screensaver page. */
    private String[] remotePhotos() {
        String s = prefs().getString("photoIndex", "");
        if (s.isEmpty()) return new String[0];
        String[] all = s.split(s.contains("\n") ? "\n" : ",");   // one per line; an older copy is comma-separated names
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Riyadh"));
        java.util.Calendar utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH));
        long day = Math.floorDiv(utc.getTimeInMillis(), 86400000L);
        long seed = (Math.floorDiv(day, PHOTO_PERIOD_DAYS) % 2147483646L) + 1;
        List<String> pick = new ArrayList<>();
        for (int guard = 0; pick.size() < Math.min(PHOTO_SET, all.length) && guard < 1000; guard++) {
            seed = (seed * 48271L) % 2147483647L;
            String f = all[(int) (seed % all.length)];
            if (!pick.contains(f)) pick.add(f);
        }
        return pick.toArray(new String[0]);
    }

    /** Keep only the current set on disk. */
    private void pruneCachedPhotos(String[] keep) {
        java.io.File[] files = new java.io.File(getFilesDir(), "photos").listFiles();
        if (files == null) return;
        java.util.Set<String> k = new java.util.HashSet<>();
        for (String p : keep) k.add(cacheName(p));
        for (java.io.File f : files) if (!k.contains(f.getName())) f.delete();
    }

    /** a photo or the index that failed is not asked for again for this long: not at every rotation of the wallpaper */
    static final long RETRY_MS = 60 * 60 * 1000L;
    private final Map<String, Long> photoFailed = new HashMap<>();

    private boolean failedRecently(String what) {
        Long at = photoFailed.get(what);
        return at != null && System.currentTimeMillis() - at < RETRY_MS;
    }

    // «مصادر الصور»: the screensaver app's merged list of the chosen sources (its ConfigProvider .../photos), when other sources than
    // our Saudi set are chosen, and how often the photo changes. A photo there is a file of photos/ or a full https address.
    static final Uri PHOTOS = Uri.parse("content://sa.bayti.screensaver.config/photos");
    private static final String[] PHOTO_HOSTS = {"https://upload.wikimedia.org/", "https://thumb.wikimedia.org/", "https://www.bing.com/"};

    static boolean photoEntry(String f) {
        if (f.matches("[A-Za-z0-9_-]+\\.jpg")) return true;
        for (String h : PHOTO_HOSTS) if (f.startsWith(h) && f.indexOf('\n') < 0) return true;
        return false;
    }

    /** the file a photo is kept in: its own name, or for a full address "src-" and the address's SHA-1, as the screensaver app names it */
    static String cacheName(String f) {
        if (!f.startsWith("https://")) return f;
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(f.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder("src-");
            for (int i = 0; i < 10; i++) s.append(String.format(java.util.Locale.US, "%02x", d[i]));
            return s.append(".jpg").toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "src-" + Integer.toHexString(f.hashCode()) + ".jpg";
        }
    }

    private long wallpaperInterval() {
        long m = prefs().getLong("photoMinutes", 0);
        return m > 0 ? m * 60_000L : WALLPAPER_INTERVAL_MS;
    }

    /** Off the main thread: the chosen sources' list from the screensaver app; true when it gave one (then GitHub's is not asked for). */
    private boolean photoIndexFromSources() {
        try (Cursor c = getContentResolver().query(PHOTOS, null, null, null, null)) {
            // an older screensaver app answers any path with its settings: no "json" column, nothing from it
            if (c == null || c.getColumnIndex("json") != 0 || !c.moveToFirst()) return false;
            String minutes = c.getString(1);
            if (minutes != null && minutes.matches("\\d{1,4}")) prefs().edit().putLong("photoMinutes", Long.parseLong(minutes)).apply();
            String json = c.getString(0);
            if (json == null) {   // only our Saudi set: GitHub's list as always, and drop a list of other sources
                if (prefs().getBoolean("photoIndexSources", false))
                    prefs().edit().remove("photoIndex").remove("photoIndexAt").putBoolean("photoIndexSources", false).apply();
                return false;
            }
            org.json.JSONArray a = new org.json.JSONArray(json);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                String f = a.getJSONObject(i).optString("f");
                if (photoEntry(f)) names.add(f);
            }
            if (names.isEmpty()) return false;
            prefs().edit().putString("photoIndex", android.text.TextUtils.join("\n", names))
                .putLong("photoIndexAt", System.currentTimeMillis()).putBoolean("photoIndexSources", true).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Off the main thread: refresh the hosted photo list once a day (after a failure, again in an hour). */
    private void refreshPhotoIndex() {
        if (photoIndexFromSources()) return;
        if (System.currentTimeMillis() - prefs().getLong("photoIndexAt", 0) < INDEX_MAX_AGE_MS) return;
        if (failedRecently("index.json")) return;
        try {
            org.json.JSONArray a = new org.json.JSONArray(new String(download(PHOTO_BASE + "index.json"), "UTF-8"));
            List<String> names = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                String f = a.getJSONObject(i).optString("f");
                if (f.matches("[A-Za-z0-9_-]+\\.jpg")) names.add(f);
            }
            if (!names.isEmpty()) prefs().edit()
                    .putString("photoIndex", android.text.TextUtils.join(",", names))
                    .putLong("photoIndexAt", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
            photoFailed.put("index.json", System.currentTimeMillis());
        }
    }

    private static byte[] download(String url) throws java.io.IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "BaytiTV/1.0 (https://github.com/HMubaireek/bayti-tv)");   // Wikimedia asks for one
        try (java.io.InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /** Opens the same photo again: the first pass reads only its size. */
    private interface Source { java.io.InputStream open() throws java.io.IOException; }

    /** A photo at about the screen's width: a 4000 px original is not held in memory to be shown on 1920 px. */
    private Bitmap decode(Source source) throws java.io.IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (java.io.InputStream in = source.open()) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, o);
        }
        int want = Math.max(1, getResources().getDisplayMetrics().widthPixels), sample = 1;
        while (o.outWidth / (sample * 2) >= want) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        try (java.io.InputStream in = source.open()) {
            return in == null ? null : BitmapFactory.decodeStream(in, null, o);
        }
    }

    // The owner's own photos («صوري»), read from the screensaver app's ConfigProvider (READ_CONFIG, read only).
    static final Uri OWN = Uri.parse("content://sa.bayti.screensaver.config/own");

    /** {how many, mode, rev}: none, mix and 0 when the screensaver app has none or does not answer */
    private Object[] ownInfo() {
        try (Cursor c = getContentResolver().query(OWN, null, null, null, null)) {
            // an older screensaver app answers any path with its settings instead: no "n" column, no photos
            if (c != null && c.getColumnIndex("n") == 0 && c.moveToFirst()) return new Object[] {c.getInt(0), c.getString(1), c.getLong(2)};
        } catch (RuntimeException ignored) { }
        return new Object[] {0, "mix", 0L};
    }

    /** Off the main thread: when the owner's photos changed since the wallpaper was last chosen, show one now. */
    private void checkOwnChanged() {
        new Thread(() -> {
            long rev = (Long) ownInfo()[2];
            if (rev != prefs().getLong("ownRev", 0)) runOnUiThread(() -> { if (!isDestroyed()) nextWallpaper(); });
        }).start();
    }

    /** Decode the next photo off the main thread, then crossfade it in over the current one. */
    private void nextWallpaper() {
        handler.removeCallbacks(rotateWallpaper);
        if (wallpapers.length == 0) return;
        final Object[] own = ownInfo();
        final int ownCount = (Integer) own[0];
        final boolean only = ownCount > 0 && "only".equals(own[1]);
        prefs().edit().putLong("ownRev", (Long) own[2]).apply();
        final String[] remote = only ? new String[0] : remotePhotos();
        // «مصادر الصور»: with other sources than our Saudi set, their photos replace the bundled ones (still the fallback, offline)
        final int bundled = prefs().getBoolean("photoIndexSources", false) && remote.length > 0 ? 0 : wallpapers.length;
        final int base = only ? 0 : bundled + remote.length;   // ours come first, then the owner's
        final int total = only ? ownCount : base + ownCount;
        if (wallIndex < 0) wallIndex = prefs().getInt("wallpaper", -1);
        wallIndex = (wallIndex + 1) % total;
        prefs().edit().putInt("wallpaper", wallIndex).apply();
        wallChangedAt = SystemClock.elapsedRealtime();
        final int idx = wallIndex;
        final String fallback = "wallpapers/" + wallpapers[idx % wallpapers.length];
        new Thread(() -> {
            if (!only) {   // no network while only the owner's photos show
                refreshPhotoIndex();
                pruneCachedPhotos(remotePhotos());
            }
            Bitmap bm = null;
            if (idx >= base) {
                final Uri photo = OWN.buildUpon().appendPath(String.valueOf(idx - base + 1)).build();
                try {
                    bm = decode(() -> getContentResolver().openInputStream(photo));
                } catch (java.io.IOException | RuntimeException ignored) { }
            } else if (idx >= bundled) {
                java.io.File f = new java.io.File(getFilesDir(), "photos/" + cacheName(remote[idx - bundled]));
                try {
                    if (!f.isFile() && failedRecently(f.getName())) throw new java.io.IOException("failed lately");   // the bundled one meanwhile
                    if (!f.isFile()) {
                        byte[] data;
                        String want = remote[idx - bundled];
                        try { data = download(want.startsWith("https://") ? want : PHOTO_BASE + want); }
                        catch (java.io.IOException e) { photoFailed.put(f.getName(), System.currentTimeMillis()); throw e; }
                        java.io.File dir = f.getParentFile();
                        if (dir.isDirectory() || dir.mkdirs()) {
                            java.io.File tmp = new java.io.File(dir, f.getName() + ".tmp");
                            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) { out.write(data); }
                            if (!tmp.renameTo(f)) tmp.delete();
                        }
                    }
                    bm = decode(() -> new java.io.FileInputStream(f));
                } catch (java.io.IOException ignored) { }
            }
            if (bm == null) {
                try { bm = decode(() -> getAssets().open(fallback)); }
                catch (java.io.IOException ignored) { }
            }
            Bitmap result = bm;
            runOnUiThread(() -> showWallpaper(result));
        }).start();
        scheduleWallpaper(wallpaperInterval());
    }

    /** The rotation's next photo: kept to come back to, and shown unless the carousel has the screen. */
    private void showWallpaper(Bitmap bm) {
        if (bm == null || isDestroyed()) return;
        lastWall = bm;
        if (!carouselShown) crossfade(bm, firstWallpaper ? 600 : 2000);
        firstWallpaper = false;
    }

    // ---------------------------------------------------------------- «العرض المتحرك»

    /** Off the main thread: the carousel's photos from the screensaver app; its stop takes the focus only while there are some. */
    private void loadCarousel() {
        new Thread(() -> {
            Carousel c = Carousel.load(this);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                carousel = c;
                boolean on = !c.items.isEmpty();
                boolean had = heroFocus.hasFocus();
                heroFocus.setFocusable(on);
                if (carouselIdx >= c.items.size()) carouselIdx = 0;
                buildDots();
                wireFocus();
                if (had && !on && !dockCards.isEmpty()) dockCards.get(0).requestFocus();
                updateCarousel();
            });
        }, "carousel").start();
    }

    private int carouselTurn;

    /** The carousel has the screen on «الرئيسية» (the top bar, its stop, the favourites) whenever it has photos; the app drawer and
     *  the other tabs have the rotation's photo. */
    private void updateCarousel() {
        boolean want = carousel != null && !carousel.items.isEmpty() && tabs != null && HomeTabs.HOME_TAB.equals(tabs.current()) && !inGrid;
        showCarousel(want);
    }

    /** it moves on by itself every «تبديل صورة العرض المتحرك كل» minutes while it is shown (and after a press, a whole interval later) */
    private final Runnable carouselNext = () -> { if (carouselShown) stepCarousel(1); };

    private void scheduleCarousel() {
        handler.removeCallbacks(carouselNext);
        if (carouselShown && carousel != null && carousel.minutes > 0 && carousel.items.size() > 1)
            handler.postDelayed(carouselNext, carousel.minutes * 60_000L);
    }

    /** The carousel takes the screen while its stop has the focus; when the focus leaves, the rotation's photo comes back. */
    private void showCarousel(boolean on) {
        boolean show = on && carousel != null && !carousel.items.isEmpty();
        dots.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show == carouselShown) { scheduleCarousel(); return; }
        carouselShown = show;
        scheduleCarousel();
        if (show) {
            handler.removeCallbacks(rotateWallpaper);
            loadCarouselImage();
        } else {
            carouselTurn++;   // a photo still on its way is not shown
            wallFront.animate().cancel();
            wallFront.setScaleX(1f);
            wallFront.setScaleY(1f);
            if (lastWall != null) crossfade(lastWall, 700);
            scheduleWallpaper(wallpaperInterval());
        }
    }

    /** Left and Right on the carousel's stop: the next photo (by 1) or the one before (by -1), round the ends. */
    private void stepCarousel(int by) {
        if (carousel == null || carousel.items.isEmpty()) return;
        int n = carousel.items.size();
        carouselIdx = ((carouselIdx + by) % n + n) % n;
        buildDots();
        loadCarouselImage();
        scheduleCarousel();
    }

    private void loadCarouselImage() {
        if (carousel == null || carousel.items.isEmpty()) return;
        final int turn = ++carouselTurn;
        final String item = carousel.items.get(carouselIdx);
        final int width = getResources().getDisplayMetrics().widthPixels;
        new Thread(() -> {
            Bitmap bm = Carousel.bitmap(this, item, width);
            runOnUiThread(() -> {
                if (turn != carouselTurn || !carouselShown || bm == null || isDestroyed()) return;
                crossfade(bm, 650);
                // a slow push in, as a billboard does, then it holds
                android.animation.ObjectAnimator push = android.animation.ObjectAnimator.ofPropertyValuesHolder(wallFront,
                        android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.06f, 1f),
                        android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.06f, 1f));
                push.setDuration(9000);
                push.setInterpolator(new DecelerateInterpolator());
                push.start();
            });
        }, "carousel-photo").start();
    }

    /** The dots under the clock block: one per photo (at most 12, the current window of them), the current one a gold dash. */
    private void buildDots() {
        dots.removeAllViews();
        if (carousel == null || carousel.items.size() < 2) return;
        int n = carousel.items.size(), shown = Math.min(n, 12);
        int first = Math.max(0, Math.min(carouselIdx - shown / 2, n - shown));
        for (int i = first; i < first + shown; i++) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(3));
            g.setColor(i == carouselIdx ? GOLD : 0x66FFFFFF);
            dot.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(i == carouselIdx ? 18 : 6), dp(6));
            if (i > first) p.setMarginStart(dp(5));
            dots.addView(dot, p);
        }
    }

    private void crossfade(Bitmap bm, int ms) {
        if (bm == null || isDestroyed()) return;
        // Swap roles: the old front becomes the back, the new photo fades in on top.
        final ImageView old = wallFront;
        wallFront = wallBack;
        wallBack = old;
        ViewGroup root = (ViewGroup) wallFront.getParent();
        root.removeView(wallFront);
        root.addView(wallFront, 1, new FrameLayout.LayoutParams(-1, -1));
        wallFront.setImageBitmap(bm);
        wallFront.setAlpha(0f);
        wallFront.setScaleX(1f);   // a carousel photo's push-in may have left this layer larger
        wallFront.setScaleY(1f);
        final ImageView shown = wallFront;
        shown.animate().alpha(1f).setDuration(ms).withEndAction(() -> {
            // release the previous photo: the view this fade was above, not whichever is behind by now (a newer photo may have swapped them)
            if (old != wallFront && old != shown) old.setImageDrawable(null);
        }).start();
    }

    private boolean firstWallpaper = true;

    private void scheduleWallpaper(long delay) {
        handler.removeCallbacks(rotateWallpaper);
        handler.postDelayed(rotateWallpaper, Math.max(1000, delay));
    }

    /** The art redrawn at the size a tile shows it (never enlarged); a banner fills the 16:9 card, an icon fits inside it. */
    private Drawable fitted(Drawable d, boolean banner) {
        int w = Math.round(getResources().getDisplayMetrics().widthPixels / (float) MIN_DOCK * 1.12f), h = w * 9 / 16;   // the focused tile grows 12%
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) return d;
        float f = banner ? Math.max(w / (float) iw, h / (float) ih) : Math.min(h / (float) iw, h / (float) ih);
        if (f >= 1f) return d;
        try {
            int bw = banner ? w : Math.max(1, Math.round(iw * f)), bh = banner ? h : Math.max(1, Math.round(ih * f));
            Bitmap bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            int dw = Math.round(iw * f), dh = Math.round(ih * f);
            d.setBounds((bw - dw) / 2, (bh - dh) / 2, (bw - dw) / 2 + dw, (bh - dh) / 2 + dh);
            d.draw(c);
            return new BitmapDrawable(getResources(), bm);
        } catch (RuntimeException | OutOfMemoryError e) {
            return d;
        }
    }

    /** Average the banner's vivid pixels, then push saturation so the glow reads as a colour. */
    private int sampleColor(String pkg, Drawable d) {
        Integer cached = colorCache.get(pkg);
        if (cached != null) return cached;
        int color = 0xFF3A3A3A;
        try {
            Bitmap bm = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, 24, 14);
            d.draw(c);
            float[] hsv = new float[3];
            double r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
                int p = bm.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double weight = 0.05 + hsv[1] * hsv[2];
                r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight; w += weight;
            }
            bm.recycle();
            if (w > 0) {
                Color.colorToHSV(Color.rgb((int) (r / w), (int) (g / w), (int) (b / w)), hsv);
                if (hsv[1] > 0.12f) { hsv[1] = Math.max(hsv[1], 0.75f); hsv[2] = 0.85f; }
                else { hsv[1] = 0f; hsv[2] = 0.45f; }
                color = Color.HSVToColor(hsv);
            }
        } catch (RuntimeException ignored) { }
        colorCache.put(pkg, color);
        return color;
    }

    // ---------------------------------------------------------------- entrance

    private void playEntrance() {
        topBar.setAlpha(0f);
        topBar.setTranslationY(-dp(12));
        topBar.animate().alpha(1f).translationY(0).setDuration(500).setStartDelay(80).start();
        clockBlock.setAlpha(0f);
        clockBlock.setTranslationX(dp(24));
        clockBlock.animate().alpha(1f).translationX(0).setInterpolator(new DecelerateInterpolator(2f)).setDuration(600).setStartDelay(160).start();
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            View t = dockRow.getChildAt(i);
            t.setAlpha(0f);
            t.setTranslationY(dp(24));
            t.animate().alpha(1f).translationY(0).setInterpolator(new DecelerateInterpolator(2f))
                    .setDuration(450).setStartDelay(160 + i * 60L).start();
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Switch to a passthrough input (HDMI1, HDMI2, AV, …). The standard
     *  TvContract passthrough URI only resolves when a Live-TV app registers
     *  it as implicit; some vendor TV apps (TCL's com.tcl.tv/.TVActivity on
     *  this hardware) handle it only when the intent names the component
     *  explicitly. Try the implicit intent first, then every candidate
     *  activity in the configured live_tv_package, then fall back to the
     *  input picker package. */
    private void openPassthroughInput(TvInputInfo t) {
        Intent base = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(t.getId()));
        base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (launch(base)) return;
        if (liveTvPkg != null && !liveTvPkg.isEmpty() && isInstalled(liveTvPkg)) {
            // Query every activity in the Live-TV package that advertises VIEW
            // and try each with the URI, newest APIs first.
            Intent probe = new Intent(Intent.ACTION_VIEW).setPackage(liveTvPkg);
            List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(probe,
                    PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo ri : handlers) {
                Intent explicit = new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(t.getId()));
                explicit.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (launch(explicit)) return;
            }
        }
        openPackage(inputPickerPkg);
    }

    private boolean launch(Intent i) {
        try {
            if (!i.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) launch(i);
    }

    static final int GOLD = 0xFFD6AF6B;
    static final String SAVER_PKG = "sa.bayti.screensaver";
    private final Map<String, Typeface> fonts = new HashMap<>();

    /** Tajawal from assets/fonts; any old family name maps to the nearest weight. */
    private Typeface font(String family) {
        String file = family.contains("display") ? "Tajawal-Bold"
                : family.contains("thin") || family.contains("light") ? "Tajawal-Light"
                : family.contains("medium") ? "Tajawal-Medium" : "Tajawal-Regular";
        Typeface tf = fonts.get(file);
        if (tf == null) {
            try { tf = Typeface.createFromAsset(getAssets(), "fonts/" + file + ".ttf"); }
            catch (RuntimeException e) { tf = Typeface.create("sans-serif", Typeface.NORMAL); }
            fonts.put(file, tf);
        }
        return tf;
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(family));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams lp(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static LinearLayout.LayoutParams llp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static int withAlpha(int c, float a) {
        return Color.argb(Math.round(a * 255), Color.red(c), Color.green(c), Color.blue(c));
    }

    private static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Menu key on the remote opens the Inputs dropdown.
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && !pills.isEmpty()) {
            showInputs(pills.get(0));
            return true;
        }
        // On the «يُتلى الآن» card ◀ is the previous surah and ▶ the next (a station has none: ◀ goes back to the pills as on any pill)
        if (e.getAction() == KeyEvent.ACTION_DOWN && nowCard != null && getCurrentFocus() == nowCard
                && (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT || e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            NowPlaying.State s = NowPlaying.read(this);
            if (s.show && !s.radio) {
                if (e.getRepeatCount() == 0) {
                    if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) NowPlaying.prev(this); else NowPlaying.next(this);
                }
                return true;
            }
        }
        // In a right to left layout Android looks for the focus the other way round, so the rows are
        // walked by hand here: «right» always moves the highlight to the right.
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            // the top bar is one row across its groups (the tabs, then the buttons): Left goes on, Right back toward the right edge
            int ti = topItems.indexOf(getCurrentFocus());
            boolean left = e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT, rightKey = e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (ti >= 0 && (left || rightKey)) {
                int n = ti + (left ? 1 : -1);
                if (n >= 0 && n < topItems.size()) topItems.get(n).requestFocus();
                return true;
            }
            if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT && moveFocusSideways(false)) return true;
            if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT && moveFocusSideways(true)) return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Moves the focus to the next item of the row, on the right (true) or on the left (false). */
    private boolean moveFocusSideways(boolean right) {
        View focused = getCurrentFocus();
        if (focused == null) return false;
        ViewGroup parent = focused.getParent() instanceof ViewGroup ? (ViewGroup) focused.getParent() : null;
        if (parent == null) return false;

        ViewGroup row = null;
        View item = null;
        if (parent instanceof LinearLayout && ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL) {
            // the focus is on an item of the row itself (a pill)
            row = parent;
            item = focused;
        } else {
            // the app tiles: the focus is on the card inside its box, the row is above the box
            ViewGroup above = parent.getParent() instanceof ViewGroup ? (ViewGroup) parent.getParent() : null;
            if (above instanceof LinearLayout && ((LinearLayout) above).getOrientation() == LinearLayout.HORIZONTAL) {
                row = above;
                item = parent;
            }
        }
        if (row == null) return false;
        int index = row.indexOfChild(item);
        if (index < 0) return false;
        // the row reads from the right, so the item before the focused one is the one on the right
        int next = right ? index - 1 : index + 1;
        if (next < 0 || next >= row.getChildCount()) return true;   // the edge of the row: stay put
        View target = row.getChildAt(next);
        if (target == null || target.getVisibility() != View.VISIBLE) return true;
        View toFocus = target;
        if (target instanceof ViewGroup) {
            ViewGroup box = (ViewGroup) target;
            for (int i = 0; i < box.getChildCount(); i++) {
                View child = box.getChildAt(i);
                if (child.isFocusable() && child.getVisibility() == View.VISIBLE) {
                    toFocus = child;
                    break;
                }
            }
        }
        if (!toFocus.isFocusable() || toFocus.getVisibility() != View.VISIBLE) return true;
        toFocus.requestFocus();
        return true;
    }

    /** 16:9 container. */
    static final class AspectFrame extends FrameLayout {
        AspectFrame(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    /** ScrollView that never auto-scrolls to the focused child; the activity scrolls it by row. */
    static final class HomeScrollView extends android.widget.ScrollView {
        HomeScrollView(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
    }
}
