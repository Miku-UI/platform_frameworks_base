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

package com.android.systemui.privacy.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.android.systemui.animation.Expandable
import com.android.systemui.animation.view.LaunchableFrameLayout
import com.android.systemui.privacy.AbstractOngoingPrivacyChip
import com.android.systemui.privacy.PrivacyChipContentDescriptionGenerator
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.privacy.ui.compose.PrivacyChipContainer

class ComposeOngoingPrivacyChip
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttrs: Int = 0,
    defStyleRes: Int = 0,
) : AbstractOngoingPrivacyChip(context, attrs, defStyleAttrs, defStyleRes) {

    private var privacyTypes by mutableStateOf(emptySet<PrivacyType>())

    private val contentDescriptionGenerator = PrivacyChipContentDescriptionGenerator(this.context)

    override val expandable = Expandable()

    var showPrivacyText by mutableStateOf(false)

    override var privacyList: List<PrivacyItem> = emptyList()
        set(value) {
            if (value != field) {
                privacyTypes =
                    value
                        .map { it.privacyType }
                        .toSet()
                        .also {
                            setContentDescriptions(
                                contentDescriptionGenerator.joinTypesForContentDescription(it)
                            )
                        }
                field = value
            }
        }

    private val chipComposableView =
        ComposeView(context).apply {
            setContent {
                PrivacyChipContainer(
                    privacyTypes,
                    showPrivacyText = showPrivacyText,
                    expandable = expandable,
                )
            }
        }

    private val launchableView = LaunchableFrameLayout(context)

    init {
        val layoutParams =
            LayoutParams(
                /* width= */ LayoutParams.WRAP_CONTENT,
                /* height= */ LayoutParams.WRAP_CONTENT,
                /* gravity= */ Gravity.CENTER,
            )
        launchableView.addView(chipComposableView)
        addView(launchableView, layoutParams)
    }

    override val launchableContentView: View
        get() = launchableView

    /**
     * When animating as a chip in the status bar, we want to animate the width for the container of
     * the privacy items. We have to subtract our own top and left offset because the bounds come to
     * us as absolute on-screen bounds, and `launchableView` is laid out relative to the frame
     * layout's bounds.
     */
    override fun setBoundsForAnimation(l: Int, t: Int, r: Int, b: Int) {
        launchableContentView.setLeftTopRightBottom(l - left, t - top, r - left, b - top)
    }
}
