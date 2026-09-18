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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.platform.test.annotations.internal.InnerRunner;
import android.platform.test.ravenwood.RavenwoodEnablementChecker.DisabledOnRavenwoodAssumptionException;
import android.util.Log;

import com.android.ravenwood.common.RavenwoodInternalUtils;

import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
            "Type,Label,Module,Class,Method,Package,RawClass,RawMethodName,AtestTarget,Reason,"
            + "Annotations,Source,Line,Passed,Failed,Skipped,DurationMillis";
    private static final String FORMAT = "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%f\n";

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
        // Return the first frame that's not from RavenwoodExperimentalApiChecker.
        for (var frame : throwable.getStackTrace()) {
            if (frame.getClassName().contains(".RavenwoodExperimentalApiChecker")) {
                continue;
            }
            return frame.getClassName() + "#" + frame.getMethodName();
        }
        // Fallback: just return the innermost frame.
        var caller = throwable.getStackTrace()[0];
        return caller.getClassName() + "#" + caller.getMethodName();
    }

    record SourceLocation(Description testDescription, String filename, int line) {
    }

    record Outcome(Description testDescription, Result result, Duration duration, Failure failure) {
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
                    if (ex instanceof RavenwoodUnsupportedApiException re) {
                        // The test hit a Ravenwood unsupported API
                        return re.getReason();
                    }
                    var cause = ex.getCause();
                    if (cause == null) {
                        if (ex instanceof UnsatisfiedLinkError) {
                            return getCaller(ex);
                        }
                        if (ex instanceof ExceptionInInitializerError
                                && (ex.getMessage().contains("RavenwoodUnsupportedApiException")
                                || ex.getMessage().contains("Stub!"))) {
                            // A static initializer hit a Ravenwood unsupported API or stub
                            return getCaller(ex);
                        }
                        if ("Stub!".equals(ex.getMessage())) {
                            // The test hit a stub API
                            return getCaller(ex);
                        }
                        if (ex instanceof AssertionError) {
                            if (ex.getMessage() == null) {
                                return "AssertionError: [No message]";
                            } else {
                                return "AssertionError: " + ex.getMessage();
                            }
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

        /**
         * If the result is a failure (skip or fail), return the source file information of
         * the failed line in the test class file.
         */
        @NonNull
        public SourceLocation failureSourceLocation() {
            // Bail early if it doesn't have an exception.
            if (failure == null || failure.getException() == null) {
                return new SourceLocation(testDescription, "", 0);
            }
            var testClass = RavenwoodImplUtils.getDescriptionTestClass(testDescription);
            var testClassName = testClass.getName();

            // Iterate over stack frames of the exception and its causes, and find the
            // first frame from the test class.
            for (var e = failure.getException(); e != null; e = e.getCause()) {
                for (var frame : e.getStackTrace()) {
                    if (frame.getClassName().equals(testClassName)) {
                        // Test class found, return the filename and the line number.
                        var pkg = testClass.getPackageName().replace('.', '/');
                        return new SourceLocation(
                                testDescription,
                                pkg + "/" + frame.getFileName(),
                                frame.getLineNumber());
                    }
                }
            }

            return new SourceLocation(testDescription, "[Unable to find source file]", 0);
        }
    }

    private final String mTestModuleName;
    private final File mOutputSymlinkFile;
    private final PrintWriter mOutputWriter;
    private final Map<String, Map<String, Outcome>> mStats = new LinkedHashMap<>();
    private final Set<Class<?>> mDisabledClasses = new HashSet<>();

    /** Ctor */
    public RavenwoodTestStats() {
        mTestModuleName = RavenwoodEnvironment.getInstance().getTestModuleName();

        var basename = "Ravenwood-stats_" + mTestModuleName + "_";

        // Get the current time
        LocalDateTime now = LocalDateTime.now();

        var tmpdir = RavenwoodEnvironment.getInstance().getTempDir().getAbsolutePath();
        File outputFile = new File(tmpdir,
                basename + now.format(RavenwoodUtils.LOG_FILE_TIMESTAMP_FORMATTER) + ".csv");

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
        var outcomeMap = mStats.computeIfAbsent(className, k -> new TreeMap<>());

        Outcome v = outcomeMap.get(methodName);
        if (v == null) {
            v = outcomeMap.put(methodName, outcome);

            // We may call addResult multiple times for the same method, but we always use
            // the first one.
            dumpSingleTest(className, methodName, outcome);
        }
    }

    /**
     * Called by the test runner when skipping a whole test class to propagate when a test
     * is skipped for @DisabledOnRavenwood via a side channel. (Because the normal notifier
     * flow doesn't have a place for us to put this information.)
     */
    public void onTestDisabled(@NonNull Class<?> clazz) {
        mDisabledClasses.add(clazz);
    }

    /**
     * Make sure the string properly escapes commas for CSV fields.
     */
    private static String escape(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    private static String getAnnotations(@Nullable AnnotatedElement element) {
        if (element == null) {
            return "";
        }
        return annotationsToResult(Arrays.asList(element.getAnnotations()));
    }

    private static String getAnnotations(@Nullable Description description) {
        if (description == null) {
            return "";
        }
        return annotationsToResult(description.getAnnotations());
    }

    /**
     * Convert annotations to a comma-separated string, ignoring uninteresting ones.
     */
    private static String annotationsToResult(@Nullable Collection<Annotation> annotations) {
        StringBuilder sb = new StringBuilder();
        var sep = "";
        for (Annotation annot : annotations) {
            var name = annot.annotationType().getName();
            if (name.equals("org.junit.Test")) {
                continue;
            }
            if (name.contains("HostStubGenProcessed")) {
                continue;
            }
            sb.append(sep);
            sb.append("@");
            sb.append(getAnnotationTypeName(annot));
            appendAnnotationValue(sb, annot);
            sep = ",";
        }

        return sb.toString();
    }

    private static final String[] KNOWN_PACKAGES = {
            "android.platform.test.annotations.internal.",
            "android.platform.test.annotations.",
            "org.junit.runner.",
    };

    private static String getAnnotationTypeName(Annotation annot) {
        var name = annot.annotationType().getName();

        for (var kp : KNOWN_PACKAGES) {
            if (name.startsWith(kp)) {
                name = name.substring(kp.length());
                break;
            }
        }
        return name;
    }

    private static void appendAnnotationValue(StringBuilder sb, Annotation annot) {
        String value = null;
        if (annot instanceof RunWith a) {
            value = a.value().getCanonicalName();
        } else if (annot instanceof InnerRunner a) {
            value = a.value().getCanonicalName();
        }
        if (value == null) {
            return;
        }
        sb.append("(");
        sb.append(value);
        sb.append(")");
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

            Class<?> testClass = null;
            for (var entry : outcomes.entrySet()) {
                var method = entry.getKey();
                var outcome = entry.getValue();

                passed += outcome.passedCount();
                skipped += outcome.skippedCount();
                failed += outcome.failedCount();
                totalDuration = totalDuration.plus(outcome.duration);

                testClass = RavenwoodImplUtils.getDescriptionTestClass(outcome.testDescription);
            }
            mOutputWriter.printf(FORMAT,
                    "c", // Type: class
                    buildClassLabel(testClass, passed, failed, skipped),
                    mTestModuleName, className,
                    "-", // method name
                    escape(getPackageName(className)),
                    escape(getClassName(className)),
                    "-", // raw method name
                    escape(buildAtestTarget(testClass, null)),
                    "-", // reason.
                    escape(getAnnotations(testClass)),
                    "", 0, // Source info
                    passed, failed, skipped,
                    totalDuration.toMillis() / 1000f);
        });
        mOutputWriter.flush();
        mStats.clear();
        Log.i(TAG, "Added result to stats file: file://" + mOutputSymlinkFile);

        copyToArtifactsDir();
    }

    private void dumpSingleTest(String className, String method, Outcome outcome) {
        var rawMethodName = extractMethodName(method);
        var testClass = RavenwoodImplUtils.getDescriptionTestClass(outcome.testDescription);
        var loc = outcome.failureSourceLocation();

        mOutputWriter.printf(FORMAT,
                "m", // Type: method
                // Method label: "P"assed, "F"ailed, "S"kipped, "D"isabled
                buildMethodLabel(outcome),
                mTestModuleName, className,
                escape(method),
                escape(getPackageName(className)),
                escape(getClassName(className)),
                escape(rawMethodName),
                escape(buildAtestTarget(testClass, method)),
                escape(outcome.reason()),
                escape(getAnnotations(outcome.testDescription)),
                escape(loc.filename()),
                loc.line(),
                outcome.passedCount(), outcome.failedCount(), outcome.skippedCount(),
                outcome.duration.toMillis() / 1000f);
        mOutputWriter.flush();
    }

    private static String getPackageName(String className) {
        var pos = className.lastIndexOf('.');
        if (pos < 0) {
            return "";
        } else {
            return className.substring(0, pos);
        }
    }

    private static String getClassName(String className) {
        var pos = className.lastIndexOf('.');
        if (pos < 0) {
            return className;
        } else {
            return className.substring(pos + 1);
        }
    }

    /** Generate a label for a method row. */
    private String buildMethodLabel(Outcome outcome) {
        var label = "[Unknown]";
        if (outcome.passedCount() > 0) {
            label = "P"; // Passed
        } else if (outcome.skippedCount() > 0) {
            // Special case @DisabledOnRavenwood.
            var isDisabledOnRavenwood =
                    mDisabledClasses.contains(
                            RavenwoodImplUtils.getDescriptionTestClass(outcome.testDescription))
                    || outcome.failure != null && outcome.failure.getException()
                        instanceof DisabledOnRavenwoodAssumptionException;
            if (isDisabledOnRavenwood) {
                label = "D"; // Disabled (normally @DisabledOnRavenwood)
            } else {
                label = "S"; // Skipped (normally @Ignore or assumption failure)
            }
        } else if (outcome.failedCount() > 0) {
            label = "F"; // Failed
        }
        return label;
    }

    /** Generate a label for a class row. */
    private StringBuilder buildClassLabel(
            @Nullable Class<?> clazz, int passed, int failed, int skipped) {
        var label = new StringBuilder();
        if (passed > 0) {
            label.append("P"); // Passed
        }
        if (failed > 0) {
            label.append("F"); // Failed
        }
        if (skipped > 0) {
            if (mDisabledClasses.contains(clazz)) {
                label.append("D"); // Disabled (normally @DisabledOnRavenwood)
            } else {
                label.append("S"); // Skipped (normally @Ignore)
            }
        }
        // Fallback
        if (label.isEmpty()) {
            label.append("[Unknown]");
        }
        return label;
    }

    private String buildAtestTarget(@Nullable Class<?> clazz, @Nullable String method) {
        if (clazz == null) { // Shouldn't happen, but just in case.
            return mTestModuleName;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(mTestModuleName);
        sb.append(":");
        sb.append(clazz.getName());
        if (method != null && !"<init>".equals(method)) {
            sb.append("#");
            sb.append(extractMethodName(method));
        }
        return sb.toString();
    }

    private void copyToArtifactsDir() {
        var artifact = RavenwoodEnvironment.getInstance().getArtifactsDir().toPath()
                .resolve(mOutputSymlinkFile.getName());
        try {
            Files.deleteIfExists(artifact);
            Files.copy(mOutputSymlinkFile.toPath(), artifact);
            Log.i(TAG, "Copied to artifacts dir: file://" + artifact);
        } catch (IOException e) {
            Log.w(TAG, "Failed to copy to artifacts dir: file://" + artifact);
        }
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
            RavenwoodExperimentalApiChecker.dumpExperimentalApiUsage();
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

        private Outcome createOutcome(Description testDescription, Result result, Failure failure) {
            var endTime = Instant.now();

            // When a class is skipped, force set duration to 0.
            // This is necessary because when we skip a test class, RavenwoodAwareTestRunner
            // calls testIgnored() without calling testStarted() (and changing this would break
            // things.)
            var duration = result == Result.Skipped ? Duration.ZERO
                    : Duration.between(mStartTime, endTime);
            return new Outcome(testDescription, result, duration, failure);
        }

        private Outcome createOutcome(Description testDescription, Result result) {
            return createOutcome(testDescription, result, null);
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
                    createOutcome(description, Result.Passed),
                    "  testFinished: ",
                    description);
        }

        @Override
        public void testFailure(Failure failure) {
            var description = failure.getDescription();
            addResultWithLogging(description.getClassName(),
                    description.getMethodName(),
                    createOutcome(description, Result.Failed, failure),
                    "  testFailure: ",
                    failure);
        }

        @Override
        public void testAssumptionFailure(Failure failure) {
            var description = failure.getDescription();
            addResultWithLogging(description.getClassName(),
                    description.getMethodName(),
                    createOutcome(description, Result.Skipped, failure),
                    "  testAssumptionFailure: ",
                    failure);
        }

        @Override
        public void testIgnored(Description description) {
            addResultWithLogging(description.getClassName(),
                    description.getMethodName(),
                    createOutcome(description, Result.Skipped),
                    "  testIgnored: ",
                    description);
        }
    };
}
