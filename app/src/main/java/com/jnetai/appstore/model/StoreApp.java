package com.jnetai.appstore.model;

import com.jnetai.appstore.util.DebugLogger;
import com.jnetai.appstore.util.ErrorHandler;

import org.json.JSONObject;

/**
 * A single entry in the J~Net App Store catalog.
 *
 * An entry is sourced either from a GitHub repository/release link
 * ("github") or from an APK uploaded to the store server ("upload").
 */
public class StoreApp {

    private static final String TAG = "StoreApp";

    public static final String SOURCE_GITHUB = "github";
    public static final String SOURCE_UPLOAD = "upload";

    private String id;
    private String name;
    private String description;
    private String packageName;
    private String version;
    private String source;
    private String githubRepo;
    private String githubUrl;
    private String apkUrl;
    private String iconUrl;
    private long sizeBytes;
    private String updated;

    public static StoreApp fromJson(JSONObject json) {
        StoreApp app = new StoreApp();
        try {
            app.id = json.optString("id", "");
            app.name = json.optString("name", "Unknown");
            app.description = json.optString("description", "");
            app.packageName = json.optString("packageName", "");
            app.version = json.optString("version", "");
            app.source = json.optString("source", SOURCE_GITHUB);
            app.githubRepo = json.optString("githubRepo", "");
            app.githubUrl = json.optString("githubUrl", "");
            app.apkUrl = json.optString("apkUrl", "");
            app.iconUrl = json.optString("iconUrl", "");
            app.sizeBytes = json.optLong("size", 0L);
            app.updated = json.optString("updated", "");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.PARSE,
                    "Failed to parse store app entry", e);
        }
        return app;
    }

    public boolean isGithub() {
        return SOURCE_GITHUB.equalsIgnoreCase(source);
    }

    public boolean hasInstallTarget() {
        return apkUrl != null && !apkUrl.isEmpty();
    }

    public String getSourceLabel() {
        return isGithub() ? "GitHub" : "Uploaded";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersion() {
        return version;
    }

    public String getSource() {
        return source;
    }

    public String getGithubRepo() {
        return githubRepo;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public String getApkUrl() {
        return apkUrl;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getUpdated() {
        return updated;
    }

    public void setApkUrl(String apkUrl) {
        this.apkUrl = apkUrl;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String debugString() {
        String s = "StoreApp{id=" + id + ", name=" + name + ", source=" + source
                + ", version=" + version + ", apkUrl=" + apkUrl + "}";
        DebugLogger.log(TAG, s);
        return s;
    }
}
