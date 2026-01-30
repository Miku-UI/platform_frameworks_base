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
import android.content.SharedPreferences
import androidx.core.content.edit
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.settings.UserFileManager
import javax.inject.Inject

/**
 * Repository for keeping track of whether a given [CustomTile] [ComponentName] has been added to
 * the set of current tiles for a user. This is used to determine when lifecycle methods in
 * `TileService` about the tile being added/removed need to be called.
 */
interface CustomTileAddedRepository {
    /**
     * Check if a particular [CustomTile] associated with [componentName] has been added for
     * [userId] and has not been removed since.
     */
    fun isTileAdded(componentName: ComponentName, userId: Int): Boolean

    /**
     * Persists whether a particular [CustomTile] associated with [componentName] has been added and
     * it's currently in the set of selected tiles for [userId].
     */
    fun setTileAdded(componentName: ComponentName, userId: Int, added: Boolean)

    /** Mark as removed all the non-current tiles for [userId]. */
    fun removeNonCurrentTiles(currentTiles: List<ComponentName>, userId: Int)

    /**
     * Get the current version of the underlying data for [userId]. This will return 1 if no version
     * has been set.
     */
    fun getVersion(userId: Int): Int

    /** Set the version for the current user. */
    fun setVersion(version: Int, userId: Int)
}

@SysUISingleton
class CustomTileAddedSharedPrefsRepository
@Inject
constructor(private val userFileManager: UserFileManager) : CustomTileAddedRepository {

    override fun isTileAdded(componentName: ComponentName, userId: Int): Boolean {
        return getSharedPreferences(userId).getBoolean(componentName.flattenToString(), false)
    }

    override fun setTileAdded(componentName: ComponentName, userId: Int, added: Boolean) {
        getSharedPreferences(userId)
            .edit()
            .putBoolean(componentName.flattenToString(), added)
            .apply()
    }

    override fun getVersion(userId: Int): Int {
        return userFileManager.getSharedPreferences(TILES, 0, userId).getInt(VERSION_KEY, 1)
    }

    override fun setVersion(version: Int, userId: Int) {
        getSharedPreferences(userId).edit().putInt(VERSION_KEY, version).apply()
    }

    override fun removeNonCurrentTiles(currentTiles: List<ComponentName>, userId: Int) {
        val sharedPreferences = getSharedPreferences(userId)
        val tilesInFile =
            sharedPreferences.all.filter { it.key.contains("/") && it.value is Boolean }.keys
        val nonCurrentTiles = tilesInFile.minus(currentTiles.map { it.flattenToString() }.toSet())
        sharedPreferences.edit { nonCurrentTiles.forEach { putBoolean(it, false) } }
    }

    private fun getSharedPreferences(userId: Int): SharedPreferences {
        return userFileManager.getSharedPreferences(TILES, 0, userId)
    }

    companion object {
        private const val TILES = "tiles_prefs"
        private const val VERSION_KEY = "version"
    }
}
