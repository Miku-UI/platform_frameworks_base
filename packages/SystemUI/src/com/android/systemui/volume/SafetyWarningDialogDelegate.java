/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.systemui.volume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources.NotFoundException;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.android.systemui.statusbar.phone.SystemUIDialog;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.HashSet;
import java.util.Set;

public class SafetyWarningDialogDelegate implements
        SystemUIDialog.Delegate,
        DialogInterface.OnKeyListener,
        DialogInterface.OnDismissListener,
        DialogInterface.OnClickListener {

    private static final String TAG = Util.logTag(SafetyWarningDialogDelegate.class);

    private static final int KEY_CONFIRM_ALLOWED_AFTER = 1000; // milliseconds

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final SystemUIDialog.Factory mSystemUIDialogFactory;
    private final Cleanup mCleanup;
    private final Set<SystemUIDialog> mDialogs = new HashSet<>();

    private long mShowTime;
    private boolean mNewVolumeUp;
    private boolean mDisableOnVolumeUp;

    @AssistedFactory
    public interface Factory {
        /** Create a SafetyWarningDialogDelegate */
        SafetyWarningDialogDelegate create(Cleanup cleanup);
    }

    @AssistedInject
    public SafetyWarningDialogDelegate(
            Context context,
            AudioManager audioManager,
            SystemUIDialog.Factory systemUIDialogFactory,
            @Assisted Cleanup cleanup) {

        mContext = context;
        mAudioManager = audioManager;
        mSystemUIDialogFactory = systemUIDialogFactory;
        mCleanup = cleanup;
        try {
            mDisableOnVolumeUp = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_safe_media_disable_on_volume_up);
        } catch (NotFoundException e) {
            mDisableOnVolumeUp = true;
        }

        final IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.registerReceiver(mReceiver, filter,
                Context.RECEIVER_EXPORTED_UNAUDITED);
    }

    @Override
    public SystemUIDialog createDialog() {
        SystemUIDialog dialog = mSystemUIDialogFactory.create(this);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        dialog.setShowForAllUsers(true);
        dialog.setMessage(mContext.getString(
                com.android.internal.R.string.safe_media_volume_warning));
        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                mContext.getString(com.android.internal.R.string.yes), this);
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                mContext.getString(com.android.internal.R.string.no),
                (DialogInterface.OnClickListener) null);
        dialog.setOnDismissListener(this);

        mDialogs.add(dialog);

        return dialog;
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return onKeyDown(keyCode, event);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            return onKeyUp(dialog, keyCode, event);
        }

        return false;
    }

    private boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mDisableOnVolumeUp && keyCode == KeyEvent.KEYCODE_VOLUME_UP
                && event.getRepeatCount() == 0) {
            mNewVolumeUp = true;
        }
        return false;
    }

    private boolean onKeyUp(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && mNewVolumeUp
                && (System.currentTimeMillis() - mShowTime) > KEY_CONFIRM_ALLOWED_AFTER) {
            if (D.BUG) Log.d(TAG, "Confirmed warning via VOLUME_UP");
            mAudioManager.disableSafeMediaVolume();
            dialog.dismiss();
        }
        return false;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        mAudioManager.disableSafeMediaVolume();
    }

    @Override
    public void onStart(SystemUIDialog dialog) {
        mShowTime = System.currentTimeMillis();
    }

    @Override
    public void onDismiss(DialogInterface unused) {
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException e) {
            // Don't crash if the receiver has already been unregistered.
            e.printStackTrace();
        }
        mCleanup.cleanup();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                if (D.BUG) Log.d(TAG, "Received ACTION_CLOSE_SYSTEM_DIALOGS");
                mDialogs.forEach(SystemUIDialog::cancel);
                mDialogs.clear();
            }
        }
    };

    public interface Cleanup {
        /** Called after the Dialog is dismissed. */
        void cleanup();
    }
}
