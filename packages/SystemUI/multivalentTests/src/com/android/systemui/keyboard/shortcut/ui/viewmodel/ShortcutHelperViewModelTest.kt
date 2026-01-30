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

package com.android.systemui.keyboard.shortcut.ui.viewmodel

import android.app.role.RoleManager
import android.app.role.mockRoleManager
import android.content.Context
import android.content.applicationContext
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.view.Display
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.keyboard.shortcut.customShortcutCategoriesRepository
import com.android.systemui.keyboard.shortcut.data.source.TestShortcuts
import com.android.systemui.keyboard.shortcut.defaultShortcutCategoriesRepository
import com.android.systemui.keyboard.shortcut.fakeCustomShortcutCategoriesRepository
import com.android.systemui.keyboard.shortcut.fakeDefaultShortcutCategoriesRepository
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategory
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType.CurrentApp
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType.MultiTasking
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType.System
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutSubCategory
import com.android.systemui.keyboard.shortcut.shared.model.shortcut
import com.android.systemui.keyboard.shortcut.shortcutHelperCategoriesInteractor
import com.android.systemui.keyboard.shortcut.shortcutHelperCustomizationModeInteractor
import com.android.systemui.keyboard.shortcut.shortcutHelperStateInteractor
import com.android.systemui.keyboard.shortcut.shortcutHelperTestHelper
import com.android.systemui.keyboard.shortcut.shortcutHelperViewModel
import com.android.systemui.keyboard.shortcut.ui.model.IconSource
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutCategoryUi
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutsUiState
import com.android.systemui.keyboard.shortcut.ui.viewmodel.ShortcutHelperViewModel.Companion.EXTENDED_APPS_SHORTCUT_CUSTOMIZATION_LIMIT
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.model.sysUiState
import com.android.systemui.settings.FakeUserTracker
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.settings.userTracker
import com.android.systemui.shade.data.repository.fakeFocusedDisplayRepository
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SHORTCUT_HELPER_SHOWING
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShortcutHelperViewModelTest : SysuiTestCase() {

    private val mockPackageManager: PackageManager = mock()
    private val mockUserContext: Context = mock()
    private val mockApplicationInfo: ApplicationInfo = mock()

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().also {
            it.defaultShortcutCategoriesRepository = it.fakeDefaultShortcutCategoriesRepository
            it.customShortcutCategoriesRepository = it.fakeCustomShortcutCategoriesRepository
            it.userTracker = FakeUserTracker(onCreateCurrentUserContext = { mockUserContext })
        }

    private val testScope = kosmos.testScope
    private val testHelper = kosmos.shortcutHelperTestHelper
    private val sysUiState = kosmos.sysUiState
    private val fakeUserTracker = kosmos.fakeUserTracker
    private val mockRoleManager = kosmos.mockRoleManager
    private val underTest = kosmos.shortcutHelperViewModel
    private val secondaryDisplayViewModel =
        with(kosmos) {
            ShortcutHelperViewModel(
                applicationContext,
                mockRoleManager,
                userTracker,
                applicationCoroutineScope,
                testDispatcher,
                shortcutHelperStateInteractor,
                shortcutHelperCategoriesInteractor,
                shortcutHelperCustomizationModeInteractor,
                displayId = SECONDARY_DISPLAY,
            )
        }
    private val fakeDefaultShortcutCategoriesRepository =
        kosmos.fakeDefaultShortcutCategoriesRepository
    private val fakeCustomShortcutCategoriesRepository =
        kosmos.fakeCustomShortcutCategoriesRepository

    @Before
    fun setUp() {
        fakeDefaultShortcutCategoriesRepository.setShortcutCategories(
            TestShortcuts.systemCategory,
            TestShortcuts.multitaskingCategory,
            TestShortcuts.currentAppCategory,
        )
        whenever(mockPackageManager.getApplicationInfo(anyString(), eq(0)))
            .thenReturn(mockApplicationInfo)
        whenever(mockPackageManager.getApplicationLabel(mockApplicationInfo))
            .thenReturn("Current App")
        whenever(mockPackageManager.getApplicationIcon(anyString()))
            .thenThrow(NameNotFoundException())
        whenever(mockUserContext.packageManager).thenReturn(mockPackageManager)
    }

    @Test
    fun shouldShow_falseByDefault() =
        testScope.runTest {
            val shouldShow by collectLastValue(underTest.shouldShow)

            assertThat(shouldShow).isFalse()
        }

    @Test
    fun shouldShow_trueAfterShowRequested() =
        testScope.runTest {
            val shouldShow by collectLastValue(underTest.shouldShow)

            testHelper.showFromActivity()

            assertThat(shouldShow).isTrue()
        }

    @Test
    fun shouldShow_trueAfterToggleRequested() =
        testScope.runTest {
            val shouldShow by collectLastValue(underTest.shouldShow)

            testHelper.toggle(deviceId = 123)

            assertThat(shouldShow).isTrue()
        }

    @Test
    fun shouldShow_falseAfterToggleTwice() =
        testScope.runTest {
            val shouldShow by collectLastValue(underTest.shouldShow)

            testHelper.toggle(deviceId = 123)
            testHelper.toggle(deviceId = 123)

            assertThat(shouldShow).isFalse()
        }

    @Test
    fun shouldShow_falseAfterViewClosed() =
        testScope.runTest {
            val shouldShow by collectLastValue(underTest.shouldShow)

            testHelper.toggle(deviceId = 567)
            underTest.onViewClosed()

            assertThat(shouldShow).isFalse()
        }

    @Test
    fun shouldShow_falseAfterCloseSystemDialogs() =
        testScope.runTest {
            val shouldShow by collectLastValue(underTest.shouldShow)

            testHelper.showFromActivity()
            testHelper.hideThroughCloseSystemDialogs()

            assertThat(shouldShow).isFalse()
        }

    @Test
    fun shouldShow_doesNotEmitDuplicateValues() =
        testScope.runTest {
            val shouldShowValues by collectValues(underTest.shouldShow)

            testHelper.hideForSystem()
            testHelper.toggle(deviceId = 987)
            testHelper.showFromActivity()
            underTest.onViewClosed()
            testHelper.hideFromActivity()
            testHelper.hideForSystem()
            testHelper.toggle(deviceId = 456)
            testHelper.showFromActivity()

            assertThat(shouldShowValues).containsExactly(false, true, false, true).inOrder()
        }

    @Test
    fun shouldShow_emitsLatestValueToNewSubscribers() =
        testScope.runTest {
            val shouldShow by collectLastValue(underTest.shouldShow)

            testHelper.showFromActivity()

            val shouldShowNew by collectLastValue(underTest.shouldShow)
            assertThat(shouldShowNew).isEqualTo(shouldShow)
        }

    @Test
    fun shouldShow_falseWhenShortcutHelperTriggeredFromDifferentDisplay() =
        testScope.runTest {
            kosmos.fakeFocusedDisplayRepository.setDisplayId(Display.DEFAULT_DISPLAY)
            val shouldShow by collectLastValue(secondaryDisplayViewModel.shouldShow)

            testHelper.showFromActivity()

            assertThat(shouldShow).isFalse()
        }

    @Test
    fun shouldShow_TrueWhenShortcutHelperTriggeredFromSameDisplay() =
        testScope.runTest {
            kosmos.fakeFocusedDisplayRepository.setDisplayId(SECONDARY_DISPLAY)
            val shouldShow by collectLastValue(secondaryDisplayViewModel.shouldShow)

            testHelper.showFromActivity()

            assertThat(shouldShow).isTrue()
        }

    @Test
    fun sysUiStateFlag_disabledByDefault() =
        testScope.runTest {
            assertThat(sysUiState.isFlagEnabled(SYSUI_STATE_SHORTCUT_HELPER_SHOWING)).isFalse()
        }

    @Test
    fun sysUiStateFlag_trueAfterViewOpened() =
        testScope.runTest {
            underTest.onViewOpened()

            assertThat(sysUiState.isFlagEnabled(SYSUI_STATE_SHORTCUT_HELPER_SHOWING)).isTrue()
        }

    @Test
    fun sysUiStateFlag_falseAfterViewClosed() =
        testScope.runTest {
            underTest.onViewOpened()
            underTest.onViewClosed()

            assertThat(sysUiState.isFlagEnabled(SYSUI_STATE_SHORTCUT_HELPER_SHOWING)).isFalse()
        }

    @Test
    fun shortcutsUiState_inactiveByDefault() =
        testScope.runTest {
            val uiState by collectLastValue(underTest.shortcutsUiState)

            assertThat(uiState).isEqualTo(ShortcutsUiState.Inactive)
        }

    @Test
    fun shortcutsUiState_featureActive_emitsActive() =
        testScope.runTest {
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()

            assertThat(uiState).isInstanceOf(ShortcutsUiState.Active::class.java)
        }

    @Test
    fun shortcutsUiState_noCurrentAppCategory_defaultSelectedCategoryIsSystem() =
        testScope.runTest {
            fakeDefaultShortcutCategoriesRepository.setShortcutCategories(
                TestShortcuts.systemCategory,
                TestShortcuts.multitaskingCategory,
            )

            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.defaultSelectedCategory).isEqualTo(System)
        }

    @Test
    fun shortcutsUiState_currentAppCategoryPresent_currentAppIsDefaultSelected() =
        testScope.runTest {
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.defaultSelectedCategory)
                .isEqualTo(CurrentApp(TestShortcuts.currentAppPackageName))
        }

    @Test
    fun shortcutsUiState_currentAppIsLauncher_defaultSelectedCategoryIsSystem() =
        testScope.runTest {
            whenever(
                    mockRoleManager.getRoleHoldersAsUser(
                        RoleManager.ROLE_HOME,
                        fakeUserTracker.userHandle,
                    )
                )
                .thenReturn(listOf(TestShortcuts.currentAppPackageName))
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.defaultSelectedCategory).isEqualTo(System)
        }

    @Test
    fun shortcutsUiState_userTypedQuery_filtersMatchingShortcutLabels() =
        testScope.runTest {
            setShortcutsCategoriesForSearchQuery()
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()
            underTest.onSearchQueryChanged("foo")

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.shortcutCategories)
                .containsExactly(
                    ShortcutCategoryUi(
                        label = "System",
                        iconSource = IconSource(imageVector = Icons.Default.Tv),
                        shortcutCategory =
                            ShortcutCategory(
                                System,
                                subCategoryWithShortcutLabels("first Foo shortcut1"),
                                subCategoryWithShortcutLabels(
                                    "second foO shortcut2",
                                    subCategoryLabel = SECOND_SIMPLE_GROUP_LABEL,
                                ),
                            ),
                    ),
                    ShortcutCategoryUi(
                        label = "Multitasking",
                        iconSource = IconSource(imageVector = Icons.Default.VerticalSplit),
                        shortcutCategory =
                            ShortcutCategory(
                                MultiTasking,
                                subCategoryWithShortcutLabels("third FoO shortcut1"),
                            ),
                    ),
                )
        }

    @Test
    fun shortcutsUiState_userTypedQuery_noMatch_returnsEmptyList() =
        testScope.runTest {
            setShortcutsCategoriesForSearchQuery()
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()
            underTest.onSearchQueryChanged("unmatched query")

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.shortcutCategories).isEmpty()
        }

    @Test
    fun shortcutsUiState_userTypedQuery_noMatch_returnsNullDefaultSelectedCategory() =
        testScope.runTest {
            setShortcutsCategoriesForSearchQuery()
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()
            underTest.onSearchQueryChanged("unmatched query")

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.defaultSelectedCategory).isNull()
        }

    @Test
    fun shortcutsUiState_userTypedQuery_changesDefaultSelectedCategoryToFirstMatchingCategory() =
        testScope.runTest {
            fakeDefaultShortcutCategoriesRepository.setShortcutCategories(
                ShortcutCategory(System, subCategoryWithShortcutLabels("first shortcut")),
                ShortcutCategory(MultiTasking, subCategoryWithShortcutLabels("second shortcut")),
            )
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()
            underTest.onSearchQueryChanged("second")

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.defaultSelectedCategory).isEqualTo(MultiTasking)
        }

    @Test
    fun shortcutsUiState_userTypedQuery_multipleCategoriesMatch_currentAppIsDefaultSelected() =
        testScope.runTest {
            fakeDefaultShortcutCategoriesRepository.setShortcutCategories(
                ShortcutCategory(System, subCategoryWithShortcutLabels("first shortcut")),
                ShortcutCategory(MultiTasking, subCategoryWithShortcutLabels("second shortcut")),
                ShortcutCategory(
                    CurrentApp(TEST_PACKAGE),
                    subCategoryWithShortcutLabels("third shortcut"),
                ),
            )
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()
            underTest.onSearchQueryChanged("shortcut")

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.defaultSelectedCategory).isInstanceOf(CurrentApp::class.java)
        }

    @Test
    fun shortcutsUiState_shouldShowResetButton_isFalseWhenThereAreNoCustomShortcuts() =
        testScope.runTest {
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.shouldShowResetButton).isFalse()
        }

    @Test
    fun shortcutsUiState_shouldShowResetButton_isTrueWhenThereAreCustomShortcuts() =
        testScope.runTest {
            fakeCustomShortcutCategoriesRepository.setShortcutCategories(
                ShortcutCategory(
                    System,
                    subCategoryWithShortcutLabels("first shortcut", isCustomShortcut = true),
                )
            )
            val uiState by collectLastValue(underTest.shortcutsUiState)

            testHelper.showFromActivity()

            val activeUiState = uiState as ShortcutsUiState.Active
            assertThat(activeUiState.shouldShowResetButton).isTrue()
        }

    @Test
    fun shortcutsUiState_searchQuery_isResetAfterHelperIsClosedAndReOpened() =
        testScope.runTest {
            val uiState by collectLastValue(underTest.shortcutsUiState)

            openHelperAndSearchForFooString()
            assertThat((uiState as? ShortcutsUiState.Active)?.searchQuery).isEqualTo("foo")

            closeAndReopenShortcutHelper()
            assertThat((uiState as? ShortcutsUiState.Active)?.searchQuery).isEqualTo("")
        }

    @Test
    fun shortcutsUiState_customizationModeDisabledByDefault() {
        testScope.runTest {
            testHelper.showFromActivity()
            val uiState by collectLastValue(underTest.shortcutsUiState)

            assertFalse((uiState as ShortcutsUiState.Active).isCustomizationModeEnabled)
        }
    }

    @Test
    fun shortcutsUiState_customizationModeEnabledOnRequest() {
        testScope.runTest {
            testHelper.showFromActivity()
            val uiState by collectLastValue(underTest.shortcutsUiState)
            underTest.toggleCustomizationMode(true)

            assertTrue((uiState as ShortcutsUiState.Active).isCustomizationModeEnabled)
        }
    }

    @Test
    fun shortcutsUiState_customizationModeDisabledOnRequest() {
        testScope.runTest {
            testHelper.showFromActivity()
            val uiState by collectLastValue(underTest.shortcutsUiState)
            underTest.toggleCustomizationMode(true)
            underTest.toggleCustomizationMode(false)

            assertFalse((uiState as ShortcutsUiState.Active).isCustomizationModeEnabled)
        }
    }

    @Test
    fun shortcutsUiState_customizationModeDisabledWhenShortcutHelperIsReopened() {
        testScope.runTest {
            testHelper.showFromActivity()
            val uiState by collectLastValue(underTest.shortcutsUiState)
            underTest.toggleCustomizationMode(true)
            closeAndReopenShortcutHelper()

            assertFalse((uiState as ShortcutsUiState.Active).isCustomizationModeEnabled)
        }
    }

    @Test
    fun allowExtendedAppShortcutsCustomization_true_WhenExtraAppsShortcutsCustomizedIsBelowLimit() {
        testScope.runTest {
            openShortcutHelper()

            underTest.toggleCustomizationMode(true)
            val uiState by collectLastValue(underTest.shortcutsUiState)

            val activeUiState = uiState as ShortcutsUiState.Active

            assertThat(activeUiState.allowExtendedAppShortcutsCustomization).isTrue()
        }
    }

    @Test
    fun allowExtendedAppShortcutsCustomization_false_WhenExtraAppsShortcutsCustomizedIsAtLimit() {
        testScope.runTest {
            openShortcutHelper(
                customShortcutsCountForExtendedApps = EXTENDED_APPS_SHORTCUT_CUSTOMIZATION_LIMIT
            )

            underTest.toggleCustomizationMode(true)
            val uiState by collectLastValue(underTest.shortcutsUiState)

            val activeUiState = uiState as ShortcutsUiState.Active

            assertThat(activeUiState.allowExtendedAppShortcutsCustomization).isFalse()
        }
    }

    @Test
    fun showsCustomAppsShortcutLimitHeader_whenAtLimit_customizationModeEnabled() {
        testScope.runTest {
            openShortcutHelper(
                customShortcutsCountForExtendedApps = EXTENDED_APPS_SHORTCUT_CUSTOMIZATION_LIMIT
            )

            underTest.toggleCustomizationMode(true)
            val uiState by collectLastValue(underTest.shortcutsUiState)

            val activeUiState = uiState as ShortcutsUiState.Active

            assertThat(activeUiState.shouldShowCustomAppsShortcutLimitHeader).isTrue()
        }
    }

    @Test
    fun doesNotShowCustomAppsShortcutLimitHeader_whenBelowLimit_customizationModeEnabled() {
        testScope.runTest {
            openShortcutHelper()

            underTest.toggleCustomizationMode(true)
            val uiState by collectLastValue(underTest.shortcutsUiState)

            val activeUiState = uiState as ShortcutsUiState.Active

            assertThat(activeUiState.shouldShowCustomAppsShortcutLimitHeader).isFalse()
        }
    }

    @Test
    fun doesNotShowCustomAppsShortcutLimitHeader_whenAtLimit_customizationModeDisabled() {
        testScope.runTest {
            openShortcutHelper(
                customShortcutsCountForExtendedApps = EXTENDED_APPS_SHORTCUT_CUSTOMIZATION_LIMIT
            )
            underTest.toggleCustomizationMode(false)
            val uiState by collectLastValue(underTest.shortcutsUiState)

            val activeUiState = uiState as ShortcutsUiState.Active

            assertThat(activeUiState.shouldShowCustomAppsShortcutLimitHeader).isFalse()
        }
    }

    @Test
    fun doesNotShowCustomAppsShortcutLimitHeader_whenBelowLimit_customizationModeDisabled() {
        testScope.runTest {
            openShortcutHelper()
            underTest.toggleCustomizationMode(false)
            val uiState by collectLastValue(underTest.shortcutsUiState)

            val activeUiState = uiState as ShortcutsUiState.Active

            assertThat(activeUiState.shouldShowCustomAppsShortcutLimitHeader).isFalse()
        }
    }

    private fun openShortcutHelper(
        customShortcutsCountForExtendedApps: Int = 0,
        customShortcutsCountForDefaultApps: Int = 3,
        defaultShortcutsCount: Int = 3,
    ) {
        setupShortcutHelperWithExtendedAppsShortcutCustomizations(
            numberOfDefaultAppsShortcuts = defaultShortcutsCount,
            numberOfCustomShortcutsForDefaultApps = customShortcutsCountForDefaultApps,
            numberOfCustomShortcutsForExtendedApps = customShortcutsCountForExtendedApps,
        )
    }

    private fun setupShortcutHelperWithExtendedAppsShortcutCustomizations(
        numberOfDefaultAppsShortcuts: Int,
        numberOfCustomShortcutsForDefaultApps: Int,
        numberOfCustomShortcutsForExtendedApps: Int,
    ) {
        testHelper.showFromActivity()
        fakeDefaultShortcutCategoriesRepository.setShortcutCategories(
            buildAppShortcutsCategory(numberOfDefaultAppsShortcuts)
        )
        fakeCustomShortcutCategoriesRepository.setShortcutCategories(
            buildAppShortcutsCategory(
                numberOfCustomShortcutsForDefaultApps,
                numberOfCustomShortcutsForExtendedApps,
            )
        )
    }

    private fun openHelperAndSearchForFooString() {
        testHelper.showFromActivity()
        underTest.onSearchQueryChanged("foo")
    }

    private fun closeAndReopenShortcutHelper() {
        underTest.onViewClosed()
        testHelper.showFromActivity()
    }

    private fun subCategoryWithShortcutLabels(
        vararg shortcutLabels: String,
        subCategoryLabel: String = FIRST_SIMPLE_GROUP_LABEL,
        isCustomShortcut: Boolean = false,
    ) =
        ShortcutSubCategory(
            label = subCategoryLabel,
            shortcuts = shortcutLabels.map { simpleShortcut(it, isCustomShortcut) },
        )

    private fun subCategoryWithShortcutLabels(
        defaultShortcutLabels: List<String>,
        customShortcutLabels: List<String>,
        subCategoryLabel: String = FIRST_SIMPLE_GROUP_LABEL,
    ) =
        ShortcutSubCategory(
            label = subCategoryLabel,
            shortcuts =
                defaultShortcutLabels.map { simpleShortcut(it) } +
                    customShortcutLabels.map { simpleShortcut(it, isCustom = true) },
        )

    private fun setShortcutsCategoriesForSearchQuery() {
        fakeDefaultShortcutCategoriesRepository.setShortcutCategories(
            ShortcutCategory(
                System,
                subCategoryWithShortcutLabels("first Foo shortcut1", "first bar shortcut1"),
                subCategoryWithShortcutLabels(
                    "second foO shortcut2",
                    "second bar shortcut2",
                    subCategoryLabel = SECOND_SIMPLE_GROUP_LABEL,
                ),
            ),
            ShortcutCategory(
                MultiTasking,
                subCategoryWithShortcutLabels("third FoO shortcut1", "third bar shortcut1"),
            ),
        )
    }

    private fun simpleShortcut(label: String, isCustom: Boolean = false) =
        shortcut(label) {
            command {
                key("Ctrl")
                key("A")
                isCustom(isCustom)
            }
            contentDescription { "$label, Press key Ctrl plus A" }
        }

    private fun buildAppShortcutsCategory(
        numberOfDefaultAppsShortcuts: Int = 0,
        numberOfCustomAppsShortcuts: Int = 0,
    ): ShortcutCategory {
        val defaultAppLabels = (1..numberOfDefaultAppsShortcuts).map { "default app $it" }
        val customAppLabels = (1..numberOfCustomAppsShortcuts).map { "custom app $it" }

        return ShortcutCategory(
            type = ShortcutCategoryType.AppCategories,
            subCategories = listOf(subCategoryWithShortcutLabels(defaultAppLabels, customAppLabels)),
        )
    }

    private companion object {
        const val FIRST_SIMPLE_GROUP_LABEL = "simple group 1"
        const val SECOND_SIMPLE_GROUP_LABEL = "simple group 2"
        const val TEST_PACKAGE = "test.package.name"
        const val SECONDARY_DISPLAY = 5
    }
}
