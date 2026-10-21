package com.jnetai.appstore;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.jnetai.appstore.adapter.AppAdapter;
import com.jnetai.appstore.data.StoreRepository;
import com.jnetai.appstore.install.ApkInstaller;
import com.jnetai.appstore.model.StoreApp;
import com.jnetai.appstore.net.StoreClient;
import com.jnetai.appstore.net.StoreException;
import com.jnetai.appstore.util.DebugLogger;
import com.jnetai.appstore.util.ErrorHandler;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AppAdapter.Listener {

    private static final String TAG = "MainActivity";

    private RecyclerView recyclerApps;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvStatus;
    private TextView tvEmpty;
    private AppAdapter adapter;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            DebugLogger.log(TAG, "MainActivity onCreate started");

            executor = Executors.newFixedThreadPool(2);

            tvStatus = findViewById(R.id.tvStatus);
            tvEmpty = findViewById(R.id.tvEmpty);
            swipeRefresh = findViewById(R.id.swipeRefresh);
            recyclerApps = findViewById(R.id.recyclerApps);
            Button btnRefresh = findViewById(R.id.btnRefresh);
            Button btnAbout = findViewById(R.id.btnAbout);

            adapter = new AppAdapter(this);
            recyclerApps.setLayoutManager(new LinearLayoutManager(this));
            recyclerApps.setAdapter(adapter);

            swipeRefresh.setColorSchemeColors(getResources().getColor(R.color.accent_red));
            swipeRefresh.setProgressBackgroundColorSchemeColor(
                    getResources().getColor(R.color.dark_secondary));
            swipeRefresh.setOnRefreshListener(this::loadCatalog);

            btnRefresh.setOnClickListener(v -> loadCatalog());
            btnAbout.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, AboutActivity.class)));

            loadCatalog();
            DebugLogger.log(TAG, "MainActivity onCreate completed");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.MAIN,
                    "Failed to initialise MainActivity", e, this);
        }
    }

    private void loadCatalog() {
        try {
            final String storeUrl = StoreRepository.getStoreUrl(this);
            tvStatus.setText("Loading " + storeUrl + " …");
            swipeRefresh.setRefreshing(true);
            DebugLogger.log(TAG, "loadCatalog from " + storeUrl);

            executor.execute(() -> {
                try {
                    final List<StoreApp> apps = StoreClient.fetchCatalog(storeUrl);
                    StoreRepository.saveCatalogCache(MainActivity.this, apps);
                    mainHandler.post(() -> {
                        swipeRefresh.setRefreshing(false);
                        adapter.submit(apps);
                        tvEmpty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
                        tvStatus.setText(apps.size() + " app(s) • " + storeUrl);
                    });
                } catch (Exception e) {
                    DebugLogger.warn(TAG, "Catalog fetch failed: " + e.getMessage());
                    final List<StoreApp> cached = StoreRepository.loadCatalogCache(MainActivity.this);
                    mainHandler.post(() -> {
                        swipeRefresh.setRefreshing(false);
                        if (!cached.isEmpty()) {
                            adapter.submit(cached);
                            tvEmpty.setVisibility(View.GONE);
                            tvStatus.setText("Offline — showing " + cached.size()
                                    + " cached app(s). Pull to retry.");
                        } else {
                            adapter.submit(cached);
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvStatus.setText("Store unavailable. Pull to retry.");
                        }
                        showStoreError(e, storeUrl);
                    });
                }
            });
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.MAIN,
                    "Failed to start catalog load", e, this);
        }
    }

    private void showStoreError(Exception e, String storeUrl) {
        try {
            ErrorHandler.handle(TAG, ErrorHandler.Code.NETWORK,
                    "Store catalog load failed", e);
            new AlertDialog.Builder(this)
                    .setTitle("Store unavailable")
                    .setMessage("Could not reach the J~Net App Store at\n" + storeUrl
                            + "\n\n" + e.getMessage()
                            + "\n\nOpen the store in a browser instead?")
                    .setPositiveButton("Open browser", (d, w) -> openUrl(storeUrl))
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception ex) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.MAIN,
                    "Failed to show store error dialog", ex);
        }
    }

    @Override
    public void onInstall(StoreApp app, int position) {
        try {
            DebugLogger.log(TAG, "Install requested for " + app.debugString());
            if (!app.hasInstallTarget()) {
                Toast.makeText(this, "No APK available for this app",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!ApkInstaller.canInstall(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("Permission required")
                        .setMessage("Allow J~Net App Store to install unknown apps, "
                                + "then tap Get again.")
                        .setPositiveButton("Open settings", (d, w) ->
                                ApkInstaller.openInstallPermissionSettings(this))
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
            downloadAndInstall(app);
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.INSTALL,
                    "Install flow failed for " + app.getName(), e, this);
        }
    }

    private void downloadAndInstall(StoreApp app) {
        showProgressDialog("Downloading " + app.getName() + "…");
        executor.execute(() -> {
            try {
                File apk = ApkInstaller.download(this, app, percent ->
                        mainHandler.post(() -> updateProgress(percent)));
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    ApkInstaller.install(this, apk);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    ErrorHandler.handle(TAG, ErrorHandler.Code.INSTALL,
                            "Download failed for " + app.getName(), e, this);
                    offerBrowserFallback(app, e);
                });
            }
        });
    }

    private void offerBrowserFallback(StoreApp app, Exception e) {
        try {
            String fallback = app.getGithubUrl();
            if (fallback == null || fallback.isEmpty()) {
                fallback = StoreRepository.getStoreUrl(this);
            }
            final String url = fallback;
            new AlertDialog.Builder(this)
                    .setTitle("Download failed")
                    .setMessage(e.getMessage() + "\n\nOpen the download page in a browser?")
                    .setPositiveButton("Open", (d, w) -> openUrl(url))
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception ex) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.INSTALL,
                    "Failed to show download fallback", ex);
        }
    }

    @Override
    public void onOpenDetails(StoreApp app) {
        try {
            if (app.isGithub() && app.getGithubUrl() != null
                    && !app.getGithubUrl().isEmpty()) {
                openUrl(app.getGithubUrl());
            } else {
                Toast.makeText(this, app.getName()
                        + (app.getVersion().isEmpty() ? "" : " v" + app.getVersion()),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.MAIN,
                    "Failed to open details for " + app.getName(), e, this);
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.MAIN,
                    "Failed to open URL " + url, e, this);
        }
    }

    private void showProgressDialog(String title) {
        try {
            dismissProgressDialog();
            View view = getLayoutInflater().inflate(R.layout.dialog_progress, null);
            ((TextView) view.findViewById(R.id.tvDialogTitle)).setText(title);
            progressDialog = new AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.MAIN,
                    "Failed to show progress dialog", e);
        }
    }

    private void updateProgress(int percent) {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                ProgressBar bar = progressDialog.findViewById(R.id.progressBar);
                TextView tv = progressDialog.findViewById(R.id.tvDialogPercent);
                if (bar != null) {
                    bar.setProgress(percent);
                }
                if (tv != null) {
                    tv.setText(percent + "%");
                }
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.MAIN,
                    "Failed to update progress", e);
        }
    }

    private void dismissProgressDialog() {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (Exception e) {
            DebugLogger.warn(TAG, "dismissProgressDialog: " + e.getMessage());
        } finally {
            progressDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissProgressDialog();
        if (executor != null) {
            executor.shutdownNow();
        }
        DebugLogger.log(TAG, "MainActivity destroyed");
    }
}
