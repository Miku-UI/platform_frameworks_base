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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.datastore.DataChangeReason
import com.android.settingslib.datastore.HandlerExecutor
import com.android.settingslib.datastore.KeyedObservable
import com.android.settingslib.datastore.KeyedObserver
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class AppOpsModeObservableTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val executor = HandlerExecutor.main
    private val op = AppOpsManager.OPSTR_MONITOR_LOCATION
    private val opCode = AppOpsManager.OP_MONITOR_LOCATION

    @Test
    fun observerNotified() {
        val pkg1 = "pkg1"
        val pkg2 = "pkg2"
        val observer1: KeyedObserver<String> = mock()
        val observer2: KeyedObserver<String> = mock()
        val anyPkgObserver: KeyedObserver<String?> = mock()

        context.addAppOpsModeObserver(op, anyPkgObserver, executor)
        val observable1 = context.appOpsModeObservable(op)
        context.addAppOpsModeObserver(op, pkg1, observer1, executor)
        context.addAppOpsModeObserver(op, pkg2, observer2, executor)
        val observable2 = context.appOpsModeObservable(op)
        try {
            assertThat(observable1).isSameInstanceAs(observable2)

            appOpsManager.setMode(opCode, 0, pkg1, AppOpsManager.MODE_ERRORED)
            appOpsManager.setMode(opCode, 0, pkg1, AppOpsManager.MODE_ERRORED) // mode unchanged
            appOpsManager.setMode(opCode, 0, pkg2, AppOpsManager.MODE_ERRORED)
            appOpsManager.setMode(opCode, 0, pkg2, AppOpsManager.MODE_ALLOWED)

            verify(observer1).onKeyChanged(pkg1, DataChangeReason.UPDATE)
            verify(anyPkgObserver).onKeyChanged(pkg1, DataChangeReason.UPDATE)
            verify(observer2, times(2)).onKeyChanged(pkg2, DataChangeReason.UPDATE)
            verify(anyPkgObserver, times(2)).onKeyChanged(pkg2, DataChangeReason.UPDATE)
        } finally {
            context.removeAppOpsModeObserver(op, anyPkgObserver)
            context.removeAppOpsModeObserver(op, pkg1, observer1)
            context.removeAppOpsModeObserver(op, pkg2, observer2)
        }
        assertThat(appOpsModeObservables.size).isEqualTo(0)
    }

    @Test
    fun appOpsModeObservable_multipleInstances() {
        val observable1 = context.appOpsModeObservable(op)
        observable1.verifyObserverNotified(0) // observable is released due to no observer

        // a new observable is created
        val observable2 = context.appOpsModeObservable(op)
        assertThat(observable1).isNotSameInstanceAs(observable2)

        // the old observable should still work
        observable1.verifyObserverNotified(1, AppOpsManager.MODE_ALLOWED) // use a different mode
        // the new observable also works
        observable2.verifyObserverNotified(0)
    }

    private fun KeyedObservable<String>.verifyObserverNotified(
        size: Int,
        mode: Int = AppOpsManager.MODE_ERRORED,
    ) {
        val observer: KeyedObserver<String> = mock()
        addObserver("pkg", observer, executor)
        try {
            appOpsManager.setMode(opCode, 0, "pkg", mode)
            verify(observer).onKeyChanged("pkg", DataChangeReason.UPDATE)
        } finally {
            removeObserver("pkg", observer)
        }
        assertThat(appOpsModeObservables.size).isEqualTo(size)
    }
}
