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

package com.android.server.appfunctions;

import static android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_MASK_OTHER;
import static android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_DENIED;
import static android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_OTHER_GRANTED;

import static com.android.server.appfunctions.AppSearchDataJsonConverter.convertGenericDocumentToJson;
import static com.android.server.appfunctions.AppSearchDataJsonConverter.convertJsonToGenericDocument;
import static com.android.server.appfunctions.AppSearchDataJsonConverter.searchResultToJsonObject;
import static com.android.server.appfunctions.AppSearchDataYamlConverter.convertGenericDocumentToYaml;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionManager;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.app.appfunctions.IAppFunctionManager;
import android.app.appfunctions.IExecuteAppFunctionCallback;
import android.app.appfunctions.IIsAppFunctionEnabledCallback;
import android.app.appfunctions.ISetAppFunctionEnabledCallback;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.SearchResult;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.SignedPackage;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Binder;
import android.os.Bundle;
import android.os.ICancellationSignal;
import android.os.Process;
import android.os.ShellCommand;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.server.appfunctions.allowlist.SystemAppFunctionAllowlistReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.xmlpull.v1.XmlPullParser;

/** Shell command implementation for the {@link AppFunctionManagerService}. */
public class AppFunctionManagerServiceShellCommand extends ShellCommand {
    private static final long DEFAULT_EXECUTE_TIMEOUT_SECONDS = 30;

    @NonNull private final Context mContext;
    @NonNull private final IAppFunctionManager mService;

    AppFunctionManagerServiceShellCommand(
            @NonNull Context context, @NonNull IAppFunctionManager service) {
        mContext = Objects.requireNonNull(context);
        mService = Objects.requireNonNull(service);
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("AppFunctionManagerService commands:");
        pw.println("  help");
        pw.println("    Prints this help text.");
        pw.println();
        pw.println("  list-app-functions [--user <USER_ID>]");
        pw.println("    Lists all app functions for a specified user in JSON.");
        pw.println(
                "    --user <USER_ID> (optional): The user ID to list functions for. "
                        + "Defaults to the current user.");
        pw.println(
                "    --package <PACKAGE_NAME> (optional): Package name to list functions for. "
                        + "Defaults to all packages.");
        pw.println();
        pw.println(
                "  execute-app-function --package <PACKAGE_NAME> --function <FUNCTION_ID> "
                        + "--parameters <PARAMETERS_JSON> [--user <USER_ID>]"
                        + "[--timeout-duration <SECONDS>] [--brief-yaml] "
                        + "[--pending-intent-path <PATH>]");
        pw.println(
                "    Executes an app function for the given package with the provided parameters "
                        + " and returns the result as a JSON string");
        pw.println("    --package <PACKAGE_NAME>: The target package name.");
        pw.println("    --function <FUNCTION_ID>: The ID of the app function to execute.");
        pw.println(
                "    --parameters <PARAMETERS_JSON>: JSON string containing the parameters for "
                        + "the function.");
        pw.println(
                "    --user <USER_ID> (optional): The user ID to execute the function under. "
                        + "Defaults to the current user.");
        pw.println(
                "    --timeout-duration <SECONDS> (optional): The timeout for the function "
                        + "execution in seconds. Defaults to "
                        + DEFAULT_EXECUTE_TIMEOUT_SECONDS
                        + " seconds.");
        pw.println("    --brief-yaml (optional): Prints a concise yaml output.");
        pw.println(
                "    --pending-intent-path <PATH> (optional): The key in the response extras to "
                        + "extract and send a PendingIntent from. Can be nested using '.' as "
                        + "separator.");
        pw.println();
        pw.println(
                "  set-enabled --package <PACKAGE_NAME> --function <FUNCTION_ID> "
                        + "--state <enable|disable> [--user <USER_ID>]");
        pw.println("    Enables or disables an app function for the specified package.");
        pw.println("    --package <PACKAGE_NAME>: The target package name.");
        pw.println("    --function <FUNCTION_ID>: The ID of the app function.");
        pw.println("    --state <enable|disable|default>: The desired enabled state.");
        pw.println(
                "    --user <USER_ID> (optional): The user ID under which to set the function state"
                        + ". Defaults to the current user.");
        pw.println();
        pw.println(
                "  is-enabled --package <PACKAGE_NAME> --function <FUNCTION_ID> "
                        + "[--user <USER_ID>]");
        pw.println("    Checks if an app function is enabled for the specified package.");
        pw.println("    --package <PACKAGE_NAME>: The target package name.");
        pw.println("    --function <FUNCTION_ID>: The ID of the app function.");
        pw.println(
                "    --user <USER_ID> (optional): The user ID under which to check the function"
                        + " state. Defaults to the current user.");
        pw.println();
        pw.println("  purge-allowlist-cache");
        pw.println("    Purges the allowlist cache.");

        pw.println();

        if (accessCheckFlagsEnabled()) {
            // grant-app-function-access
            pw.println(
                    "  grant-app-function-access --agent-package <AGENT_PACKAGE_NAME> "
                            + "--target-package <TARGET_PACKAGE_NAME> [--agent-user <USER_ID>] "
                            + "[--target-user <USER_ID>]");
            pw.println("    Grants an agent package access to an app's functions.");
            pw.println(
                    "    --agent-package <AGENT_PACKAGE_NAME>: The agent package to grant access.");
            pw.println("    --target-package <TARGET_PACKAGE_NAME>: The target package.");
            pw.println(
                    "    --agent-user <USER_ID> (optional): The user ID for the agent package. "
                            + "Defaults to the current user.");
            pw.println(
                    "    --target-user <USER_ID> (optional): The user ID for the target package. "
                            + "Defaults to the current user.");
            pw.println();

            // revoke-app-function-access
            pw.println(
                    "  revoke-app-function-access --agent-package <AGENT_PACKAGE_NAME> "
                            + "--target-package <TARGET_PACKAGE_NAME> [--agent-user <USER_ID>] "
                            + "[--target-user <USER_ID>]");
            pw.println("    Revokes an agent package's access to an app's functions.");
            pw.println(
                    "    --agent-package <AGENT_PACKAGE_NAME>: The agent package to revoke access "
                            + "from.");
            pw.println("    --target-package <TARGET_PACKAGE_NAME>: The target package.");
            pw.println(
                    "    --agent-user <USER_ID> (optional): The user ID for the agent package. "
                            + "Defaults to the current user.");
            pw.println(
                    "    --target-user <USER_ID> (optional): The user ID for the target package. "
                            + "Defaults to the current user.");
            pw.println();

            // list-valid-agents
            pw.println("  list-valid-agents [--user <USER_ID>]");
            pw.println("    Lists all valid agents.");
            pw.println(
                    "    --user <USER_ID> (optional): The user ID to list valid agents for. "
                            + "Defaults to the current user.");
            pw.println();

            // list-valid-targets
            pw.println("  list-valid-targets [--user <USER_ID>]");
            pw.println("    Lists all valid targets.");
            pw.println(
                    "    --user <USER_ID> (optional): The user ID to list valid targets for. "
                            + "Defaults to the current user.");
            pw.println();
            pw.println();
            pw.println("  set-additional-allowlisted-agents <PACKAGE_NAME_1> <PACKAGE_NAME_2> ...");
            pw.println(
                    "    Sets the agents that are allowlisted, in addition to the device allowlist."
                            + " Value is a space-separated list of package names. Will override any"
                            + " agents set by previous calls to this command.");
            pw.println("  clear-additional-allowlisted-agents");
            pw.println("    Clears any agents set by set-additional-allowlisted-agents");
        }
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(null);
        }

        try {
            switch (cmd) {
                case "list-app-functions":
                    return runListAppFunctions();
                case "execute-app-function":
                    return runExecuteAppFunction();
                case "set-enabled":
                    return runSetAppFunctionEnabled();
                case "is-enabled":
                    return runIsAppFunctionEnabled();
                case "grant-app-function-access":
                    if (!accessCheckFlagsEnabled()) {
                        return -1;
                    }
                    return runGrantAppFunctionAccess();
                case "revoke-app-function-access":
                    if (!accessCheckFlagsEnabled()) {
                        return -1;
                    }
                    return runRevokeAppFunctionAccess();
                case "list-valid-agents":
                    if (!accessCheckFlagsEnabled()) {
                        return -1;
                    }
                    return listValidAgents();
                case "list-valid-targets":
                    if (!accessCheckFlagsEnabled()) {
                        return -1;
                    }
                    return listValidTargets();
                case "set-additional-allowlisted-agents":
                    if (!accessCheckFlagsEnabled()) {
                        return -1;
                    }
                    return setAdditionalAgents();
                case "clear-additional-allowlisted-agents":
                    if (!accessCheckFlagsEnabled()) {
                        return -1;
                    }
                    return clearAdditionalAgents();
                case "set-test-page-size":
                    if (!android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
                        return -1;
                    }
                    return setTestPageSize();
                case "reset-test-page-size":
                    if (!android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
                        return -1;
                    }
                    return resetTestPageSize();
                case "read-app-description":
                    // Not added to help, because it is not a platform feature yet.
                    return readAppDescription();
                case "purge-allowlist-cache":
                    if (!android.app.appfunctions.flags.Flags.enableAppFunctionPermissionV2()) {
                        return -1;
                    }
                    return purgeAllowlistCache();
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (Exception e) {
            getOutPrintWriter().println("Exception: " + e);
        }
        return -1;
    }

    private int readAppDescription() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        final PrintWriter errPw = getErrPrintWriter();
        String packageName = null;
        int userId = 0;
        String opt;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--package":
                    packageName = getNextArgRequired();
                    break;
                case "--user":
                    userId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                default:
                    errPw.println("Unknown option: " + opt);
                    return -1;
            }
        }
        Context userContext = mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        PackageManager pm = userContext.getPackageManager();
        int appMetadataXmlRes =
                pm.getProperty(
                                /* propertyName= */ "android.app.appfunctions.app_metadata",
                                packageName)
                        .getResourceId();
        if (appMetadataXmlRes == Resources.ID_NULL) {
            errPw.println("No app metadata found for package: " + packageName);
            return -1;
        }
        ApplicationInfo targetAppInfo = pm.getApplicationInfo(packageName, /* flags= */ 0);
        Resources targetAppResources =
                pm.getResourcesForApplication(
                        targetAppInfo, userContext.getResources().getConfiguration());
        var xmlParser = targetAppResources.getXml(appMetadataXmlRes);

        while (xmlParser.getEventType() != XmlPullParser.START_TAG) {
            xmlParser.next();

            if (xmlParser.getEventType() == XmlPullParser.END_DOCUMENT) {
                errPw.println("No app description found for package: " + packageName);
                return -1;
            }
        }
        String description = getXmlAttributeValue(xmlParser, "description");
        pw.println(description);
        return 0;
    }

    private String getXmlAttributeValue(XmlResourceParser xmlParser, String attributeName) {
        var value =
                xmlParser.getAttributeValue(
                        "http://schemas.android.com/apk/res-auto", attributeName);

        return value == null
                ? xmlParser.getAttributeValue(
                        "http://schemas.android.com/apk/androidx.appfunctions", attributeName)
                : value;
    }

    private int runListAppFunctions() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        int userId = ActivityManager.getCurrentUser();
        String opt;
        String packageName = null;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--user":
                    try {
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        pw.println("Invalid user ID: " + getNextArg() + ". Using current user.");
                    }
                    break;
                case "--package":
                    packageName = getNextArgRequired();
                    break;
                default:
                    pw.println("Unknown option: " + opt);
                    return -1;
            }
        }

        Context context = mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        final long token = Binder.clearCallingIdentity();
        try {
            Map<String, List<SearchResult>> perPackageSearchResult =
                    AppFunctionDumpHelper.queryAppFunctionsStateForUser(
                            context, packageName, /* isVerbose= */ true);
            JSONObject jsonObject = new JSONObject();
            for (Map.Entry<String, List<SearchResult>> entry : perPackageSearchResult.entrySet()) {
                JSONArray searchResults = new JSONArray();
                for (SearchResult result : entry.getValue()) {
                    searchResults.put(searchResultToJsonObject(result));
                }
                jsonObject.put(entry.getKey(), searchResults);
            }
            pw.println(jsonObject.toString(/* indentSpaces= */ 2));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        pw.flush();

        return 0;
    }

    private int runSetAppFunctionEnabled() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        String packageName = null;
        String functionId = null;
        int enabledState = -1;
        int userId = ActivityManager.getCurrentUser();
        String opt;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--package":
                    packageName = getNextArgRequired();
                    break;
                case "--function":
                    functionId = getNextArgRequired();
                    break;
                case "--state":
                    enabledState = determineEnabledState(getNextArgRequired());
                    break;
                case "--user":
                    try {
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        pw.println("Invalid user ID: " + getNextArg() + ". Using current user.");
                    }
                    break;
                default:
                    pw.println("Unknown option: " + opt);
                    return -1;
            }
        }

        if (packageName == null) {
            pw.println("Error: --package must be specified.");
            return -1;
        }
        if (functionId == null) {
            pw.println("Error: --function must be specified.");
            return -1;
        }
        if (enabledState == -1) {
            pw.println(
                    "Error: --state must be specified. The accepted values are: "
                            + "`enable`, `disable`, `default`.");
            return -1;
        }

        CountDownLatch countDownLatch = new CountDownLatch(1);

        ISetAppFunctionEnabledCallback callback =
                new ISetAppFunctionEnabledCallback.Stub() {
                    @RequiresNoPermission
                    @Override
                    public void onSuccess() {
                        pw.println("App function enabled state updated successfully.");
                        countDownLatch.countDown();
                    }

                    @RequiresNoPermission
                    @Override
                    public void onError(android.os.ParcelableException exception) {
                        pw.println("Error setting app function state: " + exception);
                        countDownLatch.countDown();
                    }
                };

        mService.setAppFunctionEnabled(
                packageName, functionId, UserHandle.of(userId), enabledState, callback);

        boolean completed = countDownLatch.await(5, TimeUnit.SECONDS);
        if (!completed) {
            pw.println("Timed out");
        }
        pw.flush();

        return 0;
    }

    private int determineEnabledState(String state) {
        switch (state) {
            case "default":
                return AppFunctionManager.APP_FUNCTION_STATE_DEFAULT;
            case "enable":
                return AppFunctionManager.APP_FUNCTION_STATE_ENABLED;
            case "disable":
                return AppFunctionManager.APP_FUNCTION_STATE_DISABLED;
        }

        return -1;
    }

    private int runIsAppFunctionEnabled() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        String packageName = null;
        String functionId = null;
        int userId = ActivityManager.getCurrentUser();
        String opt;
        int enabledState = AppFunctionManager.APP_FUNCTION_STATE_DEFAULT;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--package":
                    packageName = getNextArgRequired();
                    break;
                case "--function":
                    functionId = getNextArgRequired();
                    break;
                case "--user":
                    try {
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        pw.println("Invalid user ID: " + getNextArg() + ". Using current user.");
                    }
                    break;
                default:
                    pw.println("Unknown option: " + opt);
                    return -1;
            }
        }

        if (packageName == null) {
            pw.println("Error: --package must be specified.");
            return -1;
        }
        if (functionId == null) {
            pw.println("Error: --function must be specified.");
            return -1;
        }

        CountDownLatch countDownLatch = new CountDownLatch(1);
        IIsAppFunctionEnabledCallback callback =
                new IIsAppFunctionEnabledCallback.Stub() {
                    @RequiresNoPermission
                    @Override
                    public void onSuccess(boolean isEnabled) {
                        pw.println(isEnabled);
                        countDownLatch.countDown();
                    }

                    @RequiresNoPermission
                    @Override
                    public void onError(android.os.ParcelableException exception) {
                        pw.println("Error checking app function state: " + exception);
                        countDownLatch.countDown();
                    }
                };
        mService.isAppFunctionEnabled(
                packageName, packageName, functionId, UserHandle.of(userId), callback);

        boolean completed = countDownLatch.await(5, TimeUnit.SECONDS);
        if (!completed) {
            pw.println("Timed out");
        }
        pw.flush();
        return 0;
    }

    private int runExecuteAppFunction() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        String packageName = null;
        String functionId = null;
        String parametersJson = null;
        int userId = ActivityManager.getCurrentUser();
        long timeoutDurationSeconds = DEFAULT_EXECUTE_TIMEOUT_SECONDS;
        boolean briefYaml = false;
        String pendingIntentPath = null;
        String opt;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--package":
                    packageName = getNextArgRequired();
                    break;
                case "--function":
                    functionId = getNextArgRequired();
                    break;
                case "--parameters":
                    parametersJson = getNextArgRequired();
                    break;
                case "--user":
                    try {
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        pw.println("Invalid user ID: " + getNextArg() + ". Using current user.");
                    }
                    break;
                case "--timeout-duration":
                    try {
                        timeoutDurationSeconds = Long.parseLong(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        pw.println(
                                "Invalid timeout duration: "
                                        + getNextArg()
                                        + ". Using default of "
                                        + DEFAULT_EXECUTE_TIMEOUT_SECONDS
                                        + "s.");
                    }
                    break;
                case "--brief-yaml":
                    briefYaml = true;
                    break;
                case "--pending-intent-path":
                    pendingIntentPath = getNextArgRequired();
                    break;
                default:
                    pw.println("Unknown option: " + opt);
                    return -1;
            }
        }

        if (packageName == null) {
            pw.println("Error: --package must be specified.");
            return -1;
        }
        if (functionId == null) {
            pw.println("Error: --function must be specified.");
            return -1;
        }
        if (parametersJson == null) {
            pw.println("Error: --parameters must be specified.");
            return -1;
        }

        GenericDocument parameters = convertJsonToGenericDocument(parametersJson);

        ExecuteAppFunctionAidlRequest request =
                new ExecuteAppFunctionAidlRequest(
                        new ExecuteAppFunctionRequest.Builder(packageName, functionId)
                                .setParameters(parameters)
                                .build(),
                        UserHandle.of(userId),
                        getCallingPackage(),
                        SystemClock.elapsedRealtime(),
                        System.currentTimeMillis());

        CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicInteger resultCode = new AtomicInteger(0);
        final boolean finalBriefYaml = briefYaml;
        final String finalPendingIntentPath = pendingIntentPath;
        IExecuteAppFunctionCallback callback =
                new IExecuteAppFunctionCallback.Stub() {

                    @Override
                    public void onSuccess(ExecuteAppFunctionResponse response) {
                        try {
                            // HACK: GenericDocument doesn't tell whether a property is singular
                            // or repeated. We always assume the return is an array here.
                            if (finalBriefYaml) {
                                String functionReturnYaml =
                                        convertGenericDocumentToYaml(
                                                response.getResultDocument(),
                                                /* keepEmptyValues= */ false,
                                                /* keepNullValues= */ false,
                                                /* keepGenericDocumentProperties= */ false);
                                pw.println(functionReturnYaml);
                            } else {
                                JSONObject functionReturnJson =
                                        convertGenericDocumentToJson(response.getResultDocument());
                                pw.println(functionReturnJson.toString(/* indentSpace= */ 2));
                            }

                            if (finalPendingIntentPath != null) {
                                extractAndSendPendingIntent(pw, response, finalPendingIntentPath);
                            }
                        } catch (JSONException | PendingIntent.CanceledException e) {
                            pw.println("Failed to convert the function response to JSON.");
                            resultCode.set(-1);
                        } finally {
                            countDownLatch.countDown();
                        }
                    }

                    @Override
                    public void onError(AppFunctionException e) {
                        Log.d(TAG, "onError: ", e);
                        pw.printf(
                                "Error executing app function: %s. See logcat for more details. %n",
                                e);
                        resultCode.set(-1);
                        countDownLatch.countDown();
                    }
                };

        ICancellationSignal cancellationSignal = mService.executeAppFunction(request, callback);

        boolean returned = countDownLatch.await(timeoutDurationSeconds, TimeUnit.SECONDS);
        if (!returned) {
            pw.println("Timed out");
            cancellationSignal.cancel();
            resultCode.set(-1);
        }
        pw.flush();

        return resultCode.get();
    }

    private void extractAndSendPendingIntent(
            PrintWriter pw, ExecuteAppFunctionResponse response, String pendingIntentPath)
            throws PendingIntent.CanceledException {
        PendingIntent pendingIntent = null;
        String[] pathSegments = pendingIntentPath.split("\\.");
        Bundle currentBundle = response.getExtras();
        for (int i = 0; i < pathSegments.length; i++) {
            String segment = pathSegments[i];
            if (i == pathSegments.length - 1) {
                pendingIntent = currentBundle.getParcelable(segment, PendingIntent.class);
            } else {
                currentBundle = currentBundle.getBundle(segment);
                if (currentBundle == null) {
                    break;
                }
            }
        }
        if (pendingIntent != null) {
            ActivityOptions activityOptions = ActivityOptions.makeBasic();
            activityOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
            final long token = Binder.clearCallingIdentity();
            try {
                pendingIntent.send(activityOptions.toBundle());
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            pw.println("Sent PendingIntent from extras key: " + pendingIntentPath);
        } else {
            pw.println("No PendingIntent found at extras key: " + pendingIntentPath);
        }
    }

    private int setAdditionalAgents() {
        List<String> packages = new ArrayList<>();
        packages.add(getNextArgRequired());
        String packageName;
        while ((packageName = getNextArg()) != null) {
            packages.add(packageName);
        }
        return setAdditionalAgents(packages);
    }

    private int clearAdditionalAgents() {
        return setAdditionalAgents(new ArrayList<>());
    }

    private int setAdditionalAgents(List<String> agents) {
        final long token = Binder.clearCallingIdentity();
        try {
            Settings.Secure.putString(
                    mContext.getContentResolver(),
                    Settings.Secure.APP_FUNCTION_ADDITIONAL_AGENT_ALLOWLIST,
                    SignedPackageParser.serializePackagesOnly(agents));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return 0;
    }

    private int runGrantAppFunctionAccess() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        String agentPackage = null;
        String targetPackage = null;
        int agentUserId = ActivityManager.getCurrentUser();
        int targetUserId = ActivityManager.getCurrentUser();
        String opt;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--agent-package":
                    agentPackage = getNextArgRequired();
                    break;
                case "--target-package":
                    targetPackage = getNextArgRequired();
                    break;
                case "--agent-user":
                    agentUserId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                case "--target-user":
                    targetUserId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                default:
                    pw.println("Unknown option: " + opt);
                    return -1;
            }
        }

        if (agentPackage == null) {
            pw.println("Error: --agent-package must be specified.");
            return -1;
        }
        if (targetPackage == null) {
            pw.println("Error: --target-package must be specified.");
            return -1;
        }

        boolean result =
                mService.updateAccessFlags(
                        agentPackage,
                        agentUserId,
                        targetPackage,
                        targetUserId,
                        ACCESS_FLAG_MASK_OTHER,
                        ACCESS_FLAG_OTHER_GRANTED);
        if (!result) {
            pw.println("Error: Failed to grant the app function access.");
            return -1;
        }
        pw.println("Access granted successfully.");
        return 0;
    }

    private int runRevokeAppFunctionAccess() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        String agentPackage = null;
        String targetPackage = null;
        int agentUserId = ActivityManager.getCurrentUser();
        int targetUserId = ActivityManager.getCurrentUser();
        String opt;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--agent-package":
                    agentPackage = getNextArgRequired();
                    break;
                case "--target-package":
                    targetPackage = getNextArgRequired();
                    break;
                case "--agent-user":
                    agentUserId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                case "--target-user":
                    targetUserId = UserHandle.parseUserArg(getNextArgRequired());
                    break;
                default:
                    pw.println("Unknown option: " + opt);
                    return -1;
            }
        }

        if (agentPackage == null) {
            pw.println("Error: --agent-package must be specified.");
            return -1;
        }
        if (targetPackage == null) {
            pw.println("Error: --target-package must be specified.");
            return -1;
        }
        boolean result =
                mService.updateAccessFlags(
                        agentPackage,
                        agentUserId,
                        targetPackage,
                        targetUserId,
                        ACCESS_FLAG_MASK_OTHER,
                        ACCESS_FLAG_OTHER_DENIED);
        if (!result) {
            pw.println("Error: Failed to revoke the app function access.");
            return -1;
        }
        pw.println("Access revoked successfully.");
        return 0;
    }

    private int listValidAgents() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        int userId = ActivityManager.getCurrentUser();
        String opt;

        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                userId = UserHandle.parseUserArg(getNextArgRequired());
            } else {
                pw.println("Unknown option: " + opt);
                return -1;
            }
        }

        final List<String> validAgents = mService.getValidAgents(userId);
        pw.println("Valid agents: " + validAgents.toString());
        return 0;
    }

    private int listValidTargets() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        int userId = ActivityManager.getCurrentUser();
        String opt;

        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                userId = UserHandle.parseUserArg(getNextArgRequired());
            } else {
                pw.println("Unknown option: " + opt);
                return -1;
            }
        }

        final List<String> validTargets = mService.getValidTargets(userId);
        pw.println("Valid targets: " + validTargets.toString());
        return 0;
    }

    private int setTestPageSize() {
        final PrintWriter pw = getOutPrintWriter();
        int pageSize = -1;

        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--page-size")) {
                try {
                    pageSize = Integer.parseInt(getNextArgRequired());
                } catch (Exception e) {
                    pw.println("Fail to parse page-size");
                    return -1;
                }
            } else {
                pw.println("Unknown option: " + opt);
                return -1;
            }
        }

        if (pageSize <= 0) {
            pw.println("Invalid page size of " + pageSize);
        }
        ServiceConfig.sTestPageSize.set(pageSize);
        pw.println("Set test page size to " + pageSize);
        return 0;
    }

    private int purgeAllowlistCache() {
        final PrintWriter pw = getOutPrintWriter();
        SystemAppFunctionAllowlistReader.getInstance(mContext).purgeCache();
        pw.println("Purge allowlist cache");
        return 0;
    }

    private int resetTestPageSize() {
        ServiceConfig.sTestPageSize.set(0);
        return 0;
    }

    private static String getCallingPackage() {
        return switch (UserHandle.getAppId(Binder.getCallingUid())) {
            case Process.ROOT_UID -> "root";
            case Process.SHELL_UID -> "com.android.shell";
            default -> throw new IllegalAccessError("Only allow shell or root");
        };
    }

    private boolean accessCheckFlagsEnabled() {
        return android.permission.flags.Flags.appFunctionAccessApiEnabled()
                && android.permission.flags.Flags.appFunctionAccessServiceEnabled();
    }
}
