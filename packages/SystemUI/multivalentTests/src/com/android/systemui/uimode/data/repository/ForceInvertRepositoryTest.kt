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

package com.android.systemui.uimode.data.repository

import android.app.UiModeManager
import android.app.UiModeManager.FORCE_INVERT_TYPE_DARK
import android.app.UiModeManager.ForceInvertStateChangeListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.launchIn
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@SmallTest
@RunWith(AndroidJUnit4::class)
class ForceInvertRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val capturedListeners = mutableListOf<ForceInvertStateChangeListener>()

    private val Kosmos.uiModeManager by
        Kosmos.Fixture<UiModeManager> {
            mock {
                on { addForceInvertStateChangeListener(any(), any()) } doAnswer
                    {
                        val listener = it.getArgument<ForceInvertStateChangeListener>(1)
                        capturedListeners.add(listener)
                        Unit
                    }

                on { removeForceInvertStateChangeListener(any()) } doAnswer
                    {
                        val listener = it.getArgument<ForceInvertStateChangeListener>(0)
                        capturedListeners.remove(listener)
                        Unit
                    }
            }
        }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            ForceInvertRepositoryImpl(uiModeManager = uiModeManager, bgDispatcher = testDispatcher)
        }

    @Test
    fun isForceInvertDark_typeDark_returnsTrue() =
        kosmos.runTest {
            setActiveForceInvertType(FORCE_INVERT_TYPE_DARK)

            val isForceInvertDark by collectLastValue(underTest.isForceInvertDark)
            assertThat(isForceInvertDark).isTrue()
        }

    @Test
    fun isForceInvertDark_typeOff_returnsFalse() =
        kosmos.runTest {
            setActiveForceInvertType(UiModeManager.FORCE_INVERT_TYPE_OFF)

            val isForceInvertDark by collectLastValue(underTest.isForceInvertDark)
            assertThat(isForceInvertDark).isFalse()
        }

    @Test
    fun testUnsubscribeWhenCancelled() =
        kosmos.runTest {
            val job = underTest.isForceInvertDark.launchIn(backgroundScope)
            assertThat(capturedListeners).hasSize(1)

            job.cancel()
            assertThat(capturedListeners).isEmpty()
        }

    private fun Kosmos.setActiveForceInvertType(
        @UiModeManager.ForceInvertType forceInvertType: Int
    ) {
        uiModeManager.stub { on { forceInvertState } doReturn forceInvertType }
        capturedListeners.forEach { it.onForceInvertStateChanged(forceInvertType) }
    }
}
