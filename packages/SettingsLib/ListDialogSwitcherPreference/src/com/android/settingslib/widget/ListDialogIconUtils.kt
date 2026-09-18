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
package com.android.settingslib.widget

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory

/**
 * Sets the [Drawable] on this [ImageView] and applies a circular clipping outline.
 *
 * This is a convenience function to ensure that icons displayed in the dialog and preference
 * have a consistent circular shape.
 *
 * @param drawable The [Drawable] to set as the image source.
 */
internal fun ImageView.setCircularIcon(drawable: Drawable?) {
    if (drawable == null) {
        visibility = View.GONE
        return
    }

    visibility = View.VISIBLE
    if (drawable is BitmapDrawable) {
        val roundedDrawable = RoundedBitmapDrawableFactory.create(
            context.resources, drawable.bitmap
        ).apply {
            isCircular = true
        }
        setImageDrawable(roundedDrawable)
        background = null
        clipToOutline = false
    } else {
        // For other drawable types, clip them to a circle.
        setImageDrawable(drawable)
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
        }
        background = shape
        clipToOutline = true
    }
}