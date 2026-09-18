/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles.dialog;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.Utils;
import com.android.settingslib.wifi.WifiUtils;
import com.android.systemui.qs.flags.QsWifiConfig;
import com.android.systemui.res.R;
import com.android.systemui.user.data.repository.UserRepository;
import com.android.systemui.util.kotlin.JavaAdapterKt;
import com.android.wifi.flags.Flags;
import com.android.wifitrackerlib.WifiEntry;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adapter for showing Wi-Fi networks.
 */
public class InternetAdapter extends RecyclerView.Adapter<InternetAdapter.InternetViewHolder> {

    private static final String TAG = "InternetAdapter";

    private final InternetDetailsContentController mInternetDetailsContentController;
    private final CoroutineScope mCoroutineScope;
    private final Boolean mIsInDetailsView;
    @Nullable
    private List<WifiEntry> mWifiEntries;
    private boolean mShowAllWifi;
    @VisibleForTesting
    protected int mWifiEntriesCount;
    @VisibleForTesting
    protected int mMaxEntriesCount = InternetDetailsContentController.MAX_WIFI_ENTRY_COUNT;

    protected View mHolderView;
    protected Context mContext;
    private boolean mHasMultipleFullUsers = false;

    public InternetAdapter(InternetDetailsContentController controller,
            CoroutineScope coroutineScope, boolean isInDetailsView, UserRepository userRepository) {
        mInternetDetailsContentController = controller;
        mCoroutineScope = coroutineScope;
        mIsInDetailsView = isInDetailsView;
        JavaAdapterKt.collectFlow(
                coroutineScope,
                userRepository.getHasMultipleFullUsers(),
                hasMultipleFullUsers -> {
                    mHasMultipleFullUsers = hasMultipleFullUsers;
                    notifyDataSetChanged();
                }
        );
    }

    @Override
    public InternetViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup,
            int viewType) {
        mContext = viewGroup.getContext();
        mHolderView = LayoutInflater.from(mContext).inflate(R.layout.internet_list_item,
                viewGroup, false);

        if (mIsInDetailsView) {
            // Customize the wifi list's dimensions.
            LinearLayout wifiList = mHolderView.findViewById(R.id.wifi_list);
            Resources res = mHolderView.getContext().getResources();
            LinearLayout.LayoutParams wifiListLayoutParams =
                    (LinearLayout.LayoutParams) wifiList.getLayoutParams();

            wifiListLayoutParams.height =
                    res.getDimensionPixelSize(R.dimen.tile_details_entry_height);

            final int horizontalMargin =
                    res.getDimensionPixelSize(R.dimen.tile_details_entry_horizontal_margin);

            wifiListLayoutParams.setMarginStart(horizontalMargin);
            wifiListLayoutParams.setMarginEnd(horizontalMargin);

            wifiList.setLayoutParams(wifiListLayoutParams);

            // Customize the wifi network layout's dimensions.
            LinearLayout wifiNetworkLayout = mHolderView.findViewById(R.id.wifi_network_layout);
            LinearLayout.LayoutParams wifiNetworkLayoutParams =
                    (LinearLayout.LayoutParams) wifiNetworkLayout.getLayoutParams();

            wifiNetworkLayoutParams.height = wifiListLayoutParams.height;
            wifiNetworkLayout.setLayoutParams(wifiNetworkLayoutParams);

            // Update the size of the wifi icon.
            ImageView wifiIcon = mHolderView.findViewById(R.id.wifi_icon);
            View iconContainer = (View) wifiIcon.getParent();
            ViewGroup.LayoutParams iconContainerParams = iconContainer.getLayoutParams();
            int newIconSize = res.getDimensionPixelSize(R.dimen.tile_details_entry_icon_size);
            iconContainerParams.width = newIconSize;
            iconContainerParams.height = newIconSize;
            iconContainer.setLayoutParams(iconContainerParams);

            // Set each end icon's height to MATCH_PARENT to ensure they align
            // vertically within the container.
            ImageView[] endIcons = {
                    mHolderView.findViewById(R.id.first_wifi_end_icon),
                    mHolderView.findViewById(R.id.second_wifi_end_icon)};
            for (ImageView icon : endIcons) {
                ViewGroup.LayoutParams iconParams = icon.getLayoutParams();
                iconParams.width = newIconSize;
                iconParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                icon.setLayoutParams(iconParams);
            }

            // Set the end icon container's width to WRAP_CONTENT,
            // allowing it to hold multiple icons.
            View endIconContainer =
                    mHolderView.findViewById(R.id.wifi_end_icon_container);
            ViewGroup.LayoutParams endContainerParams =
                    endIconContainer.getLayoutParams();
            endContainerParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            endContainerParams.height = newIconSize;
            endIconContainer.setLayoutParams(endContainerParams);
        }

        return new InternetViewHolder(mHolderView, mInternetDetailsContentController,
                mCoroutineScope, mIsInDetailsView);
    }

    @Override
    public void onBindViewHolder(@NonNull InternetViewHolder viewHolder, int position) {
        if (mWifiEntries == null || position >= mWifiEntriesCount) {
            return;
        }
        viewHolder.onBind(mWifiEntries.get(position), mHasMultipleFullUsers);
    }

    /**
     * Updates the Wi-Fi networks.
     *
     * @param wifiEntries the updated Wi-Fi entries.
     * @param wifiEntriesCount the total number of Wi-Fi entries.
     */
    public void setWifiEntries(@Nullable List<WifiEntry> wifiEntries, int wifiEntriesCount) {
        mWifiEntries = wifiEntries;
        if (mShowAllWifi) {
            mWifiEntriesCount = wifiEntriesCount;
        } else {
            mWifiEntriesCount =
                    (wifiEntriesCount < mMaxEntriesCount) ? wifiEntriesCount : mMaxEntriesCount;
        }
    }

    /**
     * Gets the total number of Wi-Fi networks.
     *
     * @return The total number of Wi-Fi entries.
     */
    @Override
    public int getItemCount() {
        return mWifiEntriesCount;
    }

    /**
     * Sets the maximum number of Wi-Fi networks.
     */
    public void setMaxEntriesCount(int count) {
        if (mShowAllWifi) {
            return;
        }
        if (count < 0 || mMaxEntriesCount == count) {
            return;
        }
        mMaxEntriesCount = count;
        if (mWifiEntriesCount > count) {
            mWifiEntriesCount = count;
            notifyDataSetChanged();
        }
    }

    /**
     * Sets to show all available Wi-Fi networks
     */
    public void setShowAllWifi() {
        if (!QsWifiConfig.isEnabled() || mShowAllWifi) {
            return;
        }
        mShowAllWifi = true;
        if (mWifiEntries != null) {
            mWifiEntriesCount = mWifiEntries.size();
        }
        notifyDataSetChanged();
    }

    /**
     * ViewHolder for binding Wi-Fi view.
     */
    static class InternetViewHolder extends RecyclerView.ViewHolder {

        final LinearLayout mContainerLayout;
        final LinearLayout mWifiListLayout;
        final LinearLayout mWifiNetworkLayout;
        final ImageView mWifiIcon;
        final TextView mWifiTitleText;
        final TextView mWifiSummaryText;
        final ImageView mFirstWifiEndIcon;
        final ImageView mSecondWifiEndIcon;
        final Context mContext;
        final InternetDetailsContentController mInternetDetailsContentController;
        final CoroutineScope mCoroutineScope;
        final Boolean mIsInDetailsView;
        @Nullable
        private Job mJob;

        InternetViewHolder(View view,
                InternetDetailsContentController internetDetailsContentController,
                CoroutineScope coroutineScope, Boolean isInDetailsView) {
            super(view);
            mContext = view.getContext();
            mInternetDetailsContentController = internetDetailsContentController;
            mCoroutineScope = coroutineScope;
            mIsInDetailsView = isInDetailsView;
            mContainerLayout = view.requireViewById(R.id.internet_container);
            mWifiListLayout = view.requireViewById(R.id.wifi_list);
            mWifiNetworkLayout = view.requireViewById(R.id.wifi_network_layout);
            mWifiIcon = view.requireViewById(R.id.wifi_icon);
            mWifiTitleText = view.requireViewById(R.id.wifi_title);
            mWifiSummaryText = view.requireViewById(R.id.wifi_summary);
            mFirstWifiEndIcon = view.requireViewById(R.id.first_wifi_end_icon);
            mSecondWifiEndIcon =
                    view.requireViewById(R.id.second_wifi_end_icon);
        }

        void onBind(@NonNull WifiEntry wifiEntry, boolean hasMultipleFullUsers) {
            mWifiIcon.setImageDrawable(getWifiDrawable(wifiEntry));
            setWifiNetworkLayout(wifiEntry.getTitle(),
                    Html.fromHtml(wifiEntry.getSummary(false), Html.FROM_HTML_MODE_LEGACY));
            updateEndIcons(wifiEntry, hasMultipleFullUsers);

            mWifiListLayout.setEnabled(shouldEnabled(wifiEntry));

            // Set the UI styles for details view only.
            if (mIsInDetailsView) {
                mWifiTitleText.setTextAppearance(R.style.TextAppearance_TileDetailsEntryTitle);
                mWifiSummaryText.setTextAppearance(R.style.TextAppearance_TileDetailsEntrySubTitle);
                if (mWifiIcon.getDrawable() != null) {
                    mWifiIcon.setColorFilter(
                            mContext.getColor(com.android.internal.R.color.materialColorOnSurface));
                }
            }

            if (wifiEntry.getConnectedState() != WifiEntry.CONNECTED_STATE_DISCONNECTED) {
                mWifiListLayout.setOnClickListener(
                        v -> mInternetDetailsContentController.launchWifiDetailsSetting(
                                wifiEntry.getKey(), v));
                return;
            }
            mWifiListLayout.setOnClickListener(v -> onWifiClick(wifiEntry, v));
        }

        boolean shouldEnabled(@NonNull WifiEntry wifiEntry) {
            if (wifiEntry.canConnect()) {
                return true;
            }
            // If Wi-Fi is connected or saved network, leave it enabled to disconnect or configure.
            if (wifiEntry.canDisconnect() || wifiEntry.isSaved()) {
                return true;
            }
            return false;
        }

        void onWifiClick(@NonNull WifiEntry wifiEntry, @NonNull View view) {
            if (Flags.androidVWifiApi() && wifiEntry.getSecurityTypes().contains(
                    WifiEntry.SECURITY_WEP)) {
                if (mJob == null) {
                    mJob = WifiUtils.checkWepAllowed(mContext, mCoroutineScope, wifiEntry.getSsid(),
                            WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG, intent -> {
                                mInternetDetailsContentController
                                        .startActivityForDialog(intent);
                                return null;
                            }, () -> {
                                wifiConnect(wifiEntry, view);
                                return null;
                            });
                }
                return;
            }
            wifiConnect(wifiEntry, view);
        }

        void wifiConnect(@NonNull WifiEntry wifiEntry, @NonNull View view) {
            if (wifiEntry.shouldEditBeforeConnect()) {
                final Intent intent = WifiUtils.getWifiDialogIntent(wifiEntry.getKey(),
                        true /* connectForCaller */);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                mInternetDetailsContentController.startActivityForDialog(intent);
                return;
            }

            if (wifiEntry.canConnect()) {
                mInternetDetailsContentController.connect(wifiEntry);
                return;
            }

            if (wifiEntry.isSaved()) {
                Log.w(TAG, "The saved Wi-Fi network does not allow to connect. SSID:"
                        + wifiEntry.getSsid());
                mInternetDetailsContentController.launchWifiDetailsSetting(wifiEntry.getKey(),
                        view);
            }
        }

        void setWifiNetworkLayout(CharSequence title, CharSequence summary) {
            mWifiTitleText.setText(title);
            if (TextUtils.isEmpty(summary)) {
                mWifiSummaryText.setVisibility(View.GONE);
                return;
            }
            mWifiSummaryText.setVisibility(View.VISIBLE);
            mWifiSummaryText.setText(summary);
        }

        @Nullable
        Drawable getWifiDrawable(@NonNull WifiEntry wifiEntry) {
            Drawable drawable = mInternetDetailsContentController.getWifiDrawable(wifiEntry);
            if (drawable == null) {
                return null;
            }
            drawable.setTint(
                    Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorTertiary));
            final AtomicReference<Drawable> shared = new AtomicReference<>();
            shared.set(drawable);
            return shared.get();
        }

        void updateEndIcons(@NonNull WifiEntry wifiEntry, boolean hasMultipleFullUsers) {
            boolean isConnected = wifiEntry.getConnectedState()
                    != WifiEntry.CONNECTED_STATE_DISCONNECTED;
            boolean isShared = QsWifiConfig.isEnabled() && hasMultipleFullUsers
                    && wifiEntry.isSharedWithOtherUsers();
            boolean isSecured =
                    (wifiEntry.getSecurity() != WifiEntry.SECURITY_NONE)
                    && (wifiEntry.getSecurity() != WifiEntry.SECURITY_OWE);

            // The sequence of icons matters. i.e. the shared network icon
            // should precede the lock icon.
            List<Integer> icons = new ArrayList<Integer>();
            if (isConnected) icons.add(R.drawable.ic_settings_24dp);
            else {
                if (isShared) icons.add(R.drawable.ic_group_24dp);
                if (isSecured) icons.add(R.drawable.ic_friction_lock_closed);
            }

            ImageView[] iconSlots = {mFirstWifiEndIcon, mSecondWifiEndIcon};
            for (int i = 0; i < iconSlots.length; i++) {
                ImageView iconView = iconSlots[i];
                if (i < icons.size()) {
                    iconView.setImageDrawable(
                            mContext.getDrawable(icons.get(i)));
                    iconView.setVisibility(View.VISIBLE);
                    if (mIsInDetailsView) {
                        iconView.setColorFilter(mContext.getColor(
                                com.android.internal.R.color.materialColorOnSurface));
                    }
                } else {
                    iconView.setVisibility(View.GONE);
                }
            }
        }
    }
}
