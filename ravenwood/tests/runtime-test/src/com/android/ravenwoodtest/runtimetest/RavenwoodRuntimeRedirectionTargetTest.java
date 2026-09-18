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
package com.android.ravenwoodtest.runtimetest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.util.Log;

import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsRedirect;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import org.junit.Test;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Make sure all `@RavenwoodRedirect`s has valid target methods.
 */
public class RavenwoodRuntimeRedirectionTargetTest {
    private static final String TAG = "RavenwoodRuntimeRedirectionTargetTest";

    private static final Map<String, Class<?>> PRIMITIVE_TYPE_MAP = new HashMap<>();
    static {
        PRIMITIVE_TYPE_MAP.put("int", int.class);
        PRIMITIVE_TYPE_MAP.put("long", long.class);
        PRIMITIVE_TYPE_MAP.put("double", double.class);
        PRIMITIVE_TYPE_MAP.put("float", float.class);
        PRIMITIVE_TYPE_MAP.put("boolean", boolean.class);
        PRIMITIVE_TYPE_MAP.put("char", char.class);
        PRIMITIVE_TYPE_MAP.put("byte", byte.class);
        PRIMITIVE_TYPE_MAP.put("short", short.class);
        PRIMITIVE_TYPE_MAP.put("void", void.class);    }

    /** Converts an ASM's {@link Type} object to its corresponding java class object. */
    private static Class<?> typeToClazz(Type asmType) throws ClassNotFoundException {
        Class<?> paramClass = PRIMITIVE_TYPE_MAP.get(asmType.getClassName());
        if (paramClass != null) {
            return paramClass;
        }
        var name = asmType.getInternalName().replace('/', '.');
        return Class.forName(name);
    }

    private static boolean shouldCheckClass(ClassInfo cinfo) {
        var packageName = cinfo.getPackageName();
        return !packageName.startsWith("java.")
                && !packageName.startsWith("javax.")
                && !packageName.startsWith("org.junit.")
                && !packageName.startsWith("org.mockito.")
                && !packageName.startsWith("kotlin.")
                && !packageName.startsWith("kotlinx.")
                && !packageName.startsWith("androidx.")
                && !packageName.startsWith("org.objectweb.")
                && !packageName.startsWith("net.bytebuddy.");
    }

    /**
     * Look for all loadable classes in the classpath, find methods with `@RavenwoodRedirect`s,
     * and make sure their target methods exist.
     */
    @Test
    public void testRedirectionTargetsExist() throws Exception {
        Log.w(TAG, "Check start");
        var totalClasses = 0;
        var loadableClasses = 0;

        var errors = new StringBuilder();

        // Check all loadable classes.
        for (var cinfo : ClassPath.from(ClassLoader.getSystemClassLoader())
                .getAllClasses()) {
            if (!shouldCheckClass(cinfo)) {
                continue;
            }
            totalClasses++;
            try {
                Class<?> clazz = cinfo.load();

                loadableClasses++;

                checkClass(clazz, errors);

            } catch (Throwable e) {
                Log.e(TAG, "Class not loadable: " + cinfo.getName());
            }
        }
        Log.i(TAG, String.format(
                "Check finish: totalClasses=%d, loadableClasses=%d",
                totalClasses, loadableClasses));
        assertThat(errors.toString()).isEmpty();
    }

    private void checkClass(Class<?> clazz, StringBuilder errors) {
        // Skip if the class doesn't have this annotation.
        if (!clazz.isAnnotationPresent(HostStubGenProcessedAsRedirect.class)) {
            return;
        }

        for (Method m : clazz.getDeclaredMethods()) {
            var an = m.getAnnotation(HostStubGenProcessedAsRedirect.class);
            if (an != null) {
                checkAnnotatedMethod(m, an, errors);
            }
        }
    }

    private static Method findMethod(String className, String methodName, String methodDesc)
            throws ClassNotFoundException, NoSuchMethodException {

        // 1. Load the target class
        String binaryName = className.replace('/', '.');
        Class<?> ownerClass = Class.forName(binaryName);

        // 2. Parse the method descriptor to get parameter types
        Type[] asmArgTypes = Type.getArgumentTypes(methodDesc);
        List<Class<?>> paramClasses = new ArrayList<>();

        for (Type asmType : asmArgTypes) {
            paramClasses.add(typeToClazz(asmType));
        }

        // 4. Find the method using reflection
        Class<?>[] paramArray = paramClasses.toArray(new Class<?>[0]);
        var method = ownerClass.getDeclaredMethod(methodName, paramArray);

        // 5. Make sure the return type is correct.
        var retClazz = typeToClazz(Type.getReturnType(methodDesc));
        assertWithMessage("Wrong method return type").that(method.getReturnType())
                .isEqualTo(retClazz);

        return method;
    }

    private void checkAnnotatedMethod(Method m, HostStubGenProcessedAsRedirect an,
            StringBuilder errors) {
        var description = String.format("%s.%s -> %s.%s%s",
                m.getDeclaringClass().getName(), m.getName(),
                an.targetClass(), an.targetMethod(), an.targetDesc());
        Log.i(TAG, "Found redirecting method: " + description);

        // Make sure the target method exists.
        try {
            findMethod(an.targetClass(), an.targetMethod(), an.targetDesc());

        } catch (Throwable e) {
            var msg = "Unable to find target method for {" + description + "}";
            errors.append(msg + ": " + e + "\n");
            Log.e(TAG, msg, e);
        }
    }
}
