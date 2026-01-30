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

package com.android.server.display;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.testing.TestableContext;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.wm.DesktopModeHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DisplayGroupAllocatorTest {

    @Rule
    public final TestableContext mTestableContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext());
    private DisplayGroupAllocator mDga;

    @Mock
    LogicalDisplay mLogicalDisplayMock;

    DisplayGroupAllocator.Injector mInjector;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private DisplayGroupAllocator.Injector createInjector(
            boolean simulateExtendedMode, boolean activated) {
        return new DisplayGroupAllocator.Injector() {
            @Override
            boolean canDisplayHostTasksLocked(LogicalDisplay display) {
                return true;
            }

            @Override
            boolean isDesktopModeSupportedOnInternalDisplay(Context context) {
                return simulateExtendedMode;
            }

        };
    }

    @Test
    public void testCalculateGroupId_ProjectedMode() {
        assumeTrue(DesktopModeHelper.canEnterDesktopMode(mTestableContext));

        mInjector = createInjector(/* simulateExtendedMode= */ false, /* activated= */ true);
        mDga = new DisplayGroupAllocator(mTestableContext, mInjector);
        mDga.initLater(mTestableContext);

        doReturn(false).when(mLogicalDisplayMock).canHostTasksLocked();
        assertEquals("secondary_mode",
                mDga.decideRequiredGroupTypeLocked(mLogicalDisplayMock, Display.TYPE_EXTERNAL));

    }
    @Test
    public void testCalculateGroupId_ExtendedMode() {
        assumeTrue(DesktopModeHelper.canEnterDesktopMode(mTestableContext));

        mInjector = createInjector(/* simulateExtendedMode= */ true, /* activated= */ true);
        mDga = new DisplayGroupAllocator(mTestableContext, mInjector);
        mDga.initLater(mTestableContext);

        doReturn(true).when(mLogicalDisplayMock).canHostTasksLocked();

        assertEquals("",
                mDga.decideRequiredGroupTypeLocked(mLogicalDisplayMock, Display.TYPE_INTERNAL));

    }

    @Test
    public void testCalculateGroupId_DefaultDisplay() {
        assumeTrue(DesktopModeHelper.canEnterDesktopMode(mTestableContext));

        mInjector = createInjector(/* simulateExtendedMode= */ true, /* activated= */ true);
        mDga = new DisplayGroupAllocator(mTestableContext, mInjector);
        mDga.initLater(mTestableContext);

        doReturn(true).when(mLogicalDisplayMock).canHostTasksLocked();
        assertEquals("",
                mDga.decideRequiredGroupTypeLocked(mLogicalDisplayMock, Display.TYPE_INTERNAL));

    }

    @Test
    public void testCalculateGroupId_NameChoiceObeyed() {
        assumeTrue(DesktopModeHelper.canEnterDesktopMode(mTestableContext));

        mInjector = createInjector(/* simulateExtendedMode= */ true, /* activated= */ true);
        mDga = new DisplayGroupAllocator(mTestableContext, mInjector);
        mDga.initLater(mTestableContext);

        doReturn("name_from_ddc").when(mLogicalDisplayMock).getLayoutGroupNameLocked();
        assertEquals("",
                mDga.decideRequiredGroupTypeLocked(mLogicalDisplayMock, Display.TYPE_INTERNAL));

    }

}
