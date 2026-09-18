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

package com.google.errorprone.bugpatterns.android.threading;

import com.google.errorprone.CompilationTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SystemServerLockCheckerTest {
    private CompilationTestHelper compilationHelper;

    @Before
    public void setUp() {
        compilationHelper = CompilationTestHelper.newInstance(
                SystemServerLockChecker.class, getClass());
    }

    @Test
    public void testWrongOrder() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "public class Test {",
                        "  public interface O {",
                        "    public int ORD_2 = 2;",
                        "    public int ORD_4 = 4;",
                        "  }",
                        "  @SystemServerLock(value = O.ORD_2) final Object mLock2 = new Object();",
                        "  @SystemServerLock(value = O.ORD_4) final Object mLock4 = new Object();",
                        "  public void wrongOrder() {",
                        "    // BUG: Diagnostic contains: 'Test.mLock2' (order=2) is acquired"
                                + " inside lock 'Test.mLock4' (order=4)",
                        "    synchronized (mLock4) {",
                        "      synchronized (mLock2) {}",
                        "    }",
                        "  }",
                        "  @SuppressWarnings(\"AndroidFrameworkSystemServerLock\")",
                        "  public void wrongOrderButSuppressed() {",
                        "    synchronized (mLock4) {",
                        "      synchronized (mLock2) {/* suppressed */}",
                        "    }",
                        "  }",
                        "  public void rightOrder() {",
                        "    synchronized (mLock2) {",
                        "      synchronized (mLock4) {",
                        "        synchronized (mLock2) {/* already held */}",
                        "      }",
                        "    }",
                        "  }",
                        "  public void sameOrder() {",
                        "    synchronized (mLock2) {",
                        "      synchronized (mLock2) {",
                        "      }",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testWrongOrderInNestedMethods() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "public class Test {",
                        "  @SystemServerLock(2) final Object mLock2 = new Object();",
                        "  @SystemServerLock(4) final Object mLock4 = new Object();",
                        "  public int outer() {",
                        "    // BUG: Diagnostic contains: 'Test.mLock2' (order=2) is acquired"
                                + " inside lock 'Test.mLock4' (order=4)",
                        "    synchronized (mLock4) {",
                        "      if (true) {",
                        "        return inner();",
                        "      } else {",
                        "        return 0;",
                        "      }",
                        "    }",
                        "  }",
                        "  public int inner() {",
                        "    synchronized (mLock2) {",
                        "      synchronized (mLock4) {",
                        "        return 6;",
                        "      }",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    /**
     * Test that certain "known" uses of lambdas, such as Optional.ifPresent, will be treated
     * as an immediate invocation (even if via method reference).
     *
     * <p>Normally, lambdas passed around as objects can't be tracked like this and just have to
     * be ignored. This scenario is also tested.
     */
    @Test
    public void testLambdaContextSensitivity() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "import java.util.Optional;",
                        "public class Test {",
                        "  @SystemServerLock(2) final Object mLock2 = new Object();",
                        "  @SystemServerLock(4) final Object mLock4 = new Object();",
                        "  @FunctionalInterface",
                        "  interface MyLambda {",
                        "      int get();",
                        "  }",
                        "  public MyLambda outer() {",
                        "    // BUG: Diagnostic contains: 'Test.mLock2' (order=2) is acquired"
                                + " inside lock 'Test.mLock4' (order=4)",
                        "    synchronized (mLock4) {",
                        "      Optional.ofNullable(mLock4).ifPresent(this::methodReferenceInCb);",
                        "    }",
                        "    // BUG: Diagnostic contains: 'Test.mLock2' (order=2) is acquired"
                                + " inside lock 'Test.mLock4' (order=4)",
                        "    synchronized (mLock4) {",
                        "      Optional.ofNullable(mLock4).ifPresent(o -> directCallInCb());",
                        "    }",
                        "    synchronized (mLock4) {",
                        "      return () -> lambdaInAmbiguousContext();",
                        "    }",
                        "  }",
                        "  public int lambdaInAmbiguousContext() {",
                        "    synchronized (mLock2) {",
                        "      synchronized (mLock4) {",
                        "        return 6;",
                        "      }",
                        "    }",
                        "  }",
                        "  public void methodReferenceInCb(Object o) {",
                        "    synchronized (mLock2) {/* via reference */}",
                        "  }",
                        "  public void directCallInCb() {",
                        "    synchronized (mLock2) {/* via direct call inside lambda */}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testOverriddenMethod() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "public class Test {",
                        "  @SystemServerLock(2) final Object mLock2 = new Object();",
                        "  public void run(Test t) {",
                        "    // BUG: Diagnostic contains: 'BadIdea.mLock' (order=0) is acquired"
                                + " inside lock 'Test.mLock2' (order=2)",
                        "    synchronized (mLock2) {",
                        "      if (t != this && t instanceof Test) {",
                        "        t.run(t);",
                        "      }",
                        "    }",
                        "  }",
                        "  public class BadIdea extends Test {",
                        "    @SystemServerLock(0) final Object mLock = new Object();",
                        "    @Override",
                        "    public void run(Test t) {",
                        "      synchronized (mLock) {}",
                        "    }",
                        "  }",
                        "  public class SuppressedBadIdea extends Test {",
                        "    @SystemServerLock(0) final Object mLock = new Object();",
                        "    @SuppressWarnings(\"AndroidFrameworkSystemServerLock\")",
                        "    @Override",
                        "    public void run(Test t) {",
                        "      synchronized (mLock) {/* suppressed */}",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    /**
     * Test for a special treatment on invocations of common functional
     * interfaces such as Runnable.run().
     *
     * <p>There are so many unrelated implementations of these interfaces
     * that there is no purpose served by assuming that any of them can
     * be swapped-in.
     */
    @Test
    public void testExceptionForStandardInterfaces() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "import java.util.concurrent.Callable;",
                        "import java.util.function.Supplier;",
                        "import java.util.function.Consumer;",
                        "public class Test {",
                        "  @SystemServerLock(2) final Object mLock2 = new Object();",
                        "  public void think(Runnable r, Callable<String> a,",
                        "      Supplier<String> s, Consumer<String> o) throws Exception {",
                        "    synchronized (mLock2) {",
                        "      r.run();",
                        "      a.call();",
                        "      o.accept(s.get());",
                        "    }",
                        "  }",
                        "  public class IrrelevantMultiTool implements Runnable, Callable<String>,",
                        "      Supplier<String>, Consumer<String> {",
                        "    @SystemServerLock(1) final Object mLock1 = new Object();",
                        "    @Override public void run() {synchronized (mLock1) {}}",
                        "    @Override public String call() throws Exception {",
                        "       synchronized (mLock1) {return \"\";}",
                        "    }",
                        "    @Override public String get() {synchronized (mLock1) {return \"\";}}",
                        "    @Override public void accept(String s) {synchronized (mLock1) {}}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testGuardedBy() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "import javax.annotation.concurrent.GuardedBy;",
                        "public class Test {",
                        "  @SystemServerLock(2) final Object mLock2 = new Object();",
                        "  @SystemServerLock(4) final Object mLock4 = new Object();",
                        "  @GuardedBy(\"mLock2\")",
                        "  public void rightOrder() {",
                        "    synchronized (mLock4) {",
                        "    }",
                        "  }",
                        "  @GuardedBy(\"mLock2\")",
                        "  public void earlierLockAcquiredTwice() {",
                        "    synchronized (mLock4) {",
                        "      synchronized (mLock2) {/* mLock2 was held from the outset */}",
                        "    }",
                        "  }",
                        "  @GuardedBy(\"mLock4\")",
                        "  // BUG: Diagnostic contains: 'Test.mLock2' (order=2) is acquired"
                                + " inside lock 'Test.mLock4' (order=4)",
                        "  public void wrongOrder() {",
                        "    synchronized (mLock2) {/* inside mLock4 */}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAndroidGuardedBy() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "import com.android.internal.annotations.GuardedBy;",
                        "public class Test {",
                        "  @SystemServerLock(2) final Object mLock2 = new Object();",
                        "  @SystemServerLock(4) final Object mLock4 = new Object();",
                        "  @GuardedBy({\"mLock2\"})",
                        "  public void rightOrder() {",
                        "    synchronized (mLock4) {",
                        "    }",
                        "  }",
                        "  @GuardedBy({\"mLock2\"})",
                        "  public void earlierLockAcquiredTwice() {",
                        "    synchronized (mLock4) {",
                        "      synchronized (mLock2) {/* mLock2 was held from the outset */}",
                        "    }",
                        "  }",
                        "  @GuardedBy({\"mLock4\"})",
                        "  // BUG: Diagnostic contains: 'Test.mLock2' (order=2) is acquired"
                                + " inside lock 'Test.mLock4' (order=4)",
                        "  public void wrongOrder() {",
                        "    synchronized (mLock2) {/* inside mLock4 */}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testNoAnnotation() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "public class Test {",
                        "  @SystemServerLock(2) final Object mLock2 = new Object();",
                        "  final Object mLock4 = new Object();",
                        "  public void wrongOrder() {",
                        "    synchronized (mLock4) {",
                        "      synchronized (mLock2) {}",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testForkBomb() {
        compilationHelper
                .addSourceLines("Test.java",
                        "import com.android.internal.annotations.SystemServerLock;",
                        "public class Test {",
                        "  @SystemServerLock(1) final Object mLock = new Object();",
                        "  public void a1() {synchronized (mLock) {a2();a2();}}",
                        "  public void a2() {synchronized (mLock) {a3();a3();}}",
                        "  public void a3() {synchronized (mLock) {a4();a4();}}",
                        "  public void a4() {synchronized (mLock) {a5();a5();}}",
                        "  public void a5() {synchronized (mLock) {a6();a6();}}",
                        "  public void a6() {synchronized (mLock) {a7();a7();}}",
                        "  public void a7() {synchronized (mLock) {a8();a8();}}",
                        "  public void a8() {synchronized (mLock) {a9();a9();}}",
                        "  public void a9() {synchronized (mLock) {a10();a10();}}",
                        "  public void a10() {synchronized (mLock) {a11();a11();}}",
                        "  public void a11() {synchronized (mLock) {a12();a12();}}",
                        "  public void a12() {synchronized (mLock) {return;}}",
                        "}")
                .doTest();
    }
}
