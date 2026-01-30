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

package com.android.systemui.wallpapers

import android.app.Presentation
import android.hardware.display.DisplayManagerGlobal
import android.view.Display
import android.view.DisplayAdjustments
import android.view.DisplayInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.KEYGUARD
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.NONE
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.PROVISIONING
import com.android.systemui.wallpapers.domain.interactor.fakeDisplayWallpaperPresentationInteractor
import com.android.systemui.wallpapers.ui.presentation.KeyguardWallpaperPresentationFactory
import com.android.systemui.wallpapers.ui.presentation.ProvisioningWallpaperPresentationFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class WallpaperPresentationManagerTest : SysuiTestCase() {
    private val kosmos = Kosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val fakeWallpaperPresentationInteractor =
        kosmos.fakeDisplayWallpaperPresentationInteractor
    private val testDisplay: Display =
        Display(
            DisplayManagerGlobal.getInstance(),
            /* displayId= */ 2,
            DisplayInfo(),
            DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS,
        )

    private val provisioningPresentation: Presentation = mock()
    private val provisioningPresentationFactory: ProvisioningWallpaperPresentationFactory = mock {
        on { create(any()) }.doReturn(provisioningPresentation)
    }
    private val keyguardPresentation: Presentation = mock()
    private val keyguardPresentationFactory: KeyguardWallpaperPresentationFactory = mock {
        on { create(any()) }.doReturn(keyguardPresentation)
    }

    private val wallpaperPresentationManager =
        WallpaperPresentationManager(
            display = testDisplay,
            displayCoroutineScope = testScope.backgroundScope,
            presentationInteractor = kosmos.fakeDisplayWallpaperPresentationInteractor,
            presentationFactories =
                mapOf(
                    PROVISIONING to provisioningPresentationFactory,
                    KEYGUARD to keyguardPresentationFactory,
                ),
            appCoroutineScope = testScope,
            mainDispatcher = kosmos.testDispatcher,
        )

    @Test
    fun emitProvisioning_createProvisioningPresentation() =
        kosmos.runTest {
            wallpaperPresentationManager.start()

            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(PROVISIONING)

            verify(provisioningPresentationFactory).create(eq(testDisplay))
            verify(provisioningPresentation).show()
            verifyNoMoreInteractions(provisioningPresentation)
            verifyNoInteractions(keyguardPresentationFactory)
        }

    @Test
    fun emitProvisioningThenNone_hideProvisioningPresentation() =
        kosmos.runTest {
            wallpaperPresentationManager.start()

            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(PROVISIONING)
            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(NONE)

            verify(provisioningPresentation).dismiss()
            verifyNoInteractions(keyguardPresentationFactory)
        }

    @Test
    fun emitProvisioningThenKeyguard_hideProvisioningAndShowKeyguardPresentation() =
        kosmos.runTest {
            wallpaperPresentationManager.start()

            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(PROVISIONING)
            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(KEYGUARD)

            verify(provisioningPresentation).dismiss()
            verify(keyguardPresentationFactory).create(eq(testDisplay))
            verify(keyguardPresentation).show()
            verifyNoMoreInteractions(keyguardPresentation)
        }

    @Test
    fun emitKeyguard_showKeyguardPresentation() =
        kosmos.runTest {
            wallpaperPresentationManager.start()

            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(KEYGUARD)

            verify(keyguardPresentationFactory).create(eq(testDisplay))
            verify(keyguardPresentation).show()
            verifyNoMoreInteractions(keyguardPresentation)
            verifyNoInteractions(provisioningPresentationFactory)
        }

    @Test
    fun emitKeyguardThenNone_hideKeyguardPresentation() =
        kosmos.runTest {
            wallpaperPresentationManager.start()

            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(KEYGUARD)
            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(NONE)

            verify(keyguardPresentation).dismiss()
            verifyNoInteractions(provisioningPresentationFactory)
        }

    @Test
    fun emitKeyguardThenProvisioning_hideKeyguardAndShowProvisioningPresentation() =
        kosmos.runTest {
            wallpaperPresentationManager.start()

            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(KEYGUARD)
            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(PROVISIONING)

            verify(keyguardPresentation).dismiss()
            verify(provisioningPresentation).show()
            verifyNoMoreInteractions(provisioningPresentation)
        }

    @Test
    fun stop_hidePreviouslyShownProvisioningPresentation() =
        kosmos.runTest {
            wallpaperPresentationManager.start()
            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(PROVISIONING)

            wallpaperPresentationManager.stop()

            verify(provisioningPresentation).dismiss()
        }

    @Test
    fun stop_hidePreviouslyShownKeyguardPresentation() =
        kosmos.runTest {
            wallpaperPresentationManager.start()
            fakeWallpaperPresentationInteractor._presentationFactoryFlow.emit(KEYGUARD)

            wallpaperPresentationManager.stop()

            verify(keyguardPresentation).dismiss()
        }
}
