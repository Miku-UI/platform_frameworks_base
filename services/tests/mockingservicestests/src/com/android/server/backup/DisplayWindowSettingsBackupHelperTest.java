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

package com.android.server.backup;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.os.UserHandle;

import androidx.test.filters.SmallTest;

import com.android.server.LocalServices;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;

@SmallTest
public class DisplayWindowSettingsBackupHelperTest {

    private static final String KEY_DISPLAY = "display_window";
    private static final String KEY_INVALID = "invalid_key";
    private static final int USER_ID = UserHandle.USER_SYSTEM;
    private static final byte[] FAKE_PAYLOAD = "test_payload".getBytes(StandardCharsets.UTF_8);
    private MockitoSession mMockingSession;

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Mock
    private WindowManagerInternal mWindowManagerInternal;

    private com.android.server.backup.DisplayWindowSettingsBackupHelper mBackupHelper;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .mockStatic(LocalServices.class)
                .startMocking();

        doReturn(mWindowManagerInternal)
                .when(() -> LocalServices.getService(WindowManagerInternal.class));
        mBackupHelper = new DisplayWindowSettingsBackupHelper(USER_ID);
    }

    @After
    public void tearDown() {
        mMockingSession.finishMocking();
    }

    @Test
    public void getBackupPayload_invalidKey_returnsNull() {
        assertNull(mBackupHelper.getBackupPayload(KEY_INVALID));

        verify(mWindowManagerInternal, never()).backupDisplayWindowSettings(USER_ID);
    }

    @Test
    public void getBackupPayload_returnsPayloadFromWm() {
        when(mWindowManagerInternal.backupDisplayWindowSettings(USER_ID)).thenReturn(FAKE_PAYLOAD);

        byte[] payload = mBackupHelper.getBackupPayload(KEY_DISPLAY);

        assertThat(payload).isEqualTo(FAKE_PAYLOAD);
        verify(mWindowManagerInternal).backupDisplayWindowSettings(USER_ID);
    }

    @Test
    public void applyRestoredPayload_invalidKey_doesNothing() {
        mBackupHelper.applyRestoredPayload(KEY_INVALID, FAKE_PAYLOAD);

        verify(mWindowManagerInternal, never()).restoreDisplayWindowSettings(anyInt(), any());
    }


    @Test
    public void applyRestoredPayload_triggersWmRestore() {
        mBackupHelper.applyRestoredPayload(KEY_DISPLAY, FAKE_PAYLOAD);

        verify(mWindowManagerInternal).restoreDisplayWindowSettings(USER_ID, FAKE_PAYLOAD);
    }
}
