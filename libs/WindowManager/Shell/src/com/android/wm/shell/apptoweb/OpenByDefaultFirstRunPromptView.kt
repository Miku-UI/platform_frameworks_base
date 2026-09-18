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

package com.android.wm.shell.apptoweb

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Html
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.compatui.DialogContainerSupplier
import com.android.wm.shell.shared.TypefaceUtils

/** View for open by default first-run prompt. */
class OpenByDefaultFirstRunPromptView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), DialogContainerSupplier {

    private lateinit var dialogContainer: View
    private lateinit var dialogTitle: TextView
    private lateinit var openInAppButton: Button
    private lateinit var openInBrowserButton: Button
    private lateinit var askMeLaterButton: Button
    private lateinit var backgroundDim: Drawable

    fun setDismissOnClickListener(callback: (View) -> Unit) {
        // Clicks on the background dim should also dismiss the dialog.
        setOnClickListener(callback)
        // We add a no-op on-click listener to the dialog container so that clicks on it won't
        // propagate to the listener of the layout (which represents the background dim).
        dialogContainer.setOnClickListener {}
    }

    fun setOpenInBrowserButtonClickListener(callback: (View) -> Unit) {
        openInBrowserButton.setOnClickListener(callback)
    }

    fun setOpenInAppButtonClickListener(callback: (View) -> Unit) {
        openInAppButton.setOnClickListener(callback)
    }

    fun setAskMeLaterButtonClickListener(callback: (View) -> Unit) {
        askMeLaterButton.setOnClickListener(callback)
    }

    override fun getDialogContainerView(): View = dialogContainer

    override fun getBackgroundDimDrawable(): Drawable = backgroundDim

    override fun onFinishInflate() {
        super.onFinishInflate()
        accessibilityPaneTitle = context.getString(R.string.open_by_default_settings_text)
        dialogContainer = requireViewById(R.id.dialog_container)
        dialogTitle = dialogContainer.requireViewById(R.id.title)
        openInBrowserButton = dialogContainer.requireViewById(R.id.open_in_browser_button)
        openInAppButton = dialogContainer.requireViewById(R.id.open_in_app_button)
        askMeLaterButton = dialogContainer.requireViewById(R.id.ask_me_later_button)
        backgroundDim = background.mutate()
        backgroundDim.alpha = 128
        TypefaceUtils.setTypeface(dialogTitle, TypefaceUtils.FontFamily.GSF_HEADLINE_SMALL)
        TypefaceUtils.setTypeface(openInAppButton, TypefaceUtils.FontFamily.GSF_LABEL_LARGE)
        TypefaceUtils.setTypeface(openInBrowserButton, TypefaceUtils.FontFamily.GSF_LABEL_LARGE)
        if (!Flags.useInputReportedFocusForAccessibility()) {
            setupA11yTraversal()
        }
    }

    fun bindAppName(appName: CharSequence) {
        dialogTitle.text =
            Html.fromHtml(
                context.resources.getString(
                    R.string.open_by_default_splash_dialog_title_text,
                    Html.escapeHtml(appName),
                )
            )
    }

    // Set up a11y focus so that focus loops through elements within the dialog, instead of going to
    // elements behind the dialog.
    private fun setupA11yTraversal() {
        dialogTitle.accessibilityTraversalAfter = R.id.ask_me_later_button
        dialogTitle.accessibilityTraversalBefore = R.id.open_in_browser_button

        openInBrowserButton.accessibilityTraversalAfter = R.id.title
        openInBrowserButton.accessibilityTraversalBefore = R.id.open_in_app_button

        openInAppButton.accessibilityTraversalAfter = R.id.open_in_browser_button
        openInAppButton.accessibilityTraversalBefore = R.id.ask_me_later_button

        askMeLaterButton.accessibilityTraversalAfter = R.id.open_in_app_button
        askMeLaterButton.accessibilityTraversalBefore = R.id.title
    }
}
