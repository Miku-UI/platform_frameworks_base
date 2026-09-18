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
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Process elements with @StringPolicyDefinition.
 *
 * Since this annotation holds no data and we don't export any type-specific information, this only
 * contains type-specific checks.
 */
class StringProcessor(processingEnv: ProcessingEnvironment) :
    PolicyProcessor<StringPolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_STRING = "java.lang.String"
    }

    /** Represents a built-in String */
    val stringType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_STRING).asType()

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into
     * useful data.
     *
     * @return null if the element does not have a @StringPolicyDefinition or on error, {@link
     *   StringPolicyMetadata} otherwise.
     */
    override fun processMetadata(
        element: Element
    ): Pair<TypeSpecificPolicyMetadata, PolicyDefinition>? {
        val stringDefinition =
            element.getAnnotation(StringPolicyDefinition::class.java)
                ?: throw IllegalStateException(
                    "Processor should only be called on elements with @StringPolicyDefinition"
                )

        if (!processingEnv.typeUtils.isSameType(policyType(element), stringType)) {
            printError(
                element,
                "@StringPolicyDefinition can only be applied to policies of type $stringType.",
            )
        }

        val typeSpecificMetadata =
            TypeSpecificPolicyMetadata.newBuilder()
                .setStringMetadata(extractTypeSpecificMetadata(stringDefinition))
                .build()

        return Pair(typeSpecificMetadata, stringDefinition.base)
    }

    override fun annotationClass(): Class<StringPolicyDefinition> {
        return StringPolicyDefinition::class.java
    }

    fun extractTypeSpecificMetadata(
        definition: StringPolicyDefinition
    ): TypeSpecificPolicyMetadata.StringPolicyMetadata {
        val builder =
            TypeSpecificPolicyMetadata.StringPolicyMetadata.newBuilder()
                .setEmptyStringAllowed(definition.emptyStringAllowed)
                .setUnprintableCharactersAllowed(definition.unprintableCharactersAllowed)
        if (definition.maxLength != Integer.MAX_VALUE) {
            builder.setMaxLength(definition.maxLength)
        }
        return builder.build()
    }
}
