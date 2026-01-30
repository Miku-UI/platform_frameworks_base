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

import android.hosttest.annotation.HostSideTestWholeClassKeep;

// This package also has "keep" in the policy file as a package policy.
// Because the package directive and the annotation have the same policy,
// we use the annotation as the "reason"
@HostSideTestWholeClassKeep
public class C {
    public void foo() {
    }
}
