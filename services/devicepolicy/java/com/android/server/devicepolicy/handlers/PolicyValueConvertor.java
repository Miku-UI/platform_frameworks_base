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

import android.annotation.NonNull;
import android.app.admin.IntegerPolicyValue;
import android.app.admin.ListOfStringPolicyValue;
import android.app.admin.LongPolicyValue;
import android.app.admin.PackageIdentifier;
import android.app.admin.PackagePolicyValue;
import android.app.admin.PolicyValue;
import android.app.admin.StringPolicyValue;
import android.app.admin.metadata.EnumPolicyMetadata;
import android.app.admin.metadata.IntegerPolicyMetadata;
import android.app.admin.metadata.ListPolicyMetadata;
import android.app.admin.metadata.LongPolicyMetadata;
import android.app.admin.metadata.PackagePolicyMetadata;
import android.app.admin.metadata.PolicyMetadata;
import android.app.admin.metadata.StringPolicyMetadata;

import java.util.List;

/**
 * A PolicyValueConvertor provides type specific functionality for converting policy values to
 * {@link PolicyValue} instances.
 *
 * @param <T> The value kind this convertor works on.
 */
public abstract class PolicyValueConvertor<T> {

    private static final PolicyValueConvertor<Integer> INTEGER_POLICY_VALUE_CONVERTOR =
            new PolicyValueConvertor<>() {
                @Override
                public @NonNull PolicyValue<Integer> toPolicyValue(@NonNull Integer value) {
                    return new IntegerPolicyValue(value);
                }
            };

    private static final PolicyValueConvertor<Long> LONG_POLICY_VALUE_CONVERTOR =
            new PolicyValueConvertor<>() {
                @Override
                public @NonNull PolicyValue<Long> toPolicyValue(@NonNull Long value) {
                    return new LongPolicyValue(value);
                }
            };

    private static final PolicyValueConvertor<String> STRING_POLICY_VALUE_CONVERTOR =
            new PolicyValueConvertor<>() {
                @Override
                public @NonNull PolicyValue<String> toPolicyValue(@NonNull String value) {
                    return new StringPolicyValue(value);
                }
            };

    private static final PolicyValueConvertor<PackageIdentifier> PACKAGE_POLICY_VALUE_CONVERTOR =
            new PolicyValueConvertor<>() {
                @Override
                public @NonNull PolicyValue<PackageIdentifier> toPolicyValue(
                        @NonNull PackageIdentifier value) {
                    return new PackagePolicyValue(value);
                }
            };

    private static final PolicyValueConvertor<List<String>> LIST_OF_STRING_POLICY_VALUE_CONVERTOR =
            new PolicyValueConvertor<>() {
                @Override
                public @NonNull PolicyValue<List<String>> toPolicyValue(
                        @NonNull List<String> value) {
                    return new ListOfStringPolicyValue(value);
                }
            };

    /**
     * Gets a convertor that can handle the given policy.
     *
     * @param metadata The metadata for the policy.
     * @param <T> The type of the policy.
     * @return The convertor.
     */
    @NonNull
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> PolicyValueConvertor<T> getInstance(PolicyMetadata<T> metadata) {
        return (PolicyValueConvertor<T>)
                switch (metadata) {
                    case EnumPolicyMetadata e -> INTEGER_POLICY_VALUE_CONVERTOR;
                    case IntegerPolicyMetadata i -> INTEGER_POLICY_VALUE_CONVERTOR;
                    case StringPolicyMetadata e -> STRING_POLICY_VALUE_CONVERTOR;
                    case LongPolicyMetadata e -> LONG_POLICY_VALUE_CONVERTOR;
                    case PackagePolicyMetadata e -> PACKAGE_POLICY_VALUE_CONVERTOR;
                    case ListPolicyMetadata l -> getListInstance(l);
                    default ->
                            throw new UnsupportedOperationException(
                                    "Unsupported policy: " + metadata.getId());
                };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> PolicyValueConvertor<List<T>> getListInstance(
            ListPolicyMetadata<T> listPolicy) {
        // Cast is safe since metadata already checked the type when building.
        // Can't cast to PolicyValidator<List<T>> since List<T> is not a superclass of
        // List<String> (and other list types at the same time), we need to use a raw class
        // instead.
        return (PolicyValueConvertor)
                switch (listPolicy.getElementMetadata()) {
                    case StringPolicyMetadata s -> LIST_OF_STRING_POLICY_VALUE_CONVERTOR;
                    default ->
                            throw new UnsupportedOperationException(
                                    "Unsupported list policy: " + listPolicy.getId().getId());
                };
    }

    /** Converts a value to its corresponding {@link PolicyValue}. */
    public abstract @NonNull PolicyValue<T> toPolicyValue(@NonNull T value);

    /** Converts a {@link PolicyValue} to its corresponding value. */
    public @NonNull T fromPolicyValue(@NonNull PolicyValue<T> value) {
        return value.getValue();
    }
}
