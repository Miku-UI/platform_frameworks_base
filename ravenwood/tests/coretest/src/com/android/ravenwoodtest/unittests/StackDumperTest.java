/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.platform.test.ravenwood.RavenwoodImplUtils.findCommonAncestor;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.content.Intent;

import org.junit.Test;

import java.lang.StackWalker.StackFrame;
import java.util.Arrays;
import java.util.List;

public class StackDumperTest {
    private static StackFrame sf(Class<?> clazz, String methodName) {
        return new StackFrame() {
            @Override
            public String getClassName() {
                return clazz.getName();
            }

            @Override
            public String getMethodName() {
                return methodName;
            }

            @Override
            public String getDescriptor() {
                return "";
            }

            @Override
            public Class<?> getDeclaringClass() {
                return null;
            }

            @Override
            public int getByteCodeIndex() {
                return 0;
            }

            @Override
            public String getFileName() {
                return "";
            }

            @Override
            public int getLineNumber() {
                return 0;
            }

            @Override
            public boolean isNativeMethod() {
                return false;
            }

            @Override
            public StackTraceElement toStackTraceElement() {
                return null;
            }
        };
    }

    private static List<StackFrame> sfs(StackFrame... frames) {
        return Arrays.asList(frames);
    }

    @Test
    public void testFindCommonAncestor_same_1() {
        var list1 = sfs(
                sf(Intent.class, "method1")
        );
        var list2 = sfs(
                sf(Intent.class, "method1")
        );
        assertThat(findCommonAncestor(list1, list2)).isEqualTo(0);
    }

    @Test
    public void testFindCommonAncestor_same_2() {
        var list1 = sfs(
                sf(Intent.class, "method1"),
                sf(Intent.class, "method2")
        );
        var list2 = sfs(
                sf(Intent.class, "method1"),
                sf(Intent.class, "method2")
        );
        assertThat(findCommonAncestor(list1, list2)).isEqualTo(0);
    }

    @Test
    public void testFindCommonAncestor_cur_longer() {
        var list1 = sfs(
                sf(ActivityManager.class, "method1"),
                sf(Intent.class, "method1"),
                sf(Intent.class, "method2")
        );
        var list2 = sfs(
                sf(Intent.class, "method1"),
                sf(Intent.class, "method2")
        );
        assertThat(findCommonAncestor(list1, list2)).isEqualTo(1);
    }

    @Test
    public void testFindCommonAncestor_last_longer() {
        var list1 = sfs(
                sf(Intent.class, "method1"),
                sf(Intent.class, "method2")
        );
        var list2 = sfs(
                sf(ActivityManager.class, "method1"),
                sf(Intent.class, "method1"),
                sf(Intent.class, "method2")
        );
        assertThat(findCommonAncestor(list1, list2)).isEqualTo(0);
    }

    @Test
    public void testFindCommonAncestor_cur_longer_different() {
        var list1 = sfs(
                sf(ActivityManager.class, "method1"),
                sf(Intent.class, "method1_different"),
                sf(Intent.class, "method2")
        );
        var list2 = sfs(
                sf(Intent.class, "method1"),
                sf(Intent.class, "method2")
        );
        assertThat(findCommonAncestor(list1, list2)).isEqualTo(2);
    }

    @Test
    public void testFindCommonAncestor_last_longer_different() {
        var list1 = sfs(
                sf(Intent.class, "method1_different"),
                sf(Intent.class, "method2")
        );
        var list2 = sfs(
                sf(ActivityManager.class, "method1"),
                sf(Intent.class, "method1"),
                sf(Intent.class, "method2")
        );
        assertThat(findCommonAncestor(list1, list2)).isEqualTo(1);
    }
}
