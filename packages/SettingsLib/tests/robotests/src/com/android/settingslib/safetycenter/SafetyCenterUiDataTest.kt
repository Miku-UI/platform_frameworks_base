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

import android.os.UserHandle
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafetyCenterUiDataTest {

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
        return SafetyCenterIssue.Builder(id, title, "Summary $id",
            user, sourceIds, "type_$id")
            .setSeverityLevel(severity)
            .build()
    }

    // Helper to create a minimal SafetyCenterEntry
    private fun createEntry(
        id: String,
        user: UserHandle,
        sourceId: String,
        title: String = "Entry $id",
    ): SafetyCenterEntry {
        return SafetyCenterEntry.Builder(id, title,
            user, sourceId).build()
    }

    private val entry1 = createEntry("entry1", user0, "sourceA")
    private val entry2 = createEntry("entry2", user0, "sourceB")
    private val entry3 = createEntry("entry3", user10, "sourceA")

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
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION
        )
    private val issue5 =
        createIssue(
            "issue5",
            user10,
            setOf("s1"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
        )
    private val dismissedIssue =
        createIssue(
            "issue6",
            user0,
            setOf("s4"),
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
        )

    private val testSafetyCenterUiData =
        SafetyCenterUiData(
            status = defaultStatus,
            entriesByUserIdAndSourceId =
                mapOf(
                    0 to mapOf("sourceA" to entry1, "sourceB" to entry2),
                    10 to mapOf("sourceA" to entry3),
                ),
            activeIssuesBySourceId =
                mapOf(
                    "s1" to listOf(issue1, issue3, issue5),
                    "s2" to listOf(issue2, issue3),
                    "s3" to listOf(issue4),
                ),
            dismissedIssuesBySourceId = mapOf("s4" to listOf(dismissedIssue)),
        )

    @Test
    fun getEntry_entryExists_returnsEntry() {
        assertThat(testSafetyCenterUiData.getEntry(0, "sourceA"))
            .isEqualTo(entry1)
        assertThat(testSafetyCenterUiData.getEntry(0, "sourceB"))
            .isEqualTo(entry2)
        assertThat(testSafetyCenterUiData.getEntry(10, "sourceA"))
            .isEqualTo(entry3)
    }

    @Test
    fun getEntry_entryNotExists_returnsNull() {
        assertThat(testSafetyCenterUiData.getEntry(0, "sourceX")).isNull()
        assertThat(testSafetyCenterUiData.getEntry(10, "sourceB")).isNull()
        assertThat(testSafetyCenterUiData.getEntry(99, "sourceA")).isNull()
    }

    @Test
    fun getActiveIssues_noOrder_sortedBySeverity() {
        val issues = testSafetyCenterUiData.getActiveIssues()
        assertThat(issues)
            .containsExactly(
                issue1,
                issue2,
                issue3,
                issue4,
                issue5,
            )
            .inOrder()
    }

    @Test
    fun getActiveIssues_withOrder_sortedBySeverityAndSourceOrder() {
        val sourceOrder = listOf("s3", "s2", "s1")
        val issues = testSafetyCenterUiData.getActiveIssues(sourceOrder)
        assertThat(issues)
            .containsExactly(
                issue2,
                issue1,
                issue4,
                issue3,
                issue5,
            )
            .inOrder()
    }

    @Test
    fun getActiveIssuesForSources_filtersAndSorts() {
        val sourceOrder = listOf("s3", "s1")
        val issues = testSafetyCenterUiData.getActiveIssuesForSources(sourceOrder)
        assertThat(issues)
            .containsExactly(
                issue1,
                issue4,
                issue3,
                issue5,
            )
            .inOrder()
    }

    @Test
    fun getActiveIssuesForSources_notFound_returnsEmpty() {
        val issues = testSafetyCenterUiData.getActiveIssuesForSources(listOf("nonExistent"))
        assertThat(issues).isEmpty()
    }

    @Test
    fun getDismissedIssuesForSources_filters() {
        val issues = testSafetyCenterUiData.getDismissedIssuesForSources(listOf("s4"))
        assertThat(issues).containsExactly(dismissedIssue)
    }

    @Test
    fun getDismissedIssuesForSources_notFound_returnsEmpty() {
        val issues = testSafetyCenterUiData.getDismissedIssuesForSources(listOf("s1"))
        assertThat(issues).isEmpty()
    }
}
