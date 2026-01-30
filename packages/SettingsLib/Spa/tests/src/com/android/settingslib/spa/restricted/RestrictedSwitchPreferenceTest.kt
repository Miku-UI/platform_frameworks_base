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

package com.android.settingslib.spa.restricted

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.restricted.Blocked.SwitchPreferenceOverrides
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestrictedSwitchPreferenceTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val switchPreferenceModel =
        object : SwitchPreferenceModel {
            override val title = TITLE
            var checkedState = mutableStateOf(true)
            override val checked = { checkedState.value }
            override val onCheckedChange: (Boolean) -> Unit = { checkedState.value = it }
        }

    private var testRestrictedRepository = TestRestrictedRepository()

    @Test
    fun whenRestrictionsIsEmpty_enabled() {
        val restrictions = TestRestrictions(isEmpty = true)

        setContent(restrictions)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOn()).assertIsDisplayed()
    }

    @Test
    fun whenRestrictionsIsEmpty_toggleable() {
        val restrictions = TestRestrictions(isEmpty = true)

        setContent(restrictions)
        composeTestRule.onRoot().performClick()

        composeTestRule.onNode(isOff()).assertIsDisplayed()
    }

    @Test
    fun whenNoRestricted_enabled() {
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = false

        setContent(restrictions)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOn()).assertIsDisplayed()
    }

    @Test
    fun whenNoRestricted_toggleable() {
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = false

        setContent(restrictions)
        composeTestRule.onRoot().performClick()

        composeTestRule.onNode(isOff()).assertIsDisplayed()
    }

    @Test
    fun whenBlockedWithDetails_notOverrideChecked() {
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = true

        setContent(restrictions)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOn()).assertIsDisplayed()
    }

    @Test
    fun whenBlockedWithDetails_click() {
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = true

        setContent(restrictions)
        composeTestRule.onRoot().performClick()

        assertThat(testRestrictedRepository.detailsIsShown).isTrue()
    }

    @Test
    fun whenBlockedWithDetailsThenOverrideCheckedToFalse_overrideCheckedToFalse() {
        switchPreferenceModel.checkedState.value = true
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = true

        setContent(restrictions, ifBlockedOverrideCheckedTo = false)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOff()).assertIsDisplayed()
    }

    @Test
    fun whenBlockedWithDetailsThenOverrideCheckedToTrue_overrideCheckedToTrue() {
        switchPreferenceModel.checkedState.value = false
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = true

        setContent(restrictions, ifBlockedOverrideCheckedTo = true)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOn()).assertIsDisplayed()
    }

    @Test
    fun whenBlockedWithDetailsThenOverrideCheckedAndSummaryToFalse_overrideCorrectly() {
        switchPreferenceModel.checkedState.value = true
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = true
        testRestrictedRepository.switchPreferenceOverrides =
            SwitchPreferenceOverrides(summaryOff = FORCED_OFF_SUMMARY)

        setContent(restrictions, ifBlockedOverrideCheckedTo = false)

        composeTestRule.onNodeWithText(FORCED_OFF_SUMMARY).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOff()).assertIsDisplayed()
    }

    @Test
    fun whenBlockedWithDetailsThenOverrideCheckedAndSummaryToTrue_overrideCorrectly() {
        switchPreferenceModel.checkedState.value = false
        val restrictions = TestRestrictions(isEmpty = false)
        testRestrictedRepository.isBlockedWithDetails = true
        testRestrictedRepository.switchPreferenceOverrides =
            SwitchPreferenceOverrides(summaryOn = FORCED_ON_SUMMARY)

        setContent(restrictions, ifBlockedOverrideCheckedTo = true)

        composeTestRule.onNodeWithText(FORCED_ON_SUMMARY).assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNode(isOn()).assertIsDisplayed()
    }

    private fun setContent(
        restrictions: TestRestrictions,
        ifBlockedOverrideCheckedTo: Boolean? = null,
    ) {
        composeTestRule.setContent {
            RestrictedSwitchPreference(
                model = switchPreferenceModel,
                restrictions = restrictions,
                repository = testRestrictedRepository,
                ifBlockedOverrideCheckedTo = ifBlockedOverrideCheckedTo,
            )
        }
    }

    private companion object {
        const val TITLE = "Title"
        const val FORCED_ON_SUMMARY = "Forced On Summary"
        const val FORCED_OFF_SUMMARY = "Forced Off Summary"
    }
}
