package com.daniel.cybermasterchef;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

/**
 * Cyber Master Chef runs as a web page bundled inside the app (assets/index.html),
 * with CyberChef's engine in assets/cyberchef. Everything works offline; the app
 * never connects to the internet.
 */
public class MainActivity extends Activity {

    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String START_URL = ORIGIN + "/assets/index.html";
    private static final int REQ_OPEN_FILE = 1;
    private static final int REQ_SAVE_FILE = 2;
    private static final int REQ_CAMERA = 3;
    /** Shared or opened files larger than this are refused to keep the app from running out of memory. */
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    private WebView webView;
    private String pendingSharedText;
    private volatile Uri pendingSharedFile;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingSaveBytes;
    private PermissionRequest pendingCameraRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Serving assets from an https:// address lets the page use the phone's
        // built-in crypto and run CyberChef's engine in a background worker.
        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        // The page talks to the phone through "AndroidBridge". Where the phone's WebView
        // supports it, the bridge is only given to the app's own page (not to the output
        // view, which can show content made from your input). Older WebViews get the
        // classic bridge, and the page then turns off scripts in the output view.
        final Bridge bridge = new Bridge();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "AndroidBridge", Collections.singleton(ORIGIN),
                    (view, message, sourceOrigin, isMainFrame, reply) -> {
                        if (isMainFrame) bridge.onMessage(message, reply);
                    });
        } else {
            webView.addJavascriptInterface(bridge, "AndroidBridge");
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("appassets.androidplatform.net".equals(url.getHost())) return false;
                // Any outside link opens in the normal browser instead of inside the app.
                try { startActivity(new Intent(Intent.ACTION_VIEW, url)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                deliverShared();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            // "Open file" in the page uses Android's file picker.
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(i, REQ_OPEN_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }

            // The QR scanner asks for the camera. Only the camera is ever granted.
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsCamera = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
                    }
                    if (!wantsCamera) { request.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingCameraRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    }
                });
            }
        });

        readShare(getIntent());
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (readShare(intent)) deliverShared();
    }

    /** Picks up text or a file shared to the app from another app. */
    private boolean readShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return false;
        Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream != null) {
            pendingSharedFile = stream;
            return true;
        }
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text != null) {
            pendingSharedText = text;
            return true;
        }
        return false;
    }

    private void deliverShared() {
        if (webView == null) return;
        if (pendingSharedText != null) {
            String js = "window.receiveSharedText&&window.receiveSharedText(" + JSONObject.quote(pendingSharedText) + ")";
            pendingSharedText = null;
            webView.evaluateJavascript(js, null);
        }
        if (pendingSharedFile != null) {
            // The page pulls the bytes with AndroidBridge.takeSharedFile().
            webView.evaluateJavascript("window.receiveSharedFile&&window.receiveSharedFile()", null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_FILE) {
            Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            if (fileCallback != null) fileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
            fileCallback = null;
        } else if (requestCode == REQ_SAVE_FILE) {
            byte[] bytes = pendingSaveBytes;
            pendingSaveBytes = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || bytes == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                out.write(bytes);
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Couldn't save the file", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQ_CAMERA || pendingCameraRequest == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            pendingCameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            pendingCameraRequest.deny();
        }
        pendingCameraRequest = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Let the page close any open panel first; otherwise leave the app.
        webView.evaluateJavascript("window.handleBack ? window.handleBack() : false", value -> {
            if (!"true".equals(value)) super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "shared file" : last;
    }

    /** What the page can ask the phone to do. */
    private class Bridge {
        /** New-style bridge: {id, method, args} in, {id, value} or {id, error} out. */
        void onMessage(WebMessageCompat message, JavaScriptReplyProxy reply) {
            final String data = message.getData();
            // Reading files can take a moment, so do the work off the screen's thread.
            new Thread(() -> {
                String out;
                try {
                    JSONObject req = new JSONObject(data);
                    JSONObject res = new JSONObject();
                    res.put("id", req.opt("id"));
                    try {
                        res.put("value", call(req.getString("method"), req.optJSONArray("args")));
                    } catch (Exception e) {
                        res.put("error", String.valueOf(e.getMessage()));
                    }
                    out = res.toString();
                } catch (Exception e) {
                    return;
                }
                final String msg = out;
                runOnUiThread(() -> reply.postMessage(msg));
            }).start();
        }

        /** Classic bridge for older WebViews. */
        @JavascriptInterface
        public String call(String method, String argsJson) {
            try {
                return call(method, new JSONArray(argsJson));
            } catch (Exception e) {
                return "";
            }
        }

        String call(String method, JSONArray args) throws Exception {
            if (args == null) args = new JSONArray();
            switch (method) {
                case "copy": copy(args.getString(0)); return "";
                case "paste": return paste();
                case "shareText": shareText(args.getString(0)); return "";
                case "saveFile": saveFile(args.getString(0), args.getString(1), args.optString(2)); return "";
                case "takeSharedFile": return takeSharedFile();
                default: throw new Exception("Unknown request " + method);
            }
        }

        void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Cyber Master Chef", text));
        }

        String paste() {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return "";
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
            return t == null ? "" : t.toString();
        }

        /** Opens Android's share menu with the given text. */
        void shareText(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> {
                try { startActivity(Intent.createChooser(i, "Share output")); } catch (Exception ignored) { }
            });
        }

        /** Asks where to save, then writes the bytes (sent as Base64) there. */
        void saveFile(String base64, String fileName, String mimeType) {
            final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
            i.putExtra(Intent.EXTRA_TITLE, fileName);
            runOnUiThread(() -> {
                pendingSaveBytes = bytes;
                try { startActivityForResult(i, REQ_SAVE_FILE); } catch (Exception ignored) { pendingSaveBytes = null; }
            });
        }

        /** Returns {name, base64} for a file shared to the app, or {error}. Called once per share. */
        String takeSharedFile() {
            Uri uri = pendingSharedFile;
            pendingSharedFile = null;
            JSONObject o = new JSONObject();
            try {
                if (uri == null) return "{}";
                o.put("name", displayName(uri));
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[65536];
                    int n;
                    long total = 0;
                    while ((n = in.read(chunk)) > 0) {
                        total += n;
                        if (total > MAX_FILE_BYTES) {
                            o.put("error", "That file is larger than 64 MB, which is too big to open here.");
                            return o.toString();
                        }
                        buf.write(chunk, 0, n);
                    }
                    o.put("base64", Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP));
                }
            } catch (Exception e) {
                try { o.put("error", "Couldn't read the shared file."); } catch (Exception ignored) { }
            }
            return o.toString();
        }
    }
}
