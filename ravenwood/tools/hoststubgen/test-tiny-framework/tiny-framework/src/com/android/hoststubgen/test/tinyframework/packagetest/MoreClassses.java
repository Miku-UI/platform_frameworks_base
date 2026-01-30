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
package com.android.hoststubgen.test.tinyframework.packagetest;

import android.hosttest.annotation.HostSideTestIgnore;
import android.hosttest.annotation.HostSideTestKeep;
import android.hosttest.annotation.HostSideTestRedirect;
import android.hosttest.annotation.HostSideTestRedirectionClass;
import android.hosttest.annotation.HostSideTestRemove;
import android.hosttest.annotation.HostSideTestStaticInitializerKeep;
import android.hosttest.annotation.HostSideTestThrow;
import android.hosttest.annotation.HostSideTestWholeClassKeep;

class KeepC1_NoannotationNoClassPolicy {
    static final Object sStatic = new Object();

    public void foo() {
    }
}

class KeepC2_WithClassPolicy_keep {
    static final Object sStatic = new Object();

    public void foo() {
    }
}

class KeepC3_WithClassPolicy_keepclass {
    static final Object sStatic = new Object();

    public void foo() {
    }
}

@HostSideTestKeep
class KeepC4_WithAnnotation_keep {
    static final Object sStatic = new Object();

    public void foo() {
    }
}

@HostSideTestWholeClassKeep
class KeepC5_WithAnnotation_keepclass {
    static final Object sStatic = new Object();

    public void foo() {
    }
}

@HostSideTestRedirectionClass("RedirectTo")
@HostSideTestStaticInitializerKeep
class KeepC6_MethodAnnotations_withClassExpPolicy {
    static {
    }

    @HostSideTestKeep
    public KeepC6_MethodAnnotations_withClassExpPolicy() {
    }

    @HostSideTestKeep
    public void m1_keep() {
    }

    @HostSideTestIgnore
    public void m2_ignore() {
    }

    @HostSideTestThrow
    public void m3_throw() {
    }

    @HostSideTestRemove
    public void m4_remove() {
    }

    @HostSideTestRedirect
    public static void m5_redirect() {
    }
}

@HostSideTestRedirectionClass("RedirectTo")
@HostSideTestStaticInitializerKeep
class KeepC7_MethodAnnotations_withMethodExpPolicy {
    static {
    }

    @HostSideTestKeep
    public KeepC7_MethodAnnotations_withMethodExpPolicy() {
    }

    @HostSideTestKeep
    public void m1_keep() {
    }

    @HostSideTestIgnore
    public void m2_ignore() {
    }

    @HostSideTestThrow
    public void m3_throw() {
    }

    @HostSideTestRemove
    public void m4_remove() {
    }

    @HostSideTestRedirect
    public static void m5_redirect() {
    }
}

@HostSideTestWholeClassKeep
class RedirectTo {
    public static void redirect() {
    }
}