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

import android.annotation.FlaggedApi
import android.processor.devicepolicy.protos.FullyQualifiedClassName
import android.processor.devicepolicy.protos.FullyQualifiedFieldName
import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import com.sun.source.tree.LiteralTree
import com.sun.source.tree.NewClassTree
import com.sun.source.tree.VariableTree
import com.sun.source.util.Trees
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic

abstract class PolicyProcessor<T : Annotation>(protected val processingEnv: ProcessingEnvironment) {
    private companion object {
        const val POLICY_IDENTIFIER = "android.app.admin.PolicyIdentifier"
    }

    val policyIdentifierElem =
        processingEnv.elementUtils.getTypeElement(POLICY_IDENTIFIER)
            ?: throw IllegalStateException("Could not find $POLICY_IDENTIFIER")

    /** Represents a android.app.admin.PolicyIdentifier<T> */
    val policyIdentifierType =
        policyIdentifierElem.asType()
            ?: throw IllegalStateException("Could not get type of $POLICY_IDENTIFIER")

    /** Represents a android.app.admin.PolicyIdentifier<?> */
    val genericPolicyIdentifierType =
        processingEnv.typeUtils.getDeclaredType(
            policyIdentifierElem,
            processingEnv.typeUtils.getWildcardType(null, null),
        ) ?: throw IllegalStateException("Could not get generic type of $POLICY_IDENTIFIER")

    /** Given an element that represents a PolicyIdentifier field, get the type of the policy. */
    protected fun policyType(element: Element): TypeMirror {
        val elementType = element.asType() as DeclaredType

        if (elementType.typeArguments.size != 1) {
            printError(element, "Only expected 1 type parameter in $elementType")

            throw IllegalArgumentException("Element $element is not a policy")
        }

        return elementType.typeArguments[0]
    }

    /** Print an error and make compilation fail. */
    protected fun printError(element: Element, message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message, element)
    }

    /**
     * Process policy metadata into a {@link (TypeSpecificPolicyMetadata, PolicyDefinition)}.
     *
     * Errors must be reported using {@link printError} to the user and processing should continue
     * for as long as possible.
     *
     * @return Policy metadata or null when metadata can not be obtained.
     */
    abstract fun processMetadata(
        element: Element
    ): Pair<TypeSpecificPolicyMetadata, PolicyDefinition>?

    /** Get the class of the annotation for this processor. */
    abstract fun annotationClass(): Class<T>

    /**
     * Process element into a Policy.
     *
     * Errors are reported using {@link printError} and will fail compilation, but the processor
     * will try to process as much as possible.
     *
     * @return All relevant policy data or null if this can not be obtained.
     */
    fun process(element: Element): PolicyMetadata? {
        if (!isElementValid(element)) {
            return null
        }

        checkPolicyFieldStructure(element)

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
                "@PolicyDefinition can only be applied to $policyIdentifierType, it was applied to $elementType.",
            )

            // Stop validating, we depend on the type next.
            return false
        }

        if (elementType.typeArguments.size != 1) {
            printError(element, "Only expected 1 type parameter in $elementType")
            valid = false
        }

        // Temporary check until the API is rolled out. Later other module should be able to use
        // @PolicyDefinition.
        if (!processingEnv.typeUtils.isAssignable(enclosingType, genericPolicyIdentifierType)) {
            printError(
                element,
                "@PolicyDefinition can only be applied to fields in $policyIdentifierType, it was applied to a field in $enclosingType.",
            )

            valid = false
        }

        return valid
    }

    /**
     * Make sure that policy fields look like: {@code private static final POLICY_NAME = new
     * PolicyIdentifier<>("POLICY_NAME"); }
     */
    private fun checkPolicyFieldStructure(element: Element) {
        checkPolicyFieldModifiers(element)

        val expectedName = element.simpleName
        val expectedInitializer = "'new PolicyIdentifier<>($expectedName)'"

        fun error(cause: String) {
            printError(element, "Policy must be initialized to $expectedInitializer: $cause.")
        }

        val trees = Trees.instance(processingEnv)

        val tree = trees.getTree(element)
        if (tree !is VariableTree) {
            throw IllegalStateException("Element $element Tree $tree is not an assignment")
        }

        val initializer = tree.initializer
        if (initializer !is NewClassTree) {
            error("initializer is not a call to new")
            return
        }

        if (initializer.identifier.toString() != "PolicyIdentifier<>") {
            error("found type ${initializer.identifier} instead")
            return
        }

        check(initializer.arguments.size == 1)
        val argument = initializer.arguments[0]

        if (argument !is LiteralTree) {
            error("the argument to the constructor is not a literal, found $argument")
            return
        }

        val value = argument.value
        if (value !is String) {
            error("the argument to the constructor is not a string, found $value")
            return
        }

        if (value != element.simpleName.toString()) {
            error("the argument to the constructor should be \"$expectedName\", found $value")
            return
        }
    }

    private fun checkPolicyFieldModifiers(element: Element) {
        if (!element.modifiers.contains(Modifier.STATIC)) {
            printError(element, "Field must be static")
        }

        if (!element.modifiers.contains(Modifier.FINAL)) {
            printError(element, "Field must be final")
        }

        if (!element.modifiers.contains(Modifier.PUBLIC)) {
            printError(element, "Field must be public")
        }
    }

    private fun getFullyQualifiedClassName(element: Element): FullyQualifiedClassName {
        if (element !is TypeElement) {
            throw IllegalArgumentException("Element $element is not a type element")
        }

        val packageName = processingEnv.elementUtils.getPackageOf(element).qualifiedName.toString()
        val className = element.qualifiedName.toString().removePrefix("$packageName.")

        return FullyQualifiedClassName.newBuilder()
            .setClassName(className)
            .setPackageName(packageName)
            .build()
    }

    protected fun getFullyQualifiedFieldName(element: Element): FullyQualifiedFieldName {
        val fieldName = element.simpleName.toString()
        val className = getFullyQualifiedClassName(element.enclosingElement)

        return FullyQualifiedFieldName.newBuilder()
            .setFieldName(fieldName)
            .setClassName(className.className)
            .setPackageName(className.packageName)
            .build()
    }

    private fun classTypeMirrorToName(type: TypeMirror): FullyQualifiedClassName {
        val element = processingEnv.typeUtils.asElement(type)

        val packageName = processingEnv.elementUtils.getPackageOf(element).qualifiedName.toString()
        val className = type.toString().removePrefix("$packageName.")

        return FullyQualifiedClassName.newBuilder()
            .setClassName(className)
            .setPackageName(packageName)
            .build()
    }

    private fun loadPolicyDefinition(
        element: Element,
        definition: PolicyDefinition,
        typeSpecificMetadata: TypeSpecificPolicyMetadata,
    ): PolicyMetadata? {
        val identifier = getFullyQualifiedFieldName(element)

        val type = classTypeMirrorToName(policyType(element))
        val documentation =
            processingEnv.elementUtils.getDocComment(element)?.trimIndent()?.trim() ?: ""
        val allowedScopes = convertScopes(element, definition.allowedScopes.toList())
        val affectedResource =
            convertResourceType(element, definition.affectedResource) ?: return null
        val allowedDpcTypes = convertDpcTypes(element, definition.allowedDpcTypes)
        val flaggedApi = readFlaggedApi(element)

        if (documentation.isEmpty()) {
            printError(element, "Missing JavaDoc")
        }

        val requiredPermission = definition.requiredPermission
        val requiredCrossUserPermission = definition.requiredCrossUserPermission

        val builder =
            PolicyMetadata.newBuilder()
                .setIdentifier(identifier)
                .setType(type)
                .setDocumentation(documentation)
                .setTypeSpecificMetadata(typeSpecificMetadata)
                .addAllAllowedScopes(allowedScopes)
                .setAffectedResource(affectedResource)
                .addAllAllowedDpcTypes(allowedDpcTypes)

        if (!requiredPermission.isEmpty()) {
            builder.setRequiredPermission(requiredPermission)
        }
        if (!requiredCrossUserPermission.isEmpty()) {
            validateRequiredCrossUserPermission(element, requiredCrossUserPermission)
            builder.setRequiredCrossUserPermission(requiredCrossUserPermission)
        }
        if (flaggedApi != null) {
            builder.setFlag(flaggedApi)
        }

        return builder.build()
    }

    private fun validateRequiredCrossUserPermission(
        element: Element,
        requiredCrossUserPermission: String,
    ) {
        if (
            requiredCrossUserPermission !in
                setOf(
                    "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
                    "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL",
                    "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL",
                )
        ) {
            printError(
                element,
                "requiredCrossUserPermission was set to '$requiredCrossUserPermission', but can only be set to android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS, android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL, android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL or left empty.",
            )
        }
    }

    private fun convertScopes(
        element: Element,
        allowedScopes: List<Int>,
    ): List<PolicyMetadata.PolicyScope> {
        if (allowedScopes.isEmpty()) {
            printError(element, "allowedScopes must not be empty.")
        }

        ensureNoDuplicates(element, allowedScopes, "allowedScopes", { v -> scopeToString(v) })

        return allowedScopes.mapNotNull { allowedScope ->
            PolicyMetadata.PolicyScope.forNumber(allowedScope)?.let {
                if (it == PolicyMetadata.PolicyScope.POLICY_SCOPE_UNSPECIFIED) {
                    // Not valid.
                    null
                } else {
                    it
                }
            }
                ?: run {
                    printError(
                        element,
                        "allowedScopes contains an unknown value $allowedScope, only use POLICY_SCOPE_* constants.",
                    )

                    null
                }
        }
    }

    private fun convertDpcTypes(
        element: Element,
        input: AllowedDpcTypes,
    ): List<PolicyMetadata.DpcType> {
        var result = mutableListOf<PolicyMetadata.DpcType>()

        fun addDpcType(dpcType: PolicyMetadata.DpcType, input: Int) {
            when (input) {
                AllowedDpcTypes.ALLOWED -> result.add(dpcType)
                AllowedDpcTypes.DISALLOWED -> {}
                AllowedDpcTypes.SAME_AS_UNAFFILIATED ->
                    printError(element, "$dpcType cannot be set to SAME_AS_UNAFFILIATED.")
                else -> throw IllegalArgumentException("Invalid value for $dpcType: ${input}")
            }
        }

        addDpcType(PolicyMetadata.DpcType.DPC_TYPE_DEVICE_OWNER, input.deviceOwner)
        addDpcType(PolicyMetadata.DpcType.DPC_TYPE_FINANCED_DEVICE_OWNER, input.financedDeviceOwner)
        addDpcType(
            PolicyMetadata.DpcType.DPC_TYPE_MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
            input.managedProfileOwnerOfOrganizationOwnedDevice,
        )
        addDpcType(
            PolicyMetadata.DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER0,
            input.profileOwnerOnUser0,
        )
        addDpcType(
            PolicyMetadata.DpcType.DPC_TYPE_MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE,
            input.managedProfileOwnerOfPersonalOwnedDevice,
        )
        addDpcType(
            PolicyMetadata.DpcType.DPC_TYPE_UNAFFILIATED_FULL_USER_PROFILE_OWNER,
            input.unaffiliatedFullUserProfileOwner,
        )

        // affiliatedFullUserProfileOwner supports SAME_AS_UNAFFILIATED.
        // Handle it separately.
        when (input.affiliatedFullUserProfileOwner) {
            AllowedDpcTypes.ALLOWED ->
                result.add(PolicyMetadata.DpcType.DPC_TYPE_AFFILIATED_FULL_USER_PROFILE_OWNER)
            AllowedDpcTypes.DISALLOWED -> {}
            AllowedDpcTypes.SAME_AS_UNAFFILIATED -> {
                if (input.unaffiliatedFullUserProfileOwner == AllowedDpcTypes.ALLOWED) {
                    result.add(PolicyMetadata.DpcType.DPC_TYPE_AFFILIATED_FULL_USER_PROFILE_OWNER)
                }
            }
            else ->
                throw IllegalArgumentException(
                    "Invalid value for affiliatedFullUserProfileOwner: ${input.affiliatedFullUserProfileOwner}"
                )
        }

        return result
    }

    /**
     * Checks for duplicates in `values`, and prints an error if any are found.
     *
     * @param elementToString: Method used to format the values in the error message.
     */
    public fun ensureNoDuplicates(
        element: Element,
        values: Collection<Int>,
        listName: String,
        elementToString: (Int) -> String,
    ): Boolean {
        val duplicates = getDuplicates(values)
        if (!duplicates.isEmpty()) {
            val duplicateNames = duplicates.map(elementToString)
            printError(
                element,
                "$listName contains duplicate values: ${duplicateNames.joinToString(",")}",
            )
            return false
        }
        return true
    }

    /**
     * Checks for unknown values in `values`, and prints an error if any are found.
     *
     * @param elementToString: Method used to format the values in the error message.
     */
    public fun ensureNoUnexpectedValues(
        element: Element,
        values: Collection<Int>,
        expectedValues: Collection<Int>,
        listName: String,
    ): Boolean {
        val unexpectedValues = values.filter { !expectedValues.contains(it) }

        if (!unexpectedValues.isEmpty()) {
            printError(
                element,
                "$listName contains unexpected values: ${unexpectedValues.joinToString(",")}",
            )
            return false
        }
        return true
    }

    /**
     * Checks that every value inside `expectedValues` is present in `values`, and prints an error
     * if any are missing.
     *
     * @param elementToString: Method used to format the values in the error message.
     */
    public fun ensureNoMissingValues(
        element: Element,
        values: Collection<Int>,
        expectedValues: Collection<Int>,
        listName: String,
        elementToString: (Int) -> String,
    ): Boolean {
        val missingValues = expectedValues.filter { !values.contains(it) }.map(elementToString)

        if (!missingValues.isEmpty()) {
            printError(element, "$listName must also contain: ${missingValues.joinToString(",")}")
            return false
        }
        return true
    }

    private fun scopeToString(value: Int): String {
        val enumValue = PolicyMetadata.PolicyScope.forNumber(value)
        return enumValue?.name ?: "$value"
    }

    private fun getDuplicates(values: Collection<Int>) =
        values.groupingBy { it }.eachCount().filter { it.value > 1 }.keys

    fun convertResourceType(element: Element, affectedResource: Int): PolicyMetadata.ResourceType? =
        PolicyMetadata.ResourceType.forNumber(affectedResource)?.let {
            if (it == PolicyMetadata.ResourceType.RESOURCE_TYPE_UNSPECIFIED) {
                null
            } else {
                it
            }
        }
            ?: run {
                printError(
                    element,
                    "affectedResource is set to an unknown value $affectedResource, only use RESOURCE_* constants.",
                )

                null
            }

    private fun readFlaggedApi(element: Element): String? {
        val flaggedApiAnnotation = element.getAnnotation(FlaggedApi::class.java) ?: return null

        return flaggedApiAnnotation.value
    }
}
