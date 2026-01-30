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

package com.android.systemui.statusbar.notification.row.wrapper

import android.content.Context
import android.view.View
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.Roundable
import com.android.systemui.statusbar.notification.RoundableState
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow

/**
 * Wraps a bundle header view so that it can be associated with a Roundable. This allows it to be
 * handled as one of the relevant corner re-rounding targets when adjacent to a swiped notification.
 */
class BundleHeaderViewWrapper(
    val context: Context,
    private val view: View,
    row: ExpandableNotificationRow,
) : NotificationViewWrapper(context, view, row), Roundable {
    override val clipHeight: Int
        get() = view.height

    override var roundableState: RoundableState =
        RoundableState(
            view,
            this,
            context.resources.getDimension(R.dimen.notification_bundle_header_height) / 2.0f,
        )

    var onRoundnessChangedListener: RoundnessChangedListener? = null

    override fun applyRoundnessAndInvalidate() {
        // We cannot apply the rounded corner to this View, so our parents (in drawChild()) will
        // clip our canvas. So we should invalidate our parent.
        onRoundnessChangedListener?.applyRoundnessAndInvalidate()
        super.applyRoundnessAndInvalidate()
    }

    // We may need to re-calculate the max radius if the font or display size changes.
    fun recalculateRadius() {
        roundableState.setMaxRadius(
            context.resources.getDimension(R.dimen.notification_bundle_header_height) / 2.0f
        )
        applyRoundnessAndInvalidate()
    }

    /** Interface to handle Roundness changes */
    interface RoundnessChangedListener {
        /** This method will be called when this class call applyRoundnessAndInvalidate() */
        fun applyRoundnessAndInvalidate()
    }
}
