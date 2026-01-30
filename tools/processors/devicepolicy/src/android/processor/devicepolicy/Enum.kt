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

import com.android.json.stream.JsonWriter
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
 *     <li> The default value. </li>
 *     <li> The documentation for the enumeration. </li>
 *     <li> All enumeration entries with value, name and documentation. </li>
 * </ul>
 *
 * We will use the following example to illustrate what we are doing:
 * {@snippet :
 *     public static final int ENUM_ENTRY_1 = 0;
 *     public static final int ENUM_ENTRY_2 = 1;
 *
 *     @Retention(RetentionPolicy.SOURCE)
 *     @IntDef(prefix = { "ENUM_ENTRY_" }, value = {
 *             ENUM_ENTRY_1,
 *             ENUM_ENTRY_2,
 *     })
 *     public @interface PolicyEnum {}
 *
 *     @PolicyDefinition
 *     @EnumPolicyDefinition(
 *             defaultValue = ENUM_ENTRY_2,
 *             intDef = PolicyEnum.class
 *     )
 *     public static final PolicyIdentifier<Integer> EXAMPLE_POLICY) = new PolicyIdentifier<>(
 *             "EXAMPLE_POLICY");
 * }
 */
class EnumProcessor(processingEnv: ProcessingEnvironment) : Processor<EnumPolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_INTEGER = "java.lang.Integer"

        /**
         * Find the first value matching a predicate on the key.
         */
        fun <K, V> Map<K, V>.firstValue(filter: (K) -> Boolean): V {
            return entries.first { (key, _) -> filter(key) }.value
        }
    }

    /** Represents a built-in Integer */
    val integerType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_INTEGER).asType()

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into useful data.
     *
     * @return null if the element does not have a @EnumPolicyDefinition or on error, {@link EnumPolicyMetadata} otherwise.
     */
    override fun processMetadata(element: Element): Pair<PolicyMetadata, PolicyDefinition>? {
        val enumPolicyAnnotation = element.getAnnotation(EnumPolicyDefinition::class.java)
            ?: throw IllegalStateException("Processor should only be called on elements with @EnumPolicyMetadata")

        if (!processingEnv.typeUtils.isSameType(policyType(element), integerType)) {
            printError(
                element,
                "@EnumPolicyDefinition can only be applied to policies of type $integerType."
            )
        }

        // In the class-level example above, this would be PolicyEnum.
        val intDefElement = getIntDefElement(element)

        // This is the IntDef annotation class.
        val intDefClass = processingEnv.elementUtils.getTypeElement("android.annotation.IntDef")
        // In the class-level example above, this is @IntDef annotation on the PolicyEnum.
        val annotationMirror =
            intDefElement.annotationMirrors.firstOrNull { it.annotationType.asElement() == intDefClass }

        if (annotationMirror == null) {
            printError(
                element, "@EnumPolicyDefinition.intDef must be the interface marked with @IntDef."
            )

            return null
        }

        val enumName = intDefElement.qualifiedName.toString()
        val enumDoc = processingEnv.elementUtils.getDocComment(intDefElement) ?: ""

        if (enumDoc.trim().isEmpty()) {
            printError(intDefElement, "Missing JavaDoc for IntDef used by $element")
        }

        // In the class-level example above, these would be ENUM_ENTRY_1 and ENUM_ENTRY_2.
        val entries = getIntDefIdentifiers(annotationMirror, intDefElement)

        val metadata = EnumPolicyMetadata(
            enumPolicyAnnotation.defaultValue, enumName, enumDoc, entries
        )

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
        val am = element.annotationMirrors.first {
            it.annotationType.toString() == EnumPolicyDefinition::class.java.name
        }
        val av = am.elementValues.firstValue { key ->
            key.simpleName.toString() == "intDef"
        }
        val mirror = av.value as TypeMirror
        return processingEnv.typeUtils.asElement(mirror) as TypeElement
    }

    /**
     * For an @IntDef marked element, get the identifiers used to build up that enum.
     *
     * In the class-level example above, these would be ENUM_ENTRY_1 and ENUM_ENTRY_2.     *
     */
    private fun getIntDefIdentifiers(
        annotationMirror: AnnotationMirror, intDefElement: TypeElement
    ): List<EnumEntryMetadata> {
        val annotationValue: AnnotationValue = annotationMirror.elementValues.firstValue { key ->
            key.simpleName.contentEquals("value")
        }

        // Walk the AST as we want the actual identifiers passed to @IntDef.
        val trees = Trees.instance(processingEnv)
        val tree = trees.getTree(intDefElement, annotationMirror, annotationValue)

        val identifiers = ArrayList<String>()
        tree.accept(IdentifierVisitor(), identifiers)

        // In the class-level example above, these would be {ENUM_ENTRY_1,ENUM_ENTRY_2}.
        @Suppress("UNCHECKED_CAST") val values = annotationValue.value as List<AnnotationValue>

        val documentations: List<String?> = identifiers.map { identifier ->
            // TODO(b/442973945): Support identifiers outside of same class.
            intDefElement.enclosingElement.enclosedElements.firstOrNull {
                it.simpleName.toString() == identifier
            } ?.let {
                processingEnv.elementUtils.getDocComment(it)
            }
        }

        return identifiers.mapIndexed { i, identifier ->
            EnumEntryMetadata(identifier, values[i].value as Int, documentations[i])
        }
    }

    private class IdentifierVisitor : SimpleTreeVisitor<Void, ArrayList<String>>() {
        override fun visitNewArray(node: NewArrayTree, identifiers: ArrayList<String>): Void? {
            for (initializer in node.initializers) {
                initializer.accept(this, identifiers)
            }

            return null
        }

        /**
         * Called when the identifier used in IntDef is a member of the class.
         */
        override fun visitMemberSelect(
            node: MemberSelectTree, identifiers: ArrayList<String>
        ): Void? {
            identifiers.add(node.identifier.toString())

            return null
        }

        /**
         * Called when the identifier in IntDef is an arbitrary identifier pointing outside the
         * current class.
         *
         * This is not present in the class-level example above, but was added to be consistent
         * with the original @IntDef processor logic.
         */
        override fun visitIdentifier(node: IdentifierTree, identifiers: ArrayList<String>): Void? {
            identifiers.add(node.name.toString())

            return null
        }
    }
}

class EnumEntryMetadata(val name: String, val value: Int, val documentation: String?) {
    fun dump(writer: JsonWriter) {
        writer.apply {
            beginObject()

            name("name")
            value(name)

            name("value")
            value(value.toLong())

            if (documentation != null) {
                name("documentation")
                value(documentation)
            }

            endObject()
        }
    }
}

class EnumPolicyMetadata(
    val defaultValue: Int,
    val enum: String,
    val enumDocumentation: String,
    val entries: List<EnumEntryMetadata>
) : PolicyMetadata() {
    override fun dump(writer: JsonWriter) {
        writer.apply {
            name("default")
            value(defaultValue.toLong())

            name("enum")
            value(enum)

            name("enumDocumentation")
            value(enumDocumentation)

            name("options")
            beginArray()
            entries.forEach { it.dump(writer) }
            writer.endArray()
        }
    }
}