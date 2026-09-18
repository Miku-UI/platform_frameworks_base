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

package com.android.systemui.statusbar.connectivity.ui.viewmodel

import android.os.Handler
import android.os.Looper
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.qs.TileDetailsViewModel
import com.android.systemui.qs.FakeTileDetailsViewModel
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.tiles.dialog.InternetDetailsViewModel
import com.android.systemui.qs.tiles.dialog.InternetDialogManager
import com.android.systemui.retail.domain.interactor.RetailModeInteractor
import com.android.systemui.scene.shared.model.TransitionKeys
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.connectivity.AccessPointController
import com.android.systemui.statusbar.connectivity.domain.interactor.FakeInternetConnectivityActionInteractor
import com.android.systemui.testKosmosNew
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock

@SmallTest
class InternetConnectivityActionViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val interactor = FakeInternetConnectivityActionInteractor()
    private val internetDialogManager: InternetDialogManager = mock()
    private val accessPointController: AccessPointController = mock()
    private val shadeModeInteractor: ShadeModeInteractor = mock()
    private val shadeInteractor: ShadeInteractor = mock()
    private val detailsViewModel: DetailsViewModel = mock()
    private val otherDetailsViewModel: TileDetailsViewModel = FakeTileDetailsViewModel("test")
    private val internetDetailsViewModel: InternetDetailsViewModel = mock()
    private val retailModeInteractor: RetailModeInteractor = mock()

    private lateinit var underTest: InternetConnectivityActionViewModel

    private val handler = Handler.createAsync(Looper.getMainLooper())

    @Before
    fun setUp() {
        whenever(detailsViewModel.activeTileDetails).thenReturn(null)
        whenever(retailModeInteractor.isInRetailMode).thenReturn(false)

        underTest =
            InternetConnectivityActionViewModel(
                kosmos.testScope.backgroundScope,
                handler,
                interactor,
                internetDialogManager,
                accessPointController,
                shadeInteractor,
                detailsViewModel,
                shadeModeInteractor,
                retailModeInteractor,
            )
    }

    @Test
    @DisableFlags(QsDetailedView.FLAG_NAME)
    fun detailedViewDisabled_showDialog() =
        kosmos.testScope.runTest {
            underTest.start()
            waitForIdleSync(handler)

            interactor.internetConnectivityActionEvent.emit(Unit)
            waitForIdleSync(handler)

            verify(internetDialogManager).create(eq(true), eq(false), eq(false), isNull())
        }

    @Test
    @EnableFlags(QsDetailedView.FLAG_NAME)
    fun dualShadeDisabled_showDialog() =
        kosmos.testScope.runTest {
            whenever(shadeModeInteractor.isDualShade).thenReturn(false)
            underTest.start()
            waitForIdleSync(handler)

            interactor.internetConnectivityActionEvent.emit(Unit)
            waitForIdleSync(handler)

            verify(internetDialogManager).create(eq(true), eq(false), eq(false), isNull())
        }

    @Test
    @EnableFlags(QsDetailedView.FLAG_NAME)
    fun noDetailsShowing_showInternet() =
        kosmos.testScope.runTest {
            whenever(shadeModeInteractor.isDualShade).thenReturn(true)
            underTest.start()
            waitForIdleSync(handler)

            interactor.internetConnectivityActionEvent.emit(Unit)
            waitForIdleSync(handler)

            verify(shadeInteractor)
                .expandQuickSettingsShade("ACTION_INTERNET_CONNECTIVITY", TransitionKeys.Instant)
            verify(detailsViewModel)
                .onTileClicked(com.android.systemui.qs.pipeline.shared.TileSpec.create("internet"))
        }

    @Test
    @EnableFlags(QsDetailedView.FLAG_NAME)
    fun otherDetailsShowing_switchToInternet() =
        kosmos.testScope.runTest {
            whenever(shadeModeInteractor.isDualShade).thenReturn(true)
            whenever(detailsViewModel.activeTileDetails).thenReturn(otherDetailsViewModel)
            underTest.start()
            waitForIdleSync(handler)

            interactor.internetConnectivityActionEvent.emit(Unit)
            waitForIdleSync(handler)

            verify(shadeInteractor, never())
                .collapseQuickSettingsShade("ACTION_INTERNET_CONNECTIVITY", null, true)
            verify(shadeInteractor, never())
                .expandQuickSettingsShade("ACTION_INTERNET_CONNECTIVITY", TransitionKeys.Instant)
            verify(detailsViewModel)
                .onTileClicked(com.android.systemui.qs.pipeline.shared.TileSpec.create("internet"))
        }

    @Test
    @EnableFlags(QsDetailedView.FLAG_NAME)
    fun internetDetailsShowing_doNothing() =
        kosmos.testScope.runTest {
            whenever(shadeModeInteractor.isDualShade).thenReturn(true)
            whenever(detailsViewModel.activeTileDetails).thenReturn(internetDetailsViewModel)

            underTest.start()
            waitForIdleSync(handler)

            interactor.internetConnectivityActionEvent.emit(Unit)
            waitForIdleSync(handler)

            verify(shadeInteractor, never())
                .collapseQuickSettingsShade("ACTION_INTERNET_CONNECTIVITY", null, true)
            verify(shadeInteractor, never())
                .expandQuickSettingsShade("ACTION_INTERNET_CONNECTIVITY", TransitionKeys.Instant)
        }

    @Test
    @EnableFlags(QsDetailedView.FLAG_NAME)
    fun inRetailMode_showDialog() =
        kosmos.testScope.runTest {
            whenever(shadeModeInteractor.isDualShade).thenReturn(true)
            whenever(retailModeInteractor.isInRetailMode).thenReturn(true)
            underTest.start()
            waitForIdleSync(handler)

            interactor.internetConnectivityActionEvent.emit(Unit)
            waitForIdleSync(handler)

            verify(internetDialogManager).create(eq(true), eq(false), eq(false), isNull())
        }
}
