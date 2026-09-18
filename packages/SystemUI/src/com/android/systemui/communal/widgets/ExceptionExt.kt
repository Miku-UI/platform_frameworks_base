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
 * See the License for the a specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.communal.widgets

import android.os.DeadObjectException
import android.os.TransactionTooLargeException

/**
 * Returns true if the exception is a binder buffer size error, or wraps one. See b/14255011 and
 * b/402970061 for more context.
 */
fun Throwable.isBinderSizeError(): Boolean {
    return this is TransactionTooLargeException ||
        this is DeadObjectException ||
        cause?.isBinderSizeError() == true
}
