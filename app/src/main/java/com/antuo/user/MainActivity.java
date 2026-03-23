package com.antuo.user;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.Manifest;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private ProgressBar progressBar;
    private static final String TARGET_URL = "http://175.178.45.188";

    // 文件上传相关
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraImageUri;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏沉浸式：隐藏状态栏 + 标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // 使用 XML 布局（确保 WebView 正确撑满屏幕）
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);
        progressBar = findViewById(R.id.progress_bar);

        // WebView 设置
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);
        settings.setGeolocationEnabled(true);   // 开启地理定位
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);  // 禁用缓存，每次加载最新页面
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // 标记 WebView 来自 APP（便于后端区分）
        settings.setUserAgentString(settings.getUserAgentString() + " AntuoApp/1.0");

        // WebView 背景透明（页面加载前不闪白）
        webView.setBackgroundColor(0x00000000);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                }
                // 处理 tel:/mailto: 等协议
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                // 页面加载完后设置背景为透明（不再需要蓝色背景）
                webView.setBackgroundColor(0xFF1565C0);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // ★ 地理定位权限：WebView 请求时自动授权（配合 ACCESS_FINE_LOCATION 权限）
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                // 先确保 Android 端已有定位权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, PERMISSION_REQUEST_CODE);
                }
                callback.invoke(origin, true, false); // 授权，不持久化
            }

            // ★ 文件选择器（Android 5.0+ WebView 上传必须实现此方法）
            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                // 取消上一次未完成的回调
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                    fileUploadCallback = null;
                }
                fileUploadCallback = filePathCallback;

                // 先申请权限（后台），同时立即弹出选择器
                requestPermissionsIfNeeded();
                openFileChooser();
                return true;
            }
        });

        webView.loadUrl(TARGET_URL);
    }

    /** 检查并请求存储/相机权限 */
    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.CAMERA
                };
            } else {
                perms = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                };
            }
            boolean needRequest = false;
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }
            if (needRequest) {
                requestPermissions(perms, PERMISSION_REQUEST_CODE);
            }
        }
    }

    /** 创建相机拍照临时文件 */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("ANTUO_" + timeStamp, ".jpg", storageDir);
    }

    /** 打开文件/图片选择器（含相机） */
    private void openFileChooser() {
        // 相机 Intent（FileProvider 提供 content:// URI）
        Intent cameraIntent = null;
        try {
            File photoFile = createImageFile();
            cameraImageUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                photoFile
            );
            cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            cameraImageUri = null;
            cameraIntent = null;
        }

        // 相册选择器 - 扩展支持类型
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryIntent.setType("image/*");
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, 
            new String[]{"image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif"});
        galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(galleryIntent, "选择文件");
        if (cameraIntent != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        }

        // 检查是否有可用的应用
        if (chooser.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
        } else {
            // 回退方案：只打开相册
            startActivityForResult(galleryIntent, FILE_CHOOSER_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST) return;
        if (fileUploadCallback == null) return;

        Uri[] results = null;
        try {
            if (resultCode == Activity.RESULT_OK) {
                if (data == null || data.getData() == null) {
                    // 相机拍照结果
                    if (cameraImageUri != null) {
                        results = new Uri[]{cameraImageUri};
                    }
                } else {
                    // 相册选择结果
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else {
                        String dataStr = data.getDataString();
                        if (dataStr != null) {
                            results = new Uri[]{Uri.parse(dataStr)};
                        }
                    }
                }
            }
        } catch (Exception e) {
            results = null;
        }

        // 确保回调被调用，避免WebView卡死
        try {
            fileUploadCallback.onReceiveValue(results);
        } catch (Exception e) {
            // 忽略回调异常
        }
        fileUploadCallback = null;
        cameraImageUri = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted && fileUploadCallback != null) {
                // 权限被拒绝，取消上传
                fileUploadCallback.onReceiveValue(null);
                fileUploadCallback = null;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
