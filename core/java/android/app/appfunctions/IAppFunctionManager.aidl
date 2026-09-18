/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.appfunctions;

import android.app.appfunctions.AppFunctionActivityId;
import android.app.appfunctions.AppFunctionAidlSearchSpec;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.app.appfunctions.IGetAppFunctionActivityStatesCallback;
import android.app.appfunctions.IIsAppFunctionEnabledCallback;
import android.app.appfunctions.ISetAppFunctionEnabledCallback;
import android.app.appfunctions.IExecuteAppFunctionCallback;
import android.app.appfunctions.IOnAppFunctionAccessChangeListener;
import android.app.appfunctions.IAppFunctionExecutor;
import android.app.appfunctions.IObserveAppFunctionChangesCallback;
import android.app.appfunctions.IGetAppFunctionStatesCallback;
import android.app.appfunctions.AppFunctionName;
import android.os.ICancellationSignal;
import android.os.UserHandle;
import android.content.Intent;
import android.content.pm.SignedPackage;
import android.content.pm.SignedPackageParcel;

import java.util.List;
/**
 * Defines the interface for apps to interact with the app function execution service
 * {@code AppFunctionManagerService} running in the system server process.
 * @hide
 */
interface IAppFunctionManager {
    /**
    * Executes an app function provided by {@link AppFunctionService} through the system.
    *
    * @param request the request to execute an app function.
    * @param callback the callback to report the result.
    */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = android.Manifest.permission.EXECUTE_APP_FUNCTIONS, conditional = true)")
    ICancellationSignal executeAppFunction(
        in ExecuteAppFunctionAidlRequest request, in IExecuteAppFunctionCallback callback
    );

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = android.Manifest.permission.EXECUTE_APP_FUNCTIONS, conditional = true)")
    void observeAppFunctions(
        in AppFunctionAidlSearchSpec aidlSearchSpec,
        in IObserveAppFunctionChangesCallback callback
    );

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = android.Manifest.permission.EXECUTE_APP_FUNCTIONS, conditional = true)")
    void unregisterAppFunctionObserver(
        in String callingPackage,
        in UserHandle userHandle,
        in IObserveAppFunctionChangesCallback callback
    );

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = android.Manifest.permission.EXECUTE_APP_FUNCTIONS, conditional = true)")
    void isAppFunctionEnabled(
        in String callingPackage,
        in String targetPackage,
        in String functionIdentifier,
        in UserHandle userHandle,
        in IIsAppFunctionEnabledCallback callback
    );

    /**
    * Sets an AppFunction's enabled state provided by {@link AppFunctionService} through the system.
    */
    void setAppFunctionEnabled(
        in String callingPackage,
        in String functionIdentifier,
        in UserHandle userHandle,
        int enabledState,
        in ISetAppFunctionEnabledCallback callback
    );

    int getAccessRequestState(
        in String agentPackageName,
        int agentUserId,
        in String targetPackageName,
        int targetUserId
    );

    int getAccessFlags(
        in String agentPackageName,
        int agentUserId,
        in String targetPackageName,
        int targetUserId
    );

    boolean updateAccessFlags(
        in String agentPackageName,
        int agentUserId,
        in String targetPackageName,
        int targetUserId,
        int flagMask,
        int flags
    );

    void registerAppFunctions(in String packageName, in List<String> functionIds, in IAppFunctionExecutor executor, in IBinder activityToken);

    void unregisterAppFunctions(in String packageName, in List<String> functionIds, in IAppFunctionExecutor executor);

    void revokeSelfAccess(in String targetPackageName);

    List<String> getValidAgents(
        int userId
    );

    List<String> getValidTargets(
        int targetUserId
    );

    Intent createRequestAccessIntent(in String targetPackageName);

    void addOnAccessChangedListener(IOnAppFunctionAccessChangeListener listener, int userId);

    void removeOnAccessChangedListener(IOnAppFunctionAccessChangeListener listener, int userId);

    void getAppFunctionStates(
        in List<AppFunctionName> appFunctionNames,
        in String callingPackageName,
        int targetUserId,
        in IGetAppFunctionStatesCallback callback);

    void getAppFunctionActivityStates(
        in List<AppFunctionActivityId> activityIds,
        in String callingPackageName,
        int targetUserId,
        in IGetAppFunctionActivityStatesCallback callback);
}
