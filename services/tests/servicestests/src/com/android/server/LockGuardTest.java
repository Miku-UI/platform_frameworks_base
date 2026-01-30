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
package com.android.server;

import static com.android.server.LockGuard.INDEX_ACTIVITY;
import static com.android.server.LockGuard.INDEX_APP_OPS;
import static com.android.server.LockGuard.INDEX_DPMS;
import static com.android.server.LockGuard.INDEX_PACKAGES;
import static com.android.server.LockGuard.INDEX_POWER;
import static com.android.server.LockGuard.INDEX_PROC;
import static com.android.server.LockGuard.INDEX_STORAGE;
import static com.android.server.LockGuard.INDEX_USER;
import static com.android.server.LockGuard.INDEX_WINDOW;
import static com.android.server.LockGuard.installNewLock;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public final class LockGuardTest {

    @Rule public final Expect expect = Expect.create();

    @Test
    public void testInstallNewLock_toString() {
        expect.withMessage("installNewLock(INDEX_APP_OPS)")
                .that(installNewLock(INDEX_APP_OPS).toString())
                .isEqualTo("APP_OPS");
        expect.withMessage("installNewLock(INDEX_POWER)")
                .that(installNewLock(INDEX_POWER).toString())
                .isEqualTo("POWER");
        expect.withMessage("installNewLock(INDEX_USER)")
                .that(installNewLock(INDEX_USER).toString())
                .isEqualTo("USER");
        expect.withMessage("installNewLock(INDEX_PACKAGES)")
                .that(installNewLock(INDEX_PACKAGES).toString())
                .isEqualTo("PACKAGES");
        expect.withMessage("installNewLock(INDEX_STORAGE)")
                .that(installNewLock(INDEX_STORAGE).toString())
                .isEqualTo("STORAGE");
        expect.withMessage("installNewLock(INDEX_WINDOW)")
                .that(installNewLock(INDEX_WINDOW).toString())
                .isEqualTo("WINDOW");
        expect.withMessage("installNewLock(INDEX_PROC)")
                .that(installNewLock(INDEX_PROC).toString())
                .isEqualTo("PROCESS");
        expect.withMessage("installNewLock(INDEX_ACTIVITY)")
                .that(installNewLock(INDEX_ACTIVITY).toString())
                .isEqualTo("ACTIVITY");
        expect.withMessage("installNewLock(INDEX_DPMS)")
                .that(installNewLock(INDEX_DPMS).toString())
                .isEqualTo("DPMS");
    }
}
