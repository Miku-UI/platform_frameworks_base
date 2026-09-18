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

package com.android.systemui.qs.pipeline.data.startable

import android.os.UserHandle
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.CoreStartable
import com.android.systemui.common.data.repository.PackageChangeRepository
import com.android.systemui.common.shared.model.PackageChangeModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.external.CustomTileStatePersister
import com.android.systemui.qs.pipeline.data.repository.CustomTileAddedRepository
import com.android.systemui.qs.pipeline.data.repository.TileSpecRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance

@SysUISingleton
class PackageUninstalledCoreStartable
@Inject
constructor(
    private val tileSpecRepository: TileSpecRepository,
    private val customTileStatePersister: CustomTileStatePersister,
    private val customTileAddedRepository: CustomTileAddedRepository,
    private val packageChangeRepository: PackageChangeRepository,
    @param:Background private val backgroundApplicationScope: CoroutineScope,
) : CoreStartable {
    override fun start() {
        backgroundApplicationScope.launchTraced("PackageUninstalledCoreStartable") {
            packageChangeRepository
                .packageChanged(UserHandle.ALL)
                .filterIsInstance<PackageChangeModel.Uninstalled>()
                .collect {
                    val packageName = it.packageName
                    val userId = UserHandle.getUserId(it.packageUid)
                    tileSpecRepository.removePackage(packageName, userId)
                    customTileStatePersister.removeStateForPackageAndUser(packageName, userId)
                    customTileAddedRepository.removeTilesForPackage(packageName, userId)
                }
        }
    }
}
