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
package android.platform.test.ravenwood;

import androidx.test.internal.runner.junit3.AndroidTestResultAccess;
import androidx.test.internal.runner.junit3.DelegatingFilterableTestSuiteAccess;
import androidx.test.internal.runner.junit3.NonLeakyTestSuite;

import junit.framework.Protectable;
import junit.framework.Test;
import junit.framework.TestResult;

import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs JUnit3 test class with additional Ravenwood support.
 */
public class RavenwoodAwareJUnit3TestSuite extends DelegatingFilterableTestSuiteAccess {

    public RavenwoodAwareJUnit3TestSuite(Class<?> testClass) {
        super(new NonLeakyTestSuite(testClass));
    }

    static class RavenwoodTestResult extends AndroidTestResultAccess {
        RavenwoodTestResult(TestResult result) {
            super(result);
        }

        @Override
        public void runProtected(Test test, Protectable p) {
            var ruleList = new ArrayList<TestRule>(2);
            ruleList.add(RavenwoodAwareTestRunner.sImplicitInstOuterRule);
            if (test instanceof RavenwoodRule.Provider provider) {
                ruleList.add(provider.getRavenwoodRule());
            }

            runProtectedWithRules(test, p, ruleList);
        }

        private void runProtectedWithRules(Test test, Protectable p, List<TestRule> testRules) {
            final var desc = makeDescription(test);
            Statement stmt = new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    p.protect();
                }
            };
            // Apply the rules in the inverse order, since the last rule is the innermost rule.
            for (TestRule rule : testRules.reversed()) {
                stmt = rule.apply(stmt, desc);
            }
            super.runProtected(test, stmt::evaluate);
        }
    }

    @Override
    public void run(TestResult result) {
        super.run(new RavenwoodTestResult(result));
    }
}
