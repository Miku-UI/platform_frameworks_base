/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.systemui.accessibility.floatingmenu

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.android.internal.accessibility.common.ShortcutConstants
import com.android.internal.accessibility.dialog.AccessibilityTarget
import com.android.systemui.accessibility.floatingmenu.R as FloatingMenuR
import com.android.systemui.res.R

/**
 * A simple data class used to identify the "More Options" button in the menu list. It holds no
 * logic; its behavior is handled by the AccessibilityTargetAdapter and MenuViewLayer.
 */
class MoreOptionsTarget(private val context: Context) :
    AccessibilityTarget(
        context,
        ShortcutConstants.UserShortcutType.SOFTWARE,
        ShortcutConstants.AccessibilityFragmentType.LAUNCH_ACTIVITY,
        /* isShortcutSwitched= */ false,
        /* id= */ ID,
        /* uid= */ -1,
        context.getString(R.string.floating_menu_more_options_label),
        ContextCompat.getDrawable(context, FloatingMenuR.drawable.ic_more_vert_themed),
        /* key= */ ID,
    ) {

    override fun getIcon(): Drawable? {
        return ContextCompat.getDrawable(context, FloatingMenuR.drawable.ic_more_vert_themed)
    }

    companion object {
        const val ID = "more_options_target_id"
    }
}
