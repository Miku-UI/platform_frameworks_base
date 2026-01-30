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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.platform.test.ravenwood.RavenwoodInternalUtils.MapCache;
import android.util.Log;

import com.android.hoststubgen.hosthelper.HostTestUtils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ravenwood.RavenwoodRuntimeNative;
import com.android.ravenwood.common.SneakyThrow;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Provides a method call hook that prints almost all (see below) the framework methods being
 * called with indentation.
 *
 * Enable this method call logging by adding the following lines to the options file.
 * (frameworks/base/ravenwood/texts/ravenwood-standard-options.txt)

 --default-method-call-hook
 android.platform.test.ravenwood.RavenwoodMethodCallLogger.logMethodCall

 *
 * We don't log methods that are trivial, uninteresting, or would be too noisy.
 * e.g. we don't want to log any logging related methods or collection APIs.
 *
 * This class also dumps all the called method names in the
 * {@link #CALLED_METHOD_POLICY_FILE} file in the form of a policy file.
 * Optionally, if $RAVENWOOD_METHOD_DUMP_REASON_FILTER is defined, the method policy dump
 * will only contain methods with filter reasons matching it as a regex.
 */
public class RavenwoodMethodCallLogger {
    private static final String TAG = "RavenwoodMethodCallLogger";

    public enum LogMode {
        Default,
        None,
        All,
    }

    private static final LogMode LOG_MODE =
            switch ("" + System.getenv("RAVENWOOD_METHOD_LOG_MODE")) {
        case "all" -> LogMode.All;
        case "0" -> LogMode.None;
        default -> LogMode.Default;
    };

    /** The policy file is created with this filename. */
    private static final String CALLED_METHOD_POLICY_FILE = "/tmp/ravenwood-called-methods.txt";

    /**
     * If set, we filter methods by applying this regex on the HostStubGen "filter reason"
     * when generating the policy file.
     */
    private static final String CALLED_METHOD_DUMP_REASON_FILTER_RE = System.getenv(
            "RAVENWOOD_METHOD_DUMP_REASON_FILTER");

    /** It's a singleton, except we create different instances for unit tests. */
    @VisibleForTesting
    public RavenwoodMethodCallLogger(LogMode logMode) {
        mLogMode = logMode;
    }

    /** Singleton instance */
    private static final RavenwoodMethodCallLogger sInstance =
            new RavenwoodMethodCallLogger(LOG_MODE);

    /**
     * @return the singleton instance.
     */
    public static RavenwoodMethodCallLogger getInstance() {
        return sInstance;
    }

    /** Entry point for HostStubGen generated code, which needs to be static.*/
    public static void logMethodCall(
            Class<?> methodClass,
            String methodName,
            String methodDesc
    ) {
        getInstance().onMethodCalled(methodClass, methodName, methodDesc);
    }


    /** We don't want to log anything before ravenwood is initialized. This flag controls it.*/
    private volatile boolean mEnabled = false;

    private volatile PrintStream mOut = System.out;

    private final LogMode mLogMode;

    private static class MethodDesc {
        public final String name;
        public final String desc;
        private String mReason;

        public MethodDesc(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        public void setReason(String reason) {
            mReason = reason;
        }

        public String getReason() {
            return mReason;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MethodDesc that)) return false;
            return Objects.equals(name, that.name) && Objects.equals(desc, that.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, desc);
        }
    }

    /** Stores all called methods. */
    @GuardedBy("sAllMethods")
    private final Map<Class<?>, Set<MethodDesc>> mAllMethods = new HashMap<>();

    /** Information about the current thread. */
    private static class ThreadInfo {
        /**
         * We save the current thread's nest call level here and use that as the initial level.
         * We do it because otherwise the nest level would be too deep by the time test
         * starts.
         */
        public final int mInitialNestLevel = Thread.currentThread().getStackTrace().length;
    }

    private final ThreadLocal<ThreadInfo> mThreadInfo = ThreadLocal.withInitial(ThreadInfo::new);

    /** Classes that should be logged. Uses a map for fast lookup. */
    private static final HashSet<Class<?>> sIgnoreClasses = new HashSet<>();
    static {
        // The following classes are not interesting...
        sIgnoreClasses.add(android.util.Log.class);
        sIgnoreClasses.add(android.util.Slog.class);
        sIgnoreClasses.add(android.util.EventLog.class);
        sIgnoreClasses.add(android.util.TimingsTraceLog.class);
        sIgnoreClasses.add(android.util.MathUtils.class);

        sIgnoreClasses.add(android.app.PropertyInvalidatedCache.class);
        sIgnoreClasses.add(android.os.IpcDataCache.class);

        sIgnoreClasses.add(android.text.FontConfig.class);

        sIgnoreClasses.add(android.app.PropertyInvalidatedCache.class);
        sIgnoreClasses.add(android.os.IpcDataCache.class);

        sIgnoreClasses.add(android.os.SystemClock.class);
        sIgnoreClasses.add(android.os.Trace.class);
        sIgnoreClasses.add(android.os.LocaleList.class);
        sIgnoreClasses.add(android.os.Build.class);
        sIgnoreClasses.add(android.os.SystemProperties.class);
        sIgnoreClasses.add(android.os.UserHandle.class);
        sIgnoreClasses.add(android.os.MessageQueue.class);

        sIgnoreClasses.add(com.android.internal.util.Preconditions.class);

        sIgnoreClasses.add(android.graphics.FontListParser.class);
        sIgnoreClasses.add(android.graphics.ColorSpace.class);

        sIgnoreClasses.add(android.graphics.fonts.FontStyle.class);
        sIgnoreClasses.add(android.graphics.fonts.FontVariationAxis.class);

        sIgnoreClasses.add(com.android.internal.compat.CompatibilityChangeInfo.class);
        sIgnoreClasses.add(com.android.internal.os.LoggingPrintStream.class);

        sIgnoreClasses.add(android.os.ThreadLocalWorkSource.class);

        // Following classes *may* be interesting for some purposes, but the initialization is
        // too noisy...
        sIgnoreClasses.add(android.graphics.fonts.SystemFonts.class);
        sIgnoreClasses.add(android.content.res.FontScaleConverterFactory.class);
        sIgnoreClasses.add(android.content.res.FontScaleConverterImpl.class);
    }

    /**
     * Return if a class should be ignored. Uses {link #sIgnoreCladsses}, but
     * we ignore more classes.
     *
     * If we make it operate on class names as strings directly, we wouldn't need
     * the reflections. But we cache the result anyway, so that's not super-critical.
     * Using class objects allow us to easily check inheritance too.
     */
    private boolean shouldIgnoreClass(Class<?> clazz) {
        switch (mLogMode) {
            case All:
                return false;
            case None:
                return true;
            default:
                break;
        }
        if (sIgnoreClasses.contains(clazz)) {
            return true;
        }
        // We want to hide a lot of classes from android.util.
        if ("android.util".equals(clazz.getPackageName())) {
            // Let's also ignore collection-ish classes in android.util.
            if (java.util.Collection.class.isAssignableFrom(clazz)
                    || java.util.Map.class.isAssignableFrom(clazz)
                    || java.util.Iterator.class.isAssignableFrom(clazz)
            ) {
                return true;
            }
            if (clazz.getSimpleName().endsWith("Array")) {
                return true;
            }
        }
        if ("android.util.proto".equals(clazz.getPackageName())) {
            return true;
        }
        switch (clazz.getSimpleName()) {
            case "EventLogTags":
                return true;
        }

        // Following are classes that can't be referred to here directly.
        // e.g. AndroidPrintStream is package-private, so we can't use its "class" here.
        switch (clazz.getName()) {
            case "com.android.internal.os.AndroidPrintStream":
                return true;
        }
        if (clazz.getPackageName().startsWith("repackaged.services.com.android.server.compat")) {
            return true;
        }
        return false;
    }

    private boolean shouldLogUncached(Class<?> clazz) {
        // Should we ignore this class?
        if (shouldIgnoreClass(clazz)) {
            return false;
        }
        // Is it a nested class in a class that should be ignored?
        var host = clazz.getNestHost();
        if (host != clazz && shouldIgnoreClass(host)) {
            return false;
        }

        var pkg = clazz.getPackageName();
        if (pkg.startsWith("android.icu")) {
            return false;
        }

        return true;
    }

    /** Cache for {@link #shouldLog(Class)} */
    private final MapCache<Class<?>, Boolean> mClassEnabledCache = new MapCache<>() {
        @Override
        protected Boolean compute(Class<?> key) {
            return shouldLogUncached(key);
        }
    };

    /** @return whether a class should be logged */
    private boolean shouldLog(Class<?> clazz) {
        return mClassEnabledCache.get(clazz);
    }

    /** Cache for {@link #shouldLog(String)} to avoid repeated reflections. */
    private final MapCache<String, Boolean> mStringClassEnabledCache = new MapCache<>() {
        @Override
        protected Boolean compute(String className) {
            try {
                Class<?> c = Class.forName(className);
                if (!shouldLog(c)) {
                    return false;
                }
            } catch (ClassNotFoundException e) {
                // Assume this class is loggable.
            }
            return true;
        }
    };

    /** @return whether a class should be logged */
    @VisibleForTesting
    public boolean shouldLog(String className) {
        return mStringClassEnabledCache.get(className);
    }

    /**
     * Call this to enable logging.
     */
    public void enable(@NonNull PrintStream out) {
        mEnabled = true;
        mOut = Objects.requireNonNull(out);

        // It's called from the test thread (Java's main thread). Because we're already
        // in deep nest calls, we initialize the initial nest level here.
        mThreadInfo.get();
    }

    /** Called when a method is called. */
    public void onMethodCalled(
            @NonNull Class<?> methodClass,
            @NonNull String methodName,
            @NonNull String methodDesc
    ) {
        if (!mEnabled) {
            return;
        }
        synchronized (mAllMethods) {
            var set = mAllMethods.computeIfAbsent(methodClass, (k) -> new HashSet<>());
            set.add(new MethodDesc(methodName, methodDesc));
        }
        var log = buildMethodCallLogLine(methodClass, methodName, methodDesc,
                Thread.currentThread());
        if (log != null) {
            mOut.print(log);
        }
    }

    /** Inner method exposed for testing. */
    @Nullable
    private String buildMethodCallLogLine(
            @NonNull Class<?> methodClass,
            @NonNull String methodName,
            @NonNull String methodDesc,
            @NonNull Thread mThread
    ) {
        if (!shouldLog(methodClass)) {
            return null;
        }
        final var ti = mThreadInfo.get();
        final var stack = Thread.currentThread().getStackTrace();
        final int nestLevel = stack.length - ti.mInitialNestLevel;

        // If a method is called from a "ignored" class, we don't want to log it,
        // even if this method itself is loggable.
        //
        // To do so, we have to check all the classes in the stacktrace (unfortunately) every time.
        // That's because we can't re-construct the whole call tree only from the information
        // from the method call log call, because we don't know when we exit each method.
        for (var sf : stack) {
            if (!shouldLog(sf.getClassName())) {
                return null;
            }
        }
        var sb = new StringBuilder();
        sb.append("# [");
        sb.append(getRawThreadId());
        sb.append(": ");
        sb.append(mThread.getName());
        sb.append("]: ");
        sb.append("[@");
        sb.append(String.format("%2d", nestLevel));
        sb.append("] ");
        for (int i = 0; i < nestLevel; i++) {
            sb.append("  ");
        }
        sb.append(methodClass.getName() + "." + methodName + methodDesc);
        sb.append('\n');
        return sb.toString();
    }

    /** To be overridden for unit tests */
    @VisibleForTesting
    public int getRawThreadId() {
        return RavenwoodRuntimeNative.gettid();
    }

    /**
     * Print all called methods in the form of "policy" file.
     */
    public void dumpAllCalledMethods() {
        dumpAllCalledMethodsForFileInner(
                CALLED_METHOD_POLICY_FILE, CALLED_METHOD_DUMP_REASON_FILTER_RE);
    }

    /**
     * Print all called methods in the form of "policy" file.
     */
    @VisibleForTesting
    public void dumpAllCalledMethodsForFileInner(@NonNull String filename,
            @Nullable String reasonFilterRegex) {
        Supplier<OutputStream> opener = () -> {
            try {
                return new FileOutputStream(filename);
            } catch (FileNotFoundException e) {
                SneakyThrow.sneakyThrow(e);
                return null;
            }
        };
        dumpAllCalledMethodsInner(opener, reasonFilterRegex, filename);
    }

    /** Inner method exposed for testing. */
    @VisibleForTesting
    public void dumpAllCalledMethodsInner(@NonNull Supplier<OutputStream> opener,
            @Nullable String resonFilterRegex, @NonNull String outputFileNameForLogging) {
        if (!mEnabled) {
            return;
        }

        synchronized (mAllMethods) {
            if (mAllMethods.isEmpty()) {
                return;
            }
            // "Filter reason" filter.
            final Predicate<String> reasonFilter;
            if (resonFilterRegex == null || resonFilterRegex.isEmpty()) {
                reasonFilter = (reason) -> true;
            } else {
                var pat = Pattern.compile(resonFilterRegex);

                reasonFilter = (reason) -> reason != null && pat.matcher(reason).find();
            }

            var classCount = 0;
            var methodCount = 0;
            try (PrintWriter wr = new PrintWriter(new BufferedOutputStream(opener.get()))) {
                for (var clazz : mAllMethods.keySet().stream()
                        .sorted(Comparator.comparing(Class::getName))
                        .toList()) {
                    classCount++;

                    var classMethods = mAllMethods.get(clazz);
                    // Set the reasons.
                    for (var m : classMethods) {
                        m.setReason(getMethodFilterReason(clazz, m.name, m.desc));
                    }

                    var methods = mAllMethods.get(clazz).stream()
                            .filter(m -> reasonFilter.test(m.getReason()))
                            .sorted(Comparator.comparing((MethodDesc a) -> a.name)
                                    .thenComparing(a -> a.desc))
                            .toList();

                    if (methods.isEmpty()) {
                        continue;
                    }

                    wr.print("class ");
                    wr.print(clazz.getName());
                    wr.print("\tkeep");
                    wr.println();
                    for (var method : methods) {
                        methodCount++;

                        wr.print("    method ");
                        wr.print(method.name);
                        wr.print(" ");
                        wr.print(method.desc);
                        wr.print("\tkeep");

                        var reason = method.getReason();
                        if (reason != null && !reason.isEmpty()) {
                            wr.print("\t# ");
                            wr.print(reason);
                        }

                        wr.println();
                    }
                    wr.println();
                }
                Log.i(TAG, String.format(
                        "Wrote called methods to file://%s (%d classes, %d methods)",
                        outputFileNameForLogging, classCount, methodCount));
            } catch (Exception e) {
                Log.w(TAG, "Exception while dumping called methods", e);
            }
        }
    }

    /**
     * Find a specified method, and find its "reason" from the HostStubGen annotation.
     */
    @Nullable
    private static String getMethodFilterReason(
            @NonNull Class<?> clazz,
            @NonNull String methodName,
            @NonNull String methodDesc) {
        // Special case: If the method is "<clinit>", we can't get annotations from it,
        // so let's just use the class's reason instead.
        if ("<clinit>".equals(methodName)) {
            return HostTestUtils.getHostStubGenAnnotationReason(clazz);
        }

        // Find the method, and extract the reason from the annotation, if any.
        var m = RavenwoodAsmUtils.getMethodOrNull(clazz, methodName, methodDesc);
        if (m == null) {
            return null;
        }
        return HostTestUtils.getHostStubGenAnnotationReason(m);
    }
}