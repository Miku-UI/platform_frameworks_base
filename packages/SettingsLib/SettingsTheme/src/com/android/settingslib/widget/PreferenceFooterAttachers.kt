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

import android.graphics.drawable.Drawable
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.theme.R

/**
 * Holds the configuration for a single preference footer, which can contain an optional image and
 * optional text content.
 *
 * @property image The optional image content for the footer.
 * @property text The optional text content for the footer.
 */
data class FooterData(
    val image: ImageContent? = null,
    val text: TextContent? = null,
) {
    /**
     * Represents the image content for a preference footer.
     *
     * @property imageRes The drawable resource ID for the image.
     * @property description The content description for the image.
     */
    data class ImageContent(
        @DrawableRes val imageRes: Int? = null,
        val imageDrawable: Drawable? = null,
        val description: String? = null,
    ) {
        init {
            require(imageRes != null || imageDrawable != null) {
                "Either imageRes or imageDrawable must be provided."
            }
            require(imageRes == null || imageDrawable == null) {
                "Cannot provide both imageRes and imageDrawable."
            }
        }
    }

    /**
     * Represents the text content for a preference footer.
     *
     * @property text The character sequence to display.
     * @property listener The click listener for the text.
     */
    data class TextContent(val text: CharSequence, val listener: View.OnClickListener?)
}

/**
 * Finds or creates the footer view and binds the provided data to it.
 * Handles view recycling by explicitly showing/hiding the footer.
 */
fun bindFooter(holder: PreferenceViewHolder, data: FooterData?) {
    val container = holder.itemView.findViewById<View>(R.id.settingslib_expressive_footer_container)
    val imageView = holder.itemView.findViewById<ImageView>(R.id.settingslib_expressive_footer_image)
    val textView = holder.itemView.findViewById<TextView>(R.id.settingslib_expressive_footer_text)

    if (container == null || imageView == null || textView == null) {
        return // Views not found, do nothing.
    }

    val hasImage = data?.image != null
    val hasText = data?.text != null && data.text.text.isNotEmpty()

    if (hasImage) {
        data.image.let {
            if (it.imageRes != null) {
                imageView.setImageResource(it.imageRes)
            } else {
                imageView.setImageDrawable(it.imageDrawable)
            }
            imageView.contentDescription = it.description
        }
        imageView.visibility = View.VISIBLE
    } else {
        imageView.visibility = View.GONE
    }

    // Bind text content
    if (hasText) {
        data.text.let {
            textView.text = it.text
            textView.setOnClickListener(it.listener)
            textView.movementMethod = LinkMovementMethod.getInstance()
        }
        val icon = holder.itemView.findViewById<View>(android.R.id.icon)
        if (icon != null && icon.isVisible) {
            textView.setPaddingRelative(
                textView.resources.getDimensionPixelSize(
                    R.dimen.settingslib_expressive_preference_footer_padding_start
                ),
                textView.paddingTop,
                textView.paddingEnd,
                textView.paddingBottom
            )
        } else {
            textView.setPaddingRelative(0, textView.paddingTop, textView.paddingEnd, textView.paddingBottom)
        }
        textView.visibility = View.VISIBLE
    } else {
        textView.visibility = View.GONE
    }

    // Set container visibility
    container.visibility = if (hasImage || hasText) View.VISIBLE else View.GONE
}
