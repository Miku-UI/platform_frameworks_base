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

import static com.android.server.am.psc.Constants.SchedGroup;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;

import org.mockito.verification.VerificationMode;

/**
 * Utility class for testing Process State Controller (PSC) components.
 *
 * <p>This class resides in the {@code com.android.server.am.psc} package to allow access to
 * package-private members for testing purposes.
 */
public final class MockUtils {
    private MockUtils() {
        // Do not instantiate.
    }

    /**
     * Pass through the package-private setter methods in {@link ServiceRecordInternal} to invoke
     * the real implementation on a mock object.
     */
    public static void passThroughServiceRecordInternal(ServiceRecordInternal record) {
        doCallRealMethod().when(record).setStartRequested(anyBoolean());
        doCallRealMethod().when(record).setIsForeground(anyBoolean());
        doCallRealMethod().when(record).setLastActivity(anyLong());
        doCallRealMethod().when(record).setForegroundServiceType(anyInt());
        doCallRealMethod().when(record).setLastTopAlmostPerceptibleBindRequestUptimeMs(anyLong());
        doCallRealMethod().when(record).setHostProcess(nullable(ProcessRecordInternal.class));
    }

    /** Sets the current OOM adjustment for the given process record. */
    public static void setCurAdj(ProcessRecordInternal proc, int adj) {
        proc.setCurAdj(adj);
    }

    /** Verifies the current OOM adjustment was set for the given process record. */
    public static void verifySetCurAdj(ProcessRecordInternal proc, VerificationMode mode,
            int adj) {
        verify(proc, mode).setCurAdj(adj);
    }

    /** Sets the current raw OOM adjustment for the given process record. */
    public static void setCurRawAdj(ProcessRecordInternal proc, int adj) {
        proc.setCurRawAdj(adj);
    }

    /** Verifies the current raw OOM adjustment was set for the given process record. */
    public static void verifySetCurRawAdj(ProcessRecordInternal proc, VerificationMode mode,
            int adj) {
        verify(proc, mode).setCurRawAdj(adj);
    }

    /** Sets the last set OOM adjustment for the given process record. */
    public static void setSetAdj(ProcessRecordInternal proc, int adj) {
        proc.setSetAdj(adj);
    }

    /** Verifies the last set OOM adjustment was set for the given process record. */
    public static void verifySetSetAdj(ProcessRecordInternal proc, VerificationMode mode,
            int adj) {
        verify(proc, mode).setSetAdj(adj);
    }

    /** Sets the last set raw OOM adjustment for the given process record. */
    public static void setSetRawAdj(ProcessRecordInternal proc, int adj) {
        proc.setSetRawAdj(adj);
    }

    /** Verifies the last set raw OOM adjustment was set for the given process record. */
    public static void verifySetSetRawAdj(ProcessRecordInternal proc, VerificationMode mode,
            int adj) {
        verify(proc, mode).setSetRawAdj(adj);
    }

    /** Sets the current process state for the given process record. */
    public static void setCurProcState(ProcessRecordInternal proc, int state) {
        proc.setCurProcState(state);
    }

    /** Verifies the current process state was set for the given process record. */
    public static void verifySetCurProcState(ProcessRecordInternal proc, VerificationMode mode,
            int state) {
        verify(proc, mode).setCurProcState(state);
    }

    /** Sets the current raw process state for the given process record. */
    public static void setCurRawProcState(ProcessRecordInternal proc, int state) {
        proc.setCurRawProcState(state);
    }

    /** Verifies the current raw process state was set for the given process record. */
    public static void verifySetCurRawProcState(ProcessRecordInternal proc, VerificationMode mode,
            int state) {
        verify(proc, mode).setCurRawProcState(state);
    }

    /** Sets the last reported process state for the given process record. */
    public static void setReportedProcState(ProcessRecordInternal proc, int state) {
        proc.setReportedProcState(state);
    }

    /** Verifies the last reported process state was set for the given process record. */
    public static void verifySetReportedProcState(ProcessRecordInternal proc, VerificationMode mode,
            int state) {
        verify(proc, mode).setReportedProcState(state);
    }

    /** Sets the last set process state for the given process record. */
    public static void setSetProcState(ProcessRecordInternal proc, int state) {
        proc.setSetProcState(state);
    }

    /** Verifies the last set process state was set for the given process record. */
    public static void verifySetSetProcState(ProcessRecordInternal proc, VerificationMode mode,
            int state) {
        verify(proc, mode).setSetProcState(state);
    }

    /** Sets the current capability for the given process record. */
    public static void setCurCapability(ProcessRecordInternal proc, int curCapability) {
        proc.setCurCapability(curCapability);
    }

    /** Verifies the current capability was set for the given process record. */
    public static void verifySetCurCapability(ProcessRecordInternal proc, VerificationMode mode,
            int curCapability) {
        verify(proc, mode).setCurCapability(curCapability);
    }

    /** Sets the last set capability for the given process record. */
    public static void setSetCapability(ProcessRecordInternal proc, int setCapability) {
        proc.setSetCapability(setCapability);
    }

    /** Verifies the last set capability was set for the given process record. */
    public static void verifySetSetCapability(ProcessRecordInternal proc, VerificationMode mode,
            int setCapability) {
        verify(proc, mode).setSetCapability(setCapability);
    }

    /** Adds the given CPU time reasons to the current set of reasons for the process record. */
    public static void addCurCpuTimeReasons(ProcessRecordInternal proc,
            @OomAdjuster.CpuTimeReasons int cpuTimeReasons) {
        proc.addCurCpuTimeReasons(cpuTimeReasons);
    }

    /** Verifies the given CPU time reasons were added for the given process record. */
    public static void verifyAddCurCpuTimeReasons(ProcessRecordInternal proc, VerificationMode mode,
            @OomAdjuster.CpuTimeReasons int cpuTimeReasons) {
        verify(proc, mode).addCurCpuTimeReasons(cpuTimeReasons);
    }

    /** Adds the given implicit CPU time reasons for the process record. */
    public static void addCurImplicitCpuTimeReasons(ProcessRecordInternal proc,
            @OomAdjuster.ImplicitCpuTimeReasons int implicitCpuTimeReasons) {
        proc.addCurImplicitCpuTimeReasons(implicitCpuTimeReasons);
    }

    /** Verifies the given implicit CPU time reasons were added for the given process record. */
    public static void verifyAddCurImplicitCpuTimeReasons(ProcessRecordInternal proc,
            VerificationMode mode, @OomAdjuster.ImplicitCpuTimeReasons int implicitCpuTimeReasons) {
        verify(proc, mode).addCurImplicitCpuTimeReasons(implicitCpuTimeReasons);
    }

    /** Sets the last set CPU time reasons for the given process record. */
    public static void setSetCpuTimeReasons(ProcessRecordInternal proc,
            @OomAdjuster.CpuTimeReasons int reasons) {
        proc.setSetCpuTimeReasons(reasons);
    }

    /** Verifies the last set CPU time reasons were set for the given process record. */
    public static void verifySetSetCpuTimeReasons(ProcessRecordInternal proc, VerificationMode mode,
            @OomAdjuster.CpuTimeReasons int reasons) {
        verify(proc, mode).setSetCpuTimeReasons(reasons);
    }

    /** Sets the last set implicit CPU time reasons for the given process record. */
    public static void setSetImplicitCpuTimeReasons(ProcessRecordInternal proc,
            @OomAdjuster.ImplicitCpuTimeReasons int reasons) {
        proc.setSetImplicitCpuTimeReasons(reasons);
    }

    /** Verifies the last set implicit CPU time reasons were set for the given process record. */
    public static void verifySetSetImplicitCpuTimeReasons(ProcessRecordInternal proc,
            VerificationMode mode, @OomAdjuster.ImplicitCpuTimeReasons int reasons) {
        verify(proc, mode).setSetImplicitCpuTimeReasons(reasons);
    }

    /** Sets the last reported foreground activities state for the given process record. */
    public static void setRepForegroundActivities(ProcessRecordInternal proc, boolean rep) {
        proc.setRepForegroundActivities(rep);
    }

    /**
     * Verifies the last reported foreground activities state was set for the given process record.
     */
    public static void verifySetRepForegroundActivities(ProcessRecordInternal proc,
            VerificationMode mode, boolean rep) {
        verify(proc, mode).setRepForegroundActivities(rep);
    }

    /** Sets the last time a process was in the TOP state. */
    public static void setLastTopTime(ProcessRecordInternal proc, long lastTopTime) {
        proc.setLastTopTime(lastTopTime);
    }

    /** Verifies the last time a process was in the TOP state was set. */
    public static void verifySetLastTopTime(ProcessRecordInternal proc, VerificationMode mode,
            long lastTopTime) {
        verify(proc, mode).setLastTopTime(lastTopTime);
    }

    /** Sets the service B state for the given process record. */
    public static void setServiceB(ProcessRecordInternal proc, boolean serviceB) {
        proc.setServiceB(serviceB);
    }

    /** Verifies the service B state was set for the given process record. */
    public static void verifySetServiceB(ProcessRecordInternal proc, VerificationMode mode,
            boolean serviceB) {
        verify(proc, mode).setServiceB(serviceB);
    }

    /** Sets whether the given process has shown UI. */
    public static void setHasShownUi(ProcessRecordInternal proc, boolean hasShownUi) {
        proc.setHasShownUi(hasShownUi);
    }

    /** Verifies whether the given process has shown UI was set. */
    public static void verifySetHasShownUi(ProcessRecordInternal proc, VerificationMode mode,
            boolean hasShownUi) {
        verify(proc, mode).setHasShownUi(hasShownUi);
    }

    /** Sets the system no UI state for the given process record. */
    public static void setSystemNoUi(ProcessRecordInternal proc, boolean systemNoUi) {
        proc.setSystemNoUi(systemNoUi);
    }

    /** Verifies the system no UI state was set for the given process record. */
    public static void verifySetSystemNoUi(ProcessRecordInternal proc, VerificationMode mode,
            boolean systemNoUi) {
        verify(proc, mode).setSystemNoUi(systemNoUi);
    }

    /** Sets the OOM adjustment type for the given process record. */
    public static void setAdjType(ProcessRecordInternal proc, String adjType) {
        proc.setAdjType(adjType);
    }

    /** Verifies the OOM adjustment type was set for the given process record. */
    public static void verifySetAdjType(ProcessRecordInternal proc, VerificationMode mode,
            String adjType) {
        verify(proc, mode).setAdjType(adjType);
    }

    /** Sets the current scheduling group for the given process record. */
    public static void setCurrentSchedulingGroup(ProcessRecordInternal proc,
            @SchedGroup int curSchedGroup) {
        proc.setCurrentSchedulingGroup(curSchedGroup);
    }

    /** Verifies the current scheduling group was set for the given process record. */
    public static void verifySetCurrentSchedulingGroup(ProcessRecordInternal proc,
            VerificationMode mode, @SchedGroup int curSchedGroup) {
        verify(proc, mode).setCurrentSchedulingGroup(curSchedGroup);
    }

    /** Sets the last set scheduling group for the given process record. */
    public static void setSetSchedGroup(ProcessRecordInternal proc, @SchedGroup int setSchedGroup) {
        proc.setSetSchedGroup(setSchedGroup);
    }

    /** Verifies the last set scheduling group was set for the given process record. */
    public static void verifySetSetSchedGroup(ProcessRecordInternal proc, VerificationMode mode,
            @SchedGroup int setSchedGroup) {
        verify(proc, mode).setSetSchedGroup(setSchedGroup);
    }

    /** Sets if the given process has any foreground activities. */
    public static void setHasForegroundActivities(ProcessRecordInternal proc,
            boolean hasForegroundActivities) {
        proc.setHasForegroundActivities(hasForegroundActivities);
    }

    /** Verifies the hasForegroundActivities state was set for the given process record. */
    public static void verifySetHasForegroundActivities(ProcessRecordInternal proc,
            VerificationMode mode, boolean hasForegroundActivities) {
        verify(proc, mode).setHasForegroundActivities(hasForegroundActivities);
    }
}
