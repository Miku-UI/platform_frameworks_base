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

#pragma once

struct PipelineCacheStats {
    bool inUse = false;

    size_t sizeBytes = 0;

    // Occurrences of undiagnosed errors, which may be caused by system health issues or code bugs
    uint64_t fileOpenAndTruncateFailedCount = 0;
    uint64_t fileWriteFailedCount = 0;
    uint64_t zeroByteWriteCount = 0;
    uint64_t partialWriteCount = 0;
};
