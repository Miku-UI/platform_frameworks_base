/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.display.mode

import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FLOAT_TOLERANCE = 0.001f

@SmallTest
@RunWith(TestParameterInjector::class)
class VoteSummaryTest {

    @get:Rule
    val checkFlagsRule = SetFlagsRule()

    enum class SupportedRefreshRatesTestCase(
            val supportedModesVoteEnabled: Boolean,
            internal val summaryRefreshRates: List<SupportedRefreshRatesVote.RefreshRates>?,
            val modesToFilter: Array<Display.Mode>,
            val expectedModeIds: List<Int>
    ) {
        HAS_NO_MATCHING_VOTE(true,
                listOf(SupportedRefreshRatesVote.RefreshRates(60f, 60f)),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf()
        ),
        HAS_SINGLE_MATCHING_VOTE(true,
                listOf(SupportedRefreshRatesVote.RefreshRates(60f, 90f)),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf(3)
        ),
        HAS_MULTIPLE_MATCHING_VOTES(true,
                listOf(SupportedRefreshRatesVote.RefreshRates(60f, 90f),
                        SupportedRefreshRatesVote.RefreshRates(90f, 90f)),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf(1, 3)
        ),
        HAS_NO_SUPPORTED_MODES(true,
                listOf(),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf()
        ),
        HAS_NULL_SUPPORTED_MODES(true,
                null,
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf(1, 2, 3)
        ),
        HAS_SUPPORTED_MODES_VOTE_DISABLED(false,
                listOf(),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf(1, 2, 3)
        ),
    }

    @Test
    fun testFiltersModes_supportedRefreshRates(
            @TestParameter testCase: SupportedRefreshRatesTestCase
    ) {
        val summary = createSummary(testCase.supportedModesVoteEnabled)
        summary.supportedRefreshRates = testCase.summaryRefreshRates

        val result = summary.filterModes(testCase.modesToFilter)

        assertThat(result.map { it.modeId }).containsExactlyElementsIn(testCase.expectedModeIds)
    }

    enum class SupportedModesTestCase(
            val supportedModesVoteEnabled: Boolean,
            internal val summarySupportedModes: List<Int>?,
            val modesToFilter: Array<Display.Mode>,
            val expectedModeIds: List<Int>
    ) {
        HAS_NO_MATCHING_VOTE(true,
                listOf(4, 5),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf()
        ),
        HAS_SINGLE_MATCHING_VOTE(true,
                listOf(3),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf(3)
        ),
        HAS_MULTIPLE_MATCHING_VOTES(true,
                listOf(1, 3),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf(1, 3)
        ),
        HAS_NO_SUPPORTED_MODES(true,
                listOf(),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf()
        ),
        HAS_NULL_SUPPORTED_MODES(true,
                null,
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf(1, 2, 3)
        ),
        HAS_SUPPORTED_MODES_VOTE_DISABLED(false,
                listOf(),
                arrayOf(createMode(1, 90f, 90f),
                        createMode(2, 90f, 60f),
                        createMode(3, 60f, 90f)),
                listOf(1, 2, 3)
        ),
    }

    @Test
    fun testFiltersModes_supportedModes(@TestParameter testCase: SupportedModesTestCase) {
        val summary = createSummary(testCase.supportedModesVoteEnabled)
        summary.supportedModeIds = testCase.summarySupportedModes

        val result = summary.filterModes(testCase.modesToFilter)

        assertThat(result.map { it.modeId }).containsExactlyElementsIn(testCase.expectedModeIds)
    }

    @Test
    fun testInvalidSummary_requestedRefreshRateLessThanMinRenderRate() {
        val summary = createSummary()
        summary.requestedRefreshRates = setOf(30f, 90f)
        summary.minRenderFrameRate = 60f
        summary.maxRenderFrameRate = 120f

        val result = summary.filterModes(arrayOf(createMode(1, 90f, 90f)))

        assertThat(result).isEmpty()
    }

    @Test
    fun testInvalidSummary_requestedRefreshRateMoreThanMaxRenderRate() {
        val summary = createSummary()
        summary.requestedRefreshRates = setOf(60f, 240f)
        summary.minRenderFrameRate = 60f
        summary.maxRenderFrameRate = 120f

        val result = summary.filterModes(arrayOf(createMode(1, 90f, 90f)))

        assertThat(result).isEmpty()
    }

    @Test
    fun testValidSummary_requestedRefreshRatesWithingRenderRateLimits() {
        val summary = createSummary()
        summary.requestedRefreshRates = setOf(60f, 90f)
        summary.minRenderFrameRate = 60f
        summary.maxRenderFrameRate = 120f

        val result = summary.filterModes(arrayOf(createMode(1, 90f, 90f)))

        assertThat(result).hasSize(1)
    }

    @Test
    fun testValidSummary_renderRateAchievable() {
        val summary = createSummary()
        summary.minRenderFrameRate = 48f
        summary.maxRenderFrameRate = 48f

        val result = summary.filterModes(arrayOf(createMode(1, 120f, 240f)))

        assertThat(result).hasSize(1)
    }

    @Test
    fun testInvalidSummary_renderRateNotAchievable() {
        val summary = createSummary()
        summary.minRenderFrameRate = 48f
        summary.maxRenderFrameRate = 48f

        val result = summary.filterModes(arrayOf(createMode(1, 120f, 120f)))

        assertThat(result).isEmpty()
    }

    @Test
    fun testDisableRenderRateSwitching_renderRateAchievable() {
        val summary = createSummary()
        summary.minRenderFrameRate = 48f
        summary.maxRenderFrameRate = 48f

        summary.disableRenderRateSwitching(240f, 80f)

        assertThat(summary.minRenderFrameRate).isEqualTo(48f)
        assertThat(summary.minRenderFrameRate).isWithin(FLOAT_TOLERANCE).of(48f)
    }

    @Test
    fun testDisableRenderRateSwitching_renderRateNotAchievable() {
        val summary = createSummary()
        summary.minRenderFrameRate = 48f
        summary.maxRenderFrameRate = 48f

        summary.disableRenderRateSwitching(120f, 80f)

        assertThat(summary.minRenderFrameRate).isEqualTo(80f)
        assertThat(summary.minRenderFrameRate).isWithin(FLOAT_TOLERANCE).of(80f)
    }

    @Test
    fun testAdjustSize_preferredSizeRejected_usesNextBestResolution() {
        val summary = createSummary()
        val defaultMode = createMode(1, 11, 90f, 90f, 1920, 1080)
        summary.rejectedSfModeIds = setOf(11)
        val modes = arrayOf(defaultMode,
            createMode(2, 12, 90f, 60f, 1280, 720),
            createMode(3, 13, 60f, 90f, 640, 480))
        summary.width = 1920
        summary.height = 1080

        summary.filterModes(modes)
        summary.adjustSize(defaultMode, modes)

        // Next mode by size after rejected 1920x1080 is 1280x720
        assertThat(summary.width).isEqualTo(1280)
        assertThat(summary.height).isEqualTo(720)
    }

    enum class RejectedModesTestCase(
            internal val summaryRejectedSfModes: Set<Int>?,
            val modesToFilter: Array<Display.Mode>,
            val expectedModeIds: Set<Int>
    ) {
        HAS_NO_MATCHING_VOTE(
            setOf(14, 15),
            arrayOf(createMode(1, 11, 90f, 90f),
                createMode(2, 12, 90f, 60f),
                createMode(3, 1360f, 90f)),
            setOf(1, 2, 3)
        ),
        HAS_SINGLE_MATCHING_VOTE(
            setOf(11),
            arrayOf(createMode(1, 11, 90f, 90f),
                createMode(2, 12, 90f, 60f),
                createMode(3, 13, 60f, 90f)),
            setOf(2, 3)
        ),
        HAS_MULTIPLE_MATCHING_VOTES(
            setOf(11, 12),
            arrayOf(createMode(1, 11, 90f, 90f),
                createMode(2, 12, 90f, 60f),
                createMode(3, 13, 60f, 90f)),
            setOf(3)
        ),
    }

    @Test
    fun testFilterModes_rejectedModes(@TestParameter testCase: RejectedModesTestCase) {
        val summary = createSummary()
        summary.rejectedSfModeIds = testCase.summaryRejectedSfModes

        val result = summary.filterModes(testCase.modesToFilter)

        assertThat(result.map {it.modeId}).containsExactlyElementsIn(testCase.expectedModeIds)
    }

    enum class HdrPreferenceVoteTestCase(
        val isUserPreferredHdrModeAllowed: Boolean,
        val allowHdr: Boolean,
        val modesToFilter: Array<Display.Mode>,
        val expectedModeIds: List<Int>
    ) {
        // User-preferred HDR disallowed: allowHdr is ignored, all pass
        FEATURE_DISABLED(
            isUserPreferredHdrModeAllowed = false, allowHdr = true, modesToFilter = arrayOf(
                createMode(1, 60f, 60f), createMode(2, 60f, 60f, intArrayOf(1))
            ), expectedModeIds = listOf(1, 2)
        ),

        // HDR disallowed: Only SDR passes
        FEATURE_ENABLED_ALLOW_HDR_FALSE(
            isUserPreferredHdrModeAllowed = true, allowHdr = false, modesToFilter = arrayOf(
                createMode(1, 60f, 60f), // SDR
                createMode(2, 60f, 60f, intArrayOf(1)), createMode(3, 60f, 60f, intArrayOf(2))
            ), expectedModeIds = listOf(1)
        ),

        // HDR allowed: all pass
        FEATURE_ENABLED_ALLOW_HDR_TRUE(
            isUserPreferredHdrModeAllowed = true, allowHdr = true, modesToFilter = arrayOf(
                createMode(1, 60f, 60f), createMode(2, 60f, 60f, intArrayOf(1))
            ), expectedModeIds = listOf(1, 2)
        ),
    }

    @Test
    fun testFiltersModes_hdrTypes(@TestParameter testCase: HdrPreferenceVoteTestCase) {
        val summary = createSummary(
            isUserPreferredHdrModeAllowed = testCase.isUserPreferredHdrModeAllowed
        )
        summary.allowHdr = testCase.allowHdr

        val result = summary.filterModes(testCase.modesToFilter)

        assertThat(result.map { it.modeId }).containsExactlyElementsIn(testCase.expectedModeIds)
    }

    enum class SelectBaseModeTestCase(
        val isUserPreferredHdrModeAllowed: Boolean,
        val allowHdr: Boolean,
        val appRequestBaseModeRefreshRate: Float,
        val availableModes: Array<Display.Mode>,
        val expectedModeId: Int
    ) {
        // Choose matching refresh rate (default is set to 60Hz in this test)
        MATCHING_DEFAULT_MODE_REFRESH_RATE(
            isUserPreferredHdrModeAllowed = false,
            allowHdr = true,
            appRequestBaseModeRefreshRate = 0f,
            availableModes = arrayOf(
                createMode(1, 90f, 90f), createMode(2, 60f, 60f)
            ),
            expectedModeId = 2
        ),
        // Choose matching refresh rate based on appRequestedMode
        MATCHING_APP_REQUESTED_REFRESH_RATE(
            isUserPreferredHdrModeAllowed = false,
            allowHdr = true,
            appRequestBaseModeRefreshRate = 90f,
            availableModes = arrayOf(
                createMode(1, 90f, 90f), createMode(2, 60f, 60f)
            ),
            expectedModeId = 1
        ),
        // Select matching refresh rate and HDR-capable mode
        USER_PREFERRED_HDR_ALLOWED_VOTE_ALLOW_HDR_SELECT_MATCHING_HDR(
            isUserPreferredHdrModeAllowed = true,
            allowHdr = true,
            appRequestBaseModeRefreshRate = 90f,
            availableModes = arrayOf(
                createMode(1, 60f, 60f),
                createMode(2, 90f, 90f, intArrayOf(1)),
                createMode(3, 90f, 90f)
            ),
            expectedModeId = 2
        ),
        // Select matching refresh rate even though it's SDR
        USER_PREFERRED_HDR_ALLOWED_VOTE_ALLOW_HDR_NO_MATCHING_HDR_SELECT_SDR(
            isUserPreferredHdrModeAllowed = true,
            allowHdr = true,
            appRequestBaseModeRefreshRate = 90f,
            availableModes = arrayOf(
                createMode(1, 90f, 90f),
                createMode(2, 60f, 60f, intArrayOf(1)),
                createMode(3, 60f, 60f)
            ),
            expectedModeId = 1
        ),
        // No matching refresh rate, vote allowHdr, select HDR if possible
        USER_PREFERRED_HDR_ALLOWED_VOTE_ALLOW_HDR_NO_MATCHING_REFRESH_RATE_SELECT_HDR(
            isUserPreferredHdrModeAllowed = true,
            allowHdr = true,
            appRequestBaseModeRefreshRate = 90f,
            availableModes = arrayOf(
                createMode(1, 60f, 60f), createMode(2, 60f, 60f, intArrayOf(1))
            ),
            expectedModeId = 2
        ),
        // No matching refresh rate or HDR mode, select first available mode
        USER_PREFERRED_HDR_ALLOWED_VOTE_ALLOW_HDR_NO_MATCHING_REFRESH_RATE_AND_HDR_SELECT_ANY(
            isUserPreferredHdrModeAllowed = true,
            allowHdr = true,
            appRequestBaseModeRefreshRate = 90f,
            availableModes = arrayOf(
                createMode(1, 60f, 60f), createMode(2, 60f, 60f)
            ),
            expectedModeId = 1
        ),
        // Select matching refresh rate and SDR-only mode
        USER_PREFERRED_HDR_ALLOWED_VOTE_FORCE_SDR_SELECT_MATCHING_SDR(
            isUserPreferredHdrModeAllowed = true,
            allowHdr = false,
            appRequestBaseModeRefreshRate = 90f,
            availableModes = arrayOf(
                createMode(1, 90f, 90f, intArrayOf(1)), createMode(2, 90f, 90f)
            ),
            expectedModeId = 2
        ),
        // No matching refresh rate, select first available mode. Note that if there's no matching
        // refresh rate with SDR mode, there will be no matching refresh rate with HDR-capable mode
        // either
        USER_PREFERRED_HDR_ALLOWED_VOTE_FORCE_SDR_NO_MATCHING_REFRESH_RATE_SELECT_ANY_SDR(
            isUserPreferredHdrModeAllowed = true,
            allowHdr = false,
            appRequestBaseModeRefreshRate = 90f,
            availableModes = arrayOf(
                createMode(1, 60f, 60f), createMode(2, 60f, 60f)
            ),
            expectedModeId = 1
        )
    }

    @Test
    fun testSelectBaseMode(@TestParameter testCase: SelectBaseModeTestCase) {
        val summary =
            createSummary(isUserPreferredHdrModeAllowed = testCase.isUserPreferredHdrModeAllowed)
        summary.allowHdr = testCase.allowHdr
        summary.appRequestBaseModeRefreshRate = testCase.appRequestBaseModeRefreshRate

        val defaultMode = createMode(0, 60f, 60f)
        val result = summary.selectBaseMode(testCase.availableModes.toList(), defaultMode)

        assertThat(result?.modeId).isEqualTo(testCase.expectedModeId)
    }

    enum class BaseModeSelectionIsModeBetterTestCase(
        val currentMode: Display.Mode?,
        val newMode: Display.Mode,
        val allowHdr: Boolean,
        val preferredRefreshRate: Float,
        val expectedResult: Boolean
    ) {
        CURRENT_MODE_NULL(
            null, createMode(1, 60f, 60f), /* allowHdr= */ true, 60f, /* expectedResult= */ true
        ),
        NEW_MODE_MATCHES_RR_CURRENT_MODE_DOES_NOT(
            createMode(1, 60f, 60f),
            createMode(2, 90f, 90f),
            /* allowHdr= */ true, 90f, /* expectedResult= */ true
        ),
        NEW_MODE_HDR_BUT_DOES_NOT_MATCH_RR(
            createMode(1, 90f, 90f), // Matches RR
            createMode(2, 60f, 60f, intArrayOf(1)), // HDR, but wrong RR
            /* allowHdr= */ true, 90f, /* expectedResult= */ false
        ),
        HDR_ALLOWED_PREFER_HDR_OVER_SDR(
            createMode(1, 60f, 60f), // SDR
            createMode(2, 60f, 60f, intArrayOf(1)), // HDR
            /* allowHdr= */ true, 60f, /* expectedResult= */ true
        ),
        HDR_ALLOWED_KEEP_HDR_OVER_SDR(
            createMode(1, 60f, 60f, intArrayOf(1)), // HDR
            createMode(2, 60f, 60f), // SDR
            /* allowHdr= */ true, 60f, /* expectedResult= */ false
        ),
        HDR_DISALLOWED_SWITCH_FROM_HDR_TO_SDR(
            createMode(1, 60f, 60f, intArrayOf(1)), // HDR
            createMode(2, 60f, 60f), // SDR
            /* allowHdr= */ false, 60f, /* expectedResult= */ true
        ),
        HDR_DISALLOWED_KEEP_SDR_OVER_HDR(
            createMode(1, 60f, 60f), // SDR
            createMode(2, 60f, 60f, intArrayOf(1)), // HDR
            /* allowHdr= */ false, 60f, /* expectedResult= */ false
        ),

        SAME_MODE_ATTRIBUTES_KEEP_CURRENT(
            createMode(1, 60f, 60f),
            createMode(2, 60f, 60f),
            /* allowHdr= */ true, 60f, /* expectedResult= */ false
        ),
    }

    @Test
    fun isNewModeBetterForBaseMode(@TestParameter testCase: BaseModeSelectionIsModeBetterTestCase) {
        val result = VoteSummary.isNewModeBetterForBaseMode(
            testCase.currentMode, testCase.newMode, testCase.allowHdr, testCase.preferredRefreshRate
        )
        assertThat(result).isEqualTo(testCase.expectedResult)
    }
}

private fun createMode(
    modeId: Int, refreshRate: Float, vsyncRate: Float, hdrTypes: IntArray = IntArray(0)
): Display.Mode {
    return Display.Mode(
        modeId, -1, -1, 0, 600, 800, refreshRate, vsyncRate, FloatArray(0), hdrTypes
    )
}

private fun createMode(modeId: Int, sfModeId: Int, refreshRate: Float, vsyncRate: Float,
                       width: Int = 600, height: Int = 800): Display.Mode {
    return Display.Mode(modeId, -1, sfModeId, 0, width, height, refreshRate, vsyncRate,
        FloatArray(0), IntArray(0))
}

private fun createSummary(
    supportedModesVoteEnabled: Boolean = false,
    isUserPreferredHdrModeAllowed: Boolean = false
): VoteSummary {
    val summary =
        createVotesSummary(
            supportedModesVoteEnabled = supportedModesVoteEnabled,
            isUserPreferredHdrModeAllowed = isUserPreferredHdrModeAllowed
        )
    summary.width = 600
    summary.height = 800
    summary.maxPhysicalRefreshRate = Float.POSITIVE_INFINITY
    summary.maxRenderFrameRate = Float.POSITIVE_INFINITY

    return summary
}
