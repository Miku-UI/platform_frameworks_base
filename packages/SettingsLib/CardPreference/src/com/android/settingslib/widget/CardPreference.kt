/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.card.R
import com.android.settingslib.widget.theme.R as ThemeR

/**
 * The CardPreference shows a card like suggestion in homepage, which also support additional action
 * like dismiss.
 */
open class CardPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {
    private var actionIcon: Drawable? = null
    private var actionIconContentDescription: CharSequence? = null
    private var action: ((CardPreference) -> Unit)? = null

    init {
        layoutResource = R.layout.settingslib_expressive_preference_card
    }

    fun useDismissAction() =
        setAdditionalAction(
            ThemeR.drawable.settingslib_expressive_icon_close,
            context.getString(ThemeR.string.settingslib_dismiss_button_content_description),
        ) {
            it.isVisible = false
        }

    fun setAdditionalAction(
        @DrawableRes icon: Int,
        contentDescription: CharSequence?,
        action: ((CardPreference) -> Unit)?,
    ) = setAdditionalAction(context.getDrawable(icon), contentDescription, action)

    fun setAdditionalAction(
        icon: Drawable?,
        contentDescription: CharSequence?,
        action: ((CardPreference) -> Unit)?,
    ) {
        actionIcon = icon
        actionIconContentDescription = contentDescription
        this.action = action
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedBelow = false
        holder.isDividerAllowedAbove = false
        (holder.findViewById(android.R.id.icon1) as ImageView).apply {
            visibility = if (actionIcon != null) View.VISIBLE else View.GONE
            setImageDrawable(actionIcon)
            contentDescription = actionIconContentDescription
            setOnClickListener { action?.invoke(this@CardPreference) }
            setClickable(action != null)
        }
    }
}
