/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.virtualdevicemanager;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

/**
 * A VDM warning dialog that an activity launch was blocked on a computer control session.
 */
public class NotifyComputerControlBlockedActivityFragment extends DialogFragment {

    private static final String ARG_BLOCKED_COMPONENT_NAME = "argBlockedComponentName";
    private ComponentName mBlockedComponentName;

    static NotifyComputerControlBlockedActivityFragment newInstance(
            @NonNull ComponentName blockedComponentName) {
        final var fragment = new NotifyComputerControlBlockedActivityFragment();
        final Bundle args = new Bundle();
        args.putParcelable(ARG_BLOCKED_COMPONENT_NAME, blockedComponentName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBlockedComponentName =
                getArguments().getParcelable(ARG_BLOCKED_COMPONENT_NAME, ComponentName.class);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();
        View view = activity.getLayoutInflater()
                .inflate(R.layout.notify_computer_control_blocked_activity_dialog, null);

        view.findViewById(R.id.ok_button).setOnClickListener(v -> getDialog().cancel());

        TextView titleView = view.findViewById(R.id.title);

        if (TextUtils.equals(mBlockedComponentName.getPackageName(),
                activity.getPackageManager().getPermissionControllerPackageName())) {
            titleView.setText(R.string.notify_computer_control_blocked_permission_dialog_title);
        } else {
            titleView.setText(R.string.notify_computer_control_blocked_activity_title);
        }

        Dialog dialog = new Dialog(activity);
        dialog.setContentView(view);
        return dialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        requireActivity().finish();
    }
}
