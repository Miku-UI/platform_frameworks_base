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
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/** Process elements with @ListOfPackagePolicyDefinition. */
class ListOfPackageProcessor(processingEnv: ProcessingEnvironment) :
    PolicyProcessor<ListOfPackagePolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_PACKAGE = "android.app.admin.PackageIdentifier"
        const val LIST_TYPE = "java.util.List"
    }

    /** Represents a built-in String element */
    val packageType: TypeMirror =
        processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_PACKAGE).asType()

    /** Represents a built-in List */
    val listType: TypeElement = processingEnv.elementUtils.getTypeElement(LIST_TYPE)

    /** Represents a List<String> */
    val listOfPackageType: TypeMirror =
        processingEnv.typeUtils.getDeclaredType(listType, packageType)

    val packageProcessor = PackageProcessor(processingEnv)

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into
     * useful data.
     *
     * Since this annotation holds no data and we don't export any type-specific information, this
     * only contains type-specific checks.
     *
     * @return null if the element does not have a @ListOfPackagePolicyDefinition or on error,
     *   {@link ListOfPackagePolicyMetadata} otherwise.
     */
    override fun processMetadata(
        element: Element
    ): Pair<TypeSpecificPolicyMetadata, PolicyDefinition>? {
        val listOfPackageDefinition =
            element.getAnnotation(ListOfPackagePolicyDefinition::class.java)
                ?: throw IllegalStateException(
                    "Processor should only be called on elements with " +
                        "@ListOfPackagePolicyDefinition"
                )

        if (!processingEnv.typeUtils.isSameType(policyType(element), listOfPackageType)) {
            printError(
                element,
                "@ListOfPackagePolicyDefinition can only be applied to " +
                    "policies of type $listOfPackageType.",
            )
        }

        val typeSpecificMetadata =
            TypeSpecificPolicyMetadata.newBuilder()
                .setListMetadata(
                    TypeSpecificPolicyMetadata.ListPolicyMetadata.newBuilder()
                        .setPackageMetadata(
                            TypeSpecificPolicyMetadata.PackagePolicyMetadata.getDefaultInstance()
                        )
                        .setEmptyListAllowed(listOfPackageDefinition.emptyListAllowed)
                        .setResolutionMechanism(
                            getResolutionMechanism(listOfPackageDefinition, element)
                        )
                )
                .build()

        return Pair(typeSpecificMetadata, listOfPackageDefinition.base)
    }

    private fun getResolutionMechanism(
        annotation: ListOfPackagePolicyDefinition,
        element: Element,
    ) =
        ListResolutionMechanismProcessor(errorPrinter = { message -> printError(element, message) })
            .getResolutionMechanism(annotation.resolutionMechanism)

    override fun annotationClass(): Class<ListOfPackagePolicyDefinition> {
        return ListOfPackagePolicyDefinition::class.java
    }
}
