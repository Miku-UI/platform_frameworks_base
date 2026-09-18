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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.platform.test.ravenwood;

import android.util.Log;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.model.MultipleFailureException;

import java.util.ArrayList;
import java.util.Stack;

/**
 * A run notifier that wraps another notifier and provides the following features:
 * - Handle a failure that happened before testStarted and testEnded (typically that means
 *   it's from @BeforeClass or @AfterClass, or a @ClassRule) and deliver it as if
 *   individual tests in the class reported it. This is for b/364395552.
 *
 * - Logging.
 *
 * - AssumptionViolatedException passed in as failures will be treated as assumption failures
 *   to support @DisabledOnRavenwood on JUnit3 tests.
 */
class RavenwoodRunNotifier extends RunNotifier {
    private final RunNotifier mRealNotifier;

    private final Stack<Description> mSuiteStack = new Stack<>();
    private Description mCurrentSuite = null;
    private final ArrayList<Throwable> mOutOfTestFailures = new ArrayList<>();

    /**
     * If true, we're outside of a single "test" execution (which includes
     * execution of {@code @Before/After} and various injected code from the instance rules).
     * Typically, when it's true, we're in a {@code @BeforeClass}, but it could be
     * from the test runner.
     */
    private boolean mInBeforeClass = true;
    /**
     * Similar to the above flag.  Typically, when it's true, we're in a
     * {@code @AfterClass}, but it could be from the test runner.
     */
    private boolean mInAfterClass = false;
    /**
     * When a {@code @Before} method throws, junit will skip the rest of the {@code @Before}
     * methods and the test method itself, but {@code @After} methods will still be executed.
     * Then, if any of the {@code @After} methods also fail, we'd report multiple failures
     * from a single test. If this happens, Tradefed would only report the
     * last exception, which is not useful at all because we're more interested
     * in the first exception, which may have caused the subsequent exceptions.
     * We use this flag to report only one failure from each test at most.
     */
    private boolean mAlreadyFailed = false;

    RavenwoodRunNotifier(RunNotifier realNotifier) {
        mRealNotifier = realNotifier;
    }

    private boolean isInTest() {
        return !mInBeforeClass && !mInAfterClass;
    }

    @Override
    public void addListener(RunListener listener) {
        mRealNotifier.addListener(listener);
    }

    @Override
    public void removeListener(RunListener listener) {
        mRealNotifier.removeListener(listener);
    }

    @Override
    public void addFirstListener(RunListener listener) {
        mRealNotifier.addFirstListener(listener);
    }

    @Override
    public void fireTestRunStarted(Description description) {
        Log.i(RavenwoodAwareTestRunner.TAG, "testRunStarted: " + description);
        mRealNotifier.fireTestRunStarted(description);
    }

    @Override
    public void fireTestRunFinished(Result result) {
        Log.i(RavenwoodAwareTestRunner.TAG, "testRunFinished: "
                + result.getRunCount() + ","
                + result.getFailureCount() + ","
                + result.getAssumptionFailureCount() + ","
                + result.getIgnoreCount());
        mRealNotifier.fireTestRunFinished(result);
    }

    @Override
    public void fireTestSuiteStarted(Description description) {
        Log.i(RavenwoodAwareTestRunner.TAG, "testSuiteStarted: " + description);
        mRealNotifier.fireTestSuiteStarted(description);

        mInBeforeClass = true;
        mInAfterClass = false;
        mAlreadyFailed = false;

        // Keep track of the current suite, needed if the outer test is a Suite,
        // in which case its children are test classes. (not test methods)
        mCurrentSuite = description;
        mSuiteStack.push(description);

        mOutOfTestFailures.clear();
    }

    @Override
    public void fireTestSuiteFinished(Description description) {
        Log.i(RavenwoodAwareTestRunner.TAG, "testSuiteFinished: " + description);
        mRealNotifier.fireTestSuiteFinished(description);

        maybeHandleOutOfTestFailures();

        mInBeforeClass = true;
        mInAfterClass = false;

        // Restore the upper suite.
        mSuiteStack.pop();
        mCurrentSuite = mSuiteStack.isEmpty() ? null : mSuiteStack.peek();
    }

    @Override
    public void fireTestStarted(Description description) throws StoppedByUserException {
        Log.i(RavenwoodAwareTestRunner.TAG, "testStarted: " + description);
        mRealNotifier.fireTestStarted(description);

        mInAfterClass = false;
        mInBeforeClass = false;
        mAlreadyFailed = false;
    }

    @Override
    public void fireTestFailure(Failure failure) {
        // Assumptions are a new feature introduced in JUnit4, and is implemented internally
        // with an unhandled AssumptionViolatedException. JUnit3 does not have the infrastructure
        // to handle assumption failures, and will treat AssumptionViolatedException as a normal
        // error. However, we rely on throwing AssumptionViolatedExceptions to support skipping
        // tests annotated with @DisabledOnRavenwood. To handle this situation, we inspect the
        // failure being sent here, and redirect the failure event to an assumption failure event
        // if it is actually an assumption failure.
        if (failure.getException() instanceof AssumptionViolatedException) {
            fireTestAssumptionFailed(failure);
            return;
        }

        Log.i(RavenwoodAwareTestRunner.TAG, "testFailure: " + failure);

        if (!isInTest()) {
            mOutOfTestFailures.add(failure.getException());
        } else if (!mAlreadyFailed) {
            mAlreadyFailed = true;
            mRealNotifier.fireTestFailure(failure);
        }
    }

    @Override
    public void fireTestAssumptionFailed(Failure failure) {
        Log.i(RavenwoodAwareTestRunner.TAG, "testAssumptionFailed: " + failure);

        if (isInTest()) {
            mRealNotifier.fireTestAssumptionFailed(failure);
        } else {
            mOutOfTestFailures.add(failure.getException());
        }
    }

    @Override
    public void fireTestIgnored(Description description) {
        Log.i(RavenwoodAwareTestRunner.TAG, "testIgnored: " + description);
        mRealNotifier.fireTestIgnored(description);
    }

    @Override
    public void fireTestFinished(Description description) {
        Log.i(RavenwoodAwareTestRunner.TAG, "testFinished: " + description);
        mRealNotifier.fireTestFinished(description);

        mInAfterClass = true;
        mAlreadyFailed = false;
    }

    @Override
    public void pleaseStop() {
        Log.w(RavenwoodAwareTestRunner.TAG, "pleaseStop:");
        mRealNotifier.pleaseStop();
    }

    /**
     * At the end of each Suite, we handle failures happened out of test methods.
     * (typically in @BeforeClass or @AfterClasses)
     *
     * This is to work around b/364395552.
     */
    private void maybeHandleOutOfTestFailures() {
        if (mOutOfTestFailures.isEmpty()) {
            return;
        }
        Throwable th;
        if (mOutOfTestFailures.size() == 1) {
            th = mOutOfTestFailures.getFirst();
        } else {
            th = new MultipleFailureException(mOutOfTestFailures);
        }
        if (mInBeforeClass) {
            reportBeforeTestFailure(mCurrentSuite, th);
            return;
        }
        if (mInAfterClass) {
            reportAfterTestFailure(th);
        }
    }

    public void reportBeforeTestFailure(Description suiteDesc, Throwable th) {
        // If a failure happens before running any tests, we'll need to pretend
        // as if each test in the suite reported the failure, to work around b/364395552.
        for (var child : suiteDesc.getChildren()) {
            if (child.isSuite()) {
                // If the chiil is still a "parent" -- a test class or a test suite
                // -- propagate to its children.
                mRealNotifier.fireTestSuiteStarted(child);
                reportBeforeTestFailure(child, th);
                mRealNotifier.fireTestSuiteFinished(child);
            } else {
                mRealNotifier.fireTestStarted(child);
                Failure f = new Failure(child, th);
                if (th instanceof AssumptionViolatedException) {
                    mRealNotifier.fireTestAssumptionFailed(f);
                } else {
                    mRealNotifier.fireTestFailure(f);
                }
                mRealNotifier.fireTestFinished(child);
            }
        }
    }

    public void reportAfterTestFailure(Throwable th) {
        // Unfortunately, there's no good way to report it, so kill the own process.
        RavenwoodAwareTestRunner.onCriticalError(
                "Failures detected in @AfterClass, which would be swallowed by tradefed",
                th);
    }
}
