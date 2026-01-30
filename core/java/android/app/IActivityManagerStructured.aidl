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

package android.app;

import android.app.IProcessObserver;
import android.app.IUidObserver;
import android.app.RunningAppProcessInfo;

/**
 * Structured AIDL version of IActivityManager.aidl.
 *
 * At this moment this interface provides a minimum level of support for native processes.
 * TODO(b/419409018): Migrate the entire interface to the structured AIDL.
 *
 * @hide
 */
interface IActivityManagerStructured {
    ParcelFileDescriptor openContentUri(in String uriString);
    void registerUidObserver(in IUidObserver observer, int which, int cutpoint,
            String callingPackage);
    void unregisterUidObserver(in IUidObserver observer);

    /**
     * Registers a UidObserver with a uid filter.
     *
     * @param observer The UidObserver implementation to register.
     * @param which    A bitmask of events to observe. See ActivityManager.UID_OBSERVER_*.
     * @param cutpoint The cutpoint for onUidStateChanged events. When the state crosses this
     *                 threshold in either direction, onUidStateChanged will be called.
     * @param callingPackage The name of the calling package.
     * @param uids     A list of uids to watch. If all uids are to be watched, use
     *                 registerUidObserver instead.
     * @throws RemoteException
     * @return Returns A binder token identifying the UidObserver registration.
     */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)")
    IBinder registerUidObserverForUids(in IUidObserver observer, int which, int cutpoint,
            String callingPackage, in int[] uids);

    /**
     * Adds a uid to the list of uids that a UidObserver will receive updates about.
     *
     * @param observerToken  The binder token identifying the UidObserver registration.
     * @param callingPackage The name of the calling package.
     * @param uid            The uid to watch.
     * @throws RemoteException
     */
    void addUidToObserver(in IBinder observerToken, String callingPackage, int uid);

    /**
     * Removes a uid from the list of uids that a UidObserver will receive updates about.
     *
     * @param observerToken  The binder token identifying the UidObserver registration.
     * @param callingPackage The name of the calling package.
     * @param uid            The uid to stop watching.
     * @throws RemoteException
     */
    void removeUidFromObserver(in IBinder observerToken, String callingPackage, int uid);

    boolean isUidActive(int uid, String callingPackage);
    @JavaPassthrough(annotation=
            "@android.annotation.RequiresPermission(allOf = {android.Manifest.permission.PACKAGE_USAGE_STATS, android.Manifest.permission.INTERACT_ACROSS_USERS_FULL}, conditional = true)")
    int getUidProcessState(int uid, in String callingPackage);
    int checkPermission(in String permission, int pid, int uid);

    /** Logs start of an API call to associate with an FGS, used for FGS Type Metrics */
    oneway void logFgsApiBegin(int apiType, int appUid, int appPid);

    /** Logs stop of an API call to associate with an FGS, used for FGS Type Metrics */
    oneway void logFgsApiEnd(int apiType, int appUid, int appPid);

    /** Logs API state change to associate with an FGS, used for FGS Type Metrics */
    oneway void logFgsApiStateChanged(int apiType, int state, int appUid, int appPid);

    @UnsupportedAppUsage
    void registerProcessObserver(in IProcessObserver observer);
    @UnsupportedAppUsage
    void unregisterProcessObserver(in IProcessObserver observer);
    @UnsupportedAppUsage
    List<RunningAppProcessInfo> getRunningAppProcesses();

    /** Callback from a service to AMS to indicate it has completed a scheduled operation */
    @UnsupportedAppUsage
    oneway void serviceDoneExecuting(in IBinder token, int type, int startId, int res);
    /** Type for IActivityManager.serviceDoneExecuting: anonymous operation */
    const int SERVICE_DONE_EXECUTING_ANON = 0;
    /** Type for IActivityManager.serviceDoneExecuting: done with an onStart call */
    const int SERVICE_DONE_EXECUTING_START = 1;
    /** Type for IActivityManager.serviceDoneExecuting: done stopping (destroying) service */
    const int SERVICE_DONE_EXECUTING_STOP = 2;
    /** Type for IActivityManager.serviceDoneExecuting: done with an onRebind call */
    const int SERVICE_DONE_EXECUTING_REBIND = 3;
    /** Type for IActivityManager.serviceDoneExecuting: done with an onUnbind call */
    const int SERVICE_DONE_EXECUTING_UNBIND = 4;

    /** A service reporting that it has completed a binding, and returning the IBinder */
    void publishService(in IBinder token, in IBinder bindToken, in IBinder service);

    /** A service reporting that it has completed an unbind operation */
    void unbindFinished(in IBinder token, in IBinder bindToken);

    void attachNativeApplication(in IBinder nativeThread, long startSeq);

    void finishAttachApplication(long startSeq, long timestampApplicationOnCreateNs);
}
