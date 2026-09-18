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

import android.os.Binder;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Helper class to create proxy instances for AIDL interfaces.
 */
public class RavenwoodProxyHelper {
    private static final String TAG = "RavenwoodProxyHelper";

    private RavenwoodProxyHelper() {
    }

    private static String getAidlDescriptor(Class<?> clazz) {
        try {
            // Use reflection to get the DESCRIPTOR field from the Stub class
            Class<?> stubClass = Class.forName(clazz.getName() + "$Stub");
            return (String) stubClass.getField("DESCRIPTOR").get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Error getting descriptor for " + clazz.getName(), e);
        }
    }

    /**
     * Creates a new Proxy object for an IInterface type.
     * <p>
     * The created proxy instance will forward all method calls to methods declared in
     * {@code impl} with the same signature. If no such method can be found, it
     * will throw an {@link RavenwoodUnsupportedApiException}.
     * <p>
     * Please note that the proxy will only forward method calls to methods that are <b>DECLARED</b>
     * in the {@code impl} instance. Any inherited methods will not be considered for delegation.
     * This makes it very easy to provide partial implementations for AIDL interfaces by
     * extending the {@code IAidlService.Default} class and selectively override methods.
     */
    public static <T extends IInterface> T newProxy(Class<T> clazz, Object impl) {
        return newProxy(clazz, getAidlDescriptor(clazz), new DelegateInvocationHandler(impl));
    }

    /**
     * Creates a new Proxy object for an IInterface type, which also checks whether experimental
     * APIs are enabled before each method invocation.
     */
    public static <T extends IInterface> T newExperimentalProxy(
            Class<T> clazz, InvocationHandler ih) {
        return newProxy(clazz, getAidlDescriptor(clazz), (proxy, method, args) -> {
            RavenwoodExperimentalApiChecker.onExperimentalApiCalled(method);
            return ih.invoke(proxy, method, args);
        });
    }

    /**
     * Creates a new Proxy object for an IInterface type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends IInterface> T newProxy(
            Class<T> clazz, String descriptor, InvocationHandler ih) {
        var handler = new IInterfaceInvocationWrapper(ih, descriptor);
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);
    }

    /**
     * Returns the default value for a given type.
     */
    public static Object getDefaultValue(Class<?> t) {
        if (t == boolean.class || t == Boolean.class) {
            return false;
        }
        if (t == int.class || t == Integer.class) {
            return 0;
        }
        if (t == long.class || t == Long.class) {
            return 0L;
        }
        if (t == short.class || t == Short.class) {
            return (short) 0;
        }
        if (t == char.class || t == Character.class) {
            return (char) 0;
        }
        if (t == byte.class || t == Byte.class) {
            return (byte) 0;
        }
        if (t == float.class || t == Float.class) {
            return (float) 0;
        }
        if (t == double.class || t == Double.class) {
            return (double) 0;
        }
        return null;
    }

    /**
     * InvocationHandler that always returns the default value.
     */
    public static final InvocationHandler sDefaultHandler =
            (p, m, a) -> getDefaultValue(m.getReturnType());

    /**
     * InvocationHandler that always throws {@link RavenwoodUnsupportedApiException}.
     */
    public static final InvocationHandler sNotImplementedHandler = (p, m, a) -> {
        throw new RavenwoodUnsupportedApiException().setReason(m);
    };

    /**
     * Wraps another {@link InvocationHandler}, which implements {@link IInterface} for the proxy.
     */
    private static class IInterfaceInvocationWrapper implements InvocationHandler {

        private final InvocationHandler mInner;
        private final String mDescriptor;

        private IInterfaceInvocationWrapper(InvocationHandler inner, String descriptor) {
            mInner = inner;
            mDescriptor = descriptor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Implement IInterface#asBinder
            if (method.getDeclaringClass() == IInterface.class
                    && method.getName().equals("asBinder")) {
                var b = new Binder();
                b.attachInterface((IInterface) proxy, mDescriptor);
                return b;
            }
            // Forward all Object methods to the invocation handler itself
            if (method.getDeclaringClass() == Object.class) {
                if (mInner instanceof DelegateInvocationHandler(Object impl)) {
                    return method.invoke(impl, args);
                }
                return method.invoke(mInner, args);
            }
            Log.w(TAG, "Proxy called: "
                    + method.getDeclaringClass().getName() + "#" + method.getName());
            return mInner.invoke(proxy, method, args);
        }
    }

    /**
     * InvocationHandler that delegates method calls to a real implementation.
     */
    private record DelegateInvocationHandler(Object impl) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Method real = impl.getClass().getDeclaredMethod(method.getName(),
                        method.getParameterTypes());
                real.setAccessible(true);
                return real.invoke(impl, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getTargetException();
                // If the implementation throws RavenwoodUnsupportedApiException, explicitly
                // set the reason to make the message more comprehensible.
                if (cause instanceof RavenwoodUnsupportedApiException apiException) {
                    apiException.setReason(method);
                }
                throw cause;
            } catch (NoSuchMethodException | IllegalAccessException ignore) {
                return sNotImplementedHandler.invoke(proxy, method, args);
            }
        }
    }
}
