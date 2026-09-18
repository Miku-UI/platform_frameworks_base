/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.qs.pipeline.domain.autoaddable

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.qs.pipeline.data.model.cellTileRestoreProcessor
import com.android.systemui.qs.pipeline.data.model.workTileRestoreProcessor
import com.android.systemui.qs.pipeline.data.repository.tileSpecRepository
import com.android.systemui.qs.pipeline.domain.model.AutoAddable
import com.android.systemui.settings.userTracker
import com.android.systemui.statusbar.pipeline.shared.connectivityConstants

val Kosmos.workTileAutoAddable by
    Kosmos.Fixture { WorkTileAutoAddable(userTracker, workTileRestoreProcessor) }

val Kosmos.cellAutoAddable by
    Kosmos.Fixture {
        CellAutoAddable(
            connectivityConstants,
            tileSpecRepository,
            cellTileRestoreProcessor,
            testDispatcher,
        )
    }

var Kosmos.autoAddables by Kosmos.Fixture { mutableSetOf<AutoAddable>() }

fun Kosmos.withWorkTileAutoAddable() {
    autoAddables.add(workTileAutoAddable)
}

fun Kosmos.withCellAutoAddable() {
    autoAddables.add(cellAutoAddable)
}
