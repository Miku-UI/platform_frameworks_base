/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.settingslib.graph

import android.app.Application
import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.catalyst.flags.Flags
import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.android.settingslib.ipc.ApiPermissionChecker
import com.android.settingslib.metadata.PreferenceCategory
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceScreenCoordinate
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.UI_ONLY_PREFERENCE
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.robotests.R
import com.android.settingslib.testutils.GraphTestUtils
import com.android.settingslib.testutils.GraphTestUtils.PreferenceScreenConfig
import com.android.settingslib.testutils.GraphTestUtils.createPersistentPreference
import com.android.settingslib.testutils.GraphTestUtils.createScreen
import com.android.settingslib.testutils.GraphTestUtils.createSimplePreference
import com.android.settingslib.testutils.GraphTestUtils.setRegistryFactories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@EnableFlags(Flags.FLAG_CATALYST_USE_KEY_PARAMETERS)
class GetPreferenceGraphApiHandlerTest {

    val application = spy(ApplicationProvider.getApplicationContext<Application>()!!)
    val context = application as Context

    private val dummyId = 0

    private val getPreferenceGraphApiHandler = GetPreferenceGraphApiHandler(
        dummyId,
        ApiPermissionChecker.alwaysAllow()
    )

    private fun invokeWithFlags(
        screenKey: String?,
        flags: Int,
    ) = runBlocking {
            getPreferenceGraphApiHandler.invoke(
                application,
                dummyId,
                dummyId,
                GetPreferenceGraphRequest(
                    screens = if (screenKey == null) setOf() else
                        setOf(PreferenceScreenCoordinate(screenKey)),
                    flags = flags
                ),
            )
        }

    @After
    fun tearDown() {
        ShadowBuild.reset()
    }

    @Before
    fun setUp() {
        Settings.Global.putString(
            context.contentResolver,
            "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
            null
        )
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithNonUiPreferences_returnsFullHierarchyProto() {
        setRegistryFactories(
            simpleScreen
        )

        val responseProto = invokeWithFlags(
            "simple_screen_key",
            PreferenceGetterFlags.METADATA
        )
        assertThat(responseProto.screensMap).hasSize(1)

        val screen = responseProto.screensMap["simple_screen_key"]!!.root
        assertThat(screen.preferenceOrNull?.key).isEqualTo("simple_screen_key")
        assertThat(screen.preferencesList).hasSize(2)
        assertThat(screen.preferencesList[0].preferenceOrNull?.key).isEqualTo("preference_1")

        val category1Proto = screen.preferencesList[1].group
        // category is now null
        assertThat(category1Proto.preferenceOrNull?.key).isEqualTo(null)
        assertThat(category1Proto.preferencesList).hasSize(2)
        assertThat(category1Proto.preferencesList[0].preferenceOrNull?.key).isEqualTo("preference_2")

        // category is now null
        val category2Proto = category1Proto.preferencesList[1].group
        assertThat(category2Proto.preferenceOrNull?.key).isEqualTo(null)
        assertThat(category2Proto.preferencesList).hasSize(1)
        assertThat(category2Proto.preferencesList[0].preferenceOrNull?.key).isEqualTo("preference_3")
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithUiPreferences_NonDebuggableBuild_excludesUiOnlyPreferences() {
        // makes build non-debuggable
        ShadowBuild.setType("user")
        setRegistryFactories(
            screenWithUiOnlyPreferences
        )

        val responseProto = invokeWithFlags(
            "screen_key_with_ui_only_preferences",
            PreferenceGetterFlags.METADATA
        )

        screenWithUiPreferencesProtoHasOnlyNonUiOnly(responseProto)
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithUiPreferences_DebuggableBuild_NoExcludeSetting_includesAllPreferences() {
        // makes build debuggable
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
            0
        )
        setRegistryFactories(
            screenWithUiOnlyPreferences
        )

        val responseProto = invokeWithFlags(
            "screen_key_with_ui_only_preferences",
            PreferenceGetterFlags.METADATA
        )

        screenWithUiPreferencesProtoHasAllPreferences(responseProto)
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithUiPreferences_DebuggableBuild_ExcludeSetting_includesAllPreferences() {
        // makes build debuggable
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
            1
        )
        setRegistryFactories(
            screenWithUiOnlyPreferences
        )
        val responseProto = invokeWithFlags(
            "screen_key_with_ui_only_preferences",
            PreferenceGetterFlags.METADATA
        )
        screenWithUiPreferencesProtoHasOnlyNonUiOnly(responseProto)
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithUiPreferences_DebuggableBuild_MissingExcludeSettings_excludesUiOnlyPreferences() {
        // makes build debuggable
        ShadowBuild.setType("userdebug")
        Settings.Global.putString(
            context.contentResolver,
            "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
            null
        )
        setRegistryFactories(
            screenWithUiOnlyPreferences
        )
        val responseProto = invokeWithFlags(
            "screen_key_with_ui_only_preferences",
            PreferenceGetterFlags.METADATA
        )
        screenWithUiPreferencesProtoHasOnlyNonUiOnly(responseProto)
    }
    @Test
    fun invoke_withRequestMetadata_onDoNotExposeScreen_doesNotIncludeAnyOfItsData() = runTest {
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "do_not_expose_screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(
                        createPersistentPreference<Boolean>(
                            persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                                preferenceConfig = GraphTestUtils.PreferenceConfig(
                                    key = "no_sensitivity_preference",
                                    purpose = R.string.preference_purpose,
                                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                                ),
                            )
                        )
                    ),
                    sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
                )
            )
        )

        val responseProto = invokeWithFlags(
            "do_not_expose_screen_key",
            PreferenceGetterFlags.METADATA
        )

        assertThat(responseProto.screensMap).hasSize(0)
    }

    @Test
    fun invoke_onDoNotExposePreference_doesNotIncludeIt() = runTest {
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "no_sensitivity_screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(
                        createPersistentPreference<Boolean>(
                            persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                                preferenceConfig = GraphTestUtils.PreferenceConfig(
                                    key = "do_not_expose_preference",
                                    purpose = R.string.preference_purpose,
                                    sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
                                ),
                            )
                        )
                    ),
                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                )
            )
        )

        val responseProto = invokeWithFlags(
            "no_sensitivity_screen_key",
            PreferenceGetterFlags.METADATA
        )

        assertThat(responseProto.screensMap.keys).hasSize(1)
        assertThat(responseProto.screensMap["no_sensitivity_screen_key"]?.root?.preference?.key).isEqualTo("no_sensitivity_screen_key")
        assertThat(responseProto.screensMap["no_sensitivity_screen_key"]?.root?.preferencesList).hasSize(0)
    }

    @Test
    fun invoke_onNoSensitivityScreenAndPreference_includesThem() = runTest {
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "no_sensitivity_screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(
                        createPersistentPreference<Boolean>(
                            persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                                preferenceConfig = GraphTestUtils.PreferenceConfig(
                                    key = "no_sensitivity_preference",
                                    purpose = R.string.preference_purpose,
                                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                                ),
                            )
                        )
                    ),
                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                )
            )
        )

        val responseProto = invokeWithFlags(
            "no_sensitivity_screen_key",
            PreferenceGetterFlags.METADATA
        )

        assertThat(responseProto.screensMap.keys).hasSize(1)
        assertThat(responseProto.screensMap["no_sensitivity_screen_key"]?.root?.preference?.key).isEqualTo("no_sensitivity_screen_key")
        assertThat(responseProto.screensMap["no_sensitivity_screen_key"]?.root?.preferencesList).hasSize(1)
        assertThat(responseProto.screensMap["no_sensitivity_screen_key"]?.root?.preferencesList[0]?.preference?.key).isEqualTo("no_sensitivity_preference")
    }

    @Test
    fun invoke_onExposableScreenWithInnerNonExposableScreen_onlyIncludesOuterScreen() {
        val innerSensitiveScreen = createScreen(
            PreferenceScreenConfig(
                screenKey = "inner_sensitive_screen_key",
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(
                    createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            preferenceConfig = GraphTestUtils.PreferenceConfig(
                                key = "inner_no_sensitivity_preference",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                            ),
                        )
                    )
                ),
                sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
            )
        )
        val outerNoSensitivityScreen = createScreen(
            PreferenceScreenConfig(
                screenKey = "outer_no_sensitivity_screen_key",
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(
                    createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            preferenceConfig = GraphTestUtils.PreferenceConfig(
                                key = "outer_no_sensitivity_preference",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                            ),
                        )
                    ),
                    innerSensitiveScreen
                ),
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
            )
        )
        setRegistryFactories(innerSensitiveScreen, outerNoSensitivityScreen)

        val responseProto = invokeWithFlags(
            "outer_no_sensitivity_screen_key",
            PreferenceGetterFlags.METADATA
        )

        assertThat(responseProto.screensMap["outer_no_sensitivity_screen_key"]?.root?.preference?.key).isEqualTo("outer_no_sensitivity_screen_key")
        assertThat(responseProto.screensMap["outer_no_sensitivity_screen_key"]?.root?.preferencesList).hasSize(1)
        assertThat(responseProto.screensMap["outer_no_sensitivity_screen_key"]?.root?.preferencesList[0]?.preference?.key).isEqualTo("outer_no_sensitivity_preference")
    }

    @Test
    fun invoke_onNoSensitivityNestedScreens_includesOnlyOuterScreen() {
        val innerSensitiveScreen = createScreen(
            PreferenceScreenConfig(
                screenKey = "inner_no_sensitive_screen_key",
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(
                    createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            preferenceConfig = GraphTestUtils.PreferenceConfig(
                                key = "inner_no_sensitivity_preference",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                            ),
                        )
                    )
                ),
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
            )
        )
        val outerNoSensitivityScreen = createScreen(
            PreferenceScreenConfig(
                screenKey = "outer_no_sensitivity_screen_key",
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(
                    createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            preferenceConfig = GraphTestUtils.PreferenceConfig(
                                key = "outer_no_sensitivity_preference",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                            ),
                        )
                    ),
                    innerSensitiveScreen
                ),
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
            )
        )
        setRegistryFactories(innerSensitiveScreen, outerNoSensitivityScreen)

        val responseProto = invokeWithFlags(
            "outer_no_sensitivity_screen_key",
            PreferenceGetterFlags.METADATA
        )

        assertThat(responseProto.screensMap["outer_no_sensitivity_screen_key"]?.root?.preference?.key).isEqualTo("outer_no_sensitivity_screen_key")
        assertThat(responseProto.screensMap["outer_no_sensitivity_screen_key"]?.root?.preferencesList).hasSize(1)
        assertThat(responseProto.screensMap["outer_no_sensitivity_screen_key"]?.root?.preferencesList[0]?.preference?.key).isEqualTo("outer_no_sensitivity_preference")
    }

    @Test
    fun invoke_onNoSensitivityScreenWithCategoriesAndVariousSensitivities_skipsOverDoNotExposePreferences() = runTest {
        val noSensPref = createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                key = "no_sens_pref",
                purpose = R.string.preference_purpose,
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
            )
        )
        val sensPref = createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                key = "sens_pref",
                purpose = R.string.preference_purpose,
                sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
            )
        )
        val noSensPrefInOuter = createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                key = "no_sens_outer",
                purpose = R.string.preference_purpose,
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
            )
        )
        val sensPrefInInner = createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                key = "sens_inner",
                purpose = R.string.preference_purpose,
                sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
            )
        )

        val innerCategory = GraphTestUtils.PreferenceCategoryConfig(
            key = "inner_category",
            preferences = listOf(sensPrefInInner)
        )
        val outerCategory = GraphTestUtils.PreferenceCategoryConfig(
            key = "outer_category",
            preferences = listOf(noSensPrefInOuter),
            innerCategories = listOf(innerCategory)
        )

        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "test_screen",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(noSensPref, sensPref),
                    preferencesInCategories = listOf(outerCategory),
                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                )
            )
        )

        val responseProto = invokeWithFlags(
            "test_screen",
            PreferenceGetterFlags.METADATA
        )

        assertThat(responseProto.screensMap).hasSize(1)
        val screen = responseProto.screensMap["test_screen"]!!.root
        assertThat(screen.preferencesList).hasSize(2)
        assertThat(screen.preferencesList[0].preference.key).isEqualTo("no_sens_pref")

        val outerCategoryProto = screen.preferencesList[1].group
        assertThat(outerCategoryProto.preferencesList).hasSize(2)
        assertThat(outerCategoryProto.preferencesList[0].preference.key).isEqualTo("no_sens_outer")

        val innerCategoryProto = outerCategoryProto.preferencesList[1].group
        // SensitivityLevel.DO_NOT_EXPOSE preference is filtered out completely from the list
        assertThat(innerCategoryProto.preferencesList).hasSize(0)
    }

    @Test
    fun invoke_onNoSensitivityScreen_hasNoSummary() = runTest {
        setRegistryFactories(createScreen(
            PreferenceScreenConfig(
                screenKey = "screen_key",
                purpose = R.string.preference_screen_purpose,
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY,
                summary = R.string.preference_screen_summary
            )
        ))
        val responseProto = invokeWithFlags(
            "screen_key",
            PreferenceGetterFlags.METADATA
        )
        val screenMetadata = responseProto.screensMap["screen_key"]!!.rootOrNull?.preference
        assertThat(screenMetadata?.summaryOrNull).isNull()
    }

    @Test
    fun invoke_onNoSensitivityScreen_hasSensitivityLevel() = runTest {
        setRegistryFactories(createScreen(
            PreferenceScreenConfig(
                screenKey = "screen_key",
                purpose = R.string.preference_screen_purpose,
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY,
                summary = R.string.preference_screen_summary
            )
        ))
        val responseProto = invokeWithFlags(
            "screen_key",
            PreferenceGetterFlags.METADATA
        )
        val screenMetadata = responseProto.screensMap["screen_key"]!!.rootOrNull?.preference
        assertThat(screenMetadata?.sensitivityLevel).isEqualTo(SensitivityLevel.NO_SENSITIVITY)
    }

    @Test
    fun invoke_onNoSensitivityPreference_hasNoSummary() = runTest {
        setRegistryFactories(createScreen(
            PreferenceScreenConfig(
                screenKey = "screen_key",
                purpose = R.string.preference_screen_purpose,
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY,
                preferences = listOf(
                    createSimplePreference(
                        GraphTestUtils.PreferenceConfig(
                            key = "preference_key",
                            purpose = R.string.preference_purpose,
                            summary = (R.string.preference_summary),
                            sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                        )
                    )
                )
            )
        ))

        val responseProto = invokeWithFlags(
            "screen_key",
            PreferenceGetterFlags.METADATA
        )

        val preferenceProto = responseProto.screensMap["screen_key"]!!.root?.preferencesList[0]?.preference
        assertThat(preferenceProto?.summaryOrNull).isEqualTo(null)
    }

    @Test
    fun invoke_onNoSensitivityPreference_hasSensitivityLevel() = runTest {
        setRegistryFactories(createScreen(
            PreferenceScreenConfig(
                screenKey = "screen_key",
                purpose = R.string.preference_screen_purpose,
                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY,
                preferences = listOf(
                    createSimplePreference(
                        GraphTestUtils.PreferenceConfig(
                            key = "preference_key",
                            purpose = R.string.preference_purpose,
                            summary = (R.string.preference_summary),
                            sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                        )
                    )
                )
            )
        ))

        val responseProto = invokeWithFlags(
            "screen_key",
            PreferenceGetterFlags.METADATA
        )

        val preferenceProto = responseProto.screensMap["screen_key"]!!.root?.preferencesList[0]?.preference
        assertThat(preferenceProto?.sensitivityLevel).isEqualTo(SensitivityLevel.NO_SENSITIVITY)
    }


    private fun screenWithUiPreferencesProtoHasOnlyNonUiOnly(responseProto: PreferenceGraphProto) {
        assertThat(responseProto.screensMap).hasSize(1)

        val screen = responseProto.screensMap["screen_key_with_ui_only_preferences"]!!.root
        assertThat(screen.preferenceOrNull?.key).isEqualTo("screen_key_with_ui_only_preferences")
        assertThat(screen.preferencesList).hasSize(1)
        // Ui only preference does not exist in proto
        val uiOnlyCategory = screen.preferencesList[0].group
        // Ui only category is excluded
        assertThat(uiOnlyCategory.preferenceOrNull).isNull()
        assertThat(uiOnlyCategory.preferencesList).hasSize(2)
        assertThat(uiOnlyCategory.preferencesList[0].preference.key).isEqualTo("preference_2")

        val uiOnlyCategory2 = uiOnlyCategory.preferencesList[1].group
        assertThat(uiOnlyCategory2.preferenceOrNull).isNull()
        assertThat(uiOnlyCategory2.preferencesList).hasSize(1)
        // Non-UI only preference appears
        assertThat(uiOnlyCategory2.preferencesList[0].preference.key).isEqualTo("preference_3")
    }

    private fun screenWithUiPreferencesProtoHasAllPreferences(responseProto: PreferenceGraphProto) {
        assertThat(responseProto.screensMap).hasSize(1)

        val screen = responseProto.screensMap["screen_key_with_ui_only_preferences"]!!.root
        assertThat(screen.preferenceOrNull?.key).isEqualTo("screen_key_with_ui_only_preferences")
        assertThat(screen.preferencesList).hasSize(2)
        // Ui only preference is included
        assertThat(screen.preferencesList[0].preferenceOrNull?.key).isEqualTo(uiOnlyPreference1.key)
        assertThat(screen.preferencesList[0].groupOrNull).isNull()

        val uiOnlyCategoryProto = screen.preferencesList[1].group
        // Ui only category is not included due to sensitivity
        assertThat(uiOnlyCategoryProto.preferenceOrNull?.key).isEqualTo(null)
        assertThat(uiOnlyCategoryProto.preferencesList).hasSize(2)
        assertThat(uiOnlyCategoryProto.preferencesList[0].preference.key).isEqualTo(preference2.key)

        val uiOnlyCategory2Proto = uiOnlyCategoryProto.preferencesList[1].group
        assertThat(uiOnlyCategory2Proto.preferenceOrNull?.key).isEqualTo(null)
        assertThat(uiOnlyCategory2Proto.preferencesList).hasSize(2)
        // Non-UI only preference is included
        assertThat(uiOnlyCategory2Proto.preferencesList[0].preference.key).isEqualTo(preference3.key)
        // Ui only preference is included
        assertThat(uiOnlyCategory2Proto.preferencesList[1].preferenceOrNull?.key).isEqualTo(uiOnlyPreference4.key)
        assertThat(uiOnlyCategory2Proto.preferencesList[1].groupOrNull).isNull()
    }


    val preference1 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "preference_1",
            purpose = R.string.preference_purpose,
        )
    )

    val uiOnlyPreference1 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "ui_only_preference_1",
            purpose = R.string.preference_purpose,
            isUiOnly = true
        )
    )
    val preference2 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "preference_2",
            purpose = R.string.preference_purpose
        )
    )

    val preference3 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "preference_3",
            purpose = R.string.preference_purpose
        )
    )

    val uiOnlyPreference4 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "ui_only_preference_4",
            purpose = R.string.preference_purpose,
            isUiOnly = true
        )
    )

    val uiOnlyCategory = object: PreferenceCategory(
        key = "ui_only_preference_category",
        purpose = R.string.preference_purpose,
        title = 0
    ) {
        override fun tags(context: Context) =
            arrayOf(UI_ONLY_PREFERENCE)
    }

    val uiOnlyCategory2 = object: PreferenceCategory(
        key = "ui_only_preference_category_2",
        purpose = R.string.preference_purpose,
        title = 0
    ) {
        override fun tags(context: Context) =
            arrayOf(UI_ONLY_PREFERENCE)
    }

    val category = object: PreferenceCategory(
        key = "preference_category_1",
        purpose = R.string.preference_purpose,
        title = 0
    ){}

    val category2 = object: PreferenceCategory(
        key = "preference_category_2",
        purpose = R.string.preference_purpose,
        title = 0
    ){}

     val simpleScreen = object : PreferenceScreenMetadata {
        override fun fragmentClass(): Class<out Fragment>? = null

        override fun getPreferenceHierarchy(
            context: Context,
            coroutineScope: CoroutineScope
        ): PreferenceHierarchy = preferenceHierarchy(context) {
            +preference1
            +category += {
                +preference2
                +category2 += {
                    +preference3
                }
            }
        }

        override val key: String
            get() = "simple_screen_key"
        override val purpose: Int
            get() = R.string.preference_screen_purpose
    }

    val screenWithUiOnlyPreferences = object : PreferenceScreenMetadata {

        override fun fragmentClass(): Class<out Fragment>? = null
        override fun getPreferenceHierarchy(
            context: Context,
            coroutineScope: CoroutineScope
        ): PreferenceHierarchy = preferenceHierarchy(context) {
            +uiOnlyPreference1
            +uiOnlyCategory += {
                +preference2
                +uiOnlyCategory2 += {
                    +preference3
                    +uiOnlyPreference4
                }
            }
        }

        override val key: String
            get() = "screen_key_with_ui_only_preferences"
        override val purpose: Int
            get() = R.string.preference_screen_purpose
    }
}