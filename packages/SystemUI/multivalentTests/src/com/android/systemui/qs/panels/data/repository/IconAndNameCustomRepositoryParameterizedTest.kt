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

package com.android.systemui.qs.panels.data.repository

import android.content.ComponentName
import android.content.packageManager
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.TestStubDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.service.quicksettings.Flags
import android.service.quicksettings.TileService
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.kosmos.mainCoroutineContext
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.panels.shared.model.EditTileData
import com.android.systemui.qs.pipeline.data.repository.FakeInstalledTilesComponentRepository
import com.android.systemui.qs.pipeline.data.repository.fakeInstalledTilesRepository
import com.android.systemui.qs.pipeline.data.repository.installedTilesRepository
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.settings.FakeUserTracker
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@SmallTest
internal class IconAndNameCustomRepositoryParameterizedTest(private val testCase: TestCase) :
    SysuiTestCase() {
    private val kosmos = testKosmos()

    private val packageManager: PackageManager = kosmos.packageManager
    private val userTracker: FakeUserTracker =
        kosmos.fakeUserTracker.apply {
            whenever(userContext.packageManager).thenReturn(packageManager)
        }

    private val underTest =
        with(kosmos) {
            IconAndNameCustomRepository(
                installedTilesRepository,
                userTracker,
                mainCoroutineContext,
                appIconRepositoryFactory,
            )
        }

    @Before
    fun setUp() {
        kosmos.fakeInstalledTilesRepository.setInstalledServicesForUser(
            userTracker.userId,
            listOf(testCase.toServiceInfo()),
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_QUICKSETTINGS_TILE_CATEGORIES)
    fun tileService_categoriesEnabled_returnsValidCategory() =
        with(kosmos) {
            testScope.runTest {
                val editTileDataList = underTest.getCustomTileData()
                val expectedData1 =
                    EditTileData(
                        TileSpec.create(component1),
                        Icon.Loaded(drawable1, ContentDescription.Loaded(tileService1)),
                        Text.Loaded(tileService1),
                        Text.Loaded(appName1),
                        null,
                        testCase.expected,
                    )

                assertThat(editTileDataList).containsExactly(expectedData1)
            }
        }

    @Test
    @DisableFlags(Flags.FLAG_QUICKSETTINGS_TILE_CATEGORIES)
    fun tileService_categoriesDisabled_returnsValidCategory() =
        with(kosmos) {
            testScope.runTest {
                val editTileDataList = underTest.getCustomTileData()
                val expectedData1 =
                    EditTileData(
                        TileSpec.create(component1),
                        Icon.Loaded(drawable1, ContentDescription.Loaded(tileService1)),
                        Text.Loaded(tileService1),
                        Text.Loaded(appName1),
                        null,
                        testCase.expectedDefault,
                    )

                assertThat(editTileDataList).containsExactly(expectedData1)
            }
        }

    internal data class TestCase(
        val category: String,
        val isSystemApp: Boolean,
        val expected: TileCategory,
        val expectedDefault: TileCategory =
            if (isSystemApp) TileCategory.PROVIDED_BY_SYSTEM_APP else TileCategory.PROVIDED_BY_APP,
    ) {
        fun toServiceInfo(): ServiceInfo {
            return FakeInstalledTilesComponentRepository.ServiceInfo(
                component1,
                tileService1,
                drawable1,
                appName1,
                null,
                isSystemApp,
                category,
            )
        }

        override fun toString(): String =
            "category=$category," +
                "isSystemApp=$isSystemApp," +
                "expected=${expected.name}," +
                "expectedDefault=${expectedDefault.name}"
    }

    companion object {
        val drawable1 = TestStubDrawable("drawable1")
        val appName1 = "App1"
        val tileService1 = "Tile Service 1"
        val component1 = ComponentName("pkg1", "srv1")

        @Parameters(name = "{0}")
        @JvmStatic
        fun data() =
            listOf(
                TestCase(TileService.CATEGORY_CONNECTIVITY, true, TileCategory.CONNECTIVITY),
                TestCase(TileService.CATEGORY_DISPLAY, false, TileCategory.DISPLAY),
                TestCase(TileService.CATEGORY_UTILITIES, true, TileCategory.UTILITIES),
                TestCase(TileService.CATEGORY_PRIVACY, false, TileCategory.PRIVACY),
                TestCase(TileService.CATEGORY_ACCESSIBILITY, true, TileCategory.ACCESSIBILITY),
                TestCase(
                    "android.service.quicksettings.CATEGORY_NON_VALID",
                    true,
                    TileCategory.PROVIDED_BY_SYSTEM_APP,
                ),
                TestCase(
                    "android.service.quicksettings.CATEGORY_NON_VALID",
                    false,
                    TileCategory.PROVIDED_BY_APP,
                ),
            )
    }
}
