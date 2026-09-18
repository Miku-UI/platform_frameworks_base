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

import android.annotation.StringRes;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.computercontrol.ComputerControlConsentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

/**
 * A VDM consent dialog for starting a computer control session.
 *
 * If {@code ARG_TARGET_PACKAGE_NAME} is provided this will show a per-app consent flow dialog.
 * If not, it will show the legacy global consent flow dialog.
 */
public class RequestComputerControlAccessFragment extends DialogFragment {

    private static final String TAG = "ComputerControlAccess";
    private static final String ARG_AGENT_UID = "argAgentUid";
    private static final String ARG_AGENT_PACKAGE_NAME = "argAgentPackageName";
    private static final String ARG_TARGET_PACKAGE_NAME = "argTargetPackageName";
    private static final String ARG_RESULT_RECEIVER = "argResultReceiver";
    private static final String PREF_COMPUTER_CONTROL_ACCESS_COUNTER =
            "computer_control_access_counter";
    private static final int MAX_DENIALS = 3;

    private String mAgentPackageName;
    private ResultReceiver mResultReceiver;

    private CharSequence mAgentAppLabel;
    private Drawable mAgentAppIcon;
    private int mAgentUid;

    @Nullable
    private String mTargetPackageName;
    @Nullable
    private CharSequence mTargetAppLabel;


    static RequestComputerControlAccessFragment newInstance(int agentUid,
            @NonNull String agentPackageName, @Nullable String targetPackageName,
            @NonNull ResultReceiver resultReceiver) {
        Log.d("ComputerControl",
                "RequestComputerControlAccessFragment: new fragment for " + agentPackageName
                        + " automating " + targetPackageName);
        final var fragment = new RequestComputerControlAccessFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_AGENT_UID, agentUid);
        args.putString(ARG_AGENT_PACKAGE_NAME, agentPackageName);
        args.putString(ARG_TARGET_PACKAGE_NAME, targetPackageName);
        args.putParcelable(ARG_RESULT_RECEIVER, resultReceiver);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mAgentUid = args.getInt(ARG_AGENT_UID);
        mAgentPackageName = args.getString(ARG_AGENT_PACKAGE_NAME);
        mResultReceiver = args.getParcelable(ARG_RESULT_RECEIVER, ResultReceiver.class);
        mTargetPackageName = args.getString(ARG_TARGET_PACKAGE_NAME);

        PackageManager packageManager = requireActivity().getPackageManager();
        try {
            final ApplicationInfo agentAppInfo = packageManager.getApplicationInfo(
                    mAgentPackageName, PackageManager.ApplicationInfoFlags.of(0));
            mAgentAppLabel = packageManager.getApplicationLabel(agentAppInfo);
            mAgentAppIcon = packageManager.getApplicationIcon(agentAppInfo);

            if (mTargetPackageName != null) {
                final ApplicationInfo targetAppInfo = packageManager.getApplicationInfo(
                        mTargetPackageName, PackageManager.ApplicationInfoFlags.of(0));
                mTargetAppLabel = packageManager.getApplicationLabel(targetAppInfo);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to open consent dialog because package was not found", e);
            sendResult(Activity.RESULT_CANCELED);
        }
        checkAppOp();
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();
        View view = activity.getLayoutInflater()
                .inflate(R.layout.request_computer_control_access_dialog, null);

        view.requireViewById(R.id.allow_button).setOnClickListener(this::onAllow);
        view.requireViewById(R.id.dont_allow_button).setOnClickListener(this::onDenied);
        view.requireViewById(R.id.always_allow_button).setOnClickListener(this::onAlwaysAllow);

        TextView titleView = view.requireViewById(R.id.title);
        Spanned title;
        if (isPerAppConsentEnabled()) {
            title = getHtmlFromResources(
                    R.string.request_computer_control_access_per_app_consent_dialog_title,
                    mAgentAppLabel, mTargetAppLabel);
        } else {
            title = getHtmlFromResources(
                    R.string.request_computer_control_access_dialog_title, mAgentAppLabel);
        }
        titleView.setText(title);
        TextView descriptionView = view.findViewById(R.id.description);
        Spanned description = getHtmlFromResources(
                R.string.request_computer_control_access_dialog_description, mAgentAppLabel);
        descriptionView.setText(description);

        ImageView iconView = view.findViewById(R.id.agent_icon);
        if (isPerAppConsentEnabled()) {
            iconView.setImageResource(R.drawable.ic_automation);
        } else {
            iconView.setImageDrawable(mAgentAppIcon);
        }

        Dialog dialog = new Dialog(activity);
        dialog.setContentView(view);
        return dialog;
    }

    private void checkAppOp() {
        AppOpsManager appOpsManager = requireActivity().getSystemService(AppOpsManager.class);
        int result = appOpsManager.noteOpNoThrow(AppOpsManager.OP_COMPUTER_CONTROL, mAgentUid,
                mAgentPackageName, null,
                "Package " + mAgentPackageName + " is requesting computer control access");
        if (result == AppOpsManager.MODE_IGNORED || result == AppOpsManager.MODE_ERRORED) {
            Log.w(TAG,
                    mAgentPackageName + " does not have APP_OP permission to open consent dialog");
            sendResult(Activity.RESULT_CANCELED);
        }
    }

    private void sendResult(int resultCode) {
        if (mResultReceiver != null) {
            mResultReceiver.send(resultCode, null);
        }
        dismissAllowingStateLoss();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        sendResult(Activity.RESULT_CANCELED);
    }

    private void onAllow(View view) {
        resetDenialCount();
        sendResult(Activity.RESULT_OK);
    }

    private void onAlwaysAllow(View view) {
        resetDenialCount();
        setComputerControlOp(AppOpsManager.MODE_ALLOWED);

        if (!isPerAppConsentEnabled()) {
            sendResult(Activity.RESULT_OK);
            return;
        }

        VirtualDeviceManager vdm = requireActivity().getSystemService(VirtualDeviceManager.class);
        if (vdm == null) {
            Log.w(TAG, "No VirtualDeviceManager found");
            sendResult(Activity.RESULT_CANCELED);
            return;
        }
        ComputerControlConsentManager computerControlConsentManager =
                vdm.getComputerControlConsentManager();
        if (computerControlConsentManager == null) {
            Log.w(TAG, "No AllowlistManager found");
            sendResult(Activity.RESULT_CANCELED);
            return;
        }
        computerControlConsentManager.addAppToAutomatableAppListForAgent(mAgentUid,
                mAgentPackageName, mTargetPackageName);
        sendResult(Activity.RESULT_OK);
    }

    private void setComputerControlOp(int mode) {
        AppOpsManager appOpsManager = requireActivity().getSystemService(AppOpsManager.class);
        appOpsManager.setMode(AppOpsManager.OP_COMPUTER_CONTROL, mAgentUid, mAgentPackageName,
                mode);
    }

    private void onDenied(View v) {
        if (increaseDenialCount() >= MAX_DENIALS) {
            setComputerControlOp(AppOpsManager.MODE_IGNORED);
            resetDenialCount();
        }
        if (getDialog() != null) {
            getDialog().cancel();
        }
    }

    private int increaseDenialCount() {
        SharedPreferences preferences = getContext().getSharedPreferences(
                "computer_control_access_counter", Context.MODE_PRIVATE);
        int deniedCount = preferences.getInt(mAgentPackageName, 0);
        deniedCount++;
        preferences.edit().putInt(mAgentPackageName, deniedCount).apply();
        return deniedCount;
    }

    private void resetDenialCount() {
        SharedPreferences preferences = getContext().getSharedPreferences(
                PREF_COMPUTER_CONTROL_ACCESS_COUNTER, Context.MODE_PRIVATE);
        preferences.edit().remove(mAgentPackageName).apply();
    }

    private Spanned getHtmlFromResources(@StringRes int resId, CharSequence... formatArgs) {
        final String[] escapedArgs = new String[formatArgs.length];
        for (int i = 0; i < escapedArgs.length; i++) {
            escapedArgs[i] = formatArgs[i] == null ? null : Html.escapeHtml(formatArgs[i]);
        }
        final String plain = requireActivity().getString(resId, (Object[]) escapedArgs);
        return Html.fromHtml(plain, 0);
    }

    private boolean isPerAppConsentEnabled() {
        return mTargetPackageName != null;
    }
}
