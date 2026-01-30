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
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.FilerException
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

/**
 * PolicyProcessor processes all {@link PolicyDefinition} instances:
 * <ol>
 *     <li> Verify that the policies are well formed. </li>
 *     <li> Exports the data for consumption by other tools. </li>
 * </ol>
 *
 * Currently the data exported contains:
 * <ul>
 *     <li> The name of the field. </li>
 *     <li> The type of the policy. </li>
 *     <li> The JavaDoc for the policy. </li>
 *     <li> For enums: all options and their documentation. </li>
 * </ul>
 *
 * Data is exported to `policies.json`.
 */
class PolicyProcessor : AbstractProcessor() {
    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    // Define what the annotation we care about are for compiler optimization
    override fun getSupportedAnnotationTypes() = LinkedHashSet<String>().apply {
        add(BooleanPolicyDefinition::class.java.name)
        add(EnumPolicyDefinition::class.java.name)
        add(IntegerPolicyDefinition::class.java.name)

        // Only processed to report errors.
        add(PolicyDefinition::class.java.name)
    }

    override fun process(
        annotations: MutableSet<out TypeElement>, roundEnvironment: RoundEnvironment
    ): Boolean {
        reportUnexpectedAnnotations(roundEnvironment)

        val policies = listOf(
            runProcessor(roundEnvironment, BooleanProcessor(processingEnv)),
            runProcessor(roundEnvironment, EnumProcessor(processingEnv)),
            runProcessor(roundEnvironment, IntegerProcessor(processingEnv)),
        ).flatten()

        try {
            writePolicies(roundEnvironment, policies)
        } catch (e: FilerException) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "Policies already written, not overwriting: $e",
            )

            return false
        }

        return false
    }

    private fun reportUnexpectedAnnotations(roundEnvironment: RoundEnvironment) {
        roundEnvironment.getElementsAnnotatedWith(PolicyDefinition::class.java).mapNotNull {
            printError(it, "@PolicyDefinition should not be applied to any element")
        }
    }

    private fun <T : Annotation> runProcessor(
        roundEnvironment: RoundEnvironment, processor: Processor<T>
    ): List<Policy> {
        return roundEnvironment.getElementsAnnotatedWith(processor.annotationClass()).mapNotNull {
            processor.process(it)
        }
    }

    fun writePolicies(roundEnvironment: RoundEnvironment, policies: List<Policy>) {
        val writer = createWriter(roundEnvironment)
        writer.setIndent("  ")

        writer.beginObject()
        writer.name("policies")
        dumpJSON(writer, policies)
        writer.endObject()

        writer.close()
    }

    fun createWriter(roundEnvironment: RoundEnvironment): JsonWriter {
        return JsonWriter(
            processingEnv.filer.createResource(
                StandardLocation.SOURCE_OUTPUT, "android.processor.devicepolicy", "policies.json"
            ).openWriter()
        )
    }

    private fun printError(element: Element, message: String) {
        processingEnv.messager.printMessage(
            Diagnostic.Kind.ERROR,
            message,
            element,
        )
    }
}