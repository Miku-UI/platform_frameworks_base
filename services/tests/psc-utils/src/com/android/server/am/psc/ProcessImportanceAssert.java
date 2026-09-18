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

package com.android.server.am.psc;

/**
 * ProcessImportanceAssert is a fluent style wrapper for {@link ProcessImportanceExpectations}.
 */
public class ProcessImportanceAssert {
    private final ProcessRecordInternal mProc;
    private ProcessImportanceAssert(ProcessRecordInternal proc) {
        mProc = proc;
    }

    /**
     * Returns a fluent-style object to chain asserts on.
     * @param proc the process the subsequent assertions will be made against.
     */
    public static ProcessImportanceAssert assertThat(ProcessRecordInternal proc) {
        return new ProcessImportanceAssert(proc);
    }

    /** Asserts if the process does not match the provided expectations. */
    public ProcessImportanceAssert matches(ProcessImportanceExpectations expectation) {
        expectation.validate(mProc);
        return this;
    }

    /**
     * Asserts if the process does not match the intersection of all provided expectations.
     * See {@link ProcessImportanceExpectations#intersect(ProcessImportanceExpectations...)} for
     * details on what intersecting expectations mean.
     */
    public ProcessImportanceAssert matchesIntersection(
            ProcessImportanceExpectations... expectation) {
        ProcessImportanceExpectations.intersect(expectation).validate(mProc);
        return this;
    }
}
