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

/*
 * NOTE: This is a "polyglot" file which is a valid C++ and Java source at
 * the same time. Please be careful when making edits.
 */

// \
/*
// ============================================
// C++ PREPROCESSOR CONFIGURATION FOR REWRITING
// ============================================
#define public
#define class namespace
// We keep 'static' to give the array internal linkage (hidden from linker)
// We map 'final' to 'constexpr' for compile-time validity
#define final constexpr
#define String const char*
// \
*/

// \
package com.android.server.media.metrics;

class Allowlist {
    // ==========================================
    // SHARED DATA SECTION
    // The list below can be used both by C++ and Java code.
    // ==========================================
    public static final String AUDIO_MEDIA_TYPES[] = {
        // go/keep-sorted start
        "audio/ac3",
        "audio/ac4",
        "audio/av4",
        "audio/eac3",
        "audio/eac3-joc",
        "audio/flac",
        "audio/iamf",
        "audio/iamf.base.aac",
        "audio/iamf.base.flac",
        "audio/iamf.base.opus",
        "audio/iamf.base.pcm",
        "audio/iamf.base_enhanced.aac",
        "audio/iamf.base_enhanced.flac",
        "audio/iamf.base_enhanced.opus",
        "audio/iamf.base_enhanced.pcm",
        "audio/iamf.simple.aac",
        "audio/iamf.simple.flac",
        "audio/iamf.simple.opus",
        "audio/iamf.simple.pcm",
        "audio/mhm1",
        "audio/mhm1.03",
        "audio/mhm1.04",
        "audio/mhm1.0d",
        "audio/mhm1.0e",
        "audio/midi",
        "audio/mp4",
        "audio/mp4a-latm",
        "audio/mp4a.40.02",
        "audio/mp4a.40.05",
        "audio/mp4a.40.29",
        "audio/mp4a.40.39",
        "audio/mp4a.40.42",
        "audio/mpeg",
        "audio/mpeg-L1",
        "audio/mpeg-L2",
        "audio/ogg",
        "audio/opus",
        "audio/raw",
        "audio/true-hd",
        "audio/vnd.dolby.mat",
        "audio/vnd.dolby.mlp",
        "audio/vnd.dra",
        "audio/vnd.dts",
        "audio/vnd.dts.hd",
        "audio/vnd.dts.uhd",
        "audio/vorbis",
        "audio/wav",
        "audio/webm",
        "audio/x-iec61937",
        "audio/x-matroska",
        // go/keep-sorted end
    };
}
