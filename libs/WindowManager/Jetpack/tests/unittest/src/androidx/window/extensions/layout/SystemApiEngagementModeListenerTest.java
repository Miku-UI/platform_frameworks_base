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

package androidx.window.extensions.layout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowManager.DisplayEngagementModeState;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Test class for {@link SystemApiEngagementModeListener}.
 *
 * Build/Install/Run:
 *  atest WMJetpackUnitTests:SystemApiEngagementModeListenerTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SystemApiEngagementModeListenerTest {

    private static final long TIMEOUT_MS = 5000;
    private static final int DISPLAY_ID = Display.DEFAULT_DISPLAY;

    @Mock
    private Context mMockContext;
    @Mock
    private WindowManager mMockWindowManager;
    @Mock
    private BiConsumer<Integer, Integer> mMockCallback;

    private SystemApiEngagementModeListener mListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(WindowManager.class)).thenReturn(mMockWindowManager);
        when(mMockContext.getMainExecutor()).thenReturn(Runnable::run);
        when(mMockContext.isUiContext()).thenReturn(true);
        when(mMockContext.getAssociatedDisplayId()).thenReturn(DISPLAY_ID);

        mListener = new SystemApiEngagementModeListener(mMockContext, mMockCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_nonUiContext_throwsException() {
        Context nonUiContext = mock(Context.class);
        DisplayManager mockDisplayManager = mock(DisplayManager.class);
        Display mockDisplay = mock(Display.class);

        when(nonUiContext.isUiContext()).thenReturn(false);
        when(nonUiContext.getDisplayId()).thenReturn(DISPLAY_ID);
        when(nonUiContext.getSystemService(WindowManager.class)).thenReturn(mMockWindowManager);
        when(nonUiContext.getSystemService(DisplayManager.class)).thenReturn(mockDisplayManager);
        when(mockDisplayManager.getDisplay(anyInt())).thenReturn(mockDisplay);
        when(nonUiContext.createWindowContext(any(Display.class), any(Integer.class),
                any())).thenReturn(nonUiContext);

        new SystemApiEngagementModeListener(nonUiContext, mMockCallback);
    }

    @Test
    public void testRegister_registersCallbackAndGetsInitialMode() {
        final int expectedMode = WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_VISUALS_ON;
        when(mMockWindowManager.getDisplayEngagementMode(DISPLAY_ID)).thenReturn(expectedMode);

        mListener.register(DISPLAY_ID);

        verify(mMockWindowManager, timeout(TIMEOUT_MS)).registerDisplayEngagementModeCallback(
                any(Executor.class), any(Consumer.class));
        verify(mMockWindowManager, timeout(TIMEOUT_MS)).getDisplayEngagementMode(DISPLAY_ID);
        verify(mMockCallback, timeout(TIMEOUT_MS)).accept(DISPLAY_ID, expectedMode);
    }

    @Test
    public void testUnregister_unregistersCallback() {
        mListener.register(DISPLAY_ID);
        verify(mMockWindowManager, timeout(TIMEOUT_MS)).registerDisplayEngagementModeCallback(
                any(Executor.class), any(Consumer.class));

        mListener.unregister();

        verify(mMockWindowManager, timeout(TIMEOUT_MS)).unregisterDisplayEngagementModeCallback(
                any(Consumer.class));
    }

    @Test
    public void testCallbackInvocation_forwardsToOnEngagementModeChanged() {
        ArgumentCaptor<Consumer<DisplayEngagementModeState>> callbackCaptor =
                ArgumentCaptor.forClass(Consumer.class);

        mListener.register(DISPLAY_ID);

        verify(mMockWindowManager, timeout(TIMEOUT_MS)).registerDisplayEngagementModeCallback(
                any(Executor.class), callbackCaptor.capture());

        final int expectedMode = WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_AUDIO_ON;
        DisplayEngagementModeState state = mock(DisplayEngagementModeState.class);
        when(state.getDisplayId()).thenReturn(DISPLAY_ID);
        when(state.getEngagementModeFlags()).thenReturn(expectedMode);

        // Simulate the callback being invoked by the system.
        callbackCaptor.getValue().accept(state);

        verify(mMockCallback, timeout(TIMEOUT_MS)).accept(DISPLAY_ID, expectedMode);
    }
}
