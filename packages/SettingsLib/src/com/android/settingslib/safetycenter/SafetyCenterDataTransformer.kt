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

import android.permission.flags.Flags
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterIssue

/**
 * A transformer object responsible for converting the raw [SafetyCenterData] provided by the system
 * into a [SafetyCenterUiData] structure more suitable for the Safety Center UI in Settings.
 */
object SafetyCenterDataTransformer {

    fun transform(data: SafetyCenterData): SafetyCenterUiData {
        if (!Flags.openSafetyCenterApis()) {
            return SafetyCenterUiData(
                status = data.status,
                entriesByUserIdAndSourceId = emptyMap(),
                activeIssuesBySourceId = emptyMap(),
                dismissedIssuesBySourceId = emptyMap(),
            )
        }

        val entriesByUserIdAndSourceId =
            data.entriesOrGroups
                .flatMap { it.entryGroup?.entries ?: listOfNotNull(it.entry) }
                .filter { it.user != null && it.safetySourceId != null }
                .groupBy { it.user!!.identifier }
                .mapValues { (_, entries) ->
                    entries.associateBy { it.safetySourceId!! }
                }

        val activeIssuesBySourceId = processIssues(data.issues)
        val dismissedIssuesBySourceId = processIssues(data.dismissedIssues)

        return SafetyCenterUiData(
            status = data.status,
            entriesByUserIdAndSourceId = entriesByUserIdAndSourceId,
            activeIssuesBySourceId = activeIssuesBySourceId,
            dismissedIssuesBySourceId = dismissedIssuesBySourceId,
        )
    }

    private fun processIssues(
        issues: List<SafetyCenterIssue>,
    ): Map<String, List<SafetyCenterIssue>> {
        return issues
            .flatMap { issue -> issue.safetySourceIds.map { sourceId -> sourceId to issue } }
            .groupBy { it.first }
            .mapValues { (_, pairs) -> pairs.map { it.second }.distinct() }
    }
}
