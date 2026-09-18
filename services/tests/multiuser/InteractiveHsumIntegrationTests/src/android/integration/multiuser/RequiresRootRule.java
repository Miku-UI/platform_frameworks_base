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
package android.integration.multiuser;

import static android.integration.multiuser.InstrumentationHelper.isAdbRoot;

import static org.junit.Assume.assumeTrue;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Rule that skips a test when not running as root */
public final class RequiresRootRule implements TestRule {

    private final String mReason;

    /**
     * Default constructor.
     *
     * @param reason message to show when the test is skipped.
     */
    public RequiresRootRule(String reason) {
        mReason = reason;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assumeTrue("Test " + description.getMethodName() + " in "
                        + description.getClassName() + " requires root. Reason: " + mReason,
                        isAdbRoot());
                base.evaluate();
            }
        };
    }
}
