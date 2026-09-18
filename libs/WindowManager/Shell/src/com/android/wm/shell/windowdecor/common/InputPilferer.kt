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
package com.android.wm.shell.windowdecor.common

import android.hardware.input.InputManager
import android.view.View

/** A helper class to pilfer pointers from a view. */
interface InputPilferer {
    /** Pilfers pointers from the input channel of the given view's window. */
    fun pilferPointers(v: View)
}

/** The default implementation of [InputPilferer]. */
class InputPilfererImpl(private val inputManager: InputManager?) : InputPilferer {
    override fun pilferPointers(v: View) {
        val inputToken = v.viewRootImpl?.inputToken ?: return
        inputManager?.pilferPointers(inputToken)
    }
}
