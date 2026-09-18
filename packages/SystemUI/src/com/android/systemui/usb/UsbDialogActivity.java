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

package com.android.systemui.usb;

import static android.hardware.usb.flags.Flags.enablePersistentUsbDevicePermissions;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.systemui.res.R;

abstract class UsbDialogActivity extends AlertActivity
        implements DialogInterface.OnClickListener, CheckBox.OnCheckedChangeListener {

    private static final String TAG = UsbDialogActivity.class.getSimpleName();

    protected UsbDialogHelper mDialogHelper;
    private CheckBox mAlwaysUse;
    private TextView mClearDefaultHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        try {
            mDialogHelper = new UsbDialogHelper(getApplicationContext(), getIntent());
        } catch (IllegalStateException e) {
            Log.e(TAG, "unable to initialize", e);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mDialogHelper.registerUsbDisconnectedReceiver(this);
    }

    @Override
    protected void onPause() {
        if (mDialogHelper != null) {
            mDialogHelper.unregisterUsbDisconnectedReceiver(this);
        }
        super.onPause();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            // The legacy UI lacks a button to grant persistent permission,
            // so this value is always false.
            onConfirm(/* isPersistent= */ false);
        } else {
            finish();
        }
    }

    @Override
    public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
        if (mClearDefaultHint == null) return;

        if (isChecked) {
            mClearDefaultHint.setVisibility(View.VISIBLE);
        } else {
            mClearDefaultHint.setVisibility(View.GONE);
        }
    }

    private void setAlertParams(CharSequence title, CharSequence message) {
        final AlertController.AlertParams ap = mAlertParams;
        ap.mTitle = title;
        ap.mMessage = message;
        ap.mPositiveButtonText = getString(android.R.string.ok);
        ap.mNegativeButtonText = getString(android.R.string.cancel);
        ap.mPositiveButtonListener = this;
        ap.mNegativeButtonListener = this;
    }

    private View createAlwaysUseCheckboxView() {
        final View alwaysUseCheckboxView =
                LayoutInflater.from(this)
                        .inflate(com.android.internal.R.layout.always_use_checkbox, null);
        mAlwaysUse = alwaysUseCheckboxView.findViewById(com.android.internal.R.id.alwaysUse);

        if (mDialogHelper.isUsbAccessory()) {
            mAlwaysUse.setText(
                    getString(
                            R.string.always_use_accessory,
                            mDialogHelper.getAppName(),
                            mDialogHelper.getDeviceDescription()));
        } else {
            // UsbDevice case
            mAlwaysUse.setText(
                    getString(
                            R.string.always_use_device,
                            mDialogHelper.getAppName(),
                            mDialogHelper.getDeviceDescription()));
        }

        mAlwaysUse.setOnCheckedChangeListener(this);
        mClearDefaultHint =
                alwaysUseCheckboxView.findViewById(com.android.internal.R.id.clearDefaultHint);
        mClearDefaultHint.setVisibility(View.GONE);

        return alwaysUseCheckboxView;
    }

    private View createDialogView(CharSequence title, CharSequence message) {
        final View view =
                LayoutInflater.from(this).inflate(R.layout.usb_device_dialog, /* root= */ null);

        ((TextView) view.findViewById(R.id.usb_device_dialog_title)).setText(title);

        if (message != null) {
            TextView messageView = view.findViewById(R.id.usb_device_dialog_message);
            messageView.setText(message);
            messageView.setVisibility(View.VISIBLE);
        }

        if (mDialogHelper.isUsbDevice()) {
            Log.d(TAG, "Display always allow button only for UsbDevice.");

            view.findViewById(R.id.usb_device_dialog_always_allow_button)
                    .setOnClickListener(v -> onConfirm(/* isPersistent= */ true));
            view.findViewById(R.id.usb_device_dialog_always_allow_button)
                    .setVisibility(View.VISIBLE);
        }

        view.findViewById(R.id.usb_device_dialog_allow_only_this_time_button)
                .setOnClickListener(v -> onConfirm(/* isPersistent= */ false));
        view.findViewById(R.id.usb_device_dialog_cancel_button)
                .setOnClickListener(v -> finish());

        return view;
    }

    /**
     * Displays the USB dialog.
     *
     * <p>This method determines whether to show the new UI (behind the {@code
     * enable_persistent_device_permissions} flag) or the legacy UI. It also handles the logic for
     * showing the "Always use" checkbox.
     */
    protected void showDialog(CharSequence title, CharSequence message, boolean canBeDefault) {
        // Only show the "always use" checkbox if there is no USB/Record warning
        final boolean useRecordWarning =
                mDialogHelper.isUsbDevice()
                        && mDialogHelper.deviceHasAudioCapture()
                        && !mDialogHelper.packageHasAudioRecordingPermission();
        final boolean showAlwaysUseCheckBox = canBeDefault && !useRecordWarning;

        if (enablePersistentUsbDevicePermissions()) {
            Log.d(TAG, "Show new UsbDialogActivity");

            mAlertParams.mView = createDialogView(title, message);
            if (showAlwaysUseCheckBox) {
                final ViewGroup deviceDialogViewGroup =
                        mAlertParams.mView.findViewById(R.id.usb_device_dialog_always_use_content);
                deviceDialogViewGroup.addView(createAlwaysUseCheckboxView());
                deviceDialogViewGroup.setVisibility(View.VISIBLE);
            }
        } else {
            Log.d(TAG, "Show old UsbDialogActivity");

            setAlertParams(title, message);
            if (showAlwaysUseCheckBox) {
                mAlertParams.mView = createAlwaysUseCheckboxView();
            }
        }

        setupAlert();
    }

    boolean isAlwaysUseChecked() {
        return mAlwaysUse != null && mAlwaysUse.isChecked();
    }

    /**
     * Called when the dialog is confirmed.
     *
     * @param isPersistent Whether the permission should be granted permanently.
     */
    abstract void onConfirm(boolean isPersistent);
}
