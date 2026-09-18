/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.statusbar.core

import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.android.systemui.CoreStartable
import com.android.systemui.log.MultiDisplayStatusBarLogger
import com.android.systemui.res.R
import com.android.systemui.statusbar.core.StatusBarInitializer.OnStatusBarViewUpdatedListener
import com.android.systemui.statusbar.core.StatusBarInitializer.StatusBarViewLifecycleListener
import com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepository
import com.android.systemui.statusbar.phone.PhoneStatusBarTransitions
import com.android.systemui.statusbar.phone.PhoneStatusBarView
import com.android.systemui.statusbar.phone.PhoneStatusBarViewController
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import com.android.systemui.statusbar.pipeline.shared.ui.composable.StatusBarRootFactory
import com.android.systemui.statusbar.window.StatusBarWindowController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Responsible for creating the status bar window and initializing the root components of that
 * window (see [CollapsedStatusBarFragment])
 */
interface StatusBarInitializer : CoreStartable {

    var statusBarViewUpdatedListener: OnStatusBarViewUpdatedListener?

    /** Called when the status bar associated with this instance is being destroyed. */
    fun stop()

    interface StatusBarViewLifecycleListener {

        /**
         * The status bar view has been initialized.
         *
         * @param component Dagger component that is created when the status bar view is created.
         *   Can be used to retrieve dependencies from that scope, including the status bar root
         *   view.
         */
        fun onStatusBarViewInitialized(component: HomeStatusBarComponent)

        /** The status bar view has been destroyed. */
        fun onStatusBarViewDestroyed(component: HomeStatusBarComponent) {}
    }

    interface OnStatusBarViewUpdatedListener {
        fun onStatusBarViewUpdated(
            statusBarViewController: PhoneStatusBarViewController,
            statusBarTransitions: PhoneStatusBarTransitions,
        )
    }

    interface Factory {
        fun create(
            displayId: Int,
            statusBarWindowController: StatusBarWindowController,
            statusBarModePerDisplayRepository: StatusBarModePerDisplayRepository,
            statusBarRootFactory: StatusBarRootFactory,
            componentFactory: HomeStatusBarComponent.Factory,
        ): StatusBarInitializer
    }
}

class StatusBarInitializerImpl
@AssistedInject
constructor(
    @Assisted private val displayId: Int,
    @Assisted private val statusBarWindowController: StatusBarWindowController,
    @Assisted private val statusBarModePerDisplayRepository: StatusBarModePerDisplayRepository,
    @Assisted private val statusBarRootFactory: StatusBarRootFactory,
    @Assisted private val componentFactory: HomeStatusBarComponent.Factory,
    private val lifecycleListeners: Set<@JvmSuppressWildcards StatusBarViewLifecycleListener>,
    private val logger: MultiDisplayStatusBarLogger,
) : StatusBarInitializer {
    private var component: HomeStatusBarComponent? = null

    @get:VisibleForTesting
    var initialized = false
        private set

    override var statusBarViewUpdatedListener: OnStatusBarViewUpdatedListener? = null
        set(value) {
            field = value
            // If a listener is added after initialization, immediately call the callback
            component?.let { component ->
                field?.onStatusBarViewUpdated(
                    component.phoneStatusBarViewController,
                    component.phoneStatusBarTransitions,
                )
            }
        }

    /** Stand up the [PhoneStatusBarView] in a compose root */
    override fun start() {
        initialized = true
        logger.logCreatingStatusBarRootView(displayId)
        val statusBarRoot =
            statusBarRootFactory.create(statusBarWindowController.backgroundView as ViewGroup) { cv
                ->
                val phoneStatusBarView = cv.findViewById<PhoneStatusBarView>(R.id.status_bar)
                component =
                    componentFactory.create(phoneStatusBarView, statusBarWindowController).also {
                        component ->
                        // CollapsedStatusBarFragment used to be responsible initializing
                        component.init()

                        statusBarViewUpdatedListener?.onStatusBarViewUpdated(
                            component.phoneStatusBarViewController,
                            component.phoneStatusBarTransitions,
                        )
                        statusBarModePerDisplayRepository.onStatusBarViewInitialized(component)
                        lifecycleListeners.forEach { listener ->
                            listener.onStatusBarViewInitialized(component)
                        }
                    }
            }
        logger.logCreatedStatusBarRootView(displayId)

        // Add the new compose view to the hierarchy because we don't use fragment transactions
        // anymore
        val windowBackgroundView = statusBarWindowController.backgroundView as ViewGroup
        windowBackgroundView.addView(statusBarRoot)
        logger.logStatusBarRootViewAddedToWindow(displayId)
    }

    override fun stop() {
        val component = this.component ?: return
        lifecycleListeners.forEach { it.onStatusBarViewDestroyed(component) }
    }

    @AssistedFactory
    interface Factory : StatusBarInitializer.Factory {
        override fun create(
            displayId: Int,
            statusBarWindowController: StatusBarWindowController,
            statusBarModePerDisplayRepository: StatusBarModePerDisplayRepository,
            statusBarRootFactory: StatusBarRootFactory,
            componentFactory: HomeStatusBarComponent.Factory,
        ): StatusBarInitializerImpl
    }
}
