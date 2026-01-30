/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen.visitors

import com.android.hoststubgen.HostStubGenErrors
import com.android.hoststubgen.HostStubGenInternalException
import com.android.hoststubgen.asm.CLASS_INITIALIZER_DESC
import com.android.hoststubgen.asm.CLASS_INITIALIZER_NAME
import com.android.hoststubgen.asm.CTOR_NAME
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.UnifiedVisitor
import com.android.hoststubgen.asm.UnifiedVisitor.Companion.addParams
import com.android.hoststubgen.asm.adjustStackForConstructorRedirection
import com.android.hoststubgen.asm.changeMethodDescriptorReturnType
import com.android.hoststubgen.asm.getPackageNameFromFullClassName
import com.android.hoststubgen.asm.isEnum
import com.android.hoststubgen.asm.prependArgTypeToMethodDescriptor
import com.android.hoststubgen.asm.reasonParam
import com.android.hoststubgen.asm.toJvmClassName
import com.android.hoststubgen.asm.writeByteCodeToPushArguments
import com.android.hoststubgen.asm.writeByteCodeToReturn
import com.android.hoststubgen.asm.writeByteCodeToReturnDefault
import com.android.hoststubgen.filters.FilterPolicy
import com.android.hoststubgen.filters.FilterPolicyWithReason
import com.android.hoststubgen.filters.OutputFilter
import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsExperimental
import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsIgnore
import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsKeep
import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsSubstitute
import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsThrow
import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsThrowButSupported
import com.android.hoststubgen.hosthelper.HostTestUtils
import com.android.hoststubgen.log
import com.android.hoststubgen.utils.ClassDescriptorSet
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Type
import java.lang.annotation.RetentionPolicy

const val OPCODE_VERSION = Opcodes.ASM9

/**
 * An adapter that generates the "impl" class file from an input class file.
 */
class ImplGeneratingAdapter(
    val classes: ClassNodes,
    nextVisitor: ClassVisitor,
    val filter: OutputFilter,
    val options: Options,
) : ClassVisitor(OPCODE_VERSION, nextVisitor) {

    /**
     * Options to control the behavior.
     */
    data class Options(
        val errors: HostStubGenErrors,

        val deleteClassFinals: Boolean,
        val deleteMethodFinals: Boolean,
        // We don't remove finals from fields, because final fields have a stronger memory
        // guarantee than non-final fields, see:
        // https://docs.oracle.com/javase/specs/jls/se22/html/jls-17.html#jls-17.5
        // i.e. changing a final field to non-final _could_ result in different behavior.
        // val deleteFieldFinals: Boolean,

        val throwExceptionType: String,

        // We make all annotations in this set "runtime-visible".
        val annotationsToMakeVisible: ClassDescriptorSet,

        val experimentalMethodCallHook: String?,
    )

    private lateinit var currentPackageName: String
    private lateinit var currentClassName: String
    private var redirectionClass: String? = null
    private lateinit var classPolicy: FilterPolicyWithReason

    private var classLoadHooks: List<String> = emptyList()

    private fun maybeRemoveFinalFromClass(access: Int): Int {
        if (options.deleteClassFinals && !isEnum(access)) {
            return access and Opcodes.ACC_FINAL.inv()
        }
        return access
    }

    private fun maybeRemoveFinalFromMethod(access: Int): Int {
        if (options.deleteMethodFinals) {
            return access and Opcodes.ACC_FINAL.inv()
        }
        return access
    }

    override fun visitInnerClass(
        name: String?,
        outerName: String?,
        innerName: String?,
        access: Int,
    ) {
        super.visitInnerClass(name, outerName, innerName, maybeRemoveFinalFromClass(access))
    }

    override fun visit(
        version: Int,
        origAccess: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>
    ) {
        val access = maybeRemoveFinalFromClass(origAccess)

        super.visit(version, access, name, signature, superName, interfaces)

        currentClassName = name
        currentPackageName = getPackageNameFromFullClassName(name)
        classPolicy = filter.getPolicyForClass(currentClassName)
        redirectionClass = filter.getRedirectionClass(currentClassName)
        log.d("[%s] visit: %s (package: %s)", javaClass.simpleName, name, currentPackageName)
        log.indent()
        log.v("Emitting class: %s", name)
        log.indent()
        // Inject annotations to generated classes.
        UnifiedVisitor.on(this).visitAnnotation(
            HostStubGenProcessedAsKeep.CLASS_DESCRIPTOR, true, reasonParam(classPolicy.reason))

        classLoadHooks = filter.getClassLoadHooks(currentClassName)

        // classLoadHookMethod is non-null, then we need to inject code to call it
        // in the class initializer.
        // If the target class already has a class initializer, then we need to inject code to it.
        // Otherwise, we need to create one.

        if (classLoadHooks.isNotEmpty()) {
            log.d("  ClassLoadHooks: $classLoadHooks")
            if (!classes.hasClassInitializer(currentClassName)) {
                injectClassLoadHook()
            }
        }
    }

    override fun visitEnd() {
        log.unindent()
        log.unindent()
        super.visitEnd()
    }

    /**
     * Tweak annotation "visibility" aka "retention policy".
     */
    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        // If it's a "known" annotation -- i.e. any Ravenwood annotations -- we do the following:
        // 1. For the annotation type itself, change the retention policy to "RUNTIME".
        // 2. Make the annotation "runtime-visible" across the whole jar.

        // For 1.
        if (options.annotationsToMakeVisible.contains(currentClassName)) {
            // This current type is a known annotation. We change the retention policy.

            if ("Ljava/lang/annotation/Retention;" == descriptor) {
                // If it is, we return our custom AnnotationVisitor to modify its value.
                // We pass the original visitor from the superclass to maintain the chain.
                return RetentionPolicyAnnotationVisitor(
                    super.visitAnnotation(descriptor, visible))
            }
        }

        // For 2. If the annotation we're processing now is "known" (i.e. RavenwoodKeep, etc),
        // force it to be "runtime-visible" aka RUNTIME.
        return super.visitAnnotation(descriptor,
            visible || options.annotationsToMakeVisible.contains(descriptor))
    }

    var skipMemberModificationNestCount = 0

    /**
     * This method allows writing class members without any modifications.
     */
    private inline fun writeRawMembers(callback: () -> Unit) {
        skipMemberModificationNestCount++
        try {
            callback()
        } finally {
            skipMemberModificationNestCount--
        }
    }

    /**
     * Split a class + method string into internal class name + method name
     * e.g.: "com.example.MyClass.myMethod" -> ("com/example/MyClass", "myMethod")
     */
    private fun String.parseClassMethod(): Pair<String, String> {
        val split = lastIndexOf('.')
        if (split < 0) {
            options.errors.onErrorFound(
                "Unable to find class and method name: malformed string \"%s\"".format(this))
        }
        return substring(0, split).replace('.', '/') to substring(split + 1)
    }

    private fun injectClassLoadHook() {
        writeRawMembers {
            // Create a class initializer to call onClassLoaded().
            // Each class can only have at most one class initializer, but the base class
            // StaticInitMerger will merge it with the existing one, if any.
            visitMethod(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                CLASS_INITIALIZER_NAME,
                "()V",
                null,
                null
            )!!.let { mv ->
                // Method prologue
                mv.visitCode()

                writeClassLoadHookCalls(mv)
                mv.visitInsn(Opcodes.RETURN)

                // Method epilogue
                mv.visitMaxs(0, 0)
                mv.visitEnd()
            }
        }
    }

    private fun writeClassLoadHookCalls(mv: MethodVisitor) {
        classLoadHooks.forEach { classLoadHook ->
            // First argument: the class type.
            mv.visitLdcInsn(Type.getType("L$currentClassName;"))

            // Call classLoadHook
            val (clazz, method) = classLoadHook.parseClassMethod()
            mv.visitMethodInsn(INVOKESTATIC, clazz, method, "(Ljava/lang/Class;)V", false)
        }
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?,
    ): FieldVisitor? {
        if (skipMemberModificationNestCount > 0) {
            return super.visitField(access, name, descriptor, signature, value)
        }
        val policy = filter.getPolicyForField(currentClassName, name)
        log.d("visitField: %s %s [%x] Policy: %s", name, descriptor, access, policy)

        log.withIndent {
            if (policy.policy == FilterPolicy.Remove) {
                log.d("Removing %s %s", name, policy)
                return null
            }

            log.v("Emitting field: %s %s %s", name, descriptor, policy)
            val ret = super.visitField(access, name, descriptor, signature, value)

            UnifiedVisitor.on(ret)
                .visitAnnotation(HostStubGenProcessedAsKeep.CLASS_DESCRIPTOR, true, reasonParam(policy.reason))

            return ForceFieldAnnotationVisibilityVisitor(ret)
        }
    }

    private fun updateMethodAccessFlags(
        access: Int,
        name: String,
        descriptor: String,
        policy: FilterPolicy,
    ): Int {
        if (policy.isMethodRewriteBody) {
            // If we are rewriting the entire method body, we need
            // to convert native methods to non-native
            return access and Opcodes.ACC_NATIVE.inv()
        }
        return access
    }

    override fun visitMethod(
        origAccess: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val access = maybeRemoveFinalFromMethod(origAccess)
        if (skipMemberModificationNestCount > 0) {
            return super.visitMethod(access, name, descriptor, signature, exceptions)
        }
        val p = filter.getPolicyForMethod(currentClassName, name, descriptor)
        log.d("visitMethod: %s%s [%x] [%s] Policy: %s", name, descriptor, access, signature, p)

        log.withIndent {
            // If it's a substitute-from method, then skip (== remove).
            // Instead of this method, we rename the substitute-to method with the original
            // name, in the "Maybe rename the method" part below.
            val policy = filter.getPolicyForMethod(currentClassName, name, descriptor)
            if (policy.policy == FilterPolicy.Substitute) {
                log.d("Skipping %s%s %s", name, descriptor, policy)
                return null
            }
            if (p.policy == FilterPolicy.Remove) {
                log.d("Removing %s%s %s", name, descriptor, policy)
                return null
            }

            var newAccess = access

            // Maybe rename the method.
            val newName: String
            val renameTo = filter.getRenameTo(currentClassName, name, descriptor)
            if (renameTo != null) {
                newName = renameTo

                // It's confusing, but here, `newName` is the original method name
                // (the one with the @substitute/replace annotation).
                // `name` is the name of the method we're currently visiting, so it's usually a
                // "...$ravewnwood" name.
                newAccess = checkSubstitutionMethodCompatibility(
                    classes, currentClassName, newName, name, descriptor, options.errors
                )
                if (newAccess == NOT_COMPATIBLE) {
                    return null
                }
                newAccess = maybeRemoveFinalFromMethod(newAccess)

                log.v(
                    "Emitting %s.%s%s as %s %s", currentClassName, name, descriptor,
                    newName, policy
                )
            } else {
                log.v("Emitting method: %s%s %s", name, descriptor, policy)
                newName = name
            }

            // Let subclass update the flag.
            // But note, we only use it when calling the super's method,
            // but not for visitMethodInner(), because when subclass wants to change access,
            // it can do so inside visitMethodInner().
            newAccess = updateMethodAccessFlags(newAccess, name, descriptor, policy.policy)

            val ret = visitMethodInner(
                access, newName, descriptor, signature, exceptions, policy,
                renameTo != null,
                super.visitMethod(newAccess, newName, descriptor, signature, exceptions)
            )

            return ForceMethodAnnotationVisibilityVisitor(ret)
        }
    }

    private fun visitMethodInner(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?,
        policy: FilterPolicyWithReason,
        substituted: Boolean,
        superVisitor: MethodVisitor?,
    ): MethodVisitor? {
        var innerVisitor = superVisitor

        //  If method logging is enabled, inject call to the logging method.
        val methodCallHooks = filter.getMethodCallHooks(currentClassName, name, descriptor)
        if (methodCallHooks.isNotEmpty()) {
            innerVisitor = MethodCallHookInjectingAdapter(
                name,
                descriptor,
                methodCallHooks,
                innerVisitor,
            )
        }

        // If this class already has a class initializer and a class load hook is needed, then
        // we inject code.
        if (classLoadHooks.isNotEmpty() &&
            name == CLASS_INITIALIZER_NAME &&
            descriptor == CLASS_INITIALIZER_DESC
        ) {
            innerVisitor = ClassLoadHookInjectingMethodAdapter(innerVisitor)
        }

        fun MethodVisitor.withAnnotation(descriptor: String, reason: String): MethodVisitor {
            this.visitAnnotation(descriptor, true).addParams(reasonParam(reason))
            return this
        }

        log.withIndent {
            // When we encounter native methods, we want to forcefully
            // inject a method body. Also see [updateAccessFlags].
            val forceCreateBody = (access and Opcodes.ACC_NATIVE) != 0

            when (policy.policy) {
                FilterPolicy.Throw -> {
                    log.v("Making method throw...")
                    val annot = if (policy.statsLabel.isSupported) {
                        HostStubGenProcessedAsThrowButSupported.CLASS_DESCRIPTOR
                    } else {
                        HostStubGenProcessedAsThrow.CLASS_DESCRIPTOR
                    }
                    return ThrowingMethodAdapter(
                        options.throwExceptionType.toJvmClassName(),
                        forceCreateBody,
                        innerVisitor
                    ).withAnnotation(annot, policy.reason)
                }
                FilterPolicy.Ignore -> {
                    log.v("Making method ignored...")
                    return IgnoreMethodAdapter(descriptor, forceCreateBody, innerVisitor)
                        .withAnnotation(HostStubGenProcessedAsIgnore.CLASS_DESCRIPTOR, policy.reason)
                }
                FilterPolicy.Redirect -> {
                    log.v("Redirecting method...")
                    return RedirectMethodAdapter(
                        access, name, descriptor,
                        forceCreateBody, innerVisitor
                    ).withAnnotation(HostStubGenProcessedAsSubstitute.CLASS_DESCRIPTOR, policy.reason)
                }
                FilterPolicy.Experimental -> {
                    if (options.experimentalMethodCallHook == null) {
                        options.errors.onErrorFound(
                            "Experimental policy used in $currentClassName#$name, but " +
                                    "--experimental-method-call-hook is not set.")
                    }
                    log.v("Making method experimental...")
                    innerVisitor = innerVisitor?.let {
                        ReturnDecidingMethodCallHookInjectingAdapter(
                            name,
                            descriptor,
                            listOf(options.experimentalMethodCallHook!!),
                            it,
                        ).withAnnotation(HostStubGenProcessedAsExperimental.CLASS_DESCRIPTOR, policy.reason)
                    }
                }
                else -> {
                    if (substituted) {
                        innerVisitor?.withAnnotation(HostStubGenProcessedAsSubstitute.CLASS_DESCRIPTOR, policy.reason)
                    } else {
                        innerVisitor?.withAnnotation(HostStubGenProcessedAsKeep.CLASS_DESCRIPTOR, policy.reason)
                    }
                }
            }
        }

        if (filter.hasAnyMethodCallReplace()) {
            innerVisitor = MethodCallReplacingAdapter(name, innerVisitor)
        }

        return innerVisitor
    }

    /**
     * A method adapter that replaces the method body with a `throw new ExceptionType()` call.
     */
    private inner class ThrowingMethodAdapter(
        private val exceptionType: String,
        createBody: Boolean,
        next: MethodVisitor?
    ) : BodyReplacingMethodVisitor(createBody, next) {
        override fun emitNewCode() {
            visitTypeInsn(Opcodes.NEW, exceptionType)
            visitInsn(Opcodes.DUP)
            visitMethodInsn(INVOKESPECIAL, exceptionType, "<init>", "()V", false)
            visitInsn(Opcodes.ATHROW)

            // visitMaxs(3, if (isStatic) 0 else 1)
            visitMaxs(0, 0) // We let ASM figure them out.
        }
    }

    /**
     * A method adapter that replaces the method body with a no-op return.
     */
    private inner class IgnoreMethodAdapter(
        val descriptor: String,
        createBody: Boolean,
        next: MethodVisitor?
    ) : BodyReplacingMethodVisitor(createBody, next) {
        override fun emitNewCode() {
            writeByteCodeToReturnDefault(descriptor, this)
            visitMaxs(0, 0) // We let ASM figure them out.
        }
    }

    /**
     * A method adapter that rewrite a method body with a
     * call to a method in the redirection class.
     */
    private inner class RedirectMethodAdapter(
        access: Int,
        private val name: String,
        private val descriptor: String,
        createBody: Boolean,
        next: MethodVisitor?
    ) : BodyReplacingMethodVisitor(createBody, next) {

        private val isStatic = (access and Opcodes.ACC_STATIC) != 0

        override fun emitNewCode() {
            var targetDescriptor = descriptor
            var argOffset = 0

            // For non-static method, we need to tweak it a bit.
            if (!isStatic) {
                // Push `this` as the first argument.
                this.visitVarInsn(Opcodes.ALOAD, 0)

                // Update the descriptor -- add this class's type as the first argument
                // to the method descriptor.
                targetDescriptor = prependArgTypeToMethodDescriptor(
                    descriptor,
                    currentClassName,
                )

                // Shift the original arguments by one.
                argOffset = 1
            }

            writeByteCodeToPushArguments(descriptor, this, argOffset)

            visitMethodInsn(
                INVOKESTATIC,
                redirectionClass,
                name,
                targetDescriptor,
                false
            )

            writeByteCodeToReturn(descriptor, this)

            visitMaxs(99, 0) // We let ASM figure them out.
        }
    }

    /**
     * Inject calls to the method call hooks.
     *
     * Note, when the target method is a constructor, it may contain calls to `super(...)` or
     * `this(...)`. The hook method call will be injected *before* such calls.
     */
    private inner class MethodCallHookInjectingAdapter(
        val name: String,
        val descriptor: String,
        val hooks: List<String>,
        next: MethodVisitor?,
    ) : MethodVisitor(OPCODE_VERSION, next) {
        override fun visitCode() {
            super.visitCode()

            hooks.forEach { hook ->
                mv.visitLdcInsn(Type.getType("L$currentClassName;"))
                visitLdcInsn(name)
                visitLdcInsn(descriptor)

                val (clazz, method) = hook.parseClassMethod()
                visitMethodInsn(INVOKESTATIC, clazz, method,
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)V",
                    false
                )
            }
        }
    }


    /**
     * Similar to [MethodCallHookInjectingAdapter], but the hook must return boolean.
     *
     * The hook must always return true, except in <clinit>, it can return false. If the hook
     * returns false, we return from the method, without running any of the remaining code.
     *
     * Can we use false for other methods?
     * - No, for <init>, beucase we always must call super's <init>. We can never return
     *   early from <init.
     * - For other methods, we could.
     */
    private inner class ReturnDecidingMethodCallHookInjectingAdapter(
        val name: String,
        val descriptor: String,
        val hooks: List<String>,
        next: MethodVisitor?,
    ) : MethodVisitor(OPCODE_VERSION, next) {
        /**
         * Whether or not the hook can return false to skip the rest of the method body.
         */
        val allowReturn = name == "<clinit>"

        override fun visitCode() {
            super.visitCode() // Note it's possible nested visitor added code too

            hooks.forEach { hook ->
                mv.visitLdcInsn(Type.getType("L$currentClassName;"))
                visitLdcInsn(name)
                visitLdcInsn(descriptor)

                val (clazz, method) = hook.parseClassMethod()
                visitMethodInsn(INVOKESTATIC, clazz, method,
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)Z",
                    false
                )
                if (!allowReturn) {
                    // We don't allow "return". The hook must always return true.
                    visitMethodInsn(INVOKESTATIC, HostTestUtils.CLASS_INTERNAL_NAME,
                        HostTestUtils.ASSERT_THAT_HOOK_RETURNED_TRUE,
                        "(Z)V",
                        false
                    )
                } else {
                    var label = Label()

                    visitJumpInsn(Opcodes.IFNE, label)
                    if (Type.getReturnType(descriptor) != Type.VOID_TYPE) {
                        // If we ever want to use it for non-void method,
                        // The below visitFrame() call needs to accommodate that.
                        // (Need some investigation to implement it properly...)
                        throw HostStubGenInternalException(
                            "This feature for non-void method isn't implemented yet")
                    }
                    writeByteCodeToReturnDefault(descriptor, this)

                    visitLabel(label)

                    // We need to inject a frame.
                    //
                    // We assume our method cal is the first thing we do in this method
                    // and the frame can be empty.
                    // In reality, it's possible the "next" visitors created more with frames,
                    // in super.visitCode(), in that case this would probably break...
                    //
                    super.visitFrame(Opcodes.F_NEW, 0, null, 0, null)
                }
            }
        }
    }

    /**
     * Inject a class load hook call.
     */
    private inner class ClassLoadHookInjectingMethodAdapter(
        next: MethodVisitor?
    ) : MethodVisitor(OPCODE_VERSION, next) {
        override fun visitCode() {
            super.visitCode()

            writeClassLoadHookCalls(this)
        }
    }

    private inner class MethodCallReplacingAdapter(
        val callerMethodName: String,
        next: MethodVisitor?,
    ) : MethodVisitor(OPCODE_VERSION, next) {

        private fun doReplace(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
        ): Boolean {
            when (opcode) {
                INVOKESTATIC, INVOKEVIRTUAL, INVOKEINTERFACE -> {}
                // We only support INVOKESPECIAL when replacing constructors.
                INVOKESPECIAL -> if (name != CTOR_NAME) return false
                // Don't touch other opcodes.
                else -> return false
            }

            val to = filter.getMethodCallReplaceTo(
                owner, name, descriptor
            )

            if (to == null
                // Don't replace if the target is the callsite.
                || (to.className == currentClassName && to.methodName == callerMethodName)
            ) {
                return false
            }

            if (opcode != INVOKESPECIAL) {
                // It's either a static method call or virtual method call.
                // Either way, we don't manipulate the stack and send the original arguments
                // as is to the target method.
                //
                // If the call is a virtual call (INVOKEVIRTUAL or INVOKEINTERFACE), then
                // the first argument in the stack is the "this" object, so the target
                // method must have an extra argument as the first argument to receive it.
                // We update the method descriptor with prependArgTypeToMethodDescriptor()
                // to absorb this difference.

                val toDesc = if (opcode == INVOKESTATIC) {
                    descriptor
                } else {
                    prependArgTypeToMethodDescriptor(descriptor, owner)
                }

                mv.visitMethodInsn(
                    INVOKESTATIC,
                    to.className,
                    to.methodName,
                    toDesc,
                    false
                )
            } else {
                // Because an object initializer does not return a value, the newly created
                // but uninitialized object will be dup-ed at the bottom of the stack.
                // We first call the target method to consume the constructor arguments at the top.

                val toDesc = changeMethodDescriptorReturnType(descriptor, owner)

                // Before stack: { uninitialized, uninitialized, args... }
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    to.className,
                    to.methodName,
                    toDesc,
                    false
                )
                // After stack: { uninitialized, uninitialized, obj }

                // Next we pop the 2 uninitialized instances out of the stack.
                adjustStackForConstructorRedirection(mv)
            }

            return true
        }

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean,
        ) {
            if (!doReplace(opcode, owner, name, descriptor)) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }



    /**
     * An AnnotationVisitor that specifically targets the `value` of a @Retention
     * annotation and forces it to be `RUNTIME`.
     */
    class RetentionPolicyAnnotationVisitor(
        next: AnnotationVisitor?,
    ) : AnnotationVisitor(OPCODE_VERSION, next) {
        override fun visitEnum(name: String?, descriptor: String?, value: String?) {
            // We only care about the "value" property of the @Retention annotation.
            if ("value" == name && "Ljava/lang/annotation/RetentionPolicy;" == descriptor) {
                super.visitEnum(name, descriptor, RetentionPolicy.RUNTIME.name)
            } else {
                // For any other enum property, delegate to the default behavior.
                super.visitEnum(name, descriptor, value)
            }
        }
    }

    /**
     * Force a field's annotation to be runtime-visible if it's "known".
     */
    inner class ForceFieldAnnotationVisibilityVisitor(
        next: FieldVisitor?,
    ) : FieldVisitor(OPCODE_VERSION, next) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            return super.visitAnnotation(descriptor,
                visible || options.annotationsToMakeVisible.contains(descriptor))
        }
    }

    /**
     * Force a method's annotation to be runtime-visible if it's "known".
     */
    inner class ForceMethodAnnotationVisibilityVisitor(
        next: MethodVisitor?,
    ) : MethodVisitor(OPCODE_VERSION, next) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            return super.visitAnnotation(descriptor,
                visible || options.annotationsToMakeVisible.contains(descriptor))
        }
    }
}
