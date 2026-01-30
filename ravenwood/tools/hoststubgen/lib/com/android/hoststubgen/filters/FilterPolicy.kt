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

private const val DEFAULT_POLICY_REASON = "default-by-options"

enum class FilterPolicy(val policyStringOrPrefix: String) {
    /**
     * Keep the marked item in the output jar file.
     *
     * - For classes and fields, it really only means "kept in the jar file", and is considered
     *   to be "supported". Even if a class has [Keep] but no members are kept, the class itself
     *   is still considered to be "supported".
     * - For methods, this means we keep the method body as-is (potentially with method call hooks).
     */
    Keep("keep"),

    /**
     * Only usable with classes. It does two things:
     * - Mark the class itself with [Keep].
     * - Make all its members default to [Keep] as well. Method-level annotations,
     *   text policies and other method-level filters can override individual members' policies.
     */
    KeepClass("keepclass"),

    /**
     * Only usable with classes. This is similar to [KeepClass] -- specifically, the class
     * will be treated as [Keep] -- but its members will default to [Experimental] instead of
     * [Keep].
     *
     * This policy _is_ considered to be supported. (See [isSupported] for why.)
     *
     * The text policy parser has a special case for this policy. If a package or a class
     * has an "experimental" policy, the parser will automatically convert it to
     * [ExperimentalClass]. So the label "experimentalclass" will not be used in the text policy
     * file.
     */
    ExperimentalClass("experimentalclass"),

    /**
     * Only usable with methods. Replace a method with a "substitution" method.
     *
     * This policy is considered to be supported.
     */
    Substitute("@"), // @ is a prefix

    /**
     * Only usable with methods. Redirect a method to a method in the substitution class.
     *
     * This policy is considered to be supported.
     */
    Redirect("redirect"),

    /**
     * Only usable with methods. The item will be kept in the impl jar file, but when called,
     * it'll throw.
     *
     * This policy is _not_ considered to be supported.
     */
    Throw("throw"),

    /**
     * Only usable with methods. The item will be kept in the impl jar file, but when called,
     * it'll no-op.
     *
     * This policy is _not_ considered to be supported.
     */
    Ignore("ignore"),

    /**
     * Remove the item completely.
     */
    Remove("remove"),

    /**
     * Special policy used for "partial annotation allowlisting". This policy must not be
     * used in the "main" filter chain. (which would be detected by [SanitizationFilter].)
     * It's used in a separate filter chain used by [AnnotationBasedFilter].
     */
    AnnotationAllowed("allow-annotation"),

    /**
     * Mark APIs as experimental. A new method call hook will be injected into methods so
     * these APIs can be toggled at runtime using environment variables.
     */
    Experimental("experimental");

    val needsInOutput: Boolean
        get() {
            return when (this) {
                Remove -> false
                else -> true
            }
        }

    /** Returns whether a policy can be used with classes */
    val isUsableWithClasses: Boolean
        get() {
            return when (this) {
                Keep, KeepClass, ExperimentalClass, Remove, AnnotationAllowed -> true
                // Note, classes can't have Experimental. Use ExperimentalClass instead.
                else -> false
            }
        }

    /** Returns whether a policy can be used with fields. */
    val isUsableWithFields: Boolean
        get() {
            return when (this) {
                // AnnotationAllowed isn't supported on fields (yet). We could support it if needed.
                Keep, Remove -> true
                else -> false
            }
        }

    /** Returns whether a policy can be used with methods */
    val isUsableWithMethods: Boolean
        get() {
            return when (this) {
                KeepClass -> false
                else -> true
            }
        }

    /** Returns whether a policy can be used as default policy. */
    val isUsableWithDefault: Boolean
        get() {
            return when (this) {
                Keep, Throw, Remove -> true
                else -> false
            }
        }

    /** Returns whether a policy is considered supported. */
    val isSupported: Boolean
        get() {
            return when (this) {
                Keep, KeepClass, Substitute, Redirect, AnnotationAllowed -> true

                // This one is confusing.
                // [ExperimentClass] can be only set on classes. If a class has
                // [ExperimentClass], then the class is cosndiered to have "Keep",
                // so we consider it "supported".
                //
                // When we "distribute" this policy to members, they will get [Experimental],
                // not [ExperimentClass], so such members will be considered "not supported".
                ExperimentalClass -> true
                else -> false
            }
        }

    /** Basically the same as "== KeepClass" (for now). */
    val isClassFullySupported: Boolean
        get() {
            return when (this) {
                KeepClass -> true
                else -> false
            }
        }

    val isMethodRewriteBody: Boolean
        get() {
            return when (this) {
                Redirect, Throw, Ignore -> true
                else -> false
            }
        }

    val isClassWide: Boolean
        get() {
            return when (this) {
                Remove, KeepClass, ExperimentalClass -> true
                else -> false
            }
        }

    /**
     * Internal policies must not be used in the main filter chain.
     */
    val isInternalPolicy: Boolean
        get() {
            return when (this) {
                AnnotationAllowed -> true
                else -> false
            }
        }

    val isExperimental: Boolean
        get() {
            return when (this) {
                // For the same reason ExperimentalClass is "supported",
                // ExperimentalClass itself is _not_ experimental.
                Experimental -> true
                else -> false
            }
        }

    /**
     * Convert KeepClass to Keep, or return itself.
     */
    fun resolveClassWidePolicy(): FilterPolicy {
        return when (this) {
            KeepClass -> Keep
            ExperimentalClass -> Experimental
            else -> this
        }
    }

    /**
     * Create a [FilterPolicyWithReason] with a given reason.
     */
    fun withReason(
        reason: String,
        statsLabelOverride: StatsLabel? = null,
        isDefault: Boolean = false
    ): FilterPolicyWithReason {
        return FilterPolicyWithReason(this, reason, statsLabelOverride = statsLabelOverride,
            isDefault = isDefault)
    }

    private fun ensureUsableWithDefault() {
        if (!this.isUsableWithDefault) {
            throw HostStubGenInternalException("ConstantFilter doesn't support $this.")
        }
    }

    /**
     * Convert a policy to be usable as a default class policy.
     */
    fun resolveDefaultForClass(): FilterPolicyWithReason {
        ensureUsableWithDefault()
        return when (this) {
            Throw -> Keep
            else -> this
        }.withReason(reason = DEFAULT_POLICY_REASON, isDefault = true)
    }

    /**
     * Convert a policy to be usable as a default field policy.
     */
    fun resolveDefaultForFields(): FilterPolicyWithReason {
        return resolveDefaultForClass()
    }

    /**
     * Convert a policy to be usable as a default method policy.
     */
    fun resolveDefaultForMethods(): FilterPolicyWithReason {
        ensureUsableWithDefault()
        return this.withReason(reason = DEFAULT_POLICY_REASON, isDefault = true)
    }
}
