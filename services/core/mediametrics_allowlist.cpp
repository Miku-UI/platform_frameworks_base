/*
 * Copyright 2026 The Android Open Source Project
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

#include "mediametrics_allowlist.h"

#include <unordered_set>

namespace android::mediametrics::audio {

// Correct, this is a Java file but it is also a valid C++ code.
#include "java/com/android/server/media/metrics/Allowlist.java"

const char* getFilteredAudioMediaType(std::string_view key) {
    static const std::unordered_set<std::string_view> lookupTable(
            std::begin(Allowlist::AUDIO_MEDIA_TYPES), std::end(Allowlist::AUDIO_MEDIA_TYPES));
    if (auto it = lookupTable.find(key); it != lookupTable.end()) {
        return it->data();
    }
    return "";
}

}  // namespace android::mediametrics::audio
