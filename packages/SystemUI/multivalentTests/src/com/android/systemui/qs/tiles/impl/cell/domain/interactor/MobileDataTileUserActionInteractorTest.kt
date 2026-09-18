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
import android.telephony.SubscriptionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.tiles.base.domain.actions.QSTileIntentUserInputHandlerSubject
import com.android.systemui.qs.tiles.base.domain.actions.qsTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.model.QSTileInputTestKtx
import com.android.systemui.qs.tiles.dialog.InternetDialogManager
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.statusbar.connectivity.AccessPointController
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val internetDialogManager: InternetDialogManager = mock()
    private val accessPointController: AccessPointController = mock()

    private val underTest =
        MobileDataTileUserActionInteractor(
            context,
            mobileConnectionsRepository,
            intentHandler,
            dialogFactory,
            kosmos.testDispatcher,
            dialogTransitionAnimator,
            internetDialogManager,
            accessPointController,
        )

    @Before
    fun setup() {
        val subId = 1
        mobileConnectionsRepository.setActiveMobileDataSubscriptionId(subId)
        mobileConnectionsRepository.getRepoForSubId(subId).setDataEnabled(false)
    }

    @Test
    fun handleClick_showsDialog() =
        testScope.runTest {
            val testData =
                MobileDataTileModel(
                    isSimActive = true,
                    isEnabled = true,
                    isAirplaneModeEnabled = false,
                )
            underTest.handleInput(QSTileInputTestKtx.click(testData))

            verify(internetDialogManager)
                .create(anyBoolean(), anyBoolean(), anyBoolean(), anyOrNull())
        }

    @Test
    fun handleClick_airplaneModeEnabled_showsDialog() =
        testScope.runTest {
            val testData =
                MobileDataTileModel(
                    isSimActive = true,
                    isEnabled = true,
                    isAirplaneModeEnabled = true,
                )
            underTest.handleInput(QSTileInputTestKtx.click(testData))

            verify(internetDialogManager)
                .create(anyBoolean(), anyBoolean(), anyBoolean(), anyOrNull())
        }

    @Test
    fun handleLongClick_opensSimSettings() =
        testScope.runTest {
            val testData =
                MobileDataTileModel(
                    isSimActive = true,
                    isEnabled = true,
                    isAirplaneModeEnabled = false,
                )
            underTest.handleInput(QSTileInputTestKtx.longClick(testData))

            QSTileIntentUserInputHandlerSubject.assertThat(intentHandler).handledOneIntentInput {
                assertThat(it.intent.action)
                    .isEqualTo(Settings.ACTION_MANAGE_ALL_SIM_PROFILES_SETTINGS)
            }
        }

    @Test
    fun handleLongClick_airplaneModeEnabled_opensSimSettings() =
        testScope.runTest {
            val testData =
                MobileDataTileModel(
                    isSimActive = true,
                    isEnabled = true,
                    isAirplaneModeEnabled = true,
                )
            underTest.handleInput(QSTileInputTestKtx.longClick(testData))

            QSTileIntentUserInputHandlerSubject.assertThat(intentHandler).handledOneIntentInput {
                assertThat(it.intent.action)
                    .isEqualTo(Settings.ACTION_MANAGE_ALL_SIM_PROFILES_SETTINGS)
            }
        }

    @Test
    fun handleToggleClick_whenDataIsEnabled_setsEnabledFalse() =
        testScope.runTest {
            getDataRepo()?.setDataEnabled(true)
            runCurrent()

            val testData =
                MobileDataTileModel(
                    isSimActive = true,
                    isEnabled = true,
                    isAirplaneModeEnabled = false,
                )
            underTest.handleInput(QSTileInputTestKtx.toggleClick(testData))
            runCurrent()

            assertThat(getDataRepo()?.dataEnabled?.value).isFalse()
        }

    @Test
    fun handleToggleClick_whenDataIsEnabled_noActive_setsDefaultEnabledFalse() =
        testScope.runTest {
            mobileConnectionsRepository.setActiveMobileDataSubscriptionId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            )
            val defaultRepository =
                mobileConnectionsRepository.getRepoForSubId(
                    mobileConnectionsRepository.defaultDataSubId.value
                )
            defaultRepository.setDataEnabled(true)

            val testData =
                MobileDataTileModel(
                    isSimActive = true,
                    isEnabled = true,
                    isAirplaneModeEnabled = false,
                )
            underTest.handleInput(QSTileInputTestKtx.toggleClick(testData))
            runCurrent()

            assertThat(defaultRepository.dataEnabled.value).isFalse()
        }

    @Test
    fun handleToggleClick_whenDataIsDisabled_showsDialog() =
        testScope.runTest {
            getDataRepo()?.setDataEnabled(false)

            val testData =
                MobileDataTileModel(
                    isSimActive = true,
                    isEnabled = false,
                    isAirplaneModeEnabled = false,
                )
            underTest.handleInput(QSTileInputTestKtx.toggleClick(testData))

            verify(dialogFactory).create()
            verify(dialog).show()
        }

    @Test
    fun dialogPositiveButtonClick_enablesMobileData() =
        testScope.runTest {
            getDataRepo()?.setDataEnabled(false)
            val captor = argumentCaptor<DialogInterface.OnClickListener>()
            val testData =
                MobileDataTileModel(
                    isSimActive = true,
                    isEnabled = true,
                    isAirplaneModeEnabled = false,
                )
            underTest.handleInput(QSTileInputTestKtx.toggleClick(testData))

            verify(dialog).setPositiveButton(any(), captor.capture())
            captor.firstValue.onClick(mock(), 0)
            runCurrent()

            assertThat(getDataRepo()?.dataEnabled?.value).isTrue()
        }

    private fun getDataRepo(): MobileConnectionRepository? {
        return mobileConnectionsRepository.defaultDataSubId.value?.let {
            mobileConnectionsRepository.getRepoForSubId(it)
        }
    }
}
