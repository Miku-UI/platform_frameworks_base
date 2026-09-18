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

package com.android.systemui.communal.data.db

import android.content.ComponentName
import android.os.UserHandle
import android.os.UserManager
import android.os.userManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_COMMUNAL_WIDGET_POPULATION_OPTIMIZATION
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.data.db.DefaultWidgetPopulation.SkipReason.RESTORED_FROM_BACKUP
import com.android.systemui.communal.data.model.CommunalWidgetId
import com.android.systemui.communal.data.model.FEATURE_ENABLED
import com.android.systemui.communal.data.model.SuppressionReason
import com.android.systemui.communal.data.repository.communalSettingsRepository
import com.android.systemui.communal.shared.model.SpanValue
import com.android.systemui.communal.widgets.CommunalWidgetHost
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.FakeUserRepository.Companion.MAIN_USER_ID
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.userLockedInteractor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DefaultWidgetPopulationTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val mainUser = UserHandle(MAIN_USER_ID)

    private val communalWidgetHost =
        mock<CommunalWidgetHost> {
            var nextId = 0
            on { allocateIdAndBindWidget(any(), anyOrNull()) }.thenAnswer { nextId++ }
        }
    private val communalWidgetDao = mock<CommunalWidgetDao>()
    private val database = mock<SupportSQLiteDatabase>()

    private val defaultWidgets =
        arrayOf(
            "com.android.test_package_1/fake_widget_1",
            "com.android.test_package_2/fake_widget_2",
            "com.android.test_package_3/fake_widget_3",
        )

    private lateinit var underTest: DefaultWidgetPopulation

    @Before
    fun setUp() {
        kosmos.fakeUserRepository.setUserUnlocked(MAIN_USER_ID, true)
        underTest =
            DefaultWidgetPopulation(
                bgScope = kosmos.applicationCoroutineScope,
                communalWidgetHost = communalWidgetHost,
                communalWidgetDaoProvider = { communalWidgetDao },
                defaultWidgets = defaultWidgets,
                logBuffer = logcatLogBuffer("DefaultWidgetPopulationTest"),
                userManager = kosmos.userManager,
                userLockedInteractor = kosmos.userLockedInteractor,
            )
    }

    @Test
    @DisableFlags(FLAG_COMMUNAL_WIDGET_POPULATION_OPTIMIZATION)
    fun testNoInteractionUntilMainUserUnlocked() =
        kosmos.runTest {
            fakeUserRepository.setUserUnlocked(MAIN_USER_ID, false)
            // Database created
            underTest.onCreate(database)
            verify(communalWidgetHost, never())
                .allocateIdAndBindWidget(provider = any(), user = any())
            fakeUserRepository.setUserUnlocked(MAIN_USER_ID, true)
            verify(communalWidgetHost, atLeastOnce())
                .allocateIdAndBindWidget(provider = any(), user = any())
        }

    @Test
    @EnableFlags(FLAG_COMMUNAL_WIDGET_POPULATION_OPTIMIZATION)
    fun testPopulateDefaultWidgetsWhenEnabled() =
        kosmos.runTest {
            communalSettingsRepository.setSuppressionReasons(emptyList())

            // Database created
            underTest.onCreate(database)

            // Verify default widgets are not bound
            verify(communalWidgetHost, never()).allocateIdAndBindWidget(any(), any())

            // Verify default widgets added in database
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = null,
                    componentName = defaultWidgets[0],
                    rank = 0,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = null,
                    componentName = defaultWidgets[1],
                    rank = 1,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = null,
                    componentName = defaultWidgets[2],
                    rank = 2,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
        }

    @Test
    @EnableFlags(FLAG_COMMUNAL_WIDGET_POPULATION_OPTIMIZATION)
    fun testPopulateDefaultWidgetsWhenDisabled() =
        kosmos.runTest {
            communalSettingsRepository.setSuppressionReasons(
                listOf(SuppressionReason.ReasonUnknown(FEATURE_ENABLED))
            )

            // Database created
            underTest.onCreate(database)

            // Verify default widgets are not bound
            verify(communalWidgetHost, never()).allocateIdAndBindWidget(any(), any())

            // Verify default widgets added in database with unbound widget ID
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = null,
                    componentName = defaultWidgets[0],
                    rank = 0,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = null,
                    componentName = defaultWidgets[1],
                    rank = 1,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = null,
                    componentName = defaultWidgets[2],
                    rank = 2,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
        }

    @Test
    @EnableFlags(FLAG_COMMUNAL_WIDGET_POPULATION_OPTIMIZATION)
    fun hydration_bindsWidgetsWhenEnabled() =
        kosmos.runTest {
            communalSettingsRepository.setSuppressionReasons(emptyList())

            // Mock widgets in the database
            val widgets =
                defaultWidgets
                    .mapIndexed { index, name ->
                        val rank = CommunalItemRank(uid = index.toLong(), rank = index)
                        val widget =
                            CommunalWidgetItem(
                                uid = index.toLong(),
                                widgetId = CommunalWidgetId.placeholder(index.toLong()),
                                componentName = name,
                                itemId = index.toLong(),
                                userSerialNumber = 0,
                                spanY = 1,
                                spanYNew = 1,
                            )
                        rank to widget
                    }
                    .toMap()

            whenever(communalWidgetDao.getWidgetsNow()).thenReturn(widgets)

            // Database created
            underTest.onCreate(database)

            // Verify default widgets are not bound
            verify(communalWidgetHost, never()).allocateIdAndBindWidget(any(), any())

            // Allocate widgets
            underTest.allocateWidgets()

            // Verify default widgets bound
            verify(communalWidgetHost)
                .allocateIdAndBindWidget(
                    provider = eq(ComponentName.unflattenFromString(defaultWidgets[0])!!),
                    user = eq(mainUser),
                )
            verify(communalWidgetHost)
                .allocateIdAndBindWidget(
                    provider = eq(ComponentName.unflattenFromString(defaultWidgets[1])!!),
                    user = eq(mainUser),
                )
            verify(communalWidgetHost)
                .allocateIdAndBindWidget(
                    provider = eq(ComponentName.unflattenFromString(defaultWidgets[2])!!),
                    user = eq(mainUser),
                )

            // Verify default widgets updated in database
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = CommunalWidgetId(0),
                    componentName = defaultWidgets[0],
                    rank = 0,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = CommunalWidgetId(1),
                    componentName = defaultWidgets[1],
                    rank = 1,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = CommunalWidgetId(2),
                    componentName = defaultWidgets[2],
                    rank = 2,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
        }

    @Test
    @EnableFlags(FLAG_COMMUNAL_WIDGET_POPULATION_OPTIMIZATION)
    fun testSkipDefaultWidgetsPopulation() =
        kosmos.runTest {
            // Skip default widgets population
            underTest.skipDefaultWidgetsPopulation(RESTORED_FROM_BACKUP)

            // Database created
            underTest.onCreate(database)

            // Verify no widget bounded or added to the database
            verify(communalWidgetHost, never()).allocateIdAndBindWidget(any(), any())
            verify(communalWidgetDao, never())
                .addWidget(
                    widgetId = any(),
                    componentName = any(),
                    rank = anyInt(),
                    userSerialNumber = anyInt(),
                    spanY = any(),
                )
        }

    @Test
    @DisableFlags(FLAG_COMMUNAL_WIDGET_POPULATION_OPTIMIZATION)
    fun testPopulateDefaultWidgetsWhenFlagDisabled() =
        kosmos.runTest {
            communalSettingsRepository.setSuppressionReasons(
                listOf(SuppressionReason.ReasonUnknown(FEATURE_ENABLED))
            )

            // Database created
            underTest.onCreate(database)

            // Verify default widgets bound
            verify(communalWidgetHost)
                .allocateIdAndBindWidget(
                    provider = eq(ComponentName.unflattenFromString(defaultWidgets[0])!!),
                    user = eq(mainUser),
                )
            verify(communalWidgetHost)
                .allocateIdAndBindWidget(
                    provider = eq(ComponentName.unflattenFromString(defaultWidgets[1])!!),
                    user = eq(mainUser),
                )
            verify(communalWidgetHost)
                .allocateIdAndBindWidget(
                    provider = eq(ComponentName.unflattenFromString(defaultWidgets[2])!!),
                    user = eq(mainUser),
                )

            // Verify default widgets added in database
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = CommunalWidgetId(0),
                    componentName = defaultWidgets[0],
                    rank = 0,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = CommunalWidgetId(1),
                    componentName = defaultWidgets[1],
                    rank = 1,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
            verify(communalWidgetDao)
                .addWidget(
                    widgetId = CommunalWidgetId(2),
                    componentName = defaultWidgets[2],
                    rank = 2,
                    userSerialNumber = 0,
                    spanY = SpanValue.Responsive(1),
                )
        }
}
