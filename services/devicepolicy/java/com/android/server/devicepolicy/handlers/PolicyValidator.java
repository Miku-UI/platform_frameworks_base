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
import android.app.admin.PackageIdentifier;
import android.app.admin.PolicySizeVerifier;
import android.app.admin.metadata.EnumPolicyMetadata;
import android.app.admin.metadata.IntegerPolicyMetadata;
import android.app.admin.metadata.ListPolicyMetadata;
import android.app.admin.metadata.LongPolicyMetadata;
import android.app.admin.metadata.PackagePolicyMetadata;
import android.app.admin.metadata.PolicyMetadata;
import android.app.admin.metadata.StringPolicyMetadata;
import android.content.pm.PackageParser;

import java.util.List;

/**
 * A PolicyValidator provides type specific functionality for validating the values of a policy.
 *
 * @param <T> The type of the policy value.
 */
public abstract class PolicyValidator<T> {

    private static final PolicyValidator<Integer> ENUM_POLICY_VALIDATOR =
            new PolicyValidator<Integer>() {
                @Override
                public void validate(
                        @NonNull Integer value, @NonNull PolicyMetadata<Integer> policy) {
                    // This validator is only used for `EnumPolicyMetadata`, so nobody should ever
                    // pass anything else in.
                    var enumPolicy = (EnumPolicyMetadata) policy;

                    if (!enumPolicy.getAllowedValues().contains(value)) {
                        throw new IllegalArgumentException(
                                "Unsupported value " + value + " for policy " + policy.getId());
                    }
                }
            };

    private static final PolicyValidator<Integer> INTEGER_POLICY_VALIDATOR =
            new PolicyValidator<Integer>() {
                @Override
                public void validate(
                        @NonNull Integer value, @NonNull PolicyMetadata<Integer> policy) {
                    var integerPolicy = (IntegerPolicyMetadata) policy;
                    if (value < integerPolicy.getMinValue()
                            || value > integerPolicy.getMaxValue()) {
                        throw new IllegalArgumentException(
                                "Unsupported Value "
                                        + value
                                        + " is not in the range ["
                                        + integerPolicy.getMinValue()
                                        + ", "
                                        + integerPolicy.getMaxValue()
                                        + "] for policy "
                                        + policy.getId());
                    }
                }
            };

    private static final PolicyValidator<Long> LONG_POLICY_VALIDATOR =
            new PolicyValidator<Long>() {
                @Override
                public void validate(@NonNull Long value, @NonNull PolicyMetadata<Long> policy) {
                    var longPolicy = (LongPolicyMetadata) policy;
                    if (value < longPolicy.getMinValue() || value > longPolicy.getMaxValue()) {
                        throw new IllegalArgumentException(
                                "Value "
                                        + value
                                        + " is not within range ["
                                        + longPolicy.getMinValue()
                                        + ", "
                                        + longPolicy.getMaxValue()
                                        + "] for policy "
                                        + policy.getId().getId());
                    }
                }
            };

    private static final PolicyValidator<String> STRING_POLICY_VALIDATOR =
            new PolicyValidator<String>() {
                @Override
                public void validate(
                        @NonNull String value, @NonNull PolicyMetadata<String> policy) {
                    var stringPolicy = (StringPolicyMetadata) policy;

                    if (!stringPolicy.isEmptyStringAllowed() && value.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Empty string is not allowed for policy " + policy.getId());
                    }

                    if (!stringPolicy.isUnprintableCharactersAllowed()
                            && value.codePoints().anyMatch(Character::isISOControl)) {
                        throw new IllegalArgumentException(
                                "Unprintable characters are not allowed for policy "
                                        + policy.getId()
                                        + ", the first unprintable character is "
                                        + value.codePoints()
                                                .filter(Character::isISOControl)
                                                .findFirst());
                    }

                    if (value.length() > stringPolicy.getMaxLength()) {
                        throw new IllegalArgumentException(
                                "The length of string is"
                                        + value.length()
                                        + ", which is longer than the maximum length "
                                        + stringPolicy.getMaxLength()
                                        + " for policy "
                                        + policy.getId());
                    }
                }
            };

    private static final PolicyValidator<PackageIdentifier> PACKAGE_POLICY_VALIDATOR =
            new PolicyValidator<>() {
                @Override
                public void validate(
                        @NonNull PackageIdentifier value,
                        @NonNull PolicyMetadata<PackageIdentifier> policy) {
                    String packageName = value.getPackageName();
                    if (packageName.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Package name is empty for policy " + policy.getId());
                    }

                    PolicySizeVerifier.enforceMaxPackageNameLength(packageName);

                    String validationError =
                            PackageParser.validateName(
                                    packageName,
                                    /* requireSeparator= */ true,
                                    /* requireFilename= */ false);

                    if (validationError != null) {
                        throw new IllegalArgumentException(
                                "Package name "
                                        + packageName
                                        + " is not valid for policy "
                                        + policy.getId()
                                        + ", error: "
                                        + validationError);
                    }
                }
            };

    /** Returns a validator that can handle values of the given policy. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> PolicyValidator<T> getInstance(PolicyMetadata<T> policy) {
        return (PolicyValidator<T>)
                switch (policy) {
                    case EnumPolicyMetadata e -> ENUM_POLICY_VALIDATOR;
                    case IntegerPolicyMetadata i -> INTEGER_POLICY_VALIDATOR;
                    case StringPolicyMetadata s -> STRING_POLICY_VALIDATOR;
                    case LongPolicyMetadata l -> LONG_POLICY_VALIDATOR;
                    case PackagePolicyMetadata p -> PACKAGE_POLICY_VALIDATOR;
                    // Need to use a raw type here since we can't extract the element E of
                    // T=List<E>.
                    case ListPolicyMetadata l -> getListInstance(l);
                    default ->
                            throw new UnsupportedOperationException(
                                    "Unsupported policy: " + policy.getId().getId());
                };
    }

    private static final PolicyValidator<List<String>> LIST_OF_STRING_POLICY_VALIDATOR =
            new ListPolicyValidator<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> PolicyValidator<List<T>> getListInstance(ListPolicyMetadata<T> listPolicy) {
        // Cast is safe since metadata already checked the type when building.
        // Can't cast to PolicyValidator<List<T>> since List<T> is not a superclass of List<String>
        // (and other list types at the same time), we need to use a raw class instead.
        return (PolicyValidator)
                switch (listPolicy.getElementMetadata()) {
                    case StringPolicyMetadata s -> LIST_OF_STRING_POLICY_VALIDATOR;
                    default ->
                            throw new UnsupportedOperationException(
                                    "Unsupported list policy: " + listPolicy.getId().getId());
                };
    }

    /**
     * Validate the given value, using the information available in the {@code PolicyMetadata}.
     *
     * @throws IllegalArgumentException if the value is invalid.
     */
    public abstract void validate(@NonNull T value, @NonNull PolicyMetadata<T> policy);

    private static class ListPolicyValidator<T> extends PolicyValidator<List<T>> {
        @Override
        public void validate(@NonNull List<T> value, @NonNull PolicyMetadata<List<T>> policy) {
            // This validator is only used for `ListPolicyMetadata`, so nobody should ever
            // pass anything else in.
            var listPolicy = (ListPolicyMetadata) policy;

            if (value.isEmpty() && !listPolicy.isEmptyListAllowed()) {
                throw new IllegalArgumentException(
                        "Empty list is not allowed for policy " + policy.getId().getId());
            }

            var elementValidator = getInstance(listPolicy.getElementMetadata());
            for (T element : value) {
                elementValidator.validate(element, listPolicy.getElementMetadata());
            }
        }
    }
}
