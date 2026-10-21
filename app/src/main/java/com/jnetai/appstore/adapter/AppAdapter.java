package com.jnetai.appstore.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jnetai.appstore.R;
import com.jnetai.appstore.model.StoreApp;
import com.jnetai.appstore.util.DebugLogger;
import com.jnetai.appstore.util.ErrorHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the J~Net App Store catalog list.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    private static final String TAG = "AppAdapter";

    public interface Listener {
        void onInstall(StoreApp app, int position);

        void onOpenDetails(StoreApp app);
    }

    private final List<StoreApp> apps = new ArrayList<>();
    private final Listener listener;

    public AppAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<StoreApp> newApps) {
        apps.clear();
        if (newApps != null) {
            apps.addAll(newApps);
        }
        notifyDataSetChanged();
        DebugLogger.log(TAG, "Adapter updated with " + apps.size() + " item(s)");
    }

    public StoreApp getItem(int position) {
        if (position < 0 || position >= apps.size()) {
            return null;
        }
        return apps.get(position);
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        try {
            StoreApp app = apps.get(position);
            holder.bind(app, listener);
        } catch (Exception e) {
            ErrorHandler.handle(TAG, ErrorHandler.Code.ADAPTER,
                    "Failed to bind item at position " + position, e);
        }
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvIcon;
        private final TextView tvName;
        private final TextView tvVersion;
        private final TextView tvSource;
        private final TextView tvDescription;
        private final Button btnInstall;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvIcon);
            tvName = itemView.findViewById(R.id.tvName);
            tvVersion = itemView.findViewById(R.id.tvVersion);
            tvSource = itemView.findViewById(R.id.tvSource);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            btnInstall = itemView.findViewById(R.id.btnInstall);
        }

        void bind(StoreApp app, Listener listener) {
            String name = app.getName() == null ? "" : app.getName();
            tvName.setText(name);
            tvIcon.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());

            String version = app.getVersion();
            tvVersion.setText(version == null || version.isEmpty() ? "" : "v" + version);

            tvSource.setText(app.getSourceLabel());
            boolean github = app.isGithub();
            tvSource.setTextColor(github ? Color.parseColor("#4EA1FF")
                    : Color.parseColor("#2ECC71"));

            String desc = app.getDescription();
            if (desc == null || desc.isEmpty()) {
                tvDescription.setVisibility(View.GONE);
            } else {
                tvDescription.setVisibility(View.VISIBLE);
                tvDescription.setText(desc);
            }

            if (app.hasInstallTarget()) {
                btnInstall.setEnabled(true);
                btnInstall.setAlpha(1f);
                btnInstall.setText("Get");
            } else {
                btnInstall.setEnabled(false);
                btnInstall.setAlpha(0.5f);
                btnInstall.setText("N/A");
            }

            btnInstall.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onInstall(app, pos);
                }
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpenDetails(app);
                }
            });
        }
    }
}
