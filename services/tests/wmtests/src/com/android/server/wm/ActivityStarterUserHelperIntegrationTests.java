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
package com.android.server.wm;

import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_USER;
import static android.app.ActivityManager.START_SUCCESS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.ActivityInfo.LAUNCH_MULTIPLE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.annotation.Nullable;

/**
 * Tests entry points of {@code ActivityStarter} that users {@code UserHelper}.
 */
public final class ActivityStarterUserHelperIntegrationTests extends ActivityStarterTestBase {

    @Test
    public void testExecute_userHelperDisabledByFlag() {
        ActivityStarter starter = createStarter(/* userHelper= */ null);

        assertWithMessage("result of execute()").that(starter.execute()).isEqualTo(START_SUCCESS);
    }

    @Test
    public void testExecute_checkRequestPassed() {
        ActivityStarter starter = createStarter(mMockUserHelper);
        mockCheckRequest(START_SUCCESS);

        assertWithMessage("result of execute()").that(starter.execute()).isEqualTo(START_SUCCESS);

        verifyUserHelperNotifiedActivityStarted();
    }

    @Test
    public void testExecute_checkRequestFailed() {
        ActivityStarter starter = createStarter(mMockUserHelper);
        mockCheckRequest(START_NOT_ALLOWED_FOR_USER);

        assertWithMessage("result of execute()")
                .that(starter.execute())
                .isEqualTo(START_NOT_ALLOWED_FOR_USER);

        verifyUserHelperNotNotifiedActivityStarted();
    }

    private ActivityStarter createStarter(@Nullable UserHelper userHelper) {
        ActivityStarter starter = prepareStarter(FLAG_ACTIVITY_NEW_TASK,
                /* mockGetRootTask= */ true, LAUNCH_MULTIPLE, userHelper);
        starter.mRequest.activityInfo.applicationInfo.packageName =
                ActivityBuilder.getDefaultComponent().getPackageName();
        return starter;
    }

    private void mockCheckRequest(int result) {
        doReturn(result).when(mMockUserHelper).checkRequest(any());
    }

    private void verifyUserHelperNotifiedActivityStarted() {
        ArgumentCaptor<ActivityRecord> captor = ArgumentCaptor.forClass(ActivityRecord.class);

        verify(mMockUserHelper).logActivityStarted(captor.capture(), eq(true), anyInt());

        ActivityRecord activityRecord = captor.getValue();
        assertWithMessage("ComponentName on (%s from logActivityStarted()", activityRecord)
                .that(activityRecord.mActivityComponent)
                .isEqualTo(ActivityBuilder.getDefaultComponent());
    }

    private void verifyUserHelperNotNotifiedActivityStarted() {
        verify(mMockUserHelper, never()).logActivityStarted(anyInt(), any(), anyInt());
    }
}
