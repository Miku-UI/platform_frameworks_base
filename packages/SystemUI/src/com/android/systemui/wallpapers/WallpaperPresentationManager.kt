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

package com.android.systemui.wallpapers

import android.app.Presentation
import android.util.Log
import android.view.Display
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.log.DebugLogger.debugLog
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType
import com.android.systemui.wallpapers.ui.presentation.WallpaperPresentationFactory
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * A manager which controls the lifecycle of wallpaper [Presentation] on all compatible displays.
 */
class WallpaperPresentationManager
@Inject
constructor(
    private val display: Display,
    @DisplayAware private val displayCoroutineScope: CoroutineScope,
    @DisplayAware private val presentationInteractor: DisplayWallpaperPresentationInteractor,
    private val presentationFactories:
        Map<
            @JvmSuppressWildcards
            WallpaperPresentationType,
            @JvmSuppressWildcards
            WallpaperPresentationFactory,
        >,
    @Application private val appCoroutineScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
) : SystemUIDisplaySubcomponent.LifecycleListener {

    private var presentation: Presentation? = null

    override fun start() {
        debugLog(enabled = DEBUG, tag = TAG) { "Display: ${display.displayId} started." }
        super.start()
        displayCoroutineScope.launch(mainDispatcher) {
            presentationInteractor.presentationFactoryFlow
                .onCompletion {
                    debugLog(enabled = DEBUG, tag = TAG) {
                        "Display: ${display.displayId} collection ended."
                    }
                }
                .distinctUntilChanged()
                .collect { presentationType ->
                    presentationFactories[presentationType]?.let { showPresentation(it) }
                        ?: run {
                            debugLog(enabled = DEBUG, tag = TAG) {
                                "Hiding presentation for type: $presentationType"
                            }
                            hidePresentation()
                        }
                }
        }
    }

    override fun stop() {
        debugLog(enabled = DEBUG, tag = TAG) { "Display: ${display.displayId} stopped" }
        super.stop()
        appCoroutineScope.launch(mainDispatcher) { hidePresentation() }
    }

    private fun showPresentation(factory: WallpaperPresentationFactory) {
        hidePresentation()
        presentation =
            factory.create(display).also {
                debugLog(enabled = DEBUG, tag = TAG) {
                    "Show presentation $it for display ${display.displayId}"
                }
                it.show()
            }
    }

    private fun hidePresentation() {
        presentation?.let {
            debugLog(enabled = DEBUG, tag = TAG) {
                "Hide presentation $it for display ${display.displayId}"
            }
            it.dismiss()
        }
        presentation = null
    }

    private companion object {
        const val TAG = "WallpaperPresentation"
        val DEBUG
            get() = Log.isLoggable(TAG, Log.DEBUG)
    }
}
