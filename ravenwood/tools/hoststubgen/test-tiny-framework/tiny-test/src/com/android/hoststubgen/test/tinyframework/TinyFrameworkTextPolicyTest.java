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
package com.android.hoststubgen.test.tinyframework;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsKeep;
import com.android.hoststubgen.test.tinyframework.TinyFrameworkHooks.MethodInfo;

import org.junit.Test;

public class TinyFrameworkTextPolicyTest {

    private static <T> void doNothing(T arg) {}

    @Test
    public void testClassWideExperimental() {
        TinyFrameworkHooks.sExperimentalMethodCalls.clear();

        TinyFrameworkExperimental tfc = new TinyFrameworkExperimental();
        tfc.simple();
        tfc.keep();
        tfc.replace();
        tfc.unsupported();

        // Supported annotations should override experimental policy
        assertThat(TinyFrameworkHooks.sExperimentalMethodCalls).containsNoneOf(
                new MethodInfo(TinyFrameworkExperimental.class, "keep", "()V"),
                new MethodInfo(TinyFrameworkExperimental.class, "replace", "()V")
        );
        assertThat(TinyFrameworkHooks.sExperimentalMethodCalls).containsExactly(
                new MethodInfo(TinyFrameworkExperimental.class, "<init>", "()V"),
                new MethodInfo(TinyFrameworkExperimental.class, "simple", "()V"),
                new MethodInfo(TinyFrameworkExperimental.class, "unsupported", "()V")
        ).inOrder();
    }

    @Test
    public void testPackageKeepPolicy() {
        doNothing(com.android.hoststubgen.test.tinyframework.packagetest.A.class);
        assertThrows(NoClassDefFoundError.class, () ->
                doNothing(com.android.hoststubgen.test.tinyframework.packagetest.B.class));
        doNothing(com.android.hoststubgen.test.tinyframework.packagetest.C.class);

        doNothing(com.android.hoststubgen.test.tinyframework.packagetest.sub.A.class);
        assertThrows(NoClassDefFoundError.class, () ->
                doNothing(com.android.hoststubgen.test.tinyframework.packagetest.sub.B.class));
    }

    @Test
    public void testPackageKeepPolicyReason() {
        HostStubGenProcessedAsKeep keepAnnot;

        // This class is kept by text policy
        keepAnnot = com.android.hoststubgen.test.tinyframework.packagetest.A.class
                .getAnnotation(HostStubGenProcessedAsKeep.class);
        assertThat(keepAnnot).isNotNull();
        assertThat(keepAnnot.reason()).startsWith("file-override");

        // This class is kept by text policy
        keepAnnot = com.android.hoststubgen.test.tinyframework.packagetest.sub.A.class
                .getAnnotation(HostStubGenProcessedAsKeep.class);
        assertThat(keepAnnot).isNotNull();
        assertThat(keepAnnot.reason()).startsWith("file-override");

        // C is annotated with KeepClass, and the reason should not be overridden by text policy
        keepAnnot = com.android.hoststubgen.test.tinyframework.packagetest.C.class
                .getAnnotation(HostStubGenProcessedAsKeep.class);
        assertThat(keepAnnot).isNotNull();
        assertThat(keepAnnot.reason()).startsWith("class-annotation");
    }

    @Test
    public void testPackageExperimentalPolicy() {
        TinyFrameworkHooks.sExperimentalMethodCalls.clear();

        // These classes are kept as experimental by text policy
        new com.android.hoststubgen.test.tinyframework.exp.A().foo();
        new com.android.hoststubgen.test.tinyframework.exp.sub.A().foo();

        // B is annotated with KeepClass, and should override the experimental text policy
        new com.android.hoststubgen.test.tinyframework.exp.B().foo();

        assertThat(TinyFrameworkHooks.sExperimentalMethodCalls).doesNotContain(
                new MethodInfo(com.android.hoststubgen.test.tinyframework.exp.B.class, "foo", "()V")
        );
        assertThat(TinyFrameworkHooks.sExperimentalMethodCalls).containsExactly(
                new MethodInfo(com.android.hoststubgen.test.tinyframework.exp.A.class,
                        "<clinit>", "()V"),
                new MethodInfo(com.android.hoststubgen.test.tinyframework.exp.A.class,
                        "<init>", "()V"),
                new MethodInfo(com.android.hoststubgen.test.tinyframework.exp.A.class,
                        "foo", "()V"),
                new MethodInfo(com.android.hoststubgen.test.tinyframework.exp.sub.A.class,
                        "<init>", "()V"),
                new MethodInfo(com.android.hoststubgen.test.tinyframework.exp.sub.A.class,
                        "foo", "()V")
        ).inOrder();
    }
}
