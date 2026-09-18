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

package com.android.protolog

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.google.auto.service.AutoService

@AutoService(IssueRegistry::class)
class ProtoLogIssueRegistry : IssueRegistry() {
    override val issues =
        listOf(
            ProtoLogFormatDetector.ISSUE_INVALID_FORMAT_SPECIFIER,
            ProtoLogFormatDetector.ISSUE_NON_CONSTANT_FORMAT,
            ProtoLogFormatDetector.ISSUE_ARG_COUNT,
            ProtoLogFormatDetector.ISSUE_ARG_TYPE,
            ProtoLogFormatDetector.ISSUE_TOO_MANY_ARGS,
        )

    override val api
        get() = CURRENT_API
}
