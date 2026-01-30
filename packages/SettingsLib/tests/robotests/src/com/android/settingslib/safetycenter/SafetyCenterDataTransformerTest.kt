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
import android.permission.flags.Flags
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafetyCenterDataTransformerTest {

    @get:Rule
    val setFlagsRule = SetFlagsRule()

    private val user0 = UserHandle.of(0)
    private val user10 = UserHandle.of(10)

    private val defaultStatus =
        SafetyCenterStatus.Builder("Default Title", "Default Summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private fun createIssue(
        id: String,
        user: UserHandle,
        sourceIds: Set<String>,
        title: String = "Issue $id",
    ): SafetyCenterIssue {
        return SafetyCenterIssue.Builder(id, title, "Summary $id", user, sourceIds, "type_$id")
            .build()
    }

    private fun createEntry(
        id: String,
        user: UserHandle,
        sourceId: String,
        title: String = "Entry $id",
    ): SafetyCenterEntry {
        return SafetyCenterEntry.Builder(id, title, user, sourceId).build()
    }

    @Test
    @DisableFlags(Flags.FLAG_OPEN_SAFETY_CENTER_APIS)
    fun transform_flagDisabled_returnsEmptyData() {
        val entry1 = createEntry("entry1", user0, "sourceA")
        val issue1 = createIssue("issue1", user0, setOf("sourceA"))
        val scData =
            SafetyCenterData.Builder(defaultStatus)
                .addEntryOrGroup(SafetyCenterEntryOrGroup(entry1))
                .addIssue(issue1)
                .build()

        val uiData = SafetyCenterDataTransformer.transform(scData)

        assertThat(uiData.status).isEqualTo(defaultStatus)
        assertThat(uiData.entriesByUserIdAndSourceId).isEmpty()
        assertThat(uiData.activeIssuesBySourceId).isEmpty()
        assertThat(uiData.dismissedIssuesBySourceId).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_OPEN_SAFETY_CENTER_APIS)
    fun transform_emptyData_flagEnabled_returnsEmptyUiDataWithStatus() {
        val emptyData = SafetyCenterData.Builder(defaultStatus).build()

        val uiData = SafetyCenterDataTransformer.transform(emptyData)

        assertThat(uiData.status).isEqualTo(defaultStatus)
        assertThat(uiData.entriesByUserIdAndSourceId).isEmpty()
        assertThat(uiData.activeIssuesBySourceId).isEmpty()
        assertThat(uiData.dismissedIssuesBySourceId).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_OPEN_SAFETY_CENTER_APIS)
    fun transform_withEntriesOnly_flagEnabled_mapsCorrectly() {
        val entry1 = createEntry("entry1", user0, "sourceA")
        val entry2 = createEntry("entry2", user10, "sourceB")
        val entry3 = createEntry("entry3", user0, "sourceC")

        val scData =
            SafetyCenterData.Builder(defaultStatus)
                .addEntryOrGroup(SafetyCenterEntryOrGroup(entry1))
                .addEntryOrGroup(SafetyCenterEntryOrGroup(entry2))
                .addEntryOrGroup(SafetyCenterEntryOrGroup(entry3))
                .build()

        val uiData = SafetyCenterDataTransformer.transform(scData)

        assertThat(uiData.entriesByUserIdAndSourceId).hasSize(2)
        assertThat(uiData.entriesByUserIdAndSourceId[0]).containsExactly(
            "sourceA",
            entry1,
            "sourceC",
            entry3
        )
        assertThat(uiData.entriesByUserIdAndSourceId[10]).containsExactly("sourceB", entry2)
        assertThat(uiData.activeIssuesBySourceId).isEmpty()
        assertThat(uiData.dismissedIssuesBySourceId).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_OPEN_SAFETY_CENTER_APIS)
    fun transform_withEntryGroup_flagEnabled_flattensEntries() {
        val entry1 = createEntry("entry1", user0, "sourceA")
        val entry2 = createEntry("entry2", user0, "sourceB")

        val entryGroup =
            SafetyCenterEntryGroup.Builder("group1", "Group 1 Title")
                .setEntries(listOf(entry1, entry2))
                .build()

        val scData =
            SafetyCenterData.Builder(defaultStatus)
                .addEntryOrGroup(SafetyCenterEntryOrGroup(entryGroup))
                .build()

        val uiData = SafetyCenterDataTransformer.transform(scData)

        assertThat(uiData.entriesByUserIdAndSourceId).hasSize(1)
        assertThat(uiData.entriesByUserIdAndSourceId[0]).containsExactly(
            "sourceA",
            entry1,
            "sourceB",
            entry2
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_OPEN_SAFETY_CENTER_APIS)
    fun transform_withIssuesOnly_flagEnabled_mapsCorrectly() {
        val issue1 = createIssue("issue1", user0, setOf("sourceA"))
        val issue2 = createIssue("issue2", user10, setOf("sourceB"))
        val issue3 = createIssue("issue3", user0, setOf("sourceC"))

        val scData =
            SafetyCenterData.Builder(defaultStatus)
                .addIssue(issue1)
                .addDismissedIssue(issue2)
                .addIssue(issue3)
                .build()

        val uiData = SafetyCenterDataTransformer.transform(scData)

        assertThat(uiData.entriesByUserIdAndSourceId).isEmpty()
        assertThat(uiData.activeIssuesBySourceId).containsExactly(
            "sourceA",
            listOf(issue1),
            "sourceC",
            listOf(issue3)
        )
        assertThat(uiData.dismissedIssuesBySourceId).containsExactly("sourceB", listOf(issue2))
    }

    @Test
    @EnableFlags(Flags.FLAG_OPEN_SAFETY_CENTER_APIS)
    fun transform_issueWithMultipleSources_flagEnabled_appearsUnderEachSource() {
        val issueMulti = createIssue("issueMulti", user0, setOf("sourceA", "sourceB", "sourceC"))

        val scData = SafetyCenterData.Builder(defaultStatus).addIssue(issueMulti).build()

        val uiData = SafetyCenterDataTransformer.transform(scData)

        assertThat(uiData.activeIssuesBySourceId).hasSize(3)
        assertThat(uiData.activeIssuesBySourceId["sourceA"]).containsExactly(issueMulti)
        assertThat(uiData.activeIssuesBySourceId["sourceB"]).containsExactly(issueMulti)
        assertThat(uiData.activeIssuesBySourceId["sourceC"]).containsExactly(issueMulti)
    }

    @Test
    @EnableFlags(Flags.FLAG_OPEN_SAFETY_CENTER_APIS)
    fun transform_duplicateIssues_flagEnabled_areDistinctInOutput() {
        val issue1 = createIssue("issue1", user0, setOf("sourceA"))
        val issue1_dupe = createIssue("issue1", user0, setOf("sourceA"))
        val issue2 = createIssue("issue2", user0, setOf("sourceA"))

        val scData =
            SafetyCenterData.Builder(defaultStatus)
                .addIssue(issue1)
                .addIssue(issue1_dupe)
                .addIssue(issue2)
                .build()

        val uiData = SafetyCenterDataTransformer.transform(scData)

        assertThat(uiData.activeIssuesBySourceId["sourceA"]).containsExactly(issue1, issue2)
    }

    @Test
    @EnableFlags(Flags.FLAG_OPEN_SAFETY_CENTER_APIS)
    fun transform_mixedData_multipleUsers_flagEnabled() {
        val entryU0 = createEntry("entryU0", user0, "sourceA")
        val issueU0_active = createIssue("issueU0A", user0, setOf("sourceA", "sourceB"))
        val issueU0_dismissed = createIssue("issueU0D", user0, setOf("sourceC"))

        val entryU10 = createEntry("entryU10", user10, "sourceX")
        val issueU10_active = createIssue("issueU10A", user10, setOf("sourceX"))

        val scData =
            SafetyCenterData.Builder(defaultStatus)
                .addEntryOrGroup(SafetyCenterEntryOrGroup(entryU0))
                .addIssue(issueU0_active)
                .addDismissedIssue(issueU0_dismissed)
                .addEntryOrGroup(SafetyCenterEntryOrGroup(entryU10))
                .addIssue(issueU10_active)
                .build()

        val uiData = SafetyCenterDataTransformer.transform(scData)

        assertThat(uiData.entriesByUserIdAndSourceId).hasSize(2)
        assertThat(uiData.entriesByUserIdAndSourceId[0]).containsExactly("sourceA", entryU0)
        assertThat(uiData.entriesByUserIdAndSourceId[10]).containsExactly("sourceX", entryU10)

        assertThat(uiData.activeIssuesBySourceId).containsExactly(
            "sourceA", listOf(issueU0_active),
            "sourceB", listOf(issueU0_active),
            "sourceX", listOf(issueU10_active)
        )

        assertThat(uiData.dismissedIssuesBySourceId).containsExactly(
            "sourceC",
            listOf(issueU0_dismissed)
        )
    }
}
