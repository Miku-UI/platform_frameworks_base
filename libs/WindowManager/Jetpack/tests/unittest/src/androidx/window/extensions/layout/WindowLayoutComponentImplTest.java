/*
 * Copyright (C) 2024 The Android Open Source Project
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

package androidx.window.extensions.layout;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.timeout;

import android.app.Activity;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowManager.DisplayEngagementModeState;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.window.common.DeviceStateManagerFoldingFeatureProducer;
import androidx.window.common.layout.CommonFoldingFeature;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Test class for {@link WindowLayoutComponentImpl}.
 *
 * Build/Install/Run:
 *  atest WMJetpackUnitTests:WindowLayoutComponentImplTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WindowLayoutComponentImplTest {

    private static final long TIMEOUT_MS = 10000;

    @Rule
    public final SetFlagsRule setFlagsRule = new SetFlagsRule();

    private final Context mAppContext = ApplicationProvider.getApplicationContext();

    @NonNull
    private WindowLayoutComponentImpl mWindowLayoutComponent;

    @Mock
    private DeviceStateManagerFoldingFeatureProducer mMockFoldingFeatureProducer;
    @Mock
    private WindowLayoutComponentImpl.DisplayStateProvider mMockDisplayStateProvider;
    @Mock
    private WindowManager mMockWindowManager;
    @Mock
    private Activity mMockActivity;

    @Mock
    private EngagementModeUpdateListener mMockEngagementModeListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockActivity.isUiContext()).thenReturn(true);
        when(mMockActivity.getAssociatedDisplayId()).thenReturn(Display.DEFAULT_DISPLAY);
        when(mMockActivity.getResources()).thenReturn(mAppContext.getResources());
        when(mMockActivity.getApplicationContext()).thenReturn(mAppContext);
    }

    @Test
    public void testOnDisplayFeaturesChanged_withListener_doesNotCrash() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        final Context testUiContext = new TestUiContext(mAppContext);

        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, info -> {});

        mWindowLayoutComponent.onDisplayFeaturesChanged(Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddWindowLayoutListener_nonUiContext_throwsError() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        mWindowLayoutComponent.addWindowLayoutInfoListener(mAppContext, info -> {});
    }

    @Test
    public void testAddWindowLayoutListener_activityConsumer_receivesUpdates() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);

        WindowLayoutInfo[] receivedInfo = new WindowLayoutInfo[1];
        java.util.function.Consumer<WindowLayoutInfo> consumer = (info) -> receivedInfo[0] = info;

        mWindowLayoutComponent.addWindowLayoutInfoListener(mMockActivity, consumer);

        // Trigger an update
        mWindowLayoutComponent.onDisplayFeaturesChanged(Collections.emptyList());

        assertThat(receivedInfo[0]).isNotNull();
        assertThat(receivedInfo[0].getDisplayFeatures()).isEmpty();

        // Now remove the listener and check it doesn't get updates.
        receivedInfo[0] = null;
        mWindowLayoutComponent.removeWindowLayoutInfoListener(consumer);
        mWindowLayoutComponent.onDisplayFeaturesChanged(Collections.emptyList());
        assertThat(receivedInfo[0]).isNull();
    }

    @Test
    public void testGetCurrentWindowLayoutInfo_noFoldingFeature_returnsEmptyList() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        final Context testUiContext = new TestUiContext(mAppContext);

        final WindowLayoutInfo layoutInfo =
                mWindowLayoutComponent.getCurrentWindowLayoutInfo(testUiContext);

        assertThat(layoutInfo.getDisplayFeatures()).isEmpty();
    }

    @Test
    public void testGetCurrentWindowLayoutInfo_hasFoldingFeature_returnsWindowLayoutInfo() {
        final Context testUiContext = new TestUiContext(mAppContext);
        final WindowConfiguration windowConfiguration =
                testUiContext.getResources().getConfiguration().windowConfiguration;
        final Rect featureRect = windowConfiguration.getBounds();
        // Mock DisplayStateProvider to control rotation and DisplayInfo, preventing dependency on
        // the real device orientation or display configuration. This improves test reliability on
        // devices like foldables or tablets that might have varying configurations.
        final WindowLayoutComponentImpl.DisplayStateProvider displayStateProvider =
                new WindowLayoutComponentImpl.DisplayStateProvider() {
                    @Override
                    public int getDisplayRotation(
                            @NonNull WindowConfiguration windowConfiguration) {
                        return Surface.ROTATION_0;
                    }

                    @NonNull
                    @Override
                    public DisplayInfo getDisplayInfo(int displayId) {
                        final DisplayInfo displayInfo = new DisplayInfo();
                        displayInfo.logicalWidth = featureRect.width();
                        displayInfo.logicalHeight = featureRect.height();
                        return displayInfo;
                    }
                };
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext,
                mMockFoldingFeatureProducer,
                displayStateProvider
        );
        final CommonFoldingFeature foldingFeature = new CommonFoldingFeature(
                CommonFoldingFeature.COMMON_TYPE_HINGE,
                CommonFoldingFeature.COMMON_STATE_FLAT,
                featureRect
        );
        mWindowLayoutComponent.onDisplayFeaturesChanged(List.of(foldingFeature));

        final WindowLayoutInfo layoutInfo =
                mWindowLayoutComponent.getCurrentWindowLayoutInfo(testUiContext);

        assertThat(layoutInfo.getDisplayFeatures()).containsExactly(new FoldingFeature(
                featureRect, FoldingFeature.TYPE_HINGE, FoldingFeature.STATE_FLAT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetCurrentWindowLayoutInfo_nonUiContext_throwsError() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider);
        mWindowLayoutComponent.getCurrentWindowLayoutInfo(mAppContext);
    }

    @Test
    @DisableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testGetCurrentWindowLayoutInfo_engagementModeDisabled_returnsDefaultMode() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext, mMockFoldingFeatureProducer, mMockDisplayStateProvider,
                mMockEngagementModeListener);
        final Context testUiContext = new TestUiContext(mAppContext);
        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, info -> {});
        final WindowLayoutInfo layoutInfo =
                mWindowLayoutComponent.getCurrentWindowLayoutInfo(testUiContext);

        assertThat(layoutInfo.getEngagementModeFlags()).isEqualTo(
                EngagementModeClient.DEFAULT_ENGAGEMENT_MODE);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testGetCurrentWindowLayoutInfo_systemApiEnabled_returnsDefaultMode() {
        final Context testUiContext = new TestUiContext(mAppContext, mMockWindowManager);
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                testUiContext,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider,
                mMockEngagementModeListener
        );
        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, info -> {});
        final WindowLayoutInfo layoutInfo =
                mWindowLayoutComponent.getCurrentWindowLayoutInfo(testUiContext);

        assertThat(layoutInfo.getEngagementModeFlags()).isEqualTo(
                EngagementModeClient.DEFAULT_ENGAGEMENT_MODE);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testAddWindowLayoutListener_systemApiAvailable_registersCallback() {
        final Context testUiContext = new TestUiContext(mAppContext, mMockWindowManager);
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                testUiContext,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider
        );
        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, info -> {});

        verify(mMockWindowManager, timeout(TIMEOUT_MS)).registerDisplayEngagementModeCallback(
                any(Executor.class), any(Consumer.class));
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testOnEngagementModeChanged_systemApiAvailable_updatesLayoutInfo() {
        final Context testUiContext = new TestUiContext(mAppContext, mMockWindowManager);
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                testUiContext,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider
        );
        final ArgumentCaptor<WindowLayoutInfo> layoutInfoCaptor =
                ArgumentCaptor.forClass(WindowLayoutInfo.class);
        final androidx.window.extensions.core.util.function.Consumer<WindowLayoutInfo> consumer =
                mock(androidx.window.extensions.core.util.function.Consumer.class);
        final ArgumentCaptor<Consumer<DisplayEngagementModeState>> callbackCaptor =
                ArgumentCaptor.forClass(Consumer.class);

        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, consumer);

        // The register call is asynchronous, so wait for it to complete.
        verify(mMockWindowManager, timeout(TIMEOUT_MS)).registerDisplayEngagementModeCallback(
                any(Executor.class), callbackCaptor.capture());

        final int expectedMode = WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_VISUALS_ON;
        final int displayId = testUiContext.getAssociatedDisplayId();
        final DisplayEngagementModeState state = mock(DisplayEngagementModeState.class);
        when(state.getDisplayId()).thenReturn(displayId);
        when(state.getEngagementModeFlags()).thenReturn(expectedMode);

        // Trigger the callback to simulate an engagement mode change from the system.
        callbackCaptor.getValue().accept(state);

        verify(consumer, timeout(TIMEOUT_MS).atLeastOnce()).accept(layoutInfoCaptor.capture());
        final WindowLayoutInfo lastLayoutInfo =
                layoutInfoCaptor.getValue();
        assertThat(lastLayoutInfo.getEngagementModeFlags()).isEqualTo(expectedMode);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testRemoveWindowLayoutListener_systemApiAvailable_unregistersCallback() {
        final Context testUiContext = new TestUiContext(mAppContext, mMockWindowManager);
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                testUiContext,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider
        );
        final androidx.window.extensions.core.util.function.Consumer<WindowLayoutInfo> consumer =
                mock(androidx.window.extensions.core.util.function.Consumer.class);
        final ArgumentCaptor<Consumer<DisplayEngagementModeState>> callbackCaptor =
                ArgumentCaptor.forClass(Consumer.class);

        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, consumer);

        // The register call is asynchronous, so wait for it to complete.
        verify(mMockWindowManager, timeout(TIMEOUT_MS)).registerDisplayEngagementModeCallback(
                any(Executor.class), callbackCaptor.capture());

        mWindowLayoutComponent.removeWindowLayoutInfoListener(consumer);

        verify(mMockWindowManager, timeout(TIMEOUT_MS)).unregisterDisplayEngagementModeCallback(
                callbackCaptor.getValue());
    }

    @Test
    @DisableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testAddWindowLayoutListener_sideChannelDisabled_registersCallback() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider,
                mMockEngagementModeListener
        );
        final Context testUiContext = new TestUiContext(mAppContext);
        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, info -> {});

        verify(mMockEngagementModeListener).register(testUiContext.getAssociatedDisplayId());
    }

    @Test
    @DisableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testRemoveWindowLayoutListener_sideChannelDisabled_unregistersCallback() {
        mWindowLayoutComponent = new WindowLayoutComponentImpl(
                mAppContext,
                mMockFoldingFeatureProducer,
                mMockDisplayStateProvider,
                mMockEngagementModeListener
        );
        final Context testUiContext = new TestUiContext(mAppContext);
        final androidx.window.extensions.core.util.function.Consumer<WindowLayoutInfo> consumer =
                mock(androidx.window.extensions.core.util.function.Consumer.class);
        mWindowLayoutComponent.addWindowLayoutInfoListener(testUiContext, consumer);
        mWindowLayoutComponent.removeWindowLayoutInfoListener(consumer);

        verify(mMockEngagementModeListener).unregister();
    }

    /**
     * A {@link Context} that simulates a UI context specifically for testing purposes.
     * This class overrides {@link Context#getAssociatedDisplayId()} to return
     * {@link Display#DEFAULT_DISPLAY}, ensuring the context is tied to the default display,
     * and {@link Context#isUiContext()} to always return {@code true}, simulating a UI context.
     */
    private static class TestUiContext extends ContextWrapper {

        private final WindowManager mWindowManager;
        private final int mDisplayId;

        TestUiContext(Context base) {
            this(base, null, Display.DEFAULT_DISPLAY);
        }

        TestUiContext(Context base, WindowManager windowManager) {
            this(base, windowManager, Display.DEFAULT_DISPLAY);
        }

        TestUiContext(Context base, WindowManager windowManager, int displayId) {
            super(base);
            mWindowManager = windowManager;
            mDisplayId = displayId;
        }

        @Override
        public int getAssociatedDisplayId() {
            return mDisplayId;
        }

        @Override
        public boolean isUiContext() {
            return true;
        }

        @Override
        public Object getSystemService(String name) {
            if (WINDOW_SERVICE.equals(name)) {
                return mWindowManager;
            }
            return super.getSystemService(name);
        }
    }
}
