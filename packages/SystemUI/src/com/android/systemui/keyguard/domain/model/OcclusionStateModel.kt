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

package com.android.systemui.keyguard.domain.model

import com.android.systemui.log.table.EnumDiffable

/** The unified semantic state of occlusion. */
enum class OcclusionStateModel : EnumDiffable<OcclusionStateModel> {
    /** The keyguard is not occluded by anything. */
    NONE,

    /** The keyguard is occluded by a Dream (ACTIVITY_TYPE_DREAM). */
    DREAM,

    /** The keyguard is occluded by a standard ShowWhenLocked app (e.g., Camera). */
    APP,

    /**
     * LEGACY: The keyguard is occluded, but we don't know/care by what. This is used when the
     * drive_dream_state_from_occlusion flag is disabled.
     */
    LEGACY_OCCLUDED_GENERIC;

    override val columnName = "occlusionState"
    override val valueFetcher = { name }
}
