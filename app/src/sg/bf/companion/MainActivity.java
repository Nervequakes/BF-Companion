package sg.bf.companion;

// Brave Frontier unit guide. Everything (page, textures, unit data, icons) is inside the APK under
// assets/www and is served to the WebView as https://bf.app/... so it works offline on any phone.
// The one thing that goes online is "Check for updates" (off unless you turn it on), at the end of this file.

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String HOST = "bf.app";
    private static MainActivity current;  // for Installed, Android's answer to an update install
    private WebView web;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = this;
        getWindow().setStatusBarColor(Color.parseColor("#161922"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b0a14"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setUseWideViewPort(true);        // honour the page's <meta viewport> (phone width layout)
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);                // the page sizes its own text; ignore system font scaling quirks
        web.addJavascriptInterface(new Bridge(), "BFApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return blocked();  // nothing leaves the phone
                String path = u.getPath() == null || u.getPath().equals("/") ? "/index.html" : u.getPath();
                try {
                    InputStream in = getAssets().open("www" + path);
                    return new WebResourceResponse(mime(path), mime(path).startsWith("text") ? "utf-8" : null, in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return !HOST.equals(req.getUrl().getHost());
            }
        });
        setContentView(web);
        darkSystemBars();
        if (state != null) web.restoreState(state); else web.loadUrl("https://" + HOST + "/index.html");
    }

    /** The phone's own bottom bar (gesture handle or buttons): left unset, some phones paint it white under the page's
     *  tab bar. It gets the tab bar's steel colour, and both system bars get light icons. */
    private void darkSystemBars() {
        Window w = getWindow();
        w.setNavigationBarColor(Color.parseColor("#1c2129"));
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);  // no grey scrim over it
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) c.setSystemBarsAppearance(0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            View d = w.getDecorView();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
            d.setSystemUiVisibility(d.getSystemUiVisibility() & ~light);
        }
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "utf-8", 403, "Offline", null, null);
    }

    private static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "text/javascript";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg")) return "image/jpeg";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    /** Called from the page: pictures (PNG or GIF) go to the gallery or the share sheet, text to the clipboard. */
    private class Bridge {
        @JavascriptInterface
        public void shareImage(String base64Png, String name, boolean share) { shareFile(base64Png, name, "image/png", share); }

        /** A picture of any image type (a PNG card, a GIF of a battle sprite). */
        @JavascriptInterface
        public void shareFile(String base64, String name, String mime, boolean share) {
            runOnUiThread(() -> {
                Uri uri = savePicture(base64, name, mime);
                if (uri == null) { toast("Couldn't save the picture"); return; }
                if (!share) { toast("Saved to Pictures/BF Companion"); return; }
                Intent i = new Intent(Intent.ACTION_SEND).setType(mime)
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share picture"));
            });
        }

        @JavascriptInterface
        public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Brave Frontier units", text));
            });
        }

        /** Asks GitHub for the newest release; the page hears back through BFUpdate.onCheck. */
        @JavascriptInterface
        public void checkUpdate() { new Thread(MainActivity.this::check).start(); }

        /** Downloads and installs the release the last check found (never an address from the page). */
        @JavascriptInterface
        public void installUpdate() { runOnUiThread(MainActivity.this::install); }
    }

    private Uri savePicture(String base64, String name, String mime) {
        if (Build.VERSION.SDK_INT < 29) return null;  // needs scoped storage (Android 10+)
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, mime);
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BF Companion");
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return null;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            out.write(Base64.decode(base64, Base64.DEFAULT));
            return uri;
        } catch (IOException | NullPointerException | IllegalArgumentException e) {
            getContentResolver().delete(uri, null, null);  // no empty picture left in the gallery
            return null;
        }
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() {
        // the page steps back (unit page -> list, other tab -> Units); at the start it lets the app close
        web.evaluateJavascript("window.BFBack ? BFBack() : false",
            handled -> { if (!"true".equals(handled)) MainActivity.super.onBackPressed(); });
    }

    @Override
    protected void onResume() {
        super.onResume();
        current = this;  // the one in front, also after a second copy of this screen was opened and closed
        resumed = true;
        if (confirm != null) { Intent c = confirm; confirm = null; startActivity(c); }
        if (askedAllow) {  // back from "Install unknown apps"
            askedAllow = false;
            if (getPackageManager().canRequestPackageInstalls()) install(); else state("notallowed", null);
        }
    }

    @Override
    protected void onPause() { super.onPause(); resumed = false; }

    @Override
    protected void onDestroy() { if (current == this) current = null; super.onDestroy(); }

    // ---------------- updates: only when you ask (About → Privacy: the switch or "Check for updates") ----------------
    // GitHub's API names the newest release. "Download and install" streams its APK straight into Android's installer,
    // checks it against the SHA-256 GitHub gives for it, and Android asks before putting it over this version (it only
    // takes an APK signed with the same key). The page itself still can't load anything from the internet (above).
    private static final String RELEASES = "https://api.github.com/repos/Nervequakes/BF-Companion/releases/latest";
    private static final String DOWNLOADS = "https://github.com/Nervequakes/BF-Companion/releases/download/";
    private volatile String apkUrl, apkSha;  // from the last check
    private volatile long apkSize;
    private volatile boolean busy;
    private boolean askedAllow;
    private Intent confirm;  // Android's "Update this app?" screen, when it came while the app was in the background

    private void check() {
        JSONObject r = new JSONObject();
        try {
            String have = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            r.put("current", have);
            HttpURLConnection c = open(RELEASES);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code == 403 || code == 429) throw new IOException("GitHub is busy right now. Try again in an hour.");
            if (code != 200) throw new IOException("GitHub answered with error " + code + ".");
            JSONObject rel = new JSONObject(read(c.getInputStream()));
            JSONArray assets = rel.getJSONArray("assets");
            JSONObject apk = null;
            for (int i = 0; i < assets.length(); i++)
                if ("BF-Companion.apk".equals(assets.getJSONObject(i).optString("name"))) apk = assets.getJSONObject(i);
            if (apk == null) throw new IOException("The newest release on GitHub has no APK.");
            String latest = rel.getString("tag_name").replaceFirst("^v", ""), body = rel.optString("body"), digest = apk.optString("digest");
            Matcher sha = Pattern.compile("\\b[0-9a-f]{64}\\b").matcher(body);  // the release notes carry it too
            apkSha = digest.startsWith("sha256:") ? digest.substring(7) : sha.find() ? sha.group() : null;
            apkUrl = apk.getString("browser_download_url");
            apkSize = apk.optLong("size");
            r.put("latest", latest).put("newer", newer(latest, have)).put("size", apkSize)
             .put("date", rel.optString("published_at")).put("notes", notes(body));
        } catch (Exception e) {
            try { r.put("error", message(e)); } catch (Exception ignored) {}
        }
        js("onCheck", r.toString());
    }

    private void install() {
        if (busy || apkUrl == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            askedAllow = true;  // Android's own switch for this app, then onResume carries on
            state("allow", null);
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        busy = true;
        state("download", null);
        new Thread(this::download).start();
    }

    private void download() {
        PackageInstaller pi = getPackageManager().getPackageInstaller();
        int id = -1;
        try {
            for (PackageInstaller.SessionInfo s : pi.getMySessions()) pi.abandonSession(s.getSessionId());  // one cut short before
            if (apkSha == null) throw new IOException("GitHub gave no checksum for it, so it wasn't downloaded.");
            if (!apkUrl.startsWith(DOWNLOADS)) throw new IOException("The APK isn't on BF Companion's GitHub page.");
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(getPackageName());
            p.setSize(apkSize);
            id = pi.createSession(p);
            HttpURLConnection c = open(apkUrl);  // GitHub sends it on to its file server
            if (c.getResponseCode() != 200) throw new IOException("GitHub answered with error " + c.getResponseCode() + ".");
            long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : apkSize, got = 0;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("base.apk", 0, total > 0 ? total : -1)) {
                    byte[] buf = new byte[1 << 16];
                    int shown = -1;
                    for (int n; (n = in.read(buf)) > 0; ) {
                        out.write(buf, 0, n);
                        md.update(buf, 0, n);
                        got += n;
                        int pct = (int) (100 * got / Math.max(total, 1));
                        if (pct != shown) { shown = pct; js("onProgress", got + "," + total); }
                    }
                    s.fsync(out);
                }
                if (!hex(md.digest()).equals(apkSha.toLowerCase())) throw new IOException("The download doesn't match GitHub's checksum, so it wasn't installed.");
                state("install", null);
                Intent answer = new Intent(this, Installed.class);
                s.commit(PendingIntent.getBroadcast(this, id, answer,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)).getIntentSender());
            }
        } catch (Exception e) {
            if (id >= 0) try { pi.abandonSession(id); } catch (Exception ignored) {}
            busy = false;
            state("error", message(e));
        }
    }

    /** Android's answer to an install: first "the user has to confirm" (its screen is shown), then how it ended. */
    public static class Installed extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent i) { if (current != null) current.installed(i); }
    }

    private void installed(Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent ask = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (ask == null) { busy = false; state("error", "Android didn't ask to install it."); return; }
            if (resumed) startActivity(ask); else confirm = ask;  // an app in the background can't open it
            return;
        }
        busy = false;
        if (status == PackageInstaller.STATUS_SUCCESS) state("done", null);  // usually this app has been closed by now
        else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) state("cancelled", null);
        else state("error", failure(status, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)));
    }

    /** Android's reason in words, with its own message after it for the rest. */
    private static String failure(int status, String msg) {
        if (status == PackageInstaller.STATUS_FAILURE_STORAGE) return "There isn't enough free space on this phone for the update.";
        String why = msg == null ? "" : " (" + msg + ")";
        if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
            return "Android couldn't put it over the BF Companion on this phone" + why + ". Get it from GitHub instead.";
        return "Android couldn't install it" + why + ".";
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "BF-Companion");  // GitHub's API wants one
        return c;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = i.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** "5.6.10" is newer than "5.6.9": part by part, as numbers. */
    private static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) { try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (NumberFormatException e) { return 0; } }

    /** The release's "- …" lines (what changed), without the Markdown. */
    private static JSONArray notes(String body) {
        JSONArray out = new JSONArray();
        for (String l : body.split("\n")) {
            if (!l.startsWith("- ") || out.length() >= 8) continue;
            out.put(l.substring(2).replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1").replaceAll("[*_`]", "").trim());
        }
        return out;
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return s.toString();
    }

    private static String message(Exception e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException || e instanceof SocketTimeoutException)
            return "Couldn't reach GitHub. Check the internet connection.";
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    /** Tells the page: window.BFUpdate.<fn>(<args>). */
    private void js(String fn, String args) {
        runOnUiThread(() -> web.evaluateJavascript("window.BFUpdate && BFUpdate." + fn + "(" + args + ")", null));
    }

    private void state(String s, String msg) { js("onState", JSONObject.quote(s) + "," + (msg == null ? "null" : JSONObject.quote(msg))); }
}
