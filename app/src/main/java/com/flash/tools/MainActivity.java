package com.flash.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
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
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    static final String BG = "#F2F2F7", BLUE = "#007AFF", RED = "#FF3B30", GRAY = "#8E8E93";
    static final String API = "https://api.github.com";
    static final String JSON = "application/vnd.github+json";
    static final String RAW = "application/vnd.github.raw+json";

    interface Task { void run() throws Exception; }

    static class HttpEx extends Exception {
        final int code;
        HttpEx(int c, String m) { super(m); code = c; }
    }

    FrameLayout host, overlay, barBox;
    TextView overlayText;
    final ExecutorService ex = Executors.newSingleThreadExecutor();
    SharedPreferences prefs;
    String token, repo = "", path = "";
    List<JSONObject> items = new ArrayList<>();
    final Set<String> sel = new LinkedHashSet<>();
    int screen = 0;
    boolean busy = false;
    byte[] pendingSave;

    // ---------- helpers ----------
    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable rect(String c, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(c));
        g.setCornerRadius(dp(r));
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

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    void ui(Runnable r) { runOnUiThread(r); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    String err(Exception e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }

    void alert(String t, String m) {
        new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("حسناً", null).show();
    }

    void busy(String m) { busy = true; overlayText.setText(m); overlay.setVisibility(View.VISIBLE); }
    void idle() { busy = false; overlay.setVisibility(View.GONE); }
    void status(String s) { ui(() -> overlayText.setText(s)); }

    View ripple(View v) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), null, new ColorDrawable(Color.BLACK)));
        return v;
    }

    TextView link(String s, String c, View.OnClickListener l) {
        TextView t = tv(s, 16, c, false);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        ripple(t);
        t.setOnClickListener(l);
        return t;
    }

    View row(String icon, String title, String sub, View trail, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView ic = tv(icon, 22, "#000000", false);
        ic.setPaddingRelative(0, 0, dp(12), 0);
        r.addView(ic);
        LinearLayout m = vbox();
        TextView t = tv(title, 16, "#000000", false);
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
        c.setBackground(rect("#FFFFFF", 14));
        c.setClipToOutline(true);
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                View d = new View(this);
                d.setBackgroundColor(0x1F000000);
                LinearLayout.LayoutParams p = lp(-1, 1);
                p.setMarginStart(dp(50));
                c.addView(d, p);
            }
            c.addView(rows.get(i));
        }
        return c;
    }

    LinearLayout page(String title, View back, View end, View content, View bottom) {
        LinearLayout root = vbox();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(12), 0);
        if (back != null) top.addView(back);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (end != null) top.addView(end);
        root.addView(top);
        TextView t = tv(title, 30, "#000000", true);
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
        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------- lifecycle ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ft", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.parseColor(BG));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        host = new FrameLayout(this);
        host.setBackgroundColor(Color.parseColor(BG));
        host.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCCF2F2F7);
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
        setContentView(host);
        token = loadToken();
        if (token == null) showLogin(); else showRepos();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        if (screen == 2) goBack(); else super.onBackPressed();
    }

    void goBack() {
        if (busy) return;
        if (path.isEmpty()) { showRepos(); return; }
        int i = path.lastIndexOf('/');
        path = i < 0 ? "" : path.substring(0, i);
        sel.clear();
        work("جاري التحميل...", null);
    }

    // ---------- screens ----------
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
        TextView h = tv("Flash Tools", 32, "#000000", true);
        h.setPadding(0, dp(22), 0, dp(6));
        c.addView(h);
        TextView s = tv("أدوات قوية لإدارة ملفات حسابك على GitHub", 15, GRAY, false);
        s.setGravity(Gravity.CENTER);
        c.addView(s);
        final EditText et = new EditText(this);
        et.setHint("ضع توكن GitHub هنا");
        et.setSingleLine();
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setBackground(rect("#FFFFFF", 14));
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
                    ui(() -> { idle(); showRepos(); });
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
        token = null;
        showLogin();
    }

    void showRepos() {
        screen = 1;
        repo = "";
        path = "";
        setScreen(page("مستودعاتي", null, link("خروج", RED, v -> logout()), vbox(), null));
        busy("جاري تحميل المستودعات...");
        ex.execute(() -> {
            try {
                final JSONArray a = new JSONArray(str(req("GET", API + "/user/repos?per_page=100&sort=updated", JSON, null)));
                ui(() -> {
                    idle();
                    List<View> rows = new ArrayList<>();
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        if (o == null) continue;
                        final String full = o.optString("full_name");
                        boolean pr = o.optBoolean("private");
                        rows.add(row(pr ? "🔒" : "📘", o.optString("name"), pr ? "خاص" : "عام",
                                tv("‹", 24, GRAY, false), v -> { repo = full; path = ""; showBrowser(); }));
                    }
                    LinearLayout content = vbox();
                    if (rows.isEmpty()) content.addView(tv("مفيش مستودعات", 16, GRAY, false));
                    else content.addView(card(rows));
                    setScreen(page("مستودعاتي", null, link("خروج", RED, v -> logout()), content, null));
                });
            } catch (Exception e) {
                ui(() -> { idle(); alert("خطأ", err(e)); });
            }
        });
    }

    void showBrowser() {
        screen = 2;
        items = new ArrayList<>();
        sel.clear();
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

    String human(long n) {
        if (n < 1024) return n + " B";
        if (n < 1048576) return (n / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", n / 1048576.0);
    }

    void renderBrowser() {
        List<View> rows = new ArrayList<>();
        for (JSONObject o : items) {
            final String p = o.optString("path");
            final String name = o.optString("name");
            if ("dir".equals(o.optString("type"))) {
                rows.add(row("📁", name, null, tv("‹", 24, GRAY, false), v -> {
                    path = p;
                    sel.clear();
                    work("جاري التحميل...", null);
                }));
            } else {
                final TextView ck = tv("", 14, "#FFFFFF", true);
                ck.setGravity(Gravity.CENTER);
                ck.setLayoutParams(lp(dp(24), dp(24)));
                paintCk(ck, sel.contains(p));
                rows.add(row("📄", name, human(o.optLong("size")), ck, v -> {
                    boolean now = !sel.contains(p);
                    if (now) sel.add(p); else sel.remove(p);
                    paintCk(ck, now);
                    refreshBar();
                }));
            }
        }
        LinearLayout content = vbox();
        if (rows.isEmpty()) {
            TextView e = tv("المجلد فاضي", 16, GRAY, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(60), 0, 0);
            content.addView(e, lp(-1, -2));
        } else content.addView(card(rows));
        barBox = new FrameLayout(this);
        barBox.addView(bar());
        String title = path.isEmpty() ? repo.substring(repo.indexOf('/') + 1) : base(path);
        setScreen(page(title, link("› رجوع", BLUE, v -> goBack()),
                link("↻ تحديث", BLUE, v -> work("جاري التحديث...", null)), content, barBox));
    }

    void refreshBar() {
        barBox.removeAllViews();
        barBox.addView(bar());
    }

    View act(String ic, String lb, boolean on, String col, View.OnClickListener l) {
        LinearLayout a = vbox();
        a.setGravity(Gravity.CENTER_HORIZONTAL);
        a.setPadding(0, dp(4), 0, dp(4));
        TextView i = tv(ic, 22, "#000000", false);
        i.setGravity(Gravity.CENTER);
        a.addView(i);
        a.addView(tv(lb, 11, col, false));
        a.setAlpha(on ? 1f : 0.35f);
        if (on) { ripple(a); a.setOnClickListener(l); }
        return a;
    }

    View bar() {
        int n = sel.size();
        boolean zip1 = n == 1 && sel.iterator().next().toLowerCase().endsWith(".zip");
        LinearLayout b = new LinearLayout(this);
        b.setBackgroundColor(Color.WHITE);
        b.setPadding(0, dp(6), 0, dp(6));
        b.addView(act("☁️", "رفع", true, BLUE, v -> upMenu()), new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(act("📂", "فك ضغط", zip1, BLUE, v -> unzipSel()), new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(act("🗜️", "ضغط", n > 0, BLUE, v -> zipSel()), new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(act("⬇️", "تحميل", n > 0, BLUE, v -> downloadSel()), new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(act("🗑️", "حذف", n > 0, RED, v -> deleteSel()), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout w = vbox();
        View d = new View(this);
        d.setBackgroundColor(0x2F000000);
        w.addView(d, lp(-1, 1));
        w.addView(b);
        return w;
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
                idle();
                if (screen == 2) renderBrowser();
                if (fer != null) alert("خطأ", fer);
            });
        });
    }

    void upMenu() {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"رفع ملفات", "رفع ملف zip وفك ضغطه"}, (d, i) -> pick(i == 1))
                .show();
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

    void downloadSel() {
        final List<String> ps = new ArrayList<>(sel);
        busy("جاري التجهيز...");
        ex.execute(() -> {
            try {
                final boolean one = ps.size() == 1;
                final byte[] data = one ? raw(ps.get(0)) : pack(ps);
                final String name = one ? base(ps.get(0)) : "FlashTools.zip";
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
        final Map<String, String> shas = new HashMap<>();
        for (JSONObject o : items) if (sel.contains(o.optString("path"))) shas.put(o.optString("path"), o.optString("sha"));
        new AlertDialog.Builder(this)
                .setTitle("حذف " + shas.size() + " ملف؟")
                .setMessage("لا يمكن التراجع عن الحذف")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, i) -> work("جاري الحذف...", () -> {
                    for (Map.Entry<String, String> e : shas.entrySet()) {
                        status("حذف " + base(e.getKey()));
                        del(e.getKey(), e.getValue());
                    }
                }))
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

    byte[] pack(List<String> ps) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (String p : ps) {
                status("ضغط " + base(p));
                z.putNextEntry(new ZipEntry(base(p)));
                z.write(raw(p));
                z.closeEntry();
            }
        }
        return bo.toByteArray();
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

    String cu(String p) { return API + "/repos/" + repo + "/contents/" + ep(p); }

    String msgOf(byte[] r, int code) {
        if (code == 401) return "التوكن غير صحيح أو منتهي";
        try { return new JSONObject(str(r)).optString("message", "خطأ " + code); } catch (Exception e) { return "خطأ " + code; }
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
        req("PUT", cu(p), JSON, body.toString().getBytes(StandardCharsets.UTF_8));
    }

    void del(String p, String sha) throws Exception {
        JSONObject body = new JSONObject().put("message", "Flash Tools").put("sha", sha);
        req("DELETE", cu(p), JSON, body.toString().getBytes(StandardCharsets.UTF_8));
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
