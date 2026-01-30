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

package com.android.systemui.keyboard.shortcut.data.repository

import android.content.pm.UserInfo
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyboard.shortcut.appsShortcutCategoryRepository
import com.android.systemui.keyboard.shortcut.fakeLauncherApps
import com.android.systemui.keyboard.shortcut.shared.model.Shortcut
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategory
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutIcon
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutSubCategory
import com.android.systemui.keyboard.shortcut.shortcutHelperTestHelper
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AppsShortcutCategoryRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val repo = kosmos.appsShortcutCategoryRepository
    private val fakeLauncherApps = kosmos.fakeLauncherApps
    private val userTracker = kosmos.fakeUserTracker
    private val testScope = kosmos.testScope
    private val helper = kosmos.shortcutHelperTestHelper

    @Before
    fun setup() {
        userTracker.set(
            userInfos =
                listOf(
                    UserInfo(/* id= */ PRIMARY_USER_ID, /* name= */ "Primary User", /* flags= */ 0)
                ),
            selectedUserIndex = PRIMARY_USER_INDEX,
        )

        fakeLauncherApps.installPackageForUser(
            TEST_PACKAGE_1,
            TEST_CLASS_1,
            UserHandle(/* userId= */ PRIMARY_USER_ID),
            ICON_RES_ID_1,
            TEST_PACKAGE_LABEL_1,
        )

        fakeLauncherApps.installPackageForUser(
            TEST_PACKAGE_2,
            TEST_CLASS_2,
            UserHandle(/* userId= */ PRIMARY_USER_ID),
            ICON_RES_ID_2,
            TEST_PACKAGE_LABEL_2,
        )

        helper.showFromActivity()
    }

    @Test
    fun categories_emitsEmptyList_whenShortcutHelperIsInactive() {
        testScope.runTest {
            val categories by collectLastValue(repo.categories)
            helper.hideFromActivity()

            assertThat(categories).isEmpty()
        }
    }

    @Test
    fun categories_emitsCorrectShortcutCategoryWithAllInstalledApps() {
        testScope.runTest {
            val categories by collectLastValue(repo.categories)

            assertThat(categories).containsExactly(expectedShortcutCategoryWithBothAppShortcuts)
        }
    }

    @Test
    fun categories_emitsEmptyListWhenAlUserVisibleAppsAreUninstalled() {
        testScope.runTest {
            val categories by collectLastValue(repo.categories)

            fakeLauncherApps.uninstallPackageForUser(
                TEST_PACKAGE_1,
                TEST_CLASS_1,
                UserHandle(/* userId= */ PRIMARY_USER_ID),
            )

            fakeLauncherApps.uninstallPackageForUser(
                TEST_PACKAGE_2,
                TEST_CLASS_2,
                UserHandle(/* userId= */ PRIMARY_USER_ID),
            )

            assertThat(categories).isEmpty()
        }
    }

    private val expectedShortcutCategoryWithBothAppShortcuts =
        ShortcutCategory(
            ShortcutCategoryType.AppCategories,
            ShortcutSubCategory(
                label = context.getString(R.string.keyboard_shortcut_group_applications),
                shortcuts =
                    listOf(
                        Shortcut(
                            label = TEST_PACKAGE_LABEL_1,
                            commands = emptyList(),
                            icon =
                                ShortcutIcon(
                                    packageName = TEST_PACKAGE_1,
                                    resourceId = ICON_RES_ID_1,
                                ),
                            pkgName = TEST_PACKAGE_1,
                            className = TEST_CLASS_1,
                        ),
                        Shortcut(
                            label = TEST_PACKAGE_LABEL_2,
                            commands = emptyList(),
                            icon =
                                ShortcutIcon(
                                    packageName = TEST_PACKAGE_2,
                                    resourceId = ICON_RES_ID_2,
                                ),
                            pkgName = TEST_PACKAGE_2,
                            className = TEST_CLASS_2,
                        ),
                    ),
            ),
        )

    private companion object {
        const val TEST_PACKAGE_1 = "test.package.one"
        const val TEST_PACKAGE_2 = "test.package.two"
        const val TEST_CLASS_1 = "TestClassOne"
        const val TEST_CLASS_2 = "TestClassTwo"
        const val PRIMARY_USER_ID = 10
        const val PRIMARY_USER_INDEX = 0
        const val ICON_RES_ID_1 = 1
        const val ICON_RES_ID_2 = 2
        const val TEST_PACKAGE_LABEL_1 = "ApplicationOne"
        const val TEST_PACKAGE_LABEL_2 = "ApplicationTwo"
    }
}
