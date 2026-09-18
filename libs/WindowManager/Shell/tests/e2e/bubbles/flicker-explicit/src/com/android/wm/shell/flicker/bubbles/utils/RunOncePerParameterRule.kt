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

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement

/**
 * A [TestRule] that executes a given [wrappedRule] only once per unique set of parameters per test.
 *
 * This rule ensures the [wrappedRule] is evaluated only the *first* time a test is run with a new
 * set of [params]. Subsequent tests using the same parameters will bypass the [wrappedRule]'s logic
 * and execute the test statement directly. This is useful for [Parameterized] tests which execute
 * the whole class of tests with the same parameters.
 *
 * @param testClass test class that this rule is executed in
 * @param wrappedRule the rule that we only want to apply once per test parameter change.
 * @param params the set of test parameters for the current execution. The [wrappedRule] will be
 *   re-executed whenever this set changes from the previous test run.
 */
class RunOncePerParameterRule(
    private val testClass: KClass<out Any>,
    private val wrappedRule: TestRule,
    private vararg val params: Any,
) : TestRule {

    override fun apply(base: Statement, description: Description) =
        object : Statement() {
            override fun evaluate() {
                // Check if we are in a new test or the parameters for this test run are new.
                if (testClass != prevTestClass || !params.contentEquals(prevParams)) {
                    prevTestClass = testClass
                    prevParams = params
                    isExecuted.set(false)
                }

                // Decide which statement to run.
                val statementToRun =
                    if (isExecuted.compareAndSet(false /* expect */, true /* update */)) {
                        // First time for this param set: apply the wrapped rule, which in turn will
                        // execute 'base'.
                        wrappedRule.apply(base, description)
                    } else {
                        // Subsequent times: just run 'base' directly, skipping the wrapped rule.
                        base
                    }

                statementToRun.evaluate()
            }
        }

    companion object {
        // Static fields to track execution state across test methods.
        private val isExecuted = AtomicBoolean()
        private var prevTestClass: KClass<out Any> = Any::class
        private var prevParams: Array<out Any> = emptyArray()
    }
}
