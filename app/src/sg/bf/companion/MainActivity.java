package sg.bf.companion;

// Brave Frontier unit guide. Everything (page, textures, unit data, icons) is inside the APK under
// assets/www and is served to the WebView as https://bf.app/... so it works offline on any phone.
// The one thing that goes online is "Check for updates" (off unless you turn it on), at the end of this file.
// A data update from it (the page, data and static files of a newer release, in files/www) is served before the APK's
// own copy until an APK update of that version or newer replaces it.

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final String HOST = "bf.app";
    private static MainActivity current;  // for Installed, Android's answer to an update install
    private WebView web;
    private boolean resumed;
    private volatile boolean usePack;  // files/www holds a newer release's page and data (a data update)

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = this;
        usePack = keepPack();
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
                if (path.contains("..")) return blocked();
                try {
                    File f = new File(getFilesDir(), "www" + path);  // a data update's copy first (no pictures in it)
                    InputStream in = usePack && f.isFile() ? new FileInputStream(f) : getAssets().open("www" + path);
                    return new WebResourceResponse(mime(path), mime(path).startsWith("text") ? "utf-8" : null, in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // a link to another site you tapped (a credit's channel): the phone's browser opens it, not the app
                if (req.isForMainFrame() && req.hasGesture() && ("https".equals(u.getScheme()) || "http".equals(u.getScheme()))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { /* no browser */ }
                }
                return true;
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

    /** Called from the page: pictures (PNG or GIF) and links go to the gallery or the share sheet, text and a copied picture to the clipboard. */
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

        /** A line of text (a unit's bfcompanion.app link) to Android's share sheet. */
        @JavascriptInterface
        public void shareText(String text) {
            runOnUiThread(() -> startActivity(Intent.createChooser(
                new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share link")));
        }

        @JavascriptInterface
        public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Brave Frontier units", text));
            });
        }

        /** A picture to the clipboard, not the gallery: kept in the app's cache (only the one copied last) and read
         *  through Pictures below by the app it's pasted in. False if it couldn't be written. */
        @JavascriptInterface
        public boolean copyImage(String base64Png, String name) {
            File dir = new File(getCacheDir(), Pictures.DIR);
            deleteAll(dir);
            String n = name.replaceFirst("(?i)\\.png$", "").replaceAll("[^\\w -]+", "").trim();
            String file = (n.isEmpty() ? "Picture" : n) + ".png";
            if (!dir.mkdirs()) return false;
            try (OutputStream out = new FileOutputStream(new File(dir, file))) {
                out.write(Base64.decode(base64Png, Base64.DEFAULT));
            } catch (IOException | IllegalArgumentException e) {
                deleteAll(dir);
                return false;
            }
            // a new address each time, so a chat app doesn't paste the picture it read before
            Uri uri = new Uri.Builder().scheme("content").authority(Pictures.AUTHORITY)
                .appendPath(Long.toString(System.currentTimeMillis())).appendPath(file).build();
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newUri(getContentResolver(), file, uri));
            });
            return true;
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

    /** The picture copied last (copyImage), read-only, for the app it's pasted in: not exported, Android's clipboard
     *  lets the app that reads the clip read this one file. Nothing else is served. */
    public static class Pictures extends ContentProvider {
        static final String AUTHORITY = "sg.bf.companion.pictures", DIR = "copied";

        private File file(Uri u) {
            String n = u.getLastPathSegment();
            if (n == null || !n.matches("[\\w -]+\\.png")) return null;
            File f = new File(new File(getContext().getCacheDir(), DIR), n);
            return f.isFile() ? f : null;
        }

        @Override public boolean onCreate() { return true; }

        @Override public String getType(Uri u) { return "image/png"; }

        @Override
        public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
            File f = file(u);
            if (f == null || mode.contains("w")) throw new FileNotFoundException("No such picture");
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        /** Its name and size, which some apps ask for before they read it. */
        @Override
        public Cursor query(Uri u, String[] cols, String sel, String[] args, String sort) {
            File f = file(u);
            if (f == null) return null;
            String[] c = cols != null ? cols : new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
            Object[] row = new Object[c.length];
            for (int i = 0; i < c.length; i++)
                row[i] = OpenableColumns.DISPLAY_NAME.equals(c[i]) ? f.getName() : OpenableColumns.SIZE.equals(c[i]) ? (Object) f.length() : null;
            MatrixCursor m = new MatrixCursor(c, 1);
            m.addRow(row);
            return m;
        }

        @Override public Uri insert(Uri u, ContentValues v) { return null; }

        @Override public int delete(Uri u, String sel, String[] args) { return 0; }

        @Override public int update(Uri u, ContentValues v, String sel, String[] args) { return 0; }
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
    // A release also carries update.json (the app code and pictures it was built with) and BF-Companion-www.zip (its page,
    // data and static files): an app built from the same code and pictures only needs that zip, a few MB.
    private static final String RELEASES = "https://api.github.com/repos/Nervequakes/BF-Companion/releases/latest";
    private static final String DOWNLOADS = "https://github.com/Nervequakes/BF-Companion/releases/download/";
    private static final Pattern PACK_FILE = Pattern.compile("(index\\.html|(data|static)/[A-Za-z0-9_./-]+)");
    private volatile String apkUrl, apkSha, packUrl, packSha, latest;  // from the last check
    private volatile long apkSize, packSize;
    private volatile boolean busy, dataOnly;
    private boolean askedAllow;
    private Intent confirm;  // Android's "Update this app?" screen, when it came while the app was in the background

    /** The version this app shows: a data update's, or the APK's own. */
    private String haveVersion() throws Exception {
        String apk = getPackageManager().getPackageInfo(getPackageName(), 0).versionName, pack = usePack ? packVersion() : null;
        return pack != null && newer(pack, apk) ? pack : apk;
    }

    private String packVersion() {
        try (InputStream in = new FileInputStream(new File(getFilesDir(), "www/data/version.json"))) {
            return new JSONObject(read(in)).optString("version", null);
        } catch (Exception e) { return null; }
    }

    /** A data update stays only while it's newer than the APK: an APK update of its version or later replaces it. */
    private boolean keepPack() {
        File dir = new File(getFilesDir(), "www");
        if (!dir.isDirectory()) return false;
        try {
            String pack = packVersion(), apk = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (pack != null && newer(pack, apk) && new File(dir, "index.html").isFile()) return true;
        } catch (Exception ignored) {}
        deleteAll(dir);
        return false;
    }

    private void check() {
        JSONObject r = new JSONObject();
        try {
            String have = haveVersion();
            r.put("current", have);
            HttpURLConnection c = open(RELEASES);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code == 403 || code == 429) throw new IOException("GitHub is busy right now. Try again in an hour.");
            if (code != 200) throw new IOException("GitHub answered with error " + code + ".");
            JSONObject rel = new JSONObject(read(c.getInputStream()));
            JSONArray assets = rel.getJSONArray("assets");
            JSONObject apk = null, meta = null, pack = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                String n = a.optString("name");
                if ("BF-Companion.apk".equals(n)) apk = a; else if ("update.json".equals(n)) meta = a; else if ("BF-Companion-www.zip".equals(n)) pack = a;
            }
            if (apk == null) throw new IOException("The newest release on GitHub has no APK.");
            latest = rel.getString("tag_name").replaceFirst("^v", "");
            String body = rel.optString("body"), digest = apk.optString("digest");
            Matcher sha = Pattern.compile("\\b[0-9a-f]{64}\\b").matcher(body);  // the release notes carry it too
            apkSha = digest.startsWith("sha256:") ? digest.substring(7) : sha.find() ? sha.group() : null;
            apkUrl = apk.getString("browser_download_url");
            apkSize = apk.optLong("size");
            dataOnly = false;
            boolean isNewer = newer(latest, have);
            if (isNewer && meta != null && pack != null) {
                try {  // built from this app's code and pictures? Then its data is all that's new
                    JSONObject m = new JSONObject(read(open(meta.getString("browser_download_url")).getInputStream()));
                    JSONObject mine = new JSONObject(read(getAssets().open("www/data/version.json")));
                    String pd = pack.optString("digest");
                    packSha = pd.startsWith("sha256:") ? pd.substring(7) : m.optJSONObject("pack") != null ? m.getJSONObject("pack").optString("sha256", null) : null;
                    packUrl = pack.getString("browser_download_url");
                    packSize = pack.optLong("size");
                    dataOnly = packSha != null && latest.equals(m.optString("version")) && !mine.optString("code").isEmpty()
                        && mine.optString("code").equals(m.optString("code")) && mine.optString("thumbs").equals(m.optString("thumbs"));
                } catch (Exception e) { dataOnly = false; }  // then the whole APK, as before
            }
            r.put("latest", latest).put("newer", isNewer).put("kind", dataOnly ? "data" : "apk").put("size", dataOnly ? packSize : apkSize)
             .put("date", rel.optString("published_at")).put("notes", notes(body));
        } catch (Exception e) {
            try { r.put("error", message(e)); } catch (Exception ignored) {}
        }
        js("onCheck", r.toString());
    }

    private void install() {
        if (busy || apkUrl == null) return;
        if (dataOnly) {  // no install: nothing for Android to ask
            busy = true;
            state("download", null);
            new Thread(this::downloadPack).start();
            return;
        }
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

    /** A data update: the release's zip, checked against GitHub's SHA-256, unpacked next to the app (only the page, data
     *  and static files: anything else in it stops it), then put in place in one step and the page reloaded. */
    private void downloadPack() {
        File dir = getFilesDir(), part = new File(dir, "www.zip.part"), fresh = new File(dir, "www.new"), live = new File(dir, "www"), old = new File(dir, "www.old");
        try {
            if (packSha == null) throw new IOException("GitHub gave no checksum for it, so it wasn't downloaded.");
            if (!packUrl.startsWith(DOWNLOADS)) throw new IOException("The update isn't on BF Companion's GitHub page.");
            HttpURLConnection c = open(packUrl);
            if (c.getResponseCode() != 200) throw new IOException("GitHub answered with error " + c.getResponseCode() + ".");
            long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : packSize, got = 0;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                int shown = -1;
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    got += n;
                    int pct = (int) (100 * got / Math.max(total, 1));
                    if (pct != shown) { shown = pct; js("onProgress", got + "," + total); }
                }
            }
            if (!hex(md.digest()).equals(packSha.toLowerCase())) throw new IOException("The download doesn't match GitHub's checksum, so it wasn't used.");
            deleteAll(fresh);
            unzip(part, fresh);
            String v;
            try (InputStream in = new FileInputStream(new File(fresh, "data/version.json"))) { v = new JSONObject(read(in)).optString("version"); }
            if (!v.equals(latest) || !new File(fresh, "index.html").isFile()) throw new IOException("The update is incomplete, so it wasn't used.");
            deleteAll(old);
            if (live.exists() && !live.renameTo(old)) throw new IOException("Couldn't replace the guide's files.");
            if (!fresh.renameTo(live)) { old.renameTo(live); throw new IOException("Couldn't put the guide's new files in place."); }
            deleteAll(old);
            usePack = true;
            busy = false;
            state("applied", null);
            runOnUiThread(() -> web.postDelayed(() -> { web.clearCache(false); web.reload(); }, 1200));
        } catch (Exception e) {
            deleteAll(fresh);
            busy = false;
            state("error", message(e));
        } finally {
            part.delete();
        }
    }

    private static void unzip(File zip, File to) throws IOException {
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(zip))) {
            byte[] buf = new byte[1 << 16];
            for (ZipEntry e; (e = next(z)) != null; ) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                if (n.contains("..") || !PACK_FILE.matcher(n).matches()) throw new IOException("The update has a file it shouldn't (" + n + ").");
                File f = new File(to, n);
                File p = f.getParentFile();
                if (p != null && !p.isDirectory() && !p.mkdirs()) throw new IOException("Couldn't unpack the update.");
                try (OutputStream out = new FileOutputStream(f)) {
                    for (int k; (k = z.read(buf)) > 0; ) {
                        if ((total += k) > (64L << 20)) throw new IOException("The update is too big.");
                        out.write(buf, 0, k);
                    }
                }
            }
        }
    }

    /** The zip's next file; Android itself refuses a name that climbs out of the folder ("../"): said the same way. */
    private static ZipEntry next(ZipInputStream z) throws IOException {
        try { return z.getNextEntry(); }
        catch (java.util.zip.ZipException e) { throw new IOException("The update has a file it shouldn't (" + e.getMessage() + ")."); }
    }

    private static void deleteAll(File f) {
        File[] in = f.listFiles();
        if (in != null) for (File x : in) deleteAll(x);
        f.delete();
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
