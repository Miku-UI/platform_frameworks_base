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

import android.processor.devicepolicy.protos.FullyQualifiedFieldName
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata.EnumValue as EnumValueProto
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata.ResolutionMechanism as EnumResolutionMechanismProto
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata.ResolutionMechanism.MostRestrictive as MostRestrictiveProto
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewArrayTree
import com.sun.source.util.SimpleTreeVisitor
import com.sun.source.util.Trees
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * Process elements with @EnumPolicyDefinition.
 *
 * Since information about enums values is encoded by @IntDef, we need to go trough that annotation
 * as well. Since this processor is not the direct consumer of @IntDef and we want the names for the
 * enum entries, not just values, we will have to use the JDK to walk the AST.
 *
 * We extract:
 * <ul>
 * <li> The default value. </li>
 * <li> The documentation for the enumeration. </li>
 * <li> All enumeration entries with value, name and documentation. </li>
 * <li> The conflict resolution mechanism . </li>
 * </ul>
 *
 * We will use the following example to illustrate what we are doing: {@snippet : class ExampleClass
 * { public static final int ENUM_ENTRY_1 = 0; public static final int ENUM_ENTRY_2 = 1;
 *
 *          @Retention(RetentionPolicy.SOURCE)
 *          @IntDef(prefix = { "ENUM_ENTRY_" }, value = {
 *                  ENUM_ENTRY_1,
 *                  ENUM_ENTRY_2,
 *          })
 *          public @interface PolicyEnum {}
 *          @PolicyDefinition
 *          @EnumPolicyDefinition(
 *                  defaultValue = ENUM_ENTRY_2,
 *                  intDef = PolicyEnum.class
 *          )
 *          public static final PolicyIdentifier<Integer> EXAMPLE_POLICY) =
 *                  new PolicyIdentifier<>("EXAMPLE_POLICY");
 *      }
 *
 * }
 */
class EnumProcessor(processingEnv: ProcessingEnvironment) :
    PolicyProcessor<EnumPolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_INTEGER = "java.lang.Integer"

        // These enum values are allowed to use 0 for backwards compatibility reasons.
        val LEGACY_POLICY_ENUM_WITH_ZERO_VALUE_ALLOWED =
            setOf("AUTO_TIME_USER_CHOICE", "AUTO_TIME_ZONE_USER_CHOICE")

        /** Find the first value matching a predicate on the key. */
        fun <K, V> Map<K, V>.firstValue(filter: (K) -> Boolean): V {
            return entries.first { (key, _) -> filter(key) }.value
        }
    }

    /** Represents a built-in Integer */
    val integerType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_INTEGER).asType()

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into
     * useful data.
     *
     * @return null if the element does not have a @EnumPolicyDefinition or on error, {@link
     *   EnumPolicyMetadata} otherwise.
     */
    override fun processMetadata(
        element: Element
    ): Pair<TypeSpecificPolicyMetadata, PolicyDefinition>? {
        val enumPolicyAnnotation =
            element.getAnnotation(EnumPolicyDefinition::class.java)
                ?: throw IllegalStateException(
                    "Processor should only be called on elements with @EnumPolicyDefinition"
                )

        if (!processingEnv.typeUtils.isSameType(policyType(element), integerType)) {
            printError(
                element,
                "@EnumPolicyDefinition can only be applied to policies of type $integerType.",
            )
        }

        // In the class-level example above, this would be PolicyEnum.
        val intDefElement = getIntDefElement(element)

        // This is the IntDef annotation class.
        val intDefClass = processingEnv.elementUtils.getTypeElement("android.annotation.IntDef")
        // In the class-level example above, this is @IntDef annotation on the PolicyEnum.
        val annotationMirror =
            intDefElement.annotationMirrors.firstOrNull {
                it.annotationType.asElement() == intDefClass
            }

        if (annotationMirror == null) {
            printError(
                element,
                "@EnumPolicyDefinition.intDef must be the interface marked with @IntDef.",
            )

            return null
        }

        val enumName = intDefElement.qualifiedName.toString()
        val enumDoc = processingEnv.elementUtils.getDocComment(intDefElement)?.trimIndent() ?: ""

        if (enumDoc.trim().isEmpty()) {
            printError(intDefElement, "Missing JavaDoc for IntDef used by $element")
        }

        // In the class-level example above, these would be ENUM_ENTRY_1 and ENUM_ENTRY_2.
        val entries = getIntDefIdentifiers(annotationMirror, intDefElement)
        validateEnumValues(entries, intDefElement)

        val resolutionMechanism =
            getResolutionMechanism(enumPolicyAnnotation.resolutionMechanism, entries, element)

        val metadata =
            TypeSpecificPolicyMetadata.newBuilder()
                .setEnumMetadata(
                    TypeSpecificPolicyMetadata.EnumPolicyMetadata.newBuilder()
                        .setDefaultValue(enumPolicyAnnotation.defaultValue)
                        .setIntDefName(enumName)
                        .setDocumentation(enumDoc)
                        .addAllValues(entries)
                        .setResolutionMechanism(resolutionMechanism)
                        .build()
                )
                .build()

        return Pair(metadata, enumPolicyAnnotation.base)
    }

    override fun annotationClass(): Class<EnumPolicyDefinition> {
        return EnumPolicyDefinition::class.java
    }

    /**
     * Given a policy definition element finds the type element representing the IntDef definition
     * Same as `processingEnv.elementUtils.getTypeElement(enumPolicyMetadata.intDef.qualifiedName)`,
     * but we have to use type mirrors.
     */
    private fun getIntDefElement(element: Element): TypeElement {
        val am =
            element.annotationMirrors.first {
                it.annotationType.toString() == EnumPolicyDefinition::class.java.name
            }
        val av = am.elementValues.firstValue { key -> key.simpleName.toString() == "intDef" }
        val mirror = av.value as TypeMirror
        return processingEnv.typeUtils.asElement(mirror) as TypeElement
    }

    /**
     * For an @IntDef marked element, get the identifiers used to build up that enum.
     *
     * In the class-level example above, these would be ENUM_ENTRY_1 and ENUM_ENTRY_2. *
     */
    private fun getIntDefIdentifiers(
        annotationMirror: AnnotationMirror,
        intDefElement: TypeElement,
    ): List<EnumValueProto> {
        val annotationValue: AnnotationValue =
            annotationMirror.elementValues.firstValue { key ->
                key.simpleName.contentEquals("value")
            }

        // Walk the AST as we want the actual identifiers passed to @IntDef.
        val trees = Trees.instance(processingEnv)
        val tree = trees.getTree(intDefElement, annotationMirror, annotationValue)

        // In the class-level example above, these would be {"ENUM_ENTRY_1", "ENUM_ENTRY_2"}.
        val identifiers = ArrayList<String>()
        tree.accept(IdentifierVisitor(), identifiers)

        // Get the complete field names and the documentation for each entry.
        val fields = getElementForIdentifier(identifiers, intDefElement)
        val names = getFieldNames(fields)
        val docs = getFieldDocumentations(fields)

        // In the class-level example above, these would be {ENUM_ENTRY_1, ENUM_ENTRY_2}.
        @Suppress("UNCHECKED_CAST")
        val values = (annotationValue.value as List<AnnotationValue>).map { it.value as Int }

        if (values.size < fields.size) {
            // Should never be reached.
            // We walk the same annotation definition and should find the same number of entries.
            throw IllegalStateException(
                "Annotation value $annotationValue for $intDefElement does not have enough values"
            )
        }

        return fields.mapIndexed { i, field ->
            EnumValueProto.newBuilder()
                .setFieldName(names[i])
                .setIntValue(values[i])
                .setDocumentation(docs[i])
                .build()
        }
    }

    /**
     * Verify that enum should not be declared with 0 value which is reserved for *_UNSPECIFIED
     * value (https://google.aip.dev/126).
     */
    private fun validateEnumValues(entries: List<EnumValueProto>, element: Element) {
        entries.forEach { entry ->
            if (
                entry.getIntValue() == 0 &&
                    !LEGACY_POLICY_ENUM_WITH_ZERO_VALUE_ALLOWED.contains(entry.getShortFieldName())
            ) {
                printError(
                    element,
                    "The Protobuf enum value '0' is reserved for the default *_UNSPECIFIED case " +
                        "(https://google.aip.dev/126) and should not be used for any other enum " +
                        "value. Found in: ${entry.getShortFieldName()}",
                )
            }
        }
    }

    /*
     * Given a policy definition element:
     *   * Finds the modeled resolution mechanism
     *   * Verifies only one is specified
     *   * If `mostRestrictive` is selected: verifies each enum value is mentioned exactly once.
     *
     */
    private fun getResolutionMechanism(
        annotationValue: EnumResolutionMechanism,
        allValues: List<EnumValueProto>,
        element: Element,
    ): EnumResolutionMechanismProto {
        if (!verifyResolutionMechanism(annotationValue, allValues, element)) {
            // Error is already printed
            return EnumResolutionMechanismProto.newBuilder().build()
        }

        val builder = EnumResolutionMechanismProto.newBuilder()
        if (annotationValue.custom) {
            builder.setCustom(true)
        } else if (annotationValue.notCoexistable) {
            builder.setNotCoexistable(true)
        } else {
            builder.setMostRestrictive(
                MostRestrictiveProto.newBuilder()
                    .addAllMostToLeastRestrictive(annotationValue.mostRestrictive.toList())
                    .build()
            )
        }
        return builder.build()
    }

    private fun verifyResolutionMechanism(
        annotation: EnumResolutionMechanism,
        allValues: List<EnumValueProto>,
        element: Element,
    ): Boolean {
        val isCustom = annotation.custom
        val isNotCoexistable = annotation.notCoexistable
        val isMostRestrictive = annotation.mostRestrictive.isNotEmpty()

        val count = listOf(isCustom, isNotCoexistable, isMostRestrictive).count { it }

        if (count > 1) {
            printError(
                element,
                "In @EnumResolutionMechanism, a single resolution mechanism " +
                "can be selected.",
            )
            return false
        }

        if (count == 0) {
            printError(
                element,
                "In @EnumResolutionMechanism, a resolution mechanism must " +
                "be set.",
            )
            return false
        }

        if (isMostRestrictive) {
            return verifyMostRestrictive(annotation.mostRestrictive.toList(), allValues, element)
        }

        return true
    }

    private fun verifyMostRestrictive(
        values: List<Int>,
        allValues: List<EnumValueProto>,
        element: Element,
    ): Boolean {
        var success =
            ensureNoDuplicates(
                element,
                values,
                "mostRestrictive",
                elementToString = { v -> valueToString(v, allValues) },
            ) &&
                ensureNoUnexpectedValues(
                    element,
                    values,
                    expectedValues = allValues.map { it.intValue },
                    listName = "mostRestrictive",
                ) &&
                ensureNoMissingValues(
                    element,
                    values,
                    expectedValues = allValues.map { it.intValue },
                    listName = "mostRestrictive",
                    elementToString = { v -> valueToString(v, allValues) },
                )

        return success
    }

    private fun valueToString(value: Int, allValues: List<EnumValueProto>): String {
        val matchingEnum = allValues.firstOrNull { it.intValue == value }
        return matchingEnum?.fieldName?.fieldName ?: value.toString()
    }

    private fun getElementForIdentifier(
        identifiers: List<String>,
        intDefElement: TypeElement,
    ): List<Element> =
        identifiers.mapNotNull { identifier ->
            // TODO(b/442973945): Support identifiers outside of same class.
            // Get the element pointing the field, ENUM_ENTRY_1 in our example.
            val element =
                intDefElement.enclosingElement.enclosedElements.firstOrNull {
                    it.simpleName.toString() == identifier
                }

            if (element == null) {
                // We skip fields if we can't find them.
                // Continue processing to print all errors at once.
                printError(intDefElement.enclosingElement, "Could not find $identifier")
            }

            element
        }

    private fun getFieldDocumentations(fields: List<Element>): List<String> =
        fields.map { element ->
            // Our example has no javadoc, but this would get ENUM_ENTRY_1's documentation.
            val documentation =
                processingEnv.elementUtils.getDocComment(element)?.trimIndent()?.trim() ?: ""

            if (documentation.isEmpty()) {
                printError(element, "Missing JavaDoc")
            }

            documentation
        }

    private fun getFieldNames(fields: List<Element>): List<FullyQualifiedFieldName> =
        fields.map { element ->
            // In our example this would be {"ExampleClass", "ENUM_ENTRY_1"}.
            getFullyQualifiedFieldName(element)
        }

    private fun EnumValueProto.getShortFieldName(): String = getFieldName().getFieldName()

    private class IdentifierVisitor : SimpleTreeVisitor<Void, ArrayList<String>>() {
        override fun visitNewArray(node: NewArrayTree, identifiers: ArrayList<String>): Void? {
            for (initializer in node.initializers) {
                initializer.accept(this, identifiers)
            }

            return null
        }

        /** Called when the identifier used in IntDef is a member of the class. */
        override fun visitMemberSelect(
            node: MemberSelectTree,
            identifiers: ArrayList<String>,
        ): Void? {
            identifiers.add(node.identifier.toString())

            return null
        }

        /**
         * Called when the identifier in IntDef is an arbitrary identifier pointing outside the
         * current class.
         *
         * This is not present in the class-level example above, but was added to be consistent with
         * the original @IntDef processor logic.
         */
        override fun visitIdentifier(node: IdentifierTree, identifiers: ArrayList<String>): Void? {
            identifiers.add(node.name.toString())

            return null
        }
    }
}
