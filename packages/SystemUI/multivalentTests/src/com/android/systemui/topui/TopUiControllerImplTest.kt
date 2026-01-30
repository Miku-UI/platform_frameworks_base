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

package com.android.systemui.topui

import android.app.activityManagerInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class TopUiControllerImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val iActivityManager = kosmos.activityManagerInterface

    private lateinit var underTest: TopUiControllerImpl

    @Before
    fun setUp() {
        underTest = TopUiControllerImpl(iActivityManager, kosmos.testDispatcher, kosmos.dumpManager)
    }

    @Test
    fun initialState_noActivityManagerCall() =
        kosmos.runTest {
            // No calls should have happened during initialization
            verifyNoMoreInteractions(activityManagerInterface)
        }

    @Test
    fun firstRequest_setsHasTopUiTrue() =
        kosmos.runTest {
            underTest.setRequestTopUi(true, "tag1")

            // Verify setHasTopUi(true) was called exactly once
            verify(activityManagerInterface, times(1)).setHasTopUi(true)
            verifyNoMoreInteractions(activityManagerInterface) // Ensure no other calls
        }

    @Test
    fun duplicateRequest_noExtraCall() =
        kosmos.runTest {
            // Initial request
            underTest.setRequestTopUi(true, "tag1")
            verify(activityManagerInterface, times(1)).setHasTopUi(true)

            // Duplicate request
            underTest.setRequestTopUi(true, "tag1")

            // No *new* calls should happen
            verifyNoMoreInteractions(activityManagerInterface)
        }

    @Test
    fun secondRequest_differentTag_noExtraCall() =
        kosmos.runTest {
            // Initial request
            underTest.setRequestTopUi(true, "tag1")
            verify(activityManagerInterface, times(1)).setHasTopUi(true)

            // Second request from different component
            underTest.setRequestTopUi(true, "tag2")

            // State is already true, no new calls expected
            verifyNoMoreInteractions(activityManagerInterface)
        }

    @Test
    fun releaseOneOfTwo_noCall() =
        kosmos.runTest {
            // Setup with two requesters
            underTest.setRequestTopUi(true, "tag1")
            verify(activityManagerInterface, times(1)).setHasTopUi(true)
            underTest.setRequestTopUi(true, "tag2")
            verifyNoMoreInteractions(activityManagerInterface) // No new call for tag2

            // Release first tag
            underTest.setRequestTopUi(false, "tag1")

            // State should remain true (tag2 still active), no new calls
            verifyNoMoreInteractions(activityManagerInterface)
        }

    @Test
    fun releaseLast_setsHasTopUiFalse() =
        kosmos.runTest {
            // Setup with two requesters
            underTest.setRequestTopUi(true, "tag1")
            verify(activityManagerInterface, times(1)).setHasTopUi(true)
            underTest.setRequestTopUi(true, "tag2")
            verifyNoMoreInteractions(activityManagerInterface)

            // Release first
            underTest.setRequestTopUi(false, "tag1")
            verifyNoMoreInteractions(activityManagerInterface) // Still true

            // Release second (last)
            underTest.setRequestTopUi(false, "tag2")

            // Should now call setHasTopUi(false) exactly once
            verify(activityManagerInterface, times(1)).setHasTopUi(false)
            verifyNoMoreInteractions(activityManagerInterface)
        }

    @Test
    fun duplicateRelease_noExtraCall() =
        kosmos.runTest {
            // Setup and release all
            underTest.setRequestTopUi(true, "tag1")
            verify(activityManagerInterface).setHasTopUi(true) // Use default times(1)
            underTest.setRequestTopUi(false, "tag1")
            verify(activityManagerInterface).setHasTopUi(false) // Use default times(1)

            // Duplicate release
            underTest.setRequestTopUi(false, "tag1")

            // No new calls expected
            verifyNoMoreInteractions(activityManagerInterface)
        }

    @Test
    fun releaseNonExistent_noCall() =
        kosmos.runTest {
            // Setup with one requester
            underTest.setRequestTopUi(true, "tag1")
            verify(activityManagerInterface).setHasTopUi(true)

            // Release a tag that never requested
            underTest.setRequestTopUi(false, "tagNonExistent")

            // No change expected
            verifyNoMoreInteractions(activityManagerInterface)

            // Release the actual tag should still work
            underTest.setRequestTopUi(false, "tag1")
            verify(activityManagerInterface).setHasTopUi(false)
            verifyNoMoreInteractions(activityManagerInterface)
        }

    @Test
    fun requestReleaseRequest_correctCalls() =
        kosmos.runTest {
            // Request
            underTest.setRequestTopUi(true, "tag1")
            verify(activityManagerInterface, times(1)).setHasTopUi(true)
            verifyNoMoreInteractions(activityManagerInterface)

            // Release
            underTest.setRequestTopUi(false, "tag1")
            verify(activityManagerInterface, times(1)).setHasTopUi(false)
            verifyNoMoreInteractions(activityManagerInterface)

            // Request again
            underTest.setRequestTopUi(true, "tag1")
            // Need to verify setHasTopUi(true) was called a *second* time overall
            verify(activityManagerInterface, times(2)).setHasTopUi(true)
            verifyNoMoreInteractions(activityManagerInterface)
        }
}
