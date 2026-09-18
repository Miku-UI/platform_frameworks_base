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

import static androidx.test.internal.util.AndroidRunnerBuilderUtil.hasJUnit3TestMethod;
import static androidx.test.internal.util.AndroidRunnerBuilderUtil.isJUnit3Test;

import static com.android.ravenwood.common.RavenwoodInternalUtils.RAVENWOOD_VERBOSE_LOGGING;
import static com.android.ravenwood.common.RavenwoodInternalUtils.ensureIsPublicVoidMethod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.platform.test.annotations.RavenwoodTestRunnerInitializing;
import android.platform.test.annotations.internal.InnerRunner;
import android.util.Log;

import androidx.test.internal.runner.EmptyTestRunner;
import androidx.test.internal.runner.junit3.JUnit38ClassRunner;

import com.android.internal.annotations.GuardedBy;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A test runner used for Ravenwood.
 *
 * It delegates to another runner specified with {@link InnerRunner} with the following features.
 * - Add a called before the inner runner gets a chance to run. This can be used to initialize
 *   stuff used by the inner runner.
 * - Add class hook points.
 * - Add method hook points with help from the test rule {@link #sImplicitInstOuterRule},
 *   which are also injected by the ravenizer tool.
 *
 * We use this runner to:
 * - Initialize the Ravenwood environment.
 * - Handle {@link android.platform.test.annotations.DisabledOnRavenwood}.
 */
public final class RavenwoodAwareTestRunner extends RavenwoodAwareTestRunnerBase {

    // The following rule will be injected to tests by the Ravenizer tool.
    public static final TestRule sImplicitInstOuterRule = new MethodOuterHook();

    /** The test thread that drives the test. */
    public static final Thread sTestThread = Thread.currentThread();

    /** Keeps track of runners. */
    @GuardedBy("sActiveRunners")
    private static final Deque<RavenwoodAwareTestRunner> sActiveRunners = new ArrayDeque<>();

    static RavenwoodAwareTestRunner getCurrentRunnerNoCheck() {
        synchronized (sActiveRunners) {
            return sActiveRunners.peekLast();
        }
    }

    static RavenwoodAwareTestRunner getCurrentRunner() {
        assertEquals("Current test runner can only be obtained on the test thread!",
                sTestThread, Thread.currentThread());
        var runner = getCurrentRunnerNoCheck();
        assertNotNull("Current test runner not set!", runner);
        return runner;
    }

    final Class<?> mTestJavaClass;
    private Runner mRealRunner;
    private TestClass mTestClass = null;

    /**
     * Stores internal states / methods associated with this runner that's only needed in
     * junit-impl.
     */
    final RavenwoodRunnerState mState = new RavenwoodRunnerState();

    /**
     * Constructor.
     */
    public RavenwoodAwareTestRunner(Class<?> testClass) {
        RavenwoodDriver.globalInitOnce();
        mTestJavaClass = testClass;

        /*
         * If the class has @DisabledOnRavenwood, then we'll delegate to
         * ClassIgnoreTestRunner, which simply skips it.
         *
         * We need to do it before instantiating TestClass for b/367694651.
         */
        if (!RavenwoodEnablementChecker.getInstance().shouldRunClassOnRavenwood(testClass)) {
            mRealRunner = new ClassIgnoreTestRunner(testClass);
            return;
        }

        try {
            mTestClass = new TestClass(testClass);

            Log.i(TAG, "RavenwoodAwareTestRunner initializing for " + testClass.getCanonicalName());

            // Hook point to allow more customization.
            runAnnotatedMethodsOnRavenwood(RavenwoodTestRunnerInitializing.class, null);

            mRealRunner = instantiateRealRunner(mTestClass);

            if (RavenwoodEnvironment.getInstance().isHidingDisabledTests()
                    && mRealRunner instanceof Filterable r) {
                try {
                    r.filter(RavenwoodEnablementChecker.getInstance().asJunitFilter());
                } catch (NoTestsRemainException ignore) {
                    // We hit it when the class is not disabled, but all methods are disabled.
                    // The point of $RAVENWOOD_HIDE_DISABLED_TESTS is to unclutter the teset log,
                    // so we don't log anything here.
                }
            }

            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.v(TAG, "enterTestRunner: " + this);
            }
            mState.enterTestRunner();
        } catch (Throwable throwable) {
            // If any exception occurs during the real runner instantiation, delegate to
            // ClassFailTestRunner so that this error will be reported properly.
            mRealRunner = new ClassFailTestRunner(testClass, throwable);
        }
    }

    @Override
    Runner getRealRunner() {
        return mRealRunner;
    }

    /**
     * Modified from {@link androidx.test.internal.runner.junit3.AndroidJUnit3Builder}
     */
    @Override
    RunnerBuilder junit3Builder() {
        return new RunnerBuilder() {
            @Override
            public Runner runnerForClass(Class<?> testClass) {
                if (isJUnit3Test(testClass)) {
                    if (!hasJUnit3TestMethod(testClass)) {
                        return new EmptyTestRunner(testClass);
                    }
                    return new JUnit38ClassRunner(new RavenwoodAwareJUnit3TestSuite(testClass));
                }
                return null;
            }
        };
    }

    private void runAnnotatedMethodsOnRavenwood(Class<? extends Annotation> annotationClass,
            Object instance) {
        if (RAVENWOOD_VERBOSE_LOGGING) {
            Log.v(TAG, "runAnnotatedMethodsOnRavenwood() " + annotationClass.getName());
        }

        for (var method : mTestClass.getAnnotatedMethods(annotationClass)) {
            ensureIsPublicVoidMethod(method.getMethod(), /* isStatic=*/ instance == null);

            var methodDesc = method.getDeclaringClass().getName() + "."
                    + method.getMethod().toString();
            try {
                method.getMethod().invoke(instance);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw logAndFail("Caught exception while running method " + methodDesc, e);
            }
        }
    }

    @Override
    public void run(RunNotifier realNotifier) {
        final var notifier = new RavenwoodRunNotifier(realNotifier);
        final var description = getDescription();

        RavenwoodTestStats.getInstance().attachToRunNotifier(notifier);

        if (mRealRunner instanceof ClassBypassTestRunner) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.v(TAG, "onClassSkipped: description=" + description);
            }
            mRealRunner.run(notifier);
            return;
        }

        if (RAVENWOOD_VERBOSE_LOGGING) {
            Log.v(TAG, "Running " + mTestJavaClass.getCanonicalName());
        }
        if (RAVENWOOD_VERBOSE_LOGGING) {
            dumpDescription(description);
        }

        synchronized (sActiveRunners) {
            sActiveRunners.offerLast(this);
        }
        try {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.v(TAG, "enterTestClass: " + mTestJavaClass.getName());
            }
            try {
                mState.enterTestClass();
            } catch (Throwable th) {
                notifier.reportBeforeTestFailure(description, th);
                return;
            }

            // Delegate to the inner runner.
            mRealRunner.run(notifier);
        } finally {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.v(TAG, "exitTestClass: " + mTestJavaClass.getName());
            }
            synchronized (sActiveRunners) {
                assertEquals(this, sActiveRunners.pollLast());
            }
            try {
                mState.exitTestClass();
            } catch (Throwable th) {
                notifier.reportAfterTestFailure(th);
            }
        }
    }

    /**
     * A runner that bypasses a class. It still has to support {@link Filterable}
     * because otherwise the result still says "SKIPPED" even when it's not included in the
     * filter.
     */
    private abstract static class ClassBypassTestRunner extends Runner implements Filterable {
        private final Description mDescription;
        private boolean mFilteredOut;

        ClassBypassTestRunner(Class<?> testClass, Annotation... annotations) {
            mDescription = Description.createTestDescription(testClass, "<init>", annotations);
            mFilteredOut = false;
        }

        @Override
        public Description getDescription() {
            return mDescription;
        }

        @Override
        public final void run(RunNotifier notifier) {
            if (mFilteredOut) {
                return;
            }
            notifier.fireTestSuiteStarted(mDescription);
            onRun(notifier);
            notifier.fireTestSuiteFinished(mDescription);
        }

        @Override
        public void filter(Filter filter) throws NoTestsRemainException {
            if (filter.shouldRun(mDescription)) {
                mFilteredOut = false;
            } else {
                throw new NoTestsRemainException();
            }
        }

        abstract void onRun(RunNotifier notifier);
    }

    /**
     * A runner that simply ignores a class, used when a test class is disabled by
     * {@code @DisabledOnRavenwood} (or the policy file or regex -- see
     * {@link RavenwoodEnablementChecker}).
     *
     * The default behavior is these classes still get executed but skipped with an assumption
     * failure.
     *
     * But that clutters atest output, especially when classes are disabled with regexes.
     * We optionally hide such results when the environmental variable
     * {@code $RAVENWOOD_HIDE_DISABLED_CLASSES} is set to "1". When it's set,
     * {@link #getDescription} returns an instance with annotation
     * {@link ClassIgnoredOnRavenwood}, which lets Tradefed recognized as "runner should be
     * skipped", which avoids cluttering atest output. (see b/462198969 for more information.)
     */
    private static class ClassIgnoreTestRunner extends ClassBypassTestRunner {
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        public @interface ClassIgnoredOnRavenwood {
            @ClassIgnoredOnRavenwood
            final class InstanceHolder {
                static final ClassIgnoredOnRavenwood INSTANCE = Objects.requireNonNull(
                        InstanceHolder.class.getAnnotation(ClassIgnoredOnRavenwood.class));
            }
        }

        private static final Annotation[] IGNORE_CLASS_FROM_TRADEFED_ANNOTATIONS = {
                ClassIgnoredOnRavenwood.InstanceHolder.INSTANCE
        };

        private static final Annotation[] EMPTY_ANNOTATIONS = {};

        private static final Annotation[] CLASS_IGNORE_ANNOTATIONS =
                RavenwoodEnvironment.getInstance().isHidingDisabledTests()
                        ? IGNORE_CLASS_FROM_TRADEFED_ANNOTATIONS : EMPTY_ANNOTATIONS;

        ClassIgnoreTestRunner(Class<?> testClass) {
            super(testClass, CLASS_IGNORE_ANNOTATIONS);
            RavenwoodTestStats.getInstance().onTestDisabled(testClass);
        }

        @Override
        void onRun(RunNotifier notifier) {
            notifier.fireTestIgnored(getDescription());
        }
    }

    /**
     * A runner that simply fails a class.
     */
    private static class ClassFailTestRunner extends ClassBypassTestRunner {
        private final Throwable mError;

        ClassFailTestRunner(Class<?> testClass, Throwable error) {
            super(testClass);
            mError = error;
        }

        @Override
        void onRun(RunNotifier notifier) {
            var desc = getDescription();
            notifier.fireTestStarted(desc);
            notifier.fireTestFailure(new Failure(desc, mError));
            notifier.fireTestFinished(desc);
        }
    }

    /**
     * The outermost (first) rule for each test method.
     */
    private static class MethodOuterHook implements TestRule {
        @Override
        public Statement apply(Statement base, Description methodDesc) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    getCurrentRunner().mState.enterTestMethod(methodDesc);
                    try {
                        base.evaluate();
                    } finally {
                        getCurrentRunner().mState.exitTestMethod(methodDesc);
                    }
                }
            };
        }
    }

    /**
     * Called by RavenwoodRule.
     */
    static void onRavenwoodRuleEnter(Description description, RavenwoodRule rule) {
        if (RAVENWOOD_VERBOSE_LOGGING) {
            Log.v(TAG, "onRavenwoodRuleEnter: description=" + description);
        }
        getCurrentRunner().mState.enterRavenwoodRule(rule);
    }

    /**
     * Called by RavenwoodRule.
     */
    static void onRavenwoodRuleExit(Description description, RavenwoodRule rule) {
        if (RAVENWOOD_VERBOSE_LOGGING) {
            Log.v(TAG, "onRavenwoodRuleExit: description=" + description);
        }
        getCurrentRunner().mState.exitRavenwoodRule(rule);
    }

    private void dumpDescription(Description desc) {
        dumpDescription(desc, "[TestDescription]=", "  ");
    }

    private void dumpDescription(Description desc, String header, String indent) {
        if (RAVENWOOD_VERBOSE_LOGGING) {
            Log.v(TAG, indent + header + desc);
        }

        var children = desc.getChildren();
        var childrenIndent = "  " + indent;
        for (int i = 0; i < children.size(); i++) {
            dumpDescription(children.get(i), "#" + i + ": ", childrenIndent);
        }
    }

    static volatile BiConsumer<String, Throwable> sCriticalErrorHandler = null;

    static void onCriticalError(@NonNull String message, @Nullable Throwable th) {
        Log.e(TAG, "Critical error! " + message, th);
        var handler = sCriticalErrorHandler;
        if (handler == null) {
            Log.e(TAG, "Ravenwood cannot continue. Killing self process.", th);
            System.exit(1);
        }
        handler.accept(message, th);
    }
}
