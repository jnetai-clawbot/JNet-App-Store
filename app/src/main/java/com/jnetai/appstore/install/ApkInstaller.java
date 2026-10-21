package com.jnetai.appstore.install;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.jnetai.appstore.model.StoreApp;
import com.jnetai.appstore.util.DebugLogger;
import com.jnetai.appstore.util.ErrorHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads an APK from the store and hands it to the system package installer.
 */
public final class ApkInstaller {

    private static final String TAG = "ApkInstaller";
    private static final String DOWNLOAD_SUBDIR = "Download/JNetAppStore";

    public interface ProgressListener {
        void onProgress(int percent);
    }

    private ApkInstaller() {
    }

    public static boolean canInstall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    public static void openInstallPermissionSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.INSTALL,
                    "Failed to open unknown-sources settings", e, context);
        }
    }

    public static File download(Context context, StoreApp app, ProgressListener listener)
            throws Exception {
        String apkUrl = app.getApkUrl();
        if (apkUrl == null || apkUrl.isEmpty()) {
            throw new Exception("No APK URL available for " + app.getName());
        }
        DebugLogger.log(TAG, "Downloading " + app.getName() + " from " + apkUrl);

        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "JNetAppStore");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Could not create download directory: " + dir.getAbsolutePath());
        }
        String safeName = sanitize(app.getName());
        File outFile = new File(dir, safeName + ".apk");
        File tmpFile = new File(dir, safeName + ".apk.part");

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apkUrl).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "JNetAppStore-Android");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new Exception("Download failed, HTTP " + code + " for " + apkUrl);
            }

            int total = conn.getContentLength();
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tmpFile)) {
                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int read;
                int lastPct = -1;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (listener != null && total > 0) {
                        int pct = (int) ((downloaded * 100) / total);
                        if (pct != lastPct) {
                            lastPct = pct;
                            listener.onProgress(pct);
                        }
                    }
                }
            }

            if (outFile.exists() && !outFile.delete()) {
                DebugLogger.warn(TAG, "Could not delete old APK: " + outFile.getAbsolutePath());
            }
            if (!tmpFile.renameTo(outFile)) {
                throw new Exception("Could not finalize downloaded APK");
            }
            DebugLogger.log(TAG, "Downloaded to " + outFile.getAbsolutePath()
                    + " (" + outFile.length() + " bytes)");
            return outFile;
        } catch (Exception e) {
            if (tmpFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmpFile.delete();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static void install(Context context, File apkFile) {
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apkFile);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            DebugLogger.log(TAG, "Launching installer for " + apkFile.getName());
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.INSTALL,
                    "Failed to launch installer for " + apkFile.getName(), e, context);
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "app";
        }
        return name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
