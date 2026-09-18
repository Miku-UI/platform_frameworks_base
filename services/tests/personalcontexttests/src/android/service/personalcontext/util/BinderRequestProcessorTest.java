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
package android.service.personalcontext.util;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.testutil.FakeExecutor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BinderRequestProcessorTest {
    @Mock
    private Object mTestTarget;

    @Mock
    private BinderRequestProcessor.Initializer<Object> mInitializer;

    private BinderRequestProcessor<Object> mRequestProcessor;

    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRequestProcessor = new BinderRequestProcessor.Builder<>(mTestTarget, mFakeExecutor)
                .setInitializer(mInitializer)
                .build();
    }

    @Test
    public void testInitialization() throws Exception {
        final IOpCallback callback = mock(IOpCallback.class);
        final UUID componentId = UUID.randomUUID();

        final BinderRequestProcessor.OperationHandler opHandler =
                mock(BinderRequestProcessor.OperationHandler.class);
        final BinderRequestProcessor.ExecutionParams params =
                new BinderRequestProcessor.ExecutionParams.Builder<>(callback, opHandler)
                        .setComponentId(componentId)
                        .build();
        mRequestProcessor.execute(params);
        mFakeExecutor.runAll();
        verify(mInitializer).onInitialize(eq(mTestTarget), eq(componentId));
        clearInvocations(mInitializer);
        mRequestProcessor.execute(params);
        mFakeExecutor.runAll();
        verify(mInitializer, never()).onInitialize(any(), any());
    }

    @Test
    public void testExceptionHandlingWhenInitializing() throws Exception {
        doThrow(new Exception()).when(mInitializer).onInitialize(any(), any());
        final IOpCallback callback = mock(IOpCallback.class);

        final BinderRequestProcessor.OperationHandler opHandler =
                mock(BinderRequestProcessor.OperationHandler.class);
        final BinderRequestProcessor.ExecutionParams params =
                new BinderRequestProcessor.ExecutionParams.Builder<>(callback, opHandler)
                        .build();
        mRequestProcessor.execute(params);

        assertThrows(Exception.class, () -> mFakeExecutor.runAll());
        verify(opHandler, never()).handle(any());
        verify(callback).signalCompletion();
    }

    @Test
    public void testExceptionHandlingWhenExecuting() throws Exception {
        doThrow(new Exception()).when(mInitializer).onInitialize(any(), any());
        final IOpCallback callback = mock(IOpCallback.class);

        final BinderRequestProcessor.ExecutionParams params =
                new BinderRequestProcessor.ExecutionParams.Builder<>(callback,
                        serviceInstance -> {
                            throw new Exception();
                        })
                        .build();
        mRequestProcessor.execute(params);
        assertThrows(Exception.class, () -> mFakeExecutor.runAll());
        verify(callback).signalCompletion();
    }

    @Test
    public void testNormalExecution() throws Exception {
        final IOpCallback callback = mock(IOpCallback.class);
        final BinderRequestProcessor.ExecutionParams params =
                new BinderRequestProcessor.ExecutionParams.Builder<>(callback,
                        serviceInstance -> {})
                        .build();
        mRequestProcessor.execute(params);
        verify(callback, never()).signalCompletion();
        mFakeExecutor.runAll();
        verify(callback).signalCompletion();
    }
}
