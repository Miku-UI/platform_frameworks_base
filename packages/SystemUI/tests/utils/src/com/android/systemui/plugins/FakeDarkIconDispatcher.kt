/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.plugins

import android.graphics.Rect
import com.android.systemui.statusbar.phone.LightBarTransitionsController
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher.DarkChange
import java.io.PrintWriter
import java.util.ArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeDarkIconDispatcher(
    private val lightBarTransitionsController: LightBarTransitionsController
) : SysuiDarkIconDispatcher {

    private val mutableDarkChangeFlow = MutableStateFlow(DarkChange.EMPTY)

    val receivers = mutableListOf<DarkIconDispatcher.DarkReceiver>()

    override fun setIconsDarkArea(r: ArrayList<Rect>) {
        mutableDarkChangeFlow.value = mutableDarkChangeFlow.value.copy(areas = r)
    }

    fun setIconsTint(color: Int) {
        mutableDarkChangeFlow.value = mutableDarkChangeFlow.value.copy(tint = color)
    }

    fun setDarkIntensity(darkIntensity: Float) {
        mutableDarkChangeFlow.value =
            mutableDarkChangeFlow.value.copy(darkIntensity = darkIntensity)
    }

    override fun addDarkReceiver(receiver: DarkIconDispatcher.DarkReceiver) {
        receivers.add(receiver)
    }

    override fun removeDarkReceiver(receiver: DarkIconDispatcher.DarkReceiver) {
        receivers.remove(receiver)
    }

    override fun applyDark(`object`: DarkIconDispatcher.DarkReceiver) {}

    override fun dump(pw: PrintWriter, args: Array<out String>) {}

    override fun getTransitionsController(): LightBarTransitionsController {
        return lightBarTransitionsController
    }

    override fun darkChangeFlow(): StateFlow<DarkChange> {
        return mutableDarkChangeFlow
    }

    private fun DarkChange.copy(
        areas: Collection<Rect> = this.areas,
        darkIntensity: Float = this.darkIntensity,
        tint: Int = this.tint,
    ): DarkChange {
        return DarkChange(areas, darkIntensity, tint)
    }
}
