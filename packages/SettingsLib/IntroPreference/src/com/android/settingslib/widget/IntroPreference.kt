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
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.intro.R

class IntroPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes),
    GroupSectionDividerMixin,
    OnScreenWidgetMixin {

    private var isCollapsable: Boolean = DEFAULT_COLLAPSABLE
    private var minLines: Int = DEFAULT_MIN_LINES
    private var hyperlinkListener: View.OnClickListener? = null
    private var learnMoreListener: View.OnClickListener? = null
    private var learnMoreText: CharSequence? = null
    private var iconType: IconType = IconType.APP_ICON
    private var isInitialized = false
    private var useExpressiveIconFromXml: Boolean = false

    init {
        layoutResource = R.layout.settingslib_expressive_preference_intro
        isSelectable = false
        isInitialized = true

        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.IntroPreference) {
                useExpressiveIconFromXml = getBoolean(
                    R.styleable.IntroPreference_useExpressiveIcon, false
                )
            }

            this.iconType = if (useExpressiveIconFromXml) {
                IconType.EXPRESSIVE_ICON
            } else {
                IconType.APP_ICON
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedBelow = false
        holder.isDividerAllowedAbove = false

        (holder.findViewById(R.id.collapsable_summary) as? CollapsableTextView)?.apply {
            setCollapsable(isCollapsable)
            setMinLines(minLines)
            visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
            summary?.let { setText(it.toString()) }
            hyperlinkListener?.let { setHyperlinkListener(it) }
            learnMoreListener?.let {
                setLearnMoreText(learnMoreText)
                setLearnMoreAction(it)
            }
        }

        val iconView: ImageView? = holder.findViewById(android.R.id.icon) as? ImageView
        val iconBackgroundView: ImageView? =
            holder.findViewById(R.id.icon_background) as? ImageView

        val bottomSpacer = holder.findViewById(R.id.entity_header_bottom_spacer)
        bottomSpacer?.visibility = if (summary.isNullOrEmpty()) View.VISIBLE else View.GONE

        if (icon != null) {
            iconView?.let { iv ->
                val layoutParams = iconView.layoutParams
                var size: Int

                when (iconType) {
                    IconType.APP_ICON -> {
                        iconBackgroundView?.visibility = View.GONE
                        size = context.resources.getDimensionPixelSize(
                            com.android.settingslib.widget.theme.R.dimen.settingslib_expressive_space_large3
                        )
                    }
                    IconType.EXPRESSIVE_ICON -> {
                        iconBackgroundView?.visibility = View.VISIBLE

                        iv.imageTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(
                                context, com.android.settingslib.widget.theme.R.color.settingslib_materialColorOnSecondaryContainer
                            )
                        )

                        size = context.resources.getDimensionPixelSize(
                            com.android.settingslib.widget.theme.R.dimen.settingslib_expressive_space_medium3
                        )
                    }
                }
                layoutParams?.width = size
                layoutParams?.height = size
                iv.layoutParams = layoutParams
            }
        }
    }

    /**
     * Sets whether the summary is collapsable.
     *
     * @param collapsable True if the summary should be collapsable, false otherwise.
     */
    fun setCollapsable(collapsable: Boolean) {
        isCollapsable = collapsable
        minLines = if (isCollapsable) DEFAULT_MIN_LINES else DEFAULT_MAX_LINES
        notifyChanged()
    }

    /**
     * Sets the minimum number of lines to display when collapsed.
     *
     * @param lines The minimum number of lines.
     */
    fun setMinLines(lines: Int) {
        minLines = lines.coerceIn(1, DEFAULT_MAX_LINES)
        notifyChanged()
    }

    /**
     * Sets the action when clicking on the hyperlink in the text.
     *
     * @param listener The click listener for hyperlink.
     */
    fun setHyperlinkListener(listener: View.OnClickListener) {
        if (hyperlinkListener != listener) {
            hyperlinkListener = listener
            notifyChanged()
        }
    }

    /**
     * Sets the action when clicking on the learn more view.
     *
     * @param listener The click listener for learn more.
     */
    fun setLearnMoreAction(listener: View.OnClickListener) {
        if (learnMoreListener != listener) {
            learnMoreListener = listener
            notifyChanged()
        }
    }

    /**
     * Sets the text of learn more view.
     *
     * @param text The text of learn more.
     */
    fun setLearnMoreText(text: CharSequence) {
        if (!TextUtils.equals(learnMoreText, text)) {
            learnMoreText = text
            notifyChanged()
        }
    }

    /**
     * Sets the icon as an app icon (without background).
     *
     * @param icon The drawable to be used as the icon.
     */
    fun setAppIcon(icon: Drawable) {
        if (icon != getIcon()) {
            this.iconType = IconType.APP_ICON
            super.setIcon(icon)
        } else if (iconType != IconType.APP_ICON) {
            this.iconType = IconType.APP_ICON
            notifyChanged()
        }
    }

    /**
     * Sets the icon as an expressive icon (with background).
     *
     * @param icon The drawable to be used as the icon.
     */
    fun setExpressiveIcon(icon: Drawable) {
        if (icon != getIcon()) {
            this.iconType = IconType.EXPRESSIVE_ICON
            super.setIcon(icon)
        } else if (iconType != IconType.EXPRESSIVE_ICON) {
            this.iconType = IconType.EXPRESSIVE_ICON
            notifyChanged()
        }
    }

    /**
     * Sets the icon as an expressive icon (with background).
     *
     * @param iconResId The resource ID of the drawable to be used as the icon.
     */
    fun setExpressiveIcon(@DrawableRes iconResId: Int) {
        ContextCompat.getDrawable(context, iconResId)?.let {
            setExpressiveIcon(it)
        }
    }

    /** @suppress */
    @Deprecated("Use setAppIcon or setExpressiveIcon instead.", ReplaceWith(""))
    override fun setIcon(icon: Drawable?) {
        val newIconType = IconType.APP_ICON
        if (icon != getIcon()) {
            this.iconType = newIconType
            super.setIcon(icon)
        } else if (iconType != newIconType) {
            this.iconType = newIconType
            notifyChanged()
        }
    }

    private enum class IconType {
        APP_ICON,
        EXPRESSIVE_ICON,
    }

    companion object {
        private const val DEFAULT_MAX_LINES = 50
        private const val DEFAULT_MIN_LINES = 1
        private const val DEFAULT_COLLAPSABLE = false
    }
}
