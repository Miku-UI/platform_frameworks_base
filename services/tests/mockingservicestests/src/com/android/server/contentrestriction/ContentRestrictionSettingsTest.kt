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

package com.android.server.contentrestriction

import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.util.Collections
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [ContentRestrictionSettings].
 *
 * Build/Install/Run:
 * atest FrameworksMockingServicesTests:com.android.server.contentrestriction.ContentRestrictionSettingsTest
 */
@RunWith(AndroidJUnit4::class)
class ContentRestrictionSettingsTest {

    private lateinit var mContentRestrictionSettings: ContentRestrictionSettings
    private lateinit var tempContentRestrictionDir: File

    @Before
    fun setUp() {
        // Creating a temporary folder to enable access.
        tempContentRestrictionDir = Files.createTempDirectory("tempContentRestrictionFolder").toFile()
        mContentRestrictionSettings = ContentRestrictionSettings(tempContentRestrictionDir)
    }

    @Test
    fun saveAndLoadContentRestrictionUserData_oneUser_retrievesUserDataCorrectly() {
        // Get and set user data
        mContentRestrictionSettings.updateUserData(1, true)

        // Save, change and load user data
        mContentRestrictionSettings.saveUserData()
        mContentRestrictionSettings.updateUserData(1, false)
        val newSettings = ContentRestrictionSettings(tempContentRestrictionDir)

        // Check if user data was loaded correctly
        newSettings.verifyUserData(1, true)
    }

    @Test
    fun saveAndLoadContentRestrictionUserData_manyUsers_retrievesUserDataCorrectly() {
        // Get and set user data
        mContentRestrictionSettings.updateUserData(1, true)
        mContentRestrictionSettings.updateUserData(2, true)
        mContentRestrictionSettings.updateUserData(3, false)
        mContentRestrictionSettings.updateUserData(4, false)

        // Save, change and load user data
        mContentRestrictionSettings.saveUserData()
        mContentRestrictionSettings.updateUserData(1, false)
        mContentRestrictionSettings.updateUserData(2, true)
        mContentRestrictionSettings.updateUserData(3, true)
        mContentRestrictionSettings.updateUserData(4, false)
        val newSettings = ContentRestrictionSettings(tempContentRestrictionDir)

        // Check if user data was loaded correctly
        newSettings.verifyUserData(1, true)
        newSettings.verifyUserData(2, true)
        newSettings.verifyUserData(3, false)
        newSettings.verifyUserData(4, false)
    }

    @Test
    fun removeAndGetUserData_returnsNewInstanceOfUserData() {
        // Get and set user data
        val oldUserData = mContentRestrictionSettings.getUserData(1)
        mContentRestrictionSettings.updateUserData(1, true)

        // Remove user data and get a new instance
        mContentRestrictionSettings.removeUserData(1)
        val newUserData = mContentRestrictionSettings.getUserData(1)

        // Check that user data is not the old instance and has default values
        assertThat(newUserData).isNotSameInstanceAs(oldUserData)
        assertThat(newUserData.contentRestrictionEnabled).isFalse()
        assertThat(newUserData.contentRestrictionPackages).isEmpty()
    }

    @Test
    fun saveAndLoadContentRestrictionUserData_hasPackages_retrievesUserDataCorrectly() {
        // Get and set user data
        val packages =
                mapOf(
                        "source1" to listOf("package1", "package2"),
                        "source2" to listOf("package3"),
                )
        mContentRestrictionSettings.updateUserData(1, true, packages)

        // Save, change and load user data
        mContentRestrictionSettings.saveUserData()
        mContentRestrictionSettings.updateUserData(1, false)
        val newSettings = ContentRestrictionSettings(tempContentRestrictionDir)

        // Check if user data was loaded correctly
        newSettings.verifyUserData(1, true, packages)
    }

    @Test
    fun loadUserData_withUnknownTag_skipsTagAndLoadsCorrectly() {
        // TODO(b/465618368): Transition to a XML resource file.
        val malformedFile = File(tempContentRestrictionDir, "contentrestriction_settings.xml")
        malformedFile.writeText(
            """
            <contentrestriction_data>
                <contentrestriction_user_data
                    user_id="1"
                    contentrestriction_enabled="true">
                    <unknown_tag>some value</unknown_tag>
                </contentrestriction_user_data>
            </contentrestriction_data>
            """.trimIndent()
        )

        mContentRestrictionSettings.loadUserData()

        mContentRestrictionSettings.verifyUserData(1, true)
    }
}

private fun ContentRestrictionSettings.updateUserData(
    userId: Int,
    enabled: Boolean,
    packages: Map<String, List<String>> = Collections.emptyMap(),
) {
    getUserData(userId).let {
        it.contentRestrictionEnabled = enabled
        it.contentRestrictionPackages = packages
    }
}

private fun ContentRestrictionSettings.verifyUserData(
    userId: Int,
    enabled: Boolean,
    packages: Map<String, List<String>> = Collections.emptyMap(),
) {
    getUserData(userId).let {
        assertThat(it.contentRestrictionEnabled).isEqualTo(enabled)
        assertThat(it.contentRestrictionPackages).isEqualTo(packages)
    }
}
