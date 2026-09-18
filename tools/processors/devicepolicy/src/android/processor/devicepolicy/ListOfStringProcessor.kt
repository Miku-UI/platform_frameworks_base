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

import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.ListPolicyMetadata.ResolutionMechanism as ListResolutionMechanismProto
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * Process elements with @ListOfStringPolicyDefinition.
 *
 * Since this annotation holds no data and we don't export any type-specific information, this only
 * contains type-specific checks.
 */
class ListOfStringProcessor(processingEnv: ProcessingEnvironment) :
    PolicyProcessor<ListOfStringPolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_STRING = "java.lang.String"
        const val LIST_TYPE = "java.util.List"
    }

    /** Represents a built-in String element */
    val stringType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_STRING).asType()

    /** Represents a built-in List */
    val listType: TypeElement = processingEnv.elementUtils.getTypeElement(LIST_TYPE)

    /** Represents a List<String> */
    val listOfStringType: TypeMirror = processingEnv.typeUtils.getDeclaredType(listType, stringType)

    val stringProcessor = StringProcessor(processingEnv)

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into
     * useful data.
     *
     * @return null if the element does not have a @ListOfStringPolicyDefinition or on error, {@link
     *   ListOfStringPolicyMetadata} otherwise.
     */
    override fun processMetadata(
        element: Element
    ): Pair<TypeSpecificPolicyMetadata, PolicyDefinition>? {
        val listOfStringDefinition =
            element.getAnnotation(ListOfStringPolicyDefinition::class.java)
                ?: throw IllegalStateException(
                    "Processor should only be called on elements with @ListOfStringPolicyDefinition"
                )

        if (!processingEnv.typeUtils.isSameType(policyType(element), listOfStringType)) {
            printError(
                element,
                "@ListOfStringPolicyDefinition can only be applied to policies of type $listOfStringType.",
            )
        }

        val resolutionMechanism = getResolutionMechanism(listOfStringDefinition, element)

        val typeSpecificMetadata =
            TypeSpecificPolicyMetadata.newBuilder()
                .setListMetadata(
                    TypeSpecificPolicyMetadata.ListPolicyMetadata.newBuilder()
                        .setStringMetadata(getStringMetadata(listOfStringDefinition))
                        .setEmptyListAllowed(listOfStringDefinition.emptyListAllowed)
                        .setResolutionMechanism(resolutionMechanism)
                )
                .build()

        return Pair(typeSpecificMetadata, listOfStringDefinition.base)
    }

    private fun getStringMetadata(annotation: ListOfStringPolicyDefinition) =
        TypeSpecificPolicyMetadata.StringPolicyMetadata.newBuilder()
            .setEmptyStringAllowed(annotation.emptyStringAllowed)
            .setUnprintableCharactersAllowed(annotation.unprintableCharactersAllowed)
            .build()

    private fun getResolutionMechanism(annotation: ListOfStringPolicyDefinition, element: Element) =
        ListResolutionMechanismProcessor(errorPrinter = { message -> printError(element, message) })
            .getResolutionMechanism(annotation.resolutionMechanism)

    override fun annotationClass(): Class<ListOfStringPolicyDefinition> {
        return ListOfStringPolicyDefinition::class.java
    }
}

// Helper class that processes a [ListResolutionMechanism] annotation.
class ListResolutionMechanismProcessor(val errorPrinter: (message: String) -> Unit) {
    private fun printError(message: String) = errorPrinter(message)

    public fun getResolutionMechanism(
        annotationValue: ListResolutionMechanism
    ): ListResolutionMechanismProto {
        if (!verifyResolutionMechanism(annotationValue)) {
            // Error is already printed
            return ListResolutionMechanismProto.newBuilder().build()
        }

        val builder = ListResolutionMechanismProto.newBuilder()
        if (annotationValue.custom) {
            builder.setCustom(true)
        } else {
            builder.setUnion(true)
        }
        return builder.build()
    }

    private fun verifyResolutionMechanism(annotationValue: ListResolutionMechanism): Boolean {
        val isCustom = annotationValue.custom
        val isUnion = annotationValue.union

        if (isCustom && isUnion) {
            printError("Only one resolution mechanism can be selected")
            return false
        }

        if (!isCustom && !isUnion) {
            printError("Resolution mechanism can not be empty")
            return false
        }

        return true
    }
}
