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
 * Process elements with @BooleanPolicyDefinition.
 *
 * Since this annotation holds no data and we don't export any type-specific information, this only
 * contains type-specific checks.
 */
class BooleanProcessor(processingEnv: ProcessingEnvironment) : Processor<BooleanPolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_BOOLEAN = "java.lang.Boolean"
    }

    /** Represents a built-in Boolean */
    val booleanType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_BOOLEAN).asType()

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into useful data.
     *
     * @return null if the element does not have a @BooleanPolicyDefinition or on error, {@link BooleanPolicyMetadata} otherwise.
     */
    override fun processMetadata(element: Element): Pair<PolicyMetadata, PolicyDefinition>? {
        val booleanDefinition = element.getAnnotation(BooleanPolicyDefinition::class.java)
            ?: throw IllegalStateException("Processor should only be called on elements with @BooleanPolicyMetadata")

        if (!processingEnv.typeUtils.isSameType(policyType(element), booleanType)) {
            printError(
                element,
                "booleanValue in @PolicyDefinition can only be applied to policies of type $booleanType."
            )
        }

        return Pair(BooleanPolicyMetadata(), booleanDefinition.base)
    }

    override fun annotationClass(): Class<BooleanPolicyDefinition> {
        return BooleanPolicyDefinition::class.java
    }
}

class BooleanPolicyMetadata() : PolicyMetadata() {
    override fun dump(writer: JsonWriter) {
        // Nothing to include for BooleanPolicyMetadata.
    }
}