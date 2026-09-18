/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.companiondevicemanager;

import static com.android.companiondevicemanager.CompanionDeviceResources.PERMISSION_ICONS;
import static com.android.companiondevicemanager.CompanionDeviceResources.PERMISSION_SUMMARIES;
import static com.android.companiondevicemanager.CompanionDeviceResources.PERMISSION_TITLES;
import static com.android.companiondevicemanager.Utils.getHtmlFromResources;
import static com.android.companiondevicemanager.Utils.getIcon;

import android.content.Context;
import android.text.Spanned;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

class PermissionListAdapter extends RecyclerView.Adapter<PermissionListAdapter.ViewHolder> {
    public static final int PERMISSION_SIZE = 2;
    private final Context mContext;
    private List<Integer> mPermissions;
    private CharSequence mAppLabel;
    private CharSequence mDeviceName;

    private final SparseBooleanArray mExpandedPositions = new SparseBooleanArray();

    PermissionListAdapter(Context context) {
        mContext = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.list_item_permission, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int type = getItemViewType(position);
        holder.mPermissionIcon.setImageDrawable(getIcon(mContext, PERMISSION_ICONS.get(type)));

        final Spanned title = getHtmlFromResources(mContext, PERMISSION_TITLES.get(type),
                mContext.getString(R.string.device_type));
        holder.mPermissionName.setText(title);

        boolean hasSummary = PERMISSION_SUMMARIES.containsKey(type);
        if (hasSummary) {
            final Spanned summary = getHtmlFromResources(mContext, PERMISSION_SUMMARIES.get(type),
                    mAppLabel, mContext.getString(R.string.device_type), mDeviceName);
            holder.mPermissionSummary.setText(summary);
        }

        boolean isExpandable = mPermissions.size() > PERMISSION_SIZE && hasSummary;

        if (isExpandable) {
            holder.itemView.setClickable(true);
            holder.itemView.setFocusable(true);
            boolean isExpanded = mExpandedPositions.get(position);
            // Set listener for keyboard event.
            holder.itemView.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_UP
                        && (keyCode == KeyEvent.KEYCODE_ENTER
                                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                    view.performClick();
                    return true;
                }
                return false;
            });

            holder.mPermissionSummary.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.mExpandButton.setVisibility(View.VISIBLE);
            holder.mExpandButton.setImageResource(isExpanded
                    ? R.drawable.btn_expand_less
                    : R.drawable.btn_expand_more);

            int statusRes = isExpanded ? R.string.permission_collapse : R.string.permission_expand;
            setAccessibility(holder.itemView, type, AccessibilityNodeInfo.ACTION_CLICK, statusRes);

            holder.itemView.setOnClickListener(v -> {
                boolean willExpand = !isExpanded;
                mExpandedPositions.put(position, willExpand);

                boolean hadHardwareFocus = v.isFocused();

                if (hadHardwareFocus) {
                    v.clearFocus();
                }
                notifyItemChanged(position);

                if (willExpand) {
                    RecyclerView recyclerView = (RecyclerView) v.getParent();
                    if (recyclerView != null) {
                        recyclerView.postDelayed(() -> {
                            RecyclerView.ViewHolder currentHolder =
                                    recyclerView.findViewHolderForAdapterPosition(position);
                            if (currentHolder != null) {
                                View summaryView = currentHolder.itemView.findViewById(
                                        R.id.permission_summary);
                                if (summaryView != null
                                        && summaryView.getVisibility() == View.VISIBLE) {
                                    summaryView.performAccessibilityAction(
                                            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                                }
                            }
                            if (hadHardwareFocus) {
                                currentHolder.itemView.requestFocus();
                            }
                        }, 200);
                    }
                }
            });
        } else {
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            holder.itemView.setFocusable(false);
            holder.itemView.setAccessibilityDelegate(null);
            holder.mExpandButton.setVisibility(View.GONE);
            holder.mPermissionSummary.setVisibility(View.VISIBLE);

        }
    }

    @Override
    public int getItemViewType(int position) {
        return mPermissions.get(position);
    }

    @Override
    public int getItemCount() {
        return mPermissions != null ? mPermissions.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView mPermissionName;
        private final TextView mPermissionSummary;
        private final ImageView mPermissionIcon;
        private final ImageButton mExpandButton;

        ViewHolder(View itemView) {
            super(itemView);
            mPermissionName = itemView.findViewById(R.id.permission_name);
            mPermissionSummary = itemView.findViewById(R.id.permission_summary);
            mPermissionIcon = itemView.findViewById(R.id.permission_icon);
            mExpandButton = itemView.findViewById(R.id.permission_expand_button);
        }
    }

    private void setAccessibility(View view, int viewType, int action, int statusResourceId) {
        final String permission = mContext.getString(PERMISSION_TITLES.get(viewType));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(action,
                        getHtmlFromResources(mContext, statusResourceId, permission)));
            }
        });
    }

    void setPermissionType(List<Integer> permissions) {
        mPermissions = permissions;
    }

    void setAppLabel(CharSequence appLabel) {
        mAppLabel = appLabel;
    }

    void setDeviceName(CharSequence deviceName) {
        mDeviceName = deviceName;
    }
}
