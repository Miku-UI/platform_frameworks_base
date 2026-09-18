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

package com.android.systemui.personalcontext

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.personalcontext.insight.interaction.AttributionDetails
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.personalcontext.attribution.AttributionFooter
import com.android.systemui.personalcontext.attribution.AttributionHeader
import com.android.systemui.personalcontext.attribution.Attributions
import com.android.systemui.res.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

/**
 * Note: the tests in this file do not check for UI configuration such as theming, layouts etc.
 * These are modifiable by the OEM and is not worthwhile checking. Instead, this asserts only the
 * presence of the required fields.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AttributionDialogComposableTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    // Spy on real context to access resources, mock the rest
    private lateinit var spyContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var mockRoleManager: RoleManager

    @Before
    fun setUp() {
        val realContext: Context = context
        spyContext = spy(realContext)

        mockPackageManager = mock<PackageManager>()
        mockRoleManager = mock<RoleManager>()

        // Stub the context to return mocked managers
        whenever(spyContext.packageManager).thenReturn(mockPackageManager)
        doReturn(mockRoleManager).whenever(spyContext).getSystemService(Context.ROLE_SERVICE)
    }

    @Test
    fun attributionHeader_fieldsSet() {
        val testPackageName = "com.example.testpackage"
        val appInfo =
            ApplicationInfo().apply {
                enabled = true
                packageName = testPackageName
            }
        val fakeIconDrawable: Drawable =
            BitmapDrawable(spyContext.resources, ColorDrawable(Color.BLUE).toBitmap(10, 10))
        mockPackageManager.stub {
            on { getApplicationInfo(eq(testPackageName), anyInt()) } doReturn appInfo
            on { getApplicationIcon(eq(testPackageName)) } doReturn fakeIconDrawable
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides spyContext) { AttributionHeader() }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.ace_attribution_dialog_title))
            .assertIsDisplayed()
    }

    @Test
    fun attributions_fieldsSet() {
        val testDrawable: Drawable = ColorDrawable(Color.BLUE)
        val testIcon = Icon.createWithBitmap(testDrawable.toBitmap(10, 10))
        val testAttributionLine1 =
            AttributionDetails.AttributionLine(
                "Ben Smith(Messages)",
                testIcon,
                "Messages Attribution",
                "Mentioned Philz Coffee",
            )
        val testAttributionLine2 =
            AttributionDetails.AttributionLine(
                "Julia Doe(Email)",
                testIcon,
                "Email Attribution",
                "Booking at Philz Coffee",
            )
        val testAttributionDetails =
            AttributionDetails(listOf(testAttributionLine1, testAttributionLine2))

        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides spyContext) {
                Attributions(testAttributionDetails)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Items considered in this suggestion").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Messages Attribution").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Email Attribution").assertIsDisplayed()
    }

    @Test
    fun attributionFooter_fieldsSet() {
        composeTestRule.setContent { AttributionFooter() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Settings button").assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Thumbs up feedback button")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Thumbs down feedback button")
            .assertIsDisplayed()
    }
}
