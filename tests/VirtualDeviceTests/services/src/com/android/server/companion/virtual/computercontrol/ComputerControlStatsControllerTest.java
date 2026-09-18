/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.companion.virtual.computercontrol;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.LiteProtoTruth.assertThat;

import static org.mockito.Mockito.times;

import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.content.AttributionSource;
import android.content.pm.PackageManager;
import android.platform.test.annotations.Presubmit;
import android.util.StatsEvent;
import android.util.StatsEventTestUtils;
import android.util.StatsLog;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.os.AttributionNode;
import com.android.os.computercontrol.ComputercontrolExtensionAtoms;
import com.android.os.computercontrol.ComputercontrolExtensionAtoms.ComputerControlFailedSessionReported;
import com.android.os.computercontrol.ComputercontrolExtensionAtoms.ComputerControlSessionReported;

import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;

import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ComputerControlStatsControllerTest {
    private static final AttributionSource DEFAULT_ATTRIBUTION_SOURCE =
            new AttributionSource(100, "com.agent", "");
    private static final String DEFAULT_TARGET_PACKAGE_NAME = "com.android.settings";
    private static final int DEFAULT_CLOSE_REASON =
            ComputerControlSession.CLOSE_REASON_SESSION_EMPTY;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(StatsLog.class).build();

    private final ComputerControlSessionReported mDefaultReported =
            ComputerControlSessionReported.newBuilder()
                    .addAttributionNode(AttributionNode.newBuilder().setUid(100).build())
                    .addTargetApplicationsUids(getPackageUid(DEFAULT_TARGET_PACKAGE_NAME))
                    .setTargetApplicationCount(1)
                    .setCloseReason(ComputerControlSessionReported.CloseReason.SESSION_EMPTY)
                    .build();
    @Captor ArgumentCaptor<StatsEvent> mStatsEventCaptor;
    private final ExtensionRegistryLite mExtensionRegistry = ExtensionRegistryLite.newInstance();
    private Instant mNow = Instant.EPOCH;

    private ComputerControlStatsController mController;

    @Before
    public void setUp() {
        mController =
                new ComputerControlStatsController(
                        getApplicationContext().getPackageManager(),
                        DEFAULT_ATTRIBUTION_SOURCE,
                        new ComputerControlSessionParams.Builder()
                                .setName("test")
                                .setTargetPackageNames(List.of(DEFAULT_TARGET_PACKAGE_NAME))
                                .build(),
                        () -> mNow);
        ComputercontrolExtensionAtoms.registerAllExtensions(mExtensionRegistry);
    }

    @Test
    public void writeFailedSessionWithStatsReason() {
        AttributionSource attributionSource =
                new AttributionSource(100, "com.agent", "agent tag")
                        .withNextAttributionSource(
                                new AttributionSource(200, "com.trigger", "trigger tag"));

        ComputerControlStatsController.writeFailedSessionWithStatsReason(
                getApplicationContext().getPackageManager(),
                attributionSource,
                new ComputerControlSessionParams.Builder()
                        .setName("test")
                        .setTargetPackageNames(
                                List.of(
                                        "com.android.settings",
                                        "com.unknown",
                                        "com.android.server.companion.virtual"))
                        .build(),
                ComputerControlStatsLog
                        .COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__DEVICE_LOCKED);

        assertThat(getReportedFailedSession())
                .isEqualTo(
                        ComputerControlFailedSessionReported.newBuilder()
                                .addAttributionNode(
                                        AttributionNode.newBuilder()
                                                .setUid(200)
                                                .setTag("trigger tag")
                                                .build())
                                .addAttributionNode(
                                        AttributionNode.newBuilder()
                                                .setUid(100)
                                                .setTag("agent tag")
                                                .build())
                                .addTargetApplicationsUids(getPackageUid("com.android.settings"))
                                .addTargetApplicationsUids(
                                        getPackageUid("com.android.server.companion.virtual"))
                                .setTargetApplicationCount(3) // Including unknown.
                                .setReason(
                                        ComputerControlFailedSessionReported.FailReason
                                                .DEVICE_LOCKED)
                                .build());
    }

    @Test
    public void onSessionClosed_logsMetadata() {
        AttributionSource attributionSource =
                new AttributionSource(100, "com.agent", "agent tag")
                        .withNextAttributionSource(
                                new AttributionSource(200, "com.trigger", "trigger tag"));
        mController =
                new ComputerControlStatsController(
                        getApplicationContext().getPackageManager(),
                        attributionSource,
                        new ComputerControlSessionParams.Builder()
                                .setName("test")
                                .setTargetPackageNames(
                                        List.of(
                                                "com.android.settings",
                                                "com.unknown",
                                                "com.android.server.companion.virtual"))
                                .build());

        mController.onSessionClosed(ComputerControlSession.CLOSE_REASON_USER_INITIATED);

        assertThat(getReportedSession())
                .isEqualTo(
                        ComputerControlSessionReported.newBuilder()
                                .addAttributionNode(
                                        AttributionNode.newBuilder()
                                                .setUid(200)
                                                .setTag("trigger tag")
                                                .build())
                                .addAttributionNode(
                                        AttributionNode.newBuilder()
                                                .setUid(100)
                                                .setTag("agent tag")
                                                .build())
                                .addTargetApplicationsUids(getPackageUid("com.android.settings"))
                                .addTargetApplicationsUids(
                                        getPackageUid("com.android.server.companion.virtual"))
                                .setTargetApplicationCount(3) // Including unknown.
                                .setCloseReason(
                                        ComputerControlSessionReported.CloseReason.USER_INITIATED)
                                .build());
    }

    @Test
    public void onSessionClosed_twice_logsOnce() {
        mController.onSessionClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);
        mController.onSessionClosed(ComputerControlSession.CLOSE_REASON_USER_INITIATED);

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), times(1));
        assertThat(getReportedSession().getCloseReason())
                // The first close reason is logged.
                .isEqualTo(ComputerControlSessionReported.CloseReason.CALLER_INITIATED);
    }

    @Test
    public void onSessionClosed_onSessionActive_logsDuration() {
        mNow = Instant.ofEpochMilli(100);
        mController.onSessionActive();
        mNow = Instant.ofEpochMilli(300);

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession().getDurationMillis()).isEqualTo(200);
    }

    @Test
    public void onSessionClosed_noOnSessionActive_logsZeroDuration() {
        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession().getDurationMillis()).isEqualTo(0);
    }

    @Test
    public void onSessionClosed_onSessionBlocked_logsBlockReasons() {
        mController.onSessionBlocked(ComputerControlSession.BLOCK_REASON_SECURE_CONTENT);
        mController.onSessionBlocked(
                ComputerControlSession.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH);

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession())
                .isEqualTo(
                        mDefaultReported.toBuilder()
                                .addBlockReasons(
                                        ComputerControlSessionReported.BlockReason.SECURE_CONTENT)
                                .addBlockReasons(
                                        ComputerControlSessionReported.BlockReason
                                                .DISALLOWED_ACTIVITY_LAUNCH)
                                .setBlockCount(2)
                                .build());
    }

    @Test
    public void onSessionClosed_onApplicationLaunched_logsApplicationLaunches() {
        mController.onApplicationLaunched("com.android.settings");
        mController.onApplicationLaunched("com.unknown");
        mController.onApplicationLaunched("com.android.server.companion.virtual");

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession())
                .isEqualTo(
                        mDefaultReported.toBuilder()
                                .addApplicationLaunchesUids(getPackageUid("com.android.settings"))
                                .addApplicationLaunchesUids(
                                        getPackageUid("com.android.server.companion.virtual"))
                                .setApplicationLaunchCount(3) // Including unknown.
                                .build());
    }

    @Test
    public void onSessionClosed_onTap_logsTaps() {
        mController.onTap();
        mController.onTap();

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession().getTaps()).isEqualTo(2);
    }

    @Test
    public void onSessionClosed_onSwipe_logsSwipes() {
        mController.onSwipe();
        mController.onSwipe();

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession().getSwipes()).isEqualTo(2);
    }

    @Test
    public void onSessionClosed_onLongPress_logsLongPresses() {
        mController.onLongPress();
        mController.onLongPress();

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession().getLongPresses()).isEqualTo(2);
    }

    @Test
    public void onSessionClosed_onInsertText_logsInsertTexts() {
        mController.onInsertText();
        mController.onInsertText();

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession().getInsertTexts()).isEqualTo(2);
    }

    @Test
    public void onSessionClosed_onPerformAction_logsPerformedActions() {
        mController.onPerformAction(ComputerControlSession.ACTION_GO_BACK);
        mController.onPerformAction(-1); // Unknown.
        mController.onPerformAction(ComputerControlSession.ACTION_GO_BACK);

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession())
                .isEqualTo(
                        mDefaultReported.toBuilder()
                                .addPerformedActions(ComputerControlSessionReported.Action.GO_BACK)
                                .addPerformedActions(
                                        ComputerControlSessionReported.Action.ACTION_UNKNOWN)
                                .addPerformedActions(ComputerControlSessionReported.Action.GO_BACK)
                                .setPerformedActionCount(3)
                                .build());
    }

    @Test
    public void onSessionClosed_onMirroringStartedButNotStopped_logsMirroringDuration() {
        mNow = Instant.ofEpochMilli(100);
        mController.onMirroringStarted();
        mNow = Instant.ofEpochMilli(300); // +200ms

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession())
                .isEqualTo(
                        mDefaultReported.toBuilder()
                                .setMirroringTotalDurationMillis(200) // 300 - 100
                                .build());
    }

    @Test
    public void onSessionClosed_onMirroringStartedAndStopped_logsMirroringDuration() {
        mNow = Instant.ofEpochMilli(100);
        mController.onMirroringStarted();
        mNow = Instant.ofEpochMilli(300);
        mController.onMirroringStopped();
        mNow = Instant.ofEpochMilli(1000); // This time is not logged.

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession())
                .isEqualTo(
                        mDefaultReported.toBuilder()
                                .setMirroringTotalDurationMillis(200) // 300 - 100
                                .build());
    }

    @Test
    public void onMirroringStopped_withoutStarted_doesNotLogMirroringDuration() {
        mNow = Instant.ofEpochMilli(100); // This time is not logged.
        mController.onMirroringStopped();
        mNow = Instant.ofEpochMilli(200); // This time is not logged.

        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession()).isEqualTo(mDefaultReported);
    }

    @Test
    public void onSessionClosed_onMirroringStartedMultipleTimes_logsTotalMirroringDuration() {
        mNow = Instant.ofEpochMilli(100);
        mController.onMirroringStarted();
        mNow = Instant.ofEpochMilli(200); // +100ms
        mController.onMirroringStopped();
        mNow = Instant.ofEpochMilli(1000); // +800ms - not counted.
        mController.onMirroringStarted();
        mNow = Instant.ofEpochMilli(1200); // +200ms
        mController.onSessionClosed(DEFAULT_CLOSE_REASON);

        assertThat(getReportedSession())
                .isEqualTo(
                        mDefaultReported.toBuilder()
                                // (200 - 100) + (1200 - 1000)
                                .setMirroringTotalDurationMillis(300)
                                .build());
    }

    private ComputerControlFailedSessionReported getReportedFailedSession() {
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));
        try {
            return StatsEventTestUtils.convertToAtom(
                            mStatsEventCaptor.getValue(), mExtensionRegistry)
                    .getExtension(
                            ComputercontrolExtensionAtoms.computerControlFailedSessionReported);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private ComputerControlSessionReported getReportedSession() {
        verify(() -> StatsLog.write(mStatsEventCaptor.capture()));
        try {
            return StatsEventTestUtils.convertToAtom(
                            mStatsEventCaptor.getValue(), mExtensionRegistry)
                    .getExtension(ComputercontrolExtensionAtoms.computerControlSessionReported);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getPackageUid(String packageName) {
        try {
            return getApplicationContext().getPackageManager().getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
