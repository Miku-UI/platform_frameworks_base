/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.rotation.RotationPolicyWrapper;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;
import com.android.systemui.util.wrapper.CameraRotationSettingProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class RotationLockControllerImplTest extends SysuiTestCase {

    @Mock
    RotationPolicyWrapper mRotationPolicyWrapper;
    @Mock
    CameraRotationSettingProvider mCameraRotationSettingProvider;

    private FakeExecutor mFakeExecutor = new FakeExecutor(new FakeSystemClock());

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass= */ this);
    }

    @Test
    public void setListening_registerRotationPolicyListener() {
        createRotationLockController();

        verify(mRotationPolicyWrapper).registerRotationPolicyListener(any(), anyInt());
    }

    private void createRotationLockController() {
        new RotationLockControllerImpl(
                mRotationPolicyWrapper,
                mCameraRotationSettingProvider,
                mFakeExecutor,
                mFakeExecutor
        );
        mFakeExecutor.runAllReady();
    }
}
