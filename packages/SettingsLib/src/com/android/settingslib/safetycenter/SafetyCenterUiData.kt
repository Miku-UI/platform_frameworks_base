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
import android.os.UserHandle
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStatus

/**
 * Container for the transformed SafetyCenterData.
 *
 * @property status The overall status of the Safety Center.
 * @property entriesByUserIdAndSourceId A map where the key is the user ID (Int) and the value is
 *   another map, keyed by safety source ID (String) to the SafetyCenterEntry.
 * @property staticEntriesByUserIdAndSourceId A map for static entries, structured like
 *   entriesByUserIdAndSourceId.
 * @property activeIssuesBySourceId A map grouping lists of active SafetyCenterIssue objects by
 *   their safety source ID.
 * @property dismissedIssuesBySourceId A map grouping lists of dismissed SafetyCenterIssue objects
 *   by their safety source ID.
 * @property resolvedIssues A map of issue IDs to action IDs for resolved actions.
 */
data class SafetyCenterUiData(
    val status: SafetyCenterStatus,
    val entriesByUserIdAndSourceId: Map<Int, Map<String, SafetyCenterEntry>>,
    val staticEntriesByUserIdAndSourceId: Map<Int, Map<String, SafetyCenterStaticEntry>>,
    val activeIssuesBySourceId: Map<String, List<SafetyCenterIssue>>,
    val dismissedIssuesBySourceId: Map<String, List<SafetyCenterIssue>>,
    val resolvedIssues: Map<String, String> = emptyMap(),
) {

    /** Creates a copy of this [SafetyCenterUiData] with the provided [resolvedIssues]. */
    fun copyWithResolvedIssues(resolvedIssues: Map<String, String>): SafetyCenterUiData {
        return this.copy(resolvedIssues = resolvedIssues)
    }

    /**
     * Retrieves a specific SafetyCenterUiEntry for a given user and source ID. It first checks
     * dynamic entries and then falls back to static entries.
     *
     * @param userId The ID of the user.
     * @param sourceId The safety source ID of the entry.
     * @return The matching SafetyCenterUiEntry, or null if not found in dynamic or static entries.
     */
    fun getEntry(userId: Int, sourceId: String): SafetyCenterUiEntry? {
        return entriesByUserIdAndSourceId[userId]?.get(sourceId)?.let {
            SafetyCenterUiEntry.fromSafetyCenterEntry(it)
        }
            ?: staticEntriesByUserIdAndSourceId[userId]?.get(sourceId)?.let {
                SafetyCenterUiEntry.fromSafetyCenterStaticEntry(it)
            }
    }

    /**
     * Retrieves all dynamic entries across all profiles that are associated with any of the
     * provided safety source IDs.
     *
     * @param sourceIds A list defining the safety source IDs to filter by.
     * @return A list of matching SafetyCenterEntry objects.
     */
    fun getDynamicEntriesForSources(sourceIds: List<String>): List<SafetyCenterEntry> {
        return entriesByUserIdAndSourceId.values
            .flatMap { sourceIdToEntryMap -> sourceIdToEntryMap.values }
            .filter { entry -> sourceIds.contains(entry.safetySourceId) }
    }

    /**
     * Retrieves all active issues across all profiles, sorted by severity and source ID order.
     *
     * @param sourceIdsOrder An optional list defining the preferred order of safety source IDs for
     *   secondary sorting.
     * @return A sorted list of all active SafetyCenterIssue objects.
     */
    fun getActiveIssues(sourceIdsOrder: List<String> = emptyList()): List<SafetyCenterIssue> {
        val allIssues = activeIssuesBySourceId.values.flatten().distinct()
        return allIssues.sortedWith(issueComparator(sourceIdsOrder))
    }

    /**
     * Retrieves active issues across all profiles that are associated with any of the provided
     * safety source IDs. The results are sorted by severity and the provided source ID order.
     *
     * @param sourceIdsOrder A list defining the safety source IDs to filter by and their preferred
     *   order for secondary sorting.
     * @return A sorted list of matching active SafetyCenterIssue objects.
     */
    fun getActiveIssuesForSources(sourceIdsOrder: List<String>): List<SafetyCenterIssue> {
        val relevantIssues =
            sourceIdsOrder
                .flatMap { sourceId -> activeIssuesBySourceId[sourceId] ?: emptyList() }
                .distinct()
        return relevantIssues.sortedWith(issueComparator(sourceIdsOrder))
    }

    /**
     * Retrieves all dismissed issues across all profiles, sorted by severity and source ID order.
     *
     * @param sourceIdsOrder An optional list defining the preferred order of safety source IDs for
     *   secondary sorting.
     * @return A sorted list of all dismissed [SafetyCenterIssue] objects.
     */
    fun getDismissedIssues(sourceIdsOrder: List<String> = emptyList()): List<SafetyCenterIssue> {
        val allDismissedIssues = dismissedIssuesBySourceId.values.flatten().distinct()
        return allDismissedIssues.sortedWith(issueComparator(sourceIdsOrder))
    }

    /**
     * Retrieves dismissed issues across all profiles that are associated with any of the provided
     * safety source IDs. The results are sorted by severity and the provided source ID order.
     *
     * @param sourceIdsOrder A list defining the safety source IDs to filter by and their preferred
     *   order for secondary sorting.
     * @return A sorted list of matching dismissed SafetyCenterIssue objects.
     */
    fun getDismissedIssuesForSources(sourceIdsOrder: List<String>): List<SafetyCenterIssue> {
        val relevantIssues =
            sourceIdsOrder
                .flatMap { sourceId -> dismissedIssuesBySourceId[sourceId] ?: emptyList() }
                .filter { it.severityLevel > ISSUE_SEVERITY_LEVEL_OK }
                .distinct()
        return relevantIssues.sortedWith(issueComparator(sourceIdsOrder))
    }

    companion object {
        private val SEVERITY_ORDER =
            mapOf(
                SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING to 0,
                SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION to 1,
                SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK to 2,
            )

        /**
         * Comparator for sorting SafetyCenterIssue objects. Issues are sorted primarily by severity
         * (Critical > Recommendation > OK). Secondary sorting is based on the order of safety
         * source IDs provided in `sourceIdsOrder`. An issue's position in the secondary sort is
         * determined by the lowest index of any of its associated safetySourceIds within the
         * `sourceIdsOrder` list. This comparator provides a stable sort.
         */
        private fun issueComparator(sourceIdsOrder: List<String>): Comparator<SafetyCenterIssue> {
            return compareBy<SafetyCenterIssue> {
                    SEVERITY_ORDER[it.severityLevel] ?: Int.MAX_VALUE
                }
                .thenBy { issue ->
                    issue.safetySourceIds.minOfOrNull { sourceId ->
                        sourceIdsOrder.indexOf(sourceId).let { index ->
                            if (index == -1) Int.MAX_VALUE else index
                        }
                    } ?: Int.MAX_VALUE
                }
        }
    }
}

/**
 * Represents a unified view of a safety entry, abstracting away whether it came from a dynamic or
 * static source.
 *
 * @property title The title of the entry.
 * @property summary The summary text of the entry.
 * @property severityLevel The severity level of the entry, based on
 *   [SafetyCenterEntry.EntrySeverityLevel].
 * @property safetySourceId The ID of the safety source that provided the entry.
 * @property user The Android [UserHandle] the entry belongs to.
 * @property pendingIntent The [PendingIntent] to fire when the entry is clicked.
 */
data class SafetyCenterUiEntry(
    val title: CharSequence,
    val summary: CharSequence?,
    val severityLevel: Int,
    val safetySourceId: String?,
    val user: UserHandle?,
    val pendingIntent: PendingIntent?,
) {
    companion object {
        /** Creates a [SafetyCenterUiEntry] from a [SafetyCenterEntry]. */
        fun fromSafetyCenterEntry(entry: SafetyCenterEntry): SafetyCenterUiEntry {
            return SafetyCenterUiEntry(
                title = entry.title,
                summary = entry.summary,
                severityLevel = entry.severityLevel,
                safetySourceId = entry.safetySourceId,
                user = entry.user,
                pendingIntent = entry.pendingIntent,
            )
        }

        /** Creates a [SafetyCenterUiEntry] from a [SafetyCenterStaticEntry]. */
        fun fromSafetyCenterStaticEntry(entry: SafetyCenterStaticEntry): SafetyCenterUiEntry {
            return SafetyCenterUiEntry(
                title = entry.title,
                summary = entry.summary,
                // Set the severity level to UNSPECIFIED for static entries
                severityLevel = ENTRY_SEVERITY_LEVEL_UNSPECIFIED,
                safetySourceId = entry.safetySourceId,
                user = entry.user,
                pendingIntent = entry.pendingIntent,
            )
        }
    }
}
