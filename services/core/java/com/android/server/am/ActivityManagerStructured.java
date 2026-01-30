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

package com.android.server.am;

import static com.android.server.am.ActivityManagerDebugConfig.TAG_AM;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_WITH_CLASS_NAME;

import android.app.IActivityManagerStructured;
import android.app.IProcessObserver;
import android.app.IUidObserver;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

final class ActivityManagerStructured extends IActivityManagerStructured.Stub {
    static final String TAG = TAG_WITH_CLASS_NAME ? "ActivityManagerStructured" : TAG_AM;
    private final ActivityManagerService mAm;

    ActivityManagerStructured(ActivityManagerService am) {
        mAm = am;
    }

    @Override
    public ParcelFileDescriptor openContentUri(String uriString) throws RemoteException {
        return mAm.openContentUri(uriString);
    }

    @Override
    public void registerUidObserver(
            IUidObserver observer, int which, int cutpoint, String callingPackage)
            throws RemoteException {
        mAm.registerUidObserver(observer, which, cutpoint, callingPackage);
    }

    @Override
    public void unregisterUidObserver(IUidObserver observer) throws RemoteException {
        mAm.unregisterUidObserver(observer);
    }

    @Override
    public IBinder registerUidObserverForUids(
            IUidObserver observer, int which, int cutpoint, String callingPackage, int[] uids)
            throws RemoteException {
        return mAm.registerUidObserverForUids(observer, which, cutpoint, callingPackage, uids);
    }

    @Override
    public void addUidToObserver(IBinder observerToken, String callingPackage, int uid)
            throws RemoteException {
        mAm.addUidToObserver(observerToken, callingPackage, uid);
    }

    @Override
    public void removeUidFromObserver(IBinder observerToken, String callingPackage, int uid)
            throws RemoteException {
        mAm.removeUidFromObserver(observerToken, callingPackage, uid);
    }

    @Override
    public boolean isUidActive(int uid, String callingPackage) throws RemoteException {
        return mAm.isUidActive(uid, callingPackage);
    }

    @Override
    public int getUidProcessState(int uid, String callingPackage) throws RemoteException {
        return mAm.getUidProcessState(uid, callingPackage);
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) throws RemoteException {
        return mAm.checkPermission(permission, pid, uid);
    }

    @Override
    public void logFgsApiBegin(int apiType, int appUid, int appPid) throws RemoteException {
        mAm.logFgsApiBegin(apiType, appUid, appPid);
    }

    @Override
    public void logFgsApiEnd(int apiType, int appUid, int appPid) throws RemoteException {
        mAm.logFgsApiEnd(apiType, appUid, appPid);
    }

    @Override
    public void logFgsApiStateChanged(int apiType, int state, int appUid, int appPid)
            throws RemoteException {
        mAm.logFgsApiStateChanged(apiType, state, appUid, appPid);
    }

    @Override
    public void registerProcessObserver(IProcessObserver observer) throws RemoteException {
        mAm.registerProcessObserver(observer);
    }

    @Override
    public void unregisterProcessObserver(IProcessObserver observer) throws RemoteException {
        mAm.unregisterProcessObserver(observer);
    }

    @Override
    public List<android.app.RunningAppProcessInfo> getRunningAppProcesses() throws RemoteException {
        List<android.app.ActivityManager.RunningAppProcessInfo> amsList =
                mAm.getRunningAppProcesses();
        List<android.app.RunningAppProcessInfo> appList = new ArrayList<>();
        for (android.app.ActivityManager.RunningAppProcessInfo amsInfo : amsList) {
            final android.app.RunningAppProcessInfo info = new android.app.RunningAppProcessInfo();
            amsInfo.copyTo(info);
            appList.add(info);
        }
        return appList;
    }

    @Override
    public void serviceDoneExecuting(IBinder token, int type, int startId, int res)
            throws RemoteException {
        mAm.serviceDoneExecuting(token, type, startId, res);
    }

    @Override
    public void publishService(IBinder token, IBinder bindToken, android.os.IBinder service)
            throws RemoteException {
        mAm.publishService(token, bindToken, service);
    }

    @Override
    public void unbindFinished(IBinder token, IBinder bindToken) throws RemoteException {
        mAm.unbindFinished(token, bindToken);
    }

    @Override
    public void attachNativeApplication(IBinder nativeThread, long startSeq)
            throws RemoteException {
        mAm.attachNativeApplication(nativeThread, startSeq);
    }

    @Override
    public void finishAttachApplication(long startSeq, long timestampApplicationOnCreateNs)
            throws RemoteException {
        mAm.finishAttachApplication(startSeq, timestampApplicationOnCreateNs);
    }
}
