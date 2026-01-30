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

import android.annotation.IntDef;
import android.processor.devicepolicy.BooleanPolicyDefinition;
import android.processor.devicepolicy.EnumPolicyDefinition;
import android.processor.devicepolicy.IntegerPolicyDefinition;
import android.processor.devicepolicy.PolicyDefinition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class PolicyIdentifier<T> {
    // We don't actually do anything with this.
    public PolicyIdentifier(String id) {
    }

    private static final String SIMPLE_BOOLEAN_POLICY_KEY = "simple_boolean_policy_key";

    /**
     * Test policy 1
     */
    @BooleanPolicyDefinition(
            base = @PolicyDefinition
    )
    public static final PolicyIdentifier<Boolean> SIMPLE_BOOLEAN_POLICY = new PolicyIdentifier<>(
            SIMPLE_BOOLEAN_POLICY_KEY);

    private static final String SIMPLE_ENUM_POLICY_KEY = "simple_enum_policy_key";

    /**
     * First entry
     */
    public static final int ENUM_ENTRY_1 = 0;

    /**
     * Second entry
     */
    public static final int ENUM_ENTRY_2 = 1;

    /**
     * Third entry
     */
    public static final int ENUM_ENTRY_3 = 2;

    /**
     * Enum for {@link SIMPLE_ENUM_POLICY}
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "ENUM_ENTRY_" }, value = {
            ENUM_ENTRY_1,
            ENUM_ENTRY_2,
            ENUM_ENTRY_3
    })
    public @interface SimpleEnumPolicyEnum {}

    /**
     * Test policy 2
     */
    @EnumPolicyDefinition(
            base = @PolicyDefinition,
            defaultValue = ENUM_ENTRY_2,
            intDef = SimpleEnumPolicyEnum.class
    )
    public static final PolicyIdentifier<Integer> SIMPLE_ENUM_POLICY = new PolicyIdentifier<>(
            SIMPLE_ENUM_POLICY_KEY);

    private static final String SIMPLE_INTEGER_POLICY_KEY = "simple_integer_policy_key";

    /**
     * Test policy 3
     */
    @IntegerPolicyDefinition(
            base = @PolicyDefinition
    )
    public static final PolicyIdentifier<Integer> SIMPLE_INTEGER_POLICY = new PolicyIdentifier<>(
            SIMPLE_INTEGER_POLICY_KEY);
}
