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

package com.android.systemui.privacy

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity.CENTER_VERTICAL
import android.view.Gravity.END
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import com.android.systemui.Flags
import com.android.systemui.animation.Expandable
import com.android.systemui.res.R
import com.android.systemui.statusbar.events.BackgroundAnimatableView
import java.time.Duration

abstract class AbstractOngoingPrivacyChip
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttrs: Int = 0,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes), BackgroundAnimatableView {

    init {
        id = R.id.privacy_chip
        layoutParams = LayoutParams(WRAP_CONTENT, MATCH_PARENT, CENTER_VERTICAL or END)
        clipChildren = true
        clipToPadding = true
    }

    abstract var privacyList: List<PrivacyItem>

    abstract val launchableContentView: View

    // This is lazy for OngoingPrivacyChip where launchableContentView is created in its init
    // (which happens after the init of this class). In running code, this will never be executed
    // for OngoingPrivacyChip.
    open val expandable: Expandable by lazy { Expandable.fromView(launchableContentView) }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (Flags.privacyDotLiveRegion()) {
            info.setMinDurationBetweenContentChanges(Duration.ofSeconds(10L))
        }
    }

    /**
     * String with list of types joined for content description.
     *
     * Use [PrivacyChipContentDescriptionGenerator] to generate.
     */
    protected fun setContentDescriptions(joinedTypes: String) {
        contentDescription =
            context.getString(R.string.ongoing_privacy_chip_content_multiple_apps, joinedTypes)
    }
}
