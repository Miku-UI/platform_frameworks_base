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

package com.android.wm.shell.flicker.bubbles.utils

import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A [TestRule] that skips a test if the given [condition] is false.
 *
 * This rule evaluates a [condition] lambda. If the condition returns `false`, the test will be
 * skipped with an [AssumptionViolatedException] containing the provided [message].
 *
 * This is primarily used to conditionally execute tests based on device state or other runtime
 * factors (e.g. "only run this test on large screen devices").
 *
 * @param condition a lambda function that must return `true` for the test to proceed. If it returns
 *   `false`, the test is skipped.
 * @param message the message to be reported by JUnit if the test is skipped because the assumption
 *   (condition) failed.
 * @see assumeTrue
 */
class AssumptionRule(private val condition: () -> Boolean, private val message: String) : TestRule {

    override fun apply(base: Statement, description: Description) =
        object : Statement() {
            override fun evaluate() {
                // If condition() is false, this throws an AssumptionViolatedException,
                // which JUnit interprets as "skip this test".
                assumeTrue(message, condition())

                // If assumeTrue passes, evaluate the actual test.
                base.evaluate()
            }
        }
}
