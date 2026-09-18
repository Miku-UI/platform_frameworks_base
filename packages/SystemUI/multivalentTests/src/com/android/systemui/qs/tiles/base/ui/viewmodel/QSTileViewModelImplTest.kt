/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.qs.tiles.base.ui.viewmodel

import android.os.UserManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.classifier.FalsingManagerFake
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.FakeTileDetailsViewModel
import com.android.systemui.qs.tiles.base.domain.interactor.FakeDisabledByPolicyInteractor
import com.android.systemui.qs.tiles.base.domain.interactor.FakeQSTileDataInteractor
import com.android.systemui.qs.tiles.base.domain.interactor.FakeQSTileUserActionInteractor
import com.android.systemui.qs.tiles.base.shared.logging.QSTileLogger
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTilePolicy
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.ui.analytics.QSTileAnalytics
import com.android.systemui.qs.tiles.base.ui.model.QSTileDataToStateMapper
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.user.data.repository.UserIconProvider
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class QSTileViewModelImplTest : SysuiTestCase() {

    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var qsTileLogger: QSTileLogger
    @Mock private lateinit var qsTileAnalytics: QSTileAnalytics
    @Mock private lateinit var userManager: UserManager

    private val tileDataInteractor = FakeQSTileDataInteractor<Any>()
    private val tileUserActionInteractor = FakeQSTileUserActionInteractor<Any>()
    private val disabledByPolicyInteractor = FakeDisabledByPolicyInteractor()
    private val falsingManager = FalsingManagerFake()

    private val testCoroutineDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testCoroutineDispatcher)

    private lateinit var userRepository: FakeUserRepository
    private lateinit var underTest: QSTileViewModelImpl<Any>

    @Before
    fun setup() {
        userRepository =
            FakeUserRepository(UserIconProvider(context, userManager, testCoroutineDispatcher))
    }

    @Test
    fun dumpWritesState() =
        testScope.runTest {
            val setSupportedActions = false
            initUnderTest(setSupportedActions)
            tileDataInteractor.emitData("test_data")
            underTest.state.launchIn(backgroundScope)
            runCurrent()

            val sw = StringWriter()
            PrintWriter(sw).use { underTest.dump(it, emptyArray()) }

            assertThat(sw.buffer.toString())
                .isEqualTo(
                    "test_spec:\n" +
                        "    QSTileState(" +
                        "icon=Resource(resId=0, contentDescription=Resource(res=0)), " +
                        "label=test_data, " +
                        "activationState=INACTIVE, " +
                        "secondaryLabel=null, " +
                        "supportedActions=[CLICK], " +
                        "contentDescription=null, " +
                        "stateDescription=null, " +
                        "sideViewIcon=None, " +
                        "enabledState=ENABLED, " +
                        "expandedAccessibilityClassName=android.widget.Switch)\n"
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_HSU_QS_CHANGES)
    fun headlessSystemUserWithFlagEnabledDoesNotSupportLongPress() =
        testScope.runTest {
            val setSupportedActions = true
            initUnderTest(setSupportedActions)
            userRepository.setIsCurrentUserHeadlessSystemUser(true)

            tileDataInteractor.emitData("test_data")
            underTest.state.launchIn(backgroundScope)
            runCurrent()

            assertThat(underTest.state.value).isNotNull()
            assertThat(underTest.state.value!!.supportedActions)
                .containsExactly(QSTileState.UserAction.CLICK)

            userRepository.setIsCurrentUserHeadlessSystemUser(false)
            runCurrent()
            assertThat(underTest.state.value).isNotNull()
            assertThat(underTest.state.value!!.supportedActions)
                .containsExactly(QSTileState.UserAction.CLICK, QSTileState.UserAction.LONG_CLICK)
        }

    @Test
    @DisableFlags(Flags.FLAG_HSU_QS_CHANGES)
    fun headlessSystemUserWithFlagDisabledDoesNotChangeSupportedActions() =
        testScope.runTest {
            val setSupportedActions = true
            initUnderTest(setSupportedActions)
            userRepository.setIsCurrentUserHeadlessSystemUser(true)

            tileDataInteractor.emitData("test_data")
            underTest.state.launchIn(backgroundScope)
            runCurrent()

            assertThat(underTest.state.value).isNotNull()
            assertThat(underTest.state.value!!.supportedActions)
                .containsExactly(QSTileState.UserAction.CLICK, QSTileState.UserAction.LONG_CLICK)

            userRepository.setIsCurrentUserHeadlessSystemUser(false)
            runCurrent()
            assertThat(underTest.state.value).isNotNull()
            assertThat(underTest.state.value!!.supportedActions)
                .containsExactly(QSTileState.UserAction.CLICK, QSTileState.UserAction.LONG_CLICK)
        }

    private fun initUnderTest(setSupportedActions: Boolean) {
        underTest =
            QSTileViewModelImpl(
                QSTileConfigTestBuilder.build {
                    policy = QSTilePolicy.Restricted(listOf("test_restriction"))
                },
                { tileUserActionInteractor },
                { tileDataInteractor },
                {
                    object : QSTileDataToStateMapper<Any> {
                        override fun map(config: QSTileConfig, data: Any): QSTileState =
                            QSTileState.build(
                                Icon.Resource(0, ContentDescription.Resource(0)),
                                data.toString(),
                            ) {
                                if (setSupportedActions) {
                                    supportedActions =
                                        mutableSetOf(
                                            QSTileState.UserAction.CLICK,
                                            QSTileState.UserAction.LONG_CLICK,
                                        )
                                }
                            }
                    }
                },
                disabledByPolicyInteractor,
                userRepository,
                falsingManager,
                qsTileAnalytics,
                qsTileLogger,
                FakeSystemClock(),
                testCoroutineDispatcher,
                testCoroutineDispatcher,
                testScope.backgroundScope,
                FakeTileDetailsViewModel("QSTileViewModelImplTest"),
            )
    }
}
