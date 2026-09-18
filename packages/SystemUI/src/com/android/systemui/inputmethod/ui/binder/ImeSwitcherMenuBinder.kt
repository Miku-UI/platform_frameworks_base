/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.inputmethod.ui.binder

import android.annotation.UserIdInt
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.view.inputmethod.Flags
import android.view.inputmethod.InputMethodManager.IMPickerEntryPoint
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.inputmethod.ui.ImeSwitcherMenuUi
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuBinderViewModel
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.util.asIndenting
import com.android.systemui.util.printSection
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.io.PrintWriter
import javax.inject.Inject

/** Binder for managing per-user IME Switcher Menu UI instances. */
@SysUISingleton
class ImeSwitcherMenuBinder
@Inject
constructor(
    private val viewModel: ImeSwitcherMenuBinderViewModel,
    private val uiFactory: ImeSwitcherMenuUi.Factory,
    private val viewModelFactory: ImeSwitcherMenuViewModel.Factory,
    private val context: Context,
    private val displayManager: DisplayManager,
) : CoreStartable {

    /**
     * Listener to be notified when the display that should show the UI tied to a user has changed.
     */
    private val listener =
        object : ImeSwitcherMenuBinderViewModel.DisplayChangeListener {

            override fun onChanged(
                @UserIdInt userId: Int,
                @IMPickerEntryPoint entryPoint: Int?,
                displayId: Int?,
            ) {
                updateUiState(userId, entryPoint, displayId)
            }
        }

    /** Mapping between userId and the current UI instance for that user. */
    private var userUiInstances: MutableMap<Int, DisplayUiInstance> = mutableMapOf()

    /**
     * The cached display-specific window context for the IME Switcher Menu dialog to receive
     * configuration changes.
     */
    private var latestWindowContext: Context? = null

    override fun start() {
        if (!Flags.imeSwitcherMenuSystemui()) {
            return
        }
        // The Binder is a CoreStartable and is thus always running, so it won't unregister the
        // listener.
        viewModel.registerDisplayChangeListener(listener)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        val writer = pw.asIndenting()
        writer.printSection(TAG) {
            userUiInstances.forEach { userId, instance ->
                instance.ui.dump(writer, "(userId: $userId displayId: ${instance.displayId})")
            }
        }
    }

    /**
     * Updates the UI state for the given user on the given display. If the displayId is non-`null`,
     * the UI is created and shown. Otherwise, the UI is dismissed and destroyed.
     *
     * @param userId the ID of the user to update the UI state for.
     * @param entryPoint the entry point where the UI was requested from, or `null` if the UI should
     *   be destroyed.
     * @param displayId the ID of the display to create the UI for, or `null` if the UI should be
     *   destroyed.
     */
    private fun updateUiState(
        @UserIdInt userId: Int,
        @IMPickerEntryPoint entryPoint: Int?,
        displayId: Int?,
    ) {
        if (entryPoint != null && displayId != null) {
            val current = userUiInstances[userId]
            if (current != null && current.displayId != displayId) {
                // Dismiss if the UI is currently showing on a different display.
                current.ui.dismiss()
                userUiInstances.remove(userId)
            }

            userUiInstances
                .getOrPut(userId) {
                    val ui =
                        uiFactory.create(getContext(displayId), entryPoint) { context ->
                            viewModelFactory.create(context, userId)
                        }
                    DisplayUiInstance(ui, displayId)
                }
                .also { it.ui.show() }
        } else {
            userUiInstances.remove(userId)?.ui?.dismiss()
        }
    }

    /**
     * Gets the display-specific window context for the IME Switcher Menu to receive configuration
     * changes, tied to the given display. This will cache and re-use the context for the most
     * recently requested {@code displayId}, otherwise it will compute a new context.
     *
     * @param displayId the ID of the display to get the display-specific window context for.
     * @throws IllegalArgumentException if the given displayId is invalid.
     */
    @Throws(IllegalArgumentException::class)
    private fun getContext(displayId: Int): Context {
        val curContext = latestWindowContext
        if (curContext?.displayId == displayId) {
            return curContext
        }
        // TODO(b/460776726): find the equivalent to mDisplayController.getDisplayContext in SysUI
        val display =
            displayManager.getDisplay(displayId)
                ?: throw IllegalArgumentException("Invalid display: $displayId")
        val windowContext =
            context.createWindowContext(
                display,
                WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG,
                null, /* options */
            )
        latestWindowContext = windowContext
        return windowContext
    }

    /**
     * Helper class for grouping a UI instance with the displayId it was created for.
     *
     * @param ui the actual UI instance.
     * @param displayId the ID of the display the UI was created for.
     */
    private data class DisplayUiInstance(val ui: ImeSwitcherMenuUi, val displayId: Int)

    companion object {

        private const val TAG = "ImeSwitcherMenuBinder"
    }
}

/** Dagger module for [ImeSwitcherMenuBinder]. */
@Module
interface ImeSwitcherMenuBinderModule {

    @Binds
    @IntoMap
    @ClassKey(ImeSwitcherMenuBinder::class)
    fun bindBinderStartable(impl: ImeSwitcherMenuBinder): CoreStartable
}
