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
package com.android.server.companion.datatransfer.crossdevicesync.common.fake

import android.content.res.Resources

/** A test class to provide a fake [Resources] for testing. */
@Suppress("DEPRECATION") // Using deprecated Resources constructor for testing
class FakeResources(val baseResources: Resources) :
    Resources(baseResources.assets, baseResources.displayMetrics, baseResources.configuration) {
    private val mResourceOverrides = mutableMapOf<Int, Any>()

    fun overrideResource(resId: Int, value: Any) {
        mResourceOverrides[resId] = value
    }

    fun reset() {
        mResourceOverrides.clear()
    }

    override fun getBoolean(resId: Int) =
        if (mResourceOverrides.containsKey(resId) && mResourceOverrides[resId] is Boolean) {
            mResourceOverrides[resId] as Boolean
        } else {
            baseResources.getBoolean(resId)
        }
}
