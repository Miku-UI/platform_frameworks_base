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
package android.platform.test.ravenwood;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import org.objectweb.asm.Type;

import java.lang.reflect.Executable;

/**
 * Utility methods related to ASM.
 */
public class RavenwoodAsmUtils {
    private static final String TAG = "RavenwoodAsmUtils";

    private RavenwoodAsmUtils() {
    }

    /**
     * Converts an ASM Type to a Java Class.
     */
    @NonNull
    public static Class<?> toClass(@NonNull Type type)
            throws ClassNotFoundException {
        var cl = RavenwoodAsmUtils.class.getClassLoader();
        return switch (type.getSort()) {
            case Type.VOID    -> void.class;
            case Type.BOOLEAN -> boolean.class;
            case Type.CHAR    -> char.class;
            case Type.BYTE    -> byte.class;
            case Type.SHORT   -> short.class;
            case Type.INT     -> int.class;
            case Type.FLOAT   -> float.class;
            case Type.LONG    -> long.class;
            case Type.DOUBLE  -> double.class;
            case Type.ARRAY   -> Class.forName(type.getDescriptor().replace('/', '.'),
                    false, cl);
            case Type.OBJECT  -> Class.forName(type.getClassName(), false, cl);
            default           -> throw new ClassNotFoundException("Unsupported ASM Type: " + type);
        };
    }

    /**
     * Finds a Method or a Constructor object using its class, name, and ASM-style descriptor.
     *
     * Note, the static initializer ("<clinit>") can't be accessed via reflections. It'd cause
     * {@link NoSuchMethodException}.
     */
    @NonNull
    private static Executable getMethod(
            @NonNull Class<?> clazz,
            @NonNull String methodName,
            @NonNull String methodDesc)
            throws ClassNotFoundException, NoSuchMethodException {

        // 1. Use ASM to parse the method descriptor
        Type[] asmParamTypes = Type.getArgumentTypes(methodDesc);
        Class<?>[] javaParamTypes = new Class<?>[asmParamTypes.length];

        // 2. Convert ASM Type objects to Java Class objects
        for (int i = 0; i < asmParamTypes.length; i++) {
            javaParamTypes[i] = toClass(asmParamTypes[i]);
        }

        // 3. Use standard Java Reflection to find the method
        if (methodName.equals("<init>")) {
            return clazz.getDeclaredConstructor(javaParamTypes);
        } else {
            return clazz.getDeclaredMethod(methodName, javaParamTypes);
        }
    }

    /**
     * Same as {@link #getMethod} but it'll return null for errors instead of exceptions.
     */
    @Nullable
    public static Executable getMethodOrNull(
            @NonNull Class<?> clazz,
            @NonNull String methodName,
            @NonNull String methodDesc) {
        if (methodName.equals("<clinit>")) {
            return null; // static initializer can't be resolved.
        }
        try {
            return getMethod(clazz, methodName, methodDesc);
        } catch (Exception e) {
            Log.w(TAG, String.format(
                    "Method not found: class=%s name=%s desc=%s",
                    clazz, methodName, methodDesc));
            return null;
        }
    }
}
