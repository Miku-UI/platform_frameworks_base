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
package com.android.ravenwoodtest.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.platform.test.ravenwood.RavenwoodMethodCallLogger;
import android.util.Log;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;

/**
 * Unit tests for {@link RavenwoodMethodCallLogger}.
 *
 * Ideally we want to put it in the same package as {@link RavenwoodMethodCallLogger}
 * and use package-private for @VisibleForTesting methods, but that package would be exempted
 * from Ravenizer, which would be problematic, so we can't use that package.
 *
 * NOTE: the "EXPECTED" strings depend on various implementation details, so we may need to adjust
 * them as we update the implementation.
 */
public class RavenwoodMethodCallLoggerTest {
    private final RavenwoodMethodCallLoggerForTest mTarget = new RavenwoodMethodCallLoggerForTest();

    private static class RavenwoodMethodCallLoggerForTest extends RavenwoodMethodCallLogger {
        RavenwoodMethodCallLoggerForTest() {
            super(LogMode.Default);
        }

        // We always use a fixed TID.
        @Override
        public int getRawThreadId() {
            return 123;
        }

        /** This allows overriding the nest level. */
        public Integer mNestLevel = null;
    }

    private void assertLogged(String className) {
        assertTrue(className + " should be logged", mTarget.shouldLog(className));
    }

    private void assertNotLogged(String className) {
        assertFalse(className + " should not be logged", mTarget.shouldLog(className));
    }

    @Test
    public void testShouldLog() {
        assertNotLogged("android.util.Log");
        assertNotLogged("android.util.ArrayMap");
        assertNotLogged("android.util.SparseArray");
        assertNotLogged("android.app.EventLogTags");
        assertNotLogged("android.os.Build");
        assertNotLogged("android.os.Build$VERSION"); // Inner class follows the outer class policy.

        assertLogged("android.app.ActivityThread");
        assertLogged("android.content.Context");
        assertLogged("android.util.DebugUtils");
        assertLogged("android.content.pm.PackageManager");
        assertLogged("android.content.pm.PackageManager$NameNotFoundException");
    }

    /**
     * Complete "end-to-end" test that exercises more of the target code.
     *
     * Note, method call log uses the real stacktrace to figure out the nest level.
     * So all the calls look "flat".
     */
    @Test
    public void testEndToEnd() throws Exception {
        // Create PrintStream to store the log output.
        var bos = new ByteArrayOutputStream();

        mTarget.enable(new PrintStream(bos));

        // Here, we should only use public APIs that won't suddenly be removed
        mTarget.onMethodCalled(
                Context.class,
                "getPackageName",
                "()Ljava/lang/String;");
        mTarget.onMethodCalled(
                Log.class,
                "d",
                "(Ljava/lang/String;Ljava/lang/String;)V");
        mTarget.onMethodCalled(
                Context.class,
                "getOpPackgeName",
                "()Ljava/lang/String;");

        // =================================================================
        // Check the log output.
        // Note, for implementation detail reasons, the nest levels show up as negative,
        // because of how we initialize the initial nest level.
        String expected = """
# [123: Ravenwood:Test]: [@-4] android.content.Context.getPackageName()Ljava/lang/String;
# [123: Ravenwood:Test]: [@-4] android.content.Context.getOpPackgeName()Ljava/lang/String;
                """;
        assertThat(bos.toString().trim()).isEqualTo(expected.trim());

        // =================================================================
        // Next, generate a policy file, and check the output again.
        File temp = File.createTempFile("policy-file", ".txt");
        mTarget.dumpAllCalledMethodsForFileInner(temp.getAbsolutePath(), null);

        var policy = Files.readString(temp.toPath());
        expected = """
class android.content.Context	keep
    method getOpPackgeName ()Ljava/lang/String;	keep
    method getPackageName ()Ljava/lang/String;	keep	# annotation(ThrowButSupported)

class android.util.Log	keep
    method d (Ljava/lang/String;Ljava/lang/String;)V	keep	# class-wide in android/util/Log [inner-reason: class-annotation]
                """;
        assertThat(policy.trim()).isEqualTo(expected.trim());

        // =================================================================
        // Next, we generate the policy file with a filter.
        mTarget.dumpAllCalledMethodsForFileInner(temp.getAbsolutePath(),
                "(ThrowButSupported|class-wide)");

        policy = Files.readString(temp.toPath());
        expected = """
class android.content.Context	keep
    method getPackageName ()Ljava/lang/String;	keep	# annotation(ThrowButSupported)

class android.util.Log	keep
    method d (Ljava/lang/String;Ljava/lang/String;)V	keep	# class-wide in android/util/Log [inner-reason: class-annotation]
                """;
        assertThat(policy.trim()).isEqualTo(expected.trim());
    }
}
