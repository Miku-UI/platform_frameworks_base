/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles.impl.cell.domain.interactor

import android.content.DialogInterface
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.tiles.base.domain.actions.QSTileIntentUserInputHandlerSubject
import com.android.systemui.qs.tiles.base.domain.actions.qsTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.model.QSTileInputTestKtx
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileIcon
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileDataTileUserActionInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val mobileConnectionsRepository: FakeMobileConnectionsRepository =
        kosmos.mobileConnectionsRepository.fake
    private val intentHandler = kosmos.qsTileIntentUserInputHandler
    private val dialogTransitionAnimator: DialogTransitionAnimator = mock()

    private val dialog: SystemUIDialog = mock()
    private val dialogFactory: SystemUIDialog.Factory = mock {
        whenever(mock.create()).thenReturn(dialog)
    }

    private val underTest =
        MobileDataTileUserActionInteractor(
            context,
            mobileConnectionsRepository,
            intentHandler,
            dialogFactory,
            kosmos.testDispatcher,
            dialogTransitionAnimator,
        )

    @Before
    fun setup() {
        val subId = 1
        mobileConnectionsRepository.setActiveMobileDataSubscriptionId(subId)
        mobileConnectionsRepository.getRepoForSubId(subId).setDataEnabled(false)
    }

    @Test
    fun handleLongClick_opensSimSettings() =
        testScope.runTest {
            val testData = MobileDataTileModel(true, true, MobileDataTileIcon.SignalIcon(1))
            underTest.handleInput(QSTileInputTestKtx.longClick(testData))

            QSTileIntentUserInputHandlerSubject.assertThat(intentHandler).handledOneIntentInput {
                assertThat(it.intent.action)
                    .isEqualTo(Settings.ACTION_MANAGE_ALL_SIM_PROFILES_SETTINGS)
            }
        }

    @Test
    fun handleClick_whenDataIsEnabled_setsEnabledFalse() =
        testScope.runTest {
            mobileConnectionsRepository.activeMobileDataRepository.value?.setDataEnabled(true)

            val testData = MobileDataTileModel(true, true, MobileDataTileIcon.SignalIcon(1))
            underTest.handleInput(QSTileInputTestKtx.click(testData))
            runCurrent()

            assertThat(mobileConnectionsRepository.mobileIsDefault.value).isFalse()
        }

    @Test
    fun handleClick_whenDataIsDisabled_showsDialog() =
        testScope.runTest {
            mobileConnectionsRepository.activeMobileDataRepository.value?.setDataEnabled(false)

            val testData = MobileDataTileModel(true, false, MobileDataTileIcon.SignalIcon(1))
            underTest.handleInput(QSTileInputTestKtx.click(testData))

            verify(dialogFactory).create()
            verify(dialog).show()
        }

    @Test
    fun dialogPositiveButtonClick_enablesMobileData() =
        testScope.runTest {
            mobileConnectionsRepository.activeMobileDataRepository.value?.setDataEnabled(false)
            val captor = argumentCaptor<DialogInterface.OnClickListener>()
            val testData = MobileDataTileModel(true, true, MobileDataTileIcon.SignalIcon(1))
            underTest.handleInput(QSTileInputTestKtx.click(testData))

            verify(dialog).setPositiveButton(any(), captor.capture())
            captor.firstValue.onClick(mock(), 0)
            runCurrent()

            assertThat(
                    mobileConnectionsRepository.activeMobileDataRepository.value?.dataEnabled?.value
                )
                .isTrue()
        }
}
