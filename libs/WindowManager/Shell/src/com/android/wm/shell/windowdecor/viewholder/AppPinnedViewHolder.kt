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
package com.android.wm.shell.windowdecor.viewholder

import android.app.ActivityManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import androidx.annotation.DimenRes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.android.wm.shell.R
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.DrawableInsets
import com.android.wm.shell.windowdecor.common.OPACITY_100
import com.android.wm.shell.windowdecor.common.OPACITY_55
import com.android.wm.shell.windowdecor.common.OPACITY_65
import com.android.wm.shell.windowdecor.common.Theme
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.createBackgroundDrawable
import com.android.wm.shell.windowdecor.extension.isTransparentCaptionBarAppearance
import com.android.wm.shell.windowdecor.viewholder.AppPinnedViewHolder.AppPinnedData

/**
 * ViewHolder for the Pinned Controller, used for exclusive floating windows. Handles mostly theming
 * and listener plumbing.
 */
class AppPinnedViewHolder(
    override val rootView: View,
    onTouchListener: View.OnTouchListener,
    onGenericMotionEventListener: View.OnGenericMotionListener,
    onOpenSettings: View.OnClickListener,
    onCloseWindow: View.OnClickListener,
) : WindowDecorationViewHolder<AppPinnedData>() {

    data class AppPinnedData(
        val taskInfo: ActivityManager.RunningTaskInfo,
        val hasGlobalFocus: Boolean,
    ) : Data()

    private val captionView: View = rootView.requireViewById(R.id.pinned_caption)
    private val captionHandle: View = rootView.requireViewById(R.id.caption_handle)
    private val settingsButton = rootView.requireViewById<ImageButton>(R.id.settings_button)
    private val closeButton = rootView.requireViewById<ImageButton>(R.id.close_window)
    private val decorThemeUtil = DecorThemeUtil(rootView.context)

    private val darkColors = dynamicDarkColorScheme(rootView.context)
    private val lightColors = dynamicLightColorScheme(rootView.context)

    init {
        captionView.setOnTouchListener(onTouchListener)
        captionView.setOnGenericMotionListener(onGenericMotionEventListener)
        captionHandle.setOnTouchListener(onTouchListener)
        settingsButton.setOnClickListener(onOpenSettings)
        closeButton.setOnClickListener(onCloseWindow)
    }

    override fun bindData(data: AppPinnedData) {
        // by default pinned windows are always dark, unless the app explicitly sets a transparent
        // caption bar, in which case we use the best color scheme based on the background color's
        // luminance.
        val isTransparentCaptionBar = data.taskInfo.isTransparentCaptionBarAppearance
        val theme = when {
            isTransparentCaptionBar -> decorThemeUtil.getAppTheme(data.taskInfo)
            else -> Theme.DARK
        }
        val isThemeDark = theme == Theme.DARK
        val colorScheme = decorThemeUtil.getColorScheme(/* isDarkMode= */ isThemeDark)

        val backgroundColor = when {
            isTransparentCaptionBar -> Color.TRANSPARENT
            else -> colorScheme.surfaceContainerHigh.toArgb()
        }
        captionView.setBackgroundColor(backgroundColor)

        val foregroundColor = colorScheme.onSurface.toArgb()
        val opacity = when {
            isThemeDark && !data.hasGlobalFocus -> OPACITY_55
            !isThemeDark && !data.hasGlobalFocus -> OPACITY_65
            else -> OPACITY_100
        }
        val colorStateList = ColorStateList.valueOf(foregroundColor).withAlpha(opacity)

        closeButton.apply {
            imageTintList = colorStateList
            background =
                createBackgroundDrawable(
                    color = foregroundColor,
                    cornerRadius =
                        getDimensionPixelSize(
                            R.dimen.desktop_mode_pinned_header_button_corner_radius
                        ),
                    drawableInsets =
                        getDrawableInsets(R.dimen.desktop_mode_pinned_header_button_inset),
                )
        }

        settingsButton.apply {
            imageTintList = colorStateList
            background =
                createBackgroundDrawable(
                    color = foregroundColor,
                    cornerRadius =
                        getDimensionPixelSize(
                            R.dimen.desktop_mode_pinned_header_button_corner_radius
                        ),
                    drawableInsets =
                        getDrawableInsets(R.dimen.desktop_mode_pinned_header_button_inset),
                )
        }
    }

    fun setAppName(name: CharSequence) {
        closeButton.contentDescription =
            rootView.context.getString(R.string.close_button_text, name)
    }

    private fun getDrawableInsets(@DimenRes res: Int) =
        DrawableInsets(
            vertical = getDimensionPixelSize(res),
            horizontal = getDimensionPixelSize(res),
        )

    private fun getDimensionPixelSize(@DimenRes res: Int) =
        rootView.context.resources.getDimensionPixelSize(res)

    override fun setTaskFocusState(taskFocusState: Boolean) {
        (captionView as WindowDecorLinearLayout).setTaskFocusState(taskFocusState)
    }

    override fun onHandleMenuOpened() = Unit

    override fun onHandleMenuClosed() = Unit

    override fun close() {}
}
