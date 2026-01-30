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

package com.android.systemui.communal.posturing.shared.model

import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableRowLogger

sealed interface PosturedState : Diffable<PosturedState> {
    val isStationary: Boolean
    val inOrientation: Boolean

    override fun logDiffs(prevVal: PosturedState, row: TableRowLogger) {
        if (prevVal != this) {
            row.logChange(COL_POSTURED_STATE, toString())
        }
    }

    /** Represents postured state */
    data object Postured : PosturedState {
        override val isStationary: Boolean = true
        override val inOrientation: Boolean = true
    }

    /** Represents state where we may be postured but we aren't sure yet */
    data class MayBePostured(
        override val isStationary: Boolean,
        override val inOrientation: Boolean,
    ) : PosturedState

    /** Represents unknown/uninitialized state */
    data object Unknown : PosturedState {
        override val isStationary: Boolean = false
        override val inOrientation: Boolean = false
    }

    /** Represents state where we are not postured */
    data class NotPostured(
        override val isStationary: Boolean,
        override val inOrientation: Boolean,
    ) : PosturedState

    companion object {
        const val COL_POSTURED_STATE = "posturedState"
    }
}
