/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.accessibility.floatingmenu;

import static com.android.internal.accessibility.AccessibilityShortcutController.ACCESSIBILITY_HEARING_AIDS_COMPONENT_NAME;
import static com.android.internal.accessibility.AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;

import com.android.internal.accessibility.common.ShortcutConstants.AccessibilityFragmentType;
import com.android.internal.accessibility.dialog.AccessibilityTarget;
import com.android.settingslib.bluetooth.HearingAidDeviceManager;
import com.android.settingslib.bluetooth.HearingAidDeviceManager.ConnectionStatus;
import com.android.systemui.Flags;
import com.android.systemui.accessibility.Magnification;
import com.android.systemui.accessibility.floatingmenu.AccessibilityTargetAdapter.ViewHolder;
import com.android.systemui.accessibility.hearingaid.HearingDeviceStatusDrawableInfo;
import com.android.systemui.res.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * An adapter which shows the set of accessibility targets that can be performed.
 */
public class AccessibilityTargetAdapter extends Adapter<ViewHolder> {
    @VisibleForTesting static final int PAYLOAD_HEARING_STATUS_DRAWABLE = 1;

    private int mIconWidthHeight;
    private int mBadgeWidthHeight;
    private int mItemPadding;
    private final List<AccessibilityTarget> mTargets;

    private int mHearingDeviceStatus;
    private boolean mBadgeOnLeftSide = false;
    private MoreOptionsClickListener mMoreOptionsClickListener;

    /** A listener for when the "More Options" menu item is clicked. */
    public interface MoreOptionsClickListener {
        /**
         * A callback that is invoked when the "More Options" menu item is clicked.
         *
         * @param view The view that was clicked.
         */
        void onMoreOptionsClicked(View view);
    }

    private Context mContext;
    private Magnification mMagnification;

    private Magnification.MagnificationActivationChangedListener mMagnificationEnabledListener =
            (displayId) -> {
                if (displayId == mContext.getDisplayId()) {
                    notifyDataSetChanged();
                }
            };

    @IntDef({
            ItemType.FIRST_ITEM,
            ItemType.REGULAR_ITEM,
            ItemType.LAST_ITEM
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ItemType {
        int FIRST_ITEM = 0;
        int REGULAR_ITEM = 1;
        int LAST_ITEM = 2;
    }

    public AccessibilityTargetAdapter(@NonNull List<AccessibilityTarget> targets,
            Context context,
            Magnification magnification) {
        mTargets = targets;
        mContext = context;
        mMagnification = magnification;
    }

    void show() {
        if (Flags.floatingMenuMagnificationStatus()) {
            mMagnification.registerActivationChangedListener(mMagnificationEnabledListener);
        }
    }

    void hide() {
        mMagnification.unregisterActivationChangedListener(mMagnificationEnabledListener);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @ItemType int itemType) {
        final View root = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.accessibility_floating_menu_item, parent,
                /* attachToRoot= */ false);

        if (!com.android.systemui.Flags.floatingMenuUniformPadding()) {
            if (itemType == ItemType.FIRST_ITEM) {
                return new TopViewHolder(root);
            }

            if (itemType == ItemType.LAST_ITEM) {
                return new BottomViewHolder(root);
            }
        }

        return new ViewHolder(root);
    }

    public void setMoreOptionsClickListener(MoreOptionsClickListener listener) {
        mMoreOptionsClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final AccessibilityTarget target = mTargets.get(position);
        Drawable icon = target.getIcon();
        if (target instanceof MoreOptionsTarget) {
            final int iconSize = icon.getIntrinsicWidth();
            if (mIconWidthHeight > iconSize && iconSize > 0) {
                final int inset = (mIconWidthHeight - iconSize) / 2;
                icon = new InsetDrawable(icon, inset);
            }
        }
        holder.mIconView.setBackground(icon);
        holder.mRightBadgeView.setBackground(null);
        holder.mLeftBadgeView.setBackground(null);
        holder.updateIconSize(mIconWidthHeight);
        holder.updateItemPadding(mItemPadding, getItemCount());
        holder.itemView.setOnClickListener(view -> {
            if (target instanceof MoreOptionsTarget) {
                if (mMoreOptionsClickListener != null) {
                    mMoreOptionsClickListener.onMoreOptionsClicked(view);
                }
            } else {
                target.onSelected();
            }
        });
        holder.itemView.setContentDescription(target.getLabel());

        if (Flags.floatingMenuMagnificationStatus()
                && MAGNIFICATION_CONTROLLER_NAME.equals(target.getId())) {
            final int statusResId =
                    mMagnification.isAnyMagnificationActivated(mContext.getDisplayId())
                            ? com.android.internal.R.string
                            .accessibility_shortcut_menu_item_status_on
                            : com.android.internal.R.string
                                    .accessibility_shortcut_menu_item_status_off;

            holder.itemView.setStateDescription(mContext.getString(statusResId));
        } else {
            holder.itemView.setStateDescription(target.getStateDescription());
        }

        final String clickHint = target.getFragmentType() == AccessibilityFragmentType.TOGGLE
                ? holder.itemView.getResources().getString(
                R.string.accessibility_floating_button_action_double_tap_to_toggle)
                : null;
        ViewCompat.replaceAccessibilityAction(holder.itemView,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                clickHint, /* command= */ null);

        ViewCompat.setAccessibilityDelegate(
                holder.itemView,
                new androidx.core.view.AccessibilityDelegateCompat() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(
                            View host, AccessibilityNodeInfoCompat info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        if (target instanceof MoreOptionsTarget) {
                            info.setClassName(android.widget.Button.class.getName());
                        }
                    }
                });

        if (com.android.settingslib.flags.Flags.hearingDeviceSetConnectionStatusReport()) {
            final String targetId = target.getId();
            if (targetId != null && ACCESSIBILITY_HEARING_AIDS_COMPONENT_NAME.equals(
                    ComponentName.unflattenFromString(targetId))) {
                updateHearingDeviceStatusDrawable(holder, mHearingDeviceStatus);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }

        if (com.android.settingslib.flags.Flags.hearingDeviceSetConnectionStatusReport()) {
            payloads.forEach(payload -> {
                if (payload instanceof Integer cmd) {
                    if (cmd == PAYLOAD_HEARING_STATUS_DRAWABLE) {
                        updateHearingDeviceStatusDrawable(holder, mHearingDeviceStatus);
                    }
                }
            });
        }
    }

    @ItemType
    @Override
    public int getItemViewType(int position) {
        // This LAST_ITEM condition should be checked before others to ensure proper padding when
        // adding a second target via notifyItemInserted().
        if (position == (getItemCount() - 1)) {
            return ItemType.LAST_ITEM;
        }

        if (position == 0) {
            return ItemType.FIRST_ITEM;
        }

        return ItemType.REGULAR_ITEM;
    }

    @Override
    public int getItemCount() {
        return mTargets.size();
    }

    public void setIconWidthHeight(int iconWidthHeight) {
        mIconWidthHeight = iconWidthHeight;
    }

    public void setBadgeWidthHeight(int badgeWidthHeight) {
        mBadgeWidthHeight = badgeWidthHeight;
    }

    public void setItemPadding(int itemPadding) {
        mItemPadding = itemPadding;
    }

    public void setBadgeOnLeftSide(boolean leftSide) {
        mBadgeOnLeftSide = leftSide;
    }

    /**
     * Notifies to update the hearing device status drawable at the given target index.
     *
     * @param status the connection status for hearing devices.
     *               {@link HearingAidDeviceManager.ConnectionStatus}
     * @param targetIndex The index of the hearing aid device in the target list, or -1 if not
     *                    exist.
     */
    public void onHearingDeviceStatusChanged(@HearingAidDeviceManager.ConnectionStatus int status,
            int targetIndex) {
        mHearingDeviceStatus = status;

        if (targetIndex >= 0) {
            notifyItemChanged(targetIndex, PAYLOAD_HEARING_STATUS_DRAWABLE);
        }
    }

    private void updateHearingDeviceStatusDrawable(ViewHolder holder,
            @ConnectionStatus int status) {
        final Context context = holder.itemView.getContext();
        HearingDeviceStatusDrawableInfo.StatusDrawableInfo statusDrawableInfo =
                HearingDeviceStatusDrawableInfo.get(status);
        final int baseDrawableId = statusDrawableInfo.baseDrawableId();
        final int stateDescriptionId = statusDrawableInfo.stateDescriptionId();
        final int indicatorDrawableId = statusDrawableInfo.indicatorDrawableId();

        holder.mIconView.setBackground(
                (baseDrawableId != 0) ? context.getDrawable(baseDrawableId) : null);
        holder.mRightBadgeView.setBackground(
                (indicatorDrawableId != 0) ? context.getDrawable(indicatorDrawableId) : null);
        holder.mLeftBadgeView.setBackground(
                (indicatorDrawableId != 0) ? context.getDrawable(indicatorDrawableId) : null);
        holder.itemView.setStateDescription(
                (stateDescriptionId != 0) ? context.getString(stateDescriptionId) : null);
        holder.updateBadgeSize(mBadgeWidthHeight);

        if (mBadgeOnLeftSide) {
            holder.mRightBadgeView.setVisibility(View.INVISIBLE);
            holder.mLeftBadgeView.setVisibility(View.VISIBLE);
        } else {
            holder.mRightBadgeView.setVisibility(View.VISIBLE);
            holder.mLeftBadgeView.setVisibility(View.INVISIBLE);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View mIconView;
        final View mRightBadgeView;
        final View mLeftBadgeView;

        ViewHolder(View itemView) {
            super(itemView);
            mIconView = itemView.findViewById(R.id.icon_view);
            mRightBadgeView = itemView.findViewById(R.id.right_badge_view);
            mLeftBadgeView = itemView.findViewById(R.id.left_badge_view);
        }

        void updateIconSize(int newValue) {
            final ViewGroup.LayoutParams layoutParams = mIconView.getLayoutParams();
            if (layoutParams.width == newValue) {
                return;
            }
            layoutParams.width = newValue;
            layoutParams.height = newValue;
            mIconView.setLayoutParams(layoutParams);
        }

        void updateBadgeSize(int newValue) {
            final ViewGroup.LayoutParams rightLayoutParams = mRightBadgeView.getLayoutParams();
            if (rightLayoutParams.width == newValue) {
                return;
            }
            rightLayoutParams.width = newValue;
            rightLayoutParams.height = newValue;
            final ViewGroup.LayoutParams leftLayoutParams = mLeftBadgeView.getLayoutParams();
            if (leftLayoutParams.width == newValue) {
                return;
            }
            leftLayoutParams.width = newValue;
            leftLayoutParams.height = newValue;

            mRightBadgeView.setLayoutParams(rightLayoutParams);
            mLeftBadgeView.setLayoutParams(leftLayoutParams);
        }

        void updateItemPadding(int padding, int size) {
            if (com.android.systemui.Flags.floatingMenuUniformPadding()) {
                itemView.setPaddingRelative(padding, padding, padding, padding);
            } else {
                itemView.setPaddingRelative(padding, padding, padding, 0);
            }
        }
    }

    static class TopViewHolder extends ViewHolder {
        TopViewHolder(View itemView) {
            super(itemView);
        }

        @Override
        void updateItemPadding(int padding, int size) {
            final int paddingBottom = size <= 1 ? padding : 0;
            itemView.setPaddingRelative(padding, padding, padding, paddingBottom);
        }
    }

    static class BottomViewHolder extends ViewHolder {
        BottomViewHolder(View itemView) {
            super(itemView);
        }

        @Override
        void updateItemPadding(int padding, int size) {
            itemView.setPaddingRelative(padding, padding, padding, padding);
        }
    }
}
