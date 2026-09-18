/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law of agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.desktopmode

import android.testing.AndroidTestingRunner
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.RemoteTransition
import androidx.test.filters.SmallTest
import com.android.wm.shell.desktopmode.DesktopTransitionUtils.getToFrontTransitionTypeOrNone
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@SmallTest
@RunWith(AndroidTestingRunner::class)
/**
 * Test class for [DesktopTransitionUtils] Build/Install/Run: atest
 * WMShellUnitTests:DesktopTransitionUtilsTest
 */
class DesktopTransitionUtilsTest {

    @Test
    fun getToFrontTransition_nullReturnsNone() {
        assertThat(getToFrontTransitionTypeOrNone(null)).isEqualTo(TRANSIT_NONE)
    }

    @Test
    fun getToFrontTransition_notNullReturnsTransitToFront() {
        val remoteTransition = mock(RemoteTransition::class.java)
        assertThat(getToFrontTransitionTypeOrNone(remoteTransition)).isEqualTo(TRANSIT_TO_FRONT)
    }
}
