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
package com.android.systemui.display.ui.view

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Insets
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsets.Type
import android.view.WindowInsetsAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.android.systemui.common.ui.view.updateMargin
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.DialogDelegate
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.max

/**
 * Dialog used to decide what to do with a connected display.
 *
 * [onCancelClickListener] is called **only** if either desktop mode didn't start, mirroring didn't
 * start, or when the dismiss button is pressed.
 *
 * This is a new version of the old [MirroringConfirmationDialogDelegate].
 */
class ExternalDisplayConnectionDialogDelegate
@VisibleForTesting
@AssistedInject
constructor(
    @Application private val context: Context,
    @Assisted("showConcurrentDisplayInfo") private val showConcurrentDisplayInfo: Boolean = false,
    @Assisted("isInKioskMode") private val isInKioskMode: Boolean = false,
    @Assisted private val rememberChoiceCheckBoxListener: CompoundButton.OnCheckedChangeListener,
    @Assisted("onStartDesktop") private val onStartDesktopClickListener: View.OnClickListener,
    @Assisted("onStartMirroring") private val onStartMirroringClickListener: View.OnClickListener,
    @Assisted("onCancel") private val onCancelClickListener: View.OnClickListener,
    @Assisted private val insetsProvider: () -> Insets,
) : DialogDelegate<Dialog> {

    private lateinit var rememberChoiceCheckbox: CheckBox
    private lateinit var desktopButton: Button
    private lateinit var mirrorButton: Button
    private lateinit var dismissButton: Button
    private lateinit var dualDisplayWarning: TextView
    private lateinit var bottomSheet: View
    private var optionSelected = false
    private val defaultDialogBottomInset =
        context.resources.getDimensionPixelSize(R.dimen.dialog_bottom_padding)

    override fun onCreate(dialog: Dialog, savedInstanceState: Bundle?) {
        dialog.setContentView(R.layout.connected_display_dialog_2)

        rememberChoiceCheckbox =
            dialog.requireViewById<CheckBox>(R.id.save_connection_preference).apply {
                if (isInKioskMode) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    setOnCheckedChangeListener(rememberChoiceCheckBoxListener)
                }
            }

        desktopButton =
            dialog.requireViewById<Button>(R.id.start_desktop_mode).apply {
                if (isInKioskMode) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    setOnClickListener {
                        optionSelected = true
                        onStartDesktopClickListener.onClick(this)
                    }
                }
            }

        mirrorButton =
            dialog.requireViewById<Button>(R.id.start_mirroring).apply {
                setOnClickListener {
                    optionSelected = true
                    onStartMirroringClickListener.onClick(this)
                }
            }

        dismissButton =
            dialog.requireViewById<Button>(R.id.cancel).apply {
                setOnClickListener(onCancelClickListener)
            }

        dualDisplayWarning =
            dialog.requireViewById<TextView>(R.id.dual_display_warning).apply {
                visibility = if (showConcurrentDisplayInfo) View.VISIBLE else View.GONE
            }

        bottomSheet = dialog.requireViewById(R.id.cd_bottom_sheet)

        dialog.setOnDismissListener { if (!optionSelected) onCancelClickListener.onClick(null) }
        setupInsets()
    }

    override fun onStart(dialog: Dialog) {
        dialog.window?.decorView?.setWindowInsetsAnimationCallback(insetsAnimationCallback)
    }

    override fun onStop(dialog: Dialog) {
        dialog.window?.decorView?.setWindowInsetsAnimationCallback(null)
    }

    private fun setupInsets(insets: Insets = insetsProvider()) {
        // This avoids overlap between dialog content and navigation bars.
        // we only care about the bottom inset as in all other configuration where navigations
        // are in other display sides there is no overlap with the dialog.
        dismissButton.updateMargin(bottom = max(insets.bottom, defaultDialogBottomInset))
    }

    override fun onConfigurationChanged(dialog: Dialog, configuration: Configuration) {
        setupInsets()
    }

    private val insetsAnimationCallback =
        object : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP) {

            private var lastInsets: WindowInsets? = null

            override fun onEnd(animation: WindowInsetsAnimation) {
                lastInsets?.let { onInsetsChanged(animation.typeMask, it) }
            }

            override fun onProgress(
                insets: WindowInsets,
                animations: MutableList<WindowInsetsAnimation>,
            ): WindowInsets {
                lastInsets = insets
                onInsetsChanged(changedTypes = allAnimationMasks(animations), insets)
                return insets
            }

            private fun allAnimationMasks(animations: List<WindowInsetsAnimation>): Int =
                animations.fold(0) { acc: Int, it -> acc or it.typeMask }

            private fun onInsetsChanged(changedTypes: Int, insets: WindowInsets) {
                val insetTypes = Type.navigationBars() or Type.displayCutout()
                if (changedTypes and insetTypes != 0) {
                    setupInsets(insets.getInsets(insetTypes))
                }
            }
        }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("showConcurrentDisplayInfo") showConcurrentDisplayInfo: Boolean,
            @Assisted("isInKioskMode") isInKioskMode: Boolean,
            rememberChoiceCheckBoxListener: CompoundButton.OnCheckedChangeListener,
            @Assisted("onStartDesktop") onStartDesktopClickListener: View.OnClickListener,
            @Assisted("onStartMirroring") onStartMirroringClickListener: View.OnClickListener,
            @Assisted("onCancel") onCancelClickListener: View.OnClickListener,
            insetsProvider: () -> Insets,
        ): ExternalDisplayConnectionDialogDelegate
    }
}
