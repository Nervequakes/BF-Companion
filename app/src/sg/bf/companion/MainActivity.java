package sg.bf.companion;

// Brave Frontier unit guide. Everything (page, textures, unit data, icons) is inside the APK under
// assets/www and is served to the WebView as https://bf.app/... so it works offline on any phone.

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String HOST = "bf.app";
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        if (state != null) web.restoreState(state); else web.loadUrl("https://" + HOST + "/index.html");
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

    /** Called from the page: pictures go to the gallery or the share sheet, text to the clipboard. */
    private class Bridge {
        @JavascriptInterface
        public void shareImage(String base64Png, String name, boolean share) {
            runOnUiThread(() -> {
                Uri uri = savePicture(base64Png, name);
                if (uri == null) { toast("Couldn't save the picture"); return; }
                if (!share) { toast("Saved to Pictures/BF Companion"); return; }
                Intent i = new Intent(Intent.ACTION_SEND).setType("image/png")
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
    }

    private Uri savePicture(String base64Png, String name) {
        if (Build.VERSION.SDK_INT < 29) return null;  // needs scoped storage (Android 10+)
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BF Companion");
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return null;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            out.write(Base64.decode(base64Png, Base64.DEFAULT));
            return uri;
        } catch (IOException | NullPointerException e) {
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
}
