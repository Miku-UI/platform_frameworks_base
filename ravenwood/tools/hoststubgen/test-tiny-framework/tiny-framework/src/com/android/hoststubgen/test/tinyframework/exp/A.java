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
package com.android.hoststubgen.test.tinyframework.exp;

@SuppressWarnings("unused")
public class A {
    static final Object sStatic = new Object();
    static final String sString1;
    static final String sString2;
    static final String sString3;

    static {
        // Use some local variables and stacks to make sure the experimental api hook call
        // works properly with it.
        var x = 1;
        sString1 = System.getProperty("test.prop.1");
        sString2 = System.getProperty("test.prop.2");

        // Use some local values
        var temp1 = sString1 + sString2;
        var temp2 = sString1 + sString2 + x;
        if ("".equals(temp1)) {
            sString3 = "case1";
        } else {
            sString3 = "case2";
        }
    }

    public void foo() {
    }
}
