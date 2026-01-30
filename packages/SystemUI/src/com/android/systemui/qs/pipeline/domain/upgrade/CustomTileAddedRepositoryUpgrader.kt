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

import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.pipeline.data.repository.CustomTileAddedRepository
import com.android.systemui.qs.pipeline.shared.logging.QSPipelineLogger
import com.android.systemui.util.kotlin.getOrNull
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Applies upgrade steps to [CustomTileAddedRepository] database.
 *
 * The upgrades are provided by a set of [CustomTileAddedUpgrade]. The versions in this set should
 * be unique elements in `2..N`, and if an upgrade path is missing this class will throw an error
 * during initialization.
 *
 * For each user, when it's started, the upgrades will be applied in order, starting with the lowest
 * unapplied for that user. If an upgrade cannot be applied (throws an exception), the upgrade
 * process for that user will be stopped, and it will be tried again when the user is started again.
 */
@SysUISingleton
class CustomTileAddedRepositoryUpgrader
@Inject
constructor(
    private val customTileAddedRepository: CustomTileAddedRepository,
    allUpgrades: Set<@JvmSuppressWildcards Optional<CustomTileAddedUpgrade>>,
    @Background private val backgroundScope: CoroutineScope,
    private val qsPipelineLogger: QSPipelineLogger,
) {
    private val sortedUpgrades = allUpgrades.mapNotNull { it.getOrNull() }.sortedBy { it.version }

    init {
        if (sortedUpgrades.isNotEmpty()) {
            check(
                sortedUpgrades.map { it.version } == (2..(sortedUpgrades.last().version)).toList()
            ) {
                "Upgrade versions must all be different and sequential 2..N. Found: ${
                    sortedUpgrades.joinToString(
                        ","
                    ) { it.describe() }
                }"
            }
        }
        qsPipelineLogger.logCustomTileAddedRepositoryUpgradeList(sortedUpgrades)
    }

    fun start(userFlow: Flow<Int>) {
        backgroundScope.launch { userFlow.collect(::upgradeUser) }
    }

    private suspend fun upgradeUser(userId: Int) {
        var succeeding = true
        // Continuously check the version from the repository and run the next available upgrade.
        while (succeeding) {
            val currentVersion = customTileAddedRepository.getVersion(userId)
            val nextUpgrade = sortedUpgrades.firstOrNull { it.version > currentVersion }
            // If there's no upgrade with a version higher than current, we're done.
            if (nextUpgrade == null) {
                break
            }
            // Perform the next upgrade and update the version in storage.
            with(nextUpgrade) {
                try {
                    qsPipelineLogger.logCustomTileAddedRepositoryUpgradeStarted(
                        nextUpgrade.version,
                        userId,
                    )
                    customTileAddedRepository.upgradeForUser(userId)
                    customTileAddedRepository.setVersion(
                        userId = userId,
                        version = nextUpgrade.version,
                    )
                    qsPipelineLogger.logCustomTileAddedRepositoryUpgradeFinished(
                        nextUpgrade.version,
                        userId,
                    )
                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Failed to perform upgrade ${nextUpgrade.describe()} for user $userId",
                        e,
                    )
                    qsPipelineLogger.logCustomTileAddedRepositoryUpgradeError(nextUpgrade, userId)
                    succeeding = false
                }
            }
        }
    }

    private companion object {
        const val TAG = QSPipelineLogger.CUSTOM_TILE_REPOSITORY_UPGRADE_TAG
    }
}
