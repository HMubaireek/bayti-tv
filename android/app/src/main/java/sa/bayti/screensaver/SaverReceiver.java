package sa.bayti.screensaver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The screensaver's settings from ADB, for agents (people use SettingsActivity). Only the shell can send
 * these: the manifest requires android.permission.DUMP. optimize/saver.sh wraps them.
 *
 *   am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.GET
 *   am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.CITIES
 *   am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.SET
 *       [--es city 'الرس'] [--es lat 25.86 --es lon 43.49 --es tz Asia/Kuwait]   (tz is needed for a place that is not in the list) [--es teams '136013:Al-Hilal,150643:Al-Zulfi']
 *       [--es panel prayers] [--es home_panel prayers,hourly,verse|none] [--es greeting on|off] [--es adhanbar on|off] [--es yearstrip on|off] [--es minrating 7] [--es genres Drama,Comedy] [--es langs English]
 *       [--es tabs movies,shows,today|none] [--es tmdb KEY|clear] [--es carousel nature,own|none] [--es titleapp auto|nuvio|stremio|none]
 *       [--es look minimal|classic] [--es nextprayer always|hour|off] [--es occasion on|off]
 *       [--es photos 30] [--es photosrc saudi,nature] [--es idle 10] [--es digits hindi|latin] [--es method umm_alqura|jafari|tehran|gulf|kuwait|qatar|dubai|mwl|egypt|karachi|isna]
 *       [--es cardstyle current|dark1|glass1|dark3|glass3] [--es national auto|off]
 *       [--es markets tasi,gold,btc] [--es mkstyle arrows|prices] [--es stocks 2222,1120,AAPL]
 *       [--es leagues 'Saudi-Arabian Pro League,NBA']
 *       [--es prayeradj on|off] [--es adj_fajr +2 --es adj_dhuhr 0 --es adj_asr 0 --es adj_maghrib -1 --es adj_isha 0]   («تعديل وقت الصلاة», minutes -20..20)
 *       [--es slot matches|weather|fasting|countdown|markets|none] [--es countdown 2026-12-01|clear --es cdname 'رحلة الصيف']
 *   am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.EXPORT                every setting: "backup: <base64>"
 *   am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.IMPORT --es b64 <base64>   (optimize/settings.sh)
 *   am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.UPDATE
 *   am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.TASKS
 *       --es op list | add --es text 'نص' | done --ei n 1 | del --ei n 1 | clear
 *   am broadcast -n sa.bayti.screensaver/.SaverReceiver -a sa.bayti.screensaver.PHOTOS
 *       --es op list | add --ei size BYTES | add --ei size BYTES --es path /data/local/tmp/urtv-own/in.jpg | del --ei n 1 | clear | mode --es mode mix|only
 *
 * leagues = whole leagues (Prefs.LEAGUES, up to Prefs.MAX_LEAGUES; the English or the Arabic name; "" = none): every game of every
 * club in them joins the followed teams' games.
 * PHOTOS = «صوري», the owner's own photos (Own, up to 10; optimize/photos.sh): "add" keeps the photo the shell just wrote
 * through ConfigProvider (.../own-in), or, with path, one in /data/local/tmp/urtv-own/ (only where the app may read it);
 * mode = mix (they join ours, as always) or only.
 * greeting = the greeting and the weather line above the home screen's clock (on, the default, or off: the plain good-morning).
 * yearstrip = the slim year strip under the screensaver's clock (on, or off, the default).
 * slot = what the bottom-left slot shows (Prefs.SLOTS). countdown = the date (YYYY-MM-DD) slot=countdown counts down to, or
 * "clear"; cdname = its name (up to 40 characters).
 * national = auto (as always from now: the quiet green look on National Day, 21-25 September, and Founding Day, 20-24 February) or off.
 * cardstyle = the look of the card beside the clock (Prefs.CARD_STYLES; current is the plain card).
 * markets = up to four of Prefs.MARKETS at the top left, in order; mkstyle = arrows only (as always) or the price and the day's change.
 * stocks = the watchlist of the stocks card (panel stocks): up to Prefs.MAX_STOCKS Tadawul codes (4 digits) or US tickers.
 * look = minimal (Bayti TV's default: a small light clock at the corner) or classic (the big clock in the middle). nextprayer = always,
 * hour (only the last hour before the adhan) or off. occasion = on or off (the next occasion, Ramadan's countdown, in the bottom row).
 * photosrc = where the background photos come from, in order: a comma list of PhotoSources.ALL (saudi, as always; nature mountains sunsets
 * space potd from Wikimedia Commons; bing), e.g. saudi,nature.
 * method = how the prayer times are calculated (Prefs.PRAYER_METHODS; umm_alqura as always), for the screensaver and the launcher.
 * digits = hindi (١٢٣, as always) or latin (123). photos = minutes between background photos (Prefs.PHOTO_MINUTES). idle = minutes before the TV starts the
 * screensaver (Prefs.IDLE_MINUTES): this is the system's screen_off_timeout, which a receiver (it runs as this
 * app, not as the shell) may write only when the app holds WRITE_SETTINGS; otherwise it says so and
 * optimize/saver.sh sets it from the shell instead. Nothing changes unless the extra is given.
 *
 * The answer is in the "data" of am's output. Unknown values are refused with a message, nothing half-saved.
 */
public class SaverReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Prefs p = new Prefs(c);
        String action = String.valueOf(i.getAction());
        switch (action) {
            case "sa.bayti.screensaver.GET":
                setResultData(describe(c, p));
                return;
            case "sa.bayti.screensaver.CITIES": {
                StringBuilder b = new StringBuilder();
                for (Cities.Region r : Cities.REGIONS) {
                    b.append(r.name).append(": ");
                    for (String[] city : r.cities) b.append(city[0]).append("، ");
                    b.setLength(b.length() - 2);
                    b.append('\n');
                }
                setResultData(b.toString().trim());
                return;
            }
            case "sa.bayti.screensaver.UPDATE": {
                final PendingResult pending = goAsync();   // a download: off the main thread
                final Context app = c.getApplicationContext();
                new Thread(() -> {
                    pending.setResultData("update: " + Updater.check(app, true));
                    pending.finish();
                }).start();
                return;
            }
            case "sa.bayti.screensaver.SET":
                setResultData(set(c, p, i));
                return;
            case "sa.bayti.screensaver.TASKS":
                setResultData(tasks(p, i));
                return;
            case "sa.bayti.screensaver.PHOTOS":
                setResultData(photos(c, p, i));
                return;
            case "sa.bayti.screensaver.EXPORT":   // «نسخة الإعدادات» (optimize/settings.sh save): every setting, base64 of JSON
                try { setResultData("backup: " + PrefsBackup.export(c)); }
                catch (Exception e) { setResultData("error: backup failed: " + e.getMessage()); }
                return;
            case "sa.bayti.screensaver.IMPORT": {   // --es b64 <what EXPORT gave>: the settings back
                String b64 = i.getStringExtra("b64");
                if (b64 == null || b64.isEmpty()) { setResultData("error: IMPORT needs --es b64 <backup>"); return; }
                try { setResultData("restored: " + PrefsBackup.restore(c, b64) + " settings"); }
                catch (Exception e) { setResultData("error: not a backup of these settings (" + e.getMessage() + ")"); }
                return;
            }
            default:
                setResultData("error: unknown action " + action);
        }
    }

    /** The name is a built-in city, in full or as the short form that fits only one. */
    private static boolean inList(String name) {
        for (Cities.Region r : Cities.REGIONS) for (String[] x : r.cities) if (x[0].equals(name)) return true;
        return CityMatch.find(name).size() == 1;
    }

    private static String set(Context c, Prefs p, Intent i) {
        // check everything first, then save: a typo never leaves half the settings changed
        String[] city = null;
        String name = i.getStringExtra("city"), lat = i.getStringExtra("lat"), lon = i.getStringExtra("lon");
        String tz = i.getStringExtra("tz");   // only for a place of your own; a built-in city is Asia/Riyadh
        if (tz != null && (name == null || lat == null || lon == null))
            return "error: tz goes with city, lat and lon (a built-in city is Asia/Riyadh)";
        if (tz != null && !Arrays.asList(java.util.TimeZone.getAvailableIDs()).contains(tz))
            return "error: tz is a time zone like Asia/Kuwait or Africa/Cairo";
        if (name != null) {
            if (lat != null && lon != null) {
                if (!lat.matches("-?\\d{1,2}(\\.\\d+)?") || !lon.matches("-?\\d{1,3}(\\.\\d+)?"))
                    return "error: lat/lon must be numbers like 25.86 / 43.49";
                // a place of your own has its own time zone, and a missing one would be guessed (the TV's, Riyadh's): ask for it
                if (tz == null && !inList(name))
                    return "error: add the time zone for a place that is not in the list, e.g. --es tz Asia/Riyadh or Asia/Kuwait";
                city = new String[] {name, lat, lon};
            } else {
                for (Cities.Region r : Cities.REGIONS) for (String[] x : r.cities) if (x[0].equals(name)) city = x;
                if (city == null) {   // a short form of a built-in name: «مكة» for «مكة المكرمة»
                    List<String[]> hits = CityMatch.find(name);
                    if (hits.size() == 1) city = hits.get(0);
                    else if (hits.size() > 1) {
                        List<String> names = new ArrayList<>();
                        for (String[] h : hits) names.add(h[0]);
                        return "error: «" + name + "» fits several cities: " + Prefs.join(names, "، ") + " — give one of them in full";
                    }
                }
                if (city == null) return "error: city not in the built-in list; give --es lat and --es lon too (CITIES lists them)";
            }
        }
        List<String> teams = null;
        String t = i.getStringExtra("teams");
        if (t != null) {
            teams = new ArrayList<>();
            for (String part : t.split(",")) {
                part = part.trim();
                if (part.isEmpty()) continue;
                String[] kv = part.split(":", 2);
                if (!kv[0].matches("\\d+")) return "error: teams are id:EnglishName, e.g. 136013:Al-Hilal";
                String en = kv.length > 1 ? kv[1].trim() : "";
                // the bundled list knows the club by id (its Arabic name as the screen shows it); a club outside
                // the list gets the name made from its English one
                TeamSearch.Team known = Teams.byId(c, kv[0]);
                String ar = known != null && !known.ar.isEmpty() ? known.ar
                    : en.isEmpty() ? "#" + kv[0] : Arabic.team(c, en);
                teams.add(kv[0] + "|" + ar.replace("|", " ").replace(";", " "));
            }
            if (teams.size() > Prefs.MAX_TEAMS) return "error: at most " + Prefs.MAX_TEAMS + " teams";
        }
        String panel = i.getStringExtra("panel"), home = i.getStringExtra("home_panel");
        if (panel != null && !isPanel(panel)) return "error: panel is one of " + panels();
        List<String> homeCards = null;
        if (home != null) {
            homeCards = new ArrayList<>();
            for (String h : home.split(",")) {
                h = h.trim();
                if (h.isEmpty() || "none".equals(h)) continue;
                if (!isPanel(h)) return "error: home_panel is up to " + Prefs.HOME_CARDS + " of " + panels() + ", separated by commas";
                if (!homeCards.contains(h)) homeCards.add(h);
            }
            if (homeCards.size() > Prefs.HOME_CARDS) return "error: home_panel is at most " + Prefs.HOME_CARDS + " cards";
        }
        String adhanBar = i.getStringExtra("adhanbar");
        if (adhanBar != null && !"on".equals(adhanBar) && !"off".equals(adhanBar))
            return "error: adhanbar is one of on off";
        String prayerAdj = i.getStringExtra("prayeradj");
        if (prayerAdj != null && !"on".equals(prayerAdj) && !"off".equals(prayerAdj))
            return "error: prayeradj is one of on off";
        int[] adj = null;   // «تعديل وقت الصلاة»: each given prayer is a whole number of minutes, −20..20; the rest stay
        for (int k = 0; k < PrayerAdj.COUNT; k++) {
            String v = i.getStringExtra("adj_" + PrayerAdj.KEYS[k]);
            if (v == null) continue;
            Integer n = PrayerAdj.parse(v.trim());
            if (n == null) return "error: " + PrayerAdj.KEYS[k] + " is a whole number of minutes from -" + PrayerAdj.MAX + " to " + PrayerAdj.MAX + ", like +2 or -1";
            if (adj == null) adj = p.prayerAdj();
            adj[k] = n;
        }
        String yearStrip = i.getStringExtra("yearstrip");
        if (yearStrip != null && !"on".equals(yearStrip) && !"off".equals(yearStrip))
            return "error: yearstrip is one of on off";
        String greeting = i.getStringExtra("greeting");
        if (greeting != null && !"on".equals(greeting) && !"off".equals(greeting))
            return "error: greeting is one of on off";
        String rating = i.getStringExtra("minrating");
        if (rating != null && !Arrays.asList(Prefs.MIN_RATINGS).contains(rating))
            return "error: minrating is one of " + Prefs.join(Arrays.asList(Prefs.MIN_RATINGS), " ");
        String photos = i.getStringExtra("photos");
        if (photos != null && !Arrays.asList(Prefs.PHOTO_MINUTES).contains(photos))
            return "error: photos is one of " + Prefs.join(Arrays.asList(Prefs.PHOTO_MINUTES), " ");
        String digits = i.getStringExtra("digits");
        if (digits != null && !isChoice(Prefs.DIGITS, digits))
            return "error: digits is one of " + choices(Prefs.DIGITS);
        String look = i.getStringExtra("look");
        if (look != null && !isChoice(Prefs.LOOKS, look)) return "error: look is one of " + choices(Prefs.LOOKS);
        String nextPrayer = i.getStringExtra("nextprayer");
        if (nextPrayer != null && !isChoice(Prefs.NEXT_PRAYER, nextPrayer)) return "error: nextprayer is one of " + choices(Prefs.NEXT_PRAYER);
        String occasion = i.getStringExtra("occasion");
        if (occasion != null && !"on".equals(occasion) && !"off".equals(occasion)) return "error: occasion is one of on off";
        String tabs = i.getStringExtra("tabs");
        List<String> tabList = null;
        if (tabs != null) {
            tabList = new ArrayList<>();
            for (String part : tabs.split(",")) {
                String k = part.trim();
                if (k.isEmpty() || "none".equals(k)) continue;
                if (!isChoice(Prefs.TABS, k)) return "error: tabs is a comma list of " + choices(Prefs.TABS) + ", or none";
                tabList.add(k);
            }
        }
        String tmdb = i.getStringExtra("tmdb");
        if (tmdb != null && !"clear".equals(tmdb) && !Prefs.tmdbKeyOk(tmdb)) return "error: tmdb is a TMDB API key or read token, or clear";
        String titleApp = i.getStringExtra("titleapp");
        if (titleApp != null && !isChoice(Prefs.TITLE_APPS, titleApp)) return "error: titleapp is one of " + choices(Prefs.TITLE_APPS);
        String carousel = i.getStringExtra("carousel");
        List<String> carouselList = null;
        if (carousel != null) {
            carouselList = new ArrayList<>();
            for (String part : carousel.split(",")) {
                String k = part.trim();
                if (k.isEmpty() || "none".equals(k)) continue;
                if (!PhotoSources.isSource(k) && !Prefs.OWN_SOURCE.equals(k)) return "error: carousel is a comma list of photo sources and own, or none";
                carouselList.add(k);
            }
        }
        String photoSrc = i.getStringExtra("photosrc");
        if (photoSrc != null) {
            for (String k : photoSrc.split(","))
                if (!PhotoSources.isSource(k.trim())) {
                    StringBuilder keys = new StringBuilder();
                    for (String[] s : PhotoSources.ALL) keys.append(keys.length() > 0 ? " " : "").append(s[0]);
                    return "error: photosrc is a comma list of " + keys;
                }
        }
        String method = i.getStringExtra("method");
        if (method != null && !isChoice(Prefs.PRAYER_METHODS, method))
            return "error: method is one of " + choices(Prefs.PRAYER_METHODS);
        String national = i.getStringExtra("national");
        if (national != null && !isChoice(Prefs.NATIONAL, national))
            return "error: national is one of " + choices(Prefs.NATIONAL);
        String cardStyle = i.getStringExtra("cardstyle");
        if (cardStyle != null && !isChoice(Prefs.CARD_STYLES, cardStyle))
            return "error: cardstyle is one of " + choices(Prefs.CARD_STYLES);
        String mkStyle = i.getStringExtra("mkstyle");
        if (mkStyle != null && !isChoice(Prefs.MK_STYLES, mkStyle))
            return "error: mkstyle is one of " + choices(Prefs.MK_STYLES);
        List<String> markets = codes(i.getStringExtra("markets"), Prefs.MARKETS);
        if (markets != null) {
            if (markets.contains(null)) return "error: markets are " + names(Prefs.MARKETS);
            if (markets.isEmpty() || markets.size() > Prefs.MAX_MARKETS || new java.util.HashSet<>(markets).size() != markets.size())
                return "error: markets is 1 to " + Prefs.MAX_MARKETS + " different ones of " + names(Prefs.MARKETS);
        }
        List<String> stocks = null;
        if (i.getStringExtra("stocks") != null) {
            stocks = new ArrayList<>();
            for (String s : i.getStringExtra("stocks").split(",")) {
                s = s.trim().toUpperCase(java.util.Locale.ROOT);
                if (s.isEmpty()) continue;
                if (!s.matches(Prefs.STOCK_PATTERN) || stocks.contains(s))
                    return "error: stocks are 4-digit Tadawul codes or US tickers, each once, e.g. 2222,1120,AAPL";
                stocks.add(s);
            }
            if (stocks.isEmpty() || stocks.size() > Prefs.MAX_STOCKS)
                return "error: stocks is 1 to " + Prefs.MAX_STOCKS + " codes or tickers";
        }
        String slot = i.getStringExtra("slot");
        if (slot != null && !isChoice(Prefs.SLOTS, slot))
            return "error: slot is one of " + choices(Prefs.SLOTS);
        String cd = i.getStringExtra("countdown"), cdName = i.getStringExtra("cdname");
        if (cd != null && !"clear".equals(cd) && !isDate(cd))
            return "error: countdown is a date like 2026-12-01, or clear";
        if (cdName != null && cd == null) return "error: cdname goes with countdown DATE";
        if (cdName != null && cdName.length() > 40) return "error: cdname is at most 40 characters";
        String idle = i.getStringExtra("idle");
        if (idle != null) {
            if (!Arrays.asList(Prefs.IDLE_MINUTES).contains(idle))
                return "error: idle is one of " + Prefs.join(Arrays.asList(Prefs.IDLE_MINUTES), " ");
            if (!Prefs.canWriteSettings(c))
                return "error: idle not allowed: this app may not change system settings on this TV"
                    + " (the TV's own screensaver settings can, or the shell: settings put system screen_off_timeout)";
        }
        List<String> genres = codes(i.getStringExtra("genres"), Prefs.GENRES);
        List<String> langs = codes(i.getStringExtra("langs"), Prefs.LANGS);
        if (genres != null && genres.contains(null)) return "error: genres are " + names(Prefs.GENRES);
        if (langs != null && langs.contains(null)) return "error: langs are " + names(Prefs.LANGS);
        List<String> events = codes(i.getStringExtra("events"), Prefs.EVENTS);
        if (events != null && events.contains(null)) return "error: events are " + names(Prefs.EVENTS);
        List<String> leagues = null;
        if (i.getStringExtra("leagues") != null) {
            leagues = new ArrayList<>();
            for (String s : i.getStringExtra("leagues").split(",")) {
                if (s.trim().isEmpty()) continue;
                String[] l = Prefs.leagueByName(s);
                if (l == null) return "error: leagues are (English or Arabic name) " + names(Prefs.LEAGUES);
                if (!leagues.contains(l[0])) leagues.add(l[0]);
            }
            if (leagues.size() > Prefs.MAX_LEAGUES) return "error: at most " + Prefs.MAX_LEAGUES + " leagues";
        }

        if (idle != null) {   // first: a refusal here leaves everything else unsaved
            try {
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                    Integer.parseInt(idle) * 60000);
            } catch (SecurityException e) {
                return "error: idle not allowed: the TV refused the change";
            }
        }
        if (city != null) p.setCity(city[0], city[1], city[2], tz == null ? "" : tz);
        if (teams != null) p.setList("teams", teams);
        if (panel != null) p.setPanel(panel);
        if (homeCards != null) p.setHomePanels(homeCards);
        if (greeting != null) p.setGreeting("on".equals(greeting));
        if (adhanBar != null) p.setAdhanBar("on".equals(adhanBar));
        if (yearStrip != null) p.setYearStrip("on".equals(yearStrip));
        if (adj != null) p.setPrayerAdj(adj);
        if (prayerAdj != null) p.setPrayerAdjOn("on".equals(prayerAdj));
        if (rating != null) p.setMinRating(rating);
        if (photos != null) p.setPhotoMinutes(photos);
        if (digits != null) p.setDigits(digits);
        if (method != null) p.setPrayerMethod(method);
        if (look != null) p.setLook(look);
        if (tabList != null) p.setTabs(tabList);
        if (tmdb != null) p.setTmdbKey("clear".equals(tmdb) ? "" : tmdb);
        if (carouselList != null) p.setCarouselSources(carouselList);
        if (titleApp != null) p.setTitleApp(titleApp);
        if (nextPrayer != null) p.setNextPrayer(nextPrayer);
        if (occasion != null) p.setOccasion("on".equals(occasion));
        if (photoSrc != null) p.setPhotoSources(java.util.Arrays.asList(photoSrc.split(",")));
        if (national != null) p.setNational(national);
        if (cardStyle != null) p.setCardStyle(cardStyle);
        if (mkStyle != null) p.setMkStyle(mkStyle);
        if (markets != null) p.setMarkets(markets);
        if (stocks != null) p.setStocks(stocks);
        if (slot != null) p.setSlot(slot);
        if (cd != null) {
            if ("clear".equals(cd)) p.setCountdown("", "");
            else p.setCountdown(cd, cdName == null ? "" : cdName.trim());
        }
        if (genres != null) p.setList("genres", genres);
        if (langs != null) p.setList("langs", langs);
        if (events != null) p.setList("events", events);
        if (leagues != null) p.setList("leagues", leagues);
        return "saved\n" + describe(c, p);
    }

    /** «المهام»: n is the number list shows (from 1, done ones included). */
    private static String tasks(Prefs p, Intent i) {
        String op = String.valueOf(i.getStringExtra("op"));
        switch (op) {
            case "list":
                return listing(p);
            case "add": {
                String why = p.addTask(i.getStringExtra("text"));
                return why != null ? "error: " + why : "saved\n" + listing(p);
            }
            case "done":
            case "del": {
                int n = i.getIntExtra("n", 0);
                boolean ok = "done".equals(op) ? p.doneTask(n) : p.deleteTask(n);
                return !ok ? "error: no task number " + n + " (list shows the numbers)"
                    : "saved\n" + listing(p);
            }
            case "clear":
                p.clearTasks();
                return "saved\nno tasks";
            default:
                return "error: op is one of list add done del clear";
        }
    }

    /** «صوري»: n is the number list shows (from 1). */
    private static String photos(Context c, Prefs p, Intent i) {
        String op = String.valueOf(i.getStringExtra("op"));
        switch (op) {
            case "list":
                return photoListing(c, p);
            case "add": {
                String path = i.getStringExtra("path");
                java.io.File src = Own.incoming(c);
                if (path != null) {
                    if (!path.matches("/data/local/tmp/urtv-own/[A-Za-z0-9_.-]+") || path.contains(".."))
                        return "error: path must be a file in /data/local/tmp/urtv-own/";
                    src = new java.io.File(path);
                }
                int size = i.getIntExtra("size", -1);   // what the shell sent: a short or stale file is refused
                String why = size >= 0 && src.length() != size ? "the file is not the one sent (" + src.length() + " of " + size + " bytes)"
                    : Own.add(c, src);
                if (why != null && src.equals(Own.incoming(c))) src.delete();
                return why != null ? "error: " + why : "saved\n" + photoListing(c, p);
            }
            case "del": {
                int n = i.getIntExtra("n", 0);
                return !Own.delete(c, n) ? "error: no photo number " + n + " (list shows the numbers)"
                    : "saved\n" + photoListing(c, p);
            }
            case "clear":
                Own.clear(c);
                return "saved\n" + photoListing(c, p);
            case "mode": {
                String m = i.getStringExtra("mode");
                if (!Own.MIX.equals(m) && !Own.ONLY.equals(m)) return "error: mode is mix or only";
                p.setOwnMode(m);
                p.bumpOwnRev();
                return "saved\n" + photoListing(c, p);
            }
            default:
                return "error: op is one of list add del clear mode";
        }
    }

    private static String photoListing(Context c, Prefs p) {
        int n = Own.count(c);
        StringBuilder b = new StringBuilder("photos: " + n + " of " + Own.MAX + "\nmode: " + p.ownMode());
        if (n == 0) b.append(" (no photos: ours only)");
        else if (!Own.ONLY.equals(Own.mode(c))) b.append(" (they join ours)");
        else b.append(" (only theirs)");
        for (int k = 1; k <= n; k++) b.append('\n').append(k).append(". ").append(Own.describe(c, k));
        return b.toString();
    }

    private static String listing(Prefs p) {
        List<Prefs.Task> all = p.tasks();
        if (all.isEmpty()) return "no tasks";
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < all.size(); k++)
            b.append(k + 1).append(all.get(k).done ? ". [done] " : ". ").append(all.get(k).text).append('\n');
        return b.toString().trim();
    }

    private static String describe(Context c, Prefs p) {
        String[] city = p.city();
        String latest = p.raw().getString("latestVersion", null);   // what Updater last saw on the release
        return "version: " + BuildConfig.VERSION_NAME + (latest != null ? " (newest: " + latest + ")" : "")
            + "\ncity: " + city[0] + " (" + city[1] + ", " + city[2] + ")"
            + "\ntimezone: " + p.cityTz()
            + "\nmethod: " + p.prayerMethod()
            + "\nlook: " + p.look()
            + "\ntabs: home" + (p.tabs().isEmpty() ? "" : "," + Prefs.join(p.tabs(), ","))
            + "\ntmdb: " + (p.tmdbKey().isEmpty() ? "none" : "set")   // the key itself is never printed
            + "\ncarousel: " + (p.carouselSources().isEmpty() ? "off" : Prefs.join(p.carouselSources(), ","))
            + "\ntitleapp: " + p.titleApp()
            + "\nnextprayer: " + p.nextPrayer()
            + "\noccasion: " + (p.occasion() ? "on" : "off")
            + "\nteams: " + Prefs.join(p.teams(), ", ")
            + "\npanel: " + p.panel() + "\nhome_panel: " + p.homePanel()
            + "\ngreeting: " + (p.greeting() ? "on" : "off")
            + "\nadhanbar: " + (p.adhanBar() ? "on" : "off")
            + "\nyearstrip: " + (p.yearStrip() ? "on" : "off")
            + "\nprayeradj: " + (p.prayerAdjOn() ? "on" : "off") + " " + PrayerAdj.describe(p.prayerAdj())
            + "\nminrating: " + p.minRating()
            + "\nphotos: " + p.photoMinutes()
            + "\nphotosrc: " + Prefs.join(p.photoSources(), ",")
            + "\ndigits: " + p.digits()
            + "\nnational: " + p.national()
            + "\ncardstyle: " + p.cardStyle()
            + "\nmarkets: " + Prefs.join(p.markets(), ",") + "\nmkstyle: " + p.mkStyle()
            + "\nstocks: " + Prefs.join(p.stocks(), ",")
            + "\nslot: " + p.slot()
            + "\ncountdown: " + (p.countdownDate().isEmpty() ? "none" : p.countdownDate() + " " + p.countdownName())
            + "\nidle: " + idle(Prefs.idleMs(c))
            + "\ngenres: " + Prefs.join(p.list("genres"), ",") + "\nlangs: " + Prefs.join(p.list("langs"), ",")
            + "\nevents: " + Prefs.join(p.list("events"), ",")
            + "\nleagues: " + Prefs.join(p.leagues(), ",")
            + "\nautoupdate: " + p.autoUpdate();
    }

    /** minutes the TV waits before the screensaver, "unknown" when it does not say */
    private static String idle(long ms) {
        if (ms <= 0) return "unknown";
        return ms % 60000 == 0 ? String.valueOf(ms / 60000) : (ms / 1000) + "s";
    }

    /** a real calendar date written YYYY-MM-DD */
    private static boolean isDate(String v) {
        if (!v.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        ParsePosition pos = new ParsePosition(0);
        return f.parse(v, pos) != null && pos.getIndex() == v.length();
    }

    private static boolean isChoice(String[][] all, String v) {
        for (String[] x : all) if (x[0].equals(v)) return true;
        return false;
    }

    private static String choices(String[][] all) {
        List<String> out = new ArrayList<>();
        for (String[] x : all) out.add(x[0]);
        return Prefs.join(out, " ");
    }

    private static boolean isPanel(String v) {
        for (String[] x : Prefs.PANELS) if (x[0].equals(v)) return true;
        return false;
    }

    private static String panels() {
        return names(Prefs.PANELS);
    }

    private static String names(String[][] table) {
        List<String> out = new ArrayList<>();
        for (String[] x : table) out.add(x[0]);
        return Prefs.join(out, " ");
    }

    /** "Drama,Comedy" -> the same codes, checked against the table; null entry = unknown; "" = all. */
    private static List<String> codes(String raw, String[][] table) {
        if (raw == null) return null;
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            String hit = null;
            for (String[] x : table) if (x[0].equalsIgnoreCase(s)) hit = x[0];
            out.add(hit);
        }
        return out;
    }
}
