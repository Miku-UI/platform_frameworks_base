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

package com.android.server.display.mode;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.Mode.INVALID_MODE_ID;

import static com.android.server.display.mode.DisplayModeDirector.SYNCHRONIZED_REFRESH_RATE_TOLERANCE;
import static com.android.server.display.mode.Vote.PRIORITY_LIMIT_MODE;
import static com.android.server.display.mode.Vote.PRIORITY_SYNCHRONIZED_REFRESH_RATE;
import static com.android.server.display.mode.Vote.PRIORITY_SYNCHRONIZED_RENDER_FRAME_RATE;
import static com.android.server.display.mode.Vote.PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS;
import static com.android.server.display.mode.VotesStorage.GLOBAL_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerInternal;
import android.os.Handler;
import android.os.Looper;
import android.provider.DeviceConfigInterface;
import android.testing.TestableContext;
import android.view.Display;
import android.view.DisplayInfo;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.internal.util.test.FakeSettingsProviderRule;
import com.android.server.display.ModeRequestManager;
import com.android.server.display.feature.DisplayManagerFlags;
import com.android.server.sensors.SensorManagerInternal;

import junitparams.JUnitParamsRunner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@SmallTest
@RunWith(JUnitParamsRunner.class)
public class DisplayObserverTest {
    @Rule public FakeSettingsProviderRule mSettingsProviderRule = FakeSettingsProvider.rule();

    private static final int EXTERNAL_DISPLAY = 1;
    private static final int MAX_WIDTH = 1920;
    private static final int MAX_HEIGHT = 1080;
    private static final int MAX_REFRESH_RATE = 60;

    private final Display.Mode[] mInternalDisplayModes =
            new Display.Mode[] {
                new Display.Mode(
                        /* modeId= */ 0,
                        MAX_WIDTH / 2,
                        MAX_HEIGHT / 2,
                        (float) MAX_REFRESH_RATE / 2),
                new Display.Mode(/* modeId= */ 1, MAX_WIDTH / 2, MAX_HEIGHT / 2, MAX_REFRESH_RATE),
                new Display.Mode(
                        /* modeId= */ 2, MAX_WIDTH / 2, MAX_HEIGHT / 2, MAX_REFRESH_RATE * 2),
                new Display.Mode(/* modeId= */ 3, MAX_WIDTH, MAX_HEIGHT, MAX_REFRESH_RATE * 2),
                new Display.Mode(/* modeId= */ 4, MAX_WIDTH, MAX_HEIGHT, MAX_REFRESH_RATE),
                new Display.Mode(/* modeId= */ 5, MAX_WIDTH * 2, MAX_HEIGHT * 2, MAX_REFRESH_RATE),
                new Display.Mode(
                        /* modeId= */ 6, MAX_WIDTH / 2, MAX_HEIGHT / 2, MAX_REFRESH_RATE * 3),
            };

    private final Display.Mode[] mExternalDisplayModes =
            new Display.Mode[] {
                new Display.Mode(
                        /* modeId= */ 0,
                        MAX_WIDTH / 2,
                        MAX_HEIGHT / 2,
                        (float) MAX_REFRESH_RATE / 2),
                new Display.Mode(/* modeId= */ 1, MAX_WIDTH / 2, MAX_HEIGHT / 2, MAX_REFRESH_RATE),
                new Display.Mode(
                        /* modeId= */ 2, MAX_WIDTH / 2, MAX_HEIGHT / 2, MAX_REFRESH_RATE * 2),
                new Display.Mode(/* modeId= */ 3, MAX_WIDTH, MAX_HEIGHT, MAX_REFRESH_RATE * 2),
                new Display.Mode(/* modeId= */ 4, MAX_WIDTH, MAX_HEIGHT, MAX_REFRESH_RATE),
                new Display.Mode(/* modeId= */ 5, MAX_WIDTH * 2, MAX_HEIGHT * 2, MAX_REFRESH_RATE),
            };

    private DisplayModeDirector mDmd;
    private DisplayModeDirector.Injector mInjector;
    private Handler mHandler;
    private DisplayManager.DisplayListener mObserver;
    @Mock private DisplayManagerFlags mDisplayManagerFlags;
    @Mock private ModeRequestManager mModeRequestManagerMock;
    @Mock private DisplayModeDirector.DisplayDeviceConfigProvider mDisplayDeviceConfigProvider;
    private int mExternalDisplayUserPreferredModeId = INVALID_MODE_ID;
    private int mInternalDisplayUserPreferredModeId = INVALID_MODE_ID;
    private Display mDefaultDisplay;
    private Display mExternalDisplay;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext());

    /** Setup tests. */
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mHandler = new Handler(Looper.getMainLooper());
        mSettingsProviderRule.mockContentResolver(mContext);

        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakRefreshRate, 0);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakWidth, 0);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakHeight, 0);
        mContext.getOrCreateTestableResources()
                .addOverride(R.bool.config_refreshRateSynchronizationEnabled, false);
    }

    /** Vote for user preferred mode */
    @Test
    public void testDefaultDisplay_voteUserPreferredMode() {
        var preferredMode = mInternalDisplayModes[5];
        mInternalDisplayUserPreferredModeId = preferredMode.getModeId();
        var expectedVote =
                Vote.forSize(preferredMode.getPhysicalWidth(), preferredMode.getPhysicalHeight());
        init();
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        mObserver.onDisplayAdded(DEFAULT_DISPLAY);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(expectedVote);

        mInternalDisplayUserPreferredModeId = INVALID_MODE_ID;
        mObserver.onDisplayChanged(EXTERNAL_DISPLAY);
        mObserver.onDisplayChanged(DEFAULT_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);

        preferredMode = mInternalDisplayModes[4];
        mInternalDisplayUserPreferredModeId = preferredMode.getModeId();
        expectedVote =
                Vote.forSize(preferredMode.getPhysicalWidth(), preferredMode.getPhysicalHeight());
        mObserver.onDisplayChanged(EXTERNAL_DISPLAY);
        mObserver.onDisplayChanged(DEFAULT_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(expectedVote);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);

        // Testing that the vote is removed.
        mObserver.onDisplayRemoved(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(expectedVote);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
    }

    /**
     * Vote for user preferred mode with refresh rate, config_refreshRateSynchronizationEnabled must
     * be disabled
     */
    @Test
    public void testExternalDisplay_voteUserPreferredMode_withRefreshRate() {
        var preferredMode = mExternalDisplayModes[5];
        mExternalDisplayUserPreferredModeId = preferredMode.getModeId();
        var expectedVote = Vote.forVotes(List.of(
                Vote.forSize(preferredMode.getPhysicalWidth(), preferredMode.getPhysicalHeight()),
                Vote.forPhysicalRefreshRates(preferredMode.getRefreshRate())));
        init();
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(expectedVote);

        mExternalDisplayUserPreferredModeId = INVALID_MODE_ID;
        mObserver.onDisplayChanged(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);

        preferredMode = mExternalDisplayModes[4];
        mExternalDisplayUserPreferredModeId = preferredMode.getModeId();
        expectedVote = Vote.forVotes(List.of(
                Vote.forSize(preferredMode.getPhysicalWidth(), preferredMode.getPhysicalHeight()),
                Vote.forPhysicalRefreshRates(preferredMode.getRefreshRate())));

        mObserver.onDisplayChanged(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(expectedVote);

        // Testing that the vote is removed.
        mObserver.onDisplayRemoved(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
    }

    /** External display: Do not apply limit to user preferred mode */
    @Test
    public void testExternalDisplay_doNotApplyLimitToUserPreferredMode() {
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakRefreshRate, MAX_REFRESH_RATE);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakWidth, MAX_WIDTH);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakHeight, MAX_HEIGHT);
        // synchronization must be enabled, to avoid refresh rate range vote.
        mContext.getOrCreateTestableResources()
                .addOverride(R.bool.config_refreshRateSynchronizationEnabled, true);

        var preferredMode = mExternalDisplayModes[5];
        mExternalDisplayUserPreferredModeId = preferredMode.getModeId();
        var expectedResolutionVote =
                Vote.forSize(preferredMode.getPhysicalWidth(), preferredMode.getPhysicalHeight());
        init();
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(expectedResolutionVote);

        // Testing that the vote is removed.
        mObserver.onDisplayRemoved(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
    }

    /** Default display: Do not apply limit to user preferred mode */
    @Test
    public void testDefaultDisplayAdded_notAppliedLimitToUserPreferredMode() {
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakRefreshRate, MAX_REFRESH_RATE);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakWidth, MAX_WIDTH);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakHeight, MAX_HEIGHT);
        var preferredMode = mInternalDisplayModes[5];
        mInternalDisplayUserPreferredModeId = preferredMode.getModeId();
        var expectedResolutionVote =
                Vote.forSize(preferredMode.getPhysicalWidth(), preferredMode.getPhysicalHeight());
        init();
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
        mObserver.onDisplayAdded(DEFAULT_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(expectedResolutionVote);
        mObserver.onDisplayRemoved(DEFAULT_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS))
                .isEqualTo(null);
    }

    /** Default display added, no mode limit set */
    @Test
    public void testDefaultDisplayAdded() {
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakRefreshRate, MAX_REFRESH_RATE);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakWidth, MAX_WIDTH);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakHeight, MAX_HEIGHT);
        init();
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        mObserver.onDisplayAdded(DEFAULT_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
    }

    /** External display added, apply resolution refresh rate limit */
    @Test
    public void testExternalDisplayAdded_applyResolutionRefreshRateLimit() {
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakRefreshRate, MAX_REFRESH_RATE);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakWidth, MAX_WIDTH);
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_externalDisplayPeakHeight, MAX_HEIGHT);
        init();
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_LIMIT_MODE))
                .isEqualTo(
                        Vote.forSizeAndPhysicalRefreshRatesRange(
                                0,
                                0,
                                MAX_WIDTH,
                                MAX_HEIGHT,
                                /* minPhysicalRefreshRate= */ 0,
                                MAX_REFRESH_RATE));
        mObserver.onDisplayRemoved(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
    }

    /** External display added, disabled resolution refresh rate limit. */
    @Test
    public void testExternalDisplayAdded_disabledResolutionRefreshRateLimit() {
        init();
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        mObserver.onDisplayChanged(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        mObserver.onDisplayRemoved(EXTERNAL_DISPLAY);
        assertThat(getVote(DEFAULT_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
        assertThat(getVote(EXTERNAL_DISPLAY, PRIORITY_LIMIT_MODE)).isEqualTo(null);
    }

    /** External display added, applied refresh rates synchronization */
    @Test
    public void testExternalDisplayAdded_appliedRefreshRatesSynchronization() {
        mContext.getOrCreateTestableResources()
                .addOverride(R.bool.config_refreshRateSynchronizationEnabled, true);
        init();
        assertNoRefreshRateSynchronizationVote();
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertRefreshRateSynchronizationVote();

        // Remove external display and check that sync vote is no longer present.
        mObserver.onDisplayRemoved(EXTERNAL_DISPLAY);
        assertNoRefreshRateSynchronizationVote();
    }

    /** External display added, disabled feature refresh rates synchronization */
    @Test
    public void testExternalDisplayAdded_disabledFeatureRefreshRatesSynchronization() {
        init();
        assertNoRefreshRateSynchronizationVote();
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertNoRefreshRateSynchronizationVote();
    }

    /**
     * External display not applied refresh rates synchronization, because
     * config_refreshRateSynchronizationEnabled is false.
     */
    @Test
    public void testExternalDisplay_notAppliedRefreshRatesSynchronization() {
        init();
        assertNoRefreshRateSynchronizationVote();
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertNoRefreshRateSynchronizationVote();
    }

    @Test
    public void testDefaultInternalDisplayTransferredToExternal_applyRefreshRatesSynchronization() {
        mContext.getOrCreateTestableResources()
                .addOverride(R.bool.config_refreshRateSynchronizationEnabled, true);
        init();
        mObserver.onDisplayAdded(DEFAULT_DISPLAY);
        assertNoRefreshRateSynchronizationVote();

        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertRefreshRateSynchronizationVote();

        // Simulate the case where the default display is transferred to an external display.
        doAnswer(
                        c -> {
                            DisplayInfo info = c.getArgument(1);
                            info.type = Display.TYPE_EXTERNAL;
                            info.displayId = DEFAULT_DISPLAY;
                            info.defaultModeId = 0;
                            info.supportedModes = mExternalDisplayModes;
                            info.userPreferredModeId = mExternalDisplayUserPreferredModeId;
                            return true;
                        })
                .when(mInjector)
                .getDisplayInfo(eq(DEFAULT_DISPLAY), /* displayInfo= */ any());

        // Trigger the case where the default display is transferred to an external display.
        mObserver.onDisplayChanged(DEFAULT_DISPLAY);
        mObserver.onDisplayRemoved(EXTERNAL_DISPLAY);
        assertRefreshRateSynchronizationVote();

        // Default display is transferred back to the internal display.
        doAnswer(
                        c -> {
                            DisplayInfo info = c.getArgument(1);
                            // Simulate the case where the default display is transferred to an
                            // external display.
                            info.type = Display.TYPE_INTERNAL;
                            info.displayId = DEFAULT_DISPLAY;
                            info.defaultModeId = 0;
                            info.supportedModes = mInternalDisplayModes;
                            info.userPreferredModeId = mInternalDisplayUserPreferredModeId;
                            return true;
                        })
                .when(mInjector)
                .getDisplayInfo(eq(DEFAULT_DISPLAY), /* displayInfo= */ any());

        // Trigger the case where the default display is transferred to an external display.
        mObserver.onDisplayChanged(DEFAULT_DISPLAY);
        mObserver.onDisplayAdded(EXTERNAL_DISPLAY);
        assertRefreshRateSynchronizationVote();

        // Remove external display and check that sync vote is no longer present.
        mObserver.onDisplayRemoved(EXTERNAL_DISPLAY);
        assertNoRefreshRateSynchronizationVote();
    }

    private void init() {
        mInjector = mock(DisplayModeDirector.Injector.class);
        doAnswer(
                        invocation -> {
                            assertThat(mObserver).isNull();
                            mObserver = invocation.getArgument(0);
                            return null;
                        })
                .when(mInjector)
                .registerDisplayListener(any(DisplayModeDirector.DisplayObserver.class), any());

        doAnswer(
                        c -> {
                            DisplayInfo info = c.getArgument(1);
                            info.type = Display.TYPE_INTERNAL;
                            info.displayId = DEFAULT_DISPLAY;
                            info.defaultModeId = 0;
                            info.supportedModes = mInternalDisplayModes;
                            info.userPreferredModeId = mInternalDisplayUserPreferredModeId;
                            return true;
                        })
                .when(mInjector)
                .getDisplayInfo(eq(DEFAULT_DISPLAY), /* displayInfo= */ any());

        doAnswer(
                        c -> {
                            DisplayInfo info = c.getArgument(1);
                            info.type = Display.TYPE_EXTERNAL;
                            info.displayId = EXTERNAL_DISPLAY;
                            info.defaultModeId = 0;
                            info.supportedModes = mExternalDisplayModes;
                            info.userPreferredModeId = mExternalDisplayUserPreferredModeId;
                            return true;
                        })
                .when(mInjector)
                .getDisplayInfo(eq(EXTERNAL_DISPLAY), /* displayInfo= */ any());

        doAnswer(c -> mock(SensorManagerInternal.class)).when(mInjector).getSensorManagerInternal();
        doAnswer(c -> mock(DeviceConfigInterface.class)).when(mInjector).getDeviceConfig();
        doAnswer(c -> mock(DisplayManagerInternal.class))
                .when(mInjector)
                .getDisplayManagerInternal();

        mDefaultDisplay = mock(Display.class);
        when(mDefaultDisplay.getDisplayId()).thenReturn(DEFAULT_DISPLAY);
        doAnswer(c -> mInjector.getDisplayInfo(DEFAULT_DISPLAY, c.getArgument(0)))
                .when(mDefaultDisplay)
                .getDisplayInfo(/* displayInfo= */ any());

        mExternalDisplay = mock(Display.class);
        when(mExternalDisplay.getDisplayId()).thenReturn(EXTERNAL_DISPLAY);
        doAnswer(c -> mInjector.getDisplayInfo(EXTERNAL_DISPLAY, c.getArgument(0)))
                .when(mExternalDisplay)
                .getDisplayInfo(/* displayInfo= */ any());

        when(mInjector.getDisplays()).thenReturn(new Display[] {mDefaultDisplay, mExternalDisplay});

        when(mInjector.getModeChangeObserver(any(), any()))
                .thenReturn(mock(ModeChangeObserver.class));

        mDmd =
                new DisplayModeDirector(
                        mContext,
                        mHandler,
                        mInjector,
                        mDisplayManagerFlags,
                        mDisplayDeviceConfigProvider,
                        mModeRequestManagerMock);
        mDmd.start(null);
        assertThat(mObserver).isNotNull();
    }

    @Nullable
    private Vote getVote(final int displayId, final int priority) {
        return mDmd.getVote(displayId, priority);
    }

    private void assertNoRefreshRateSynchronizationVote() {
        assertThat(getVote(GLOBAL_ID, PRIORITY_SYNCHRONIZED_REFRESH_RATE)).isEqualTo(null);
        assertThat(getVote(GLOBAL_ID, PRIORITY_SYNCHRONIZED_RENDER_FRAME_RATE)).isEqualTo(null);
    }

    private void assertRefreshRateSynchronizationVote() {
        assertThat(getVote(GLOBAL_ID, PRIORITY_SYNCHRONIZED_REFRESH_RATE))
                .isEqualTo(
                        Vote.forPhysicalRefreshRates(
                                MAX_REFRESH_RATE - SYNCHRONIZED_REFRESH_RATE_TOLERANCE,
                                MAX_REFRESH_RATE + SYNCHRONIZED_REFRESH_RATE_TOLERANCE));
        assertThat(getVote(GLOBAL_ID, PRIORITY_SYNCHRONIZED_RENDER_FRAME_RATE))
                .isEqualTo(
                        Vote.forRenderFrameRates(
                                MAX_REFRESH_RATE - SYNCHRONIZED_REFRESH_RATE_TOLERANCE,
                                MAX_REFRESH_RATE + SYNCHRONIZED_REFRESH_RATE_TOLERANCE));
    }
}
