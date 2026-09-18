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

package com.android.server.appfunctions;

import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.util.ArrayMap;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.json.JSONObject;
import org.json.JSONException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;

/**
 * A logger for AppFunction request and response.
 *
 * <p>This logger internally uses {@link AppFunctionPersistentLogger} to log request and response to
 * disk.
 */
public class AppFunctionRequestResponseLogger {

    private final AppFunctionPersistentLogger mPersistentLogger;

    /**
     * @param baseDir The directory to store logs (e.g., /data/system_ce/0/appfunctions)
     */
    AppFunctionRequestResponseLogger(File baseDir) throws IOException {
        File logDir = new File(baseDir, "request_responses");
        mPersistentLogger =
                new AppFunctionPersistentLogger(logDir, "app_functions_request_response.log");
    }

    @WorkerThread
    public void log(
            @NonNull ExecuteAppFunctionRequest request,
            @Nullable ExecuteAppFunctionResponse response,
            @Nullable AppFunctionException exception) {
        Objects.requireNonNull(request);

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("Request:\n").append(convertRequestToString(request));
        if (response != null) {
            logBuilder.append("\nResponse:\n").append(convertResponseToString(response));
        }
        if (exception != null) {
            logBuilder.append("\nException:\n").append(convertExceptionToString(exception));
        }
        mPersistentLogger.log(logBuilder.toString());
    }

    private String convertExceptionToString(AppFunctionException exception) {
        Map<String, Object> map = new ArrayMap<String, Object>();

        map.put("message", exception.getMessage());
        map.put("errorCode", exception.getErrorCode());

        return AppSearchDataYamlConverter.MinimalYamlGenerator.dump(map);
    }

    private String convertRequestToString(ExecuteAppFunctionRequest request) {
        Map<String, Object> map = new ArrayMap<String, Object>();

        map.put("targetPackageName", request.getTargetPackageName());
        map.put("functionIdentifier", request.getFunctionIdentifier());
        map.put(
                "parameters",
                AppSearchDataYamlConverter.convertGenericDocumentToYaml(
                        request.getParameters(),
                        /* keepEmptyValues= */ false,
                        /* keepNullValues= */ false,
                        /* keepGenericDocumentProperties= */ false));

        return AppSearchDataYamlConverter.MinimalYamlGenerator.dump(map);
    }

    private String convertResponseToString(ExecuteAppFunctionResponse response) {
        return AppSearchDataYamlConverter.convertGenericDocumentToYaml(
                        response.getResultDocument(),
                        /* keepEmptyValues= */ false,
                        /* keepNullValues= */ false,
                        /* keepGenericDocumentProperties= */ false)
                .toString();
    }

    public void close() {
        mPersistentLogger.close();
    }
}
