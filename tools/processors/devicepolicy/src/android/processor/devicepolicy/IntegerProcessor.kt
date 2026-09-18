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
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.IntegerPolicyMetadata.ResolutionMechanism as IntegerResolutionMechanismProto
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Process elements with @IntegerPolicyDefinition.
 *
 * Since this annotation holds no data and we don't export any type-specific information, this only
 * contains type-specific checks.
 */
class IntegerProcessor(processingEnv: ProcessingEnvironment) :
    PolicyProcessor<IntegerPolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_INTEGER = "java.lang.Integer"
    }

    /** Represents a built-in Integer */
    val integerType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_INTEGER).asType()

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into
     * useful data.
     *
     * @return null on error, {@link IntegerPolicyMetadata} otherwise.
     */
    override fun processMetadata(
        element: Element
    ): Pair<TypeSpecificPolicyMetadata, PolicyDefinition>? {
        val integerDefinition =
            element.getAnnotation(IntegerPolicyDefinition::class.java)
                ?: throw IllegalStateException(
                    "Processor should only be called on elements with @IntegerPolicyDefinition"
                )

        val actualType = policyType(element)
        if (!processingEnv.typeUtils.isSameType(actualType, integerType)) {
            printError(
                element,
                "@IntegerPolicyDefinition can only be applied to policies of type $integerType, but got $actualType.",
            )
        }

        val integerMetadataBuilder =
            TypeSpecificPolicyMetadata.IntegerPolicyMetadata.newBuilder()
                .setResolutionMechanism(getResolutionMechanism(integerDefinition, element))
        if (integerDefinition.minValue != Integer.MIN_VALUE) {
            integerMetadataBuilder.setMinValue(integerDefinition.minValue)
        }
        if (integerDefinition.maxValue != Integer.MAX_VALUE) {
            integerMetadataBuilder.setMaxValue(integerDefinition.maxValue)
        }
        if (integerDefinition.minValue > integerDefinition.maxValue) {
            printError(element, "minValue cannot be larger than maxValue.")
        }

        val typeSpecificMetadata =
            TypeSpecificPolicyMetadata.newBuilder()
                .setIntegerMetadata(integerMetadataBuilder)
                .build()

        return Pair(typeSpecificMetadata, integerDefinition.base)
    }

    override fun annotationClass(): Class<IntegerPolicyDefinition> {
        return IntegerPolicyDefinition::class.java
    }

    private fun getResolutionMechanism(annotation: IntegerPolicyDefinition, element: Element) =
        ResolutionMechanismProcessor(element).getResolutionMechanism(annotation.resolutionMechanism)

    // Helper class to process the resolution mechanism field
    inner class ResolutionMechanismProcessor(val element: Element) {

        public fun getResolutionMechanism(
            annotationValue: IntegerResolutionMechanism
        ): IntegerResolutionMechanismProto {
            if (!verifyResolutionMechanism(annotationValue)) {
                // Error is already printed
                return IntegerResolutionMechanismProto.newBuilder().build()
            }

            val builder = IntegerResolutionMechanismProto.newBuilder()
            if (annotationValue.custom) {
                builder.setCustom(true)
            } else {
                builder.setNotCoexistable(true)
            }
            return builder.build()
        }

        private fun verifyResolutionMechanism(
            annotationValue: IntegerResolutionMechanism
        ): Boolean {
            val isCustom = annotationValue.custom
            val isNotCoexistable = annotationValue.notCoexistable

            if (isCustom && isNotCoexistable) {
                printError(element, "Only one resolution mechanism can be selected")
                return false
            }

            if (!isCustom && !isNotCoexistable) {
                printError(element, "Resolution mechanism can not be empty")
                return false
            }

            return true
        }
    }
}
