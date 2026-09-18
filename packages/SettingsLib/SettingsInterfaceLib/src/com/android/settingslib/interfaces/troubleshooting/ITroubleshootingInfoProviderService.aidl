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

package com.android.settingslib.interfaces.troubleshooting;

import android.os.Bundle;
import android.os.ResultReceiver;

/** Interface for providing troubleshooting information. */
interface ITroubleshootingInfoProviderService {
    /** Current issue type */
    const String EXTRA_KEY_ISSUE_SUBJECT = "issue_subject";

    /** The current issue subject is Wi-Fi function. */
    const String ISSUE_SUBJECT_IS_WIFI = "active_wifi_connection";

    /** The current issue subject is Cellular function. */
    const String ISSUE_SUBJECT_IS_CELLULAR = "active_mobile_connection";

    /**
     * Result code sent to the ResultReceiver indicating the
     * issue detection check or operation completed successfully.
     */
    const int RESULT_SUCCESS = 0;

    /**
     * Result code sent to the ResultReceiver indicating the
     * issue detection check or operation failed.
     */
    const int RESULT_FAILED = 1;

    /** The package name of the target app.*/
    const String KEY_PACKAGE_NAME = "package_name";

    /** The class name of the target Activity of app*/
    const String KEY_CLASS_NAME = "class_name";

    /** The title of UI*/
    const String KEY_TITLE = "title";

    /** The description of UI */
    const String KEY_DESCRIPTION = "description";

    /** The name of the negative button. */
    const String KEY_NAME_OF_BTN_NEGATIVE = "name_of_negative_button";

    /** The action of the negative button. */
    const String KEY_ACTION_OF_BTN_NEGATIVE = "action_of_negative_button";

    /** The name of the positive button. */
    const String KEY_NAME_OF_BTN_POSITIVE = "name_of_positive_button";

    /** The action of the positive button. */
    const String KEY_ACTION_OF_BTN_POSITIVE = "action_of_positive_button";

    /** The name of footer for Settings preference UI. */
    const String KEY_NAME_OF_PREFERENCE_UI_FOOTER = "name_of_preference_ui_footer";

    /** The action of footer for Settings preference UI. */
    const String KEY_ACTION_OF_PREFERENCE_UI_FOOTER = "action_of_preference_ui_footer";

    /**
     * Registers a ResultReceiver to receive callbacks regarding the detection status
     * of a specific issue subject (e.g., Wi-Fi, Cellular).
     *
     * The service will use the provided {@link ResultReceiver} to send back results
     * related to the monitored {@code issueSubject}. The {@code resultCode} sent to the
     * receiver will typically be {@link #RESULT_SUCCESS} or {@link #RESULT_FAILED}.
     * Additional details may be provided in the {@code resultData} Bundle.
     *
     * This is a oneway call; the client will not block waiting for a response.
     *
     * @param issueSubject The category of issue to monitor. This should be one of the
     *                     ISSUE_SUBJECT_* constants defined in this interface
     *                     (e.g., {@link #ISSUE_SUBJECT_IS_WIFI}).
     * @param resultReceiver The {@link ResultReceiver} instance that will handle the
     *                       callback results from the service.
     */
    oneway void registerIssueDetectionCallback(String issueSubject, in ResultReceiver resultReceiver);

    /**
     * Unregisters a previously registered {@link ResultReceiver} from receiving
     * callbacks for the specified issue subject.
     *
     * After this call, the provided {@code resultReceiver} will no longer receive
     * updates for the given {@code issueSubject}. The client should use the same
     * instances of {@code issueSubject} and {@code resultReceiver} that were used
     * during registration.
     *
     * This is a oneway call.
     *
     * @param issueSubject The category of issue from which to unregister. This should
     *                     match the string used in {@link #registerIssueDetectionCallback}.
     * @param resultReceiver The exact {@link ResultReceiver} instance that was previously
     *                       passed to {@link #registerIssueDetectionCallback}.
     */
    oneway void unregisterIssueDetectionCallback(String issueSubject, in ResultReceiver resultReceiver);

    /** Get UI content of diagnostic information. */
    Bundle getDiagnosticUiInfo(String issueSubject);
}
