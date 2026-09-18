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

import javax.inject.Inject;

/**
 * Dialog shown to confirm the package to start when a USB device or accessory is attached and there
 * is only one package that claims to handle this USB device or accessory.
 */
public class UsbConfirmActivity extends UsbDialogActivity {

    private final UsbAudioWarningDialogMessage mUsbConfirmMessageHandler;

    @Inject
    public UsbConfirmActivity(UsbAudioWarningDialogMessage usbAudioWarningDialogMessage) {
        mUsbConfirmMessageHandler = usbAudioWarningDialogMessage;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mUsbConfirmMessageHandler.init(UsbAudioWarningDialogMessage.TYPE_CONFIRM, mDialogHelper);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final int titleId = mUsbConfirmMessageHandler.getPromptTitleId();
        final CharSequence title =
                Html.fromHtml(
                        getString(
                                titleId,
                                mDialogHelper.getAppName(),
                                mDialogHelper.getDeviceDescription()),
                        Html.FROM_HTML_MODE_LEGACY);
        final int messageId = mUsbConfirmMessageHandler.getMessageId();
        final CharSequence message =
                (messageId != Resources.ID_NULL)
                        ? getString(
                                messageId,
                                mDialogHelper.getAppName(),
                                mDialogHelper.getDeviceDescription())
                        : null;

        showDialog(title, message, /* canBeDefault= */ true);
    }

    @Override
    void onConfirm(boolean isPersistent) {
        mDialogHelper.grantUidAccessPermission(isPersistent);
        if (isAlwaysUseChecked()) {
            mDialogHelper.setDefaultPackage();
        } else {
            mDialogHelper.clearDefaultPackage();
        }
        mDialogHelper.confirmDialogStartActivity();
        finish();
    }
}
