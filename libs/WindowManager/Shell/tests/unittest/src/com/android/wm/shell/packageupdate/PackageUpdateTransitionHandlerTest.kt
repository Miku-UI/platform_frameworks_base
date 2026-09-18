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

package com.android.wm.shell.packageupdate

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.suppliers.TransactionSupplier
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class PackageUpdateTransitionHandlerTest : ShellTestCase() {

    private val testExecutor = mock<ShellExecutor>()

    private val openingTaskLeash = mock<SurfaceControl>()
    private val closingTaskLeash = mock<SurfaceControl>()
    private val transactionProvider = mock<TransactionSupplier>()

    private lateinit var handler: PackageUpdateTransitionHandler

    @Before
    fun setUp() {
        handler =
            PackageUpdateTransitionHandler(
                transactionProvider,
                mContext,
                testExecutor,
                testExecutor,
                mock(),
                mock(),
            )
    }

    @Test
    fun handleRequest_returnsNull() {
        assertNull(handler.handleRequest(mock(), mock()))
    }

    @Test
    fun startAnimation_openAndCloseChange_returnsTrue() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info = createTransitionInfo(),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should animate", animates)
    }

    @Test
    fun startAnimation_noChanges_returnsTrue() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info = mock(),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should not crash", animates)
    }

    private fun createTransitionInfo(type: Int = TRANSIT_TO_BACK): TransitionInfo =
        TransitionInfo(type, /* flags= */ 0).apply {
            addChange(
                TransitionInfo.Change(mock(), closingTaskLeash).apply {
                    mode = TRANSIT_TO_BACK
                    parent = null
                    taskInfo = null
                }
            )
            addChange(
                TransitionInfo.Change(mock(), openingTaskLeash).apply {
                    mode = TRANSIT_OPEN
                    parent = null
                    taskInfo = null
                }
            )
        }
}
