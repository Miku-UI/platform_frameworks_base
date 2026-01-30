/*
 * Copyright (C) 2022 The Android Open Source Project
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

@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package android.processor.immutability

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic

val IMMUTABLE_ANNOTATION_NAME = Immutable::class.qualifiedName!!

class ImmutabilityProcessor : AbstractProcessor() {

    companion object {

        /**
         * Types that are already immutable. Will also ignore subclasses.
         */
        private val IGNORED_SUPER_TYPES = listOf(
            "java.io.File",
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.CharSequence",
            "java.lang.Character",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.String",
            "java.lang.Void",
            "java.util.UUID",
            "android.os.Parcelable.Creator",
        )

        /**
         * Types that are already immutable. Must be an exact match, does not include any super
         * or sub classes.
         */
        private val IGNORED_EXACT_TYPES = listOf(
            "java.lang.Class",
            "java.lang.Object",
        )

        private val IGNORED_METHODS = listOf(
            "writeToParcel",
        )
    }

    private lateinit var collectionType: TypeMirror
    private lateinit var mapType: TypeMirror

    private lateinit var ignoredSuperTypes: List<TypeMirror>
    private lateinit var ignoredExactTypes: List<TypeMirror>
    private val seenTypesByPolicy =
        mutableMapOf<Set<Immutable.Policy.Exception>, MutableSet<TypeMirror>>() // Use MutableSet

    override fun getSupportedSourceVersion() = SourceVersion.latestSupported()

    override fun getSupportedAnnotationTypes() = setOf(Immutable::class.qualifiedName!!)

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        collectionType = processingEnv.erasedType("java.util.Collection")!!
        mapType = processingEnv.erasedType("java.util.Map")!!
        ignoredSuperTypes = IGNORED_SUPER_TYPES.mapNotNull { processingEnv.erasedType(it) }
        ignoredExactTypes = IGNORED_EXACT_TYPES.mapNotNull { processingEnv.erasedType(it) }
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnvironment: RoundEnvironment
    ): Boolean {
        annotations.find {
            it.qualifiedName.toString() == IMMUTABLE_ANNOTATION_NAME
        } ?: return false
        roundEnvironment.getElementsAnnotatedWith(Immutable::class.java)
            .forEach {
                visitClass(
                    parentChain = emptyList(),
                    seenTypesByPolicy = seenTypesByPolicy,
                    elementToPrint = it,
                    classType = it as TypeElement,
                    parentPolicyExceptions = emptySet()
                )
            }
        return true
    }

    /**
     * @return true if any error was encountered at this level or any child level
     */
    private fun visitClass(
        parentChain: List<String>,
        seenTypesByPolicy: MutableMap<Set<Immutable.Policy.Exception>, MutableSet<TypeMirror>>,
        elementToPrint: Element,
        classType: TypeElement,
        parentPolicyExceptions: Set<Immutable.Policy.Exception>,
    ): Boolean {
        if (isIgnored(classType)) return false

        val policyAnnotation = classType.getAnnotation(Immutable.Policy::class.java)
        val newPolicyExceptions = parentPolicyExceptions + policyAnnotation?.exceptions.orEmpty()

        // If already seen this type with the same policies applied, skip it
        val seenTypes = seenTypesByPolicy.getOrPut(newPolicyExceptions) { mutableSetOf() }
        val type = classType.asType()
        if (!seenTypes.add(type)) return false // Use add() for MutableSet

        val allowFinalClassesFinalFields =
            newPolicyExceptions.contains(Immutable.Policy.Exception.FINAL_CLASSES_WITH_FINAL_FIELDS)

        val filteredElements = classType.enclosedElements
            .filterNot(::isIgnored)

        val hasFieldError = filteredElements
            .filter { it.kind == ElementKind.FIELD }
            .fold(false) { anyError, field ->
                if (field.modifiers.contains(Modifier.STATIC)) {
                    if (!field.modifiers.contains(Modifier.PRIVATE)) {
                        val finalityError = !field.modifiers.contains(Modifier.FINAL)
                        if (finalityError) {
                            printError(parentChain, field, MessageUtils.staticNonFinalFailure())
                        }

                        // Must call visitType first so it doesn't get short circuited by the ||
                        visitType(
                            parentChain,
                            seenTypesByPolicy,
                            field,
                            field.asType(),
                            newPolicyExceptions
                        ) || anyError || finalityError
                    } else {
                        anyError
                    }
                } else {
                    val isFinal = field.modifiers.contains(Modifier.FINAL)
                    if (!isFinal || !allowFinalClassesFinalFields) {
                        printError(parentChain, field, MessageUtils.memberNotMethodFailure())
                        true
                    } else {
                        anyError
                    }
                }
            }

        // Scan inner classes before methods so that any violations isolated to the file prints
        // the error on the class declaration rather than on the method that returns the type.
        // Although it doesn't matter too much either way.
        val hasClassError = filteredElements
            .filter { it.kind == ElementKind.CLASS }
            .map { it as TypeElement }
            .fold(false) { anyError, innerClass ->
                visitClass(
                    parentChain,
                    seenTypesByPolicy,
                    innerClass,
                    innerClass,
                    newPolicyExceptions
                ) || anyError
            }

        val newChain = parentChain + "$classType"

        val hasMethodError = filteredElements
            .filter { it.kind == ElementKind.METHOD }
            .map { it as ExecutableElement }
            .filterNot { it.modifiers.contains(Modifier.STATIC) }
            .filterNot { IGNORED_METHODS.contains(it.simpleName.toString()) }
            .fold(false) { anyError, method ->
                // Must call visitMethod first so it doesn't get short circuited by the ||
                visitMethod(newChain, seenTypesByPolicy, method, newPolicyExceptions) ||
                        anyError // Use method to point to current method in the errors
            }

        val className = classType.simpleName.toString()
        val isRegularClass = classType.kind == ElementKind.CLASS
        var anyError = hasFieldError || hasClassError || hasMethodError

        // If final classes are not considered OR there's a non-field failure, also check for
        // interface/@Immutable, assuming the class is malformed
        if ((isRegularClass && !allowFinalClassesFinalFields) || hasMethodError || hasClassError) {
            if (classType.getAnnotation(Immutable::class.java) == null) {
                printError(
                    parentChain,
                    elementToPrint,
                    MessageUtils.classNotImmutableFailure(className)
                )
                anyError = true
            }

            if (classType.kind != ElementKind.INTERFACE) {
                printError(parentChain, elementToPrint, MessageUtils.nonInterfaceClassFailure())
                anyError = true
            }
        }

        (classType.interfaces).forEach { // Only process interfaces here
            val element = processingEnv.typeUtils.asElement(it) ?: return@forEach
            if (element is TypeElement) {
                visitClass(
                    parentChain,
                    seenTypesByPolicy,
                    element,
                    element,
                    newPolicyExceptions
                ) // element as the class type, the element parameter
            }
        }
        val superClass = classType.superclass
        if (superClass.kind != TypeKind.NONE) { // Verify that kind is NONE
            val superClassElement = processingEnv.typeUtils.asElement(superClass)
            if (superClassElement is TypeElement) {
                visitClass(
                    parentChain,
                    seenTypesByPolicy,
                    superClassElement,
                    superClassElement,
                    newPolicyExceptions
                )
            }
        }

        if (isRegularClass && !anyError && allowFinalClassesFinalFields &&
            !classType.modifiers.contains(Modifier.FINAL)
        ) {
            printError(parentChain, elementToPrint, MessageUtils.classNotFinalFailure(className))
            return true
        }

        return anyError
    }

    /**
     * @return true if any error was encountered at this level or any child level
     */
    private fun visitMethod(
        parentChain: List<String>,
        seenTypesByPolicy: MutableMap<Set<Immutable.Policy.Exception>, MutableSet<TypeMirror>>,
        method: ExecutableElement,
        parentPolicyExceptions: Set<Immutable.Policy.Exception>,
    ): Boolean {
        val returnType = method.returnType
        val typeName = returnType.toString()
        return when (returnType.kind) {
            TypeKind.BOOLEAN,
            TypeKind.BYTE,
            TypeKind.SHORT,
            TypeKind.INT,
            TypeKind.LONG,
            TypeKind.CHAR,
            TypeKind.FLOAT,
            TypeKind.DOUBLE,
            TypeKind.NONE,
            TypeKind.NULL -> false

            TypeKind.VOID -> {
                if (!method.simpleName.contentEquals("<init>")) {
                    printError(parentChain, method, MessageUtils.voidReturnFailure())
                    true
                } else false
            }

            TypeKind.ARRAY -> {
                printError(parentChain, method, MessageUtils.arrayFailure())
                true
            }

            TypeKind.DECLARED ->
                visitType(
                    parentChain,
                    seenTypesByPolicy,
                    method,
                    returnType,
                    parentPolicyExceptions
                )

            TypeKind.ERROR,
            TypeKind.TYPEVAR,
            TypeKind.WILDCARD,
            TypeKind.PACKAGE,
            TypeKind.EXECUTABLE,
            TypeKind.OTHER,
            TypeKind.UNION,
            TypeKind.INTERSECTION,
            null -> {
                printError(
                    parentChain,
                    method,
                    MessageUtils.genericTypeKindFailure(typeName = typeName)
                )
                true
            }

            else -> {
                printError(
                    parentChain,
                    method,
                    MessageUtils.genericTypeKindFailure(typeName = typeName)
                )
                true
            }
        }
    }

    /**
     * @return true if any error was encountered at this level or any child level
     */
    private fun visitType(
        parentChain: List<String>,
        seenTypesByPolicy: MutableMap<Set<Immutable.Policy.Exception>, MutableSet<TypeMirror>>,
        element: Element,
        type: TypeMirror,
        parentPolicyExceptions: Set<Immutable.Policy.Exception>,
        nonInterfaceClassFailure: () -> String = { MessageUtils.nonInterfaceReturnFailure() },
    ): Boolean {
        // Skip if the symbol being considered is itself ignored
        // Skip if the type being checked, like for a typeArg or return type, is ignored
        // Skip if that typeArg is itself ignored when inspected at the class header level
        if (isIgnored(element) || isIgnored(type) ||
            isIgnored(processingEnv.typeUtils.asElement(type))) return false

        if (type.kind.isPrimitive) return false
        if (type.kind == TypeKind.VOID) {
            printError(parentChain, element, MessageUtils.voidReturnFailure())
            return true
        }

        val policyAnnotation = element.getAnnotation(Immutable.Policy::class.java)
        val newPolicyExceptions = parentPolicyExceptions + policyAnnotation?.exceptions.orEmpty()

        // Key Change: Check assignability *only* if we know it's a declared type,
        // and only *after* other checks
        if (type is DeclaredType) {
            val isMap = processingEnv.typeUtils.isAssignable(type, mapType)
            if (!processingEnv.typeUtils.isAssignable(type, collectionType) && !isMap) {
                val isInterface =
                    processingEnv.typeUtils.asElement(type)?.kind == ElementKind.INTERFACE
                if (!isInterface && !newPolicyExceptions
                    .contains(Immutable.Policy.Exception.FINAL_CLASSES_WITH_FINAL_FIELDS)
                ) {
                    printError(parentChain, element, nonInterfaceClassFailure())
                    return true
                }
                val elementResult = processingEnv.typeUtils.asElement(type)
                if (elementResult is TypeElement) {
                    return visitClass(
                        parentChain, seenTypesByPolicy, element, elementResult, newPolicyExceptions
                    )
                } else {
                    return false
                }
            }
            var anyError = false
            type.typeArguments.forEachIndexed { index, typeArg ->
                if (isIgnored(processingEnv.typeUtils.asElement(typeArg))) return@forEachIndexed
                val argError =
                    visitType(
                        parentChain,
                        seenTypesByPolicy,
                        element,
                        typeArg,
                        newPolicyExceptions
                    ) {
                        val typeArgElement = processingEnv.typeUtils.asElement(typeArg)
                        MessageUtils.nonInterfaceReturnFailure(
                            prefix =
                            when {
                                !isMap -> ""
                                index == 0 -> "Key " + (typeArgElement?.simpleName ?: "")
                                else -> "Value " + (typeArgElement?.simpleName ?: "")
                            },
                            index = index
                        )
                    }
                anyError = anyError || argError
            }
            return anyError
        }
        return false
    }

    private fun printError(parentChain: List<String>, element: Element, message: String) {
        processingEnv.messager.printMessage(
            Diagnostic.Kind.ERROR,
            parentChain.plus(element.simpleName).joinToString() + "\n\t " + message,
            element,
        )
    }

    private fun ProcessingEnvironment.erasedType(typeName: String) =
        elementUtils.getTypeElement(typeName)?.asType()?.let(typeUtils::erasure)

    private fun isIgnored(type: TypeMirror): Boolean {
        return try {
            val ignoreAnnotation =
                type.annotationMirrors.find {
                    it.annotationType.toString() == Immutable.Ignore::class.qualifiedName
                }
            ignoreAnnotation != null ||
                    ignoredSuperTypes.any { processingEnv.typeUtils.isAssignable(type, it) } ||
                    ignoredExactTypes.any { processingEnv.typeUtils.isSameType(type, it) }
        } catch (e: IllegalArgumentException) {
            false // If isAssignable/isSameType throws, consider it not ignored.
        }
    }

    private fun isIgnored(element: Element): Boolean {

        return try {
            when {
                // Anything annotated as @Ignore is always ignored
                element.annotationMirrors.any {
                    it.annotationType.toString() == Immutable.Ignore::class.qualifiedName
                } -> true

                // Then ignore exact types, regardless of what kind they are
                ignoredExactTypes.any {
                    processingEnv.typeUtils.isSameType(
                        element.asType(),
                        it
                    )
                } -> true

                // Then only allow methods through, since other types (fields) are usually a failure
                element.kind != ElementKind.METHOD -> false
                // Finally, check for any ignored super types
                else -> ignoredSuperTypes.any {
                    processingEnv.typeUtils.isAssignable(
                        element.asType(),
                        it
                    )
                }
            }
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
