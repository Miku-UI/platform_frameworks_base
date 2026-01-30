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

import com.android.hoststubgen.addNonNullElement
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.toHumanReadableClassName
import com.android.hoststubgen.asm.toHumanReadableMethodName
import com.android.hoststubgen.asm.toJvmClassName
import com.android.hoststubgen.log
import com.android.hoststubgen.utils.Trie

// TODO: Validate all input names.

/**
 * [InMemoryOutputFilter] basically handles the "text file policies", which handles
 * package, class, field and method policies.
 *
 * This filter basically takes precedence over other filters such as [AnnotationBasedFilter],
 * with the following exceptions.
 *
 * - This will _not_ override more narrowly scoped policies from other filters.
 *   For example, in the following case:
 *
 *   Class definition:
 *     @KeepPartialClass
 *     class C {
 *         @Throw
 *         void foo() {}
 *
 *         void bar() {}
 *     }
 *
 *   Policy:
 *     class C keepclass
 *
 *   The C will have "KeepClass" and method "bar()" will get Keep. However, the text policy
 *   will not override foo()'s annotation, so the method will still throw.
 *
 *   In order to "keep" foo() as well, set an explicit policy on the method, like so:
 *
 *   Policy:
 *     class C keepclass
 *       method foo keep
 *
 * - Similarly, package policies will never override class-level annotations, or class-level
 *   text policies.
 *
 * - "Experimental" text policy will never override "supported" policy.
 *   For example, in the following case:
 *
 *     @KeepPartialClass
 *     class C {
 *         @Keep
 *         void foo() {}
 *
 *         void bar() {}
 *
 *         @Ignore
 *         void baz() {}
 *     }
 *
 *   Policy:
 *     class C
 *       method foo experimental
 *
 *   foo() will just be "Kept", not made "experimental", even though the text policy is set on the
 *   same scope as the annotation (== method).
 *
 *   A more complicated example -- with the same class, if you have the following text policy:
 *
 *   Policy:
 *     class C experimental # Mark the entire class as "experimental"
 *
 *  the result will be:
 *     @KeepPartialClass // class is "supported", so it's still "supported".
 *     class C {
 *         @Keep
 *         void foo() {}
 *
 *         // This will be marked as "experimental" from the text policy file.
 *         void bar() {}
 *
 *         // This policy is "unsupported" policy, but the class-wide experimental policy won't
 *         // override it.
 *         @Ignore
 *         void baz() {}
 *     }
 *
 *   If you want to make baz() as experimental, add en explicit method policy:
 *
 *     class C experimental # Mark the entire class as "experimental"
 *       method baz experimental # buz() will be "experimental, not "ignore".
 */
class InMemoryOutputFilter(
    private val classes: ClassNodes,
    fallback: OutputFilter,
) : DelegatingFilter(fallback) {
    private val mPolicies = mutableMapOf<String, FilterPolicyWithReason>()
    private val mRenames = mutableMapOf<String, String>()
    private val mRedirectionClasses = mutableMapOf<String, String>()
    private val mClassLoadHooks = mutableMapOf<String, String>()
    private val mMethodCallReplaceSpecs = mutableListOf<MethodCallReplaceSpec>()
    private val mTypeRenameSpecs = mutableListOf<TypeRenameSpec>()
    private val mPackagePolicies = PackagePolicyTrie()

    // We want to pick the most specific filter for a package name.
    // Since any package with a matching prefix is a valid match, we can use a prefix tree
    // to help us find the nearest matching filter.
    private class PackagePolicyTrie : Trie<String, String, FilterPolicyWithReason>() {
        // Split package name into individual component
        override fun splitToComponents(key: String): Iterator<String> {
            return key.split('.').iterator()
        }
    }

    private fun getPackageKey(packageName: String): String {
        return packageName.toHumanReadableClassName()
    }

    private fun getPackageKeyFromClass(className: String): String {
        val clazz = className.toHumanReadableClassName()
        val idx = clazz.lastIndexOf('.')
        return if (idx >= 0) clazz.substring(0, idx) else ""
    }

    private fun getClassKey(className: String): String {
        return className.toHumanReadableClassName()
    }

    private fun getFieldKey(className: String, fieldName: String): String {
        return getClassKey(className) + "." + fieldName
    }

    private fun getMethodKey(className: String, methodName: String, signature: String): String {
        return getClassKey(className) + "." + methodName + ";" + signature
    }

    private fun checkClass(className: String) {
        if (classes.findClass(className) == null) {
            log.w("Unknown class $className")
        }
    }

    private fun checkField(className: String, fieldName: String) {
        if (classes.findField(className, fieldName) == null) {
            log.w("Unknown field $className.$fieldName")
        }
    }

    private fun checkMethod(
        className: String,
        methodName: String,
        descriptor: String
    ) {
        if (descriptor == "*") {
            return
        }
        if (classes.findMethod(className, methodName, descriptor) == null) {
            log.w("Unknown method $className.$methodName$descriptor")
        }
    }

    override fun getPolicyForClass(className: String): FilterPolicyWithReason {
        val inMemoryClassPolicy = mPolicies[getClassKey(className)]
        // If the in-memory policy is set and is _not_ experimental, use it.
        if (inMemoryClassPolicy != null && !inMemoryClassPolicy.policy.isExperimental) {
            return inMemoryClassPolicy
        }
        // Now, the in-memory class policy is either null or experimental.

        // If the class is "fully"-supported, use it.
        val fallback = super.getPolicyForClass(className)
        if (fallback.policy.isClassFullySupported) {
            return fallback
        }

        // Otherwise, if the in-memory clsas policy is set -- so it must be "experimental"
        // at this point -- use it.
        if (inMemoryClassPolicy != null) {
            return inMemoryClassPolicy
        }

        // Otherwise, if a fallback is not from the default one, use it.
        if (!fallback.isDefault) {
            return fallback
        }

        // Lastly, see if we have a package policy, and if so, use it.
        val parentPolicy = mPackagePolicies[getPackageKeyFromClass(className)]
        if (parentPolicy != null) {
            return parentPolicy
        }

        // Return whatever returned by fallback. (which should be at this point "Unspecified".)
        return fallback
    }

    fun setPolicyForClass(className: String, policy: FilterPolicyWithReason) {
        checkClass(className)
        mPolicies[getClassKey(className)] = policy
    }

    fun setPolicyForPackage(packageName: String, policy: FilterPolicyWithReason) {
        mPackagePolicies[getPackageKey(packageName)] = policy
    }

    private fun getMemberPolicy(
        inMemoryPolicy: FilterPolicyWithReason?,
        fallbackFetcher: () -> FilterPolicyWithReason,
        ): FilterPolicyWithReason {

        // Similar to getPolicyForClass().
        // In-memory-policy should take precedence, but if it's "experimental", it can
        // override an "unsupported" fallback policy.
        if (inMemoryPolicy != null && !inMemoryPolicy.policy.isExperimental) {
            return inMemoryPolicy
        }
        val fallback = fallbackFetcher()
        if (fallback.policy.isSupported) {
            return fallback
        }
        if (inMemoryPolicy != null) {
            return inMemoryPolicy
        }
        return fallback
    }

    override fun getPolicyForField(className: String, fieldName: String): FilterPolicyWithReason {
        return getMemberPolicy(mPolicies[getFieldKey(className, fieldName)]) {
            super.getPolicyForField(className, fieldName)
        }
    }

    fun setPolicyForField(className: String, fieldName: String, policy: FilterPolicyWithReason) {
        checkField(className, fieldName)
        mPolicies[getFieldKey(className, fieldName)] = policy
    }

    override fun getPolicyForMethod(
        className: String,
        methodName: String,
        descriptor: String,
    ): FilterPolicyWithReason {
        val policy = mPolicies[getMethodKey(className, methodName, descriptor)]
            ?: mPolicies[getMethodKey(className, methodName, "*")]

        return getMemberPolicy(policy) {
            super.getPolicyForMethod(className, methodName, descriptor)
        }
    }

    fun setPolicyForMethod(
        className: String,
        methodName: String,
        descriptor: String,
        policy: FilterPolicyWithReason,
    ) {
        checkMethod(className, methodName, descriptor)
        mPolicies[getMethodKey(className, methodName, descriptor)] = policy
    }

    override fun getRenameTo(className: String, methodName: String, descriptor: String): String? {
        return mRenames[getMethodKey(className, methodName, descriptor)]
            ?: mRenames[getMethodKey(className, methodName, "*")]
            ?: super.getRenameTo(className, methodName, descriptor)
    }

    fun setRenameTo(className: String, methodName: String, descriptor: String, toName: String) {
        checkMethod(className, methodName, descriptor)
        checkMethod(className, toName, descriptor)
        mRenames[getMethodKey(className, methodName, descriptor)] = toName
    }

    override fun getRedirectionClass(className: String): String? {
        return mRedirectionClasses[getClassKey(className)]
            ?: super.getRedirectionClass(className)
    }

    fun setRedirectionClass(from: String, to: String) {
        checkClass(from)

        // Redirection classes may be provided from other jars, so we can't do this check.
        // ensureClassExists(to)
        mRedirectionClasses[getClassKey(from)] = to.toJvmClassName()
    }

    override fun getClassLoadHooks(className: String): List<String> {
        return addNonNullElement(
            super.getClassLoadHooks(className),
            mClassLoadHooks[getClassKey(className)]
        )
    }

    fun setClassLoadHook(className: String, methodName: String) {
        mClassLoadHooks[getClassKey(className)] = methodName.toHumanReadableMethodName()
    }

    override fun hasAnyMethodCallReplace(): Boolean {
        return mMethodCallReplaceSpecs.isNotEmpty() || super.hasAnyMethodCallReplace()
    }

    override fun getMethodCallReplaceTo(
        className: String,
        methodName: String,
        descriptor: String,
    ): MethodReplaceTarget? {
        // Maybe use 'Tri' if we end up having too many replacements.
        mMethodCallReplaceSpecs.forEach {
            if (className == it.fromClass &&
                methodName == it.fromMethod
            ) {
                if (it.fromDescriptor == "*" || descriptor == it.fromDescriptor) {
                    return MethodReplaceTarget(it.toClass, it.toMethod)
                }
            }
        }
        return super.getMethodCallReplaceTo(className, methodName, descriptor)
    }

    fun setMethodCallReplaceSpec(spec: MethodCallReplaceSpec) {
        mMethodCallReplaceSpecs.add(spec)
    }

    override fun remapType(className: String): String? {
        mTypeRenameSpecs.forEach {
            if (it.typeInternalNamePattern.matcher(className).matches()) {
                return it.typeInternalNamePrefix + className
            }
        }
        return super.remapType(className)
    }

    fun setRemapTypeSpec(spec: TypeRenameSpec) {
        mTypeRenameSpecs.add(spec)
    }
}
