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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.row

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.widget.ImageView
import com.android.systemui.res.R

class NotificationDismissButton
@JvmOverloads
constructor(private val context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ImageView(context, attrs, defStyleAttr) {

    private lateinit var pillDrawable: Drawable

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateTint()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        val layerDrawable = background as LayerDrawable
        pillDrawable = layerDrawable.findDrawableByLayerId(R.id.dismiss_button_pill_colorized_layer)

        updateTint()
    }

    private fun updateTint() {
        val backgroundTintColor =
            context.resources.getColor(
                com.android.internal.R.color.materialColorSurfaceBright,
                context.theme,
            )

        pillDrawable.setTintList(ColorStateList.valueOf(backgroundTintColor))

        val iconTintColor =
            context.resources.getColor(
                com.android.internal.R.color.materialColorOnSurface,
                context.theme,
            )

        imageTintList = ColorStateList.valueOf(iconTintColor)
    }
}
