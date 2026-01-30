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

package android.processor.devicepolicy

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic

abstract class Processor<T : Annotation>(protected val processingEnv: ProcessingEnvironment) {
    private companion object {
        const val POLICY_IDENTIFIER = "android.app.admin.PolicyIdentifier"
    }

    val policyIdentifierElem =
        processingEnv.elementUtils.getTypeElement(POLICY_IDENTIFIER) ?: throw IllegalStateException(
            "Could not find $POLICY_IDENTIFIER"
        )

    /** Represents a android.app.admin.PolicyIdentifier<T> */
    val policyIdentifierType = policyIdentifierElem.asType()
        ?: throw IllegalStateException("Could not get type of $POLICY_IDENTIFIER")

    /** Represents a android.app.admin.PolicyIdentifier<?> */
    val genericPolicyIdentifierType = processingEnv.typeUtils.getDeclaredType(
        policyIdentifierElem, processingEnv.typeUtils.getWildcardType(null, null)
    ) ?: throw IllegalStateException("Could not get generic type of $POLICY_IDENTIFIER")


    /**
     * Given an element that represents a PolicyIdentifier field, get the type of the policy.
     */
    protected fun policyType(element: Element): TypeMirror {
        val elementType = element.asType() as DeclaredType

        if (elementType.typeArguments.size != 1) {
            printError(
                element, "Only expected 1 type parameter in $elementType"
            )

            throw IllegalArgumentException("Element $element is not a policy")
        }

        return elementType.typeArguments[0]
    }

    /**
     * Print an error and make compilation fail.
     */
    protected fun printError(element: Element, message: String) {
        processingEnv.messager.printMessage(
            Diagnostic.Kind.ERROR,
            message,
            element,
        )
    }

    /**
     * Process policy metadata into a {@link (PolicyMetadata, DevicePolicyDefinition)}.
     *
     * Errors must be reported using {@link printError} to the user and processing should
     * continue for as long as possible.
     *
     * @return Policy metadata or null when metadata can not be obtained.
     */
    abstract fun processMetadata(element: Element): Pair<PolicyMetadata, PolicyDefinition>?

    /**
     * Get the class of the annotation for this processor.
     */
    abstract fun annotationClass(): Class<T>

    /**
     * Process element into a Policy.
     *
     * Errors are reported using {@link printError} and will fail compilation, but the processor
     * will try to process as much as possible.
     *
     * @return All relevant policy data or null if this can not be obtained.
     */
    fun process(element: Element): Policy? {
        if (!isElementValid(element)) {
            return null
        }

        val (metadata, policyDefinition) = processMetadata(element) ?: return null

        return loadPolicyDefinition(element, policyDefinition, metadata)
    }

    private fun isElementValid(element: Element): Boolean {
        var valid = true

        if (element.kind != ElementKind.FIELD) {
            printError(element, "@PolicyDefinition can only be applied to fields")
            valid = false
        }

        val elementType = element.asType() as DeclaredType
        val enclosingType = element.enclosingElement.asType()

        if (!processingEnv.typeUtils.isAssignable(elementType, genericPolicyIdentifierType)) {
            printError(
                element,
                "@PolicyDefinition can only be applied to $policyIdentifierType, it was applied to $elementType."
            )

            // Stop validating, we depend on the type next.
            return false
        }

        if (elementType.typeArguments.size != 1) {
            printError(
                element, "Only expected 1 type parameter in $elementType"
            )
            valid = false
        }

        // Temporary check until the API is rolled out. Later other module should be able to use @PolicyDefinition.
        if (!processingEnv.typeUtils.isAssignable(enclosingType, genericPolicyIdentifierType)) {
            printError(
                element,
                "@PolicyDefinition can only be applied to fields in $policyIdentifierType, it was applied to a field in $enclosingType."
            )

            valid = false
        }

        return valid
    }

    private fun loadPolicyDefinition(
        element: Element, definition: PolicyDefinition, metadata: PolicyMetadata
    ): Policy {
        val enclosingType = element.enclosingElement.asType()

        val name = "$enclosingType.$element"
        val type = policyType(element).toString()
        val documentation = processingEnv.elementUtils.getDocComment(element) ?: ""

        if (documentation.trim().isEmpty()) {
            printError(element, "Missing JavaDoc")
        }

        return Policy(name, type, documentation, metadata)
    }
}