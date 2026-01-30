/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.protolog;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.tracing.perfetto.CreateTlsStateArgs;
import android.util.proto.ProtoInputStream;

import com.android.internal.protolog.common.LogLevel;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import perfetto.protos.DataSourceConfigOuterClass;
import perfetto.protos.ProtologCommon;
import perfetto.protos.ProtologConfig;

public class ProtologDataSourceTest {
    @Test
    public void noConfig() {
        final ProtoLogDataSource.TlsState tlsState = createTlsState(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder().build());

        Truth.assertThat(tlsState.getLogFromLevel("SOME_TAG")).isEqualTo(LogLevel.WTF);
        Truth.assertThat(tlsState.getShouldCollectStacktrace("SOME_TAG")).isFalse();
    }

    @Test
    public void defaultTraceMode() {
        final ProtoLogDataSource.TlsState tlsState = createTlsState(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder()
                        .setProtologConfig(
                                ProtologConfig.ProtoLogConfig.newBuilder()
                                        .setTracingMode(
                                                ProtologConfig.ProtoLogConfig.TracingMode
                                                        .ENABLE_ALL)
                                        .build()
                        ).build());

        Truth.assertThat(tlsState.getLogFromLevel("SOME_TAG")).isEqualTo(LogLevel.VERBOSE);
        Truth.assertThat(tlsState.getShouldCollectStacktrace("SOME_TAG")).isFalse();
    }

    @Test
    public void allEnabledTraceMode() {
        final ProtoLogDataSource.TlsState tlsState = createTlsState(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder().setProtologConfig(
                        ProtologConfig.ProtoLogConfig.newBuilder()
                                .setTracingMode(
                                        ProtologConfig.ProtoLogConfig.TracingMode.ENABLE_ALL)
                                .build()
                ).build()
        );

        Truth.assertThat(tlsState.getLogFromLevel("SOME_TAG")).isEqualTo(LogLevel.VERBOSE);
        Truth.assertThat(tlsState.getShouldCollectStacktrace("SOME_TAG")).isFalse();
    }

    @Test
    public void requireGroupTagInOverrides() {
        Exception exception = assertThrows(RuntimeException.class, () -> {
            createTlsState(DataSourceConfigOuterClass.DataSourceConfig.newBuilder()
                    .setProtologConfig(
                            ProtologConfig.ProtoLogConfig.newBuilder()
                                    .addGroupOverrides(
                                            ProtologConfig.ProtoLogGroup.newBuilder()
                                                    .setLogFrom(
                                                            ProtologCommon.ProtoLogLevel
                                                                    .PROTOLOG_LEVEL_WARN)
                                                    .setCollectStacktrace(true)
                                    )
                                    .build()
                    ).build());
        });

        Truth.assertThat(exception).hasMessageThat().contains("group override without a group tag");
    }

    @Test
    public void stackTraceCollection() {
        final ProtoLogDataSource.TlsState tlsState = createTlsState(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder().setProtologConfig(
                        ProtologConfig.ProtoLogConfig.newBuilder()
                                .addGroupOverrides(
                                        ProtologConfig.ProtoLogGroup.newBuilder()
                                                .setGroupName("SOME_TAG")
                                                .setCollectStacktrace(true)
                                )
                                .build()
                ).build());

        Truth.assertThat(tlsState.getShouldCollectStacktrace("SOME_TAG")).isTrue();
    }

    @Test
    public void groupLogFromOverrides() {
        final ProtoLogDataSource.TlsState tlsState = createTlsState(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder().setProtologConfig(
                        ProtologConfig.ProtoLogConfig.newBuilder()
                                .addGroupOverrides(
                                        ProtologConfig.ProtoLogGroup.newBuilder()
                                                .setGroupName("SOME_TAG")
                                                .setLogFrom(
                                                        ProtologCommon.ProtoLogLevel
                                                                .PROTOLOG_LEVEL_DEBUG)
                                                .setCollectStacktrace(true)
                                )
                                .addGroupOverrides(
                                        ProtologConfig.ProtoLogGroup.newBuilder()
                                                .setGroupName("SOME_OTHER_TAG")
                                                .setLogFrom(
                                                        ProtologCommon.ProtoLogLevel
                                                                .PROTOLOG_LEVEL_WARN)
                                )
                                .build()
                ).build());

        Truth.assertThat(tlsState.getLogFromLevel("SOME_TAG")).isEqualTo(LogLevel.DEBUG);
        Truth.assertThat(tlsState.getShouldCollectStacktrace("SOME_TAG")).isTrue();

        Truth.assertThat(tlsState.getLogFromLevel("SOME_OTHER_TAG")).isEqualTo(LogLevel.WARN);
        Truth.assertThat(tlsState.getShouldCollectStacktrace("SOME_OTHER_TAG")).isFalse();

        Truth.assertThat(tlsState.getLogFromLevel("UNKNOWN_TAG")).isEqualTo(LogLevel.WTF);
        Truth.assertThat(tlsState.getShouldCollectStacktrace("UNKNOWN_TAG")).isFalse();
    }

    @Test
    public void registerAndUnregisterAllCallbacksSuccessfully() {
        final ProtoLogDataSource ds = new ProtoLogDataSource();
        ProtoLogDataSource.Instance.TracingInstanceStartCallback mockStartCallback =
                Mockito.mock(ProtoLogDataSource.Instance.TracingInstanceStartCallback.class);
        // Updated type for mockFlushCallback
        ProtoLogDataSource.Instance.TracingFlushCallback mockFlushCallback =
                Mockito.mock(ProtoLogDataSource.Instance.TracingFlushCallback.class);
        ProtoLogDataSource.Instance.TracingInstanceStopCallback mockStopCallback =
                Mockito.mock(ProtoLogDataSource.Instance.TracingInstanceStopCallback.class);

        // Register all callbacks
        ds.registerOnStartCallback(mockStartCallback);
        ds.registerOnFlushCallback(mockFlushCallback);
        ds.registerOnStopCallback(mockStopCallback);

        // Simulate events to trigger callbacks
        ProtoLogDataSource.Instance instance1 = ds.createInstance(new ProtoInputStream(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder().build().toByteArray()), 0);
        instance1.onStart(Mockito.mock(android.tracing.perfetto.StartCallbackArguments.class));
        instance1.onFlush(Mockito.mock(android.tracing.perfetto.FlushCallbackArguments.class));
        instance1.onStop(Mockito.mock(android.tracing.perfetto.StopCallbackArguments.class));

        // Verify callbacks were called once with correct methods and arguments
        Mockito.verify(mockStartCallback, Mockito.times(1)).onTracingInstanceStart(Mockito.anyInt(),
                Mockito.any(ProtoLogDataSource.ProtoLogConfig.class));
        Mockito.verify(mockFlushCallback, Mockito.times(1)).onTracingFlush();
        Mockito.verify(mockStopCallback, Mockito.times(1)).onTracingInstanceStop(Mockito.anyInt(),
                Mockito.any(ProtoLogDataSource.ProtoLogConfig.class));

        // Unregister all callbacks
        ds.unregisterOnStartCallback(mockStartCallback);
        ds.unregisterOnFlushCallback(mockFlushCallback);
        ds.unregisterOnStopCallback(mockStopCallback);

        // Simulate events again
        ProtoLogDataSource.Instance instance2 = ds.createInstance(new ProtoInputStream(
                DataSourceConfigOuterClass.DataSourceConfig.newBuilder().build().toByteArray()), 1);
        instance2.onStart(Mockito.mock(android.tracing.perfetto.StartCallbackArguments.class));
        instance2.onFlush(Mockito.mock(android.tracing.perfetto.FlushCallbackArguments.class));
        instance2.onStop(Mockito.mock(android.tracing.perfetto.StopCallbackArguments.class));

        // Verify callbacks were not called again (still only once in total)
        Mockito.verify(mockStartCallback, Mockito.times(1)).onTracingInstanceStart(Mockito.anyInt(),
                Mockito.any(ProtoLogDataSource.ProtoLogConfig.class));
        Mockito.verify(mockFlushCallback, Mockito.times(1)).onTracingFlush();
        Mockito.verify(mockStopCallback, Mockito.times(1)).onTracingInstanceStop(Mockito.anyInt(),
                Mockito.any(ProtoLogDataSource.ProtoLogConfig.class));
    }

    private ProtoLogDataSource.TlsState createTlsState(
            DataSourceConfigOuterClass.DataSourceConfig config) {
        final ProtoLogDataSource ds = Mockito.spy(new ProtoLogDataSource());

        ProtoInputStream configStream = new ProtoInputStream(config.toByteArray());
        final ProtoLogDataSource.Instance dsInstance = Mockito.spy(
                ds.createInstance(configStream, 8));
        Mockito.doNothing().when(dsInstance).release();
        final CreateTlsStateArgs mockCreateTlsStateArgs = Mockito.mock(CreateTlsStateArgs.class);
        Mockito.when(mockCreateTlsStateArgs.getDataSourceInstanceLocked()).thenReturn(dsInstance);
        return ds.createTlsState(mockCreateTlsStateArgs);
    }
}
