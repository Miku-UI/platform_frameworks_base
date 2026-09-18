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

package com.android.settingslib.safetycenter

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStatus
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafetyCenterUiDataTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val user0 = UserHandle.of(0)
    private val user10 = UserHandle.of(10)

    private val defaultStatus =
        SafetyCenterStatus.Builder("Default Title", "Default Summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
            .build()

    // Helper to create a minimal SafetyCenterIssue
    private fun createIssue(
        id: String,
        user: UserHandle,
        sourceIds: Set<String>,
        severity: Int,
        title: String = "Issue $id",
    ): SafetyCenterIssue {
        return SafetyCenterIssue.Builder(
                id,
                title,
                "Summary $id",
                user,
                sourceIds,
                "type_$id",
                "source_issue_id_$id",
            )
            .setSeverityLevel(severity)
            .build()
    }

    // Helper to create a minimal SafetyCenterEntry
    private fun createEntry(
        id: String,
        user: UserHandle,
        sourceId: String,
        title: String = "Entry $id",
        summary: CharSequence? = null,
        severityLevel: Int = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED,
        pendingIntent: PendingIntent? = null,
    ): SafetyCenterEntry {
        return SafetyCenterEntry.Builder(id, title, user, sourceId)
            .setSummary(summary)
            .setSeverityLevel(severityLevel)
            .setPendingIntent(pendingIntent)
            .build()
    }

    // Helper to create a minimal SafetyCenterStaticEntry
    private fun createStaticEntry(
        user: UserHandle,
        sourceId: String,
        title: String = "Static entry",
        summary: CharSequence? = null,
        pendingIntent: PendingIntent? = null,
    ): SafetyCenterStaticEntry {
        return SafetyCenterStaticEntry.Builder(title, user, sourceId)
            .setSummary(summary)
            .setPendingIntent(pendingIntent)
            .build()
    }

    private val entry1 = createEntry("entry1", user0, "sourceA")
    private val entry2 = createEntry("entry2", user0, "sourceB")
    private val entry3 = createEntry("entry3", user10, "sourceA")
    private val entry4 = createEntry("entry4", user10, "sourceC")

    private val staticEntry1 = createStaticEntry(user0, "sourceD")
    private val staticEntry2 = createStaticEntry(user10, "sourceE")

    private val issue1 =
        createIssue(
            "issue1",
            user0,
            setOf("s1"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING,
        )
    private val issue2 =
        createIssue(
            "issue2",
            user10,
            setOf("s2"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING,
        )
    private val issue3 =
        createIssue(
            "issue3",
            user0,
            setOf("s1", "s2"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION,
        )
    private val issue4 =
        createIssue(
            "issue4",
            user0,
            setOf("s3"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION,
        )
    private val issue5 =
        createIssue(
            "issue5",
            user10,
            setOf("s1"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK,
            )
    private val dismissedIssue1 =
        createIssue(
            "issue6",
            user0,
            setOf("s4"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK,
            )
    private val dismissedIssue2 =
        createIssue(
            "issue7",
            user0,
            setOf("s3"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION,
        )
    private val dismissedIssue3 =
        createIssue(
            "issue8",
            user10,
            setOf("s1"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING,
        )

    private val testSafetyCenterUiData =
        SafetyCenterUiData(
            status = defaultStatus,
            entriesByUserIdAndSourceId =
                mapOf(
                    0 to mapOf("sourceA" to entry1, "sourceB" to entry2),
                    10 to mapOf("sourceA" to entry3, "sourceC" to entry4),
                ),
            staticEntriesByUserIdAndSourceId =
                mapOf(
                    0 to mapOf("sourceD" to staticEntry1),
                    10 to mapOf("sourceE" to staticEntry2),
                ),
            activeIssuesBySourceId =
                mapOf(
                    "s1" to listOf(issue1, issue3, issue5),
                    "s2" to listOf(issue2, issue3),
                    "s3" to listOf(issue4),
                ),
            dismissedIssuesBySourceId =
                mapOf(
                    "s4" to listOf(dismissedIssue1),
                    "s3" to listOf(dismissedIssue2),
                    "s1" to listOf(dismissedIssue3),
                ),
        )

    @Test
    fun getEntry_dynamicEntryExists_returnsDynamicEntry() {
        assertThat(testSafetyCenterUiData.getEntry(0, "sourceA"))
            .isEqualTo(SafetyCenterUiEntry.fromSafetyCenterEntry(entry1))
        assertThat(testSafetyCenterUiData.getEntry(0, "sourceB"))
            .isEqualTo(SafetyCenterUiEntry.fromSafetyCenterEntry(entry2))
        assertThat(testSafetyCenterUiData.getEntry(10, "sourceA"))
            .isEqualTo(SafetyCenterUiEntry.fromSafetyCenterEntry(entry3))
    }

    @Test
    fun getEntry_noDynamicEntry_onlyStaticEntryExists_returnsStaticEntry() {
        assertThat(testSafetyCenterUiData.getEntry(0, "sourceD"))
            .isEqualTo(SafetyCenterUiEntry.fromSafetyCenterStaticEntry(staticEntry1))
        assertThat(testSafetyCenterUiData.getEntry(10, "sourceE"))
            .isEqualTo(SafetyCenterUiEntry.fromSafetyCenterStaticEntry(staticEntry2))
    }

    @Test
    fun getEntry_entryNotExists_returnsNull() {
        assertThat(testSafetyCenterUiData.getEntry(0, "sourceX")).isNull()
        assertThat(testSafetyCenterUiData.getEntry(10, "sourceB")).isNull()
        assertThat(testSafetyCenterUiData.getEntry(99, "sourceA")).isNull()
    }

    @Test
    fun getDynamicEntriesForSources_noMatchingSources_returnsEmptyList() {
        val result =
            testSafetyCenterUiData.getDynamicEntriesForSources(listOf("sourceX", "sourceY"))
        assertThat(result).isEmpty()
    }

    @Test
    fun getDynamicEntriesForSources_oneMatchingSource_returnsMatchingEntries() {
        val result = testSafetyCenterUiData.getDynamicEntriesForSources(listOf("sourceB"))
        assertThat(result).containsExactly(entry2)
    }

    @Test
    fun getDynamicEntriesForSources_multipleMatchingSources_returnsAllMatchingEntries() {
        val result =
            testSafetyCenterUiData.getDynamicEntriesForSources(listOf("sourceA", "sourceC"))
        assertThat(result).containsExactly(entry1, entry3, entry4)
    }

    @Test
    fun getDynamicEntriesForSources_someNonMatchingSources_returnsOnlyMatchingEntries() {
        val result =
            testSafetyCenterUiData.getDynamicEntriesForSources(
                listOf("sourceB", "sourceX", "sourceC")
            )
        assertThat(result).containsExactly(entry2, entry4)
    }

    @Test
    fun getDynamicEntriesForSources_duplicateSourceIdsInInput_returnsDistinctEntries() {
        val result =
            testSafetyCenterUiData.getDynamicEntriesForSources(listOf("sourceA", "sourceA"))
        assertThat(result).containsExactly(entry1, entry3)
    }

    @Test
    fun getDynamicEntriesForSources_emptySourceIdList_returnsEmptyList() {
        val result = testSafetyCenterUiData.getDynamicEntriesForSources(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun getDynamicEntriesForSources_emptyData_returnsEmptyList() {
        val emptyUiData =
            SafetyCenterUiData(
                status = defaultStatus,
                entriesByUserIdAndSourceId = emptyMap(),
                staticEntriesByUserIdAndSourceId = emptyMap(),
                activeIssuesBySourceId = emptyMap(),
                dismissedIssuesBySourceId = emptyMap(),
            )
        val result = emptyUiData.getDynamicEntriesForSources(listOf("sourceA"))
        assertThat(result).isEmpty()
    }

    @Test
    fun getActiveIssues_noOrder_sortedBySeverity() {
        val issues = testSafetyCenterUiData.getActiveIssues()
        assertThat(issues).containsExactly(issue1, issue2, issue3, issue4, issue5).inOrder()
    }

    @Test
    fun getActiveIssues_withOrder_sortedBySeverityAndSourceOrder() {
        val sourceOrder = listOf("s3", "s2", "s1")
        val issues = testSafetyCenterUiData.getActiveIssues(sourceOrder)
        assertThat(issues).containsExactly(issue2, issue1, issue4, issue3, issue5).inOrder()
    }

    @Test
    fun getDismissedIssues_noOrder_sortedBySeverity() {
        val issues = testSafetyCenterUiData.getDismissedIssues()
        assertThat(issues)
            .containsExactly(dismissedIssue3, dismissedIssue2, dismissedIssue1)
            .inOrder()
    }

    @Test
    fun getDismissedIssues_withOrder_sortedBySeverityAndSourceOrder() {
        val sourceOrder = listOf("s3", "s4", "s1")
        val issues = testSafetyCenterUiData.getDismissedIssues(sourceOrder)
        // Expected order: CRITICAL (s1), RECOMMENDATION (s3), OK (s4)
        assertThat(issues)
            .containsExactly(dismissedIssue3, dismissedIssue2, dismissedIssue1)
            .inOrder()
    }

    @Test
    fun getDismissedIssues_emptyData_returnsEmptyList() {
        val emptyUiData =
            SafetyCenterUiData(
                status = defaultStatus,
                entriesByUserIdAndSourceId = emptyMap(),
                staticEntriesByUserIdAndSourceId = emptyMap(),
                activeIssuesBySourceId = emptyMap(),
                dismissedIssuesBySourceId = emptyMap(),
            )
        val result = emptyUiData.getDismissedIssues()
        assertThat(result).isEmpty()
    }

    @Test
    fun getActiveIssuesForSources_filtersAndSorts() {
        val sourceOrder = listOf("s3", "s1")
        val issues = testSafetyCenterUiData.getActiveIssuesForSources(sourceOrder)
        assertThat(issues).containsExactly(issue1, issue4, issue3, issue5).inOrder()
    }

    @Test
    fun getActiveIssuesForSources_notFound_returnsEmpty() {
        val issues = testSafetyCenterUiData.getActiveIssuesForSources(listOf("nonExistent"))
        assertThat(issues).isEmpty()
    }

    @Test
    fun getDismissedIssuesForSources_filtersOutGreenIssues() {
        val issues = testSafetyCenterUiData.getDismissedIssuesForSources(listOf("s1", "s3", "s4"))
        assertThat(issues).containsExactly(dismissedIssue3, dismissedIssue2).inOrder()
    }

    @Test
    fun getDismissedIssuesForSources_notFound_returnsEmpty() {
        val issues = testSafetyCenterUiData.getDismissedIssuesForSources(listOf("s2"))
        assertThat(issues).isEmpty()
    }

    @Test
    fun resolvedIssues_byDefault_isEmpty() {
        assertThat(testSafetyCenterUiData.resolvedIssues).isEmpty()
    }

    @Test
    fun copyWithResolvedIssues_createsCopyAndUpdatesResolvedIssues() {
        val resolved = mapOf("issue_id_1" to "action_id_1", "issue_id_2" to "action_id_2")

        val updatedUiData = testSafetyCenterUiData.copyWithResolvedIssues(resolved)

        assertThat(testSafetyCenterUiData.resolvedIssues).isEmpty()
        assertThat(updatedUiData).isNotSameInstanceAs(testSafetyCenterUiData)
        assertThat(updatedUiData.resolvedIssues).isEqualTo(resolved)

        val expectedUiData = testSafetyCenterUiData.copy(resolvedIssues = resolved)
        assertThat(updatedUiData).isEqualTo(expectedUiData)
    }

    /** Verifies that all fields from a [SafetyCenterEntry] are correctly mapped. */
    @Test
    fun fromSafetyCenterEntry_mapsAllFieldsCorrectly() {
        // Setup: Create a detailed SafetyCenterEntry
        val intent = Intent("FAKE_ACTION")
        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val entry =
            createEntry(
                id = "entry_id",
                user = user0,
                sourceId = "source_id",
                title = "Title",
                summary = "Summary",
                severityLevel = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION,
                pendingIntent = pendingIntent,
            )

        // Action: Convert to SafetyCenterUiEntry
        val uiEntry = SafetyCenterUiEntry.fromSafetyCenterEntry(entry)

        // Assertions: Verify all fields are mapped correctly
        assertThat(uiEntry.title).isEqualTo("Title")
        assertThat(uiEntry.summary).isEqualTo("Summary")
        assertThat(uiEntry.severityLevel)
            .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
        assertThat(uiEntry.safetySourceId).isEqualTo("source_id")
        assertThat(uiEntry.user).isEqualTo(user0)
        assertThat(uiEntry.pendingIntent).isSameInstanceAs(pendingIntent)
    }

    /**
     * Verifies that all fields from a [SafetyCenterStaticEntry] are correctly mapped, with
     * severity level set to UNSPECIFIED.
     */
    @Test
    fun fromSafetyCenterStaticEntry_mapsAllFieldsCorrectly() {
        // Setup: Create a detailed SafetyCenterStaticEntry
        val intent = Intent("FAKE_ACTION")
        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val staticEntry =
            createStaticEntry(
                user = user0,
                sourceId = "source_id",
                title = "Title",
                summary = "Summary",
                pendingIntent = pendingIntent,
            )

        // Action: Convert to SafetyCenterUiEntry
        val uiEntry = SafetyCenterUiEntry.fromSafetyCenterStaticEntry(staticEntry)

        // Assertions: Verify all fields are mapped correctly
        assertThat(uiEntry.title).isEqualTo("Title")
        assertThat(uiEntry.summary).isEqualTo("Summary")
        // Static entries should always have an UNSPECIFIED severity level
        assertThat(uiEntry.severityLevel)
            .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
        assertThat(uiEntry.safetySourceId).isEqualTo("source_id")
        assertThat(uiEntry.user).isEqualTo(user0)
        assertThat(uiEntry.pendingIntent).isSameInstanceAs(pendingIntent)
    }
}
