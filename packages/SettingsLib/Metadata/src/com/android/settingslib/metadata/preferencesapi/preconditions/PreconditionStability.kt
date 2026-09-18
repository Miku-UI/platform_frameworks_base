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

package com.android.settingslib.metadata.preferencesapi.preconditions

/**
 * Stability of the precondition state.
 *
 * This allows us to inform clients how frequently they should check
 * precondition state. It is acceptable (but suboptimal) to report a
 * precondition as being LESS stable than it actually is, but never more stable.
 */
enum class PreconditionStability {
    /** The precondition state will not change until the APK is updated. */
    STABLE_UNTIL_APK_UPDATE,
    /** The precondition state can change at any time. */
    UNSTABLE
}
