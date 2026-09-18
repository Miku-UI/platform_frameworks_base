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

package com.google.errorprone.bugpatterns.android;

import com.google.errorprone.CompilationTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CannotBeSpecialUserCheckerTest {
    private CompilationTestHelper compilationHelper;

    @Before
    public void setUp() {
        compilationHelper = CompilationTestHelper.newInstance(
                CannotBeSpecialUserChecker.class, getClass());
    }

    @Test
    public void testInvalid() {
        compilationHelper
                .addSourceFile("/android/os/UserHandle.java")
                .addSourceLines("Test.java",
                        "import android.annotation.SpecialUsers.CannotBeSpecialUser;",
                        "import android.os.UserHandle;",
                        "public class Test {",
                        "  public void doSomething(@CannotBeSpecialUser UserHandle user) {}",
                        "  public void doSomethingInt(@CannotBeSpecialUser int userId) {}",
                        "  public void test() {",
                        "    // BUG: Diagnostic contains:",
                        "    doSomething(UserHandle.CURRENT);",
                        "    // BUG: Diagnostic contains:",
                        "    doSomethingInt(UserHandle.USER_CURRENT);",
                        "    // BUG: Diagnostic contains:",
                        "    doSomething(UserHandle.ALL);",
                        "    // BUG: Diagnostic contains:",
                        "    doSomethingInt(UserHandle.USER_ALL);",
                        "    // BUG: Diagnostic contains:",
                        "    doSomething(UserHandle.NULL);",
                        "    // BUG: Diagnostic contains:",
                        "    doSomethingInt(UserHandle.USER_NULL);",
                        "    // BUG: Diagnostic contains:",
                        "    doSomething(UserHandle.CURRENT_OR_SELF);",
                        "    // BUG: Diagnostic contains:",
                        "    doSomethingInt(UserHandle.USER_CURRENT_OR_SELF);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testValid() {
        compilationHelper
                .addSourceFile("/android/os/UserHandle.java")
                .addSourceLines("Test.java",
                        "import android.annotation.SpecialUsers.CannotBeSpecialUser;",
                        "import android.os.UserHandle;",
                        "public class Test {",
                        "  public void doSomething(@CannotBeSpecialUser UserHandle user) {}",
                        "  public void test() {",
                        "    doSomething(UserHandle.of(0));",
                        "    doSomething(UserHandle.of(10));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testNoAnnotation() {
        compilationHelper
                .addSourceFile("/android/os/UserHandle.java")
                .addSourceLines("Test.java",
                        "import android.annotation.SpecialUsers.CannotBeSpecialUser;",
                        "import android.os.UserHandle;",
                        "public class Test {",
                        "  public void doSomething(UserHandle user) {}",
                        "  public void doSomethingInt(int userId) {}",
                        "  public void test() {",
                        "    doSomething(UserHandle.CURRENT);",
                        "    doSomethingInt(UserHandle.USER_CURRENT);",
                        "    doSomething(UserHandle.ALL);",
                        "    doSomethingInt(UserHandle.USER_ALL);",
                        "    doSomething(UserHandle.NULL);",
                        "    doSomethingInt(UserHandle.USER_NULL);",
                        "    doSomething(UserHandle.CURRENT_OR_SELF);",
                        "    doSomethingInt(UserHandle.USER_CURRENT_OR_SELF);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testMixed() {
        compilationHelper
                .addSourceFile("/android/os/UserHandle.java")
                .addSourceLines("Test.java",
                        "import android.annotation.SpecialUsers.CannotBeSpecialUser;",
                        "import android.os.UserHandle;",
                        "public class Test {",
                        "  public void doSomethingMixed(UserHandle user1,",
                        "      @CannotBeSpecialUser UserHandle user2) {}",
                        "  public void test() {",
                        "    doSomethingMixed(UserHandle.CURRENT, UserHandle.of(0));",
                        "    // BUG: Diagnostic contains:",
                        "    doSomethingMixed(UserHandle.of(0), UserHandle.CURRENT);",
                        "  }",
                        "}")
                .doTest();
    }
}
