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
package com.android.ravenwood.common;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.modules.utils.ravenwood.RavenwoodHelper;

import org.junit.runner.Description;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class RavenwoodInternalUtils {
    public static final String TAG = "Ravenwood";

    private RavenwoodInternalUtils() {
    }

    public static final String ANDROID_PACKAGE_NAME = "android";

    /**
     * If set to "1", we enable the verbose logging.
     * <p>
     * (See also InitLogging() in http://ac/system/libbase/logging.cpp)
     */
    public static final boolean RAVENWOOD_VERBOSE_LOGGING =
            "1".equals(System.getenv("RAVENWOOD_VERBOSE"))
                    || "1".equals(System.getProperty("ravenwood.verbose"));

    private static boolean sEnableExtraRuntimeCheck =
            "1".equals(System.getenv("RAVENWOOD_ENABLE_EXTRA_RUNTIME_CHECK"));

    public static final String RAVENWOOD_SYSPROP = "ro.is_on_ravenwood";

    /**
     * @return if we're running on Ravenwood.
     */
    public static boolean isOnRavenwood() {
        return RavenwoodHelper.isRunningOnRavenwood();
    }

    /**
     * @return if the various extra runtime check should be enabled.
     */
    public static boolean shouldEnableExtraRuntimeCheck() {
        return sEnableExtraRuntimeCheck;
    }

    /**
     * Simple logging method.
     */
    public static void log(String tag, String message) {
        // Avoid using Android's Log class, which could be broken for various reasons.
        // (e.g. the JNI file doesn't exist for whatever reason)
        System.out.print(tag + ": " + message + "\n");
    }

    /**
     * Simple logging method.
     */
    private void log(String tag, String format, Object... args) {
        log(tag, String.format(format, args));
    }

    /**
     * Internal implementation of {@link android.platform.test.ravenwood.RavenwoodRule#loadJniLibrary(String)}
     */
    public static void loadJniLibrary(String libname) {
        if (isOnRavenwood()) {
            System.load(getJniLibraryPath(libname));
        } else {
            System.loadLibrary(libname);
        }
    }

    /**
     * Find the shared library path from java.library.path.
     */
    public static String getJniLibraryPath(String libname) {
        var path = System.getProperty("java.library.path");
        var filename = "lib" + libname + ".so";

        System.out.println("Looking for library " + libname + ".so in java.library.path:" + path);

        try {
            if (path == null) {
                throw new UnsatisfiedLinkError("Cannot find library " + libname + "."
                        + " Property java.library.path not set!");
            }
            for (var dir : path.split(":")) {
                var file = new File(dir + "/" + filename);
                if (file.exists()) {
                    return file.getAbsolutePath();
                }
            }
        } catch (Throwable e) {
            dumpFiles(System.out);
            throw e;
        }
        throw new UnsatisfiedLinkError("Library " + libname + " not found in "
                + "java.library.path: " + path);
    }

    private static void dumpFiles(PrintStream out) {
        try {
            var path = System.getProperty("java.library.path");
            out.println("# java.library.path=" + path);

            for (var dir : path.split(":")) {
                listFiles(out, new File(dir), "");

                var gparent = new File((new File(dir)).getAbsolutePath() + "../../..")
                        .getCanonicalFile();
                if (gparent.getName().contains("testcases")) {
                    // Special case: if we found this directory, dump its contents too.
                    listFiles(out, gparent, "");
                }
            }

            var gparent = new File("../..").getCanonicalFile();
            out.println("# ../..=" + gparent);
            listFiles(out, gparent, "");
        } catch (Throwable th) {
            out.println("Error: " + th.toString());
            th.printStackTrace(out);
        }
    }

    private static void listFiles(PrintStream out, File dir, String prefix) {
        if (!dir.isDirectory()) {
            out.println(prefix + dir.getAbsolutePath() + " is not a directory!");
            return;
        }
        out.println(prefix + ":" + dir.getAbsolutePath() + "/");
        // First, list the files.
        for (var file : Arrays.stream(dir.listFiles()).sorted().toList()) {
            out.println(prefix + "  " + file.getName() + "" + (file.isDirectory() ? "/" : ""));
        }

        // Then recurse.
        if (dir.getAbsolutePath().startsWith("/usr") || dir.getAbsolutePath().startsWith("/lib")) {
            // There would be too many files, so don't recurse.
            return;
        }
        for (var file : Arrays.stream(dir.listFiles()).sorted().toList()) {
            if (file.isDirectory()) {
                listFiles(out, file, prefix + "  ");
            }
        }
    }

    /**
     * @return the full directory path that contains the "ravenwood-runtime" files.
     * <p>
     * This method throws if called on the device side.
     */
    public static String getRavenwoodRuntimePath() {
        return RavenwoodHelper.getRavenwoodRuntimePath();
    }

    /**
     * Close an {@link AutoCloseable}.
     */
    public static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Close a {@link FileDescriptor}.
     */
    public static void closeQuietly(FileDescriptor fd) {
        var is = new FileInputStream(fd);
        closeQuietly(is);
    }

    public static void ensureIsPublicVoidMethod(Method method, boolean isStatic) {
        var ok = Modifier.isPublic(method.getModifiers())
                && (Modifier.isStatic(method.getModifiers()) == isStatic)
                && (method.getReturnType() == void.class);
        if (ok) {
            return; // okay
        }
        throw new AssertionError(String.format(
                "Method %s.%s() expected to be public %svoid",
                method.getDeclaringClass().getName(), method.getName(),
                (isStatic ? "static " : "")));
    }

    public static void ensureIsPublicMember(Member member, boolean isStatic) {
        var ok = Modifier.isPublic(member.getModifiers())
                && (Modifier.isStatic(member.getModifiers()) == isStatic);
        if (ok) {
            return; // okay
        }
        throw new AssertionError(String.format(
                "%s.%s expected to be public %s",
                member.getDeclaringClass().getName(), member.getName(),
                (isStatic ? "static" : "")));
    }

    /**
     * Run a supplier and swallow the exception, if any.
     * <p>
     * It's a dangerous function. Only use it in an exception handler where we don't want to crash.
     */
    @Nullable
    public static <T> T runIgnoringException(@NonNull Supplier<T> s) {
        try {
            return s.get();
        } catch (Throwable th) {
            log(TAG, "Warning: Exception detected! " + getStackTraceString(th));
        }
        return null;
    }

    /**
     * Run a runnable and swallow the exception, if any.
     * <p>
     * It's a dangerous function. Only use it in an exception handler where we don't want to crash.
     */
    public static void runIgnoringException(@NonNull Runnable r) {
        runIgnoringException(() -> {
            r.run();
            return null;
        });
    }

    @NonNull
    public static String getStackTraceString(@NonNull Throwable th) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        th.printStackTrace(writer);
        return stringWriter.toString();
    }

    /**
     * Same as {@link Integer#parseInt(String)} but accepts null and returns null.
     */
    @Nullable
    public static Integer parseNullableInt(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value);
    }

    /**
     * @return {@code value} if it's non-null. Otherwise, returns {@code def}.
     */
    @Nullable
    public static <T> T withDefault(@Nullable T value, @Nullable T def) {
        return value != null ? value : def;
    }


    private static final Pattern sWildcardValidater =
            Pattern.compile("[^\\w.*$]");

    /**
     * Convert a wildcard string using "*" and "**" to match Java classnames.
     * <p>
     * The input string can only contain alnum, "$", "." and "*".
     */
    public static Pattern parseClassNameWildcard(String pattern) {
        // Convert "**" -> match anything (== .*)
        // Convert "*" -> match anything except for periods (== [^.]*)
        // Convert "." -> \.

        if (sWildcardValidater.matcher(pattern).find()) {
            throw new IllegalArgumentException(
                    "Invalid character found in wildcard '" + pattern + "'");
        }

        // Save "**" to something else and convert it back to ".*" at the end.
        String temp = pattern.replace("**", "@@");
        temp = temp.replace(".", "\\.");
        temp = temp.replace("$", "\\$");
        temp = temp.replace("*", "[^.]*");
        temp = temp.replace("@@", ".*");
        return Pattern.compile(temp);
    }

    /**
     * @return true if it's a junit4 test method.
     */
    public static Boolean isTestMethod(@NonNull Class<?> clazz, @NonNull Method method) {
        return method.getAnnotation(org.junit.Test.class) != null;
    }

    private static final Pattern sParamsPattern = Pattern.compile("\\[.*$");

    /**
     * Take a {@link Description#getMethodName()} result and extract the "real" method name,
     * i.e. without the parameters.
     */
    public static String getMethodNameFromMethodDescription(@NonNull String methodDescriptionName) {
        return sParamsPattern.matcher(methodDescriptionName).replaceFirst("");
    }

    /**
     * @return a canonical name of a test method. {@code clazz} may be a subclass of
     * {@link Method#getDeclaringClass()}.
     */
    public static String toCanonicalTestName(@NonNull Class<?> clazz, @NonNull Method method) {
        return clazz.getName() + "#" + method.getName();
    }

    /**
     * @return a canonical name of a test method.
     */
    public static String toCanonicalTestName(@NonNull Description description) {
        if (!description.isTest()) {
            // It's a test class.
            return description.getClassName();
        } else {
            // It's a test method
            return description.getClassName() + "#" + getMethodNameFromMethodDescription(
                    description.getMethodName());
        }
    }
}