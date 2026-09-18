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

package com.android.wm.shell.dagger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Just an annotation for code that is only used in a downstream Shell implementation, but should
 * not be mistaken for dead code with no usages.
 *
 * Example:
 *   @UsedDownstream(product="wear,auto")
 *   fun looksLikeDeadCode() {
 *       ...
 *   }
 */
@Retention(RetentionPolicy.SOURCE)
public @interface UsedDownstream {
    String product();
}