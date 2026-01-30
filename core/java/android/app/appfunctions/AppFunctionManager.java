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

import static android.Manifest.permission.MANAGE_APP_FUNCTION_ACCESS;
import static android.app.appfunctions.AppFunctionException.ERROR_SYSTEM_ERROR;
import static android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER;
import static android.permission.flags.Flags.FLAG_APP_FUNCTION_ACCESS_UI_ENABLED;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.annotation.UserHandleAware;
import android.app.appfunctions.AppFunctionManagerHelper.AppFunctionNotFoundException;
import android.app.appsearch.AppSearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.SignedPackage;
import android.content.pm.SignedPackageParcel;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ICancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelableException;
import android.os.RemoteException;
import android.os.SystemClock;
import android.permission.flags.Flags;
import android.provider.BaseColumns;
import android.util.ArraySet;

import com.android.internal.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Provides access to App Functions. App Functions is currently a beta/experimental preview feature.
 *
 * <p>An app function is a piece of functionality that apps expose to the system for cross-app
 * orchestration.
 *
 * <h3>Building App Functions</h3>
 *
 * <p>Most developers should build app functions through the AppFunctions SDK. This SDK library
 * offers a more convenient and type-safe way to build app functions. The SDK provides predefined
 * function schemas for common use cases and associated data classes for function parameters and
 * return values. Apps only have to implement the provided interfaces. Internally, the SDK converts
 * these data classes into {@link ExecuteAppFunctionRequest#getParameters()} and {@link
 * ExecuteAppFunctionResponse#getResultDocument()}.
 *
 * <h3>Discovering App Functions</h3>
 *
 * <p>When there is a package change or the device starts up, the metadata of available functions is
 * indexed on-device by {@link AppSearchManager}. AppSearch stores the indexed information as an
 * {@code AppFunctionStaticMetadata} document. This document contains the {@code functionIdentifier}
 * and the schema information that the app function implements. This allows other apps and the app
 * itself to discover these functions using the AppSearch search APIs. Visibility to this metadata
 * document is based on the packages that have visibility to the app providing the app functions.
 * AppFunction SDK provides a convenient way to achieve this and is the preferred method.
 *
 * <h3>Executing App Functions</h3>
 *
 * <p>To execute an app function, the caller app can retrieve the {@code functionIdentifier} from
 * the {@code AppFunctionStaticMetadata} document and use it to build an {@link
 * ExecuteAppFunctionRequest}. Then, invoke {@link #executeAppFunction} with the request to execute
 * the app function. Callers need the {@code android.permission.EXECUTE_APP_FUNCTIONS} permission to
 * execute app functions from other apps. An app can always execute its own app functions and
 * doesn't need these permissions. AppFunction SDK provides a convenient way to achieve this and is
 * the preferred method.
 *
 * <h3>Example</h3>
 *
 * <p>An assistant app is trying to fulfill the user request "Save XYZ into my note". The assistant
 * app should first list all available app functions as {@code AppFunctionStaticMetadata} documents
 * from AppSearch. Then, it should identify an app function that implements the {@code CreateNote}
 * schema. Finally, the assistant app can invoke {@link #executeAppFunction} with the {@code
 * functionIdentifier} of the chosen function.
 */
@FlaggedApi(FLAG_ENABLE_APP_FUNCTION_MANAGER)
@SystemService(Context.APP_FUNCTION_SERVICE)
public final class AppFunctionManager {

    /**
     * The contract between the AppFunction access history provider and applications with read
     * permission. Contains definitions for the supported URIs and columns.
     *
     * <p>This class provides access to the history of AppFunction calls. The access history is
     * stored on a per-user basis. An application querying the access history provider will only see
     * the records for the user it is currently running as.
     *
     * @see AppFunctionAttribution
     * @hide
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @SystemApi
    public static final class AccessHistory implements BaseColumns {
        private AccessHistory() {}

        @NonNull
        private static final Uri TARGET_USER_URI =
                Uri.parse("content://com.android.appfunction.accesshistory/user");

        /**
         * The package name of the agent app.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_AGENT_PACKAGE_NAME = "agent_package_name";

        /**
         * The package name of the target app.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_TARGET_PACKAGE_NAME = "target_package_name";

        /**
         * The type of interaction that triggered the function call. See {@link
         * AppFunctionAttribution.InteractionType} for a list of possible values.
         *
         * <p>The column is nullable. The caller should call {@link android.database.Cursor#isNull}
         * to check if the column value is null for that row.
         *
         * <p>Type: INTEGER (int)
         */
        @SuppressLint("IntentName")
        public static final String COLUMN_INTERACTION_TYPE = "interaction_type";

        /**
         * The custom interaction type, used when {@link
         * AppFunctionAttribution#getInteractionType()} is {@link
         * AppFunctionAttribution#INTERACTION_TYPE_OTHER}.
         *
         * <p>The column is nullable. The caller should call {@link android.database.Cursor#isNull}
         * to check if the column value is null for that row.
         *
         * <p>Type: TEXT
         */
        @SuppressLint("IntentName")
        public static final String COLUMN_CUSTOM_INTERACTION_TYPE = "custom_interaction_type";

        /**
         * A URI linking to the original interaction context.
         *
         * <p>The column is nullable. The caller should call {@link android.database.Cursor#isNull}
         * to check if the column value is null for that row.
         *
         * <p>To launch this URI, the caller must construct an explicit {@link
         * android.content.Intent}. An implicit Intent is not sufficient and may not resolve to the
         * correct component. The required procedure is as follows:
         *
         * <ol>
         *   <li>Create an {@link android.content.Intent} with this URI as its data.
         *   <li>Call {@link android.content.Intent#setPackage(String)} on the Intent, providing the
         *       package name from {@link AccessHistory#COLUMN_AGENT_PACKAGE_NAME}.
         *   <li>Resolve the target activity by calling {@link
         *       android.content.pm.PackageManager#resolveActivity(Intent, int)}.
         *   <li>If the returned {@link android.content.pm.ResolveInfo} and its nested {@code
         *       activityInfo} are not null, create an explicit Intent.
         *   <li>Make the Intent explicit by calling {@link
         *       android.content.Intent#setComponent(android.content.ComponentName)}, creating the
         *       {@code ComponentName} from the {@code packageName} and {@code name} fields within
         *       the {@link android.content.pm.ResolveInfo#activityInfo}.
         *   <li>The resulting explicit Intent can now be used to start the activity.
         * </ol>
         *
         * <p>Type: TEXT
         *
         * @see AppFunctionAttribution.Builder#setInteractionUri
         */
        @SuppressLint("IntentName")
        public static final String COLUMN_INTERACTION_URI = "interaction_uri";

        /**
         * An identifier to group related function calls.
         *
         * <p>The column is nullable. The caller should call {@link android.database.Cursor#isNull}
         * to check if the column value is null for that row.
         *
         * <p>Type: TEXT
         */
        public static final String COLUMN_THREAD_ID = "thread_id";

        /**
         * The timestamp (in milliseconds) when the app function was accessed.
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_ACCESS_TIME = "access_time";

        /**
         * The duration (in milliseconds) of the app function execution.
         *
         * <p>Type: INTEGER (long)
         */
        public static final String COLUMN_DURATION = "access_duration";
    }

    /**
     * Activity action: Launch UI that shows list of all agents and provides management of App
     * Function access of those agents.
     *
     * <p>Input: Nothing.
     *
     * <p>Output: Nothing.
     */
    @FlaggedApi(FLAG_APP_FUNCTION_ACCESS_UI_ENABLED)
    @SdkConstant(SdkConstant.SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_APP_FUNCTION_ACCESS =
            "android.app.appfunctions.action.MANAGE_APP_FUNCTION_ACCESS";

    /**
     * Activity action: Launch UI that shows a list of all targets that the specified agent package
     * can access, and provides management of App Function access of those targets.
     *
     * <p>Input: {@link android.content.Intent#EXTRA_PACKAGE_NAME} specifies the package whose
     * access will be managed by the launched UI.
     *
     * <p>Output: Nothing.
     *
     * @see android.content.Intent#EXTRA_PACKAGE_NAME
     */
    @FlaggedApi(FLAG_APP_FUNCTION_ACCESS_UI_ENABLED)
    @SdkConstant(SdkConstant.SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_AGENT_APP_FUNCTION_ACCESS =
            "android.app.appfunctions.action.MANAGE_AGENT_APP_FUNCTION_ACCESS";

    /**
     * Activity action: Launch UI that shows list of all agents for a specific target and provides
     * management of App Function access by those agents.
     *
     * <p>Input: {@link android.content.Intent#EXTRA_PACKAGE_NAME} specifies the package whose
     * access will be managed by the launched UI.
     *
     * <p>Output: Nothing.
     *
     * @see android.content.Intent#EXTRA_PACKAGE_NAME
     */
    @FlaggedApi(FLAG_APP_FUNCTION_ACCESS_UI_ENABLED)
    @SdkConstant(SdkConstant.SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_TARGET_APP_FUNCTION_ACCESS =
            "android.app.appfunctions.action.MANAGE_TARGET_APP_FUNCTION_ACCESS";

    /**
     * Activity action: Launch UI to for an agent to request App Function access of a target.
     *
     * <p>Input: {@link android.content.Intent#EXTRA_PACKAGE_NAME} specifies the package for which
     * the calling agent is requesting access of.
     *
     * <p>Output: Nothing.
     *
     * @see android.content.Intent#EXTRA_PACKAGE_NAME
     * @hide
     */
    @FlaggedApi(FLAG_APP_FUNCTION_ACCESS_UI_ENABLED)
    @SystemApi
    @SdkConstant(SdkConstant.SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_REQUEST_APP_FUNCTION_ACCESS =
            "android.app.appfunctions.action.REQUEST_APP_FUNCTION_ACCESS";

    /**
     * The default state of the app function. Call {@link #setAppFunctionEnabled} with this to reset
     * enabled state to the default value.
     */
    public static final int APP_FUNCTION_STATE_DEFAULT = 0;

    /**
     * The app function is enabled. To enable an app function, call {@link #setAppFunctionEnabled}
     * with this value.
     */
    public static final int APP_FUNCTION_STATE_ENABLED = 1;

    /**
     * The app function is disabled. To disable an app function, call {@link #setAppFunctionEnabled}
     * with this value.
     */
    public static final int APP_FUNCTION_STATE_DISABLED = 2;

    /**
     * App Function access request state indicating that the access has been granted for a
     * particular agent and target
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    public static final int ACCESS_REQUEST_STATE_GRANTED = 0;

    /**
     * App Function access request state indicating that the access has been denied for a particular
     * agent and target
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    public static final int ACCESS_REQUEST_STATE_DENIED = 1;

    /**
     * App Function access request state indicating that the access is not able to be granted for a
     * particular agent and target, due to the agent not being granted the EXECUTE_APP_FUNCTIONS
     * permission, or the target not having an App Function Service, or the agent not being in the
     * device allowlist, or one or both apps not being installed.
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    public static final int ACCESS_REQUEST_STATE_UNREQUESTABLE = 2;

    /** @hide */
    @IntDef(
            prefix = {"ACCESS_REQUEST_STATE_"},
            value = {
                ACCESS_REQUEST_STATE_DENIED,
                ACCESS_REQUEST_STATE_GRANTED,
                ACCESS_REQUEST_STATE_UNREQUESTABLE
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface AppFunctionAccessState {}

    /**
     * A flag indicating the app function access state has been pregranted by the system
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @SystemApi
    public static final int ACCESS_FLAG_PREGRANTED = 1;

    /**
     * A flag indicating the app function access is granted through a mechanism not tied to any
     * other flag (e.g. ADB)
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @SystemApi
    public static final int ACCESS_FLAG_OTHER_GRANTED = 1 << 1;

    /**
     * A flag indicating the app function access state has been denied by some other mechanism not
     * covered by another flag (e.g. ADB, self revoke)
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @SystemApi
    public static final int ACCESS_FLAG_OTHER_DENIED = 1 << 2;

    /**
     * A flag indicating the user granted the app function access state through UI
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @SystemApi
    public static final int ACCESS_FLAG_USER_GRANTED = 1 << 3;

    /**
     * A flag indicating the app function access state has been denied by the user
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @SystemApi
    public static final int ACCESS_FLAG_USER_DENIED = 1 << 4;

    /**
     * All USER flags
     *
     * @hide
     */
    @TestApi
    public static final int ACCESS_FLAG_MASK_USER =
            ACCESS_FLAG_USER_GRANTED | ACCESS_FLAG_USER_DENIED;

    /**
     * All OTHER flags
     *
     * @hide
     */
    @TestApi
    public static final int ACCESS_FLAG_MASK_OTHER =
            ACCESS_FLAG_OTHER_GRANTED | ACCESS_FLAG_OTHER_DENIED;

    /**
     * All access flags
     *
     * @hide
     */
    @TestApi
    public static final int ACCESS_FLAG_MASK_ALL =
            ACCESS_FLAG_PREGRANTED
                    | ACCESS_FLAG_OTHER_GRANTED
                    | ACCESS_FLAG_OTHER_DENIED
                    | ACCESS_FLAG_USER_GRANTED
                    | ACCESS_FLAG_USER_DENIED;

    @IntDef(
            prefix = {"ACCESS_FLAG_"},
            flag = true,
            value = {
                ACCESS_FLAG_PREGRANTED,
                ACCESS_FLAG_OTHER_GRANTED,
                ACCESS_FLAG_OTHER_DENIED,
                ACCESS_FLAG_USER_GRANTED,
                ACCESS_FLAG_USER_DENIED
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface AppFunctionAccessFlags {}

    private final IAppFunctionManager mService;
    private final Context mContext;

    /**
     * The enabled state of the app function.
     *
     * @hide
     */
    @IntDef(
            prefix = {"APP_FUNCTION_STATE_"},
            value = {
                APP_FUNCTION_STATE_DEFAULT,
                APP_FUNCTION_STATE_ENABLED,
                APP_FUNCTION_STATE_DISABLED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnabledState {}

    /**
     * Creates an instance.
     *
     * @param service An interface to the backing service.
     * @param context A {@link Context}.
     * @hide
     */
    public AppFunctionManager(IAppFunctionManager service, Context context) {
        mService = service;
        mContext = context;
    }

    /**
     * Executes the app function.
     *
     * <p>Note: Applications can execute functions they define. To execute functions defined in
     * another component, apps would need to have the permission {@code
     * android.permission.EXECUTE_APP_FUNCTIONS}.
     *
     * @param request the request to execute the app function
     * @param executor the executor to run the callback
     * @param cancellationSignal the cancellation signal to cancel the execution.
     * @param callback the callback to receive the function execution result or error.
     *     <p>If the calling app does not own the app function or does not have {@code
     *     android.permission.EXECUTE_APP_FUNCTIONS}, the execution result will contain {@code
     *     AppFunctionException.ERROR_DENIED}.
     *     <p>If the caller only has {@code android.permission.EXECUTE_APP_FUNCTIONS}, the execution
     *     result will contain {@code AppFunctionException.ERROR_DENIED}
     *     <p>If the function requested for execution is disabled, then the execution result will
     *     contain {@code AppFunctionException.ERROR_DISABLED}
     *     <p>If the cancellation signal is issued, the operation is cancelled and no response is
     *     returned to the caller.
     */
    @RequiresPermission(value = Manifest.permission.EXECUTE_APP_FUNCTIONS, conditional = true)
    @UserHandleAware
    public void executeAppFunction(
            @NonNull ExecuteAppFunctionRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        ExecuteAppFunctionAidlRequest aidlRequest =
                new ExecuteAppFunctionAidlRequest(
                        request,
                        mContext.getUser(),
                        mContext.getPackageName(),
                        /* requestTime= */ SystemClock.elapsedRealtime(),
                        /* requestWallTime= */ System.currentTimeMillis());

        try {
            ICancellationSignal cancellationTransport =
                    mService.executeAppFunction(
                            aidlRequest,
                            new IExecuteAppFunctionCallback.Stub() {
                                @Override
                                public void onSuccess(ExecuteAppFunctionResponse result) {
                                    try {
                                        executor.execute(() -> callback.onResult(result));
                                    } catch (RuntimeException e) {
                                        // Ideally shouldn't happen since errors are wrapped into
                                        // the response, but we catch it here for additional safety.
                                        executor.execute(
                                                () ->
                                                        callback.onError(
                                                                new AppFunctionException(
                                                                        ERROR_SYSTEM_ERROR,
                                                                        e.getMessage())));
                                    }
                                }

                                @Override
                                public void onError(AppFunctionException exception) {
                                    executor.execute(() -> callback.onError(exception));
                                }
                            });
            if (cancellationTransport != null) {
                cancellationSignal.setRemote(cancellationTransport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a boolean through a callback, indicating whether the app function is enabled.
     *
     * <p>This method can only check app functions owned by the caller, or those where the caller
     * has visibility to the owner package and holds the {@link
     * Manifest.permission#EXECUTE_APP_FUNCTIONS} permission.
     *
     * <p>If the operation fails, the callback's {@link OutcomeReceiver#onError} is called with
     * errors:
     *
     * <ul>
     *   <li>{@link IllegalArgumentException}, if the function is not found or the caller does not
     *       have access to it.
     * </ul>
     *
     * @param functionIdentifier the identifier of the app function to check (unique within the
     *     target package) and in most cases, these are automatically generated by the AppFunctions
     *     SDK
     * @param targetPackage the package name of the app function's owner
     * @param executor the executor to run the request
     * @param callback the callback to receive the function enabled check result
     */
    @RequiresPermission(value = Manifest.permission.EXECUTE_APP_FUNCTIONS, conditional = true)
    public void isAppFunctionEnabled(
            @NonNull String functionIdentifier,
            @NonNull String targetPackage,
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<Boolean, Exception> callback) {
        isAppFunctionEnabledInternal(functionIdentifier, targetPackage, executor, callback);
    }

    /**
     * Returns a boolean through a callback, indicating whether the app function is enabled.
     *
     * <p>This method can only check app functions owned by the caller, unlike {@link
     * #isAppFunctionEnabled(String, String, Executor, OutcomeReceiver)}, which allows specifying a
     * different target package.
     *
     * <p>If the operation fails, the callback's {@link OutcomeReceiver#onError} is called with
     * errors:
     *
     * <ul>
     *   <li>{@link IllegalArgumentException}, if the function is not found or the caller does not
     *       have access to it.
     * </ul>
     *
     * @param functionIdentifier the identifier of the app function to check (unique within the
     *     target package) and in most cases, these are automatically generated by the AppFunctions
     *     SDK
     * @param executor the executor to run the request
     * @param callback the callback to receive the function enabled check result
     */
    public void isAppFunctionEnabled(
            @NonNull String functionIdentifier,
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<Boolean, Exception> callback) {
        isAppFunctionEnabledInternal(
                functionIdentifier, mContext.getPackageName(), executor, callback);
    }

    /**
     * Sets the enabled state of the app function owned by the calling package.
     *
     * <p>If operation fails, the callback's {@link OutcomeReceiver#onError} is called with errors:
     *
     * <ul>
     *   <li>{@link IllegalArgumentException}, if the function is not found or the caller does not
     *       have access to it.
     * </ul>
     *
     * @param functionIdentifier the identifier of the app function to enable (unique within the
     *     calling package). In most cases, identifiers are automatically generated by the
     *     AppFunctions SDK
     * @param newEnabledState the new state of the app function
     * @param executor the executor to run the callback
     * @param callback the callback to receive the result of the function enablement. The call was
     *     successful if no exception was thrown.
     */
    @UserHandleAware
    public void setAppFunctionEnabled(
            @NonNull String functionIdentifier,
            @EnabledState int newEnabledState,
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(functionIdentifier);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        CallbackWrapper callbackWrapper = new CallbackWrapper(executor, callback);
        try {
            mService.setAppFunctionEnabled(
                    mContext.getPackageName(),
                    functionIdentifier,
                    mContext.getUser(),
                    newEnabledState,
                    callbackWrapper);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void isAppFunctionEnabledInternal(
            @NonNull String functionIdentifier,
            @NonNull String targetPackage,
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<Boolean, Exception> callback) {
        Objects.requireNonNull(functionIdentifier);
        Objects.requireNonNull(targetPackage);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        AppSearchManager appSearchManager = mContext.getSystemService(AppSearchManager.class);
        if (appSearchManager == null) {
            callback.onError(new IllegalStateException("Failed to get AppSearchManager."));
            return;
        }

        // Wrap the callback to convert AppFunctionNotFoundException to IllegalArgumentException
        // to match the documentation.
        OutcomeReceiver<Boolean, Exception> callbackWithExceptionInterceptor =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Boolean result) {
                        callback.onResult(result);
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        if (exception instanceof AppFunctionNotFoundException) {
                            exception = new IllegalArgumentException(exception);
                        }
                        callback.onError(exception);
                    }
                };

        AppFunctionManagerHelper.isAppFunctionEnabled(
                functionIdentifier,
                targetPackage,
                appSearchManager,
                executor,
                callbackWithExceptionInterceptor);
    }

    /**
     * Checks whether the given agent has access to app functions of the given target app, or if the
     * access is not {@link #getAccessRequestState(String) valid}. Requires the {@link
     * Manifest.permission.MANAGE_APP_FUNCTION_ACCESS} permission if the {@param agentPackageName}
     * is not the calling app.
     *
     * @param agentPackageName The package name of the agent
     * @param targetPackageName The package name of the target
     * @return The state of the access, one of {@link #ACCESS_REQUEST_STATE_GRANTED}, {@link
     *     #ACCESS_REQUEST_STATE_DENIED}, or {@link #ACCESS_REQUEST_STATE_UNREQUESTABLE}
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @RequiresPermission(value = MANAGE_APP_FUNCTION_ACCESS, conditional = true)
    @AppFunctionAccessState
    public int getAccessRequestState(
            @NonNull String agentPackageName, @NonNull String targetPackageName) {
        try {
            return mService.getAccessRequestState(
                    agentPackageName,
                    mContext.getUserId(),
                    targetPackageName,
                    mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Checks whether the calling app has access to app functions of the given target app, for the
     * given users, or if the access is invalid (not able to be requested). An access is valid if:
     * 1. The agent (calling app) and target apps are both installed, and the agent has visibility
     * of the target. 2. The agent has the {@link Manifest.permission.EXECUTE_APP_FUNCTIONS}
     * permission granted. 3. The agent is allowlisted by the system. 4. The target has an
     * AppFunctionService.
     *
     * @param targetPackageName The package name of the target
     * @return The state of the access, one of {@link #ACCESS_REQUEST_STATE_GRANTED}, {@link
     *     #ACCESS_REQUEST_STATE_DENIED}, or {@link #ACCESS_REQUEST_STATE_UNREQUESTABLE}
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @AppFunctionAccessState
    public int getAccessRequestState(@NonNull String targetPackageName) {
        return getAccessRequestState(mContext.getOpPackageName(), targetPackageName);
    }

    /**
     * Get the access flags for a given agent and target. These flags include extra information
     * about the access state (whether it is pregranted, if the user has set state, etc.). Returns 0
     * if the access is not {@link #getAccessRequestState(String) valid}.
     *
     * @param agentPackageName The package name of the agent
     * @param targetPackageName The package name of the target
     * @return The flags for the given agent and target app, or 0 if the combination is not valid
     * @hide
     */
    @SystemApi
    @RequiresPermission(MANAGE_APP_FUNCTION_ACCESS)
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @AppFunctionAccessFlags
    public int getAccessFlags(@NonNull String agentPackageName, @NonNull String targetPackageName) {
        try {
            return mService.getAccessFlags(
                    agentPackageName,
                    mContext.getUserId(),
                    targetPackageName,
                    mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Updates the access flags for the given agent and target. If the access is not {@link
     * #getAccessRequestState(String) valid}, this method is a no-op.
     *
     * @param agentPackageName The package name of the agent
     * @param targetPackageName The package name of the target
     * @param flagMask The mask determining which flag values will be changed
     * @param flags The flag values to be changed
     * @throws IllegalArgumentException if an invalid flag is specified, opposing flags (e.g.
     *     USER_GRANTED and USER_DENIED) are set together, or a flag with an opposite is set,
     *     without its opposite being explicitly cleared (via being included in the flag mask, but
     *     not the flag set).
     * @hide
     */
    @SystemApi
    @RequiresPermission(MANAGE_APP_FUNCTION_ACCESS)
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    public void updateAccessFlags(
            @NonNull String agentPackageName,
            @NonNull String targetPackageName,
            @AppFunctionAccessFlags int flagMask,
            @AppFunctionAccessFlags int flags) {
        try {
            mService.updateAccessFlags(
                    agentPackageName,
                    mContext.getUserId(),
                    targetPackageName,
                    mContext.getUserId(),
                    flagMask,
                    flags);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Revoke the App Function access for the calling app and the given target
     *
     * @param targetPackageName The app whose AppFunctionService the calling app should lose access
     *     to.
     */
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    public void revokeSelfAccess(@NonNull String targetPackageName) {
        try {
            mService.revokeSelfAccess(targetPackageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets all {@link #getAccessRequestState(String) valid} agents.
     *
     * @return A list of all valid agent package names
     * @hide
     */
    @SystemApi
    @RequiresPermission(MANAGE_APP_FUNCTION_ACCESS)
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    public @NonNull List<String> getValidAgents() {
        try {
            return mService.getValidAgents(mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets all {@link #getAccessRequestState(String) valid} target apps.
     *
     * @return A list of all target app package names in the current user that the agent can request
     *     access for
     * @hide
     */
    @SystemApi
    @RequiresPermission(MANAGE_APP_FUNCTION_ACCESS)
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    public @NonNull List<String> getValidTargets() {
        try {
            return mService.getValidTargets(mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Creates an intent which can be used to request App Function access for the given target app.
     * This intent MUST be used with {@link android.app.Activity#startActivityForResult}.The result
     * code of the activity will be {@link android.app.Activity#RESULT_OK} if the request was
     * granted, {@link android.app.Activity#RESULT_CANCELED} if not.
     *
     * @param targetPackageName The app access is being requested for.
     * @return The created intent.
     */
    @FlaggedApi(FLAG_APP_FUNCTION_ACCESS_UI_ENABLED)
    public @NonNull Intent createRequestAccessIntent(@NonNull String targetPackageName) {
        try {
            return mService.createRequestAccessIntent(targetPackageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the configured list of package names that should be grouped as Device Settings.
     *
     * <p>The list here is a configuration, the returned packages are not necessarily installed. The
     * package names here must refer to system apps.
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @NonNull
    public Set<String> getDeviceSettingPackages() {
        final String[] deviceSettingPackages =
                mContext.getResources()
                        .getStringArray(R.array.config_appFunctionDeviceSettingsPackages);
        return new ArraySet<>(deviceSettingPackages);
    }

    /**
     * Gets the current agent allowlist
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(MANAGE_APP_FUNCTION_ACCESS)
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    public @NonNull List<SignedPackage> getAgentAllowlist() {
        try {
            List<SignedPackageParcel> packageParcels = mService.getAgentAllowlist();
            int packageParcelsSize = packageParcels.size();
            List<SignedPackage> packages = new ArrayList<>(packageParcelsSize);
            for (int i = 0; i < packageParcelsSize; i++) {
                packages.add(new SignedPackage(packageParcels.get(i)));
            }
            return packages;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Clear the access history data.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(MANAGE_APP_FUNCTION_ACCESS)
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @UserHandleAware
    public void clearAccessHistory() {
        try {
            mService.clearAccessHistory(mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the {@code content://} style URI for the AppFunction access history table for the
     * user from the context used to obtain the instance of this class.
     *
     * <p>To query the content provider using the returned URI, the calling application must hold
     * the {@link android.Manifest.permission#MANAGE_APP_FUNCTION_ACCESS} permission.
     *
     * <p>To query for a user other than the current one, the caller must also hold the {@link
     * android.Manifest.permission#INTERACT_ACROSS_USERS_FULL} permission.
     *
     * <p>Attempting to query the content provider with the returned URI without holding the
     * necessary permissions will result in a {@link java.lang.SecurityException}.
     *
     * @return The {@link Uri} for the AppFunction access history table.
     * @hide
     */
    @SuppressLint("RequiresPermission") // Permission enforced in AppFunctionAccessHistoryProvider
    @SystemApi
    @FlaggedApi(Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
    @UserHandleAware
    @NonNull
    public Uri getAccessHistoryContentUri() {
        // The verification of whether the user has access to the target user's URI is enforced
        // in the provide.
        final int userId = mContext.getUserId();
        return AccessHistory.TARGET_USER_URI
                .buildUpon()
                .appendPath(Integer.toString(userId))
                .build();
    }

    private static class CallbackWrapper extends IAppFunctionEnabledCallback.Stub {

        private final OutcomeReceiver<Void, Exception> mCallback;
        private final Executor mExecutor;

        CallbackWrapper(
                @NonNull Executor callbackExecutor,
                @NonNull OutcomeReceiver<Void, Exception> callback) {
            mCallback = callback;
            mExecutor = callbackExecutor;
        }

        @Override
        public void onSuccess() {
            mExecutor.execute(() -> mCallback.onResult(null));
        }

        @Override
        public void onError(@NonNull ParcelableException exception) {
            mExecutor.execute(
                    () -> {
                        if (IllegalArgumentException.class.isAssignableFrom(
                                exception.getCause().getClass())) {
                            mCallback.onError((IllegalArgumentException) exception.getCause());
                        } else if (SecurityException.class.isAssignableFrom(
                                exception.getCause().getClass())) {
                            mCallback.onError((SecurityException) exception.getCause());
                        } else {
                            mCallback.onError(exception);
                        }
                    });
        }
    }
}
