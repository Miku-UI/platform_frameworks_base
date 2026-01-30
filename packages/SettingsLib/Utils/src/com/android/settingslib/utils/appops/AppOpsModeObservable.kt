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

package com.android.settingslib.utils.appops

import android.app.AppOpsManager
import android.app.AppOpsManager.OnOpChangedListener
import android.content.Context
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.collection.MutableScatterMap
import com.android.settingslib.datastore.AbstractKeyedDataObservable
import com.android.settingslib.datastore.DataChangeReason
import com.android.settingslib.datastore.KeyedObservable
import com.android.settingslib.datastore.KeyedObserver
import java.util.concurrent.Executor

@GuardedBy("itself")
@VisibleForTesting
internal val appOpsModeObservables = MutableScatterMap<String, KeyedObservable<String>>()

/**
 * Adds an observer to monitor app-ops mode change on given operation. To avoid memory leak,
 * [removeAppOpsModeObserver] must be invoked sometime later.
 *
 * As an improvement, there is only one [OnOpChangedListener] registered to [AppOpsManager] for the
 * same [op].
 *
 * @param op the operation to monitor, one of `AppOpsManager.OPSTR_*`
 * @param observer observer of the mode change (callback key is package name)
 * @param executor executor to run the observer callback
 */
fun Context.addAppOpsModeObserver(
    op: String,
    observer: KeyedObserver<String?>,
    executor: Executor,
) = appOpsModeObservable(op).addObserver(observer, executor)

/**
 * Adds an observer to monitor app-ops mode change on given operation and package. To avoid memory
 * leak, [removeAppOpsModeObserver] must be invoked sometime later.
 *
 * As an improvement, there is only one [OnOpChangedListener] registered to [AppOpsManager] for the
 * same [op] even if several observers are added for different packages.
 *
 * @param op the operation to monitor, one of `AppOpsManager.OPSTR_*`
 * @param packageName package to monitor the mode change
 * @param observer observer of the mode change (callback key is package name)
 * @param executor executor to run the observer callback
 */
fun Context.addAppOpsModeObserver(
    op: String,
    packageName: String,
    observer: KeyedObserver<String>,
    executor: Executor,
) = appOpsModeObservable(op).addObserver(packageName, observer, executor)

/** Removes the observer added by [addAppOpsModeObserver]. */
fun Context.removeAppOpsModeObserver(op: String, observer: KeyedObserver<String?>) =
    appOpsModeObservable(op).removeObserver(observer)

/** Removes the observer added by [addAppOpsModeObserver]. */
fun Context.removeAppOpsModeObserver(
    op: String,
    packageName: String,
    observer: KeyedObserver<String>,
) = appOpsModeObservable(op).removeObserver(packageName, observer)

/**
 * Returns a shared [KeyedObservable] to monitor app-ops mode change on given operation. Only a
 * single [OnOpChangedListener] will be registered to [AppOpsManager] for the same [op].
 *
 * Notes:
 * - The observer key is package name.
 * - It is not recommend to save the returned object (e.g. keep in a field, pass around lambda),
 *   just invoke this method everytime. This is to avoid multiple instances of [KeyedObservable]
 *   created for the same [op]. Hence use [addAppOpsModeObserver] and [removeAppOpsModeObserver]
 *   whenever possible.
 */
internal fun Context.appOpsModeObservable(op: String): KeyedObservable<String> =
    synchronized(appOpsModeObservables) {
        appOpsModeObservables.getOrPut(op) { AppOpsModeObservable(applicationContext, op) }
    }

/**
 * Class to monitor app-ops mode change on given operation.
 *
 * This class helps to reduce the number of [OnOpChangedListener] registered to [AppOpsManager]. No
 * matter how many packages are observed, there is only one [OnOpChangedListener].
 */
private class AppOpsModeObservable(private val appContext: Context, private val op: String) :
    AbstractKeyedDataObservable<String>(), OnOpChangedListener {

    private val appOpsManager
        get() = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    override fun onOpChanged(op: String?, packageName: String) {
        notifyChange(packageName, DataChangeReason.UPDATE)
    }

    override fun onFirstObserverAdded() {
        appOpsManager.startWatchingMode(op, null, this)
    }

    override fun onLastObserverRemoved() {
        appOpsManager.stopWatchingMode(this)
        synchronized(appOpsModeObservables) { appOpsModeObservables.remove(op, this) }
    }
}
