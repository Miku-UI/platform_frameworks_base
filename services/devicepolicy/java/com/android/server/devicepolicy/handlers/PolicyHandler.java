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

package com.android.server.devicepolicy.handlers;

import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER;
import static android.app.admin.DevicePolicyManager.RESOURCE_DEVICE_WIDE;
import static android.app.admin.DevicePolicyManager.RESOURCE_PER_USER;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.admin.DevicePolicyManager.DpcType;
import android.app.admin.DevicePolicyManager.PolicyScope;
import android.app.admin.PolicyIdentifier;
import android.app.admin.PolicyValue;
import android.app.admin.PolicyValueTransport;
import android.app.admin.metadata.PolicyMetadata;
import android.app.admin.metadata.PolicyTransportValueConvertor;

import com.android.server.devicepolicy.CallerIdentity;
import com.android.server.devicepolicy.IPermissionChecker;
import com.android.server.devicepolicy.PolicyDefinition;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A PolicyHandler contains all the logic required for setting the policy with the given key.
 *
 * <p>This logic uses the information provided in the PolicyDefinition annotation applied to the key
 * (of type {@link PolicyIdentifier}).
 *
 * <p>To add support for a new policy, you need a {@link PolicyHandler} and a {@link
 * PolicyDefinition}. Both are generated automatically, but can be overridden if your policy needs
 * some special handling.
 *
 * <p>If handling of your policy needs to do something extra, you can achieve that by overriding the
 * corresponding `protected` function in your policy handler in the {@code
 * PolicyHandlerFactory.build()} list. For example, to completely overrule the permission checks you
 * can override the checkPermissions() method:
 *
 * {@snippet :
 *     public static List<PolicyHandler<?>> build() {
 *         List<PolicyHandler<?>> handlers = new ArrayList<>();
 *         // <snip other handlers>
 *         handlers.add(new PolicyHandler<>(PolicyIdentifier.MY_POLICY) {
 *            @Override
 *            public void checkPermissions(CallerIdentity caller, int scope) {
 *                 // Your custom permission checks go here.
 *            }
 *         });
 *         return handlers;
 *     }
 * }
 *
 * If you want to *augment* the permission checks instead, you can use `super` to invoke the default
 * version:
 *
 * {@snippet :
 *     public static List<PolicyHandler<?>> build() {
 *         List<PolicyHandler<?>> handlers = new ArrayList<>();
 *         // <snip other handlers>
 *         handlers.add(new PolicyHandler<>(PolicyIdentifier.MY_POLICY) {
 *            @Override
 *            public void checkPermissions(CallerIdentity caller, int scope) {
 *                  // Invoke normal permission checks
 *                  super.checkPermissions(caller, scope);
 *                 // Your custom permission checks go here.
 *             }
 *         });
 *         return handlers;
 *     }
 * }
 *
 * @param <T> Represents the type of the value that is associated with this policy.
 */
public class PolicyHandler<T> {

    private final PolicyIdentifier<T> mKey;

    // These must be initialized by calling {@code initialize()} before using the policy handler.
    private PolicyMetadata<T> mPolicyMetadata = null;
    private PolicyValidator<T> mValidator = null;
    private PolicyValueConvertor<T> mValueConvertor = null;
    private PolicyTransportValueConvertor<T> mTransportValueConvertor = null;
    private PolicyDefinition<T> mPolicyDefinition = null;

    /** Helper class that provides access to methods used while processing policies. */
    private Delegate mDelegate = null;

    public PolicyHandler(@NonNull PolicyIdentifier<T> key) {
        this.mKey = key;
    }

    /** Convenience constructor used by unittests */
    public PolicyHandler(
            @NonNull PolicyIdentifier<T> key,
            @NonNull PolicyMetadata<T> policyMetadata,
            @Nullable PolicyDefinition<T> policyDefinition,
            @NonNull Delegate delegate) {
        this(key);
        initialize(delegate, policyDefinition, policyMetadata);
    }

    public PolicyIdentifier<T> getKey() {
        return mKey;
    }

    public final boolean hasPolicyDefinition() {
        return (mPolicyDefinition != null);
    }

    @NonNull
    protected PolicyTransportValueConvertor<T> getTransportValueConvertor() {
        return mTransportValueConvertor;
    }

    @NonNull
    protected PolicyValueConvertor<T> getValueConvertor() {
        return mValueConvertor;
    }

    @NonNull
    protected PolicyMetadata<T> getPolicyMetadata() {
        return mPolicyMetadata;
    }

    @NonNull
    protected Delegate getDelegate() {
        if (mDelegate == null) {
            throw new IllegalStateException("Delegate must be set before using the handler");
        }
        return mDelegate;
    }

    public void initialize(
            @NonNull Delegate delegate,
            @Nullable PolicyDefinition<T> definition,
            @NonNull PolicyMetadata<T> metadata) {
        mDelegate = delegate;
        mPolicyDefinition = definition;
        mPolicyMetadata = metadata;
        mValidator = PolicyValidator.getInstance(metadata);
        mTransportValueConvertor = PolicyTransportValueConvertor.getInstance(metadata);
        mValueConvertor = PolicyValueConvertor.getInstance(metadata);
    }

    /******************************************************************************************
     * The methods below can be overwritten to change the behavior of your policy.
     *****************************************************************************************/

    /**
     * Performs every step required to set the policy, except for permission checks.
     *
     * <p>The caller is responsible for calling {@link #checkPermissions(CallerIdentity, int)}
     * before calling this method.
     */
    public void setPolicyUnchecked(
            @NonNull CallerIdentity caller,
            @PolicyScope int scope,
            @Nullable PolicyValueTransport transportValue) {
        validateScope(scope);

        T value = convertValue(transportValue);

        validateValue(caller, value);

        storePolicyValue(caller, scope, value);
    }

    /**
     * Performs every step required to retrieve the policy, except for permission checks.
     *
     * <p>The caller is responsible for calling {@link #checkPermissions(CallerIdentity, int)}
     * before calling this method.
     */
    public @Nullable PolicyValueTransport getPolicyUnchecked(
            @NonNull CallerIdentity caller, @PolicyScope int scope) {
        validateScope(scope);

        T value = getPolicyValue(caller, scope);

        return convertValue(value);
    }

    /** Read the resolved per-user policy value. */
    protected T getResolvedPerUserPolicyValue(int userId) {
        return getDelegate().getResolvedPerUserPolicy(userId, getPolicyDefinition());
    }

    /** Retrieves the effective value of the per-user policy. Does not check permissions. */
    public PolicyValueTransport getResolvedPerUserPolicyUnchecked(int userId) {
        if (getPolicyMetadata().getAffectedResource() != RESOURCE_PER_USER) {
            throw new IllegalArgumentException(
                    "Policy "
                            + getPolicyMetadata().getId()
                            + " is not per-user. Call getResolvedDeviceWidePolicy instead.");
        }

        return convertValue(getResolvedPerUserPolicyValue(userId));
    }

    /** Read the resolved device-wide policy value. */
    protected T getResolvedDeviceWidePolicyValue() {
        return getDelegate().getResolvedDeviceWidePolicy(getPolicyDefinition());
    }

    /** Retrieves the effective value of the device-wide policy. Does not check permissions. */
    public PolicyValueTransport getResolvedDeviceWidePolicyUnchecked() {
        if (getPolicyMetadata().getAffectedResource() != RESOURCE_DEVICE_WIDE) {
            throw new IllegalArgumentException(
                    "Policy "
                            + getPolicyMetadata().getId()
                            + " is not device-wide. Call getResolvedPerUserPolicy instead.");
        }

        return convertValue(getResolvedDeviceWidePolicyValue());
    }

    /**
     * Validates if the {@link PolicyScope} can be used for this policy.
     *
     * @throws IllegalArgumentException if the scope can not be used.
     */
    protected void validateScope(@PolicyScope int scope) {
        if (!getPolicyMetadata().getAllowedScopes().contains(scope)) {
            throw new IllegalArgumentException(
                    "Policy "
                            + getKey().getId()
                            + " only supports scopes "
                            + scopesToString(getPolicyMetadata().getAllowedScopes())
                            + ", but got scope "
                            + scopeToString(scope));
        }
    }

    /**
     * Converts the given {@link PolicyValueTransport} to the value of type `T` that should be
     * stored.
     */
    @Nullable
    protected T convertValue(@Nullable PolicyValueTransport transportValue) {
        if (transportValue == null) {
            return null;
        }
        return getTransportValueConvertor().fromTransport(transportValue);
    }

    /** Converts the given value to the corresponding {@link PolicyValueTransport}. */
    @Nullable
    protected PolicyValueTransport convertValue(@Nullable T value) {
        if (value == null) {
            return null;
        }
        return getTransportValueConvertor().toTransport(value);
    }

    @NonNull
    private String getRequiredPermission() {
        var requiredPermission = getPolicyMetadata().getRequiredPermission();
        if (requiredPermission == null) {
            throw new IllegalStateException(
                    "Policy "
                            + getKey().getId()
                            + " has no requiredPermission, either add one or override"
                            + " checkPermissions in the handler");
        }
        return requiredPermission;
    }

    private void checkPermissionInternal(
            CallerIdentity caller,
            boolean checkRequiredPermission,
            boolean checkRequiredCrossUserPermission) {
        var permissionChecker = getPermissionChecker();
        var requiredPermission = getRequiredPermission();

        if (checkRequiredPermission) {
            if (!isPolicyAllowedForDpc(mDelegate.getDpcType(caller))) {
                permissionChecker.enforce(requiredPermission, caller);
            }
        }

        if (checkRequiredCrossUserPermission) {
            var requiredCrossUserPermission = getPolicyMetadata().getRequiredCrossUserPermission();
            if (requiredCrossUserPermission != null) {
                permissionChecker.enforce(requiredCrossUserPermission, caller);
            }
        }
    }

    /**
     * Performs permission checks, based on the information in the {@link PolicyDefinition}
     * information provided on the {@link PolicyIdentifier}.
     *
     * <p>If the policy is applied on global or parent scope this additionally checks if the caller
     * has the proper 'manage across users' permission.
     *
     * <p>Can be overridden to provide custom permission checks instead.
     *
     * @throws SecurityException when the caller does not have the required permissions.
     */
    public void checkPermissions(CallerIdentity caller, @PolicyScope int scope) {
        checkPermissionInternal(
                caller,
                /* checkRequiredPermission= */ true,
                /* checkRequiredCrossUserPermission= */ scope != POLICY_SCOPE_USER);
    }

    /**
     * Performs permission checks, based on the information in the {@link PolicyDefinition}
     * information provided on the {@link PolicyIdentifier}.
     *
     * <p>If the target {@code userId} is different from the {@code caller}, this additionally
     * checks if the caller has the proper 'manage across users' permission.
     *
     * @param caller The caller from the binder context.
     * @param userId The target user.
     */
    public void checkReadResolvedPerUserPermissions(CallerIdentity caller, @UserIdInt int userId) {
        var permissionChecker = getPermissionChecker();

        var hasQueryAdminPolicy =
                permissionChecker.hasPermission(permission.QUERY_ADMIN_POLICY, caller);

        checkPermissionInternal(
                caller,
                /* checkRequiredPermission= */ !hasQueryAdminPolicy,
                /* checkRequiredCrossUserPermission= */ caller.getUserId() != userId);
    }

    /**
     * Performs permission checks, based on the information in the {@link PolicyDefinition}
     * information provided on the {@link PolicyIdentifier}.
     *
     * <p>Always checks if the caller has the proper 'manage across users' permission.
     *
     * @param caller The caller from the binder context.
     */
    public void checkReadResolvedDeviceWidePermissions(CallerIdentity caller) {
        var permissionChecker = getPermissionChecker();

        var hasQueryAdminPolicy =
                permissionChecker.hasPermission(permission.QUERY_ADMIN_POLICY, caller);

        checkPermissionInternal(
                caller,
                /* checkRequiredPermission= */ !hasQueryAdminPolicy,
                /* checkRequiredCrossUserPermission= */ true);
    }

    /**
     * Performs validation of the provided value, based on the information in the {@link
     * PolicyDefinition} information provided on the {@link PolicyIdentifier}.
     *
     * <p>Can be overridden to provide custom validation instead.
     *
     * @throws IllegalArgumentException when validation rejects the value.
     */
    protected void validateValue(@NonNull CallerIdentity caller, @Nullable T value) {
        if (value != null) {
            getValidator().validate(value, getPolicyMetadata());
        }
    }

    /**
     * Stores the policy value inside the {@code DevicePolicyEngine}.
     *
     * <p>Can be overridden to store the value somewhere else instead.
     */
    protected void storePolicyValue(
            @NonNull CallerIdentity caller, @PolicyScope int scope, @Nullable T value) {
        PolicyDefinition<T> key = getPolicyDefinition();

        if (value != null) {
            storePolicy(caller, key, scope, getValueConvertor().toPolicyValue(value));
        } else {
            clearPolicy(caller, key, scope);
        }
    }

    /**
     * Retrieves the policy value from the {@code DevicePolicyEngine}.
     *
     * <p>Can be overridden to retrieve the value from somewhere else instead.
     */
    protected @Nullable T getPolicyValue(@NonNull CallerIdentity caller, @PolicyScope int scope) {
        return getPolicySetByAdmin(caller, getPolicyDefinition(), scope);
    }

    /******************************************************************************************
     * The methods below are helper methods to be used in your overwritten methods.
     *****************************************************************************************/

    protected static String scopeToString(@PolicyScope int scope) {
        return switch (scope) {
            case POLICY_SCOPE_DEVICE -> "SCOPE_DEVICE";
            case POLICY_SCOPE_USER -> "SCOPE_USER";
            case POLICY_SCOPE_PARENT_USER -> "SCOPE_PARENT_USER";
            default -> "INVALID_SCOPE_" + Integer.toString(scope);
        };
    }

    protected static String scopesToString(Collection<Integer> scopes) {
        return "["
                + scopes.stream().map(s -> scopeToString(s)).collect(Collectors.joining(", "))
                + "]";
    }

    protected final @NonNull PolicyValidator<T> getValidator() {
        return mValidator;
    }

    /**
     * Returns the policy definition used by {@link storePolicyValue} to store the policy in the
     * DevicePolicyEngine.
     */
    @NonNull
    public final PolicyDefinition<T> getPolicyDefinition() {
        if (mPolicyDefinition == null) {
            throw new IllegalStateException(
                    "getPolicyDefinition() can not be used for policy "
                            + getKey().getId()
                            + ", since PolicyDefinitionFactory returned null");
        }
        return mPolicyDefinition;
    }

    @DpcType
    protected final int getDpcType(@NonNull CallerIdentity caller) {
        return getDelegate().getDpcType(caller);
    }

    protected final IPermissionChecker getPermissionChecker() {
        return getDelegate().getPermissionChecker();
    }

    // Returns if the policy is automatically allowed by a DPC of the given type.
    // If this is the case, then the DPC does not need the permission.
    // Note that the DPC would still need the cross-user permission if the policy is set on
    // a scope other than USER.
    protected final boolean isPolicyAllowedForDpc(@DpcType int dpcType) {
        return getPolicyMetadata().getAllowedDpcTypes().contains(dpcType);
    }

    protected final <StoredType> void storePolicy(
            @NonNull CallerIdentity caller,
            @NonNull PolicyDefinition<StoredType> key,
            @PolicyScope int scope,
            @NonNull PolicyValue<StoredType> value) {
        getDelegate().storePolicy(caller, key, scope, value);
    }

    protected final <StoredType> void clearPolicy(
            @NonNull CallerIdentity caller,
            @NonNull PolicyDefinition<StoredType> key,
            @PolicyScope int scope) {
        getDelegate().clearPolicy(caller, key, scope);
    }

    protected final <StoredType> @Nullable StoredType getPolicySetByAdmin(
            @NonNull CallerIdentity caller,
            @NonNull PolicyDefinition<StoredType> key,
            @PolicyScope int scope) {
        return getDelegate().getPolicySetByAdmin(caller, key, scope);
    }

    /** Helper class that provides access to helper methods used while processing policies. */
    public interface Delegate {
        /** Returns the DPC type of the given caller. */
        @DpcType
        int getDpcType(@NonNull CallerIdentity caller);

        /** Returns the permission checker that can be used to check permissions. */
        IPermissionChecker getPermissionChecker();

        /**
         * Helper method to store a policy into the {@code DevicePolicyEngine}. Invoked from {@link
         * PolicyHandler.storePolicyValue}.
         *
         * <p>Will use {@code scope} to decide on the correct storage (global vs local on user vs
         * local on parent of the user).
         */
        <StoredType> void storePolicy(
                @NonNull CallerIdentity caller,
                @NonNull PolicyDefinition<StoredType> key,
                @PolicyScope int scope,
                @NonNull PolicyValue<StoredType> value);

        /**
         * Helper method to remove a policy from the {@code DevicePolicyEngine}. Invoked from {@link
         * PolicyHandler.storePolicyValue}.
         *
         * <p>Will use {@link scope} to decide on the correct storage (global vs local on user vs
         * local on parent of the user).
         */
        <StoredType> void clearPolicy(
                @NonNull CallerIdentity caller,
                @NonNull PolicyDefinition<StoredType> key,
                @PolicyScope int scope);

        /**
         * Helper method to retrieve the policy value stored by the caller in the {@code
         * DevicePolicyEngine}, or null if no value is stored. Invoked from {@link
         * PolicyHandler.getPolicyValue}.
         *
         * <p>Will use {@link scope} to decide on the correct storage (global vs local on user vs
         * local on parent of the user).
         *
         * <p>Note this does not return the resolved policy value, but the value stored by the
         * caller.
         */
        @Nullable
        <StoredType> StoredType getPolicySetByAdmin(
                @NonNull CallerIdentity caller,
                @NonNull PolicyDefinition<StoredType> key,
                @PolicyScope int scope);

        /**
         * Helper method to get the effective per-user policy value from the {@code
         * DevicePolicyEngine}. Invoked from {@link
         * PolicyHandler.getResolvedPerUserPolicyUnchecked}.
         *
         * <p>Will use {@code caller} to decide on the correct storage.
         */
        @Nullable
        <StoredType> StoredType getResolvedPerUserPolicy(
                @UserIdInt int userId, @NonNull PolicyDefinition<StoredType> key);

        /**
         * Helper method to get the effective device-wide policy value from the {@code
         * DevicePolicyEngine}. Invoked from {@link
         * PolicyHandler.getResolvedDeviceWidePolicyUnchecked}.
         */
        @Nullable
        <T> T getResolvedDeviceWidePolicy(@NonNull PolicyDefinition<T> key);
    }
}
