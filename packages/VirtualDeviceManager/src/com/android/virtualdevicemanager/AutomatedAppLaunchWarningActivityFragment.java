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
import android.app.ActivityOptions;
import android.app.Dialog;
import android.companion.virtual.IVirtualDeviceManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.util.Slog;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class AutomatedAppLaunchWarningActivityFragment  extends DialogFragment {

    private static final String TAG = "AutomatedAppLaunchWarning";

    private static final String ARG_AGENT_PACKAGE_NAME = "argAgentPackageName";
    private static final String ARG_TARGET = "argTarget";
    private static final String ARG_TARGET_PACKAGE_NAME = "argTargetPackageName";
    private static final String ARG_RESULT_RECEIVER = "argResultReceiver";

    private String mAgentPackageName;
    private IntentSender mTarget;
    private String mTargetPackageName;
    private ResultReceiver mResultReceiver;


    private CharSequence mAgentAppLabel;
    private Drawable mAgentAppIcon;
    private CharSequence mTargetAppLabel;
    private Drawable mTargetAppIcon;

    static AutomatedAppLaunchWarningActivityFragment newInstance(
            @NonNull String targetPackageName, @NonNull IntentSender target,
            @NonNull String agentPackageName, @NonNull ResultReceiver resultReceiver) {
        final var fragment = new AutomatedAppLaunchWarningActivityFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_AGENT_PACKAGE_NAME, agentPackageName);
        args.putParcelable(ARG_TARGET, target);
        args.putString(ARG_TARGET_PACKAGE_NAME, targetPackageName);
        args.putParcelable(ARG_RESULT_RECEIVER, resultReceiver);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAgentPackageName = getArguments().getString(ARG_AGENT_PACKAGE_NAME);
        mTarget = getArguments().getParcelable(ARG_TARGET, IntentSender.class);
        mTargetPackageName = getArguments().getString(ARG_TARGET_PACKAGE_NAME);
        mResultReceiver = getArguments().getParcelable(ARG_RESULT_RECEIVER, ResultReceiver.class);

        PackageManager packageManager = requireActivity().getPackageManager();
        try {
            final ApplicationInfo agentAppInfo = packageManager.getApplicationInfo(
                    mAgentPackageName, PackageManager.ApplicationInfoFlags.of(0));
            mAgentAppLabel = packageManager.getApplicationLabel(agentAppInfo);
            mAgentAppIcon = packageManager.getApplicationIcon(agentAppInfo);
            final ApplicationInfo targetAppInfo = packageManager.getApplicationInfo(
                    mTargetPackageName, PackageManager.ApplicationInfoFlags.of(0));
            mTargetAppLabel = packageManager.getApplicationLabel(targetAppInfo);
            mTargetAppIcon = packageManager.getApplicationIcon(targetAppInfo);
        } catch (PackageManager.NameNotFoundException e) {
            requireActivity().finish();
        }
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();
        View view = activity.getLayoutInflater()
                .inflate(R.layout.automated_app_launch_warning_activity_dialog, null);

        view.findViewById(R.id.stop_button).setOnClickListener(this::onStop);
        view.findViewById(R.id.wait_button).setOnClickListener(v -> getDialog().cancel());

        TextView titleView = view.findViewById(R.id.title);
        titleView.setText(requireActivity().getString(
                R.string.automated_app_launch_warning_dialog_title, mAgentAppLabel,
                mTargetAppLabel));
        TextView descriptionView = view.findViewById(R.id.description);
        descriptionView.setText(requireActivity().getString(
                R.string.automated_app_launch_warning_dialog_description, mAgentAppLabel,
                mTargetAppLabel));

        ImageView agentIconView = view.findViewById(R.id.agent_icon);
        agentIconView.setImageDrawable(mAgentAppIcon);
        ImageView targetIconView = view.findViewById(R.id.target_icon);
        targetIconView.setImageDrawable(mTargetAppIcon);

        Dialog dialog = new Dialog(activity);
        dialog.setContentView(view);
        return dialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        requireActivity().finish();
    }

    private void onStop(View view) {
        var vdm = IVirtualDeviceManager.Stub.asInterface(
                ServiceManager.getService(Context.VIRTUAL_DEVICE_SERVICE));
        if (vdm == null) {
            Slog.e(TAG, "Failed to get VDM");
            requireActivity().finish();
        }

        mResultReceiver.send(Activity.RESULT_OK, null);

        final Bundle activityOptions = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle();
        try {
            mTarget.sendIntent(requireActivity(), 0, null, null, activityOptions, null, null);
        } catch (IntentSender.SendIntentException e) {
            Slog.e(TAG, "Error while starting intent " + mTarget, e);
        }
        requireActivity().finish();
    }
}
