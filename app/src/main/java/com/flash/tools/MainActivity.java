package com.flash.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    static final String GRAY = "#8E8E93", GOLD = "#FFD479", SOFT = "#E6DDFF";
    static final String API = "https://api.github.com";
    static final String JSON = "application/vnd.github+json";
    static final String RAW = "application/vnd.github.raw+json";
    static final String[][] CARDS = {
            {"🔑", "ادخل بالتوكن", "حط توكن GitHub مرة واحدة. بيتخزن مشفّر على جهازك ومحدش يقدر ياخده."},
            {"☰", "افتح القائمة الجانبية", "هتلاقي مستودعاتك متقسمة: حسابك لوحده وكل فريق انت عضو فيه لوحده."},
            {"📂", "ادخل أي مستودع", "تصفّح الملفات والمجلدات. اضغط على الملف عشان تعدله، واضغط مطوّل عشان تحدد أو تغيّر الاسم."},
            {"🛠️", "أدوات قوية", "رفع ملفات، فك ضغط zip، ضغط، تحميل المستودع كامل، وحذف مجلدات كاملة."},
            {"🛡️", "على حسب صلاحيات التوكن", "الأدوات بتتفعّل حسب صلاحيتك على كل مستودع: مدير، كتابة، أو قراءة فقط."}
    };

    interface Task { void run() throws Exception; }

    static class HttpEx extends Exception {
        final int code;
        HttpEx(int c, String m) { super(m); code = c; }
    }

    String BG, CARD, TXT, SEP, BLUE, RED;
    boolean dark;
    FrameLayout host, overlay, drawer;
    LinearLayout panel, cardView, dotsBox;
    View scrim;
    TextView overlayText, clockT, clockD;
    EditText edit;
    String origTxt = "", editPath = "";
    final ExecutorService ex = Executors.newSingleThreadExecutor();
    final Handler hnd = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    String token, repo = "", path = "", branch = "main", me = "";
    volatile String scopes, lastScopes;
    List<JSONObject> items = new ArrayList<>();
    List<JSONObject> repos = new ArrayList<>();
    JSONObject cur;
    final Set<String> sel = new LinkedHashSet<>();
    final Set<String> openGroups = new HashSet<>();
    int screen = 0, cardIdx = 0, panelW;
    boolean busy = false, loaded = false, canWrite = false, drawerOpen = false, gInit = false;
    boolean selMode = false, animating = false;
    byte[] pendingSave;
    final SimpleDateFormat tf = new SimpleDateFormat("hh:mm:ss a", Locale.forLanguageTag("ar"));
    final SimpleDateFormat df = new SimpleDateFormat("EEEE d MMMM", Locale.forLanguageTag("ar"));

    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (screen == 1 && clockT != null) updateClock();
            hnd.postDelayed(this, 1000);
        }
    };
    final Runnable adv = new Runnable() {
        @Override public void run() {
            if (screen == 1 && cardView != null && !drawerOpen && !busy && !animating) slideOut(1);
            hnd.postDelayed(this, 4500);
        }
    };

    // ---------- helpers ----------
    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable rect(String c, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(c));
        g.setCornerRadius(dp(r));
        return g;
    }

    GradientDrawable legend(int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1E1147, 0xFF4B2A8A, 0xFF2A0F5C});
        g.setCornerRadius(dp(r));
        g.setStroke(dp(2), 0x99FFD479);
        return g;
    }

    // كرت شفاف زجاجي
    GradientDrawable glass(int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0x26FFFFFF : 0x40FFFFFF);
        g.setCornerRadius(dp(r));
        g.setStroke(dp(1), dark ? 0x80FFD479 : 0x99B8860B);
        return g;
    }

    TextView tv(String s, float sz, String c, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sz);
        t.setTextColor(Color.parseColor(c));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout hrow(View... vs) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        for (View v : vs) l.addView(v);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    View gap(int d) {
        View v = new View(this);
        v.setLayoutParams(lp(1, dp(d)));
        return v;
    }

    void ui(Runnable r) { runOnUiThread(r); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    String err(Exception e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }

    AlertDialog.Builder ab() {
        return new AlertDialog.Builder(this, dark ? AlertDialog.THEME_DEVICE_DEFAULT_DARK : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
    }

    void alert(String t, String m) { ab().setTitle(t).setMessage(m).setPositiveButton("حسناً", null).show(); }
    void busy(String m) { busy = true; overlayText.setText(m); overlay.setVisibility(View.VISIBLE); }
    void idle() { busy = false; overlay.setVisibility(View.GONE); }
    void status(String s) { ui(() -> overlayText.setText(s)); }

    View ripple(View v) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22888888), null, new ColorDrawable(Color.BLACK)));
        return v;
    }

    TextView link(String s, String c, View.OnClickListener l) {
        TextView t = tv(s, 16, c, false);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        ripple(t);
        t.setOnClickListener(l);
        return t;
    }

    TextView menuBtn() {
        TextView t = link("☰", BLUE, v -> openDrawer());
        t.setTextSize(26);
        return t;
    }

    View row(String icon, String title, String sub, View trail, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView ic = tv(icon, 22, TXT, false);
        ic.setPaddingRelative(0, 0, dp(12), 0);
        r.addView(ic);
        LinearLayout m = vbox();
        TextView t = tv(title, 16, TXT, false);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        m.addView(t);
        if (sub != null) m.addView(tv(sub, 12, GRAY, false));
        r.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
        if (trail != null) r.addView(trail);
        ripple(r);
        r.setOnClickListener(l);
        return r;
    }

    LinearLayout card(List<View> rows) {
        LinearLayout c = vbox();
        c.setBackground(rect(CARD, 14));
        c.setClipToOutline(true);
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                View d = new View(this);
                d.setBackgroundColor(Color.parseColor(SEP));
                LinearLayout.LayoutParams p = lp(-1, 1);
                p.setMarginStart(dp(50));
                c.addView(d, p);
            }
            c.addView(rows.get(i));
        }
        return c;
    }

    LinearLayout page(String title, View start, View end, View content, View bottom) {
        LinearLayout root = vbox();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(12), 0);
        if (start != null) top.addView(start);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (end != null) top.addView(end);
        root.addView(top);
        TextView t = tv(title, 30, TXT, true);
        t.setPadding(dp(20), dp(2), dp(20), dp(8));
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(t);
        ScrollView sv = new ScrollView(this);
        LinearLayout wrap = vbox();
        wrap.setPadding(dp(16), dp(8), dp(16), dp(16));
        wrap.addView(content);
        sv.addView(wrap);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        if (bottom != null) root.addView(bottom);
        return root;
    }

    void setScreen(View v) {
        host.removeAllViews();
        host.addView(v, new FrameLayout.LayoutParams(-1, -1));
        host.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    void applyTheme() {
        dark = prefs.getBoolean("dark", false);
        BG = dark ? "#000000" : "#F2F2F7";
        CARD = dark ? "#1C1C1E" : "#FFFFFF";
        TXT = dark ? "#FFFFFF" : "#000000";
        SEP = dark ? "#38383A" : "#D1D1D6";
        BLUE = dark ? "#0A84FF" : "#007AFF";
        RED = dark ? "#FF453A" : "#FF3B30";
        getWindow().setStatusBarColor(Color.parseColor(BG));
        getWindow().setNavigationBarColor(Color.parseColor(CARD));
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0
                : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        host.setBackgroundColor(Color.parseColor(BG));
        overlay.setBackgroundColor(dark ? 0xCC000000 : 0xCCF2F2F7);
    }

    // ---------- lifecycle ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ft", MODE_PRIVATE);
        host = new FrameLayout(this);
        host.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        overlay = new FrameLayout(this);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);
        LinearLayout ob = vbox();
        ob.setGravity(Gravity.CENTER);
        ob.addView(new ProgressBar(this));
        overlayText = tv("", 14, GRAY, false);
        overlayText.setGravity(Gravity.CENTER);
        overlayText.setPadding(dp(20), dp(10), dp(20), 0);
        ob.addView(overlayText);
        overlay.addView(ob, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        drawer = new FrameLayout(this);
        drawer.setVisibility(View.GONE);
        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setOnClickListener(v -> closeDrawer());
        drawer.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        panel = vbox();
        panelW = Math.min(dp(330), (int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
        drawer.addView(panel, new FrameLayout.LayoutParams(panelW, -1, Gravity.START));
        applyTheme();
        setContentView(host);
        token = loadToken();
        if (token == null) showLogin(); else showHome();
        hnd.post(tick);
        hnd.postDelayed(adv, 4500);
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        if (drawerOpen) { closeDrawer(); return; }
        if (screen == 3) { closeEditor(); return; }
        if (screen == 2) {
            if (selMode) exitSel();
            else goBack();
            return;
        }
        super.onBackPressed();
    }

    void goBack() {
        if (busy) return;
        if (path.isEmpty()) { showHome(); return; }
        int i = path.lastIndexOf('/');
        path = i < 0 ? "" : path.substring(0, i);
        sel.clear();
        selMode = false;
        work("جاري التحميل...", null);
    }

    // ---------- login / account ----------
    void showLogin() {
        screen = 0;
        LinearLayout c = vbox();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(28), dp(70), dp(28), dp(28));
        TextView logo = tv("⚡", 54, "#FFFFFF", false);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A84FF, 0xFFBF5AF2});
        g.setCornerRadius(dp(26));
        logo.setBackground(g);
        logo.setElevation(dp(10));
        c.addView(logo, lp(dp(104), dp(104)));
        TextView h = tv("Flash Tools", 32, TXT, true);
        h.setPadding(0, dp(22), 0, dp(6));
        c.addView(h);
        TextView s = tv("أدوات قوية لإدارة ملفات حسابك على GitHub", 15, GRAY, false);
        s.setGravity(Gravity.CENTER);
        c.addView(s);
        final EditText et = new EditText(this);
        et.setHint("ضع توكن GitHub هنا");
        et.setHintTextColor(Color.parseColor(GRAY));
        et.setTextColor(Color.parseColor(TXT));
        et.setSingleLine();
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setBackground(rect(CARD, 14));
        et.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams ep = lp(-1, -2);
        ep.topMargin = dp(30);
        c.addView(et, ep);
        TextView go = tv("دخول", 17, "#FFFFFF", true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(rect(BLUE, 14));
        go.setPadding(0, dp(14), 0, dp(14));
        go.setOnClickListener(v -> {
            final String t = et.getText().toString().trim();
            if (t.isEmpty()) return;
            busy("جاري التحقق...");
            ex.execute(() -> {
                try {
                    token = t;
                    req("GET", API + "/user", JSON, null);
                    saveToken(t);
                    ui(() -> { idle(); showHome(); });
                } catch (Exception e) {
                    token = null;
                    ui(() -> { idle(); alert("فشل الدخول", err(e)); });
                }
            });
        });
        LinearLayout.LayoutParams gp = lp(-1, -2);
        gp.topMargin = dp(18);
        c.addView(go, gp);
        TextView n = tv("🔒 التوكن بيتخزن مشفّر على جهازك فقط", 12, GRAY, false);
        n.setPadding(0, dp(14), 0, 0);
        c.addView(n);
        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        setScreen(sv);
    }

    void logout() {
        prefs.edit().remove("t").apply();
        token = null; me = ""; cur = null; loaded = false;
        repos = new ArrayList<>();
        closeDrawer();
        showLogin();
    }

    void loadAccount() {
        busy("جاري تحميل حسابك...");
        ex.execute(() -> {
            try {
                JSONObject u = new JSONObject(str(req("GET", API + "/user", JSON, null)));
                me = u.optString("login");
                scopes = lastScopes;
                List<JSONObject> all = new ArrayList<>();
                for (int pg = 1; pg <= 5; pg++) {
                    JSONArray a = new JSONArray(str(req("GET", API + "/user/repos?per_page=100&page=" + pg
                            + "&sort=updated&affiliation=owner,collaborator,organization_member", JSON, null)));
                    for (int i = 0; i < a.length(); i++) all.add(a.getJSONObject(i));
                    if (a.length() < 100) break;
                }
                repos = all;
                loaded = true;
                ui(() -> { idle(); if (screen == 1) render(); });
            } catch (Exception e) {
                ui(() -> {
                    idle();
                    if (e instanceof HttpEx && ((HttpEx) e).code == 401) logout();
                    else alert("خطأ", err(e));
                });
            }
        });
    }

    // ---------- home ----------
    void showHome() {
        screen = 1;
        render();
        if (!loaded) loadAccount();
    }

    void render() {
        if (screen == 1) homeUI();
        else if (screen == 2) renderBrowser();
    }

    void updateClock() {
        Date d = new Date();
        clockT.setText("⚜ " + tf.format(d));
        clockD.setText(df.format(d));
    }

    void homeUI() {
        LinearLayout c = vbox();
        LinearLayout cl = new LinearLayout(this);
        cl.setGravity(Gravity.CENTER_VERTICAL);
        cl.setPadding(dp(18), dp(12), dp(18), dp(12));
        cl.setBackground(legend(20));
        clockT = tv("", 22, GOLD, true);
        clockD = tv("", 12, SOFT, false);
        cl.addView(clockT, new LinearLayout.LayoutParams(0, -2, 1));
        cl.addView(clockD);
        c.addView(cl);
        updateClock();
        c.addView(gap(16));

        cardView = vbox();
        cardView.setGravity(Gravity.CENTER);
        cardView.setPadding(dp(24), dp(24), dp(24), dp(24));
        cardView.setMinimumHeight(dp(300));
        cardView.setBackground(glass(28));
        fillCard(cardView, cardIdx);
        cardView.setOnTouchListener(new View.OnTouchListener() {
            float x0;
            @Override public boolean onTouch(View v, MotionEvent m) {
                if (animating) return true;
                switch (m.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = m.getRawX();
                        hnd.removeCallbacks(adv);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float mv = m.getRawX() - x0;
                        v.setTranslationX(mv);
                        v.setRotation(mv / 40f);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float d = m.getRawX() - x0;
                        if (Math.abs(d) > dp(100)) {
                            slideOut(d >= 0 ? 1 : -1);
                        } else {
                            v.animate().translationX(0).rotation(0).setDuration(200).start();
                        }
                        hnd.postDelayed(adv, 6000);
                        return true;
                }
                return false;
            }
        });
        c.addView(cardView, lp(-1, -2));

        dotsBox = new LinearLayout(this);
        dotsBox.setGravity(Gravity.CENTER);
        dotsBox.setPadding(0, dp(16), 0, dp(8));
        paintDots();
        c.addView(dotsBox, lp(-1, -2));

        // زر دخول حسابي تحت الكروت
        TextView enter = tv("👤  دخول حسابي", 17, "#FFFFFF", true);
        enter.setGravity(Gravity.CENTER);
        enter.setBackground(rect(BLUE, 14));
        enter.setPadding(0, dp(14), 0, dp(14));
        enter.setOnClickListener(v -> enterMyAccount());
        LinearLayout.LayoutParams ep = lp(-1, -2);
        ep.setMargins(dp(4), dp(4), dp(4), dp(4));
        c.addView(enter, ep);

        TextView info = tv(loaded ? repos.size() + " مستودع متاح لتوكنك — افتح القائمة ☰ واختار مستودع"
                : "جاري تحميل حسابك...", 13, GRAY, false);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(10), 0, dp(14));
        c.addView(info, lp(-1, -2));

        TextView dev = tv("تطوير AXEL ⚡", 12, GRAY, true);
        dev.setGravity(Gravity.CENTER);
        c.addView(dev, lp(-1, -2));

        TextView sig = tv("Flash Team </>", 14, GOLD, false);
        sig.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        sig.setGravity(Gravity.CENTER);
        sig.setPadding(0, dp(4), 0, 0);
        c.addView(sig, lp(-1, -2));

        setScreen(page("Flash Tools", hrow(menuBtn()), null, c, null));
    }

    void enterMyAccount() {
        if (!loaded) { toast("استنى لحظة، جاري تحميل حسابك..."); return; }
        JSONObject target = null;
        for (JSONObject r : repos) {
            JSONObject o = r.optJSONObject("owner");
            if (o != null && o.optString("login").equals(me)) { target = r; break; }
        }
        if (target == null) {
            if (repos.isEmpty()) { toast("مفيش مستودعات في حسابك"); return; }
            target = repos.get(0);
        }
        openRepo(target);
    }

    void fillCard(LinearLayout cv, int i) {
        cv.removeAllViews();
        String[] d = CARDS[i];
        String tc = dark ? GOLD : "#8A5A00";
        String bc = dark ? SOFT : "#3C3C43";
        TextView st = tv((i + 1) + " / " + CARDS.length, 12, tc, false);
        st.setGravity(Gravity.CENTER);
        TextView em = tv(d[0], 64, TXT, false);
        em.setGravity(Gravity.CENTER);
        em.setPadding(0, dp(18), 0, dp(14));
        TextView t = tv(d[1], 24, tc, true);
        t.setGravity(Gravity.CENTER);
        TextView b = tv(d[2], 16, bc, false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(12), 0, 0);
        b.setLineSpacing(0, 1.25f);
        cv.addView(st, lp(-1, -2));
        cv.addView(em, lp(-1, -2));
        cv.addView(t, lp(-1, -2));
        cv.addView(b, lp(-1, -2));
    }

    // انتقال: الكرت بيروح لليمين ويختفي، والجاي ييجي من الشمال
    void slideOut(final int dir) {
        if (cardView == null || animating) return;
        animating = true;
        final LinearLayout cv = cardView;
        final float w = getResources().getDisplayMetrics().widthPixels;
        cardIdx = (cardIdx + 1) % CARDS.length;
        paintDots();
        cv.animate().translationX(dir * w).rotation(dir * 14f).alpha(0f)
                .setDuration(280).setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    fillCard(cv, cardIdx);
                    cv.setTranslationX(-dir * w);
                    cv.setRotation(-dir * 14f);
                    cv.animate().translationX(0).rotation(0).alpha(1f)
                            .setDuration(340).setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> animating = false).start();
                }).start();
    }

    void paintDots() {
        if (dotsBox == null) return;
        dotsBox.removeAllViews();
        for (int i = 0; i < CARDS.length; i++) {
            View d = new View(this);
            d.setBackground(rect(i == cardIdx ? GOLD : GRAY, 4));
            LinearLayout.LayoutParams p = lp(dp(i == cardIdx ? 20 : 7), dp(7));
            p.setMargins(dp(3), 0, dp(3), 0);
            dotsBox.addView(d, p);
        }
    }

    // ---------- drawer ----------
    void openDrawer() {
        if (busy || screen == 0) return;
        buildDrawer();
        drawer.setVisibility(View.VISIBLE);
        drawerOpen = true;
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        panel.setTranslationX(panelW);
        panel.animate().translationX(0).setDuration(250).start();
    }

    void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        scrim.animate().alpha(0f).setDuration(180).start();
        panel.animate().translationX(panelW).setDuration(220)
                .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    String permText(JSONObject r) {
        JSONObject p = r.optJSONObject("permissions");
        if (p == null) return "قراءة";
        if (p.optBoolean("admin")) return "مدير";
        if (p.optBoolean("push") || p.optBoolean("maintain")) return "كتابة";
        return "قراءة فقط";
    }

    String sourceText(JSONObject r) {
        JSONObject o = r.optJSONObject("owner");
        String l = o == null ? "" : o.optString("login");
        if (l.equals(me)) return "👤 حسابي";
        if (o != null && "Organization".equals(o.optString("type"))) return "👥 فريق: " + l;
        return "🤝 مشارك: " + l;
    }

    void buildDrawer() {
        panel.removeAllViews();
        panel.setBackgroundColor(Color.parseColor(BG));
        panel.setElevation(dp(16));
        if (!gInit) { openGroups.add(me); gInit = true; }
        LinearLayout c = vbox();
        c.setPadding(dp(14), dp(44), dp(14), dp(24));

        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(dp(16), dp(16), dp(16), dp(16));
        hd.setBackground(legend(20));
        TextView lg = tv("⚡", 30, "#FFFFFF", false);
        lg.setPaddingRelative(0, 0, dp(12), 0);
        hd.addView(lg);
        LinearLayout nm = vbox();
        nm.addView(tv("Flash Tools", 19, GOLD, true));
        nm.addView(tv("@" + me, 13, SOFT, false));
        hd.addView(nm);
        c.addView(hd);

        TextView tk = tv("🔑 صلاحيات التوكن", 12, GRAY, false);
        tk.setPadding(dp(6), dp(16), 0, dp(4));
        c.addView(tk);
        String sc = scopes == null ? "توكن دقيق (Fine-grained) — الصلاحيات بتختلف من مستودع للتاني"
                : scopes.isEmpty() ? "بدون نطاقات — وصول للمستودعات العامة فقط" : scopes;
        TextView st = tv(sc, 13, TXT, false);
        st.setPadding(dp(16), dp(12), dp(16), dp(12));
        st.setBackground(rect(CARD, 14));
        c.addView(st);

        c.addView(gap(14));
        final Switch sw = new Switch(this);
        sw.setChecked(dark);
        sw.setOnCheckedChangeListener((bv, on) -> {
            prefs.edit().putBoolean("dark", on).apply();
            applyTheme();
            drawerOpen = false;
            drawer.setVisibility(View.GONE);
            render();
        });
        List<View> tr = new ArrayList<>();
        tr.add(row("🌙", "الوضع الداكن", null, sw, v -> sw.toggle()));
        c.addView(card(tr));

        TextView rt = tv("📦 المستودعات (" + repos.size() + ")", 12, GRAY, false);
        rt.setPadding(dp(6), dp(16), 0, dp(4));
        c.addView(rt);
        Map<String, List<JSONObject>> g = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        g.put(me, new ArrayList<>());
        for (JSONObject r : repos) {
            JSONObject o = r.optJSONObject("owner");
            String k = o == null ? "؟" : o.optString("login");
            if (!g.containsKey(k)) g.put(k, new ArrayList<>());
            g.get(k).add(r);
            types.put(k, o == null ? "" : o.optString("type"));
        }
        for (Map.Entry<String, List<JSONObject>> en : g.entrySet()) {
            final String k = en.getKey();
            if (en.getValue().isEmpty()) continue;
            boolean open = openGroups.contains(k);
            String label = k.equals(me) ? "👤 حسابي"
                    : "Organization".equals(types.get(k)) ? "👥 فريق: " + k : "🤝 مشارك: " + k;
            List<View> rows = new ArrayList<>();
            rows.add(row("", label + " (" + en.getValue().size() + ")", null,
                    tv(open ? "▾" : "▸", 18, GRAY, false), v -> {
                        if (!openGroups.remove(k)) openGroups.add(k);
                        buildDrawer();
                    }));
            if (open) {
                for (JSONObject r : en.getValue()) {
                    final JSONObject rr = r;
                    boolean isCur = cur != null && cur.optString("full_name").equals(r.optString("full_name"));
                    rows.add(row(r.optBoolean("private") ? "🔒" : "📘", r.optString("name"),
                            permText(r) + (isCur ? " • مفتوح الآن" : ""), null, v -> openRepo(rr)));
                }
            }
            c.addView(gap(8));
            c.addView(card(rows));
        }

        c.addView(gap(16));
        List<View> f = new ArrayList<>();
        f.add(row("🔄", "تحديث المستودعات", null, null, v -> { closeDrawer(); loadAccount(); }));
        f.add(row("👨‍💻", "المطوّر", "AXEL", null, v -> { }));
        c.addView(card(f));
        TextView lo = link("🚪 خروج", RED, v -> logout());
        lo.setGravity(Gravity.CENTER);
        c.addView(gap(8));
        c.addView(lo, lp(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        panel.addView(sv, lp(-1, -1));
    }

    void openRepo(JSONObject r) {
        cur = r;
        repo = r.optString("full_name");
        branch = r.optString("default_branch", "main");
        JSONObject p = r.optJSONObject("permissions");
        canWrite = p != null && (p.optBoolean("push") || p.optBoolean("admin") || p.optBoolean("maintain"));
        path = "";
        selMode = false;
        closeDrawer();
        showBrowser();
    }

    // ---------- browser ----------
    void showBrowser() {
        screen = 2;
        clockT = null;
        cardView = null;
        items = new ArrayList<>();
        sel.clear();
        selMode = false;
        renderBrowser();
        work("جاري التحميل...", null);
    }

    void paintCk(TextView ck, boolean on) {
        ck.setText(on ? "✓" : "");
        if (on) ck.setBackground(rect(BLUE, 12));
        else {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(12));
            g.setStroke(dp(2), Color.parseColor("#C7C7CC"));
            ck.setBackground(g);
        }
    }

    View checkBox(boolean on) {
        TextView ck = tv(on ? "✓" : "", 14, "#FFFFFF", true);
        ck.setGravity(Gravity.CENTER);
        paintCk(ck, on);
        LinearLayout.LayoutParams p = lp(dp(24), dp(24));
        p.setMarginEnd(dp(12));
        ck.setLayoutParams(p);
        return ck;
    }

    String human(long n) {
        if (n < 1024) return n + " B";
        if (n < 1048576) return (n / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", n / 1048576.0);
    }

    boolean isDir(String p) {
        for (JSONObject o : items) if (o.optString("path").equals(p)) return "dir".equals(o.optString("type"));
        return false;
    }

    boolean nameExists(String n) {
        for (JSONObject o : items) if (o.optString("name").equalsIgnoreCase(n)) return true;
        return false;
    }

    void toggleSel(String p) {
        if (!sel.remove(p)) sel.add(p);
    }

    void exitSel() {
        selMode = false;
        sel.clear();
        renderBrowser();
    }

    void selectAll() {
        sel.clear();
        for (JSONObject o : items) sel.add(o.optString("path"));
        renderBrowser();
    }

    void renderBrowser() {
        List<View> rows = new ArrayList<>();
        for (JSONObject o : items) {
            final String p = o.optString("path");
            final String name = o.optString("name");
            final boolean dir = "dir".equals(o.optString("type"));
            View trail;
            if (selMode) trail = checkBox(sel.contains(p));
            else if (dir) trail = tv("‹", 24, GRAY, false);
            else trail = null;
            View rv = row(dir ? "📁" : "📄", name, dir ? null : human(o.optLong("size")), trail, v -> {
                if (selMode) { toggleSel(p); renderBrowser(); }
                else if (dir) { path = p; sel.clear(); work("جاري التحميل...", null); }
                else openEditor(p);
            });
            rv.setOnLongClickListener(v -> { onItemMenu(p, name); return true; });
            rows.add(rv);
        }
        LinearLayout content = vbox();
        TextView meta = tv((cur == null ? "" : sourceText(cur) + "  •  " + permText(cur)), 12, GRAY, false);
        meta.setPadding(dp(6), 0, 0, dp(10));
        content.addView(meta);
        if (selMode) {
            LinearLayout sr = hrow(
                    link("✕ إلغاء التحديد", RED, v -> exitSel()),
                    link("☑ تحديد الكل", BLUE, v -> selectAll()));
            sr.setPadding(dp(2), 0, 0, dp(8));
            content.addView(sr);
        }
        if (!canWrite) {
            TextView ro = tv("🔒 قراءة فقط — توكنك مالوش صلاحية كتابة على المستودع ده", 13, RED, false);
            ro.setPadding(dp(6), 0, 0, dp(10));
            content.addView(ro);
        }
        if (rows.isEmpty()) {
            TextView e = tv("المجلد فاضي", 16, GRAY, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(60), 0, 0);
            content.addView(e, lp(-1, -2));
        } else content.addView(card(rows));

        LinearLayout bottom = vbox();
        bottom.addView(downloadBtn());
        bottom.addView(bar());
        String title = path.isEmpty() ? repo.substring(repo.indexOf('/') + 1) : base(path);
        setScreen(page(title, hrow(menuBtn(), link("› رجوع", BLUE, v -> goBack())),
                link("↻ تحديث", BLUE, v -> work("جاري التحديث...", null)), content, bottom));
    }

    // زر تحميل ملفات المستودع (أو الملفات المحددة لو فيه تحديد)
    TextView downloadBtn() {
        String lbl = sel.isEmpty() ? "⬇️ تحميل ملفات المستودع" : "⬇️ تحميل المحدد (" + sel.size() + ")";
        TextView t = tv(lbl, 16, "#FFFFFF", true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rect(BLUE, 14));
        t.setPadding(0, dp(13), 0, dp(13));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.setMargins(dp(16), dp(10), dp(16), dp(4));
        t.setLayoutParams(p);
        t.setOnClickListener(v -> downloadSel());
        return t;
    }

    void onItemMenu(final String p, final String name) {
        final List<String> opts = new ArrayList<>();
        opts.add("☑ تحديد");
        if (canWrite) {
            opts.add("✏️ تغيير الاسم");
            opts.add("🗑️ حذف");
        }
        opts.add("⬇️ تحميل");
        ab().setTitle(name).setItems(opts.toArray(new String[0]), (d, i) -> {
            String o = opts.get(i);
            if (o.startsWith("☑")) {
                selMode = true;
                sel.clear();
                sel.add(p);
                renderBrowser();
            } else if (o.startsWith("✏")) {
                askRename(p, name);
            } else if (o.startsWith("🗑")) {
                sel.clear();
                sel.add(p);
                deleteSel();
            } else {
                sel.clear();
                sel.add(p);
                downloadSel();
            }
        }).show();
    }

    void askRename(final String p, final String name) {
        final EditText et = new EditText(this);
        et.setText(name);
        et.setSingleLine();
        et.setPadding(dp(20), dp(12), dp(20), dp(12));
        ab().setTitle("تغيير الاسم")
                .setView(et)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (d, i) -> {
                    final String nn = et.getText().toString().trim();
                    if (nn.isEmpty() || nn.contains("/") || nn.equals(name)) return;
                    if (nameExists(nn)) { alert("خطأ", "في عنصر بنفس الاسم ده في المجلد"); return; }
                    work("جاري تغيير الاسم...", () -> renameItem(p, join(path, nn)));
                })
                .show();
    }

    View act(String ic, String lb, boolean on, String col, View.OnClickListener l) {
        LinearLayout a = vbox();
        a.setGravity(Gravity.CENTER_HORIZONTAL);
        a.setPadding(0, dp(4), 0, dp(4));
        TextView i = tv(ic, 22, TXT, false);
        i.setGravity(Gravity.CENTER);
        a.addView(i);
        a.addView(tv(lb, 11, col, false));
        a.setAlpha(on ? 1f : 0.35f);
        if (on) { ripple(a); a.setOnClickListener(l); }
        return a;
    }

    View bar() {
        int n = sel.size();
        boolean w = canWrite;
        boolean zip1 = n == 1 && sel.iterator().next().toLowerCase(Locale.ROOT).endsWith(".zip");
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(Color.parseColor(CARD));
        b.setPadding(0, dp(6), 0, dp(6));
        b.addView(act("☁️", "رفع", w, BLUE, v -> upMenu()), new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(act("📂", "فك ضغط", zip1 && w, BLUE, v -> unzipSel()), new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(act("🗜️", "ضغط", n > 0 && w, BLUE, v -> zipSel()), new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(act("🗑️", "حذف", n > 0 && w, RED, v -> deleteSel()), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout wr = vbox();
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor(SEP));
        wr.addView(d, lp(-1, 1));
        wr.addView(b);
        return wr;
    }

    // ---------- editor ----------
    boolean looksBinary(byte[] b) {
        int n = Math.min(b.length, 8000);
        for (int i = 0; i < n; i++) if (b[i] == 0) return true;
        return false;
    }

    void openEditor(final String p) {
        busy("جاري فتح الملف...");
        ex.execute(() -> {
            try {
                byte[] b = raw(p);
                if (looksBinary(b)) {
                    ui(() -> { idle(); alert("ملف مش نصي", "الملف ده ثنائي (صورة أو فيديو أو مضغوط)، حمّله بزرار التحميل بدل التعديل."); });
                    return;
                }
                final String txt = str(b);
                ui(() -> { idle(); showEditor(p, txt); });
            } catch (Exception e) {
                ui(() -> { idle(); alert("خطأ", err(e)); });
            }
        });
    }

    void showEditor(final String p, String txt) {
        editPath = p;
        screen = 3;
        origTxt = txt;
        LinearLayout root = vbox();
        root.setBackgroundColor(Color.parseColor(BG));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(40), dp(12), dp(4));
        top.addView(link("› رجوع", BLUE, v -> closeEditor()));
        TextView title = tv(base(p), 17, TXT, true);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = link(canWrite ? "حفظ" : "🔒 قراءة", canWrite ? BLUE : GRAY, v -> {
            if (canWrite) saveEditor();
            else toast("توكنك للقراءة بس على المستودع ده");
        });
        save.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(save);
        root.addView(top);

        edit = new EditText(this);
        edit.setText(txt);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setTextSize(13);
        edit.setTextColor(Color.parseColor(TXT));
        edit.setHintTextColor(Color.parseColor(GRAY));
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setBackground(rect(CARD, 14));
        edit.setPadding(dp(12), dp(12), dp(12), dp(12));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (!canWrite) {
            edit.setFocusable(false);
            edit.setFocusableInTouchMode(false);
        }
        edit.setLongClickable(true);
        edit.setTextIsSelectable(true);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, 0, 1);
        ep.setMargins(dp(12), dp(6), dp(12), dp(6));
        root.addView(edit, ep);

        LinearLayout tb = new LinearLayout(this);
        tb.setBackgroundColor(Color.parseColor(CARD));
        tb.setPadding(0, dp(4), 0, dp(6));
        tb.addView(act("📋", "نسخ الكل", true, BLUE, v -> copyAll()), new LinearLayout.LayoutParams(0, -2, 1));
        tb.addView(act("🔤", "تحديد الكل", true, BLUE, v -> edit.selectAll()), new LinearLayout.LayoutParams(0, -2, 1));
        tb.addView(act("🧹", "مسح الكل", canWrite, RED, v -> edit.setText("")), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(tb);

        setScreen(root);
    }

    void copyAll() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", edit.getText().toString()));
        toast("اتنسخ الكود ✅");
    }

    void saveEditor() {
        final String p = editPath;
        final String txt = edit.getText().toString();
        final byte[] data = txt.getBytes(StandardCharsets.UTF_8);
        busy("جاري الحفظ في المستودع...");
        ex.execute(() -> {
            try {
                up(p, data);
                ui(() -> { origTxt = txt; idle(); toast("اتحفظ في المستودع ✅"); });
            } catch (Exception e) {
                ui(() -> { idle(); alert("فشل الحفظ", err(e)); });
            }
        });
    }

    void closeEditor() {
        String now = edit == null ? "" : edit.getText().toString();
        if (canWrite && !now.equals(origTxt)) {
            ab().setTitle("في تغييرات مش متحفوظة")
                    .setMessage("تخرج من غير ما تحفظ؟")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("خروج", (d, i) -> { screen = 2; renderBrowser(); })
                    .show();
            return;
        }
        screen = 2;
        renderBrowser();
    }

    // ---------- actions ----------
    void work(String label, Task t) {
        busy(label);
        ex.execute(() -> {
            String er = null;
            try { if (t != null) t.run(); } catch (Exception e) { er = err(e); }
            try { items = listDir(); } catch (Exception e) { if (er == null) er = err(e); }
            final String fer = er;
            ui(() -> {
                sel.clear();
                selMode = false;
                idle();
                if (screen == 2) renderBrowser();
                if (fer != null) alert("خطأ", fer);
            });
        });
    }

    void upMenu() {
        ab().setItems(new String[]{"رفع ملفات", "رفع ملف zip وفك ضغطه"}, (d, i) -> pick(i == 1)).show();
    }

    void pick(boolean zip) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        if (!zip) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, zip ? 2 : 1);
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent d) {
        super.onActivityResult(rc, res, d);
        if (res != RESULT_OK || d == null) return;
        final List<Uri> us = new ArrayList<>();
        ClipData cd = d.getClipData();
        if (cd != null) {
            for (int i = 0; i < cd.getItemCount(); i++) us.add(cd.getItemAt(i).getUri());
        } else if (d.getData() != null) us.add(d.getData());
        if (us.isEmpty()) return;
        if (rc == 1) {
            work("جاري الرفع...", () -> {
                for (Uri u : us) {
                    String n = nameOf(u);
                    status("رفع " + n);
                    up(join(path, n), readUri(u));
                }
            });
        } else if (rc == 2) {
            work("جاري فك الضغط...", () -> extract(readUri(us.get(0))));
        } else if (rc == 3) {
            final byte[] data = pendingSave;
            ex.execute(() -> {
                try (OutputStream o = getContentResolver().openOutputStream(us.get(0))) {
                    o.write(data);
                    ui(() -> toast("تم الحفظ ✅"));
                } catch (Exception e) {
                    ui(() -> alert("خطأ", err(e)));
                }
            });
        }
    }

    void unzipSel() {
        final String p = sel.iterator().next();
        work("جاري فك الضغط...", () -> extract(raw(p)));
    }

    void zipSel() {
        final List<String> ps = new ArrayList<>(sel);
        work("جاري الضغط...", () -> up(join(path, "Archive_" + System.currentTimeMillis() + ".zip"), pack(ps)));
    }

    // تحميل: لو فيه تحديد ينزل المحدد، لو مفيش ينزل ملفات المستودع كلها
    void downloadSel() {
        final List<String> ps = new ArrayList<>(sel);
        final boolean whole = ps.isEmpty();
        final boolean one = !whole && ps.size() == 1 && !isDir(ps.get(0));
        final String name = whole ? repo.substring(repo.indexOf('/') + 1) + ".zip"
                : one ? base(ps.get(0))
                : (ps.size() == 1 ? base(ps.get(0)) : "FlashTools") + ".zip";
        sel.clear();
        selMode = false;
        if (screen == 2) renderBrowser();
        busy("جاري التجهيز...");
        ex.execute(() -> {
            try {
                final byte[] data = whole ? packFiles(repoFiles(), "")
                        : one ? raw(ps.get(0))
                        : pack(ps);
                ui(() -> {
                    idle();
                    pendingSave = data;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(one ? "application/octet-stream" : "application/zip");
                    i.putExtra(Intent.EXTRA_TITLE, name);
                    startActivityForResult(i, 3);
                });
            } catch (Exception e) {
                ui(() -> { idle(); alert("خطأ", err(e)); });
            }
        });
    }

    void deleteSel() {
        final List<String> ps = new ArrayList<>(sel);
        if (ps.isEmpty()) return;
        ab().setTitle("حذف " + ps.size() + " عنصر؟")
                .setMessage("الملفات والمجلدات (بكل محتواها) هتتحذف نهائيًا")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, i) -> work("جاري الحذف...", () -> deleteMany(ps)))
                .show();
    }

    // ---------- files ----------
    String nameOf(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) { }
        return "file_" + System.currentTimeMillis();
    }

    byte[] readUri(Uri u) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(u)) { return readAll(in); }
    }

    byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }

    String join(String d, String n) { return d.isEmpty() ? n : d + "/" + n; }
    String base(String p) { return p.substring(p.lastIndexOf('/') + 1); }
    String str(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
    byte[] bytes(JSONObject o) { return o.toString().getBytes(StandardCharsets.UTF_8); }

    void extract(byte[] z) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(z))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String n = e.getName();
                if (e.isDirectory() || n.contains("..") || n.startsWith("__MACOSX")) continue;
                byte[] b = readAll(in);
                status("رفع " + n);
                up(join(path, n), b);
            }
        }
    }

    void walk(String p, List<String> out) throws Exception {
        String s = str(req("GET", cu(p), JSON, null)).trim();
        if (s.startsWith("[")) {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if ("dir".equals(o.optString("type"))) walk(o.getString("path"), out);
                else out.add(o.getString("path"));
            }
        } else out.add(p);
    }

    // بيضغط الملفات، والأسماء بتبقى نسبية لجذر الزيب (root)
    byte[] packFiles(List<String> fs, String root) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (String f : fs) {
                status("ضغط " + base(f));
                String n = root.isEmpty() ? f
                        : (f.startsWith(root + "/") ? f.substring(root.length() + 1) : f);
                z.putNextEntry(new ZipEntry(n));
                z.write(raw(f));
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    byte[] pack(List<String> ps) throws Exception {
        List<String> fs = new ArrayList<>();
        for (String p : ps) walk(p, fs);
        return packFiles(fs, path);
    }

    // كل ملفات المستودع من الفرع الأساسي
    List<String> repoFiles() throws Exception {
        List<String> out = new ArrayList<>();
        String cs = headCommit();
        String ts = commitTree(cs);
        JSONObject tr = new JSONObject(str(req("GET", R() + "/git/trees/" + ts + "?recursive=1", JSON, null)));
        if (tr.optBoolean("truncated")) { walk("", out); return out; }
        JSONArray ents = tr.getJSONArray("tree");
        for (int i = 0; i < ents.length(); i++) {
            JSONObject o = ents.getJSONObject(i);
            if ("blob".equals(o.optString("type"))) out.add(o.getString("path"));
        }
        return out;
    }

    // ---------- GitHub ----------
    String ep(String p) {
        StringBuilder s = new StringBuilder();
        for (String seg : p.split("/")) {
            if (seg.isEmpty()) continue;
            if (s.length() > 0) s.append('/');
            try { s.append(URLEncoder.encode(seg, "UTF-8").replace("+", "%20")); } catch (Exception ignored) { }
        }
        return s.toString();
    }

    String R() { return API + "/repos/" + repo; }
    String cu(String p) { return R() + "/contents/" + ep(p); }

    String headCommit() throws Exception {
        JSONObject ref = new JSONObject(str(req("GET", R() + "/git/ref/heads/" + branch, JSON, null)));
        return ref.getJSONObject("object").getString("sha");
    }

    String commitTree(String cs) throws Exception {
        return new JSONObject(str(req("GET", R() + "/git/commits/" + cs, JSON, null)))
                .getJSONObject("tree").getString("sha");
    }

    String msgOf(byte[] r, int code) {
        if (code == 401) return "التوكن غير صحيح أو منتهي";
        if (code == 403) return "مفيش صلاحية لده (403): " + tryMsg(r, "");
        return tryMsg(r, "خطأ " + code);
    }

    String tryMsg(byte[] r, String d) {
        try { return new JSONObject(str(r)).optString("message", d); } catch (Exception e) { return d; }
    }

    byte[] req(String m, String url, String accept, byte[] body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(m);
        c.setConnectTimeout(20000);
        c.setReadTimeout(180000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", accept);
        if (body != null) {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body); }
        }
        int code = c.getResponseCode();
        if (url.equals(API + "/user")) lastScopes = c.getHeaderField("X-OAuth-Scopes");
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        byte[] r = is == null ? new byte[0] : readAll(is);
        c.disconnect();
        if (code >= 300) throw new HttpEx(code, msgOf(r, code));
        return r;
    }

    List<JSONObject> listDir() throws Exception {
        List<JSONObject> l = new ArrayList<>();
        try {
            String s = str(req("GET", cu(path), JSON, null)).trim();
            if (s.startsWith("[")) {
                JSONArray arr = new JSONArray(s);
                for (int i = 0; i < arr.length(); i++) l.add(arr.getJSONObject(i));
            }
        } catch (HttpEx e) {
            if (e.code != 404) throw e;
        }
        Collections.sort(l, (p1, p2) -> {
            int x = "dir".equals(p1.optString("type")) ? 0 : 1;
            int y = "dir".equals(p2.optString("type")) ? 0 : 1;
            return x != y ? x - y : p1.optString("name").compareToIgnoreCase(p2.optString("name"));
        });
        return l;
    }

    byte[] raw(String p) throws Exception { return req("GET", cu(p), RAW, null); }

    void up(String p, byte[] b) throws Exception {
        String sha = null;
        try {
            sha = new JSONObject(str(req("GET", cu(p), JSON, null))).optString("sha", null);
        } catch (HttpEx e) {
            if (e.code != 404) throw e;
        }
        JSONObject body = new JSONObject().put("message", "Flash Tools")
                .put("content", Base64.encodeToString(b, Base64.NO_WRAP));
        if (sha != null) body.put("sha", sha);
        req("PUT", cu(p), JSON, bytes(body));
    }

    void del(String p, String sha) throws Exception {
        req("DELETE", cu(p), JSON, bytes(new JSONObject().put("message", "Flash Tools").put("sha", sha)));
    }

    // تعديل شجرة الكومتات في commit واحد: حذف ps، ولو newRoot موجود بينقل المحتوى لمسار جديد
    void treeChange(String msg, List<String> ps, String newRoot) throws Exception {
        String cs = headCommit();
        String ts = commitTree(cs);
        JSONObject tr = new JSONObject(str(req("GET", R() + "/git/trees/" + ts + "?recursive=1", JSON, null)));
        if (tr.optBoolean("truncated")) throw new Exception("المستودع كبير أوي للعملية دي");
        JSONArray ents = tr.getJSONArray("tree");
        JSONArray out = new JSONArray();
        for (int i = 0; i < ents.length(); i++) {
            JSONObject o = ents.getJSONObject(i);
            if (!"blob".equals(o.optString("type"))) continue;
            String fp = o.getString("path");
            String mode = o.optString("mode", "100644");
            for (String d : ps) {
                if (!fp.equals(d) && !fp.startsWith(d + "/")) continue;
                out.put(new JSONObject().put("path", fp).put("mode", mode)
                        .put("type", "blob").put("sha", JSONObject.NULL));
                if (newRoot != null) {
                    out.put(new JSONObject().put("path", newRoot + fp.substring(d.length()))
                            .put("mode", mode).put("type", "blob").put("sha", o.getString("sha")));
                }
                break;
            }
        }
        if (out.length() == 0) throw new Exception("مفيش ملفات تتعدل");
        JSONObject nt = new JSONObject(str(req("POST", R() + "/git/trees", JSON,
                bytes(new JSONObject().put("base_tree", ts).put("tree", out)))));
        JSONObject cm = new JSONObject(str(req("POST", R() + "/git/commits", JSON,
                bytes(new JSONObject().put("message", msg).put("tree", nt.getString("sha"))
                        .put("parents", new JSONArray().put(cs))))));
        req("PATCH", R() + "/git/refs/heads/" + branch, JSON,
                bytes(new JSONObject().put("sha", cm.getString("sha"))));
    }

    void deleteMany(List<String> ps) throws Exception {
        try {
            status("جاري الحذف (commit واحد)...");
            treeChange("Flash Tools: delete", ps, null);
        } catch (Exception e) {
            for (String p : ps) { status("حذف " + base(p)); delRec(p); }
        }
    }

    void renameItem(String oldP, String newP) throws Exception {
        status("جاري تغيير الاسم...");
        try {
            treeChange("Flash Tools: rename", Collections.singletonList(oldP), newP);
        } catch (Exception e) {
            if (isDir(oldP)) throw e;
            byte[] b = raw(oldP);
            up(newP, b);
            String sha = new JSONObject(str(req("GET", cu(oldP), JSON, null))).getString("sha");
            del(oldP, sha);
        }
    }

    void delRec(String p) throws Exception {
        String s = str(req("GET", cu(p), JSON, null)).trim();
        if (s.startsWith("[")) {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) delRec(a.getJSONObject(i).getString("path"));
        } else del(p, new JSONObject(s).getString("sha"));
    }

    // ---------- encrypted token (Android Keystore, AES-GCM) ----------
    SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias("ft_key")) {
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("ft_key", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            g.generateKey();
        }
        return (SecretKey) ks.getKey("ft_key", null);
    }

    void saveToken(String t) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(t.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        prefs.edit().putString("t", Base64.encodeToString(all, Base64.NO_WRAP)).apply();
    }

    String loadToken() {
        try {
            String s = prefs.getString("t", null);
            if (s == null) return null;
            byte[] all = Base64.decode(s, Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
