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
package com.android.systemui.util

import android.view.MotionEvent
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertAbout

/** Truth [Subject] for [MotionEvent]. */
class MotionEventSubject
private constructor(metadata: FailureMetadata, private val actual: MotionEvent?) :
    Subject(metadata, actual) {
    companion object {
        private val FACTORY =
            Factory<MotionEventSubject, MotionEvent> { metadata, actual ->
                MotionEventSubject(metadata, actual)
            }

        fun assertThat(actual: MotionEvent?): MotionEventSubject {
            return assertAbout(FACTORY).that(actual)
        }
    }

    /**
     * Assert that the action returned by [MotionEvent.getActionMasked] is an
     * [MotionEvent.ACTION_DOWN].
     */
    fun isDown() = maskedActionEqualTo(MotionEvent.ACTION_DOWN)

    /**
     * Assert that the action returned by [MotionEvent.getActionMasked] is an
     * [MotionEvent.ACTION_MOVE].
     */
    fun isMove() = maskedActionEqualTo(MotionEvent.ACTION_MOVE)

    /**
     * Assert that the action returned by [MotionEvent.getActionMasked] is an
     * [MotionEvent.ACTION_UP].
     */
    fun isUp() = maskedActionEqualTo(MotionEvent.ACTION_UP)

    /**
     * Assert that the action returned by [MotionEvent.getActionMasked] is an
     * [MotionEvent.ACTION_CANCEL].
     */
    fun isCancel() = maskedActionEqualTo(MotionEvent.ACTION_CANCEL)

    /**
     * Assert that the action returned by [MotionEvent.getActionMasked] is an
     * [MotionEvent.ACTION_POINTER_DOWN].
     */
    fun isPointerDown() = maskedActionEqualTo(MotionEvent.ACTION_POINTER_DOWN)

    /**
     * Assert that the action returned by [MotionEvent.getActionMasked] is an
     * [MotionEvent.ACTION_POINTER_UP].
     */
    fun isPointerUp() = maskedActionEqualTo(MotionEvent.ACTION_POINTER_UP)

    /** Fails if the event doesn't have the given [MotionEvent.getPointerCount]. */
    fun hasPointerCount(count: Int) {
        checkNotNull(actual)
        check("getPointerCount()").that((actual.pointerCount)).isEqualTo(count)
    }

    /** Asserts that the action associated with this event matches the expected one. */
    private fun maskedActionEqualTo(action: Int) {
        checkNotNull(actual)
        check("getActionMasked()").that(actual.actionMasked).isEqualTo(action)
    }
}
