/*
 * Copyright (C) 2026 The Android Open Source Project
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

/** Process elements with @PackagePolicyDefinition. */
class PackageProcessor(processingEnv: ProcessingEnvironment) :
    PolicyProcessor<PackagePolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_PACKAGE = "android.app.admin.PackageIdentifier"
    }

    /** Represents a Package */
    val packageType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_PACKAGE).asType()

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into
     * useful data.
     *
     * @return null if the element does not have a @PackagePolicyDefinition or on error, {@link
     *   PackagePolicyDefinition} otherwise.
     */
    override fun processMetadata(
        element: Element
    ): Pair<TypeSpecificPolicyMetadata, PolicyDefinition>? {
        val packageDefinition =
            element.getAnnotation(PackagePolicyDefinition::class.java)
                ?: throw IllegalStateException(
                    "Processor should only be called on elements with @PackagePolicyMetadata"
                )

        if (!processingEnv.typeUtils.isSameType(policyType(element), packageType)) {
            printError(
                element,
                "@PackagePolicyDefinition can only be applied to policies of type $packageType.",
            )
        }

        // Since this annotation holds no data and we don't export any type-specific information,
        // this only contains type-specific checks.
        val typeSpecificMetadata =
            TypeSpecificPolicyMetadata.newBuilder()
                .setPackageMetadata(
                    TypeSpecificPolicyMetadata.PackagePolicyMetadata.getDefaultInstance()
                )
                .build()

        return Pair(typeSpecificMetadata, packageDefinition.base)
    }

    override fun annotationClass(): Class<PackagePolicyDefinition> {
        return PackagePolicyDefinition::class.java
    }
}
