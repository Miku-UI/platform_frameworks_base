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

package com.android.systemui.qs.panels.ui.viewmodel.toolbar

import android.content.pm.UserInfo
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_HSU_QS_CHANGES
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.qs.fake
import com.android.systemui.qs.footer.domain.interactor.FakeFooterActionInteractor
import com.android.systemui.qs.footer.domain.model.SecurityButtonConfig
import com.android.systemui.qs.footerActionsInteractor
import com.android.systemui.qs.panels.LargeScreenQSInlinePowerMenu
import com.android.systemui.res.R
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.model.SelectedUserModel
import com.android.systemui.user.data.model.SelectionStatus
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.fakeHeadlessSystemUserMode
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
class ToolbarViewModelTest : SysuiTestCase() {
    private val kosmos =
        testKosmosNew().apply { footerActionsInteractor = FakeFooterActionInteractor() }

    private val Kosmos.underTest by
        Kosmos.Fixture { toolbarViewModelFactory.create().apply { activateIn(testScope) } }

    @Test
    fun start_noSecurityInfo_collapsed() =
        with(kosmos) {
            runTest {
                assertThat(underTest.securityInfoViewModel).isNull()
                assertThat(underTest.securityInfoShowCollapsed).isTrue()
            }
        }

    @Test
    fun nullConfig_noSecurityInfo_collapsed() =
        with(kosmos) {
            runTest {
                setSecurityConfig(null)

                assertThat(underTest.securityInfoViewModel).isNull()
                assertThat(underTest.securityInfoShowCollapsed).isTrue()
            }
        }

    @Test
    fun config_notCollapsed() =
        with(kosmos) {
            runTest {
                setSecurityConfig(MANAGED_CONFIG)

                with(underTest.securityInfoViewModel!!) {
                    assertThat(model.icon).isEqualTo(MANAGED_CONFIG.icon)
                    assertThat(model.text).isEqualTo(MANAGED_CONFIG.text)
                    assertThat(onClick).isNotNull()
                }

                assertThat(underTest.securityInfoShowCollapsed).isFalse()
            }
        }

    @Test
    fun config_notCollapsed_beforeDelay() =
        with(kosmos) {
            runTest {
                setSecurityConfig(MANAGED_CONFIG)

                testScope.advanceTimeBy(COLLAPSED_DELAY - 100.milliseconds)

                assertThat(underTest.securityInfoShowCollapsed).isFalse()
            }
        }

    @Test
    fun config_collapsed_afterDelay() =
        with(kosmos) {
            runTest {
                setSecurityConfig(MANAGED_CONFIG)

                assertThat(underTest.securityInfoShowCollapsed).isFalse()

                testScope.advanceTimeBy(COLLAPSED_DELAY + 100.milliseconds)

                assertThat(underTest.securityInfoShowCollapsed).isTrue()
            }
        }

    @Test
    fun changeConfig_timerRestartedForCollapsed() =
        with(kosmos) {
            runTest {
                setSecurityConfig(MANAGED_CONFIG)

                testScope.advanceTimeBy(COLLAPSED_DELAY - 2.seconds)

                setSecurityConfig(INFO_CONFIG)

                with(underTest.securityInfoViewModel!!) {
                    assertThat(model.icon).isEqualTo(INFO_CONFIG.icon)
                    assertThat(model.text).isEqualTo(INFO_CONFIG.text)
                    assertThat(onClick).isNull()
                }

                assertThat(underTest.securityInfoShowCollapsed).isFalse()

                testScope.advanceTimeBy(COLLAPSED_DELAY - 100.milliseconds)

                assertThat(underTest.securityInfoShowCollapsed).isFalse()
            }
        }

    @Test
    fun changeConfigToNull_collapsedAgainImmediately() =
        with(kosmos) {
            runTest {
                setSecurityConfig(MANAGED_CONFIG)

                testScope.advanceTimeBy(COLLAPSED_DELAY - 2.seconds)

                setSecurityConfig(null)

                assertThat(underTest.securityInfoShowCollapsed).isTrue()
            }
        }

    @Test
    @DisableFlags(FLAG_HSU_QS_CHANGES)
    fun settingsButton_isNotNullWithFlagDisabled() =
        with(kosmos) {
            runTest {
                selectSystemUser()

                assertThat(underTest.settingsButtonViewModel).isNotNull()
            }
        }

    @Test
    @EnableFlags(FLAG_HSU_QS_CHANGES)
    fun settingsButton_hideForHeadlessSystemUser() =
        with(kosmos) {
            runTest {
                fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)

                selectSystemUser()

                assertThat(underTest.settingsButtonViewModel).isNull()
            }
        }

    @Test
    @EnableFlags(FLAG_HSU_QS_CHANGES)
    fun settingsButton_showWhenNotHeadlessSystemUser() =
        with(kosmos) {
            runTest {
                fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(false)

                selectSystemUser()

                assertThat(underTest.settingsButtonViewModel).isNotNull()
            }
        }

    @EnableFlags(LargeScreenQSInlinePowerMenu.FLAG_NAME)
    @Test
    fun useInlinePowerMenu_configEnabled_isTrue() =
        kosmos.runTest {
            setQsInlinePowerMenuEnabled(true)

            assertThat(underTest.useInlinePowerMenu).isTrue()
            assertThat(underTest.isInlinePowerMenuVisible).isFalse()
        }

    @EnableFlags(LargeScreenQSInlinePowerMenu.FLAG_NAME)
    @Test
    fun useInlinePowerMenu_configDisabled_isFalse() =
        with(kosmos) {
            runTest {
                setQsInlinePowerMenuEnabled(false)

                assertThat(underTest.useInlinePowerMenu).isFalse()
                assertThat(underTest.isInlinePowerMenuVisible).isFalse()
            }
        }

    @EnableFlags(LargeScreenQSInlinePowerMenu.FLAG_NAME)
    @Test
    fun powerMenuToggle_flagEnabled_togglesVisibility() =
        with(kosmos) {
            runTest {
                setQsInlinePowerMenuEnabled(true)

                // Initial state
                assertThat(underTest.isInlinePowerMenuVisible).isFalse()
                assertThat(underTest.powerMenuToggleButtonUiState.isSelected).isFalse()

                // Click 1: Show menu
                underTest.powerMenuToggleButtonUiState.onClick.invoke()
                assertThat(underTest.isInlinePowerMenuVisible).isTrue()
                assertThat(underTest.powerMenuToggleButtonUiState.isSelected).isTrue()

                // Click 2: Hide menu
                underTest.powerMenuToggleButtonUiState.onClick.invoke()
                assertThat(underTest.isInlinePowerMenuVisible).isFalse()
                assertThat(underTest.powerMenuToggleButtonUiState.isSelected).isFalse()
            }
        }

    @EnableFlags(LargeScreenQSInlinePowerMenu.FLAG_NAME)
    @Test
    fun powerMenuDismissed_flagEnabled_hidesMenu() =
        with(kosmos) {
            runTest {
                setQsInlinePowerMenuEnabled(true)

                // Show menu
                underTest.powerMenuToggleButtonUiState.onClick.invoke()
                assertThat(underTest.isInlinePowerMenuVisible).isTrue()
                assertThat(underTest.powerMenuToggleButtonUiState.isSelected).isTrue()

                // Dismiss menu
                underTest.onPowerMenuDismissed()
                assertThat(underTest.isInlinePowerMenuVisible).isFalse()
                assertThat(underTest.powerMenuToggleButtonUiState.isSelected).isFalse()
            }
        }

    @EnableFlags(LargeScreenQSInlinePowerMenu.FLAG_NAME)
    @Test
    fun powerMenuVisible_whenShadePartiallyCollapsed_hidesMenu() =
        with(kosmos) {
            runTest {
                setQsInlinePowerMenuEnabled(true)

                shadeTestUtil.setQsExpansion(1.0f)

                underTest.powerMenuToggleButtonUiState.onClick.invoke()
                assertThat(underTest.isInlinePowerMenuVisible).isTrue()

                shadeTestUtil.setQsExpansion(0.9f)
                assertThat(underTest.isInlinePowerMenuVisible).isFalse()
            }
        }

    private fun Kosmos.setSecurityConfig(config: SecurityButtonConfig?) {
        footerActionsInteractor.fake.setSecurityConfig(config)
        runCurrent()
    }

    private fun setQsInlinePowerMenuEnabled(isEnabled: Boolean) {
        overrideResource(R.bool.config_qsInlinePowerMenu, isEnabled)
    }

    private fun Kosmos.selectSystemUser() {
        kosmos.fakeUserRepository.selectedUser.value =
            SelectedUserModel(
                userInfo = UserInfo(UserHandle.USER_SYSTEM, "system_user", 0),
                selectionStatus = SelectionStatus.SELECTION_COMPLETE,
            )
        runCurrent()
    }

    private companion object {
        val MANAGED_CONFIG =
            SecurityButtonConfig(
                icon = Icon.Resource(R.drawable.vd_work, null),
                text = "Managed device",
                isClickable = true,
            )

        val INFO_CONFIG =
            SecurityButtonConfig(
                icon = Icon.Resource(R.drawable.ic_info, null),
                text = "General information",
                isClickable = false,
            )

        private val COLLAPSED_DELAY = 5.seconds
    }
}
