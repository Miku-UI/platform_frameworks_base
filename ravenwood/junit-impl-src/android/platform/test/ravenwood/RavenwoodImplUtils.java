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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Singleton;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ravenwood.common.StackTrace;

import org.junit.internal.management.ManagementFactory;
import org.junit.runner.Description;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Various utility methods that can be used on the "impl" side.
 */
public class RavenwoodImplUtils {
    private static final String TAG = RavenwoodDriver.TAG;

    private RavenwoodImplUtils() {
    }

    /**
     * Base class for a simple key value cache. Subclass implements {@link #compute}.
     */
    public abstract static class MapCache<K, V> {
        private final Object mLock = new Object();

        private final HashMap<K, V> mCache = new HashMap<>();

        /**
         * Subclass implements it.
         *
         * This method may be called for the same key multiple times if access to the same
         * key happens on multiple threads at the same time.
         */
        protected abstract V compute(K key);

        /**
         * @return the value for a given key, using the cache.
         */
        public V get(K key) {
            synchronized (mLock) {
                var cached = mCache.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            V value = compute(key);
            synchronized (mLock) {
                mCache.put(key, value);
            }
            return value;
        }
    }

    /**
     * Helper method to build a {@link StackTrace} object.
     *
     * @param message exception message.
     * @param stackTraceStartClass if set, we remove the stacktrace up to this class.
     * @param removeMatchingFrameToo whether to remove the matching class too.
     */
    public static StackTrace getStackTrace(
            @Nullable String message,
            @Nullable Class<?> stackTraceStartClass,
            boolean removeMatchingFrameToo
    ) {
        // If we're in the middle of a message dispatch, chaing the message poster trace too.
        // TODO: I'd be great if we could show Executor.execute() call too.
        var poster = RavenwoodMessageTracker.getInstance().getCurrentMessagePoster();

        var ret = new StackTrace(message, poster);

        if (stackTraceStartClass != null) {
            ret.removeStackTraceUntil(
                    StackTrace.classPredicate(stackTraceStartClass),
                    removeMatchingFrameToo);
        }

        return ret;
    }

    /**
     * @return comment line arguments to the current JVM.
     */
    public static List<String> getJvmArguments() {
        // Note, we use the wrapper in JUnit4, not the actual class (
        // java.lang.management.ManagementFactory), because we can't see the later at the build
        // because this source file is compiled for the device target, where ManagementFactory
        // doesn't exist.
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    private static volatile Boolean sIsDebuggerAttached;

    /**
     * @return if the debugger is attached.
     */
    public static boolean isDebuggerAttached() {
        if (sIsDebuggerAttached == null) {
            var ret = false;
            for (String arg : getJvmArguments()) {
                if (arg.startsWith("-agentlib:jdwp=")) {
                    ret = true;
                    break;
                }
            }
            sIsDebuggerAttached = ret;
        }
        return sIsDebuggerAttached;
    }

    /**
     * Async handler on the main thread. Used to call a dump() method without getting blocked
     * by sync barriers.
     */
    private static final Singleton<Handler> sMainAsyncHandler = new Singleton<>() {
        @Override
        protected Handler create() {
            return new Handler(Looper.getMainLooper(), null, true);
        }
    };

    /**
     * Use this to call a dump() method on the main thread. If the calling method is _not_
     * the main thread, we can apply a timeout. But if the calling thread is the main thread,
     * the timeout won't apply.
     */
    public static String callDumpOnMainThreadWithTimeout(
            @NonNull Consumer<PrintStream> dumper,
            int timeoutMillis) {
        var bst = new ByteArrayOutputStream(1024 * 4);
        var pst = new PrintStream(bst, /* autoflush= */ true);
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                dumper.accept(pst);
            } else {
                RavenwoodUtils.runOnHandlerSync(sMainAsyncHandler.get(), () -> {
                    dumper.accept(pst);
                    return null;
                }, timeoutMillis);
            }
        } catch (Throwable th) {
            var msg = "Exception detected while running dump: " + th.getMessage();
            Log.w(TAG, msg, th);
            RavenwoodErrorHandler.onWarningDetected(msg);
        }
        return bst.toString();
    }

    /**
     * Safer version of {@link Description#getTestClass()}, which normally returns
     * a class. However, it can return null, which we observed with
     * {@link org.junit.runners.Parameterized}. In this case, the description is a suite
     * and has children, which do have a test class set. So this method digs into children
     * recursively and returns the test class that's found first.
     */
    @NonNull
    public static Class<?> getDescriptionTestClass(@NonNull Description desc ) {
        var ret = getDescriptionTestClassInner(desc);
        if (ret != null) {
            return ret;
        }
        throw new IllegalStateException("Cannot get test class from Description: " + desc);
    }

    @Nullable
    private static Class<?> getDescriptionTestClassInner(@NonNull Description desc ) {
        // Normally, a Description has a test class.
        if (desc.getTestClass() != null) {
            return desc.getTestClass();
        }
        // If not, which can happen with some parameterized runner,
        // we look into children and
        for (var child : desc.getChildren()) {
            var childClass = getDescriptionTestClassInner(child);
            if (childClass != null) {
                return childClass;
            }
        }
        return null;
    }

    /**
     * Utility method {@link #dumpStack} which prints the call hierarchy on the current
     * thread in reverse order. It skips all the top-level methods that are exactly the
     * same as the previous call to make it less verbose.
     */
    public static void dumpStack(String logTag, int skipFrames) {
        StackDumper.dumpStack(logTag, skipFrames + 1 /* +1 for this method */);
    }

    private static class StackDumper {

        private static final StackWalker sWalker = StackWalker.getInstance(
                StackWalker.Option.RETAIN_CLASS_REFERENCE);

        /* Use to remember the last call on the same method. */
        private static class ThreadInfo {
            List<StackFrame> mFrames;

            ThreadInfo(List<StackFrame> frames) {
                mFrames = frames;
            }
        }

        private static final ThreadLocal<ThreadInfo> sThreadInfo = ThreadLocal.withInitial(() -> {
            var frames = new ArrayList<StackFrame>();
            return new ThreadInfo(frames);
        });

        private static void dumpStack(String logTag, int skipFrames) {
            var lastFrames = sThreadInfo.get().mFrames;
            var curFrames = sWalker.walk((s) ->
                    s.skip(skipFrames + 1) // "+1" for to skip this method (dumpStack) itself.
                            .toList());

            var end = findCommonAncestor(curFrames, lastFrames);
            for (int i = 0; i <= end; i++) {
                var f = curFrames.get(i);
                Log.d(logTag, "  " + " ".repeat(i) + f.getClassName()
                        + "." + f.getMethodName() + f.getDescriptor()
                        + " (" + f.getFileName() + ":" + f.getLineNumber() + ")");
            }

            sThreadInfo.get().mFrames = curFrames;
        }
    }

    /**
     * Find the most recent common ancestor between the current stack trace and
     * the last stack trace.
     */
    @VisibleForTesting
    public static int findCommonAncestor(List<StackFrame> cur, List<StackFrame> last) {
        var cpos = cur.size();
        var lpos = last.size();
        var lastMatch = cur.size() - 1;
        for (;;) {
            cpos--;
            lpos--;
            if ((cpos < 0) || (lpos < 0)) {
                break;
            }
            var cf = cur.get(cpos);
            var lf = last.get(lpos);
            if (!(cf.getClassName().equals(lf.getClassName())
                    && cf.getMethodName().equals(lf.getMethodName())
                    && cf.getDescriptor().equals(lf.getDescriptor()))) {
                break;
            }
            lastMatch = cpos;
            // Compare the parent frame...
        }
        return lastMatch;
    }
}
