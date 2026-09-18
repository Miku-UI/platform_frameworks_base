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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Test class for {@link SideChannelEngagementModeListener}.
 *
 * Build/Install/Run:
 *  atest WMJetpackUnitTests:SideChannelEngagementModeListenerTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SideChannelEngagementModeListenerTest {

    private static final int DISPLAY_ID_1 = Display.DEFAULT_DISPLAY;
    private static final int DISPLAY_ID_2 = Display.DEFAULT_DISPLAY + 1;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private EngagementModeClient mMockClient;
    @Mock
    private BiConsumer<Integer, Integer> mMockCallback;
    @Mock
    private Supplier<Set<Integer>> mMockActiveDisplayIdsSupplier;

    private SideChannelEngagementModeListener mListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context mockContext = mock(Context.class);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        mListener = new SideChannelEngagementModeListener(mockContext, mMockCallback,
                mMockActiveDisplayIdsSupplier) {
            @Override
            EngagementModeClient createClient(Context context) {
                return mMockClient;
            }
        };
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testCreateClient_flagEnabled_returnsRealClient() {
        Context mockContext = mock(Context.class);
        when(mockContext.isUiContext()).thenReturn(true);
        when(mockContext.getAssociatedDisplayId()).thenReturn(DISPLAY_ID_1);
        SideChannelEngagementModeListener listener = new SideChannelEngagementModeListener(
                mockContext, mMockCallback, mMockActiveDisplayIdsSupplier);

        EngagementModeClient client = listener.createClient(mockContext);

        assertThat(client).isInstanceOf(EngagementModeClientImpl.class);
    }

    @Test
    @DisableFlags(com.android.window.flags.Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    public void testCreateClient_flagDisabled_returnsNoOpClient() {
        Context mockContext = mock(Context.class);
        SideChannelEngagementModeListener listener = new SideChannelEngagementModeListener(
                mockContext, mMockCallback, mMockActiveDisplayIdsSupplier);

        EngagementModeClient client = listener.createClient(mockContext);

        assertThat(client).isInstanceOf(NoOpEngagementModeClient.class);
    }

    @Test
    public void testRegister_addsCallbackAndGetsInitialMode() {
        final int expectedMode = WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_VISUALS_ON;
        when(mMockClient.getEngagementModeFlags()).thenReturn(expectedMode);

        mListener.register(DISPLAY_ID_1);

        verify(mMockClient).addUpdateCallback(any(Executor.class), any(Consumer.class));
        verify(mMockCallback).accept(DISPLAY_ID_1, expectedMode);
    }

    @Test
    public void testUnregister_removesCallback() {
        mListener.register(DISPLAY_ID_1);
        verify(mMockClient).addUpdateCallback(any(Executor.class), any(Consumer.class));

        mListener.unregister();

        verify(mMockClient).removeUpdateCallback(any(Consumer.class));
    }

    @Test
    public void testCallbackInvocation_fansOutToAllActiveDisplays() {
        ArgumentCaptor<Consumer<Integer>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        Set<Integer> activeDisplays = new HashSet<>(Arrays.asList(DISPLAY_ID_1, DISPLAY_ID_2));
        when(mMockActiveDisplayIdsSupplier.get()).thenReturn(activeDisplays);

        mListener.register(DISPLAY_ID_1);

        verify(mMockClient).addUpdateCallback(any(Executor.class), callbackCaptor.capture());

        final int expectedMode = WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_AUDIO_ON;

        // Simulate the callback being invoked by the client.
        callbackCaptor.getValue().accept(expectedMode);

        verify(mMockCallback, times(1)).accept(DISPLAY_ID_1, expectedMode);
        verify(mMockCallback, times(1)).accept(DISPLAY_ID_2, expectedMode);
    }

    @Test
    public void testRegister_onlyRegistersOnce() {
        mListener.register(DISPLAY_ID_1);
        mListener.register(DISPLAY_ID_2);

        verify(mMockClient, times(1)).addUpdateCallback(any(Executor.class), any(Consumer.class));
    }

    @Test
    public void testUnregister_withoutRegister_doesNothing() {
        mListener.unregister();
        verify(mMockClient, never()).removeUpdateCallback(any(Consumer.class));
    }
}
