/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyboard.stickykeys.ui

import android.app.Dialog
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyboard.data.repository.FakeStickyKeysRepository
import com.android.systemui.keyboard.data.repository.keyboardRepository
import com.android.systemui.keyboard.stickykeys.StickyKeysLogger
import com.android.systemui.keyboard.stickykeys.shared.model.Locked
import com.android.systemui.keyboard.stickykeys.shared.model.ModifierKey.ALT
import com.android.systemui.keyboard.stickykeys.shared.model.ModifierKey.SHIFT
import com.android.systemui.keyboard.stickykeys.ui.viewmodel.StickyKeysIndicatorViewModel
import com.android.systemui.settings.displayTracker
import com.android.systemui.testKosmos
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class StickyKeysIndicatorCoordinatorTest : SysuiTestCase() {

    private lateinit var coordinator: StickyKeysIndicatorCoordinator
    private val testScope = TestScope(StandardTestDispatcher())
    private val stickyKeysRepository = FakeStickyKeysRepository()
    private val displayTracker = testKosmos().displayTracker
    private val dialog = mock<Dialog>()
    private val dialogExternalDisplay = mock<Dialog>()
    private val display = mock<Display> { on { displayId } doReturn (Display.DEFAULT_DISPLAY) }
    private val externalDisplay = mock<Display> { on { displayId } doReturn (1) }

    @Before
    fun setup() {
        val dialogFactory = mock<StickyKeyDialogFactory>()
        whenever(dialogFactory.create(eq(display), any())).thenReturn(dialog)
        whenever(dialogFactory.create(eq(externalDisplay), any())).thenReturn(dialogExternalDisplay)
        val keyboardRepository = testKosmos().keyboardRepository
        val viewModel =
            StickyKeysIndicatorViewModel(
                stickyKeysRepository,
                keyboardRepository,
                testScope.backgroundScope,
            )
        coordinator =
            StickyKeysIndicatorCoordinator(
                testScope.backgroundScope,
                dialogFactory,
                viewModel,
                mock<StickyKeysLogger>(),
                displayTracker,
            )
        coordinator.startListening()
        keyboardRepository.setIsAnyKeyboardConnected(true)
    }

    @Test
    fun dialogsAreShownWhenStickyKeysAreEmitted() {
        testScope.run {
            displayTracker.allDisplays = arrayOf(display, externalDisplay)

            verifyNoMoreInteractions(dialog)
            verifyNoMoreInteractions(dialogExternalDisplay)

            stickyKeysRepository.setStickyKeys(linkedMapOf(SHIFT to Locked(true)))
            runCurrent()

            verify(dialog).show()
            verify(dialogExternalDisplay).show()
        }
    }

    @Test
    fun dialogsDisappearWhenStickyKeysAreEmpty() {
        testScope.run {
            displayTracker.allDisplays = arrayOf(display, externalDisplay)

            verifyNoMoreInteractions(dialog)
            verifyNoMoreInteractions(dialogExternalDisplay)

            stickyKeysRepository.setStickyKeys(linkedMapOf(SHIFT to Locked(true)))
            runCurrent()
            stickyKeysRepository.setStickyKeys(linkedMapOf())
            runCurrent()

            verify(dialog).dismiss()
            verify(dialogExternalDisplay).dismiss()
        }
    }

    @Test
    fun dialogIsAddedToNewExternalDisplayWhenStickyKeysAreEmitted() {
        testScope.run {
            displayTracker.allDisplays = arrayOf(display)

            verifyNoMoreInteractions(dialog)
            verifyNoMoreInteractions(dialogExternalDisplay)

            stickyKeysRepository.setStickyKeys(linkedMapOf(SHIFT to Locked(true)))
            runCurrent()

            verify(dialog).show()
            verify(dialogExternalDisplay, never()).show()

            displayTracker.allDisplays = arrayOf(display, externalDisplay)

            stickyKeysRepository.setStickyKeys(linkedMapOf(ALT to Locked(true)))
            runCurrent()

            verify(dialogExternalDisplay).show()
        }
    }
}
