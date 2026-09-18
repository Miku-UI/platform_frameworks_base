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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.ClipData;
import android.os.IBinder;
import android.view.SurfaceControl;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link Session} class.
 *
 * Build/Install/Run:
 * atest WmTests:SessionTest
 */
@RunWith(WindowTestRunner.class)
public final class SessionTest extends WindowTestsBase {

    @Test
    public void testPerformDrag_VisibleBackgroundUser_Blocked() {
        // 1. Create a visible background user.
        final int visibleBackgroundUserId = 11;
        doReturn(true).when(mWm.mUmInternal).isVisibleBackgroundFullUser(visibleBackgroundUserId);

        // 2. Create a secondary display and attach it to the visible background user.
        final DisplayContent dc = createNewDisplay();
        final int displayId = dc.getDisplayId();

        final WindowState win = newWindowBuilder("WindowOnSecondaryDisplay",
                TYPE_APPLICATION).setDisplay(dc).build();

        doReturn(visibleBackgroundUserId).when(mWm.mUmInternal).getUserAssignedToDisplay(displayId);

        // 4. Create the Session instance and call performDrag
        final Session session = createTestSession(mWm.mAtmService);
        final IBinder token = session.performDrag(
                win.mClient,
                /* flags= */ 0,
                mock(SurfaceControl.class),
                /* touchSource= */ 0,
                /* touchDeviceId= */ 0,
                /* touchPointerId= */ 0,
                /* touchX= */ 0,
                /* touchY= */ 0,
                /* thumbCenterX= */ 0,
                /* thumbCenterY= */ 0,
                mock(ClipData.class)
        );

        // Verify Drag-n-Drop is blocked for visible background user
        assertNull("PerformDrag must be blocked for visible background users on secondary displays",
                token);
    }
}
