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

package com.android.settingslib.utils.applications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.IntDef
import androidx.collection.MutableObjectIntMap
import com.android.settingslib.datastore.AbstractKeyedDataObservable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The reason of package change. */
@IntDef(
    PackageChangeReason.UNKNOWN,
    PackageChangeReason.ADDED,
    PackageChangeReason.CHANGED,
    PackageChangeReason.REMOVED,
)
@Retention(AnnotationRetention.SOURCE)
annotation class PackageChangeReason {
    companion object {
        const val UNKNOWN = 0
        const val ADDED = 1
        const val CHANGED = 2
        const val REMOVED = 3

        fun of(action: String?) =
            when (action) {
                Intent.ACTION_PACKAGE_ADDED -> ADDED
                Intent.ACTION_PACKAGE_CHANGED -> CHANGED
                Intent.ACTION_PACKAGE_REMOVED -> REMOVED
                else -> UNKNOWN
            }
    }
}

/**
 * A shared observable to monitor package change (the observer key is package name).
 *
 * Normally, following events are observed when:
 * - Install a new app: [PackageChangeReason.ADDED], [PackageChangeReason.CHANGED]
 * - Replace an app: [PackageChangeReason.REMOVED], [PackageChangeReason.ADDED]
 * - Uninstall an app: [PackageChangeReason.REMOVED]
 *
 * To avoid multiple notifications on the same package, [debounceTimeoutMs] is provided to conflate
 * events. [debounceTimeoutMs] is set to 200ms by default and non positive value will notify all the
 * events immediately.
 */
class PackageObservable
private constructor(private val appContext: Context, private val debounceTimeoutMs: Long = 200) :
    AbstractKeyedDataObservable<String>() {

    private val pendingChanges = MutableObjectIntMap<String>()
    private var task: Job? = null

    private val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val reason = PackageChangeReason.of(intent.action)
                val pkg = intent.data?.encodedSchemeSpecificPart ?: ""
                if (debounceTimeoutMs > 0) {
                    // notify all observers when package is absent
                    if (pkg.isEmpty()) pendingChanges.clear()
                    pendingChanges[pkg] = reason
                    task?.cancel()
                    task =
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(debounceTimeoutMs)
                            pendingChanges.forEach(::notify)
                            pendingChanges.clear()
                            task = null
                        }
                } else {
                    notify(pkg, reason)
                }
            }
        }

    private fun notify(pkg: String, reason: Int) {
        // notify all observers when package is absent
        if (pkg.isEmpty()) {
            notifyChange(reason)
        } else {
            notifyChange(pkg, reason)
        }
    }

    override fun onFirstObserverAdded() {
        val intentFilter =
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
        appContext.registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onLastObserverRemoved() {
        appContext.unregisterReceiver(broadcastReceiver)
    }

    companion object {
        @Volatile private var instance: PackageObservable? = null

        /**
         * Returns the default [PackageObservable] instance.
         *
         * Package events are conflated with a 200ms timeout, which means
         * - if a package receives EventA and EventB within 200ms time window, only EventB is
         *   notified for the package
         * - the event is delayed with at least 200ms when it happened
         */
        fun get(context: Context) =
            instance
                ?: synchronized(this) {
                    instance ?: PackageObservable(context.applicationContext).also { instance = it }
                }
    }
}
