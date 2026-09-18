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
package com.android.ravenwoodtest.coretest;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ContentInterface;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.IContentProvider;
import android.net.Uri;
import android.platform.test.ravenwood.RavenwoodProxyHelper;
import android.test.mock.MockContentResolver;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ravenwood.common.SneakyThrow;

import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RavenwoodContentResolverTest {

    static class MockInvocationException extends Throwable {}

    private static final Answer DEFAULT_ANSWER = invocation -> {
        throw new MockInvocationException();
    };

    private static final List<Method> MOCKABLE_METHODS;

    // These are methods in ContentResolver that does not override or overload ContentInterface
    private static final List<String> ADDITIONAL_MOCKABLE_METHODS = List.of(
            "canonicalizeOrElse",
            "openInputStream",
            "openOutputStream",
            "openFileDescriptor",
            "openAssetFileDescriptor",
            "openTypedAssetFileDescriptor"
    );

    static {
        // All ContentInterface methods and its overloads are mockable
        var interfaceMethods = Arrays.stream(ContentInterface.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toUnmodifiableSet());

        MOCKABLE_METHODS = Arrays.stream(ContentResolver.class.getDeclaredMethods())
                .filter(m -> interfaceMethods.contains(m.getName())
                        || ADDITIONAL_MOCKABLE_METHODS.contains(m.getName()))
                .peek(m -> m.setAccessible(true))
                .toList();
    }

    @Test
    public void testWrapContentResolver() {
        var mock = mock(ContentInterface.class, DEFAULT_ANSWER);
        var resolver = ContentResolver.wrap(mock);
        assertResolverCallsMock(resolver);
    }

    @Test
    public void testMockContentResolver() {
        var resolver = new MockContentResolver();
        resolver.addProvider("com.example", createMockProvider());
        assertResolverCallsMock(resolver);
    }

    @Test
    public void testTestableContentResolver() {
        try (var context = new TestableContext(
                InstrumentationRegistry.getInstrumentation().getContext())) {
            var resolver = context.getContentResolver();
            resolver.addProvider("com.example", createMockProvider());
            assertResolverCallsMock(resolver);
        }
    }

    private static ContentProvider createMockProvider() {
        var mock = mock(IContentProvider.class, DEFAULT_ANSWER);
        var provider = mock(ContentProvider.class);
        when(provider.getIContentProvider()).thenReturn(mock);
        return provider;
    }

    private static void assertResolverCallsMock(ContentResolver resolver) {
        for (Method m : MOCKABLE_METHODS) {
            assertThrows(MockInvocationException.class,
                    () -> invokeMockableMethod(resolver, m));
        }
    }

    private static void invokeMockableMethod(Object o, Method m) {
        var list = new ArrayList<>();
        for (Class<?> type : m.getParameterTypes()) {
            if (type == Uri.class) {
                list.add(Uri.parse("content://com.example/"));
            } else if (type == String.class) {
                list.add("com.example");
            } else if (type == ArrayList.class) {
                list.add(new ArrayList<>());
            } else if (type.isArray()) {
                list.add(Array.newInstance(type.getComponentType(), 0));
            } else {
                list.add(RavenwoodProxyHelper.getDefaultValue(type));
            }
        }
        try {
            m.invoke(o, list.toArray());
        } catch (IllegalAccessException e) {
            SneakyThrow.sneakyThrow(e);
        } catch (InvocationTargetException e) {
            SneakyThrow.sneakyThrow(e.getTargetException());
        }
    }
}
