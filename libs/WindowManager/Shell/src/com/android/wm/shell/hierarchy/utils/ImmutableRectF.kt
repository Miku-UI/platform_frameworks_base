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
package com.android.wm.shell.hierarchy.utils

import android.graphics.Rect
import android.graphics.RectF

/**
 * Utility functions to support querying/manipulating the hierarchy.
 */
class ImmutableRectF : RectF {

    constructor() : super()
    constructor(r: Rect) : super(r)
    constructor(r: RectF) : super(r)
    constructor(l: Float, t: Float, r: Float, b: Float) : super(l, t, r, b)

    override fun setEmpty() {
        throw IllegalStateException("Not supported")
    }

    override fun set(p0: Float, p1: Float, p2: Float, p3: Float) {
        throw IllegalStateException("Not supported")
    }

    override fun set(p0: RectF) {
        throw IllegalStateException("Not supported")
    }

    override fun set(p0: Rect) {
        throw IllegalStateException("Not supported")
    }

    override fun offset(p0: Float, p1: Float) {
        throw IllegalStateException("Not supported")
    }

    override fun offsetTo(p0: Float, p1: Float) {
        throw IllegalStateException("Not supported")
    }

    override fun inset(p0: Float, p1: Float) {
        throw IllegalStateException("Not supported")
    }
}
