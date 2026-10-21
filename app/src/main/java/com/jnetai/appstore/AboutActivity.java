package com.jnetai.appstore;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.jnetai.appstore.data.StoreRepository;
import com.jnetai.appstore.util.DebugLogger;
import com.jnetai.appstore.util.ErrorHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "AboutActivity";

    private static final String GITHUB_REPO = "jnetai-clawbot/JNet-App-Store";
    private static final String GITHUB_RELEASES_URL =
            "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String GITHUB_RELEASES_PAGE =
            "https://github.com/" + GITHUB_REPO + "/releases";
    private static final String PROJECT_URL = "https://jnetai.com";

    private TextView tvVersion;
    private TextView tvStoreUrl;
    private Button btnCheckUpdate;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_about);
            DebugLogger.log(TAG, "AboutActivity onCreate started");
            executor = Executors.newSingleThreadExecutor();

            tvVersion = findViewById(R.id.tvVersion);
            tvStoreUrl = findViewById(R.id.tvStoreUrl);
            btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
            Button btnShareApp = findViewById(R.id.btnShareApp);
            Button btnStoreUrl = findViewById(R.id.btnStoreUrl);
            Button btnOpenStore = findViewById(R.id.btnOpenStore);
            TextView tvAbout = findViewById(R.id.tvAbout);

            String versionName = resolveVersionName();
            tvVersion.setText("Version: " + versionName);
            tvAbout.setText("J~Net App Store\n\n"
                    + "Discover, download and install apps published by jnetai.com. "
                    + "The catalog is served from a self-hosted store and supports both "
                    + "GitHub release links and uploaded APK files.\n\n"
                    + "Made by jnetai.com");
            tvStoreUrl.setText("Store: " + StoreRepository.getStoreUrl(this));

            final String currentVersion = versionName;
            btnCheckUpdate.setOnClickListener(v -> checkForUpdate(currentVersion));
            btnShareApp.setOnClickListener(v -> shareApp());
            btnStoreUrl.setOnClickListener(v -> showStoreUrlDialog());
            btnOpenStore.setOnClickListener(v ->
                    openUrl(StoreRepository.getStoreUrl(this)));

            DebugLogger.log(TAG, "AboutActivity onCreate completed");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.ABOUT,
                    "Failed to initialise AboutActivity", e, this);
        }
    }

    private String resolveVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.ABOUT,
                    "Failed to read package version", e);
            return "1.0.0";
        }
    }

    private void checkForUpdate(String currentVersion) {
        try {
            btnCheckUpdate.setEnabled(false);
            btnCheckUpdate.setText("Checking…");
            DebugLogger.log(TAG, "Checking for updates, current=" + currentVersion);

            executor.execute(() -> {
                String latestTag = null;
                Exception failure = null;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(GITHUB_RELEASES_URL).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setRequestProperty("User-Agent", "JNetAppStore-Android");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        StringBuilder body = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(),
                                        StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                body.append(line);
                            }
                        }
                        latestTag = extractTag(body.toString());
                    } else {
                        failure = new Exception("GitHub returned HTTP " + code);
                    }
                } catch (Exception e) {
                    failure = e;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }

                final String tag = latestTag;
                final Exception err = failure;
                mainHandler.post(() -> onUpdateResult(tag, err, currentVersion));
            });
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.UPDATE,
                    "Failed to start update check", e, this);
        }
    }

    private void onUpdateResult(String latestTag, Exception failure, String currentVersion) {
        btnCheckUpdate.setEnabled(true);
        btnCheckUpdate.setText("Check for Update");
        try {
            if (failure != null) {
                ErrorHandler.handle(TAG, ErrorHandler.Code.UPDATE,
                        "Update check failed, falling back to browser", failure);
                new AlertDialog.Builder(this)
                        .setTitle("Update check failed")
                        .setMessage("Could not reach GitHub: " + failure.getMessage()
                                + "\n\nOpen the releases page instead?")
                        .setPositiveButton("Open", (d, w) -> openUrl(GITHUB_RELEASES_PAGE))
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
            if (latestTag == null || latestTag.isEmpty()) {
                Toast.makeText(this, "Could not determine latest version",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!latestTag.equalsIgnoreCase(currentVersion)
                    && !latestTag.equalsIgnoreCase("v" + currentVersion)) {
                new AlertDialog.Builder(this)
                        .setTitle("Update available")
                        .setMessage("Latest: " + latestTag + "\nInstalled: v" + currentVersion
                                + "\n\nOpen the releases page?")
                        .setPositiveButton("Open", (d, w) -> openUrl(GITHUB_RELEASES_PAGE))
                        .setNegativeButton("Later", null)
                        .show();
            } else {
                Toast.makeText(this, "You are up to date (v" + currentVersion + ")",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.UPDATE,
                    "Failed to process update result", e, this);
        }
    }

    private String extractTag(String json) {
        try {
            int idx = json.indexOf("\"tag_name\"");
            if (idx >= 0) {
                int start = json.indexOf('"', idx + 10) + 1;
                int end = json.indexOf('"', start);
                if (start > 0 && end > start) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.UPDATE,
                    "Failed to parse tag_name", e);
        }
        return null;
    }

    private void showStoreUrlDialog() {
        try {
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
            input.setTextColor(getResources().getColor(R.color.text_primary));
            input.setText(StoreRepository.getStoreUrl(this));
            input.setSelection(input.getText().length());

            new AlertDialog.Builder(this)
                    .setTitle("Store URL")
                    .setMessage("Point the app at your self-hosted store "
                            + "(e.g. https://apps.jnetai.com).")
                    .setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        StoreRepository.setStoreUrl(this, input.getText().toString());
                        tvStoreUrl.setText("Store: " + StoreRepository.getStoreUrl(this));
                        Toast.makeText(this, "Store URL saved", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.ABOUT,
                    "Failed to show store URL dialog", e, this);
        }
    }

    private void shareApp() {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "J~Net App Store");
            share.putExtra(Intent.EXTRA_TEXT,
                    "Check out the J~Net App Store — apps by jnetai.com!\n\n"
                            + GITHUB_RELEASES_PAGE + "\n\nMade by " + PROJECT_URL);
            startActivity(Intent.createChooser(share, "Share via"));
            DebugLogger.log(TAG, "Share intent sent");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.ABOUT,
                    "Failed to share app", e, this);
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.ABOUT,
                    "Failed to open URL " + url, e, this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
