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

package com.android.credentialmanager

import android.content.Context
import android.platform.test.flag.junit.SetFlagsRule
import com.android.credentialmanager.getflow.RequestDisplayInfo
import com.android.credentialmanager.model.CredentialType
import com.android.credentialmanager.model.get.ProviderInfo
import com.android.credentialmanager.model.get.CredentialEntryInfo
import platform.test.screenshot.getEmulatedDevicePathConfig
import platform.test.screenshot.utils.compose.ComposeScreenshotTestRule
import org.junit.Rule
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.PhoneAndTabletFull
import androidx.test.core.app.ApplicationProvider
import com.android.credentialmanager.tests.screenshot.R

/** A screenshot test for our Get-Credential flows. */
@RunWith(ParameterizedAndroidJunit4::class)
class GetCredScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() = DeviceEmulationSpec.PhoneAndTabletFull

        val REQUEST_DISPLAY_INFO = RequestDisplayInfo(
            appName = "Test App",
            preferImmediatelyAvailableCredentials = false,
            preferIdentityDocUi = false,
            preferTopBrandingContent = null,
            typePriorityMap = emptyMap(),
        )
    }

    @get:Rule
    val screenshotRule = ComposeScreenshotTestRule(
        emulationSpec,
        CredentialManagerGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec))
    )

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    // The existing test uses a removed primary screen impl. The class structure is left in case
    // future tests are added.

    private fun buildProviderInfoList(): List<ProviderInfo> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider1 = ProviderInfo(
            id = "1",
            icon = context.getDrawable(R.drawable.provider1)!!,
            displayName = "Password Manager 1",
            credentialEntryList = listOf(
                CredentialEntryInfo(
                    providerId = "1",
                    entryKey = "key1",
                    entrySubkey = "subkey1",
                    pendingIntent = null,
                    fillInIntent = null,
                    credentialType = CredentialType.PASSWORD,
                    credentialTypeDisplayName = "Passkey",
                    providerDisplayName = "Password Manager 1",
                    userName = "username",
                    displayName = "Display Name",
                    icon = null,
                    shouldTintIcon = true,
                    lastUsedTimeMillis = null,
                    isAutoSelectable = false,
                    entryGroupId = "username",
                    isDefaultIconPreferredAsSingleProvider = false,
                    rawCredentialType = "unknown-type",
                    affiliatedDomain = null,
                )
            ),
            authenticationEntryList = emptyList(),
            remoteEntry = null,
            actionEntryList = emptyList(),
        )
        return listOf(provider1)
    }
}