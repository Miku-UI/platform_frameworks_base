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

package android.processor.devicepolicy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Define a policy.
 * <p>
 * Can only be applied to fields inside {@link android.app.admin.PolicyIdentifier} by nesting it in
 * a type specific annotation such as {@link EnumPolicyDefinition}.
 * The field must be static final and have a type of {@link android.app.admin.PolicyIdentifier}.
 * </p>
 *
 *  The name of the policy is taken from the field name.
 *  The type is the parameter passed to {@link android.app.admin.PolicyIdentifier}.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface PolicyDefinition {
}
