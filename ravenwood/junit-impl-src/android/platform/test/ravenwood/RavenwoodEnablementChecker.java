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

import static com.android.ravenwood.common.RavenwoodInternalUtils.isTestMethod;
import static com.android.ravenwood.common.RavenwoodInternalUtils.parseClassNameWildcard;
import static com.android.ravenwood.common.RavenwoodInternalUtils.toCanonicalTestName;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnabledOnRavenwood;
import android.platform.test.ravenwood.EnablementTextPolicyParser.ParseException;
import android.platform.test.ravenwood.RavenwoodEnablementChecker.PolicyChecker;
import android.platform.test.ravenwood.RavenwoodEnablementChecker.RunPolicy;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.SneakyThrow;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Class to check if a class / method should be executed.
 *
 * - Normally, it consults @EnabledOnRavenwood and @DisabledOnRavenwood.
 *
 * - If $RAVENWOOD_TEST_ENABLEMENT_POLICY is specified, we use it as a list of files
 *   containing "enablement policies", which changes the default enablement behavior.
 *   Meaning, if a class/method has the above annotations, we still use them, but
 *   for method without explicit annotations (which would normally mean "run"),
 *   we can disable them with the polcy file.
 *
 *   This feature is only used locally. On CI server, we never use it.
 *
 * - You can override the above logic and run exact test classes / test methods by
 *   setting a case-insensitive regex to $RAVENWOOD_FORCE_FILTER_REGEX. If it's set,
 *   only the tests that are mathing it would be executed. For example,
 *   running with RAVENWOOD_FORCE_FILTER_REGEX='(MyTestClass1|MyTestClass2#testFoo)' would
 *   execute all tests in MyTestClass1, and only testFoo() in MyTestClass2.
 */
public abstract class RavenwoodEnablementChecker {
    private static final String TAG = RavenwoodInternalUtils.TAG;

    /**
     * AssumptionViolatedException subclass specifically used for "disabled on ravenwood tests".
     */
    public static class DisabledOnRavenwoodAssumptionException extends AssumptionViolatedException {
        public DisabledOnRavenwoodAssumptionException(String message) {
            super(message);
        }
    }

    /**
     * How we want to run each test class / method.
     */
    @VisibleForTesting
    public enum RunPolicy {
        /** Run it. */
        Enabled,

        /** Don't run it, unless $RAVENWOOD_RUN_DISABLED_TESTS is set to 1. */
        Disabled,

        /** Never run it, even if $RAVENWOOD_RUN_DISABLED_TESTS is set to 1. */
        NeverRun,

        /** Not explicitly specified, which defaults to "run it". */
        Unspecified;

        public static RunPolicy parse(@NonNull String text) {
            return switch (text) {
                case "enable", "e", "true" -> Enabled;
                case "disable", "d", "false" -> Disabled;
                case "never", "n" -> NeverRun;
                default -> throw new IllegalArgumentException("Invalid policy '" + text + "'");
            };
        }
    }

    @VisibleForTesting
    public enum RunMode {
        /** Run normally. Disabled tests are skipped. */
        Normal {
            @Override
            public boolean shouldRunClass(@NonNull RunPolicy policy) {
                return switch (policy) {
                    case Unspecified, Enabled -> true;
                    default -> false;
                };
            }
        },

        /** Run the disabled tests too, along with the enabled tests. */
        AlsoDisabledTests {
            @Override
            public boolean shouldRunClass(@NonNull RunPolicy policy) {
                return switch (policy) {
                    case Unspecified, Enabled, Disabled -> true;
                    default -> false;
                };
            }
        },

        /** Run only the disabled tests, except for "never" ones. */
        DisabledOnly {
            @Override
            public boolean shouldRunClass(@NonNull RunPolicy policy) {
                return switch (policy) {
                    case Unspecified, Enabled, Disabled -> true;
                    default -> false;
                };
            }

            /** We only run disabled mehods */
            @Override
            public boolean shouldRunMethod(@NonNull RunPolicy policy) {
                return switch (policy) {
                    case Disabled -> true;
                    default -> false;
                };
            }
        };

        /** @return if a policy means "run" or not for a class. */
        public abstract boolean shouldRunClass(@NonNull RunPolicy policy);

        /** @return if a policy means "run" or not for a method. */
        public boolean shouldRunMethod(@NonNull RunPolicy policy) {
            // By default, we use the same logic for classes and methods.
            return shouldRunClass(policy);
        }
    }

    /**
     * Internal interface for "checkers".
     */
    @VisibleForTesting
    public interface PolicyChecker {
        /** @return run policy for a class */
        RunPolicy getClassPolicy(@NonNull Class<?> testClass);

        /** @return run policy for a method */
        RunPolicy getMethodPolicy(@NonNull Description description);
    }

    /**
     * Normally, we don't run "disabled" tests, but if it's set to 1, we run the disabled tests
     * too.
     *
     * (But if a policy file says "never", we still won't run it.)
     */
    private static RunMode getDefaultRunMode() {
        var mode = RavenwoodEnvironment.getInstance().getEnvVar("RAVENWOOD_RUN_DISABLED_TESTS", "");
        switch (mode) {
            case "1":
                return RunMode.AlsoDisabledTests;
            case "2":
                return RunMode.DisabledOnly;
            default:
                return RunMode.Normal;
        }
    }

    /**
     * Optional, enablement policy files. On CI server, it's always empty.
     */
    private static String[] getPolicyFiles() {
        return RavenwoodEnvironment.getInstance().getArrayEnvVar(
                "RAVENWOOD_TEST_ENABLEMENT_POLICY");
    }

    private static volatile RavenwoodEnablementCheckerImpl sInstance;

    /**
     * @return the singleton instance.
     */
    public static RavenwoodEnablementChecker getInstance() {
        if (sInstance == null) {
            setDefaultInstance();
        }
        return sInstance;
    }

    /**
     * Reset to the default checker. Exposed for unit tets.
     */
    @VisibleForTesting
    public static void setDefaultInstance() {
        sInstance = new RavenwoodEnablementCheckerImpl(
                getDefaultRunMode(),
                getPolicyFiles(),
                RavenwoodEnvironment.getInstance().getRunFilterRegex(),
                RavenwoodEnvironment.getInstance().isSkippingLargeTests(),
                RavenwoodEnvironment.getInstance().isDumpingTestsOnly()
        );
    }

    @VisibleForTesting
    public static void overrideInstance(
            @NonNull RunMode runMode,
            @Nullable String policyText,
            @Nullable String overridingPattern,
            boolean ignoreLargeTests,
            boolean dumpTestsOnly
    )  {
        try {
            var parser = new EnablementTextPolicyParser();
            if (policyText != null && !policyText.isEmpty()) {
                parser.parse("[in-memory]", policyText, ignoreLargeTests);
            }
            sInstance = new RavenwoodEnablementCheckerImpl(
                    runMode, parser.getResult(),
                    dumpTestsOnly,
                    overridingPattern);
        } catch (IOException e) {
            SneakyThrow.sneakyThrow(e); // IOException shouldn't happen, but just in case
        }
    }

    /**
     * Throws {@link DisabledOnRavenwoodAssumptionException} if a test method is not supposed
     * to be executed on Ravenwood.
     */
    public final void assumeShouldRunTestMethod(Description description) {
        if (!shouldRunMethodOnRavenwood(description)) {
            throw new DisabledOnRavenwoodAssumptionException(
                    "This test is disabled on Ravenwood: " + description);
        }
    }

    /**
     * @return if a test class should be executed.
     */
    public abstract boolean shouldRunClassOnRavenwood(@NonNull Class<?> testClass);

    /**
     * @return if a test method should be executed.
     */
    public abstract boolean shouldRunMethodOnRavenwood(Description description);

    /**
     * @return if disabled tests would run.
     */
    public abstract boolean wouldRunDisabledTests();

    /** @return as a Junit filter */
    public Filter asJunitFilter() {
        return new Filter() {
            @Override
            public boolean shouldRun(Description description) {
                if (description.isTest()) {
                    return shouldRunMethodOnRavenwood(description);
                } else {
                    return shouldRunClassOnRavenwood(
                            RavenwoodImplUtils.getDescriptionTestClass(description));
                }
            }

            @Override
            public String describe() {
                return "Filtered by @DisabledOnRavenwood";
            }
        };
    }

    /**
     * Actual logic. This combines the annotation policy with the text policy.
     */
    static class RavenwoodEnablementCheckerImpl extends RavenwoodEnablementChecker {
        private final RunMode mRunMode;
        private final PolicyChecker mChecker;

        RavenwoodEnablementCheckerImpl(
                @NonNull RunMode runMode,
                @NonNull String[] policyFiles,
                @Nullable String overridingPattern,
                boolean ignoreLargeTests,
                boolean dumpTestOnly
        ) {
            this(runMode,
                    EnablementTextPolicyParser.parsePolicyFiles(policyFiles, ignoreLargeTests),
                    dumpTestOnly,
                    overridingPattern);
        }

        RavenwoodEnablementCheckerImpl(
                @NonNull RunMode runMode,
                @NonNull PolicyChecker subChecker,
                boolean dumpTestOnly,
                @Nullable String overridingPattern) {
            this.mRunMode = runMode;
            var chain = new PolicyCheckerChain();

            if (dumpTestOnly) {
                chain.add(new SkipAllMethodsPolicyChecker());
            }

            var forceOverride = overridingPattern != null && overridingPattern.startsWith("!");
            if (forceOverride) {
                // If the override pattern starts with a "!", we ignore the enablement file and
                // just run what's specified.
                chain.add(new RegexRunFilter(
                        Pattern.compile(overridingPattern.substring(1), Pattern.CASE_INSENSITIVE),
                        RunPolicy.Enabled,
                        RunPolicy.NeverRun
                ));
            } else {
                // If a pattern is set, but it doesn't start with an "!", then we use it
                // to further filter the default enablement logic.
                if (overridingPattern != null && !overridingPattern.isEmpty()) {
                    chain.add(new RegexRunFilter(
                            Pattern.compile(overridingPattern, Pattern.CASE_INSENSITIVE),
                            // If the pattern matches, fall back to the usual filtering.
                            RunPolicy.Unspecified,
                            // If the pattern doesn't match, don't run it.
                            RunPolicy.NeverRun
                    ));
                }

                // Annotations always win over the text policy file.
                chain.add(new AnnotationPolicyChecker());

                // Text policy changes the default behavior.
                chain.add(subChecker);
            }

            mChecker = chain;
        }

        @Override
        public boolean shouldRunClassOnRavenwood(Class<?> testClass) {
            return mRunMode.shouldRunClass(mChecker.getClassPolicy(testClass));
        }

        @Override
        public boolean shouldRunMethodOnRavenwood(Description description) {
            return mRunMode.shouldRunMethod(mChecker.getMethodPolicy(description));
        }

        @Override
        public boolean wouldRunDisabledTests() {
            return mRunMode.shouldRunMethod(RunPolicy.Disabled);
        }
    }

    @VisibleForTesting
    public static PolicyChecker getTextPolicyCheckerForTest(
            String filename,
            String text,
            boolean ignoreLargeTests
    ) throws Exception {
        var parser = new EnablementTextPolicyParser();
        parser.parse(filename, text, ignoreLargeTests);
        return parser.getResult();
    }
}

/**
 * Combine multiple checkers into one.
 */
class PolicyCheckerChain implements PolicyChecker {
    private final ArrayList<PolicyChecker> mChain = new ArrayList<>();

    public void add(PolicyChecker checker) {
        mChain.add(checker);
    }

    @Override
    public RunPolicy getClassPolicy(Class<?> testClass) {
        for (PolicyChecker checker : mChain) {
            var p = checker.getClassPolicy(testClass);
            if (p != null && p != RunPolicy.Unspecified) {
                return p;
            }
        }
        return RunPolicy.Unspecified;
    }

    @Override
    public RunPolicy getMethodPolicy(Description description) {
        for (PolicyChecker checker : mChain) {
            var p = checker.getMethodPolicy(description);
            if (p != null && p != RunPolicy.Unspecified) {
                return p;
            }
        }
        return RunPolicy.Unspecified;
    }
}

/**
 * Used for just logging all test methods without running them.
 *
 * For classes, it always returns {@link RunPolicy#Unspecified} to fall back to the following
 * policies, but for methods, it'll always return "never".
 */
class SkipAllMethodsPolicyChecker implements PolicyChecker {
    SkipAllMethodsPolicyChecker() {
    }

    @Override
    public RunPolicy getClassPolicy(Class<?> testClass) {
        return RunPolicy.Unspecified;
    }

    @Override
    public RunPolicy getMethodPolicy(Description description) {
        return RunPolicy.NeverRun;
    }
}

/**
 * {@link PolicyChecker} based on annotations.
 */
class AnnotationPolicyChecker implements PolicyChecker {
    @Override
    public RunPolicy getClassPolicy(@NonNull Class<?> testClass) {
        if (testClass.getAnnotation(EnabledOnRavenwood.class) != null) {
            return RunPolicy.Enabled;
        } else if (testClass.getAnnotation(DisabledOnRavenwood.class) != null) {
            return RunPolicy.Disabled;
        }
        return RunPolicy.Unspecified;
    }

    @Override
    public RunPolicy getMethodPolicy(Description description) {
        // If it's a method, then the method annotation will take precedence.
        if (description.isTest()) {
            if (description.getAnnotation(EnabledOnRavenwood.class) != null) {
                return RunPolicy.Enabled;
            } else if (description.getAnnotation(DisabledOnRavenwood.class) != null) {
                return RunPolicy.Disabled;
            }
        }
        // Otherwise, consult any class-level annotations
        return getClassPolicy(RavenwoodImplUtils.getDescriptionTestClass(description));
    }
}

/**
 * Text file based policy checker.
 */
class PatternBasedChecker implements PolicyChecker {
    private static class ClassPolicy {
        public final Pattern namePattern;
        public final RunPolicy policy;

        ClassPolicy(Pattern pattern, RunPolicy policy) {
            this.namePattern = pattern;
            this.policy = policy;
        }
    }

    /**
     * Class name patterns -> policy. Because we support wildcards, we use a list here,
     * and search from the top.
     *
     * This represents lines without a '#' in the policy file.
     */
    private final ArrayList<ClassPolicy> mClassPolicies = new ArrayList<>();

    /**
     * Class name (no pattern allowed) -> method name minus [...] (for tests with params) -> policy.
     *
     * This represents lines with a '#' in the policy file.
     */
    private final Map<String, Map<String, RunPolicy>> mMethodPolicies = new HashMap<>();

    /**
     * When we have a method policy, we need to enable the enclosing class. To make the look-up
     * faster, we store the "computed" class policy here, which overrides {@link #mClassPolicies}.
     */
    private final Map<String, RunPolicy> mClassPolicyFromMethods = new HashMap<>();

    /**
     * Adda class policy.
     */
    public void addClassPolicy(Pattern pattern, RunPolicy policy) {
        mClassPolicies.add(new ClassPolicy(pattern, policy));
    }

    /**
     * Adda method policy.
     */
    public void addMethodPolicy(String className,
            String methodName,
            RunPolicy policy,
            String filename,
            int line) {
        var classPolicy = mMethodPolicies.computeIfAbsent(className,
                (k) -> new HashMap<>());
        if (classPolicy.get(methodName) != null) {
            throw new ParseException(
                    "Method policy already defined for " + methodName, filename, line);
        }
        classPolicy.put(methodName, policy);

        // When a class has any enabled methods, the class needs to be enabled too.
        // When a class has ay disabled methods, the class needs to be executed in the
        // RunDisabledToo mode, so we set "disabled", unless it already is "enabled".
        if (policy == RunPolicy.Enabled) {
            mClassPolicyFromMethods.put(className, policy);
        } else if (policy == RunPolicy.Disabled) {
            if (mClassPolicyFromMethods.get(className) != RunPolicy.Enabled) {
                mClassPolicyFromMethods.put(className, policy);
            }
        }
    }

    /**
     * Get the class policy purely from {@link #mClassPolicies}, without taking into account
     * any method-based policies.
     */
    private RunPolicy getPureClassPolicy(Class<?> testClass) {
        var className = testClass.getName();

        for (ClassPolicy policy : mClassPolicies) {
            if (policy.namePattern.matcher(className).matches()) {
                return policy.policy;
            }
        }
        return RunPolicy.Unspecified;
    }

    private static final Pattern sParamMatcher = Pattern.compile("\\[.*$");

    /**
     * Get the class policy. Here, unlike {@link #getPureClassPolicy}, we need to
     * take into account any method-based policies, so that a class with "enabled" method will
     * actually be executed.
     */
    @Override
    public RunPolicy getClassPolicy(Class<?> testClass) {
        var className = testClass.getName();

        var policyFromMethods = mClassPolicyFromMethods.get(className);
        if (policyFromMethods != null) { // We don't have "unspecified" here, so just a null check.
            return policyFromMethods;
        }
        return getPureClassPolicy(testClass);
    }

    @Override
    public RunPolicy getMethodPolicy(Description description) {
        var className = description.getClassName();

        // Method name without [...].
        var methodName = sParamMatcher.matcher(description.getMethodName()).replaceFirst("");

        // If the enclosing class has "never", disable all its methods, even if there's an "enable"
        // method policy.
        // This is to easily disable certain tests in a policy file, even if it already
        // has enabled methods.
        var classPolicy = getPureClassPolicy(
                RavenwoodImplUtils.getDescriptionTestClass(description));
        if (classPolicy == RunPolicy.NeverRun) {
            return classPolicy;
        }

        // Check if we have an exact policy.
        var methods = mMethodPolicies.get(className);
        if (methods != null) {
            var policy = methods.get(methodName);
            if (policy != null) { // We don't have "unspecified" here, so just a null check.
                return policy;
            }
        }

        // Method has no explicit policy.
        // Here, (unlike the annotation based checker) we can't fallback to
        // getClassPolicy(), because getClassPolicy() consults mClassPolicyFromMethods, which
        // says "enabled" if the class has any enabled methods.
        // However, what's wrong -- when a method has no explicit policy, we still don't want
        // to run it (assuming the class doesn't have "enable" policy set explicitly).
        //
        // So instead, we fallback to getPureClassPolicy(), which only uses mClassPolicies.
        return classPolicy;
    }
}

/**
 * Parse enablement policy files and build {@link PatternBasedChecker}.
 */
class EnablementTextPolicyParser {
    private static final String TAG = "EnablementTextPolicyParser";

    /** Exception for parse errors. */
    public static class ParseException extends RuntimeException {
        public ParseException(String message, String filename) {
            this(message, filename, 0, null);
        }

        public ParseException(String message, String filename, Throwable cause) {
            this(message, filename, 0, cause);
        }

        public ParseException(String message, String filename, int line) {
            this(message, filename, line, null);
        }

        public ParseException(String message, String filename, int line, Throwable cause) {
            super(buildMessage(message, filename, line), cause);
        }

        private static String buildMessage(String message, String filename, int line) {
            var sb = new StringBuilder();
            sb.append(message);
            sb.append(": file '");
            sb.append(filename);
            sb.append("'");

            if (line > 0) {
                sb.append(" in line ");
                sb.append(line);
            }

            return sb.toString();
        }
    }

    /**
     * Parse given files.
     */
    public static PatternBasedChecker parsePolicyFiles(@NonNull String[] files,
            boolean ignoreLargeTests
    ) {
        var parser = new EnablementTextPolicyParser();
        for (String file : files) {
            try {
                parser.parse(file, Files.readString(Path.of(file)), ignoreLargeTests);
            } catch (IOException e) {
                throw new ParseException("Unabled to read enablement policy", file, e);
            }
        }
        return parser.getResult();
    }

    private final PatternBasedChecker mResult = new PatternBasedChecker();

    /**
     * Constructor
     */
    EnablementTextPolicyParser() {
    }

    /**
     * Return the result.
     */
    PatternBasedChecker getResult() {
        return mResult;
    }

    static final String[] EMPTY = new String[0];
    static final Pattern SPACE = Pattern.compile("\\s+");
    static final Pattern INLINE_COMMENT = Pattern.compile("\\s+#.*$");

    /**
     * Normalize and split a line.
     *
     * - Lines starting with "#" are ignored.
     * - Inline comments start with " #"
     * - Leading/trailing whitespace are all ignored.
     *
     * Unlike String.split(), it'll return an empty array for an empty line.
     */
    private String[] split(String line) {
        if (line.startsWith("#")) {
            return EMPTY;
        }
        line = INLINE_COMMENT.matcher(line).replaceFirst("");
        line = line.trim();
        if (line.isEmpty()) {
            return EMPTY;
        }
        return SPACE.split(line);
    }

    /**
     * Parse a whole policy file content.
     */
    void parse(String filename, String text, boolean ignoreLargeTests) throws IOException {
        // The first line should be the module name.
        var rd = new BufferedReader(new StringReader(text));

        // Skip if it's for other test modules.
        if (!checkHeader(filename, rd)) {
            return;
        }

        String line;
        int n = 0; // line number
        while ((line = rd.readLine()) != null) {
            n++;

            // First, parse the line
            var cols = split(line);
            if (cols.length == 0) {
                continue;
            }

            var classMethod = cols[0].split("#");
            var policy = RunPolicy.Enabled; // Default to enabled
            if (cols.length >= 2) {
                try {
                    policy = RunPolicy.parse(cols[1]);
                } catch (IllegalArgumentException e) {
                    throw new ParseException(e.getMessage(), filename, n);
                }
            }

            var className = classMethod[0];
            var isMethod = classMethod.length > 1;

            // Remaining columns are options.
            var isLarge = false;
            for (int i = 2; i < cols.length; i++) {
                var v = cols[i];
                if (":large".equals(v.toLowerCase(Locale.ROOT))) {
                    isLarge = true;
                    continue;
                }
                throw new ParseException("Unknown option \"" + v + "\"", filename, n);
            }
            if (isLarge && ignoreLargeTests) {
                policy = RunPolicy.NeverRun;
            }

            // Set the policy in mResult.
            if (!isMethod) {
                // It's a package / class policy.
                var pattern = parseClassNameWildcard(className);
                mResult.addClassPolicy(pattern, policy);
                // Log.v(TAG, "class: " + pattern.pattern() + " -> " + policy);
            } else {
                // It's a method policy.
                var methodName = classMethod[1];
                if (className.contains("*")) {
                    throw new ParseException(
                            "Method policy cannot use wildcards: line='" + line + "'", filename, n);
                }
                mResult.addMethodPolicy(className, methodName, policy, filename, n);
                // Log.v(TAG, "method: " + className + "." + methodName + " -> " + policy);
            }
        }
    }

    /**
     * Check the first line of a policy file, which should contain "!module ModuleName",
     * and return true if it's for the currently running test.
     */
    private boolean checkHeader(String file, BufferedReader rd) throws IOException {
        var header = rd.readLine();
        if (header == null) {
            throw new ParseException("File is empty", file);
        }
        var cols = split(header);

        if (cols.length != 2 || !"!module".equals(cols[0])) {
            throw new ParseException("File must start with '!module ModuleName'", file);
        }

        if (!cols[1].equals(RavenwoodEnvironment.getInstance().getTestModuleName())) {
            Log.i(TAG, "Skip " + file);
            return false;
        }
        Log.i(TAG, "Parsing " + file);
        return true;
    }
}

/**
 * {@link PolicyChecker} based on a {@link Pattern}.
 */
class RegexRunFilter implements PolicyChecker {
    private static final String TAG = "RegexRunFilter";
    private final Pattern mRegex;
    private final RunPolicy mMatchingPolicy;
    private final RunPolicy mNonMatchingPolicy;

    /**
     * Constructor.
     *
     * @param regex regex to match. We apply it on both classes and methods.
     * @param matchingPolicy policy for matching items
     * @param nonMatchingPolicy  policy for non-matching items
     */
    public RegexRunFilter(
            @NonNull Pattern regex,
            @NonNull RunPolicy matchingPolicy,
            @NonNull RunPolicy nonMatchingPolicy) {
        mRegex = regex;
        mMatchingPolicy = matchingPolicy;
        mNonMatchingPolicy = nonMatchingPolicy;
    }

    private boolean classMatches(Class<?> testClass) {
        return mRegex.matcher(testClass.getName()).find();
    }

    @Override
    public RunPolicy getClassPolicy(Class<?> testClass) {
        if (classMatches(testClass)) {
            return mMatchingPolicy;
        }
        // If the class has any test matching test method, still return "enabled".
        // (but unmatched methods would be "unspecified".)
        for (var m : testClass.getMethods()) {
            if (!isTestMethod(testClass, m)) {
                continue;
            }
            if (mRegex.matcher(toCanonicalTestName(testClass, m)).find()) {
                return mMatchingPolicy;
            }
        }
        return mNonMatchingPolicy;
    }

    @Override
    public RunPolicy getMethodPolicy(Description description) {
        if (mRegex.matcher(toCanonicalTestName(description)).find()) {
            return mMatchingPolicy;
        }
        return classMatches(RavenwoodImplUtils.getDescriptionTestClass(description))
                ? mMatchingPolicy : mNonMatchingPolicy;
    }
}
