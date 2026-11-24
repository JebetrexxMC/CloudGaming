package com.limelight.cloudsync;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.PcView;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CloudSync: a mobile game launcher in the style of Collapse Launcher
 * (https://github.com/CollapseLauncher/Collapse, MIT), wired to your own cloud PC.
 *
 *  - Live game art, banners and news from the official HoYoPlay launcher feed
 *    (the same content Collapse and HoYoPlay show), nothing bundled.
 *  - Layered parallax (tilt), slow zoom, rotating backgrounds, blur on scroll,
 *    acrylic panels, banner carousel with pips, news tabs, playtime.
 *  - Start Game: adds your host to Moonlight and opens it. The split arrow has
 *    Start/Stop host (GitHub Actions), Wake PC, all hosts, VPN and setup.
 *  - Play without a PC: Genshin Impact Cloud (official) and GeForce NOW.
 */
public class CloudSyncActivity extends Activity implements SensorEventListener {
    private static final String PREFS = "cloudsync";
    private static final String API = "https://sg-hyp-api.hoyoverse.com/hyp/hyp-connect/api/";
    private static final String LAUNCHER_ID = "VYTpXlbWo8";
    private static final String CLOUD_GENSHIN_PKG = "com.hoyoverse.cloudgames.GenshinImpact";
    private static final String GFN_PKG = "com.nvidia.geforcenow";

    private static final int ACCENT = Color.parseColor("#FFD52A");   // Collapse accent
    private static final int INK = Color.parseColor("#0E0F13");
    private static final int GLASS = Color.parseColor("#8C14161C");  // acrylic tint
    private static final int GLASS_LINE = Color.parseColor("#26FFFFFF");
    private static final int MUTED = Color.parseColor("#B8C0CC");

    // Game id, biz, fallback name
    private static final String[][] GAMES = {
            {"gopR6Cufr3", "hk4e_global", "Genshin Impact"},
            {"4ziysqXOQ8", "hkrpg_global", "Honkai: Star Rail"},
            {"U5hbdsT9W7", "nap_global", "Zenless Zone Zero"},
            {"5TIVvvcwtM", "bh3_global", "Honkai Impact 3rd"},
    };

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(48 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private SharedPreferences prefs;
    private int game;
    private JSONObject basicInfo, gamesInfo;

    // background layers
    private FrameLayout bgLayer;
    private ImageView bgA, bgB, bgTheme, bgBlur;
    private boolean bgFront;
    private final List<String[]> backgrounds = new ArrayList<>();
    private int bgIndex;
    private LinearLayout bgPips;
    private ValueAnimator kenBurns;

    // content
    private ScrollView scroll;
    private ImageView logo;
    private TextView gameTitle, statusText, playtimeText;
    private View statusDot;
    private LinearLayout gameBar;
    private FrameLayout carousel;
    private ImageView carouselImg;
    private LinearLayout carouselPips;
    private final List<String[]> banners = new ArrayList<>();
    private int bannerIndex;
    private LinearLayout newsList, newsTabs, socialRow;
    private JSONArray posts = new JSONArray();
    private String newsType = "POST_TYPE_ACTIVITY";

    private SensorManager sensors;
    private float tiltX, tiltY;
    private int scrollY;
    private View topBg;

    private final Runnable rotateBg = new Runnable() {
        @Override public void run() {
            if (backgrounds.size() > 1) showBackground((bgIndex + 1) % backgrounds.size());
            ui.postDelayed(this, 12000);
        }
    };
    private final Runnable rotateBanner = new Runnable() {
        @Override public void run() {
            if (banners.size() > 1) showBanner((bannerIndex + 1) % banners.size(), 1);
            ui.postDelayed(this, 5500);
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        game = Math.max(0, Math.min(GAMES.length - 1, prefs.getInt("game", 0)));
        edgeToEdge();
        setContentView(buildUi());
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        loadFeeds();
    }

    @Override
    protected void onResume() {
        super.onResume();
        long started = prefs.getLong("session_start", 0);
        if (started > 0) {
            long mins = Math.max(0, (System.currentTimeMillis() - started) / 60000);
            String k = "playtime_" + GAMES[game][1];
            prefs.edit().putLong(k, prefs.getLong(k, 0) + mins)
                    .putLong("last_session_" + GAMES[game][1], mins)
                    .remove("session_start").apply();
        }
        updatePlaytime();
        refreshStatus();
        if (sensors != null) {
            Sensor s = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (s != null) sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        }
        ui.postDelayed(rotateBg, 12000);
        ui.postDelayed(rotateBanner, 5500);
        if (kenBurns != null) kenBurns.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensors != null) sensors.unregisterListener(this);
        ui.removeCallbacks(rotateBg);
        ui.removeCallbacks(rotateBanner);
        if (kenBurns != null) kenBurns.cancel();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void edgeToEdge() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.parseColor("#CC0E0F13"));
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(INK);

        // Background: two crossfading layers + optional character layer + blur layer
        bgLayer = new FrameLayout(this);
        bgA = bgImage(); bgB = bgImage(); bgTheme = bgImage(); bgBlur = bgImage();
        bgB.setAlpha(0f); bgBlur.setAlpha(0f);
        bgLayer.addView(bgA); bgLayer.addView(bgB); bgLayer.addView(bgTheme); bgLayer.addView(bgBlur);
        FrameLayout.LayoutParams over = new FrameLayout.LayoutParams(-1, -1);
        // oversize so parallax never shows edges
        over.setMargins(-dp(28), -dp(28), -dp(28), -dp(28));
        root.addView(bgLayer, over);
        kenBurns = ValueAnimator.ofFloat(1.0f, 1.08f);
        kenBurns.setDuration(24000);
        kenBurns.setRepeatMode(ValueAnimator.REVERSE);
        kenBurns.setRepeatCount(ValueAnimator.INFINITE);
        kenBurns.addUpdateListener(a -> {
            float s = (float) a.getAnimatedValue();
            bgLayer.setScaleX(s); bgLayer.setScaleY(s);
        });

        // Scrims, like Collapse's bottom/left gradient
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#99000000"), Color.TRANSPARENT, Color.parseColor("#33000000"), Color.parseColor("#E60E0F13")}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // Scrolling content
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), statusBarHeight() + dp(64), dp(16), dp(150));
        scroll.addView(col);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        scroll.getViewTreeObserver().addOnScrollChangedListener(() -> onScrolled(scroll.getScrollY()));

        // Hero: logo + title + bg pips
        int heroH = (int) (getResources().getDisplayMetrics().heightPixels * 0.38f);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.BOTTOM);
        hero.setMinimumHeight(heroH);
        logo = new ImageView(this);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        hero.addView(logo, new LinearLayout.LayoutParams(dp(260), dp(120)));
        gameTitle = text("", 15, Color.WHITE, true);
        gameTitle.setShadowLayer(dp(6), 0, dp(1), Color.BLACK);
        hero.addView(gameTitle);
        TextView tag = text("Streams from your cloud PC. No queue, no timer, no phone storage.", 13, MUTED, false);
        tag.setShadowLayer(dp(6), 0, dp(1), Color.BLACK);
        hero.addView(tag);
        bgPips = new LinearLayout(this);
        bgPips.setPadding(0, dp(10), 0, dp(14));
        hero.addView(bgPips);
        col.addView(hero);

        // Banner carousel
        carousel = new FrameLayout(this);
        rounded(carousel, 16);
        carousel.setBackground(glass(16));
        carouselImg = new ImageView(this);
        carouselImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        carousel.addView(carouselImg, new FrameLayout.LayoutParams(-1, -1));
        carouselPips = new LinearLayout(this);
        carouselPips.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, dp(22), Gravity.BOTTOM);
        carousel.addView(carouselPips, pl);
        final GestureDetector swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (banners.isEmpty() || Math.abs(vx) < Math.abs(vy)) return false;
                int dir = vx < 0 ? 1 : -1;
                showBanner((bannerIndex + dir + banners.size()) % banners.size(), dir);
                ui.removeCallbacks(rotateBanner); ui.postDelayed(rotateBanner, 8000);
                return true;
            }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (!banners.isEmpty()) openUrl(banners.get(bannerIndex)[1]);
                return true;
            }
        });
        carousel.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.getParent().requestDisallowInterceptTouchEvent(true);
            return swipe.onTouchEvent(e);
        });
        int cw = getResources().getDisplayMetrics().widthPixels - dp(32);
        col.addView(carousel, new LinearLayout.LayoutParams(-1, Math.round(cw * 0.45f)));

        // News panel with tabs
        LinearLayout news = panel(col, null);
        newsTabs = new LinearLayout(this);
        news.addView(newsTabs);
        String[][] tabs = {{"Events", "POST_TYPE_ACTIVITY"}, {"Notices", "POST_TYPE_ANNOUNCE"}, {"Info", "POST_TYPE_INFO"}};
        for (String[] t : tabs) {
            TextView tv = text(t[0], 14, Color.WHITE, true);
            tv.setPadding(dp(12), dp(6), dp(12), dp(6));
            tv.setTag(t[1]);
            tv.setOnClickListener(v -> { newsType = (String) v.getTag(); renderNews(); });
            newsTabs.addView(tv);
        }
        newsList = new LinearLayout(this);
        newsList.setOrientation(LinearLayout.VERTICAL);
        news.addView(newsList);
        socialRow = new LinearLayout(this);
        socialRow.setPadding(0, dp(10), 0, 0);
        HorizontalScrollView sh = new HorizontalScrollView(this);
        sh.setHorizontalScrollBarEnabled(false);
        sh.addView(socialRow);
        news.addView(sh);

        // Watch panel: Cloudstream + JebetrexxMC/extensions (YouTube, Twitch, Internet Archive...)
        LinearLayout watch = panel(col, "Watch while the host boots");
        TextView wt = text("Cloudstream with your extensions repo: YouTube, Twitch, Dailymotion, Internet Archive, Invidious.", 13, MUTED, false);
        watch.addView(wt);
        LinearLayout w1 = chipRow(watch), w2 = chipRow(watch);
        chip(w1, "Add repo to Cloudstream", v -> openCloudstreamRepo());
        chip(w1, "Get Cloudstream", v -> openUrl("https://github.com/recloudstream/cloudstream/releases/latest"));
        chip(w2, "Live on Twitch", v -> openUrl("https://www.twitch.tv/directory/category/" + twitchSlug()));
        chip(w2, "Arcade (browser)", v -> openUrl("https://" + owner().toLowerCase() + ".github.io/" + repoName() + "/arcade.html"));

        // No-PC cloud: official HoYoverse cloud and GeForce NOW stream the game from
        // their servers, so nothing is downloaded and no PC or VM is needed.
        LinearLayout np = panel(col, "Play without a PC");
        np.addView(text("Genshin Impact \u00b7 Cloud is HoYoverse's official cloud version (US, Canada and SEA, incl. Philippines). "
                + "GeForce NOW also streams HoYoverse games. Same account and saves.", 13, MUTED, false));
        LinearLayout n1 = chipRow(np), n2 = chipRow(np);
        chip(n1, "Genshin \u00b7 Cloud app", v -> openApp(CLOUD_GENSHIN_PKG));
        chip(n1, "Genshin \u00b7 Cloud web", v -> openUrl("https://cloudgenshin.hoyoverse.com/en-us"));
        chip(n2, "GeForce NOW", v -> openApp(GFN_PKG));
        chip(n2, "GeForce NOW web", v -> openUrl("https://play.geforcenow.com/"));

        // Cloud PC panel
        LinearLayout pc = panel(col, "Cloud PC");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        GradientDrawable dot = new GradientDrawable(); dot.setShape(GradientDrawable.OVAL); dot.setColor(MUTED);
        statusDot.setBackground(dot);
        row.addView(statusDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        statusText = text("Checking host...", 14, Color.WHITE, false);
        statusText.setPadding(dp(10), 0, 0, 0);
        row.addView(statusText);
        row.setOnClickListener(v -> refreshStatus());
        pc.addView(row);
        LinearLayout chips1 = chipRow(pc), chips2 = chipRow(pc), chips3 = chipRow(pc);
        chip(chips1, "Start host", v -> dispatch("start"));
        chip(chips1, "Stop host", v -> dispatch("stop"));
        chip(chips2, "Wake my PC", v -> wake());
        chip(chips2, "All hosts", v -> openMoonlight());
        chip(chips3, "Private network", v -> openTailscale());
        chip(chips3, "Setup", v -> setup());

        // Playtime panel
        LinearLayout pt = panel(col, "Playtime");
        playtimeText = text("", 14, Color.WHITE, false);
        pt.addView(playtimeText);

        TextView foot = text("CloudSync. Launcher style from Collapse Launcher (MIT). Streaming by Moonlight (GPL-3.0). "
                + "Game art and news come from the official HoYoPlay feed. Not affiliated with HoYoverse.", 11, MUTED, false);
        foot.setPadding(dp(4), dp(18), dp(4), 0);
        foot.setOnClickListener(v -> openUrl("https://" + owner().toLowerCase() + ".github.io/" + repoName() + "/"));
        col.addView(foot);

        // Top bar: wordmark + game switcher (Collapse's game selector)
        topBg = new View(this);
        topBg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#F00E0F13"), Color.parseColor("#D90E0F13"), Color.TRANSPARENT}));
        topBg.setAlpha(0f);
        root.addView(topBg, new FrameLayout.LayoutParams(-1, statusBarHeight() + dp(80), Gravity.TOP));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), statusBarHeight() + dp(8), dp(12), dp(8));
        TextView brand = text("Cloud", 20, Color.WHITE, true);
        TextView brand2 = text("Sync", 20, ACCENT, true);
        top.addView(brand); top.addView(brand2);
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        gameBar = new LinearLayout(this);
        gameBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        gameBar.setBackground(glass(22));
        for (int i = 0; i < GAMES.length; i++) {
            final int gi = i;
            ImageView ic = new ImageView(this);
            ic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            rounded(ic, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.setMargins(dp(3), 0, dp(3), 0);
            ic.setOnClickListener(v -> selectGame(gi));
            gameBar.addView(ic, lp);
        }
        top.addView(gameBar);
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Bottom bar: Collapse-style split Start Game button
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        bottom.setPadding(dp(16), dp(10), dp(16), dp(18));
        LinearLayout split = new LinearLayout(this);
        split.setElevation(dp(10));
        GradientDrawable sb = new GradientDrawable(); sb.setColor(ACCENT); sb.setCornerRadius(dp(14));
        split.setBackground(sb);
        rounded(split, 14);
        TextView startBtn = text("Start Game", 18, INK, true);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding(dp(30), 0, dp(26), 0);
        startBtn.setOnClickListener(v -> play());
        split.addView(startBtn, new LinearLayout.LayoutParams(-2, dp(56)));
        View div = new View(this); div.setBackgroundColor(Color.parseColor("#33000000"));
        split.addView(div, new LinearLayout.LayoutParams(dp(1), dp(32)));
        TextView more = text("\u25BE", 18, INK, true);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(16), 0, dp(16), 0);
        more.setOnClickListener(this::showMoreMenu);
        split.addView(more, new LinearLayout.LayoutParams(-2, dp(56)));
        press(startBtn);
        bottom.addView(split);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        renderGameBar();
        return root;
    }

    private ImageView bgImage() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        return v;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private GradientDrawable glass(int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(GLASS);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), GLASS_LINE);
        return g;
    }

    private void rounded(View v, final int radius) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(radius));
            }
        });
        v.setClipToOutline(true);
    }

    private LinearLayout panel(LinearLayout parent, String title) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14), dp(12), dp(14), dp(14));
        p.setBackground(glass(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        parent.addView(p, lp);
        if (title != null) {
            TextView t = text(title, 13, MUTED, true);
            t.setPadding(0, 0, 0, dp(8));
            p.addView(t);
        }
        // fade + rise in as it scrolls into view
        p.setAlpha(0f); p.setTranslationY(dp(24));
        p.animate().alpha(1f).translationY(0).setStartDelay(150L * parent.getChildCount())
                .setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
        return p;
    }

    private LinearLayout chipRow(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(8), 0, 0);
        parent.addView(r);
        return r;
    }

    private void chip(LinearLayout row, String label, View.OnClickListener l) {
        TextView c = text(label, 14, Color.WHITE, true);
        c.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#22FFFFFF"));
        g.setCornerRadius(dp(12));
        c.setBackground(g);
        c.setOnClickListener(l);
        press(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(row.getChildCount() == 0 ? 0 : dp(8), 0, 0, 0);
        row.addView(c, lp);
    }

    private void press(View v) {
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
    }

    private void pips(LinearLayout box, int count, int active) {
        box.removeAllViews();
        if (count < 2) return;
        for (int i = 0; i < count; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(3));
            g.setColor(i == active ? ACCENT : Color.parseColor("#80FFFFFF"));
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(i == active ? dp(18) : dp(6), dp(6));
            lp.setMargins(dp(3), 0, dp(3), 0);
            box.addView(d, lp);
        }
    }

    // ------------------------------------------------------------------ effects

    private void onScrolled(int y) {
        scrollY = y;
        float k = Math.min(1f, y / (float) dp(260));
        bgBlur.setAlpha(k);                       // blur in as panels slide over the art
        if (topBg != null) topBg.setAlpha(Math.min(1f, y / (float) dp(120)));
        logo.setTranslationY(y * 0.35f);          // hero logo drifts slower than content
        logo.setAlpha(1f - Math.min(1f, y / (float) dp(220)));
        applyParallax();
    }

    private void applyParallax() {
        float shift = dp(14);
        float sx = -tiltX * shift / 9.8f, sy = tiltY * shift / 9.8f;
        float sc = -scrollY * 0.25f;
        bgA.setTranslationX(sx); bgA.setTranslationY(sy + sc);
        bgB.setTranslationX(sx); bgB.setTranslationY(sy + sc);
        bgBlur.setTranslationX(sx); bgBlur.setTranslationY(sy + sc);
        bgTheme.setTranslationX(sx * 2.2f); bgTheme.setTranslationY(sy * 2.2f + sc * 1.3f); // character layer moves more
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        tiltX += (e.values[0] - tiltX) * 0.08f;   // low-pass for smooth drift
        tiltY += (e.values[1] - 5f - tiltY) * 0.08f;
        tiltX = Math.max(-6f, Math.min(6f, tiltX));
        tiltY = Math.max(-6f, Math.min(6f, tiltY));
        applyParallax();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }

    private void showBackground(int i) {
        if (backgrounds.isEmpty()) return;
        bgIndex = i;
        pips(bgPips, backgrounds.size(), i);
        final String[] b = backgrounds.get(i);
        image(b[0], 1600, bmp -> {
            final ImageView in = bgFront ? bgA : bgB, out = bgFront ? bgB : bgA;
            bgFront = !bgFront;
            in.setImageBitmap(bmp);
            in.animate().alpha(1f).setDuration(900).start();
            out.animate().alpha(0f).setDuration(900).start();
            setBlur(bmp);
        });
        if (b[1] != null && !b[1].isEmpty()) {
            image(b[1], 1600, bmp -> {
                bgTheme.setAlpha(0f); bgTheme.setImageBitmap(bmp);
                bgTheme.animate().alpha(1f).setDuration(900).start();
            });
        } else {
            bgTheme.animate().alpha(0f).setDuration(400).start();
        }
    }

    private void setBlur(Bitmap src) {
        if (Build.VERSION.SDK_INT >= 31) {
            bgBlur.setImageBitmap(src);
            bgBlur.setRenderEffect(RenderEffect.createBlurEffect(dp(24), dp(24), Shader.TileMode.CLAMP));
        } else {
            // Cheap blur: downscale then upscale with filtering
            int w = Math.max(8, src.getWidth() / 24), h = Math.max(8, src.getHeight() / 24);
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            bgBlur.setImageBitmap(Bitmap.createScaledBitmap(small, w * 4, h * 4, true));
        }
        bgBlur.setColorFilter(Color.parseColor("#55000000"));
    }

    private void showBanner(int i, int dir) {
        if (banners.isEmpty()) return;
        bannerIndex = i;
        pips(carouselPips, banners.size(), i);
        image(banners.get(i)[0], 1200, bmp -> {
            carouselImg.setTranslationX(dir * dp(40));
            carouselImg.setAlpha(0f);
            carouselImg.setImageBitmap(bmp);
            carouselImg.animate().translationX(0).alpha(1f).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator()).start();
        });
    }

    // ------------------------------------------------------------------ feeds

    private void loadFeeds() {
        io.execute(() -> {
            try {
                basicInfo = new JSONObject(http("GET", API + "getAllGameBasicInfo?launcher_id=" + LAUNCHER_ID + "&language=en-us", null, null)).getJSONObject("data");
                gamesInfo = new JSONObject(http("GET", API + "getGames?launcher_id=" + LAUNCHER_ID + "&language=en-us", null, null)).getJSONObject("data");
            } catch (Exception e) {
                toast("Offline: game art unavailable");
            }
            ui.post(() -> { renderGameBar(); selectGame(game); });
        });
    }

    private JSONObject gameDisplay(int i) {
        if (gamesInfo == null) return null;
        JSONArray a = gamesInfo.optJSONArray("games");
        for (int k = 0; a != null && k < a.length(); k++) {
            JSONObject g = a.optJSONObject(k);
            if (GAMES[i][0].equals(g.optString("id"))) return g.optJSONObject("display");
        }
        return null;
    }

    private void renderGameBar() {
        for (int i = 0; i < gameBar.getChildCount(); i++) {
            ImageView ic = (ImageView) gameBar.getChildAt(i);
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(10));
            ring.setColor(Color.parseColor("#33FFFFFF"));
            if (i == game) ring.setStroke(dp(2), ACCENT);
            ic.setBackground(ring);
            ic.setPadding(i == game ? dp(2) : 0, i == game ? dp(2) : 0, i == game ? dp(2) : 0, i == game ? dp(2) : 0);
            ic.setAlpha(i == game ? 1f : 0.7f);
            JSONObject d = gameDisplay(i);
            if (d != null) {
                String url = d.optJSONObject("icon") != null ? d.optJSONObject("icon").optString("url") : "";
                if (!url.isEmpty()) image(url, 128, ic::setImageBitmap);
            }
        }
    }

    private void selectGame(int i) {
        game = i;
        prefs.edit().putInt("game", i).apply();
        renderGameBar();
        updatePlaytime();
        JSONObject d = gameDisplay(i);
        String name = d != null ? d.optString("name", GAMES[i][2]) : GAMES[i][2];
        gameTitle.setText(name);
        logo.setImageBitmap(null);
        if (d != null && d.optJSONObject("logo") != null) {
            image(d.optJSONObject("logo").optString("url"), 700, bmp -> {
                logo.setAlpha(0f); logo.setTranslationY(dp(12));
                logo.setImageBitmap(bmp);
                logo.animate().alpha(1f).translationY(0).setDuration(600).start();
            });
        }
        // backgrounds
        backgrounds.clear();
        if (basicInfo != null) {
            JSONArray list = basicInfo.optJSONArray("game_info_list");
            for (int k = 0; list != null && k < list.length(); k++) {
                JSONObject g = list.optJSONObject(k);
                if (!GAMES[i][0].equals(g.optJSONObject("game").optString("id"))) continue;
                JSONArray bgs = g.optJSONArray("backgrounds");
                for (int b = 0; bgs != null && b < bgs.length(); b++) {
                    JSONObject o = bgs.optJSONObject(b);
                    String bg = o.optJSONObject("background") != null ? o.optJSONObject("background").optString("url") : "";
                    String th = o.optJSONObject("theme") != null ? o.optJSONObject("theme").optString("url") : "";
                    if (!bg.isEmpty()) backgrounds.add(new String[]{bg, th});
                }
            }
        }
        if (backgrounds.isEmpty() && d != null && d.optJSONObject("background") != null) {
            backgrounds.add(new String[]{d.optJSONObject("background").optString("url"), ""});
        }
        showBackground(0);
        // banners, posts, social
        banners.clear(); posts = new JSONArray(); renderNews(); socialRow.removeAllViews(); pips(carouselPips, 0, 0);
        carouselImg.setImageBitmap(null);
        io.execute(() -> {
            try {
                JSONObject c = new JSONObject(http("GET", API + "getGameContent?launcher_id=" + LAUNCHER_ID
                        + "&game_id=" + GAMES[i][0] + "&language=en-us", null, null))
                        .getJSONObject("data").getJSONObject("content");
                final List<String[]> bn = new ArrayList<>();
                JSONArray ba = c.optJSONArray("banners");
                for (int k = 0; ba != null && k < ba.length(); k++) {
                    JSONObject im = ba.optJSONObject(k).optJSONObject("image");
                    if (im != null) bn.add(new String[]{im.optString("url"), im.optString("link")});
                }
                final JSONArray ps = c.optJSONArray("posts");
                final JSONArray so = c.optJSONArray("social_media_list");
                ui.post(() -> {
                    if (game != i) return;
                    banners.clear(); banners.addAll(bn); showBanner(0, 1);
                    posts = ps != null ? ps : new JSONArray(); renderNews();
                    renderSocial(so);
                });
            } catch (Exception ignored) { }
        });
    }

    private boolean hasType(String t) {
        for (int i = 0; i < posts.length(); i++) if (t.equals(posts.optJSONObject(i).optString("type"))) return true;
        return false;
    }

    private void renderNews() {
        if (posts.length() > 0 && !hasType(newsType)) {
            for (String t : new String[]{"POST_TYPE_ACTIVITY", "POST_TYPE_ANNOUNCE", "POST_TYPE_INFO"}) {
                if (hasType(t)) { newsType = t; break; }
            }
        }
        for (int i = 0; i < newsTabs.getChildCount(); i++) {
            TextView t = (TextView) newsTabs.getChildAt(i);
            boolean on = newsType.equals(t.getTag());
            t.setTextColor(on ? INK : Color.WHITE);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(10));
            g.setColor(on ? ACCENT : Color.TRANSPARENT);
            t.setBackground(g);
        }
        newsList.removeAllViews();
        int shown = 0;
        for (int i = 0; i < posts.length() && shown < 5; i++) {
            final JSONObject p = posts.optJSONObject(i);
            if (!newsType.equals(p.optString("type"))) continue;
            LinearLayout r = new LinearLayout(this);
            r.setPadding(dp(4), dp(10), dp(4), dp(10));
            TextView t = text(p.optString("title"), 14, Color.WHITE, false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView d = text(p.optString("date"), 12, MUTED, false);
            d.setPadding(dp(10), 0, 0, 0);
            r.addView(d);
            r.setOnClickListener(v -> openUrl(p.optString("link")));
            r.setAlpha(0f); r.setTranslationX(dp(16));
            r.animate().alpha(1f).translationX(0).setStartDelay(60L * shown).setDuration(320).start();
            newsList.addView(r);
            shown++;
        }
        if (shown == 0) {
            TextView t = text(posts.length() == 0 ? "Loading news..." : "Nothing here right now.", 13, MUTED, false);
            t.setPadding(dp(4), dp(10), 0, dp(6));
            newsList.addView(t);
        }
    }

    private void renderSocial(JSONArray so) {
        socialRow.removeAllViews();
        for (int k = 0; so != null && k < so.length(); k++) {
            JSONObject s = so.optJSONObject(k);
            JSONObject icon = s.optJSONObject("icon");
            JSONArray links = s.optJSONArray("links");
            if (icon == null || links == null || links.length() == 0) continue;
            final String link = links.optJSONObject(0).optString("link");
            ImageView iv = new ImageView(this);
            iv.setPadding(dp(6), dp(6), dp(6), dp(6));
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(Color.parseColor("#22FFFFFF"));
            iv.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.setMargins(0, 0, dp(8), 0);
            socialRow.addView(iv, lp);
            iv.setOnClickListener(v -> openUrl(link));
            image(icon.optString("url"), 96, iv::setImageBitmap);
        }
    }

    private void updatePlaytime() {
        if (playtimeText == null) return;
        long m = prefs.getLong("playtime_" + GAMES[game][1], 0);
        long last = prefs.getLong("last_session_" + GAMES[game][1], 0);
        playtimeText.setText((m / 60) + "h " + (m % 60) + "m total" + (last > 0 ? "  ·  last session " + last + "m" : ""));
    }

    // ------------------------------------------------------------------ images

    private interface BitmapCallback { void done(Bitmap b); }

    private void image(final String url, final int maxW, final BitmapCallback cb) {
        if (url == null || url.isEmpty()) return;
        Bitmap hit = cache.get(url);
        if (hit != null) { cb.done(hit); return; }
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(20000);
                byte[] data = readAll(c.getInputStream());
                c.disconnect();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int s = 1;
                while (o.outWidth / (s * 2) >= maxW) s *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = s;
                final Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o2);
                if (b == null) return;
                cache.put(url, b);
                ui.post(() -> cb.done(b));
            } catch (Exception ignored) { }
        });
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    // ------------------------------------------------------------------ settings

    private String repo() { return prefs.getString("repo", "JebetrexxMC/CloudGaming").trim(); }
    private String owner() { String r = repo(); int i = r.indexOf('/'); return i > 0 ? r.substring(0, i) : r; }
    private String repoName() { String r = repo(); int i = r.indexOf('/'); return i > 0 ? r.substring(i + 1) : r; }

    private void toast(final String s) { ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }

    private void showMoreMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Start host");
        m.getMenu().add(0, 2, 1, "Stop host");
        m.getMenu().add(0, 3, 2, "Wake my PC");
        m.getMenu().add(0, 4, 3, "All hosts (Moonlight)");
        m.getMenu().add(0, 5, 4, "Private network");
        m.getMenu().add(0, 6, 5, "Website and console");
        m.getMenu().add(0, 7, 6, "Setup");
        m.getMenu().add(0, 8, 7, "Genshin \u00b7 Cloud (no PC)");
        m.getMenu().add(0, 9, 8, "GeForce NOW (no PC)");
        m.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1: dispatch("start"); break;
                case 2: dispatch("stop"); break;
                case 3: wake(); break;
                case 4: openMoonlight(); break;
                case 5: openTailscale(); break;
                case 6: openUrl("https://" + owner().toLowerCase() + ".github.io/" + repoName() + "/"); break;
                case 7: setup(); break;
                case 8: openApp(CLOUD_GENSHIN_PKG); break;
                case 9: openApp(GFN_PKG); break;
            }
            return true;
        });
        m.show();
    }

    private void setup() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText repo = field(box, "GitHub repo (owner/name)", "repo", false);
        final EditText token = field(box, "GitHub token (Actions: write) for start/stop", "token", true);
        final EditText host = field(box, "Host address (IP, Tailscale or DNS name)", "host", false);
        final EditText hours = field(box, "Auto-stop after hours (0 = never)", "hours", false);
        final EditText mac = field(box, "Wake-on-LAN MAC (home PC)", "mac", false);
        final EditText bcast = field(box, "Wake-on-LAN broadcast or host", "bcast", false);
        if (hours.getText().length() == 0) hours.setText("0");
        if (bcast.getText().length() == 0) bcast.setText("255.255.255.255");
        ScrollView s = new ScrollView(this);
        s.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("CloudSync setup")
                .setView(s)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit()
                            .putString("repo", repo.getText().toString().trim())
                            .putString("token", token.getText().toString().trim())
                            .putString("host", host.getText().toString().trim())
                            .putString("hours", hours.getText().toString().trim())
                            .putString("mac", mac.getText().toString().trim())
                            .putString("bcast", bcast.getText().toString().trim())
                            .apply();
                    refreshStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText field(LinearLayout parent, String hint, String key, boolean secret) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setPadding(0, dp(10), 0, 0);
        parent.addView(label);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(prefs.getString(key, key.equals("repo") ? "JebetrexxMC/CloudGaming" : ""));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        parent.addView(e);
        return e;
    }

    // ------------------------------------------------------------------ host status / control

    private void setStatus(String s, int color) {
        statusText.setText(s);
        GradientDrawable dot = new GradientDrawable(); dot.setShape(GradientDrawable.OVAL); dot.setColor(color);
        statusDot.setBackground(dot);
    }

    private void refreshStatus() {
        setStatus("Checking host...", MUTED);
        io.execute(() -> {
            try {
                JSONObject st = new JSONObject(http("GET", "https://raw.githubusercontent.com/" + repo()
                        + "/live-status/status.json?t=" + System.currentTimeMillis(), null, null));
                JSONObject h = st.optJSONObject("host");
                final String s; final int c;
                if (h == null || !h.optBoolean("configured")) {
                    s = prefs.getString("host", "").isEmpty() ? "Host not set up. Open Setup." : "Host: " + prefs.getString("host", "");
                    c = Color.parseColor("#F0B43C");
                } else if (h.optBoolean("online")) {
                    s = "Online" + (h.isNull("ms") ? "" : " · " + h.optInt("ms") + " ms") + ". Ready to play.";
                    c = Color.parseColor("#4DE1A0");
                } else {
                    s = "Offline. Start host, wait 2 min, then Start Game.";
                    c = Color.parseColor("#FF6B6B");
                }
                ui.post(() -> setStatus(s, c));
            } catch (Exception e) {
                ui.post(() -> setStatus("Status unavailable. Tap to retry.", MUTED));
            }
        });
    }

    private void dispatch(String action) {
        String token = prefs.getString("token", "");
        if (token.isEmpty()) {
            toast("Add a GitHub token in Setup first (fine-grained, this repo, Actions: read and write).");
            setup();
            return;
        }
        String hours = prefs.getString("hours", "0").replaceAll("[^0-9]", "");
        if (hours.isEmpty()) hours = "0";
        final String json = "{\"ref\":\"main\",\"inputs\":{\"action\":\"" + action
                + "\",\"auto_stop_hours\":\"" + hours + "\"}}";
        toast(action.equals("start") ? "Starting host..." : "Stopping host...");
        io.execute(() -> {
            try {
                http("POST", "https://api.github.com/repos/" + repo()
                        + "/actions/workflows/vm-control.yml/dispatches", json, token);
                toast(action.equals("start")
                        ? "Host is starting. Give it 2-3 minutes, then tap Start Game."
                        : "Host is stopping. The disk and game files are kept.");
            } catch (Exception e) {
                toast("GitHub said: " + e.getMessage());
            }
        });
    }

    private String http(String method, String url, String body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "CloudSync-Android");
        if (token != null) {
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Accept", "application/vnd.github+json");
        }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in != null ? new String(readAll(in), StandardCharsets.UTF_8) : "";
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + text);
        return text;
    }

    private void play() {
        final String host = prefs.getString("host", "");
        final String name = gameTitle.getText().toString();
        if (host.isEmpty()) {
            toast("Enter your host address in Setup (the Elastic IP, or the Tailscale name).");
            setup();
            return;
        }
        toast("Connecting to " + host + "...");
        bindService(new Intent(this, ComputerManagerService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName cn, IBinder binder) {
                final ServiceConnection self = this;
                final ComputerManagerService.ComputerManagerBinder b = (ComputerManagerService.ComputerManagerBinder) binder;
                io.execute(() -> {
                    boolean ok = false;
                    try {
                        String h = host;
                        int port = NvHTTP.DEFAULT_HTTP_PORT;
                        int colon = h.lastIndexOf(':');
                        if (colon > 0 && h.indexOf(':') == colon) {
                            port = Integer.parseInt(h.substring(colon + 1));
                            h = h.substring(0, colon);
                        }
                        ComputerDetails d = new ComputerDetails();
                        d.manualAddress = new ComputerDetails.AddressTuple(h, port);
                        ok = b.addComputerBlocking(d);
                    } catch (Exception ignored) { }
                    final boolean fok = ok;
                    ui.post(() -> {
                        try { unbindService(self); } catch (Exception ignored) { }
                        toast(fok ? "Host ready. Tap it (pair once with the Apollo PIN), then pick " + name + "."
                                  : "Host not reachable yet. Is it started, and is your VPN on?");
                        prefs.edit().putLong("session_start", System.currentTimeMillis()).apply();
                        openMoonlight();
                    });
                });
            }
            @Override public void onServiceDisconnected(ComponentName cn) { }
        }, Context.BIND_AUTO_CREATE);
    }

    private void openMoonlight() { startActivity(new Intent(this, PcView.class)); }

    private void wake() {
        final String mac = prefs.getString("mac", "").replaceAll("[^0-9A-Fa-f]", "");
        if (mac.length() != 12) {
            toast("Enter your PC's MAC address in Setup, e.g. AA:BB:CC:DD:EE:FF.");
            setup();
            return;
        }
        final String target = prefs.getString("bcast", "255.255.255.255");
        io.execute(() -> {
            try {
                byte[] m = new byte[6];
                for (int i = 0; i < 6; i++) m[i] = (byte) Integer.parseInt(mac.substring(i * 2, i * 2 + 2), 16);
                byte[] pkt = new byte[6 + 16 * 6];
                for (int i = 0; i < 6; i++) pkt[i] = (byte) 0xff;
                for (int i = 6; i < pkt.length; i += 6) System.arraycopy(m, 0, pkt, i, 6);
                InetAddress addr = InetAddress.getByName(target.isEmpty() ? "255.255.255.255" : target);
                try (DatagramSocket s = new DatagramSocket()) {
                    s.setBroadcast(true);
                    for (int port : new int[]{9, 7}) s.send(new DatagramPacket(pkt, pkt.length, addr, port));
                }
                toast("Wake packet sent to " + target + ".");
            } catch (Exception e) {
                toast("Wake failed: " + e.getMessage());
            }
        });
    }

    private static final String EXT_REPO = "raw.githubusercontent.com/JebetrexxMC/extensions/master/repo.json";

    private void openCloudstreamRepo() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("cloudstreamrepo://" + EXT_REPO)));
        } catch (ActivityNotFoundException e) {
            toast("Install Cloudstream first, then tap this again.");
            openUrl("https://github.com/recloudstream/cloudstream/releases/latest");
        }
    }

    private String twitchSlug() {
        switch (game) {
            case 1: return "honkai-star-rail";
            case 2: return "zenless-zone-zero";
            case 3: return "honkai-impact-3rd";
            default: return "genshin-impact";
        }
    }

    private void openTailscale() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.tailscale.ipn");
        if (launch != null) { startActivity(launch); return; }
        new AlertDialog.Builder(this)
                .setTitle("Private network")
                .setMessage("Only needed if your host is private (Tailscale, Headscale). If your host uses a public IP, "
                        + "skip this. Android allows one VPN app at a time, so Tailscale stays a separate app.")
                .setPositiveButton("Get Tailscale", (d, w) -> openUrl("market://details?id=com.tailscale.ipn"))
                .setNegativeButton("Close", null)
                .show();
    }

    /** Launch an installed app, otherwise open its Play Store page. */
    private void openApp(String pkg) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) { startActivity(launch); return; }
        openUrl("market://details?id=" + pkg);
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            if (url.startsWith("market://")) openUrl("https://play.google.com/store/apps/" + url.substring("market://".length()));
            else toast("No app can open " + url);
        }
    }
}
