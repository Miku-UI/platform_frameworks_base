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

import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStatus

/**
 * Container for the transformed SafetyCenterData.
 *
 * @property status The overall status of the Safety Center.
 * @property entriesByUserIdAndSourceId A map where the key is the user ID (Int) and the value is
 *     another map, keyed by safety source ID (String) to the SafetyCenterEntry.
 * @property activeIssuesBySourceId A map grouping lists of active SafetyCenterIssue
 *     objects by their safety source ID.
 * @property dismissedIssuesBySourceId A map grouping lists of dismissed SafetyCenterIssue
 *     objects by their safety source ID.
 */
data class SafetyCenterUiData(
    val status: SafetyCenterStatus,
    val entriesByUserIdAndSourceId: Map<Int, Map<String, SafetyCenterEntry>>,
    val activeIssuesBySourceId: Map<String, List<SafetyCenterIssue>>,
    val dismissedIssuesBySourceId: Map<String, List<SafetyCenterIssue>>,
) {

    /**
     * Retrieves a specific SafetyCenterEntry for a given user and source ID.
     *
     * @param userId The ID of the user.
     * @param sourceId The safety source ID of the entry.
     * @return The matching SafetyCenterEntry, or null if not found.
     */
    fun getEntry(userId: Int, sourceId: String): SafetyCenterEntry? {
        return entriesByUserIdAndSourceId[userId]?.get(sourceId)
    }

    /**
     * Retrieves all active issues across all profiles, sorted by severity and source ID order.
     *
     * @param sourceIdsOrder An optional list defining the preferred order
     *      of safety source IDs for secondary sorting.
     * @return A sorted list of all active SafetyCenterIssue objects.
     */
    fun getActiveIssues(
        sourceIdsOrder: List<String> = emptyList(),
    ): List<SafetyCenterIssue> {
        val allIssues = activeIssuesBySourceId.values.flatten().distinct()
        return allIssues.sortedWith(issueComparator(sourceIdsOrder))
    }

    /**
     * Retrieves active issues across all profiles that are associated with
     * any of the provided safety source IDs.
     * The results are sorted by severity and the provided source ID order.
     *
     * @param sourceIdsOrder A list defining the safety source IDs to filter
     *      by and their preferred order for secondary sorting.
     * @return A sorted list of matching active SafetyCenterIssue objects.
     */
    fun getActiveIssuesForSources(
        sourceIdsOrder: List<String>,
    ): List<SafetyCenterIssue> {
        val relevantIssues =
            sourceIdsOrder
                .flatMap { sourceId -> activeIssuesBySourceId[sourceId] ?: emptyList() }
                .distinct()
        return relevantIssues.sortedWith(issueComparator(sourceIdsOrder))
    }

    /**
     * Retrieves dismissed issues across all profiles that are associated with
     * any of the provided safety source IDs.
     * The results are sorted by severity and the provided source ID order.
     *
     * @param sourceIdsOrder A list defining the safety source IDs to filter
     *      by and their preferred order for secondary sorting.
     * @return A sorted list of matching dismissed SafetyCenterIssue objects.
     */
    fun getDismissedIssuesForSources(
        sourceIdsOrder: List<String>,
    ): List<SafetyCenterIssue> {
        val relevantIssues =
            sourceIdsOrder
                .flatMap { sourceId -> dismissedIssuesBySourceId[sourceId] ?: emptyList() }
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
         * Comparator for sorting SafetyCenterIssue objects.
         * Issues are sorted primarily by severity (Critical > Recommendation > OK).
         * Secondary sorting is based on the order of safety source IDs
         * provided in `sourceIdsOrder`.
         * An issue's position in the secondary sort is determined by the lowest index
         * of any of its associated safetySourceIds within the `sourceIdsOrder` list.
         * This comparator provides a stable sort.
         */
        private fun issueComparator(sourceIdsOrder: List<String>): Comparator<SafetyCenterIssue> {
            return compareBy<SafetyCenterIssue> {
                SEVERITY_ORDER[it.severityLevel] ?: Int.MAX_VALUE
            }
                .thenBy { issue ->
                    issue.safetySourceIds
                        .minOfOrNull { sourceId ->
                            sourceIdsOrder.indexOf(sourceId).let { index ->
                                if (index == -1) Int.MAX_VALUE else index
                            }
                        }
                        ?: Int.MAX_VALUE
                }
        }
    }
}
