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

package com.android.server.devicepolicy.handlers;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.BooleanPolicyValue;
import android.app.admin.PolicyIdentifier;
import android.app.admin.metadata.EnumPolicyMetadata;
import android.app.admin.metadata.PolicyMetadata;

import com.android.server.devicepolicy.CallerIdentity;
import com.android.server.devicepolicy.PolicyDefinition;

import java.util.Set;

/**
 * PolicyHandler for a policy that is modeled as an enum in the policy annotations but which is
 * stored as a boolean in the {@code DevicePolicyEngine}.
 *
 * <p>Used for preexisting policies that were already stored as booleans.
 *
 * <p>New policies should *not* use this class and should simply store their policy values as an
 * enum inside {@code DevicePolicyEngine}.
 */
public class EnumStoredAsBooleanPolicyHandler extends PolicyHandler<Integer> {

    private final PolicyDefinition<Boolean> mPolicyDefinition;
    private final int mTrueValue;
    private final int mFalseValue;

    public EnumStoredAsBooleanPolicyHandler(
            @NonNull PolicyIdentifier<Integer> identifier,
            @NonNull PolicyDefinition<Boolean> definition,
            int trueValue,
            int falseValue) {
        super(identifier);
        mPolicyDefinition = definition;
        mTrueValue = trueValue;
        mFalseValue = falseValue;
    }

    @Override
    protected void storePolicyValue(CallerIdentity caller, int scope, Integer value) {
        if (value == null) {
            clearPolicy(caller, mPolicyDefinition, scope);
        } else {
            boolean booleanValue = enumToBoolean(value);
            storePolicy(caller, mPolicyDefinition, scope, new BooleanPolicyValue(booleanValue));
        }
    }

    @Override
    protected Integer getPolicyValue(CallerIdentity caller, int scope) {
        Boolean booleanValue = getPolicySetByAdmin(caller, mPolicyDefinition, scope);
        return boxedBooleanToEnum(booleanValue);
    }

    @Override
    protected Integer getResolvedPerUserPolicyValue(int userId) {
        Boolean booleanValue = getDelegate().getResolvedPerUserPolicy(userId, mPolicyDefinition);
        return boxedBooleanToEnum(booleanValue);
    }

    @Override
    protected Integer getResolvedDeviceWidePolicyValue() {
        Boolean booleanValue = getDelegate().getResolvedDeviceWidePolicy(mPolicyDefinition);
        return boxedBooleanToEnum(booleanValue);
    }

    @Override
    public void initialize(
            @NonNull Delegate delegate,
            @Nullable PolicyDefinition<Integer> definition,
            @NonNull PolicyMetadata<Integer> metadata) {
        super.initialize(delegate, definition, metadata);

        checkAllowedValuesMatchGivenTrueAndFalseValues();
        checkPolicyDefinitionFactoryIsUpdated(definition);
    }

    private Integer boxedBooleanToEnum(Boolean value) {
        if (value == null) {
            return null;
        } else if (value) {
            return mTrueValue;
        } else {
            return mFalseValue;
        }
    }

    private boolean enumToBoolean(int value) {
        return (value == mTrueValue);
    }

    private void checkAllowedValuesMatchGivenTrueAndFalseValues() {
        var enumPolicy = (EnumPolicyMetadata) getPolicyMetadata();
        var expectedValues = Set.of(mTrueValue, mFalseValue);
        if (!enumPolicy.getAllowedValues().equals(expectedValues)) {
            throw new IllegalStateException(
                    "Policy "
                            + getKey()
                            + " should only accept the values passed into the "
                            + "constructor of `EnumStoredAsBooleanPolicyHandler` (which are "
                            + expectedValues
                            + "), but the policy actually accepts "
                            + enumPolicy.getAllowedValues());
        }
    }

    private void checkPolicyDefinitionFactoryIsUpdated(
            @Nullable PolicyDefinition<Integer> generatedDefinition) {
        if (generatedDefinition != null) {
            throw new IllegalStateException(
                    "When using `EnumStoredAsBooleanPolicyHandler` you must update "
                            + "PolicyDefinitionFactory to return `null` for your key ("
                            + getKey()
                            + ")");
        }
    }
}
