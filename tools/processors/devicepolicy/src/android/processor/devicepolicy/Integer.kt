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
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Process elements with @IntegerPolicyDefinition.
 *
 * Since this annotation holds no data and we don't export any type-specific information, this only
 * contains type-specific checks.
 */
class IntegerProcessor(processingEnv: ProcessingEnvironment) : Processor<IntegerPolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_INTEGER = "java.lang.Integer"
    }

    /** Represents a built-in Integer */
    val integerType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_INTEGER).asType()

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into useful data.
     *
     * @return null on error, {@link IntegerPolicyMetadata} otherwise.
     */
    override fun processMetadata(element: Element): Pair<PolicyMetadata, PolicyDefinition>? {
        val integerDefinition = element.getAnnotation(IntegerPolicyDefinition::class.java)
            ?: throw IllegalStateException("Processor should only be called on elements with @IntegerPolicyMetadata")

        val actualType = policyType(element)
        if (!processingEnv.typeUtils.isSameType(actualType, integerType)) {
            printError(
                element,
                "@IntegerPolicyDefinition can only be applied to policies of type $integerType, but got $actualType."
            )
        }

        return Pair(IntegerPolicyMetadata(), integerDefinition.base)
    }

    override fun annotationClass(): Class<IntegerPolicyDefinition> {
        return IntegerPolicyDefinition::class.java
    }
}

class IntegerPolicyMetadata() : PolicyMetadata() {
    override fun dump(writer: JsonWriter) {
        // Nothing to include for IntegerPolicyMetadata.
    }
}