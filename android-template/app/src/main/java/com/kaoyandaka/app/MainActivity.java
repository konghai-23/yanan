package com.kaoyandaka.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.ValueCallback;
import java.io.ByteArrayOutputStream;
import androidx.core.content.FileProvider;
import java.io.File;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> mFileMsg = null;
    private Uri mCaptureUri = null;
    private static final int REQ_PICK = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        makeEdgeToEdge();
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(msg)
                    .setPositiveButton("确定", (d, w) -> r.confirm())
                    .setOnCancelListener(d -> r.cancel())
                    .show();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(msg)
                    .setPositiveButton("确定", (d, w) -> r.confirm())
                    .setNegativeButton("取消", (d, w) -> r.cancel())
                    .setOnCancelListener(d -> r.cancel())
                    .show();
                return true;
            }
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams params) {
                if (mFileMsg != null) { mFileMsg.onReceiveValue(null); }
                mFileMsg = filePathCallback;
                try {
                    if (params != null && params.isCaptureEnabled()) {
                        File dir = new File(getCacheDir(), "capture");
                        if (!dir.exists()) dir.mkdirs();
                        File f = new File(dir, "cap_" + System.currentTimeMillis() + ".jpg");
                        mCaptureUri = FileProvider.getUriForFile(MainActivity.this, "com.kaoyandaka.app.fileprovider", f);
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, mCaptureUri);
                        cam.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        startActivityForResult(cam, REQ_PICK);
                        return true;
                    }
                    Intent chooser = params.createIntent();
                    startActivityForResult(chooser, REQ_PICK);
                    return true;
                } catch (Exception e) {
                    if (mFileMsg != null) { mFileMsg.onReceiveValue(null); mFileMsg = null; }
                    return false;
                }
            }
        });
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    if (name == null || name.length() == 0) name = "kaoyan_paper_" + System.currentTimeMillis() + ".pdf";
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm != null) dm.enqueue(req);
                } catch (Exception ignored) {}
            }
        });
        web.addJavascriptInterface(new ThemeBridge(), "AndroidTheme");
        setContentView(web);
        web.loadUrl("file:///android_asset/public/index.html");
    }

    private void makeEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        int vis = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().getDecorView().setSystemUiVisibility(vis);
    }

    private void setStatusIcons(boolean lightIcons) {
        int vis = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (lightIcons) vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(vis);
    }

    private class ThemeBridge {
        @JavascriptInterface
        public void apply(final String mode) {
            runOnUiThread(() -> setStatusIcons(!"dark".equals(mode)));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK && mFileMsg != null) {
            Uri result = null;
            if (resultCode == RESULT_OK) {
                if (mCaptureUri != null && new File(mCaptureUri.getPath()).exists()) {
                    pushPhotoToJs(new File(mCaptureUri.getPath()));
                } else if (data != null && data.getData() != null) { result = data.getData(); }
            }
            mFileMsg.onReceiveValue(result != null ? new Uri[]{result} : null);
            mFileMsg = null;
            mCaptureUri = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void pushPhotoToJs(final File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int w = o.outWidth, h = o.outHeight;
            int sample = 1;
            while (Math.max(w, h) / sample > 1600) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            if (b == null) return;
            float max = 1200f;
            float sc = Math.min(1f, max / (float) Math.max(b.getWidth(), b.getHeight()));
            Bitmap scaled = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * sc)), Math.max(1, Math.round(b.getHeight() * sc)), true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, bos);
            byte[] bytes = bos.toByteArray();
            String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            final String js = "window.__photoFromNative&&window.__photoFromNative('data:image/jpeg;base64," + b64 + "')";
            web.post(new Runnable() { public void run() { try { web.evaluateJavascript(js, null); } catch (Exception ignored) {} } });
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) { web.goBack(); } else { super.onBackPressed(); }
    }
}
