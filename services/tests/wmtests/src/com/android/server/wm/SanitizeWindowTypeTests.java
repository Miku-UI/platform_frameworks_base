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

import static android.view.WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW;
import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;

import static org.junit.Assert.assertEquals;

import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link WindowManagerService#sanitizeWindowType(Session, int, IBinder, int)}.
 *
 * Build/Install/Run:
 * atest WmTests:SanitizeWindowTypeTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class SanitizeWindowTypeTests extends WindowTestsBase {

    @Test
    public void testSanitizeWindowType_AccessibilityOverlay_ValidToken() {
        final WindowToken token = createTestWindowToken(
                TYPE_ACCESSIBILITY_OVERLAY, mDisplayContent);
        final Session session = createTestSession(mAtm);

        final int sanitizedType = mWm.sanitizeWindowType(session, mDisplayContent.getDisplayId(),
                token.token, TYPE_ACCESSIBILITY_OVERLAY);

        assertEquals(TYPE_ACCESSIBILITY_OVERLAY, sanitizedType);
    }

    @Test
    public void testSanitizeWindowType_AccessibilityOverlay_MismatchedToken() {
        final WindowToken token = createTestWindowToken(TYPE_APPLICATION_OVERLAY, mDisplayContent);
        final Session session = createTestSession(mAtm);

        final int sanitizedType = mWm.sanitizeWindowType(session, mDisplayContent.getDisplayId(),
                token.token, TYPE_ACCESSIBILITY_OVERLAY);

        assertEquals(0, sanitizedType);
    }

    @Test
    public void testSanitizeWindowType_AccessibilityOverlay_InvalidToken() {
        final Session session = createTestSession(mAtm);

        final int sanitizedType = mWm.sanitizeWindowType(session, mDisplayContent.getDisplayId(),
                new Binder(), TYPE_ACCESSIBILITY_OVERLAY);

        assertEquals(0, sanitizedType);
    }

    @Test
    public void testSanitizeWindowType_AccessibilityOverlay_NullToken() {
        final Session session = createTestSession(mAtm);
        setFieldValue(session, "mCanAddInternalSystemWindow", false);

        // When token is null, it checks mCanAddInternalSystemWindow for system windows.
        final int sanitizedType = mWm.sanitizeWindowType(session, mDisplayContent.getDisplayId(),
                null, TYPE_ACCESSIBILITY_OVERLAY);

        assertEquals(0, sanitizedType);
    }

    @Test
    public void testSanitizeWindowType_SystemWindow_NoPermission() {
        final Session session = createTestSession(mAtm);
        setFieldValue(session, "mCanAddInternalSystemWindow", false);

        final int sanitizedType = mWm.sanitizeWindowType(session, mDisplayContent.getDisplayId(),
                null, FIRST_SYSTEM_WINDOW);

        assertEquals(0, sanitizedType);
    }

    @Test
    public void testSanitizeWindowType_SystemWindow_WithPermission() {
        final Session session = createTestSession(mAtm);
        setFieldValue(session, "mCanAddInternalSystemWindow", true);

        final int sanitizedType = mWm.sanitizeWindowType(session, mDisplayContent.getDisplayId(),
                null, FIRST_SYSTEM_WINDOW);

        assertEquals(FIRST_SYSTEM_WINDOW, sanitizedType);
    }

    @Test
    public void testSanitizeWindowType_AppWindow_NoPermission() {
        final Session session = createTestSession(mAtm);
        setFieldValue(session, "mCanAddInternalSystemWindow", false);

        final int sanitizedType = mWm.sanitizeWindowType(session, mDisplayContent.getDisplayId(),
                null, TYPE_APPLICATION);

        assertEquals(0, sanitizedType);
    }

    @Test
    public void testSanitizeWindowType_SubWindow_NoPermission() {
        final Session session = createTestSession(mAtm);
        setFieldValue(session, "mCanAddInternalSystemWindow", false);

        assertEquals(0, mWm.sanitizeWindowType(session,
                mDisplayContent.getDisplayId(), null, TYPE_APPLICATION_PANEL));
        assertEquals(0, mWm.sanitizeWindowType(session,
                mDisplayContent.getDisplayId(), null, TYPE_APPLICATION_MEDIA));
        assertEquals(TYPE_APPLICATION_SUB_PANEL, mWm.sanitizeWindowType(session,
                mDisplayContent.getDisplayId(), null, TYPE_APPLICATION_SUB_PANEL));
    }
}
