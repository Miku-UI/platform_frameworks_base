/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.pipeline.data.repository

import android.content.ComponentName

class FakeCustomTileAddedRepository : CustomTileAddedRepository {

    private val tileAddedRegistry = mutableSetOf<Pair<Int, ComponentName>>()
    private val version = mutableMapOf<Int, Int>()

    override fun isTileAdded(componentName: ComponentName, userId: Int): Boolean {
        return (userId to componentName) in tileAddedRegistry
    }

    override fun setTileAdded(componentName: ComponentName, userId: Int, added: Boolean) {
        if (added) {
            tileAddedRegistry.add(userId to componentName)
        } else {
            tileAddedRegistry.remove(userId to componentName)
        }
    }

    override fun removeNonCurrentTiles(currentTiles: List<ComponentName>, userId: Int) {
        tileAddedRegistry.removeIf { it.first == userId && it.second !in currentTiles }
    }

    override fun getVersion(userId: Int): Int {
        return version.getOrDefault(userId, 1)
    }

    override fun setVersion(version: Int, userId: Int) {
        this.version.put(userId, version)
    }
}
