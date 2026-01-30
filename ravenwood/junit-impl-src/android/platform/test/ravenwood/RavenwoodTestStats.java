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

import static com.android.ravenwood.common.RavenwoodInternalUtils.RAVENWOOD_VERBOSE_LOGGING;

import android.util.Log;

import com.android.ravenwood.common.RavenwoodInternalUtils;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Collect test result stats and write them into a CSV file containing the test results.
 *
 * The output file is created as `/tmp/Ravenwood-stats_[TEST-MODULE=NAME]_[TIMESTAMP].csv`.
 * A symlink to the latest result will be created as
 * `/tmp/Ravenwood-stats_[TEST-MODULE=NAME]_latest.csv`.
 *
 * Also responsible for dumping all called methods in the form of policy file, by calling
 * {@link RavenwoodMethodCallLogger#dumpAllCalledMethodsInner}, if the method call log is enabled.
 */
public class RavenwoodTestStats {
    private static final String TAG = RavenwoodInternalUtils.TAG;
    private static final String HEADER =
            "Type,Module,Class,Method,RawMethodName,Reason,Passed,Failed,Skipped,DurationMillis";

    private static RavenwoodTestStats sInstance;

    /**
     * @return a singleton instance.
     */
    public static RavenwoodTestStats getInstance() {
        if (sInstance == null) {
            sInstance = new RavenwoodTestStats();
        }
        return sInstance;
    }

    /**
     * Represents a test result.
     */
    enum Result {
        Passed,
        Failed,
        Skipped,
    }

    private static String getCaller(Throwable throwable) {
        var caller = throwable.getStackTrace()[0];
        return caller.getClassName() + "#" + caller.getMethodName();
    }

    record Outcome(Result result, Duration duration, Failure failure) {
        /** @return 1 if {@link #result} is "passed". */
        public int passedCount() {
            return result == Result.Passed ? 1 : 0;
        }

        /** @return 1 if {@link #result} is "failed". */
        public int failedCount() {
            return result == Result.Failed ? 1 : 0;
        }

        /** @return 1 if {@link #result} is "skipped". */
        public int skippedCount() {
            return result == Result.Skipped ? 1 : 0;
        }

        /**
         * Try to extract the real reason behind a test failure.
         * The logic here is just some heuristic to generate human-readable information.
         */
        public String reason() {
            if (failure != null) {
                var ex = failure.getException();
                // Keep unwrapping the exception until we found
                // unsupported API exception or the deepest cause.
                for (;;) {
                    if (ex instanceof RavenwoodUnsupportedApiException) {
                        // The test hit a Ravenwood unsupported API
                        return getCaller(ex);
                    }
                    var cause = ex.getCause();
                    if (cause == null) {
                        if (ex instanceof ExceptionInInitializerError
                                && ex.getMessage().contains("RavenwoodUnsupportedApiException")) {
                            // A static initializer hit a Ravenwood unsupported API
                            return getCaller(ex);
                        }
                        if ("Stub!".equals(ex.getMessage())) {
                            // The test hit a stub API
                            return getCaller(ex);
                        }
                        // We don't actually know what's up, just report the exception class name.
                        return ex.getClass().getName();
                    } else {
                        ex = cause;
                    }
                }
            }
            return "-";
        }
    }

    private final String mTestModuleName;
    private final File mOutputSymlinkFile;
    private final PrintWriter mOutputWriter;
    private final Map<String, Map<String, Outcome>> mStats = new LinkedHashMap<>();

    /** Ctor */
    public RavenwoodTestStats() {
        mTestModuleName = RavenwoodEnvironment.getInstance().getTestModuleName();

        var basename = "Ravenwood-stats_" + mTestModuleName + "_";

        // Get the current time
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

        var tmpdir = System.getProperty("java.io.tmpdir");
        File outputFile = new File(tmpdir, basename + now.format(fmt) + ".csv");

        try {
            mOutputWriter = new PrintWriter(outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create logfile. File=" + outputFile, e);
        }

        // Create the "latest" symlink.
        Path symlink = Paths.get(tmpdir, basename + "latest.csv");
        try {
            Files.deleteIfExists(symlink);
            Files.createSymbolicLink(symlink, Paths.get(outputFile.getName()));

        } catch (IOException e) {
            throw new RuntimeException("Failed to create logfile. File=" + outputFile, e);
        }
        mOutputSymlinkFile = symlink.toFile();

        Log.i(TAG, "Test result stats file: " + mOutputSymlinkFile);

        // Print the header.
        mOutputWriter.println(HEADER);
        mOutputWriter.flush();
    }

    private void addResult(String className, String methodName, Outcome outcome) {
        mStats.computeIfAbsent(className, k -> new TreeMap<>()).putIfAbsent(methodName, outcome);
    }

    /**
     * Make sure the string properly escapes commas for CSV fields.
     */
    private static String normalize(String s) {
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    /**
     * Dump all the results and clear it.
     */
    private void dumpAllAndClear() {
        mStats.forEach((className, outcomes) -> {
            int passed = 0;
            int skipped = 0;
            int failed = 0;
            Duration totalDuration = Duration.ZERO;

            for (var entry : outcomes.entrySet()) {
                var method = entry.getKey();
                var outcome = entry.getValue();

                passed += outcome.passedCount();
                skipped += outcome.skippedCount();
                failed += outcome.failedCount();
                totalDuration = totalDuration.plus(outcome.duration);

                // Skip the constructor method, which shows up as a result if a class
                // has @DisabledOnRavenwood.
                if ("<init>".equals(method)) {
                    continue;
                }

                var rawMethodName = extractMethodName(method);

                // Per-method status, with "m".
                mOutputWriter.printf("m,%s,%s,%s,%s,%s,%d,%d,%d,%f\n",
                        mTestModuleName,
                        className, normalize(method), normalize(rawMethodName),
                        normalize(outcome.reason()),
                        outcome.passedCount(), outcome.failedCount(), outcome.skippedCount(),
                        outcome.duration.toMillis() / 1000f);
            }

            // Per-class status, with "c".
            mOutputWriter.printf("c,%s,%s,-,-,%d,%d,%d,%f\n", mTestModuleName, className,
                    passed, failed, skipped, totalDuration.toMillis() / 1000f);
        });
        mOutputWriter.flush();
        mStats.clear();
        Log.i(TAG, "Added result to stats file: file://" + mOutputSymlinkFile);
    }

    private static final Pattern sParamsPattern = Pattern.compile("\\[.*$");

    /**
     * Remove "[parameters..]" from a method full name.
     */
    private static String extractMethodName(String methodNameWithParams) {
        return sParamsPattern.matcher(methodNameWithParams).replaceFirst("");
    }

    private static void createCalledMethodPolicyFile() {
        // Ideally we want to call it only once, when the very last test class finishes,
        // but we don't know which test class is last or not, so let's just do the dump
        // after every test class.
        RavenwoodMethodCallLogger.getInstance().dumpAllCalledMethods();
    }

    public void attachToRunNotifier(RunNotifier notifier) {
        notifier.addListener(mRunListener);
    }

    private final RunListener mRunListener = new RunListener() {
        private Instant mStartTime;

        @Override
        public void testSuiteStarted(Description description) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "testSuiteStarted: " + description);
            }
        }

        @Override
        public void testSuiteFinished(Description description) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "testSuiteFinished: " + description);
            }
        }

        @Override
        public void testRunStarted(Description description) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "testRunStarted: " + description);
            }
        }

        @Override
        public void testRunFinished(org.junit.runner.Result result) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "testRunFinished: " + result);
            }

            createCalledMethodPolicyFile();
            dumpAllAndClear();
        }

        @Override
        public void testStarted(Description description) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "  testStarted: " + description);
            }
            mStartTime = Instant.now();
        }

        private Outcome createOutcome(Result result, Failure failure) {
            var endTime = Instant.now();

            // When a class is skipped, force set duration to 0.
            // This is necessary because when we skip a test class, RavenwoodAwareTestRunner
            // calls testIgnored() without calling testStarted() (and changing this would break
            // things.)
            var duration = result == Result.Skipped ? Duration.ZERO
                    : Duration.between(mStartTime, endTime);
            return new Outcome(result, duration, failure);
        }

        private Outcome createOutcome(Result result) {
            return createOutcome(result, null);
        }

        private void addResultWithLogging(
                String className,
                String methodName,
                Outcome outcome,
                String logMessage,
                Object messageExtra) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, logMessage + messageExtra);
            }
            addResult(className, methodName, outcome);
        }

        @Override
        public void testFinished(Description description) {
            // Note: testFinished() is always called, even in failure cases and another callback
            // (e.g. testFailure) has already called. But we just call it anyway because if
            // we already recorded a result to the same metho, we won't overwrite it.
            addResultWithLogging(description.getClassName(),
                    description.getMethodName(),
                    createOutcome(Result.Passed),
                    "  testFinished: ",
                    description);
        }

        @Override
        public void testFailure(Failure failure) {
            var description = failure.getDescription();
            addResultWithLogging(description.getClassName(),
                    description.getMethodName(),
                    createOutcome(Result.Failed, failure),
                    "  testFailure: ",
                    failure);
        }

        @Override
        public void testAssumptionFailure(Failure failure) {
            var description = failure.getDescription();
            addResultWithLogging(description.getClassName(),
                    description.getMethodName(),
                    createOutcome(Result.Skipped),
                    "  testAssumptionFailure: ",
                    failure);
        }

        @Override
        public void testIgnored(Description description) {
            addResultWithLogging(description.getClassName(),
                    description.getMethodName(),
                    createOutcome(Result.Skipped),
                    "  testIgnored: ",
                    description);
        }
    };
}