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

package com.android.systemui.statusbar.ui

import android.content.Context
import com.android.internal.policy.SystemBarUtils
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import dagger.Binds
import javax.inject.Inject
import kotlin.math.max

/**
 * Proxy interface to [SystemBarUtils], allowing injection of different logic for testing.
 *
 * Developers should almost always prefer [SystemBarUtilsState] instead.
 */
interface SystemBarUtilsProxy {
    fun getStatusBarHeight(context: Context? = null): Int

    fun getStatusBarHeaderHeightKeyguard(context: Context? = null): Int
}

class SystemBarUtilsProxyImpl @Inject constructor(@Application private val appContext: Context) :
    SystemBarUtilsProxy {
    override fun getStatusBarHeight(context: Context?): Int {
        return SystemBarUtils.getStatusBarHeight(context ?: appContext)
    }

    override fun getStatusBarHeaderHeightKeyguard(context: Context?): Int {
        val context = context ?: appContext
        val waterfallInsetTop = context.display.cutout?.waterfallInsets?.top ?: 0
        val statusBarHeaderHeightKeyguard =
            context.resources.getDimensionPixelSize(R.dimen.status_bar_header_height_keyguard)
        return max(getStatusBarHeight(), statusBarHeaderHeightKeyguard + waterfallInsetTop)
    }

    @dagger.Module
    interface Module {
        @Binds fun bindImpl(impl: SystemBarUtilsProxyImpl): SystemBarUtilsProxy
    }
}
