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
package com.android.hoststubgen.visitors

import com.android.hoststubgen.asm.CTOR_NAME
import com.android.hoststubgen.asm.prependArgTypeToMethodDescriptor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL

private const val JDK_PATCH_CLASS = "com/android/ravenwood/RavenwoodJdkPatch"

private val classRemap = mapOf(
    "java/io/FileInputStream" to "com/android/ravenwood/RavenwoodFileInputStream",
    "java/io/FileOutputStream" to "com/android/ravenwood/RavenwoodFileOutputStream",
)

private val methodRemap = mapOf(
    "java/io/FileDescriptor" to mapOf(
        "getInt\$" to "getInt\$",
        "setInt\$" to "setInt\$",
    ),
    "java/util/LinkedHashMap" to mapOf(
        "eldest" to "eldest",
    ),
    "java/util/regex/Pattern" to mapOf(
        "compile" to "compilePattern",
    ),
)

class JdkPatchVisitor(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM9, cv) {

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
    ) {
        if (superName != null) {
            val newSuper = classRemap.getOrDefault(superName, superName)
            super.visit(version, access, name, signature, newSuper, interfaces)
        } else {
            super.visit(version, access, name, signature, null, interfaces)
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor {
        return MethodCallRewrite(super.visitMethod(access, name, descriptor, signature, exceptions))
    }

    class MethodCallRewrite(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM9, mv) {

        override fun visitTypeInsn(opcode: Int, type: String) {
            // We only care about NEW and JDK classes
            if (opcode == Opcodes.NEW && type.startsWith("java/")) {
                super.visitTypeInsn(opcode, classRemap.getOrDefault(type, type))
            } else {
                super.visitTypeInsn(opcode, type)
            }
        }

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            if (!owner.startsWith("java/")) {
                // We only care about patching JDK methods
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                return
            }

            when (opcode) {
                INVOKEVIRTUAL, INVOKESTATIC -> {
                    methodRemap[owner]?.let { it[name] }?.let { newName ->
                        val desc = if (opcode == INVOKEVIRTUAL) {
                            prependArgTypeToMethodDescriptor(descriptor, owner)
                        } else {
                            descriptor
                        }
                        super.visitMethodInsn(
                            INVOKESTATIC, JDK_PATCH_CLASS, newName, desc, isInterface)
                        return
                    }
                }
                INVOKESPECIAL -> {
                    if (name == CTOR_NAME) {
                        val newOwner = classRemap.getOrDefault(owner, owner)
                        super.visitMethodInsn(opcode, newOwner, name, descriptor, isInterface)
                        return
                    }
                }
            }

            // Default to call super
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
    }
}
