/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.res.Resources;
import android.os.Bundle;
import android.text.Html;

import androidx.annotation.VisibleForTesting;

import javax.inject.Inject;

/**
 * Dialog shown when a package requests access to a USB device or accessory.
 */
public class UsbPermissionActivity extends UsbDialogActivity {

    @VisibleForTesting boolean mPermissionGranted = false;
    private final UsbAudioWarningDialogMessage mUsbPermissionMessageHandler;

    @Inject
    public UsbPermissionActivity(UsbAudioWarningDialogMessage usbAudioWarningDialogMessage) {
        mUsbPermissionMessageHandler = usbAudioWarningDialogMessage;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mUsbPermissionMessageHandler.init(UsbAudioWarningDialogMessage.TYPE_PERMISSION,
                mDialogHelper);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final int titleId = mUsbPermissionMessageHandler.getPromptTitleId();
        final CharSequence title =
                Html.fromHtml(
                        getString(
                                titleId,
                                mDialogHelper.getAppName(),
                                mDialogHelper.getDeviceDescription()),
                        Html.FROM_HTML_MODE_LEGACY);
        final int messageId = mUsbPermissionMessageHandler.getMessageId();
        final CharSequence message =
                (messageId != Resources.ID_NULL)
                        ? getString(
                                messageId,
                                mDialogHelper.getAppName(),
                                mDialogHelper.getDeviceDescription())
                        : null;

        showDialog(title, message, mDialogHelper.canBeDefault());
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            mDialogHelper.sendPermissionDialogResponse(mPermissionGranted);
        }
        super.onPause();
    }

    @Override
    void onConfirm(boolean isPersistent) {
        mDialogHelper.grantUidAccessPermission(isPersistent);
        if (isAlwaysUseChecked()) {
            mDialogHelper.setDefaultPackage();
        }
        mPermissionGranted = true;
        finish();
    }
}
