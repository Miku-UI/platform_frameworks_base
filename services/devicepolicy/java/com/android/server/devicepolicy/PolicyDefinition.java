/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.devicepolicy;

import static android.app.role.RoleManager.ROLE_SYSTEM_FINANCED_DEVICE_CONTROLLER;
import static android.app.role.RoleManager.ROLE_SYSTEM_SUPERVISION;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.AccountTypePolicyKey;
import android.app.admin.BooleanPolicyValue;
import android.app.admin.DevicePolicyIdentifiers;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IntegerPolicyValue;
import android.app.admin.IntentFilterPolicyKey;
import android.app.admin.LockTaskPolicy;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PackagePermissionPolicyKey;
import android.app.admin.PackagePolicyKey;
import android.app.admin.PolicyKey;
import android.app.admin.PolicyValue;
import android.app.admin.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;

import com.android.internal.util.function.QuadFunction;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class PolicyDefinition<V> {

    static final String TAG = "PolicyDefinition";

    static final int POLICY_FLAG_NONE = 0;

    // Only use this flag if a policy can not be applied locally.
    public static final int POLICY_FLAG_GLOBAL_ONLY_POLICY = 1;

    // Only use this flag if a policy can not be applied globally.
    public static final int POLICY_FLAG_LOCAL_ONLY_POLICY = 1 << 1;

    // Only use this flag if a policy is inheritable by child profile from parent.
    static final int POLICY_FLAG_INHERITABLE = 1 << 2;

    // Use this flag if admin policies should be treated independently of each other and should not
    // have any resolution logic applied, this should only be used for very limited policies were
    // this would make sense and the enforcing logic should handle it appropriately, e.g.
    // application restrictions set by different admins for a single package should not be merged,
    // but saved and queried independent of each other.
    // Currently, support is  added for local only policies, if you need to add a non coexistable
    // global policy please add support.
    static final int POLICY_FLAG_NON_COEXISTABLE_POLICY = 1 << 3;

    // Add this flag to any policy that is a user restriction, the reason for this is that there
    // are some special APIs to handle user restriction policies and this is the way we can identify
    // them.
    static final int POLICY_FLAG_USER_RESTRICTION_POLICY = 1 << 4;

    // Only invoke the policy enforcer callback when the policy value changes, and do not invoke the
    // callback in other cases such as device reboots.
    static final int POLICY_FLAG_SKIP_ENFORCEMENT_IF_UNCHANGED = 1 << 5;

    // Add this flag to any policy that is a package policy and whose enforcement callback only
    // takes effect on installed packages. This flag will cause the enforcement callback to be
    // re-applied when the package is installed.
    private static final int POLICY_FLAG_PACKAGE_POLICY = 1 << 6;

    public static final MostRestrictive<Boolean> FALSE_MORE_RESTRICTIVE =
            new MostRestrictive<>(
                    List.of(new BooleanPolicyValue(false), new BooleanPolicyValue(true)));

    static final MostRestrictive<Boolean> TRUE_MORE_RESTRICTIVE = new MostRestrictive<>(
            List.of(new BooleanPolicyValue(true), new BooleanPolicyValue(false)));

    static final PolicyDefinition<Integer> GENERIC_PERMISSION_GRANT =
            new PolicyDefinition<>(
                    new PackagePermissionPolicyKey(DevicePolicyIdentifiers.PERMISSION_GRANT_POLICY),
                    // TODO: is this really the best mechanism, what makes denied more
                    //  restrictive than
                    //  granted?
                    new MostRestrictive<>(
                            List.of(
                                    new IntegerPolicyValue(
                                            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED),
                                    new IntegerPolicyValue(
                                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED),
                                    new IntegerPolicyValue(
                                            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT))),
                    POLICY_FLAG_LOCAL_ONLY_POLICY,
                    PolicyEnforcerCallbacks::setPermissionGrantState,
                    new IntegerPolicySerializer());

    static PolicyDefinition<Integer> PERMISSION_GRANT(
            @NonNull String packageName, @NonNull String permissionName) {
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(permissionName, "permissionName must not be null");
        return GENERIC_PERMISSION_GRANT.createPolicyDefinition(
                new PackagePermissionPolicyKey(
                        DevicePolicyIdentifiers.PERMISSION_GRANT_POLICY,
                        packageName,
                        permissionName));
    }

    static PolicyDefinition<Boolean> SECURITY_LOGGING = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.SECURITY_LOGGING_POLICY),
            TRUE_MORE_RESTRICTIVE,
            POLICY_FLAG_GLOBAL_ONLY_POLICY,
            PolicyEnforcerCallbacks::enforceSecurityLogging,
            new BooleanPolicySerializer());

    static PolicyDefinition<Boolean> AUDIT_LOGGING = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.AUDIT_LOGGING_POLICY),
            TRUE_MORE_RESTRICTIVE,
            POLICY_FLAG_GLOBAL_ONLY_POLICY,
            PolicyEnforcerCallbacks::enforceAuditLogging,
            new BooleanPolicySerializer());

    static PolicyDefinition<LockTaskPolicy> LOCK_TASK = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.LOCK_TASK_POLICY),
            new TopPriority<>(List.of(
                    EnforcingAdmin.getRoleAuthorityOf(ROLE_SYSTEM_FINANCED_DEVICE_CONTROLLER),
                    EnforcingAdmin.DPC_AUTHORITY)),
            POLICY_FLAG_LOCAL_ONLY_POLICY,
            (LockTaskPolicy value, Context context, Integer userId, PolicyKey policyKey) ->
                    PolicyEnforcerCallbacks.setLockTask(value, context, userId),
            new LockTaskPolicySerializer());

    static PolicyDefinition<Set<String>> USER_CONTROLLED_DISABLED_PACKAGES =
            new PolicyDefinition<>(
                    new NoArgsPolicyKey(
                            DevicePolicyIdentifiers.USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    new PackageSetUnion(),
                    PolicyEnforcerCallbacks::setUserControlDisabledPackages,
                    new PackageSetPolicySerializer());

    static PolicyDefinition<ComponentName> GENERIC_PERSISTENT_PREFERRED_ACTIVITY =
            new PolicyDefinition<>(
                    new IntentFilterPolicyKey(
                            DevicePolicyIdentifiers.PERSISTENT_PREFERRED_ACTIVITY_POLICY),
                    new TopPriority<>(List.of(
                            EnforcingAdmin.getRoleAuthorityOf(
                                    ROLE_SYSTEM_FINANCED_DEVICE_CONTROLLER),
                            EnforcingAdmin.DPC_AUTHORITY)),
                    POLICY_FLAG_LOCAL_ONLY_POLICY,
                    PolicyEnforcerCallbacks::addPersistentPreferredActivity,
                    new ComponentNamePolicySerializer());

    static PolicyDefinition<ComponentName> PERSISTENT_PREFERRED_ACTIVITY(
            @NonNull IntentFilter intentFilter) {
        Objects.requireNonNull(intentFilter, "intentFilter must not be null");
        return GENERIC_PERSISTENT_PREFERRED_ACTIVITY.createPolicyDefinition(
                new IntentFilterPolicyKey(
                        DevicePolicyIdentifiers.PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                        intentFilter));
    }

    static PolicyDefinition<Boolean> GENERIC_PACKAGE_UNINSTALL_BLOCKED =
            new PolicyDefinition<>(
                    new PackagePolicyKey(
                            DevicePolicyIdentifiers.PACKAGE_UNINSTALL_BLOCKED_POLICY),
                    TRUE_MORE_RESTRICTIVE,
                    POLICY_FLAG_LOCAL_ONLY_POLICY,
                    PolicyEnforcerCallbacks::setUninstallBlocked,
                    new BooleanPolicySerializer());

    static PolicyDefinition<Boolean> PACKAGE_UNINSTALL_BLOCKED(@NonNull String packageName) {
        Objects.requireNonNull(packageName, "packageName must not be null");
        return GENERIC_PACKAGE_UNINSTALL_BLOCKED.createPolicyDefinition(
                new PackagePolicyKey(
                        DevicePolicyIdentifiers.PACKAGE_UNINSTALL_BLOCKED_POLICY, packageName));
    }

    static PolicyDefinition<Bundle> GENERIC_APPLICATION_RESTRICTIONS =
            new PolicyDefinition<>(
                    new PackagePolicyKey(
                            DevicePolicyIdentifiers.APPLICATION_RESTRICTIONS_POLICY),
                    // Don't need to take in a resolution mechanism since its never used, but might
                    // need some refactoring to not always assume a non-null mechanism.
                    new MostRecent<>(),
                    // Only invoke the enforcement callback during policy change and not other state
                    POLICY_FLAG_LOCAL_ONLY_POLICY | POLICY_FLAG_INHERITABLE
                            | POLICY_FLAG_NON_COEXISTABLE_POLICY
                            | POLICY_FLAG_SKIP_ENFORCEMENT_IF_UNCHANGED,
                    PolicyEnforcerCallbacks::setApplicationRestrictions,
                    new BundlePolicySerializer());

    static PolicyDefinition<Bundle> APPLICATION_RESTRICTIONS(@NonNull String packageName) {
        Objects.requireNonNull(packageName, "packageName must not be null");
        return GENERIC_APPLICATION_RESTRICTIONS.createPolicyDefinition(
                new PackagePolicyKey(
                        DevicePolicyIdentifiers.APPLICATION_RESTRICTIONS_POLICY, packageName));
    }

    static PolicyDefinition<Long> RESET_PASSWORD_TOKEN = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.RESET_PASSWORD_TOKEN_POLICY),
            // Don't need to take in a resolution mechanism since its never used, but might
            // need some refactoring to not always assume a non-null mechanism.
            new MostRecent<>(),
            POLICY_FLAG_LOCAL_ONLY_POLICY | POLICY_FLAG_NON_COEXISTABLE_POLICY,
            // DevicePolicyManagerService handles the enforcement, this just takes care of storage
            PolicyEnforcerCallbacks::noOp,
            new LongPolicySerializer());

    static PolicyDefinition<Integer> KEYGUARD_DISABLED_FEATURES = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.KEYGUARD_DISABLED_FEATURES_POLICY),
            new FlagUnion(),
            POLICY_FLAG_LOCAL_ONLY_POLICY,
            // Nothing is enforced for keyguard features, we just need to store it
            PolicyEnforcerCallbacks::noOp,
            new IntegerPolicySerializer());

    static PolicyDefinition<Boolean> GENERIC_APPLICATION_HIDDEN =
            new PolicyDefinition<>(
                    new PackagePolicyKey(
                            DevicePolicyIdentifiers.APPLICATION_HIDDEN_POLICY),
                    // TODO(b/276713779): Don't need to take in a resolution mechanism since its
                    //  never used, but might need some refactoring to not always assume a non-null
                    //  mechanism.
                    TRUE_MORE_RESTRICTIVE,
                    POLICY_FLAG_LOCAL_ONLY_POLICY | POLICY_FLAG_INHERITABLE
                            | POLICY_FLAG_PACKAGE_POLICY,
                    PolicyEnforcerCallbacks::setApplicationHidden,
                    new BooleanPolicySerializer());

    static PolicyDefinition<Boolean> APPLICATION_HIDDEN(@NonNull String packageName) {
        Objects.requireNonNull(packageName, "packageName must not be null");
        return GENERIC_APPLICATION_HIDDEN.createPolicyDefinition(
                new PackagePolicyKey(
                        DevicePolicyIdentifiers.APPLICATION_HIDDEN_POLICY, packageName));
    }

    static PolicyDefinition<Boolean> GENERIC_ACCOUNT_MANAGEMENT_DISABLED =
            new PolicyDefinition<>(
                    new AccountTypePolicyKey(
                            DevicePolicyIdentifiers.ACCOUNT_MANAGEMENT_DISABLED_POLICY),
                    TRUE_MORE_RESTRICTIVE,
                    POLICY_FLAG_LOCAL_ONLY_POLICY | POLICY_FLAG_INHERITABLE,
                    // Nothing is enforced, we just need to store it
                    PolicyEnforcerCallbacks::noOp,
                    new BooleanPolicySerializer());

    static PolicyDefinition<Boolean> ACCOUNT_MANAGEMENT_DISABLED(@NonNull String accountType) {
        Objects.requireNonNull(accountType, "accountType must not be null");
        return GENERIC_ACCOUNT_MANAGEMENT_DISABLED.createPolicyDefinition(
                new AccountTypePolicyKey(
                        DevicePolicyIdentifiers.ACCOUNT_MANAGEMENT_DISABLED_POLICY, accountType));
    }

    static PolicyDefinition<Set<String>> PERMITTED_INPUT_METHODS = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.PERMITTED_INPUT_METHODS_POLICY),
            (Flags.usePolicyIntersectionForPermittedInputMethods()
                    ? new StringSetIntersection()
                    : new MostRecent<>()),
            POLICY_FLAG_LOCAL_ONLY_POLICY | POLICY_FLAG_INHERITABLE,
            PolicyEnforcerCallbacks::noOp,
            new PackageSetPolicySerializer());


    public static PolicyDefinition<Boolean> SCREEN_CAPTURE_DISABLED = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.SCREEN_CAPTURE_DISABLED_POLICY),
            TRUE_MORE_RESTRICTIVE,
            POLICY_FLAG_INHERITABLE,
            PolicyEnforcerCallbacks::setScreenCaptureDisabled,
            new BooleanPolicySerializer());

    static PolicyDefinition<Boolean> PERSONAL_APPS_SUSPENDED = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.PERSONAL_APPS_SUSPENDED_POLICY),
            new MostRecent<>(),
            POLICY_FLAG_LOCAL_ONLY_POLICY | POLICY_FLAG_INHERITABLE,
            PolicyEnforcerCallbacks::setPersonalAppsSuspended,
            new BooleanPolicySerializer());

    static PolicyDefinition<Boolean> USB_DATA_SIGNALING = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.USB_DATA_SIGNALING_POLICY),
            // usb data signaling is enabled by default, hence disabling it is more restrictive.
            FALSE_MORE_RESTRICTIVE,
            POLICY_FLAG_GLOBAL_ONLY_POLICY,
            (Boolean value, Context context, Integer userId, PolicyKey policyKey) ->
                    PolicyEnforcerCallbacks.setUsbDataSignalingEnabled(value, context),
            new BooleanPolicySerializer());

    static PolicyDefinition<Integer> CONTENT_PROTECTION = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.CONTENT_PROTECTION_POLICY),
            new MostRecent<>(),
            POLICY_FLAG_LOCAL_ONLY_POLICY,
            PolicyEnforcerCallbacks::setContentProtectionPolicy,
            new IntegerPolicySerializer());

    static PolicyDefinition<Integer> APP_FUNCTIONS = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.APP_FUNCTIONS_POLICY),
            new MostRestrictive<>(
                    List.of(
                            new IntegerPolicyValue(
                                    DevicePolicyManager.APP_FUNCTIONS_DISABLED),
                            new IntegerPolicyValue(
                                    DevicePolicyManager.APP_FUNCTIONS_DISABLED_CROSS_PROFILE),
                            new IntegerPolicyValue(
                                    DevicePolicyManager.APP_FUNCTIONS_NOT_CONTROLLED_BY_POLICY))),
            POLICY_FLAG_LOCAL_ONLY_POLICY,
            PolicyEnforcerCallbacks::noOp,
            new IntegerPolicySerializer());

    static PolicyDefinition<Integer> PASSWORD_COMPLEXITY = new PolicyDefinition<>(
            new NoArgsPolicyKey(DevicePolicyIdentifiers.PASSWORD_COMPLEXITY_POLICY),
            new MostRestrictive<>(
                    List.of(
                            new IntegerPolicyValue(
                                    DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH),
                            new IntegerPolicyValue(
                                    DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM),
                            new IntegerPolicyValue(
                                    DevicePolicyManager.PASSWORD_COMPLEXITY_LOW),
                            new IntegerPolicyValue(
                                    DevicePolicyManager.PASSWORD_COMPLEXITY_NONE))),
            POLICY_FLAG_LOCAL_ONLY_POLICY,
            PolicyEnforcerCallbacks::noOp,
            new IntegerPolicySerializer());

    static PolicyDefinition<Set<String>> PACKAGES_SUSPENDED =
            new PolicyDefinition<>(
                    new NoArgsPolicyKey(
                            DevicePolicyIdentifiers.PACKAGES_SUSPENDED_POLICY),
                    new PackageSetUnion(),
                    PolicyEnforcerCallbacks::noOp,
                    new PackageSetPolicySerializer());

    static PolicyDefinition<Integer> MEMORY_TAGGING = new PolicyDefinition<>(
            new NoArgsPolicyKey(
                    DevicePolicyIdentifiers.MEMORY_TAGGING_POLICY),
            new TopPriority<>(List.of(EnforcingAdmin.DPC_AUTHORITY)),
            PolicyEnforcerCallbacks::setMtePolicy,
            new IntegerPolicySerializer());

    static PolicyDefinition<Set<String>> CROSS_PROFILE_WIDGET_PROVIDER =
            new PolicyDefinition<>(
                    new NoArgsPolicyKey(
                            DevicePolicyIdentifiers.CROSS_PROFILE_WIDGET_PROVIDER_POLICY),
                    new StringSetIntersection(),
                    PolicyEnforcerCallbacks::setCrossProfileWidgetProviderPolicy,
                    new PackageSetPolicySerializer());

    static final PolicyDefinition<Integer> COMMON_CRITERIA_MODE =
            new PolicyDefinition<>(
                    new NoArgsPolicyKey(
                            DevicePolicyIdentifiers.COMMON_CRITERIA_MODE_POLICY),
                    new MostRestrictive<>(
                            List.of(
                                    new IntegerPolicyValue(
                                            DevicePolicyManager.COMMON_CRITERIA_MODE_ENABLED),
                                    new IntegerPolicyValue(
                                            DevicePolicyManager.COMMON_CRITERIA_MODE_DISABLED)
                            )
                    ),
                    POLICY_FLAG_GLOBAL_ONLY_POLICY,
                    PolicyEnforcerCallbacks::noOp,
                    new IntegerPolicySerializer()
            );

    private final PolicyKey mPolicyKey;
    private final ResolutionMechanism<V> mResolutionMechanism;
    private final int mPolicyFlags;
    // A function that accepts  policy to apply, context, userId, callback arguments, and returns
    // true if the policy has been enforced successfully.
    private final QuadFunction<V, Context, Integer, PolicyKey, CompletableFuture<Boolean>>
            mPolicyEnforcerCallback;
    private final PolicySerializer<V> mPolicySerializer;

    PolicyDefinition<V> createPolicyDefinition(PolicyKey key) {
        return new PolicyDefinition<>(key, mResolutionMechanism, mPolicyFlags,
                mPolicyEnforcerCallback, mPolicySerializer);
    }

    @NonNull
    public PolicyKey getPolicyKey() {
        return mPolicyKey;
    }

    @NonNull
    public ResolutionMechanism<V> getResolutionMechanism() {
        return mResolutionMechanism;
    }

    /**
     * Returns {@code true} if the policy is a global policy by nature and can't be applied locally.
     */
    public boolean isGlobalOnlyPolicy() {
        return (mPolicyFlags & POLICY_FLAG_GLOBAL_ONLY_POLICY) != 0;
    }

    /**
     * Returns {@code true} if the policy is a local policy by nature and can't be applied globally.
     */
    public boolean isLocalOnlyPolicy() {
        return (mPolicyFlags & POLICY_FLAG_LOCAL_ONLY_POLICY) != 0;
    }

    /**
     * Returns {@code true} if the policy is inheritable by child profiles.
     */
    boolean isInheritable() {
        return (mPolicyFlags & POLICY_FLAG_INHERITABLE) != 0;
    }

    /**
     * Returns {@code true} if the policy engine should not try to resolve policies set by different
     * admins and should just store it and pass it on to the enforcing logic.
     */
    boolean isNonCoexistablePolicy() {
        return (mPolicyFlags & POLICY_FLAG_NON_COEXISTABLE_POLICY) != 0;
    }

    boolean isUserRestrictionPolicy() {
        return (mPolicyFlags & POLICY_FLAG_USER_RESTRICTION_POLICY) != 0;
    }

    boolean shouldSkipEnforcementIfNotChanged() {
        return (mPolicyFlags & POLICY_FLAG_SKIP_ENFORCEMENT_IF_UNCHANGED) != 0;
    }

    boolean shouldReapplyOnPackageInstall() {
        return (mPolicyFlags & POLICY_FLAG_PACKAGE_POLICY) != 0;
    }

    @Nullable
    ResolvedPolicy<V> resolvePolicy(
            LinkedHashMap<EnforcingAdmin, PolicyValue<V>> adminsPolicy) {
        return mResolutionMechanism.resolve(adminsPolicy);
    }

    CompletableFuture<Boolean> enforcePolicy(@Nullable V value, Context context, int userId) {
        return mPolicyEnforcerCallback.apply(value, context, userId, mPolicyKey);
    }

    /**
     * Callers must ensure that {@code policyType} have implemented an appropriate
     * {@link Object#equals} implementation.
     */
    public PolicyDefinition(
            @NonNull PolicyKey key,
            ResolutionMechanism<V> resolutionMechanism,
            QuadFunction<V, Context, Integer, PolicyKey, CompletableFuture<Boolean>>
                    policyEnforcerCallback,
            PolicySerializer<V> policySerializer) {
        this(key, resolutionMechanism, POLICY_FLAG_NONE, policyEnforcerCallback, policySerializer);
    }

    /**
     * Callers must ensure that custom {@code policyKeys} and {@code V} have an appropriate
     * {@link Object#equals} and {@link Object#hashCode()} implementation.
     */
    PolicyDefinition(
            @NonNull PolicyKey policyKey,
            ResolutionMechanism<V> resolutionMechanism,
            int policyFlags,
            QuadFunction<V, Context, Integer, PolicyKey, CompletableFuture<Boolean>>
                    policyEnforcerCallback,
            PolicySerializer<V> policySerializer) {
        Objects.requireNonNull(policyKey);
        mPolicyKey = policyKey;
        mResolutionMechanism = resolutionMechanism;
        mPolicyFlags = policyFlags;
        mPolicyEnforcerCallback = policyEnforcerCallback;
        mPolicySerializer = policySerializer;

        if (isNonCoexistablePolicy() && !isLocalOnlyPolicy()) {
            throw new UnsupportedOperationException("Non-coexistable global policies not supported,"
                    + "please add support.");
        }
    }

    void savePolicyValueToXml(TypedXmlSerializer serializer, V value)
            throws IOException {
        mPolicySerializer.saveToXml(serializer, value);
    }

    @Nullable
    PolicyValue<V> readPolicyValueFromXml(TypedXmlPullParser parser) {
        return mPolicySerializer.readFromXml(parser);
    }

    @Override
    public String toString() {
        return "PolicyDefinition{ mPolicyKey= "
                + mPolicyKey
                + ", mResolutionMechanism= "
                + mResolutionMechanism
                + ", mPolicyFlags= "
                + mPolicyFlags
                + " }";
    }

    /** Returns a builder object for a {@link PolicyDefinition}. */
    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    public static class Builder<T> {

        private int mFlags = 0;
        private PolicyKey mKey = null;
        private QuadFunction<T, Context, Integer, PolicyKey, CompletableFuture<Boolean>>
                mEnforcerCallback = null;
        private PolicySerializer<T> mSerializer = null;
        private ResolutionMechanism<T> mResolutionMechanism = null;

        public PolicyDefinition<T> build() {
            if (mKey == null) {
                throw new IllegalStateException("Missing key when building PolicyDefinition");
            }
            if (mSerializer == null) {
                throw new IllegalStateException(
                        "Missing policy serializer when building PolicyDefinition of " + mKey);
            }
            if (mResolutionMechanism == null) {
                throw new IllegalStateException(
                        "Missing resolution mechanism when building PolicyDefinition of " + mKey);
            }
            if (mEnforcerCallback == null) {
                throw new IllegalStateException(
                        "Missing enforcer callback when building PolicyDefinition of " + mKey);
            }
            return new PolicyDefinition<T>(
                    mKey, mResolutionMechanism, mFlags, mEnforcerCallback, mSerializer);
        }

        public Builder<T> setKey(@NonNull PolicyKey key) {
            mKey = key;
            return this;
        }

        public Builder<T> setResolutionMechanism(@NonNull ResolutionMechanism<T> mechanism) {
            mResolutionMechanism = mechanism;
            return this;
        }

        public Builder<T> setEnforcerCallback(
                @NonNull
                        QuadFunction<T, Context, Integer, PolicyKey, CompletableFuture<Boolean>>
                                callback) {
            mEnforcerCallback = callback;
            return this;
        }

        public Builder<T> setSerializer(PolicySerializer<T> serializer) {
            mSerializer = serializer;
            return this;
        }

        /** Adds the given flag to the flags previously added to this builder. */
        public Builder<T> addFlag(int newFlagValue) {
            mFlags |= newFlagValue;
            return this;
        }

        /** Resets the flags previously added to this builder. */
        public Builder<T> clearFlags() {
            mFlags = 0;
            return this;
        }
    }
}
