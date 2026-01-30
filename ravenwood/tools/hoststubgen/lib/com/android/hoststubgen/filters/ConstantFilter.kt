/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen.filters

import com.android.hoststubgen.HostStubGenInternalException


/**
 * [OutputFilter] with a given policy. Used to represent the default policy.
 *
 * This is used as the last fallback filter.
 */
class ConstantFilter(
    private val classPolicy: FilterPolicyWithReason,
    private val fieldPolicy: FilterPolicyWithReason = classPolicy,
    private val methodPolicy: FilterPolicyWithReason = classPolicy,
) : OutputFilter() {
    init {
        if (!classPolicy.policy.isUsableWithClasses) {
            throw HostStubGenInternalException("${classPolicy.policy} can't be used for classes")
        }
        if (!fieldPolicy.policy.isUsableWithFields) {
            throw HostStubGenInternalException("${fieldPolicy.policy} can't be used for fields")
        }
        if (!methodPolicy.policy.isUsableWithMethods) {
            throw HostStubGenInternalException("${methodPolicy.policy} can't be used for methods")
        }
    }

    override fun getPolicyForClass(className: String): FilterPolicyWithReason {
        return classPolicy
    }

    override fun getPolicyForField(className: String, fieldName: String): FilterPolicyWithReason {
        return fieldPolicy
    }

    override fun getPolicyForMethod(
        className: String,
        methodName: String,
        descriptor: String,
    ): FilterPolicyWithReason {
        return methodPolicy
    }
}
