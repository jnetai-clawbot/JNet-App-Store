package com.jnetai.appstore.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.jnetai.appstore.R;
import com.jnetai.appstore.model.StoreApp;
import com.jnetai.appstore.util.DebugLogger;
import com.jnetai.appstore.util.ErrorHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local persistence for the store configuration and the last known catalog.
 * Kept simple and dependency-free (SharedPreferences) so it survives updates.
 */
public final class StoreRepository {

    private static final String TAG = "StoreRepository";
    private static final String PREFS = "jnet_app_store_prefs";
    private static final String KEY_STORE_URL = "store_url";
    private static final String KEY_CACHE_JSON = "cache_json";
    private static final String KEY_CACHE_TIME = "cache_time";

    private StoreRepository() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getStoreUrl(Context context) {
        String def = context.getString(R.string.store_base_url);
        return prefs(context).getString(KEY_STORE_URL, def);
    }

    public static void setStoreUrl(Context context, String url) {
        String clean = url == null ? "" : url.trim();
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        prefs(context).edit().putString(KEY_STORE_URL, clean).apply();
        DebugLogger.log(TAG, "Store URL updated to " + clean);
    }

    public static void saveCatalogCache(Context context, List<StoreApp> apps) {
        try {
            JSONArray arr = new JSONArray();
            for (StoreApp app : apps) {
                JSONObject o = new JSONObject();
                o.put("id", app.getId());
                o.put("name", app.getName());
                o.put("description", app.getDescription());
                o.put("packageName", app.getPackageName());
                o.put("version", app.getVersion());
                o.put("source", app.getSource());
                o.put("githubRepo", app.getGithubRepo());
                o.put("githubUrl", app.getGithubUrl());
                o.put("apkUrl", app.getApkUrl());
                o.put("iconUrl", app.getIconUrl());
                o.put("size", app.getSizeBytes());
                o.put("updated", app.getUpdated());
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("apps", arr);
            prefs(context).edit()
                    .putString(KEY_CACHE_JSON, root.toString())
                    .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.IO, "Failed to cache catalog", e);
        }
    }

    public static List<StoreApp> loadCatalogCache(Context context) {
        List<StoreApp> result = new ArrayList<>();
        String json = prefs(context).getString(KEY_CACHE_JSON, null);
        if (json == null) {
            return result;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("apps");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) {
                        result.add(StoreApp.fromJson(o));
                    }
                }
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.PARSE, "Failed to read catalog cache", e);
        }
        return result;
    }

    public static long getCacheTime(Context context) {
        return prefs(context).getLong(KEY_CACHE_TIME, 0L);
    }
}
