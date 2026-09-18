/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.platform.test.annotations.EnableFlags;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.messages.nano.SystemMessageProto;
import com.android.systemui.Flags;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.plugins.VolumeDialog;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CsdWarningDialogDelegateTest extends SysuiTestCase {

    private final FakeExecutor mExecutor =  new FakeExecutor(new FakeSystemClock());
    @Mock
    private NotificationManager mNotificationManager;
    @Mock
    private AudioManager mAudioManager;
    private BroadcastDispatcher mFakeBroadcastDispatcher;
    @Mock
    private SystemUIDialog mSystemUIDialog;
    @Mock
    private SystemUIDialog.Factory mSystemUIDialogFactory;
    private static final String DISMISS_CSD_NOTIFICATION =
            "com.android.systemui.volume.DISMISS_CSD_NOTIFICATION";
    private final Optional<List<CsdWarningAction>> mEmptyActions =
            Optional.of(Collections.emptyList());

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mFakeBroadcastDispatcher = getFakeBroadcastDispatcher();

        when(mSystemUIDialogFactory.create(any(SystemUIDialog.Delegate.class)))
                .thenReturn(mSystemUIDialog);
    }

    @Test
    public void create1XCsdDialogAndWait_sendsNotification() {
        CsdWarningDialogDelegate delegate = createDelegate(
                AudioManager.CSD_WARNING_DOSE_REACHED_1X, mEmptyActions);
        delegate.maybeShow(mSystemUIDialog);
        delegate.onStart(mSystemUIDialog);

        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mNotificationManager).notify(
                eq(SystemMessageProto.SystemMessage.NOTE_CSD_LOWER_AUDIO), any(Notification.class));
    }

    @Test
    public void create5XCsdDialogAndWait_willSendNotification() {
        CsdWarningDialogDelegate delegate = createDelegate(
                AudioManager.CSD_WARNING_DOSE_REPEATED_5X, mEmptyActions);
        delegate.maybeShow(mSystemUIDialog);

        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mNotificationManager).notify(
                eq(SystemMessageProto.SystemMessage.NOTE_CSD_LOWER_AUDIO), any(Notification.class));
    }

    @Test
    @EnableFlags(Flags.FLAG_SOUNDDOSE_CUSTOMIZATION)
    public void create1XCsdDialogWithActionsAndUndoIntent_willRegisterReceiverAndUndoVolume() {
        Intent undoIntent = new Intent(VolumeDialog.ACTION_VOLUME_UNDO)
                .setPackage(mContext.getPackageName());
        CsdWarningDialogDelegate delegate = createDelegate(
                AudioManager.CSD_WARNING_DOSE_REPEATED_5X,
                Optional.of(List.of(new CsdWarningAction("Undo", undoIntent, false))));

        when(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(25);
        delegate.maybeShow(mSystemUIDialog);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        delegate.mReceiverUndo.onReceive(mContext, undoIntent);

        verify(mNotificationManager).notify(
                eq(SystemMessageProto.SystemMessage.NOTE_CSD_LOWER_AUDIO),
                any(Notification.class));
        verify(mAudioManager).setStreamVolume(
                eq(AudioManager.STREAM_MUSIC),
                eq(25),
                eq(AudioManager.FLAG_SHOW_UI));
    }

    @Test
    @EnableFlags(Flags.FLAG_SOUNDDOSE_CUSTOMIZATION)
    public void deleteNotificationIntent_willUnregisterAllReceivers() {
        Intent undoIntent = new Intent(VolumeDialog.ACTION_VOLUME_UNDO)
                .setPackage(mContext.getPackageName());
        CsdWarningDialogDelegate delegate = createDelegate(
                AudioManager.CSD_WARNING_DOSE_REPEATED_5X,
                Optional.of(List.of(new CsdWarningAction("Undo", undoIntent, false))));
        delegate.maybeShow(mSystemUIDialog);

        Intent dismissIntent = new Intent(DISMISS_CSD_NOTIFICATION)
                .setPackage(mContext.getPackageName());

        delegate.mReceiverDismissNotification.onReceive(mContext, dismissIntent);

        List<ResolveInfo> resolveInfoListDismiss = mContext.getPackageManager()
                .queryBroadcastReceivers(dismissIntent, PackageManager.GET_RESOLVED_FILTER);
        assertThat(resolveInfoListDismiss).hasSize(0);
        List<ResolveInfo> resolveInfoListUndo = mContext.getPackageManager()
                .queryBroadcastReceivers(undoIntent, PackageManager.GET_RESOLVED_FILTER);
        assertThat(resolveInfoListUndo).hasSize(0);
    }

    private CsdWarningDialogDelegate createDelegate(
            @AudioManager.CsdWarning int csdWarning,
            Optional<List<CsdWarningAction>> actionIntents) {
        return new CsdWarningDialogDelegate(
                csdWarning,
                mContext,
                mAudioManager,
                mNotificationManager,
                mExecutor,
                null,
                actionIntents,
                mFakeBroadcastDispatcher,
                mSystemUIDialogFactory);
    }
}
