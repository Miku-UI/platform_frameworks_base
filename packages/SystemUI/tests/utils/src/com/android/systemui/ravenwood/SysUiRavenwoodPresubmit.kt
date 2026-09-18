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
package com.android.systemui.ravenwood

/**
 * Annotate tests to run in presubmit on Ravenwood.
 *
 * All those presubmit tests must be kept passing.
 *
 * If [neverDisable] is true, it indicates the target test is a "core" test that should never be
 * disabled.
 *
 * If [neverDisable] is false, the target test must still be passing and maintained, but if a change
 * introduces dependencies to Ravenwood-unsupported APIs that cannot be easily supported, it's okay
 * to disable the affected tests with [android.platform.test.annotations.DisabledOnRavenwood].
 *
 * There's no automated check on [neverDisable], so it needs to be checked manually.
 *
 * See go/sysui-ravenwood for more details.
 *
 * @see SysUiRavenwoodCorePresubmit
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SysUiRavenwoodPresubmit(
    /** If true, the target test should not be disabled. */
    val neverDisable: Boolean = false
)
