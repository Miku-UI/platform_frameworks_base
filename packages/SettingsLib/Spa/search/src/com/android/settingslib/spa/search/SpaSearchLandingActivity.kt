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

package com.android.settingslib.spa.search

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting

abstract class SpaSearchLandingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyString = intent.getStringExtra(EXTRA_FRAGMENT_ARG_KEY)
        if (!keyString.isNullOrEmpty() && isValidCall()) {
            tryLaunch(keyString)
        }
        finish()
    }

    private fun tryLaunch(keyString: String) {
        val key = decodeToSpaSearchLandingKey(keyString) ?: return
        if (key.hasSpaPage()) {
            val destination = key.spaPage.destination
            if (destination.isNotEmpty()) {
                Log.d(TAG, "Launch SPA search result: ${key.spaPage}")
                startSpaPage(destination, key.spaPage.highlightItemKey)
            }
        }
        if (key.hasFragment()) {
            Log.d(TAG, "Launch fragment search result: ${key.fragment}")
            val arguments =
                Bundle().apply {
                    key.fragment.argumentsMap.forEach { (k, v) ->
                        if (v.hasIntValue()) putInt(k, v.intValue)
                    }
                    putString(EXTRA_FRAGMENT_ARG_KEY, key.fragment.preferenceKey)
                }
            startFragment(key.fragment.fragmentName, arguments)
        }
    }

    abstract fun isValidCall(): Boolean

    /**
     * Starts the Spa page.
     *
     * @param destination The destination of SPA page.
     * @param highlightItemKey The key to highlight the item.
     */
    open fun startSpaPage(destination: String, highlightItemKey: String) {
        throw UnsupportedOperationException()
    }

    open fun startFragment(fragmentName: String, arguments: Bundle) {
        throw UnsupportedOperationException()
    }

    companion object {
        @VisibleForTesting
        const val EXTRA_FRAGMENT_ARG_KEY: String = ":settings:fragment_args_key"

        private const val TAG = "SpaSearchLandingActivit"
    }
}
