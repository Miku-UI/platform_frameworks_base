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

package com.android.systemui.qs.pipeline.domain.upgrade

import com.android.systemui.qs.pipeline.data.repository.CustomTileAddedRepository

/** Upgrade path for [CustomTileAddedRepository]. */
interface CustomTileAddedUpgrade {
    /**
     * The version to upgrade to. These should be unique among the elements bound into the set.
     *
     * @see CustomTileAddedRepositoryUpgrader
     */
    val version: Int

    /**
     * Upgrade instructions for a particular user. The assumption should be that the state of the
     * repository is that at [version] - 1.
     */
    suspend fun CustomTileAddedRepository.upgradeForUser(userId: Int)
}

/** User readable description. */
fun CustomTileAddedUpgrade.describe() = "${this::class.java.name}(version=$version)"
