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

package android.processor.devicepolicy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Define the conflict resolution mechanism for a list policy.
 *
 * <p>This expresses which value takes effect when multiple values are set for the same policy.
 *
 * <p>Exactly one option must be selected.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface ListResolutionMechanism {
    /**
     * Use a custom resolution mechanism hard-coded in the code.
     *
     * <p>Only use this option if none of the other resolution mechanism apply.
     */
    boolean custom() default false;

    /**
     * The resolved policy will contain the union of all values set.
     * Duplicate values will be present exactly once in the resulting union.
     */
    boolean union() default false;
}
