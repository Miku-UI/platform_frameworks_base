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

package com.android.systemui.qs.tiles

import android.os.Handler
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.MetricsLogger
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.Expandable
import com.android.systemui.classifier.FalsingManagerFake
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigProvider
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.impl.cell.domain.interactor.MobileDataTileDataInteractor
import com.android.systemui.qs.tiles.impl.cell.domain.interactor.MobileDataTileUserActionInteractor
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileIcon
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.qs.tiles.impl.cell.ui.mapper.MobileDataTileMapper
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
class MobileDataTileTest : SysuiTestCase() {

    @Mock private lateinit var host: QSHost
    @Mock private lateinit var uiEventLogger: QsEventLogger
    @Mock private lateinit var metricsLogger: MetricsLogger
    @Mock private lateinit var statusBarStateController: StatusBarStateController
    @Mock private lateinit var activityStarter: ActivityStarter
    @Mock private lateinit var qsLogger: QSLogger
    @Mock private lateinit var qsTileConfigProvider: QSTileConfigProvider

    // Mocks for the new-arch components
    @Mock private lateinit var dataInteractor: MobileDataTileDataInteractor
    @Mock private lateinit var tileMapper: MobileDataTileMapper
    @Mock private lateinit var userActionInteractor: MobileDataTileUserActionInteractor

    private lateinit var testableLooper: TestableLooper
    private lateinit var underTest: MobileDataTile
    private val tileDataFlow =
        MutableStateFlow<MobileDataTileModel>(
            MobileDataTileModel(
                isSimActive = false,
                isEnabled = false,
                icon = MobileDataTileIcon.SignalIcon(4),
            )
        )

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testableLooper = TestableLooper.get(this)

        // Mock host context for resource loading and user
        whenever(host.context).thenReturn(context)
        whenever(host.userContext).thenReturn(context)
        whenever(host.userId).thenReturn(0)

        // Mock the data interactor to return our controllable flow
        whenever(dataInteractor.tileData()).thenReturn(tileDataFlow)

        // Provide a default config
        whenever(qsTileConfigProvider.getConfig(MobileDataTile.TILE_SPEC))
            .thenReturn(
                QSTileConfigTestBuilder.build {
                    uiConfig =
                        QSTileUIConfig.Resource(
                            iconRes = R.drawable.ic_signal_strength_zero_bar_no_internet,
                            labelRes = R.string.quick_settings_cellular_detail_title,
                        )
                }
            )

        underTest =
            MobileDataTile(
                host,
                uiEventLogger,
                testableLooper.looper,
                Handler(testableLooper.looper),
                FalsingManagerFake(),
                metricsLogger,
                statusBarStateController,
                activityStarter,
                qsLogger,
                qsTileConfigProvider,
                dataInteractor,
                tileMapper,
                userActionInteractor,
            )

        underTest.initialize()
        underTest.setListening(this, true) // Start listening to trigger data collection
        testableLooper.processAllMessages()
    }

    @After
    fun tearDown() {
        underTest.setListening(this, false)
        underTest.destroy()
        testableLooper.processAllMessages()
    }

    @Test
    fun tileDataChanges_triggersMapperWithNewModel() {
        val model =
            MobileDataTileModel(
                isSimActive = true,
                isEnabled = true,
                icon = MobileDataTileIcon.SignalIcon(4),
            )
        // Mock a placeholder state to be returned by the mapper
        whenever(tileMapper.map(any(), any())).thenReturn(mock(QSTileState::class.java))

        // Act: Emit a new model from the data source
        tileDataFlow.value = model
        testableLooper.processAllMessages()

        // Assert: Verify the tile passed the model to the mapper
        verify(tileMapper).map(any(), eq(model))
    }

    @Test
    fun click_callsUserActionInteractor() = runTest {
        // Act: Use the public click() method
        underTest.click(mock(Expandable::class.java))
        testableLooper.processAllMessages()

        // Assert
        verify(userActionInteractor).handleClick(any())
    }

    @Test
    fun isAvailable_fromDataInteractor_isTrue() {
        // Arrange
        whenever(dataInteractor.isAvailable()).thenReturn(true)

        // Act & Assert
        assertThat(underTest.isAvailable).isTrue()
    }

    @Test
    fun isAvailable_fromDataInteractor_isFalse() {
        // Arrange
        whenever(dataInteractor.isAvailable()).thenReturn(false)

        // Act & Assert
        assertThat(underTest.isAvailable).isFalse()
    }
}
