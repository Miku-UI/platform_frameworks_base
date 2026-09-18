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
package com.android.hoststubgen.hosthelper;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation injected to all methods that are processed as "redirect".
 *
 * - It's set to individual methods with the target information
 * - It's also set to their enclosing classes, without any parameter.
 */
@Target({METHOD, TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HostStubGenProcessedAsRedirect {
    String CLASS_INTERNAL_NAME = HostTestUtils.getInternalName(
            HostStubGenProcessedAsRedirect.class);
    String CLASS_DESCRIPTOR = "L" + CLASS_INTERNAL_NAME + ";";

    String TARGET_CLASS_FIELD = "targetClass";
    String TARGET_METHOD_FIELD = "targetMethod";
    String TARGET_DESC_FIELD = "targetDesc";

    /** Redirection target class name as an internal name. e.g. "com/android/...." */
    String targetClass() default "";

    /** Redirection target method name. */
    String targetMethod() default "";

    /** Redirection target method descriptor. */
    String targetDesc() default "";
}
