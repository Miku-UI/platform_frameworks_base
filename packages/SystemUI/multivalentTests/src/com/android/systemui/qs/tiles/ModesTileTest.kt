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

package com.android.systemui.qs.tiles

import android.graphics.drawable.TestStubDrawable
import android.os.Handler
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.FlagsParameterization.allCombinationsOf
import android.service.quicksettings.Tile
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import androidx.test.filters.SmallTest
import com.android.internal.logging.MetricsLogger
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.systemui.Flags.FLAG_DO_NOT_USE_RUN_BLOCKING
import com.android.systemui.SysuiTestCase
import com.android.systemui.classifier.FalsingManagerFake
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.keyguard.data.repository.keyguardRepository
import com.android.systemui.kosmos.mainCoroutineContext
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tiles.base.domain.actions.FakeQSTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigProvider
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.impl.modes.domain.interactor.ModesTileDataInteractor
import com.android.systemui.qs.tiles.impl.modes.domain.interactor.ModesTileUserActionInteractor
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesTileModel
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesTileModel.ActiveMode
import com.android.systemui.qs.tiles.impl.modes.ui.mapper.ModesTileMapper
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.statusbar.policy.data.repository.zenModeRepository
import com.android.systemui.statusbar.policy.domain.interactor.zenModeInteractor
import com.android.systemui.statusbar.policy.ui.dialog.ModesDialogDelegate
import com.android.systemui.statusbar.policy.ui.dialog.modesDialogEventLogger
import com.android.systemui.statusbar.policy.ui.dialog.viewmodel.modesDialogViewModel
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.FakeSettings
import com.android.systemui.util.settings.SecureSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@EnableFlags(android.app.Flags.FLAG_MODES_UI_TILE_REACTIVATES_LAST)
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@RunWithLooper(setAsMainLooper = true)
class ModesTileTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val testDispatcher = kosmos.testDispatcher

    @Mock private lateinit var qsHost: QSHost

    @Mock private lateinit var metricsLogger: MetricsLogger

    @Mock private lateinit var statusBarStateController: StatusBarStateController

    @Mock private lateinit var activityStarter: ActivityStarter

    @Mock private lateinit var qsLogger: QSLogger

    @Mock private lateinit var uiEventLogger: QsEventLogger

    @Mock private lateinit var qsTileConfigProvider: QSTileConfigProvider

    @Mock private lateinit var dialogDelegate: ModesDialogDelegate

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return allCombinationsOf(FLAG_DO_NOT_USE_RUN_BLOCKING)
        }
    }

    private val inputHandler = FakeQSTileIntentUserInputHandler()
    private val zenModeRepository = kosmos.zenModeRepository
    private val tileDataInteractor =
        ModesTileDataInteractor(
            context,
            kosmos.zenModeInteractor,
            kosmos.shadeInteractor,
            kosmos.keyguardRepository,
            testDispatcher,
            testScope,
        )
    private val mapper = ModesTileMapper(context.resources, context.theme)

    private lateinit var userActionInteractor: ModesTileUserActionInteractor
    private lateinit var secureSettings: SecureSettings
    private lateinit var testableLooper: TestableLooper
    private lateinit var underTest: ModesTile

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testableLooper = TestableLooper.get(this)
        secureSettings = FakeSettings()

        // Allow the tile to load resources
        whenever(qsHost.context).thenReturn(context)
        whenever(qsHost.userContext).thenReturn(context)

        whenever(qsTileConfigProvider.getConfig(any()))
            .thenReturn(
                QSTileConfigTestBuilder.build {
                    uiConfig =
                        QSTileUIConfig.Resource(
                            iconRes = ModesTile.ICON_RES_ID,
                            labelRes = R.string.quick_settings_modes_label,
                        )
                }
            )

        userActionInteractor =
            ModesTileUserActionInteractor(
                kosmos.mainCoroutineContext,
                inputHandler,
                dialogDelegate,
                kosmos.zenModeInteractor,
                tileDataInteractor,
                kosmos.modesDialogEventLogger,
            )

        underTest =
            ModesTile(
                qsHost,
                uiEventLogger,
                testableLooper.looper,
                Handler(testableLooper.looper),
                FalsingManagerFake(),
                metricsLogger,
                statusBarStateController,
                activityStarter,
                qsLogger,
                qsTileConfigProvider,
                tileDataInteractor,
                mapper,
                userActionInteractor,
                kosmos.modesDialogViewModel,
            )

        underTest.initialize()
        underTest.setListening(Object(), true)

        testableLooper.processAllMessages()
    }

    @After
    fun tearDown() {
        underTest.destroy()
        testableLooper.processAllMessages()
    }

    @Test
    fun stateUpdatesOnChange() =
        testScope.runTest {
            assertThat(underTest.state.state).isEqualTo(Tile.STATE_INACTIVE)

            zenModeRepository.addMode(id = "Test", active = true)
            runCurrent()
            testableLooper.processAllMessages()

            assertThat(underTest.state.state).isEqualTo(Tile.STATE_ACTIVE)
        }

    @Test
    fun handleUpdateState_withTileModel_updatesState() =
        testScope.runTest {
            val tileState =
                QSTile.State().apply {
                    state = Tile.STATE_INACTIVE
                    secondaryLabel = "Old secondary label"
                }
            val model =
                ModesTileModel(
                    isActivated = true,
                    activeModes = listOf(ActiveMode("1", "One"), ActiveMode("2", "Two")),
                    icon = TestStubDrawable().asIcon(),
                    quickMode = TestModeBuilder.MANUAL_DND,
                )

            underTest.handleUpdateState(tileState, model)

            assertThat(tileState.state).isEqualTo(Tile.STATE_ACTIVE)
            assertThat(tileState.label).isEqualTo("2 Modes")
            assertThat(tileState.disabledByPolicy).isFalse()
        }

    @Test
    fun handleUpdateState_withNull_updatesState() =
        testScope.runTest {
            val tileState =
                QSTile.State().apply {
                    state = Tile.STATE_INACTIVE
                    secondaryLabel = "Old secondary label"
                }
            zenModeRepository.addMode("One", active = true)
            zenModeRepository.addMode("Two", active = true)
            runCurrent()

            underTest.handleUpdateState(tileState, null)

            assertThat(tileState.state).isEqualTo(Tile.STATE_ACTIVE)
            assertThat(tileState.label).isEqualTo("2 Modes")
        }

    @Test
    fun handleUpdateState_checksUserRestriction() =
        testScope.runTest {
            val tileState = QSTile.State().apply { state = Tile.STATE_INACTIVE }
            val model =
                ModesTileModel(
                    isActivated = false,
                    activeModes = listOf(),
                    icon = TestStubDrawable().asIcon(),
                    quickMode = TestModeBuilder.MANUAL_DND,
                )
            val userManager = mock<UserManager>()
            whenever(userManager.getUserRestrictionSources(any(), any()))
                .thenReturn(
                    listOf(
                        UserManager.EnforcingUser(
                            context.userId,
                            UserManager.RESTRICTION_SOURCE_DEVICE_OWNER,
                        )
                    )
                )
            context.addMockSystemService(UserManager::class.java, userManager)
            context.prepareCreatePackageContextAsUser(context.packageName, context.user, context)

            underTest.handleUpdateState(tileState, model)

            assertThat(tileState.disabledByPolicy).isTrue()
            verify(userManager)
                .getUserRestrictionSources(eq(UserManager.DISALLOW_ADJUST_VOLUME), any())
        }
}
