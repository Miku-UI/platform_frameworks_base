/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.app.ActivityManager.START_ASSISTANT_HIDDEN_SESSION;
import static android.app.ActivityManager.START_ASSISTANT_NOT_ACTIVE_SESSION;
import static android.app.ActivityManager.START_CANCELED;
import static android.app.ActivityManager.START_CANNOT_GUARANTEE_TASK_MOVABILITY;
import static android.app.ActivityManager.START_CLASS_NOT_FOUND;
import static android.app.ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT;
import static android.app.ActivityManager.START_INTENT_NOT_RESOLVED;
import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_USER;
import static android.app.ActivityManager.START_NOT_ACTIVITY;
import static android.app.ActivityManager.START_NOT_VOICE_COMPATIBLE;
import static android.app.ActivityManager.START_PERMISSION_DENIED;
import static android.app.ActivityManager.START_VOICE_HIDDEN_SESSION;
import static android.app.ActivityManager.START_VOICE_NOT_ACTIVE_SESSION;
import static android.app.Instrumentation.checkStartActivityResult;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.annotation.Nullable;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.AndroidRuntimeException;

import androidx.test.filters.LargeTest;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.Test;

@LargeTest
public final class InstrumentationTest {

    // Copied from ActivityManager
    private static final int LAST_START_FATAL_ERROR_CODE = -1;
    private static final int LAST_START_SUCCESS_CODE = 99;


    // "Intent" used on most tests for checkStartActivityResult() - it takes an Object intent, hence
    // it's an String not Intent.
    private final String mIntent = "Well-intentioned I am.";

    private final Intent mIntentWithoutComponent = new Intent().setAction("Ready, set, action!");

    private final Intent mIntentWithComponent = new Intent()
            .setComponent(ComponentName.createRelative("I compose", "therefore I am!"));

    /**
     * Simple stress test for {@link Instrumentation#sendStatus(int, android.os.Bundle)}, to
     * ensure it can handle many rapid calls without failing.
     */
    @Test
    public void testSendStatus() {
        for (int i = 0; i < 10000; i++) {
            Bundle bundle = new Bundle();
            bundle.putInt("iterations", i);
            getInstrumentation().sendStatus(-1, bundle);
        }
    }

    @Test
    public void testCheckStartActivityResult_notError() {
        checkStartActivityResult(LAST_START_SUCCESS_CODE, mIntent);

        // Nothing to assert - would throw exception if it's an arror
    }

    @Test
    public void testCheckStartActivityResult_intentNotResolved_notIntent() {
        var thrown = assertThrows(ActivityNotFoundException.class,
                () -> checkStartActivityResult(START_INTENT_NOT_RESOLVED, mIntent));

        assertMessageNotIntent(thrown, mIntent);
    }

    @Test
    public void testCheckStartActivityResult_intentNotResolved_intentWithoutComponent() {
        var thrown = assertThrows(ActivityNotFoundException.class,
                () -> checkStartActivityResult(START_INTENT_NOT_RESOLVED, mIntentWithoutComponent));

        assertMessageNotIntent(thrown, mIntentWithoutComponent);
    }

    @Test
    public void testCheckStartActivityResult_intentNotResolved_intentWitComponent() {
        var thrown = assertThrows(ActivityNotFoundException.class,
                () -> checkStartActivityResult(START_INTENT_NOT_RESOLVED, mIntentWithComponent));

        assertMessageComponentNotFound(thrown, mIntentWithComponent);
    }

    @Test
    public void testCheckStartActivityResult_classNotFound_notIntent() {
        var thrown = assertThrows(ActivityNotFoundException.class,
                () -> checkStartActivityResult(START_CLASS_NOT_FOUND, mIntent));

        assertMessageNotIntent(thrown, mIntent);
    }

    @Test
    public void testCheckStartActivityResult_classNotFound_intentWithoutComponent() {
        var thrown = assertThrows(ActivityNotFoundException.class,
                () -> checkStartActivityResult(START_CLASS_NOT_FOUND, mIntentWithoutComponent));

        assertMessageNotIntent(thrown, mIntentWithoutComponent);
    }

    @Test
    public void testCheckStartActivityResult_classNotFound_intentWitComponent() {
        var thrown = assertThrows(ActivityNotFoundException.class,
                () -> checkStartActivityResult(START_CLASS_NOT_FOUND, mIntentWithComponent));

        assertMessageComponentNotFound(thrown, mIntentWithComponent);
    }

    @Test
    public void testCheckStartActivityResult_permissionDenied() {
        var thrown = assertThrows(SecurityException.class,
                () -> checkStartActivityResult(START_PERMISSION_DENIED, mIntent));

        assertMessage(thrown, "Not allowed to start activity %s", mIntent);
    }

    @Test
    public void testCheckStartActivityResult_forwardAndRequestConflict() {
        var thrown = assertThrows(AndroidRuntimeException.class,
                () -> checkStartActivityResult(START_FORWARD_AND_REQUEST_CONFLICT, mIntent));

        assertMessage(thrown, "FORWARD_RESULT_FLAG used while also requesting a result");
    }

    @Test
    public void testCheckStartActivityResult_startNotActivity() {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> checkStartActivityResult(START_NOT_ACTIVITY, mIntent));

        assertMessage(thrown, "PendingIntent is not an activity");
    }

    @Test
    public void testCheckStartActivityResult_notVoiceCompatible() {
        var thrown = assertThrows(SecurityException.class,
                () -> checkStartActivityResult(START_NOT_VOICE_COMPATIBLE, mIntent));

        assertMessage(thrown, "Starting under voice control not allowed for: %s", mIntent);
    }

    @Test
    public void testCheckStartActivityResult_voiceNotActiveSession() {
        var thrown = assertThrows(IllegalStateException.class,
                () -> checkStartActivityResult(START_VOICE_NOT_ACTIVE_SESSION, mIntent));

        assertMessage(thrown, "Session calling startVoiceActivity does not match active session");
    }

    @Test
    public void testCheckStartActivityResult_voiceHiddenSession() {
        var thrown = assertThrows(IllegalStateException.class,
                () -> checkStartActivityResult(START_VOICE_HIDDEN_SESSION, mIntent));

        assertMessage(thrown, "Cannot start voice activity on a hidden session");
    }

    @Test
    public void testCheckStartActivityResult_assistantNotActiveSession() {
        var thrown = assertThrows(IllegalStateException.class,
                () -> checkStartActivityResult(START_ASSISTANT_NOT_ACTIVE_SESSION, mIntent));

        assertMessage(thrown,
                "Session calling startAssistantActivity does not match active session");
    }

    @Test
    public void testCheckStartActivityResult_assistantHiddenSession() {
        var thrown = assertThrows(IllegalStateException.class,
                () -> checkStartActivityResult(START_ASSISTANT_HIDDEN_SESSION, mIntent));

        assertMessage(thrown, "Cannot start assistant activity on a hidden session");
    }

    @Test
    public void testCheckStartActivityResult_startCancelled() {
        var thrown = assertThrows(AndroidRuntimeException.class,
                () -> checkStartActivityResult(START_CANCELED, mIntent));

        assertMessage(thrown, "Activity could not be started for %s", mIntent);
    }

    @Test
    public void testCheckStartActivityResult_startNotAllowedForUser() {
        var thrown = assertThrows(AndroidRuntimeException.class,
                () -> checkStartActivityResult(START_NOT_ALLOWED_FOR_USER, mIntent));

        assertMessage(thrown, "Cannot start activity for %s for this user", mIntent);
    }

    @Test
    public void testCheckStartActivityResult_startCannotGuaranteeTaskMovability() {
        var thrown = assertThrows(InfeasibleActivityOptionsException.class,
                () -> checkStartActivityResult(START_CANNOT_GUARANTEE_TASK_MOVABILITY, mIntent));

        assertMessage(thrown, "Cannot guarantee that the activity will start in a movable task");
    }

    @Test
    public void testCheckStartActivityResult_unknownError() {
        int result = LAST_START_FATAL_ERROR_CODE;
        // NOTE: must pick a fatal error for result, hence the check above
        assertWithMessage("isStartResultFatalError(%s)", result)
                .that(ActivityManager.isStartResultFatalError(result)).isTrue();

        var thrown = assertThrows(AndroidRuntimeException.class,
                () -> checkStartActivityResult(result, mIntent));

        assertMessage(thrown, "Unknown error code %s when starting %s", result, mIntent);
    }

    @FormatMethod
    private void assertMessage(Throwable t, @FormatString String fmt, @Nullable Object...args) {
        String expectedMessage = String.format(fmt, args);
        assertWithMessage("exception message").that(t).hasMessageThat().isEqualTo(expectedMessage);
    }

    private void assertMessageNotIntent(Throwable t, Object intent) {
        assertMessage(t, "No Activity found to handle %s", intent);
    }

    private void assertMessageComponentNotFound(Throwable t, Intent intent) {
        assertMessage(t, "Unable to find explicit activity class %s; have you declared this "
                + "activity in your AndroidManifest.xml, or does your intent not match its declared"
                + " <intent-filter>?",
                intent.getComponent().toShortString());
    }
}
