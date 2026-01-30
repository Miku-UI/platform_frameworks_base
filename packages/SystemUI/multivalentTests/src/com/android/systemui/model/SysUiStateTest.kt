/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.systemui.model

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.runTest
import com.android.systemui.model.SysUiState.SysUiStateCallback
import com.android.systemui.testKosmos
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
open class SysUiStateTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val callback: SysUiStateCallback = mock()

    private lateinit var underTest: SysUiState

    @Before
    fun setup() {
        underTest = createInstance(Display.DEFAULT_DISPLAY)
    }

    @Test
    fun addSingle_setFlag() =
        kosmos.runTest {
            setFlags(FLAG_1)

            verify(callback, times(1)).onSystemUiStateChanged(FLAG_1, Display.DEFAULT_DISPLAY)
        }

    @Test
    fun addMultiple_setFlag() =
        kosmos.runTest {
            setFlags(FLAG_1)
            setFlags(FLAG_2)

            verify(callback, times(1)).onSystemUiStateChanged(FLAG_1, Display.DEFAULT_DISPLAY)
            verify(callback, times(1))
                .onSystemUiStateChanged((FLAG_1 or FLAG_2), Display.DEFAULT_DISPLAY)
        }

    @Test
    fun addMultipleRemoveOne_setFlag() =
        kosmos.runTest {
            setFlags(FLAG_1)
            setFlags(FLAG_2)
            underTest.setFlag(FLAG_1, false).commitUpdate(DISPLAY_ID)

            verify(callback, times(1)).onSystemUiStateChanged(FLAG_1, Display.DEFAULT_DISPLAY)
            verify(callback, times(1))
                .onSystemUiStateChanged((FLAG_1 or FLAG_2), Display.DEFAULT_DISPLAY)
            verify(callback, times(1)).onSystemUiStateChanged(FLAG_2, Display.DEFAULT_DISPLAY)
        }

    @Test
    fun addMultiple_setFlags() =
        kosmos.runTest {
            setFlags(FLAG_1, FLAG_2, FLAG_3, FLAG_4)

            val expected = FLAG_1 or FLAG_2 or FLAG_3 or FLAG_4
            verify(callback, times(1)).onSystemUiStateChanged(expected, Display.DEFAULT_DISPLAY)
        }

    @Test
    fun addMultipleRemoveOne_setFlags() =
        kosmos.runTest {
            setFlags(FLAG_1, FLAG_2, FLAG_3, FLAG_4)
            underTest.setFlag(FLAG_2, false).commitUpdate(DISPLAY_ID)

            val expected1 = FLAG_1 or FLAG_2 or FLAG_3 or FLAG_4
            verify(callback, times(1)).onSystemUiStateChanged(expected1, Display.DEFAULT_DISPLAY)
            val expected2 = FLAG_1 or FLAG_3 or FLAG_4
            verify(callback, times(1)).onSystemUiStateChanged(expected2, Display.DEFAULT_DISPLAY)
        }

    @Test
    fun removeCallback() =
        kosmos.runTest {
            underTest.removeCallback(callback)
            setFlags(FLAG_1, FLAG_2, FLAG_3, FLAG_4)

            val expected = FLAG_1 or FLAG_2 or FLAG_3 or FLAG_4
            verify(callback, times(0)).onSystemUiStateChanged(expected, Display.DEFAULT_DISPLAY)
        }

    @Test
    fun setFlag_receivedForDefaultDisplay() =
        kosmos.runTest {
            setFlags(FLAG_1)

            verify(callback, times(1)).onSystemUiStateChanged(FLAG_1, Display.DEFAULT_DISPLAY)
        }

    @Test
    fun init_registersWithDumpManager() =
        kosmos.runTest {
            underTest.start()

            verify(dumpManager).registerNormalDumpable(any(), eq(underTest))
        }

    @Test
    fun destroy_unregistersWithDumpManager() =
        kosmos.runTest {
            underTest.destroy()

            verify(dumpManager).unregisterDumpable(anyString())
        }

    private fun createInstance(displayId: Int): SysUiState {
        return SysUiStateImpl(
                displayId,
                kosmos.fakeSceneContainerPlugin,
                kosmos.dumpManager,
                kosmos.sysUIStateDispatcher,
            )
            .apply { addCallback(callback) }
    }

    private fun setFlags(vararg flags: Long) {
        setFlags(underTest, *flags)
    }

    private fun setFlags(instance: SysUiState, vararg flags: Long) {
        for (flag in flags) {
            instance.setFlag(flag, true)
        }
        instance.commitUpdate()
    }

    companion object {
        private const val FLAG_1 = 1L
        private const val FLAG_2 = 1L shl 1
        private const val FLAG_3 = 1L shl 2
        private const val FLAG_4 = 1L shl 3
        private const val DISPLAY_ID = Display.DEFAULT_DISPLAY
    }
}
