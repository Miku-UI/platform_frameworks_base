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

package com.android.systemui.qs.tiles.impl.modes.domain.interactor

import android.graphics.drawable.TestStubDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.settingslib.notification.modes.TestModeBuilder.MANUAL_DND
import com.android.settingslib.notification.modes.ZenMode
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.mainCoroutineContext
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.tiles.base.domain.actions.QSTileIntentUserInputHandlerSubject
import com.android.systemui.qs.tiles.base.domain.actions.qsTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.model.QSTileInputTestKtx
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesTileModel
import com.android.systemui.statusbar.policy.data.repository.zenModeRepository
import com.android.systemui.statusbar.policy.domain.interactor.zenModeInteractor
import com.android.systemui.statusbar.policy.ui.dialog.mockModesDialogDelegate
import com.android.systemui.statusbar.policy.ui.dialog.modesDialogEventLogger
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ModesTileUserActionInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val inputHandler = kosmos.qsTileIntentUserInputHandler
    private val mockDialogDelegate = kosmos.mockModesDialogDelegate
    private val zenModeRepository = kosmos.zenModeRepository
    private val zenModeInteractor = kosmos.zenModeInteractor
    private val tileDataInteractor = kosmos.modesTileDataInteractor

    private val underTest =
        ModesTileUserActionInteractor(
            kosmos.mainCoroutineContext,
            inputHandler,
            mockDialogDelegate,
            zenModeInteractor,
            tileDataInteractor,
            kosmos.modesDialogEventLogger,
        )

    @Test
    fun handleClick_active_showsDialog() = runTest {
        val expandable = mock<Expandable>()
        underTest.handleInput(
            QSTileInputTestKtx.click(data = modelOf(true, listOf("DND")), expandable = expandable)
        )

        verify(mockDialogDelegate).showDialog(eq(expandable))
    }

    @Test
    fun handleClick_inactive_showsDialog() = runTest {
        val expandable = mock<Expandable>()
        underTest.handleInput(
            QSTileInputTestKtx.click(data = modelOf(false, emptyList()), expandable = expandable)
        )

        verify(mockDialogDelegate).showDialog(eq(expandable))
    }

    @Test
    fun handleToggleClick_multipleModesActive_deactivatesAll() =
        testScope.runTest {
            val activeModes by collectLastValue(zenModeInteractor.activeModes)

            zenModeRepository.activateMode(MANUAL_DND)
            zenModeRepository.addModes(
                listOf(
                    TestModeBuilder().setName("Mode 1").setActive(true).build(),
                    TestModeBuilder().setName("Mode 2").setActive(true).build(),
                )
            )
            assertThat(activeModes?.count).isEqualTo(3)

            underTest.handleInput(
                QSTileInputTestKtx.toggleClick(
                    data = modelOf(true, listOf("DND", "Mode 1", "Mode 2"))
                )
            )

            assertThat(activeModes?.isAnyActive()).isFalse()
        }

    @Test
    fun handleToggleClick_dndActive_deactivatesDnd() =
        testScope.runTest {
            val dndMode by collectLastValue(zenModeInteractor.dndMode)

            zenModeRepository.activateMode(MANUAL_DND)
            assertThat(dndMode?.isActive).isTrue()

            underTest.handleInput(
                QSTileInputTestKtx.toggleClick(data = modelOf(true, listOf("DND")))
            )

            assertThat(dndMode?.isActive).isFalse()
        }

    @Test
    @DisableFlags(android.app.Flags.FLAG_MODES_UI_TILE_REACTIVATES_LAST)
    fun handleToggleClick_dndInactive_activatesDnd() =
        testScope.runTest {
            val dndMode by collectLastValue(zenModeInteractor.dndMode)

            assertThat(dndMode?.isActive).isFalse()

            underTest.handleInput(
                QSTileInputTestKtx.toggleClick(data = modelOf(false, emptyList()))
            )

            assertThat(dndMode?.isActive).isTrue()
        }

    @Test
    @EnableFlags(android.app.Flags.FLAG_MODES_UI_TILE_REACTIVATES_LAST)
    fun handleToggleClick_noModesActive_activatesQuickMode() =
        testScope.runTest {
            val dndMode by collectLastValue(zenModeInteractor.dndMode)
            zenModeRepository.addMode("mode", active = false)
            val model = modelOf(false, emptyList(), quickMode = zenModeRepository.getMode("mode")!!)

            underTest.handleInput(QSTileInputTestKtx.toggleClick(model))

            runCurrent()
            assertThat(zenModeRepository.getMode("mode")?.isActive).isTrue()
            assertThat(dndMode?.isActive).isFalse()
        }

    @Test
    fun handleLongClick_active_opensSettings() = runTest {
        underTest.handleInput(QSTileInputTestKtx.longClick(modelOf(true, listOf("DND"))))

        QSTileIntentUserInputHandlerSubject.assertThat(inputHandler).handledOneIntentInput {
            assertThat(it.intent.action).isEqualTo(Settings.ACTION_ZEN_MODE_SETTINGS)
        }
    }

    @Test
    fun handleLongClick_inactive_opensSettings() = runTest {
        underTest.handleInput(QSTileInputTestKtx.longClick(modelOf(false, emptyList())))

        QSTileIntentUserInputHandlerSubject.assertThat(inputHandler).handledOneIntentInput {
            assertThat(it.intent.action).isEqualTo(Settings.ACTION_ZEN_MODE_SETTINGS)
        }
    }

    private fun modelOf(
        isActivated: Boolean,
        activeModeIdsAndNames: List<String>,
        quickMode: ZenMode? = MANUAL_DND,
    ): ModesTileModel {
        return ModesTileModel(
            isActivated,
            activeModeIdsAndNames.map {
                // For testing purposes, we use the same value for id and name, but replicate
                // the flagged behavior of the DataInteractor.
                if (android.app.Flags.modesUiTileReactivatesLast())
                    ModesTileModel.ActiveMode(it, it)
                else ModesTileModel.ActiveMode(null, it)
            },
            TestStubDrawable("icon").asIcon(resId = 123),
            quickMode,
        )
    }
}
