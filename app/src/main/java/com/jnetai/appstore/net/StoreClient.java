package com.jnetai.appstore.net;

import com.jnetai.appstore.model.StoreApp;
import com.jnetai.appstore.util.DebugLogger;
import com.jnetai.appstore.util.ErrorHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to the self-hosted J~Net App Store server (apps.jnetai.com).
 *
 * The server exposes the catalog at {baseUrl}/api/apps and serves uploaded
 * APK files from {baseUrl}/apk/... together with GitHub-sourced download URLs.
 */
public final class StoreClient {

    private static final String TAG = "StoreClient";
    private static final int CONNECT_TIMEOUT = 12000;
    private static final int READ_TIMEOUT = 20000;

    private StoreClient() {
    }

    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static List<StoreApp> fetchCatalog(String baseUrl) throws Exception {
        String base = normalizeBaseUrl(baseUrl);
        String endpoint = base + "/api/apps";
        DebugLogger.log(TAG, "Fetching catalog from " + endpoint);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "JNetAppStore-Android");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new StoreException(ErrorHandler.Code.NETWORK,
                        "Store returned HTTP " + code + " for " + endpoint);
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            JSONObject root = new JSONObject(body.toString());
            JSONArray apps = root.optJSONArray("apps");
            List<StoreApp> result = new ArrayList<>();
            if (apps != null) {
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject item = apps.optJSONObject(i);
                    if (item != null) {
                        StoreApp app = StoreApp.fromJson(item);
                        if (app.getApkUrl() != null && !app.getApkUrl().isEmpty()
                                && app.getApkUrl().startsWith("/")) {
                            app.setApkUrl(base + app.getApkUrl());
                        }
                        result.add(app);
                    }
                }
            }
            DebugLogger.log(TAG, "Catalog loaded: " + result.size() + " app(s)");
            return result;
        } catch (StoreException e) {
            throw e;
        } catch (Exception e) {
            throw new StoreException(ErrorHandler.Code.NETWORK,
                    "Failed to load store catalog from " + endpoint + ": " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
