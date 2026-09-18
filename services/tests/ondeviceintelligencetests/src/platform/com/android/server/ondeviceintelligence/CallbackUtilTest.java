/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ondeviceintelligence;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.ondeviceintelligence.embedding.EmbeddingModel;
import android.app.ondeviceintelligence.embedding.EmbeddingResponse;
import android.app.ondeviceintelligence.embedding.IEmbeddingCallback;
import android.app.ondeviceintelligence.embedding.IEmbeddingModelCallback;
import android.app.ondeviceintelligence.embedding.IEmbeddingModelListCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionModelCallback;
import android.app.ondeviceintelligence.imagedescription.IImageDescriptionModelListCallback;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionModel;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionResponse;
import android.os.PersistableBundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.infra.AndroidFuture;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CallbackUtilTest {

    @Test
    public void wrapWithCompletion_IEmbeddingModelListCallback_onSuccess() throws RemoteException {
        IEmbeddingModelListCallback mockCallback = mock(IEmbeddingModelListCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        List<EmbeddingModel> result = Collections.emptyList();

        IEmbeddingModelListCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onSuccess(result);

        verify(mockCallback).onSuccess(result);
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IEmbeddingModelListCallback_onFailure() throws RemoteException {
        IEmbeddingModelListCallback mockCallback = mock(IEmbeddingModelListCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        PersistableBundle bundle = new PersistableBundle();

        IEmbeddingModelListCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onFailure(1, "error", bundle);

        verify(mockCallback).onFailure(eq(1), eq("error"), eq(bundle));
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IEmbeddingModelCallback_onSuccess() throws RemoteException {
        IEmbeddingModelCallback mockCallback = mock(IEmbeddingModelCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        EmbeddingModel model = mock(EmbeddingModel.class);

        IEmbeddingModelCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onSuccess(model);

        verify(mockCallback).onSuccess(model);
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IEmbeddingModelCallback_onFailure() throws RemoteException {
        IEmbeddingModelCallback mockCallback = mock(IEmbeddingModelCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        PersistableBundle bundle = new PersistableBundle();

        IEmbeddingModelCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onFailure(1, "error", bundle);

        verify(mockCallback).onFailure(eq(1), eq("error"), eq(bundle));
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IEmbeddingCallback_onSuccess() throws RemoteException {
        IEmbeddingCallback mockCallback = mock(IEmbeddingCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        EmbeddingResponse response = mock(EmbeddingResponse.class);

        IEmbeddingCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onSuccess(response);

        verify(mockCallback).onSuccess(response);
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IEmbeddingCallback_onFailure() throws RemoteException {
        IEmbeddingCallback mockCallback = mock(IEmbeddingCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        PersistableBundle bundle = new PersistableBundle();

        IEmbeddingCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onFailure(1, "error", bundle);

        verify(mockCallback).onFailure(eq(1), eq("error"), eq(bundle));
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IImageDescriptionModelListCallback_onSuccess()
            throws RemoteException {
        IImageDescriptionModelListCallback mockCallback =
                mock(IImageDescriptionModelListCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        List<ImageDescriptionModel> result = Collections.emptyList();

        IImageDescriptionModelListCallback wrapper =
                CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onSuccess(result);

        verify(mockCallback).onSuccess(result);
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IImageDescriptionModelListCallback_onFailure()
            throws RemoteException {
        IImageDescriptionModelListCallback mockCallback =
                mock(IImageDescriptionModelListCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        PersistableBundle bundle = new PersistableBundle();

        IImageDescriptionModelListCallback wrapper =
                CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onFailure(1, "error", bundle);

        verify(mockCallback).onFailure(eq(1), eq("error"), eq(bundle));
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IImageDescriptionModelCallback_onSuccess()
            throws RemoteException {
        IImageDescriptionModelCallback mockCallback = mock(IImageDescriptionModelCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        ImageDescriptionModel model = mock(ImageDescriptionModel.class);

        IImageDescriptionModelCallback wrapper =
                CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onSuccess(model);

        verify(mockCallback).onSuccess(model);
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IImageDescriptionModelCallback_onFailure()
            throws RemoteException {
        IImageDescriptionModelCallback mockCallback = mock(IImageDescriptionModelCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        PersistableBundle bundle = new PersistableBundle();

        IImageDescriptionModelCallback wrapper =
                CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onFailure(1, "error", bundle);

        verify(mockCallback).onFailure(eq(1), eq("error"), eq(bundle));
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IImageDescriptionCallback_onSuccess() throws RemoteException {
        IImageDescriptionCallback mockCallback = mock(IImageDescriptionCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        ImageDescriptionResponse result = mock(ImageDescriptionResponse.class);

        IImageDescriptionCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onSuccess(result);

        verify(mockCallback).onSuccess(result);
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IImageDescriptionCallback_onFailure() throws RemoteException {
        IImageDescriptionCallback mockCallback = mock(IImageDescriptionCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        PersistableBundle bundle = new PersistableBundle();

        IImageDescriptionCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onFailure(1, "error", bundle);

        verify(mockCallback).onFailure(eq(1), eq("error"), eq(bundle));
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithCompletion_IImageDescriptionCallback_onNewText() throws RemoteException {
        IImageDescriptionCallback mockCallback = mock(IImageDescriptionCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();

        IImageDescriptionCallback wrapper = CallbackUtil.wrapWithCompletion(mockCallback, future);
        wrapper.onNewText("some text");

        verify(mockCallback).onNewText("some text");
        assertTrue(!future.isDone());
    }
}
