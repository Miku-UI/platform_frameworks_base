/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.display;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Message;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.SurfaceControl;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.graphics.surfaceflinger.flags.Flags;
import com.android.server.display.mode.DisplayModeDirector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_MODESET_MULTI_DISPLAY)
public class BatchDisplayModeUpdateTest {

    @Mock private DisplayManagerService.SyncRoot mSyncRoot;
    @Mock private Handler mHandler;

    private LocalDisplayAdapter mLocalAdapter;
    private OverlayDisplayAdapter mOverlayAdapter;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mLocalAdapter = mock(LocalDisplayAdapter.class);
        when(mLocalAdapter.getHandler()).thenReturn(mHandler);
        when(mLocalAdapter.getSyncRoot()).thenReturn(mSyncRoot);

        mOverlayAdapter = mock(OverlayDisplayAdapter.class);
        when(mOverlayAdapter.getHandler()).thenReturn(mHandler);
        when(mOverlayAdapter.getSyncRoot()).thenReturn(mSyncRoot);
    }

    @Test
    public void testMixedAdapterBatch() {
        LocalDisplayAdapter.LocalDisplayDevice localDevice =
                mock(LocalDisplayAdapter.LocalDisplayDevice.class);
        when(localDevice.getAdapterLocked()).thenReturn(mLocalAdapter);

        OverlayDisplayAdapter.OverlayDisplayDevice overlayDevice =
                mock(OverlayDisplayAdapter.OverlayDisplayDevice.class);
        when(overlayDevice.getAdapterLocked()).thenReturn(mOverlayAdapter);

        DisplayModeDirector.DesiredDisplayModeSpecs localSpecs =
                new DisplayModeDirector.DesiredDisplayModeSpecs();
        localSpecs.baseModeId = 1;
        DisplayModeDirector.DesiredDisplayModeSpecs overlaySpecs =
                new DisplayModeDirector.DesiredDisplayModeSpecs();
        overlaySpecs.baseModeId = 2;

        SurfaceControl.DesiredDisplayModeSpecs localSfSpecs =
                new SurfaceControl.DesiredDisplayModeSpecs();
        when(localDevice.getDisplayModeSpecs(localSpecs)).thenReturn(localSfSpecs);

        Map<DisplayDevice, DisplayModeDirector.DesiredDisplayModeSpecs> specsMap = new HashMap<>();
        specsMap.put(localDevice, localSpecs);
        specsMap.put(overlayDevice, overlaySpecs);

        // We need to call the real method on the mock for applyBatchDisplayModeUpdatesLocked
        doCallRealMethod().when(mLocalAdapter).applyBatchDisplayModeUpdatesLocked(any());
        doCallRealMethod().when(mOverlayAdapter).applyBatchDisplayModeUpdatesLocked(any());

        // DMS calls this for each adapter
        mLocalAdapter.applyBatchDisplayModeUpdatesLocked(specsMap);
        mOverlayAdapter.applyBatchDisplayModeUpdatesLocked(specsMap);

        // LocalDisplayAdapter should process localDevice and send a message
        verify(localDevice).getDisplayModeSpecs(localSpecs);
        verify(mHandler, times(1)).sendMessage(any(Message.class));

        // OverlayDisplayAdapter should process overlayDevice
        verify(overlayDevice).applyDisplayModeUpdateLocked(overlaySpecs.baseModeId);
    }
}
