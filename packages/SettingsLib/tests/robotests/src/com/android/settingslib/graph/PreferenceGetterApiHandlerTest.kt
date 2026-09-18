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

import android.Manifest.permission.INTERACT_ACROSS_PROFILES
import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.app.Application
import android.content.Context
import android.platform.test.annotations.EnableFlags
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.catalyst.flags.Flags
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.graph.PreferenceGetterFlags.setEagerMode
import com.android.settingslib.ipc.ApiPermissionChecker
import com.android.settingslib.metadata.PreferenceCoordinate
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.testutils.GraphTestUtils
import com.android.settingslib.testutils.GraphTestUtils.createPersistentPreference
import com.android.settingslib.testutils.GraphTestUtils.createScreen
import com.android.settingslib.testutils.GraphTestUtils.makePermissionPass
import com.android.settingslib.testutils.GraphTestUtils.setRegistryFactories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.robolectric.RobolectricTestRunner
import com.android.settingslib.robotests.R
import com.android.settingslib.testutils.GraphTestUtils.PersistentPreferenceConfig
import com.android.settingslib.testutils.GraphTestUtils.PreferenceConfig
import com.android.settingslib.testutils.GraphTestUtils.PreferenceScreenConfig

@RunWith(RobolectricTestRunner::class)
@EnableFlags(Flags.FLAG_CATALYST_USE_KEY_PARAMETERS)
class PreferenceGetterApiHandlerTest {

    val application = spy(ApplicationProvider.getApplicationContext<Application>()!!)
    val context = application as Context

    private val preferenceGetterApiHandler = PreferenceGetterApiHandler(
        0,
        ApiPermissionChecker.alwaysAllow()
    )

    private fun invokeWithRequest(
        screenKey: String,
        key: String,
        flags: Int = PreferenceGetterFlags.ALL.setEagerMode()
    ): PreferenceGetterResponse =
        runBlocking {
            preferenceGetterApiHandler.invoke(
                application,
                0,
                0,
                PreferenceGetterRequest(
                    preferences = arrayOf(
                        PreferenceCoordinate(
                            screenKey = screenKey,
                            key = key,
                        )
                    ),
                    flags,
                )
            )
        }

    @Test
    fun invoke_onStringPreference_succeeds() {
        val persistentPreference = createPersistentPreference<String>(
            persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                preferenceConfig = GraphTestUtils.PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                    isEnabled = true,
                    isAvailable = true,
                    isRestricted = false,
                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY,
                ),
                valueType = String::class.javaObjectType,
                readPermission = INTERACT_ACROSS_USERS,
                readPermit = ReadWritePermit.ALLOW,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = "hello"
            )
        )
        val screen = createScreen(
            screenConfig = GraphTestUtils.PreferenceScreenConfig(
                screenKey = "screen_key",
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(persistentPreference)
            )

        )
        setRegistryFactories(screen)
        makePermissionPass(application, INTERACT_ACROSS_USERS, true)
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)

        val expectedProto = preferenceProto {
            key = "preference_key"
            enabled = true
            available = true
            restricted = false
            purpose = R.string.preference_purpose
            persistent = true
            sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
            writable = true
            addGetPreconditions("availability description")
            readPermissions = Permissions.allOf(
                INTERACT_ACROSS_USERS
            ).toProto()
            writePermissions = Permissions.allOf(
                INTERACT_ACROSS_PROFILES
            ).toProto()
            readWritePermit = ReadWritePermit.make(
                readPermit = ReadWritePermit.ALLOW,
                writePermit = ReadWritePermit.ALLOW
            )
            value = preferenceValueProto {
                stringValue = "hello"
            }
            valueDescriptor = preferenceValueDescriptorProto {
                stringType = true
            }
            launchIntent = screen.getLaunchIntent(context, persistentPreference)?.toProto()
        }

        val actualResponse = invokeWithRequest("screen_key", "preference_key")
        assertThat(actualResponse.errors).isEmpty()
        val actualProto = actualResponse.preferences[
            PreferenceCoordinate("screen_key", "preference_key")
        ]
        assertThat(actualProto).isEqualTo(expectedProto)
    }

    @Test
    fun invoke_onScreenWithScreenKeyPreference_returnsNotFound() {
        val screen = createScreen(
            GraphTestUtils.PreferenceScreenConfig(
                screenKey = "screen_key",
                purpose = R.string.preference_screen_purpose,
            )
        )
        setRegistryFactories(screen)

        val response = invokeWithRequest("screen_key", "screen_key")

        assertThat(response.preferences).isEmpty()
        assertThat(response.errors[
                PreferenceCoordinate("screen_key", "screen_key")
        ]).isEqualTo(PreferenceGetterErrorCode.NOT_FOUND)
    }

    @Test
    fun invoke_onScreenWithDoNotExposePreference_returnsNotFound() {
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            GraphTestUtils.PreferenceConfig(
                                key = "dne_preference",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
                                ),
                            readPermission = null,
                            defaultValue = true
                        )
                    ))
                )
            )
        )

        val response = invokeWithRequest("screen_key", "dne_preference")

        assertThat(response.preferences).isEmpty()
        assertThat(response.errors[
            PreferenceCoordinate("screen_key", "dne_preference")
        ]).isEqualTo(PreferenceGetterErrorCode.NOT_FOUND)
    }

    @Test
    fun invoke_onPreferenceWithNoSensitivityPreference_returnsValue(){
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            GraphTestUtils.PreferenceConfig(
                                key = "no_sensitivity_pref",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                            ),
                            readPermission = null,
                            defaultValue = true
                        ),
                    ))
                )
            )
        )

        val response = invokeWithRequest("screen_key", "no_sensitivity_pref")

        val preference = response.preferences[
            PreferenceCoordinate("screen_key", "no_sensitivity_pref")
        ]
        assertThat(preference?.key).isEqualTo("no_sensitivity_pref")
        assertThat(preference?.value?.booleanValue).isEqualTo(true)
        assertThat(response.errors).isEmpty()
    }

    @Test
    fun invoke_onPreferenceWithMustProvideUndoSensitivity_returnsValue(){
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            GraphTestUtils.PreferenceConfig(
                                key = "must_provide_undo_pref",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO
                            ),
                            readPermission = null,
                            defaultValue = true
                        ),
                    ))
                )
            )
        )

        val response = invokeWithRequest("screen_key", "must_provide_undo_pref")

        val preference = response.preferences[
            PreferenceCoordinate("screen_key", "must_provide_undo_pref")
        ]
        assertThat(preference?.key).isEqualTo("must_provide_undo_pref")
        assertThat(preference?.value?.booleanValue).isEqualTo(true)
        assertThat(response.errors).isEmpty()
    }

    @Test
    fun invoke_onScreenWithRequiresConfirmationSensitivity_returnsValue(){
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            GraphTestUtils.PreferenceConfig(
                                key = "requires_confirmation_pref",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.REQUIRES_CONFIRMATION
                            ),
                            readPermission = null,
                            defaultValue = true
                        ),
                    ))
                )
            )
        )

        val response = invokeWithRequest("screen_key", "requires_confirmation_pref")

        val preference = response.preferences[
            PreferenceCoordinate("screen_key", "requires_confirmation_pref")
        ]
        assertThat(preference?.key).isEqualTo("requires_confirmation_pref")
        assertThat(preference?.value?.booleanValue).isEqualTo(true)
        assertThat(response.errors).isEmpty()
    }

    @Test
    fun invoke_onScreenWithDeeplinkOnlySensitivity_returnsValue(){
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            GraphTestUtils.PreferenceConfig(
                                key = "deeplink_only_pref",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.DEEP_LINK_ONLY
                            ),
                            readPermission = null,
                            defaultValue = true
                        ),
                    ))
                )
            )
        )

        val response = invokeWithRequest("screen_key", "deeplink_only_pref")

        val preference = response.preferences[
            PreferenceCoordinate("screen_key", "deeplink_only_pref")
        ]
        assertThat(preference?.key).isEqualTo("deeplink_only_pref")
        assertThat(preference?.value?.booleanValue).isEqualTo(true)
        assertThat(response.errors).isEmpty()
    }

    @Test
    fun invoke_onScreenWithUiOnlyPreference_returnsNotFound(){
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            GraphTestUtils.PreferenceConfig(
                                key = "ui_only_pref",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY,
                                isUiOnly = true
                            ),
                            readPermission = null,
                            defaultValue = true
                        ),
                    ))
                )
            )
        )
        val response = invokeWithRequest("screen_key", "ui_only_pref")

        assertThat(response.preferences).isEmpty()
        assertThat(response.errors[
            PreferenceCoordinate("screen_key", "ui_only_pref")
        ]).isEqualTo(PreferenceGetterErrorCode.NOT_FOUND)

    }

    @Test
    fun invoke_onScreenWithInnerScreenPreference_returnsNotFound() {
        val innerScreen = createScreen(
            GraphTestUtils.PreferenceScreenConfig(
                screenKey = "inner_screen_key",
                purpose = R.string.preference_screen_purpose
            )
        )
        setRegistryFactories(
            innerScreen,
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "outer_screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(innerScreen)
                )
            )
        )

        val response = invokeWithRequest("outer_screen_key", "inner_screen_key")

        assertThat(response.preferences).isEmpty()
        assertThat(response.errors[
            PreferenceCoordinate("outer_screen_key", "inner_screen_key")
        ]).isEqualTo(PreferenceGetterErrorCode.NOT_FOUND)
    }

    @Test
    fun invoke_onDoNotExposeScreen_returnsNotFound() {
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "dne_screen",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(createPersistentPreference<Boolean>(
                        persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                            GraphTestUtils.PreferenceConfig(
                                key = "no_sensitivity_pref",
                                purpose = R.string.preference_purpose,
                                sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                            ),
                            readPermission = null,
                            defaultValue = true
                        ),
                    )),
                    sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
                )
            )
        )

        val response = invokeWithRequest("dne_screen", "no_sensitivity_pref")

        assertThat(response.preferences).isEmpty()
        assertThat(response.errors[
            PreferenceCoordinate("dne_screen", "no_sensitivity_pref")
        ]).isEqualTo(PreferenceGetterErrorCode.NOT_FOUND)

    }

    @Test
    fun invoke_onNoSensitivityLevelInCategory_returnsValue() {
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "pref_screen",
                    purpose = R.string.preference_screen_purpose,
                    preferencesInCategories = listOf(GraphTestUtils.PreferenceCategoryConfig(
                        key = "preference_category",
                        preferences = listOf(createPersistentPreference<Boolean>(
                            persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                                GraphTestUtils.PreferenceConfig(
                                    key = "no_sensitivity_pref",
                                    purpose = R.string.preference_purpose,
                                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                                ),
                                readPermission = null,
                                defaultValue = true
                            ),
                        ))
                    )),
                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                )
            )
        )

        val response = invokeWithRequest("pref_screen", "no_sensitivity_pref")

        val preference = response.preferences[
            PreferenceCoordinate("pref_screen", "no_sensitivity_pref")
        ]
        assertThat(preference?.key).isEqualTo("no_sensitivity_pref")
        assertThat(preference?.value?.booleanValue).isEqualTo(true)
        assertThat(response.errors).isEmpty()
    }

    @Test
    fun invoke_onDoNotExposeSensitivityLevelInCategory_returnsNotFound() {
        setRegistryFactories(
            createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                    screenKey = "pref_screen",
                    purpose = R.string.preference_screen_purpose,
                    preferencesInCategories = listOf(GraphTestUtils.PreferenceCategoryConfig(
                        key = "preference_category",
                        preferences = listOf(createPersistentPreference<Boolean>(
                            persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                                GraphTestUtils.PreferenceConfig(
                                    key = "dne_pref",
                                    purpose = R.string.preference_purpose,
                                    sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
                                ),
                                readPermission = null,
                                defaultValue = true
                            ),
                        ))
                    )),
                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY
                )
            )
        )

        val response = invokeWithRequest("pref_screen", "dne_pref")

        assertThat(response.preferences).isEmpty()
        assertThat(response.errors[
            PreferenceCoordinate("pref_screen", "dne_pref")
        ]).isEqualTo(PreferenceGetterErrorCode.NOT_FOUND)
    }

    @Test
    fun invoke_onNestedScreens_callOnInnerPreferenceFromInnerScreen_succeeds() {
        val innerPreference = createPersistentPreference<Boolean>(
            persistentPreferenceConfig = PersistentPreferenceConfig(
                PreferenceConfig(
                    key = "inner_preference",
                    purpose = R.string.preference_purpose,
                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY,
                ),
                readPermission = null,
                writePermission = null,
                defaultValue = true
            ),
        )
        val innerScreen = createScreen(PreferenceScreenConfig(
            screenKey = "inner_screen",
            purpose = R.string.preference_screen_purpose,
            preferences = listOf(innerPreference)
        )
        )
        setRegistryFactories(
            innerScreen,
            createScreen(PreferenceScreenConfig(
                screenKey = "outer_screen",
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(innerScreen)
            ))
        )

        val response = invokeWithRequest("inner_screen", "inner_preference")

        val preference = response.preferences[
            PreferenceCoordinate("inner_screen", "inner_preference")
        ]
        assertThat(preference?.key).isEqualTo("inner_preference")
        assertThat(preference?.value?.booleanValue).isEqualTo(true)
        assertThat(response.errors).isEmpty()
    }

    @Test
    fun invoke_onNestedScreens_callOnInnerPreferenceFromOuterScreen_returnsNotFound() {
        val innerPreference = createPersistentPreference<Boolean>(
            persistentPreferenceConfig = PersistentPreferenceConfig(
                PreferenceConfig(
                    key = "inner_preference",
                    purpose = R.string.preference_purpose,
                    sensitivityLevel = SensitivityLevel.NO_SENSITIVITY,
                ),
                readPermission = null,
                writePermission = null,
                defaultValue = true
            ),
        )
        val innerScreen = createScreen(PreferenceScreenConfig(
            screenKey = "inner_screen",
            purpose = R.string.preference_screen_purpose,
            preferences = listOf(innerPreference)
        )
        )
        setRegistryFactories(
            innerScreen,
            createScreen(PreferenceScreenConfig(
                screenKey = "outer_screen",
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(innerScreen)
            ))
        )

        val response = invokeWithRequest("outer_screen", "inner_preference")

        assertThat(response.preferences).isEmpty()
        assertThat(response.errors[
            PreferenceCoordinate("outer_screen", "inner_preference")
        ]).isEqualTo(PreferenceGetterErrorCode.NOT_FOUND)
    }


}