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

package com.android.systemui.inputmethod.ui.view

import android.content.Context
import android.util.IndentingPrintWriter
import android.util.Slog
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager.IMPickerEntryPoint
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.systemui.inputmethod.ui.ImeSwitcherMenuUi
import com.android.systemui.inputmethod.ui.composable.ImeSwitcherMenuContent
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import com.android.systemui.util.Assert
import com.android.systemui.util.printSection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Default implementation of the IME Switcher Menu UI. */
class ImeSwitcherMenuDialogDelegate
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted @param:IMPickerEntryPoint private val entryPoint: Int,
    @Assisted private val viewModelFactory: (context: Context) -> ImeSwitcherMenuViewModel,
    private val sysuiDialogFactory: SystemUIDialogFactory,
) : SystemUIDialog.Delegate, ImeSwitcherMenuUi {

    /** The currently showing IME Switcher Menu dialog, or `null` if no menu is showing. */
    private var currentDialog: SystemUIDialog? = null

    override fun createDialog(): SystemUIDialog {
        Assert.isMainThread()
        currentDialog?.let {
            Slog.w(TAG, "Dialog is already open, dismissing it and creating a new one")
            it.dismiss()
        }
        val dialog =
            sysuiDialogFactory.create(context = context, dialogDelegate = this) {
                ImeSwitcherMenuContent(viewModelFactory) { it.dismiss() }
            }

        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let {
            it.attributes.apply {
                // Use an alternate token for the dialog for that window manager can group the token
                // with other IME windows based on type vs. grouping based on whichever token
                // happens to get selected by the system later on.
                token = dialog.context.windowContextToken
                gravity =
                    Gravity.getAbsoluteGravity(
                        Gravity.BOTTOM or Gravity.END,
                        dialog.context.resources.configuration.layoutDirection,
                    )
                x = HORIZONTAL_OFFSET
                privateFlags =
                    privateFlags or WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
                type = WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
                // Used for debugging only, not user visible.
                title = WINDOW_TITLE
                fitInsetsTypes =
                    fitInsetsTypes or
                        WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.displayCutout()
            }
            it.setHideOverlayWindows(true)
        }
        dialog.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    Assert.isMainThread()
                    currentDialog = null
                }
            }
        )

        currentDialog = dialog
        return dialog
    }

    override fun getWidth(dialog: SystemUIDialog): Int {
        // The base width of the window is set from config_prefDialogWidth. This would be overridden
        // by the first view that has some fixed layout_width set. For Compose the width modifier
        // would not be enforced, and thus limit it to the base width, and the requiredWidthIn
        // modifier would not propagate up the hierarchy, and would lead to clipping the content.
        // TODO(b/469720572): Use WRAP_CONTENT and set width on the outermost Column above once
        //  view-compose measurement is fixed.
        val desiredWidth = SystemUIDialog.calculateDialogWidthWithInsets(dialog, DESIRED_WITH_DP)
        val screenWidth = dialog.context.resources.displayMetrics.widthPixels
        return desiredWidth.coerceAtMost(screenWidth)
    }

    @MainThread
    override fun show() {
        Assert.isMainThread()

        if (isShowing) {
            return
        }

        val dialog = createDialog()
        dialog.show()
    }

    @MainThread
    override fun dismiss() {
        Assert.isMainThread()

        currentDialog?.dismiss()
    }

    /** Whether the IME Switcher Menu is showing. */
    val isShowing: Boolean
        get() = currentDialog?.isShowing == true

    override fun dump(pw: IndentingPrintWriter, description: String) {
        pw.run { printSection("$TAG $description") { println("isShowing: $isShowing") } }
    }

    @AssistedFactory
    interface Factory : ImeSwitcherMenuUi.Factory {

        override fun create(
            context: Context,
            @IMPickerEntryPoint entryPoint: Int,
            viewModelFactory: (Context) -> ImeSwitcherMenuViewModel,
        ): ImeSwitcherMenuDialogDelegate
    }

    companion object {

        private const val TAG: String = "ImeSwitcherMenuDialog"

        /**
         * The horizontal offset from the menu to the edge of the screen corresponding to
         * [Gravity.END].
         */
        private const val HORIZONTAL_OFFSET = 16

        /** The desired width of the dialog, in dp units. */
        private const val DESIRED_WITH_DP = 320

        /** The title of the window, used for debugging. */
        private const val WINDOW_TITLE = "IME Switcher Menu"
    }
}
