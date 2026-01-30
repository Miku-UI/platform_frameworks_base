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

package android.app.admin;

import static android.app.admin.flags.Flags.FLAG_POLICY_STREAMLINING;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.TestApi;
import android.processor.devicepolicy.EnumPolicyDefinition;
import android.processor.devicepolicy.PolicyDefinition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents a type safe identifier for a policy. Use it as a key for
 * {@link DevicePolicyManager.setPolicy setPolicy} and related APIs.
 *
 * @param <T> Represents the type of the value that is associated with this identifier.
 */
@FlaggedApi(FLAG_POLICY_STREAMLINING)
public final class PolicyIdentifier<T> {
    private final String mId;

    /**
     * Create an instance of PolicyIdentifier. Should only be used to create the static
     * definitions below.
     *
     * @hide
     */
    @TestApi
    public PolicyIdentifier(@NonNull String id) {
        this.mId = id;
    }

    /**
     * Get the string representation of this identifier.
     *
     * @return The string representation of this identifier
     * @hide
     */
    @NonNull
    @TestApi
    public String getId() {
        return mId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyIdentifier)) return false;
        PolicyIdentifier<?> that = (PolicyIdentifier<?>) o;
        return mId.equals(that.mId);
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }

    @Override
    @NonNull
    public String toString() {
        return mId;
    }

    /**
     * Block screen capture. See {@link android.view.Display#FLAG_SECURE} for more details on how
     * blocking works.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING)
    public static final int SCREEN_CAPTURE_BLOCKED = 1;

    /**
     * Allow screen capture.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING)
    public static final int SCREEN_CAPTURE_ALLOWED = 2;

    /**
     * Possible values {@link SCREEN_CAPTURE}
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "SCREEN_CAPTURE_" }, value = {
            SCREEN_CAPTURE_BLOCKED,
            SCREEN_CAPTURE_ALLOWED,
    })
    public @interface ScreenCaptureValue {}

    private static final String SCREEN_CAPTURE_KEY = "screenCapture";

    /**
     * Policy that controls whether the screen capture is enabled or disabled. Disabling
     * screen capture also prevents the content from being shown on display devices that do not have
     * a secure video output. See {@link android.view.Display#FLAG_SECURE} for more details about
     * secure surfaces and secure displays.
     * Throws SecurityException if the caller is not permitted to control screen capture policy.
     * If the scope is set to {@link DevicePolicyManager.POLICY_SCOPE_DEVICE} and the caller
     * is not a profile owner of an organization-owned managed profile, a security exception will
     * be thrown.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING)
    @NonNull
    @EnumPolicyDefinition(
            base = @PolicyDefinition,
            intDef = ScreenCaptureValue.class,
            defaultValue = SCREEN_CAPTURE_ALLOWED
    )
    public static final PolicyIdentifier<Integer> SCREEN_CAPTURE = new PolicyIdentifier<>(
            SCREEN_CAPTURE_KEY);
}
