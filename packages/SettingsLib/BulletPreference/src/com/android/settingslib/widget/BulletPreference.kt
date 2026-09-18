/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.bullet.R

/**
 * The BulletPreference shows a text which describe a feature.
 */
class BulletPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    init {
        layoutResource = R.layout.settingslib_expressive_bullet_preference
    }

    /**
     * Learn more affordance to be shown at the bottom of the [BulletPreference], this will only
     * become visible once this is set.
     */
    var learnMore: LearnMore? = null
        set(value) {
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false

        val learnMoreTextView = holder.findViewById(R.id.settingslib_learn_more) as TextView

        setupLearnMoreView(learnMoreTextView, learnMore)

        // Set clickable to false to ensure that the BulletPreference works correctly with TalkBack,
        // Voice access and Switch Access. This will ensure it's selectable, but not tappable.
        holder.itemView.isClickable = false
    }

    private fun setupLearnMoreView(textView: TextView, learnMore: LearnMore?) {
        if (learnMore == null) {
            textView.isGone = true
            return
        }

        textView.isVisible = true
        val learnMoreText = when (learnMore.text) {
            is LearnMoreText.WithString -> { learnMore.text.string }
            is LearnMoreText.WithResId -> { context.getString(learnMore.text.id) }
        }

        textView.text = SpannableString(learnMoreText).apply {
            setSpan(UnderlineSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                LearnMoreSpan(learnMore.listener),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        textView.movementMethod = LinkMovementMethodCompat.getInstance()
    }

    /** A sealed class representing the source of the learn more text. */
    sealed interface LearnMoreText {
        class WithString(val string: String) : LearnMoreText
        class WithResId(@StringRes val id: Int) : LearnMoreText
    }

    /** A data class holding the information for the learn more affordance. */
    data class LearnMore(
        val text: LearnMoreText,
        val listener: View.OnClickListener
    )

    private class LearnMoreSpan(private val listener: View.OnClickListener) : ClickableSpan() {
        override fun onClick(widget: View) {
            listener.onClick(widget)
        }
    }
}