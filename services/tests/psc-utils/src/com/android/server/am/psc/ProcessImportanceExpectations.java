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

import static org.junit.Assert.fail;

import android.annotation.Nullable;
import android.app.ActivityManager;

import java.util.StringJoiner;

/**
 * ProcessImportanceExpectations is a utility for validating {@link ProcessRecordInternal}
 * importance.
 */
public class ProcessImportanceExpectations {
    final Integer mExpectedProcState;
    final Integer mExpectedOomAdjScore;
    final Boolean mExpectedFreezability;

    private ProcessImportanceExpectations(Builder builder) {
        mExpectedProcState = builder.mProcState;
        mExpectedOomAdjScore = builder.mOomAdjScore;
        mExpectedFreezability = builder.mFreezability;
    }

    /**
     * Creates a {@link ProcessImportanceExpectations} that is the intersection of the provided
     * expectations.
     * In other words, each expectation of the returned {@link ProcessImportanceExpectations}
     * will be the most constrained of all the provided expectations.
     */
    public static ProcessImportanceExpectations intersect(
            ProcessImportanceExpectations... expectations) {
        Integer procState = null;
        Integer oomAdjScore = null;
        Boolean freezability = null;
        for (ProcessImportanceExpectations e : expectations) {
            procState = combine(procState, e.mExpectedProcState, Math::min);
            oomAdjScore = combine(oomAdjScore, e.mExpectedOomAdjScore, Math::min);
            freezability = combine(freezability, e.mExpectedFreezability, Boolean::logicalAnd);
        }
        return new Builder()
                .setProcState(procState)
                .setOomAdjScore(oomAdjScore)
                .setFreezability(freezability)
                .build();
    }

    private static <T> T combine(T a, T b, java.util.function.BinaryOperator<T> op) {
        if (a == null) return b;
        if (b == null) return a;
        return op.apply(a, b);
    }

    /**
     * Returns true, if the provided {@link ProcessRecordInternal} matches expectations.
     */
    public void validate(ProcessRecordInternal proc) {
        final int actualProcState = proc.getSetProcState();
        final int actualOomAdjScore = proc.getSetAdj();
        final boolean actualFreezability = OomAdjuster.getFreezePolicy(proc);

        if (checkState(actualProcState, actualOomAdjScore, actualFreezability)) {
            // Passed
            return;
        }

        // Fail with a dump of the state.
        fail(generateErrorMessage(proc, actualProcState, actualOomAdjScore, actualFreezability));
    }

    private boolean checkState(int actualProcState, int actualOomAdjScore,
            boolean actualFreezability) {
        if (mExpectedProcState != null && actualProcState != mExpectedProcState) {
            return false;
        }
        if (mExpectedOomAdjScore != null && actualOomAdjScore != mExpectedOomAdjScore) {
            return false;
        }
        if (mExpectedFreezability != null && actualFreezability != mExpectedFreezability) {
            return false;
        }
        return true;
    }

    private String generateErrorMessage(ProcessRecordInternal proc, int actualProcState,
            int actualOomAdjScore, boolean actualFreezability) {
        StringBuilder str = new StringBuilder();
        str.append("Process State Validation failed for ");
        str.append(proc);
        str.append("\n");

        str.append("actual: ");
        str.append(toString(actualProcState, actualOomAdjScore, actualFreezability));
        str.append("\n");
        str.append("expected: ");
        str.append(this.toString());
        str.append("\n");

        return str.toString();
    }

    private String toString(Integer procState, Integer oomAdjScore, Boolean freezability) {
        StringJoiner sj = new StringJoiner(", ", "{", "}");
        // If all states are null, denote the lack of state with "{<none>}".
        sj.setEmptyValue("{<none>}");

        if (procState != null) {
            sj.add("ProcState:" + procState);
        }
        if (oomAdjScore != null) {
            sj.add("OomAdjScore:" + oomAdjScore);
        }
        if (freezability != null) {
            sj.add("Freezability:" + freezability);
        }

        return sj.toString();
    }

    @Override
    public String toString() {
        return ProcessImportanceExpectations.class.getSimpleName() + toString(mExpectedProcState,
                mExpectedOomAdjScore, mExpectedFreezability);
    }

    /** Builder for {@link ProcessImportanceExpectations} */
    public static class Builder {
        Integer mProcState = null;
        Integer mOomAdjScore = null;
        Boolean mFreezability = null;

        /**
         * Returns the built {link ProcessImportanceExpectations} object.
         */
        public ProcessImportanceExpectations build() {
            return new ProcessImportanceExpectations(this);
        }

        /**
         * Set the expected procState score for the validated process. {@code null} means no
         * expectations.
         */
        public Builder setProcState(@Nullable @ActivityManager.ProcessState Integer procState) {
            mProcState = procState;
            return this;
        }

        /**
         * Set the expected oomAdj score for the validated process. {@code null} means no
         * expectations.
         */
        public Builder setOomAdjScore(@Nullable Integer oomAdjScore) {
            mOomAdjScore = oomAdjScore;
            return this;
        }

        /**
         * Set the expected freezability score for the validated process. {@code null} means no
         * expectations.
         */
        public Builder setFreezability(@Nullable Boolean freezability) {
            mFreezability = freezability;
            return this;
        }
    }
}
