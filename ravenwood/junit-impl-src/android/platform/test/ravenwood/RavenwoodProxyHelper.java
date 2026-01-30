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
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Helper class to create proxy instances for AIDL interfaces.
 */
public class RavenwoodProxyHelper {
    private static final String TAG = "RavenwoodProxyHelper";

    private RavenwoodProxyHelper() {
    }

    /**
     * Creates a new Proxy object for a type, which also logs all called method names.
     */
    @SuppressWarnings("unchecked")
    public static <T> T newProxy(Class<T> clazz, InvocationHandler ih) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
                new LoggingInvocationWrapper<>(clazz, ih));
    }

    /**
     * InvocationHandler that always returns the default value.
     */
    public static final InvocationHandler sDefaultHandler =
            new DefaultReturningInvocationHandler();

    /**
     * InvocationHandler that always throws {@link RavenwoodUnsupportedApiException}.
     */
    public static final InvocationHandler sNotImplementedHandler = (p, m, a) -> {
        throw new RavenwoodUnsupportedApiException();
    };

    /**
     * Wraps another {@link InvocationHandler}, and prints the method information
     * in every call.
     */
    private static class LoggingInvocationWrapper<I> implements InvocationHandler {
        private final Class<I> mInterface;
        private final InvocationHandler mInner;

        private LoggingInvocationWrapper(Class<I> anInterface, InvocationHandler inner) {
            mInterface = anInterface;
            mInner = inner;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.w(TAG, "Proxy called: " + mInterface.getSimpleName() + "." + method);
            return mInner.invoke(proxy, method, args);
        }
    }

    /**
     * InvocationHandler that always returns the default value.
     */
    private static class DefaultReturningInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var t = method.getReturnType();
            if (t == boolean.class || t == Boolean.class) {
                return false;
            }
            if (t == int.class || t == Integer.class) {
                return Integer.valueOf(0);
            }
            if (t == long.class || t == Long.class) {
                return Long.valueOf(0);
            }
            if (t == short.class || t == Short.class) {
                return Short.valueOf((short) 0);
            }
            if (t == char.class || t == Character.class) {
                return Character.valueOf((char) 0);
            }
            if (t == byte.class || t == Byte.class) {
                return Byte.valueOf((byte) 0);
            }
            if (t == float.class || t == Float.class) {
                return Float.valueOf(0);
            }
            if (t == double.class || t == Double.class) {
                return Double.valueOf(0);
            }
            return null;
        }
    }

    /**
     * Helper class for implementing an IXxx system server binder object.
     */
    public static class BinderHelper<IClass extends IInterface> {
        private final Class<IClass> mInterfaceClass;
        private final IClass mProxy;

        BinderHelper(@NonNull Class<IClass> interfaceClass, @NonNull InvocationHandler handler) {
            mInterfaceClass = interfaceClass;
            mProxy = RavenwoodProxyHelper.newProxy(mInterfaceClass, handler::invoke);
        }

        @NonNull
        public IClass getObject() {
            return mProxy;
        }

        public IBinder getIBinder() {
            return new Binder() {
                @Override
                public IInterface queryLocalInterface(String descriptor) {
                    try {
                        // Use reflection to get the DESCRIPTOR field from the Stub class
                        Class<?> stubClass = Class.forName(mInterfaceClass.getName() + "$Stub");
                        String stubDescriptor = (String) stubClass.getField("DESCRIPTOR").get(null);

                        if (stubDescriptor.equals(descriptor)) {
                            return mProxy;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Error getting descriptor for " + mInterfaceClass.getName(), e);
                    }
                    throw new RuntimeException("Unknown descriptor: " + descriptor);
                }
            };
        }
    }
}
