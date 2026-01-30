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

package com.android.server.ondeviceintelligence;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ondeviceintelligence.IResponseCallback;
import android.app.ondeviceintelligence.IStreamingResponseCallback;
import android.app.ondeviceintelligence.ITokenInfoCallback;
import android.app.ondeviceintelligence.InferenceInfo;
import android.app.ondeviceintelligence.TokenInfo;
import android.database.CursorWindow;
import android.graphics.Bitmap;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.os.SharedMemory;
import android.system.ErrnoException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.infra.AndroidFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class BundleUtilTest {
    private ParcelFileDescriptor mReadOnlyPfd;
    private ParcelFileDescriptor mReadWritePfd;
    private Bitmap mImmutableBitmap;
    private Bitmap mMutableBitmap;
    private SharedMemory mSharedMemory;
    private CursorWindow mCursorWindow;

    private final Executor mDirectExecutor = Runnable::run;

    @Before
    public void setUp() throws IOException, ErrnoException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        mReadOnlyPfd = pipe[0];
        mReadWritePfd = pipe[1];

        mMutableBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        mImmutableBitmap = mMutableBitmap.asShared();

        mSharedMemory = SharedMemory.create("test", 1024);
        mCursorWindow = new CursorWindow("test_cursor");
    }

    @After
    public void tearDown() throws Exception {
        try {
            mReadOnlyPfd.close();
        } catch (IOException e) {
            // ignore
        }
        try {
            mReadWritePfd.close();
        } catch (IOException e) {
            // ignore
        }
        if (mImmutableBitmap != null) {
            mImmutableBitmap.recycle();
        }
        if (mMutableBitmap != null) {
            mMutableBitmap.recycle();
        }
        mSharedMemory.close();
        mCursorWindow.close();
    }

    private ParcelFileDescriptor createReadOnlyPfd() throws IOException {
        return ParcelFileDescriptor.createPipe()[0];
    }

    private ParcelFileDescriptor createReadWritePfd() throws IOException {
        return ParcelFileDescriptor.createPipe()[1];
    }

    private Bundle getParcelledBundle(Bundle bundle) {
        Parcel p = Parcel.obtain();
        bundle.writeToParcel(p, 0);
        p.setDataPosition(0);
        Bundle newBundle = new Bundle(p);
        p.recycle();
        return newBundle;
    }

    @Test
    public void sanitizeInferenceParams_nullBundle_throws() {
        assertThrows(
                IllegalArgumentException.class, () -> BundleUtil.sanitizeInferenceParams(null));
    }

    @Test
    public void sanitizeInferenceParams_bundleWithBinder_throws() {
        Bundle bundle = new Bundle();
        bundle.putBinder("binder", new Binder());
        assertThrows(
                BadParcelableException.class, () -> BundleUtil.sanitizeInferenceParams(bundle));
    }

    @Test
    public void sanitizeInferenceParams_validTypes_success() {
        Bundle bundle = new Bundle();
        bundle.putByteArray("bytes", new byte[1]);
        bundle.putParcelable("persistable", new PersistableBundle());
        BundleUtil.sanitizeInferenceParams(getParcelledBundle(bundle)); // no exception
    }

    @Test
    public void sanitizeInferenceParams_readOnlyPfd_success() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("pfd", mReadOnlyPfd);
        BundleUtil.sanitizeInferenceParams(getParcelledBundle(bundle));
    }

    @Test
    public void sanitizeInferenceParams_readWritePfd_throws() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("pfd", mReadWritePfd);
        assertThrows(
                BadParcelableException.class, () -> BundleUtil.sanitizeInferenceParams(bundle));
    }

    @Test
    public void sanitizeInferenceParams_sharedMemory_setsReadOnly() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putParcelable("shmem", mSharedMemory);
        BundleUtil.sanitizeInferenceParams(getParcelledBundle(bundle));
    }

    @Test
    public void sanitizeInferenceParams_immutableBitmap_success() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("bitmap", mImmutableBitmap);
        BundleUtil.sanitizeInferenceParams(getParcelledBundle(bundle));
    }

    @Test
    public void sanitizeInferenceParams_mutableBitmap_throws() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("bitmap", mMutableBitmap);
        assertThrows(
                BadParcelableException.class, () -> BundleUtil.sanitizeInferenceParams(bundle));
    }

    @Test
    public void sanitizeInferenceParams_unsupportedParcelable_throws() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("unsupported", mock(Parcelable.class));
        assertThrows(
                BadParcelableException.class, () -> BundleUtil.sanitizeInferenceParams(bundle));
    }

    @Test
    public void sanitizeInferenceParams_readOnlyPfdArray_success() throws IOException {
        Bundle bundle = new Bundle();
        bundle.putParcelableArray(
                "pfd_array", new Parcelable[] {createReadOnlyPfd(), createReadOnlyPfd()});
        BundleUtil.sanitizeInferenceParams(getParcelledBundle(bundle));
    }

    @Test
    public void sanitizeInferenceParams_readWritePfdInArray_throws() throws IOException {
        Bundle bundle = new Bundle();
        bundle.putParcelableArray(
                "pfd_array", new Parcelable[] {createReadOnlyPfd(), createReadWritePfd()});
        assertThrows(
                BadParcelableException.class, () -> BundleUtil.sanitizeInferenceParams(bundle));
    }

    @Test
    public void sanitizeInferenceParams_immutableBitmapsArray_success() {
        Bundle bundle = new Bundle();
        bundle.putParcelableArray(
                "bitmap_array", new Parcelable[] {mImmutableBitmap, mImmutableBitmap});
        BundleUtil.sanitizeInferenceParams(getParcelledBundle(bundle));
    }

    @Test
    public void sanitizeInferenceParams_mutableBitmapInArray_throws() {
        Bundle bundle = new Bundle();
        bundle.putParcelableArray(
                "bitmap_array", new Parcelable[] {mImmutableBitmap, mMutableBitmap});
        assertThrows(
                BadParcelableException.class, () -> BundleUtil.sanitizeInferenceParams(bundle));
    }

    @Test
    public void sanitizeInferenceParams_nestedBundle_sanitizesRecursively() {
        Bundle nestedBundle = new Bundle();
        nestedBundle.putParcelable("pfd", mReadWritePfd);
        Bundle bundle = new Bundle();
        bundle.putBundle("nested", nestedBundle);
        assertThrows(
                BadParcelableException.class,
                () -> BundleUtil.sanitizeInferenceParams(getParcelledBundle(bundle)));
    }

    @Test
    public void sanitizeInferenceParams_withCursorWindow_success() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("cursor", mCursorWindow);
        BundleUtil.sanitizeInferenceParams(getParcelledBundle(bundle));
    }

    @Test
    public void sanitizeResponseParams_unsupportedTypes_throws() {
        Bundle bundleSharedMem = new Bundle();
        bundleSharedMem.putParcelable("shmem", mSharedMemory);
        assertThrows(
                BadParcelableException.class,
                () -> BundleUtil.sanitizeResponseParams(bundleSharedMem));

        Bundle bundleCursor = new Bundle();
        bundleCursor.putParcelable("cursor", mCursorWindow);
        assertThrows(
                BadParcelableException.class,
                () -> BundleUtil.sanitizeResponseParams(bundleCursor));
    }

    @Test
    public void sanitizeStateParams_readOnlyPfd_success() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("pfd", mReadOnlyPfd);
        BundleUtil.sanitizeStateParams(getParcelledBundle(bundle));
    }

    @Test
    public void tryCloseResource_nullAndEmptyBundle_doesNotThrow() {
        BundleUtil.tryCloseResource(null);
        BundleUtil.tryCloseResource(new Bundle());
    }

    @Test
    public void tryCloseResource_closesPfd() throws Exception {
        ParcelFileDescriptor pfd = createReadWritePfd();
        Bundle bundle = new Bundle();
        bundle.putParcelable("pfd", pfd);
        assertTrue(pfd.getFileDescriptor().valid());
        BundleUtil.tryCloseResource(bundle);
        assertFalse(pfd.getFileDescriptor().valid());
    }

    @Test
    public void tryCloseResource_recyclesBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asShared();
        Bundle bundle = new Bundle();
        bundle.putParcelable("bitmap", bitmap);

        assertFalse(bitmap.isRecycled());
        BundleUtil.tryCloseResource(bundle);
        assertTrue(bitmap.isRecycled());
    }

    @Test
    public void tryCloseResource_closesSharedMemory() throws Exception {
        SharedMemory shmem = SharedMemory.create("closetest", 128);
        Bundle bundle = new Bundle();
        bundle.putParcelable("shmem", shmem);

        BundleUtil.tryCloseResource(bundle);
    }

    @Test
    public void tryCloseResource_closesCursorWindow() {
        CursorWindow window = new CursorWindow("closetest");
        Bundle bundle = new Bundle();
        bundle.putParcelable("cursor", window);
        BundleUtil.tryCloseResource(bundle);
    }

    @Test
    public void tryCloseResource_closesPfdInArray() throws Exception {
        ParcelFileDescriptor pfd = createReadWritePfd();
        Bundle bundle = new Bundle();
        bundle.putParcelableArray("pfd_array", new Parcelable[] {pfd});
        assertTrue(pfd.getFileDescriptor().valid());
        BundleUtil.tryCloseResource(bundle);
        assertFalse(pfd.getFileDescriptor().valid());
    }

    @Test
    public void tryCloseResource_recyclesBitmapInArray() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asShared();
        Bundle bundle = new Bundle();
        bundle.putParcelableArray("bitmap_array", new Parcelable[] {bitmap});

        assertFalse(bitmap.isRecycled());
        BundleUtil.tryCloseResource(bundle);
        assertTrue(bitmap.isRecycled());
    }

    @Test
    public void tryCloseResource_closesNestedBundleResource() throws Exception {
        ParcelFileDescriptor pfdMock = mock(ParcelFileDescriptor.class);
        Bundle nestedBundle = new Bundle();
        nestedBundle.putParcelable("pfd", pfdMock);
        Bundle bundle = new Bundle();
        bundle.putBundle("nested", nestedBundle);
        BundleUtil.tryCloseResource(bundle);
        verify(pfdMock).close();
    }

    @Test
    public void wrapWithValidation_IStreamingResponseCallback_onSuccess() throws Exception {
        IStreamingResponseCallback mockCallback = mock(IStreamingResponseCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        InferenceInfoStore mockStore = mock(InferenceInfoStore.class);
        Bundle result = new Bundle();
        result.putParcelable("pfd", createReadOnlyPfd());
        result = getParcelledBundle(result);

        IStreamingResponseCallback wrapper =
                BundleUtil.wrapWithValidation(
                        mockCallback, mDirectExecutor, future, mockStore, false);
        wrapper.onSuccess(result);

        verify(mockCallback).onSuccess(result);
        verify(mockStore).addInferenceInfoFromBundle(any(Bundle.class));
        assertTrue(future.isDone());
        assertFalse(
                result.getParcelable("pfd", ParcelFileDescriptor.class)
                        .getFileDescriptor()
                        .valid());
    }

    @Test
    public void wrapWithValidation_IStreamingResponseCallback_onFailure() throws Exception {
        IStreamingResponseCallback mockCallback = mock(IStreamingResponseCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        InferenceInfoStore mockStore = mock(InferenceInfoStore.class);
        PersistableBundle errorParams = new PersistableBundle();

        IStreamingResponseCallback wrapper =
                BundleUtil.wrapWithValidation(
                        mockCallback, mDirectExecutor, future, mockStore, false);
        wrapper.onFailure(1, "error", errorParams);

        verify(mockCallback).onFailure(1, "error", errorParams);
        verify(mockStore).addInferenceInfoFromBundle(any(PersistableBundle.class));
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithValidation_IStreamingResponseCallback_onInferenceInfo_forwardsWhenEnabled()
            throws Exception {
        IStreamingResponseCallback mockCallback = mock(IStreamingResponseCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        InferenceInfoStore mockStore = mock(InferenceInfoStore.class);
        InferenceInfo info = new InferenceInfo.Builder(123).build();

        IStreamingResponseCallback wrapper =
                BundleUtil.wrapWithValidation(
                        mockCallback, mDirectExecutor, future, mockStore, true);
        wrapper.onInferenceInfo(info);

        verify(mockStore).add(info);
        verify(mockCallback).onInferenceInfo(info);
    }

    @Test
    public void
            wrapWithValidation_IStreamingResponseCallback_onInferenceInfo_doesNotForwardWhenDisabled()
                    throws Exception {
        IStreamingResponseCallback mockCallback = mock(IStreamingResponseCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        InferenceInfoStore mockStore = mock(InferenceInfoStore.class);
        InferenceInfo info = new InferenceInfo.Builder(123).build();
        IStreamingResponseCallback wrapperNoForward =
                BundleUtil.wrapWithValidation(
                        mockCallback, mDirectExecutor, future, mockStore, false);
        wrapperNoForward.onInferenceInfo(info);
        verify(mockCallback, never()).onInferenceInfo(info);
    }

    @Test
    public void wrapWithValidation_IResponseCallback_onSuccess() throws Exception {
        IResponseCallback mockCallback = mock(IResponseCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        InferenceInfoStore mockStore = mock(InferenceInfoStore.class);
        Bundle result = new Bundle();
        result.putParcelable("pfd", createReadOnlyPfd());
        result = getParcelledBundle(result);

        IResponseCallback wrapper =
                BundleUtil.wrapWithValidation(
                        mockCallback, mDirectExecutor, future, mockStore, false);
        wrapper.onSuccess(result);

        verify(mockCallback).onSuccess(result);
        verify(mockStore).addInferenceInfoFromBundle(any(Bundle.class));
        assertTrue(future.isDone());
        assertFalse(
                result.getParcelable("pfd", ParcelFileDescriptor.class)
                        .getFileDescriptor()
                        .valid());
    }

    @Test
    public void wrapWithValidation_IResponseCallback_onDataAugmentRequest() throws Exception {
        IResponseCallback mockCallback = mock(IResponseCallback.class);
        RemoteCallback mockRemoteCallback = mock(RemoteCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        InferenceInfoStore mockStore = mock(InferenceInfoStore.class);
        Bundle processedContent = new Bundle();
        processedContent.putParcelable("pfd_processed", createReadOnlyPfd());
        processedContent = getParcelledBundle(processedContent);
        Bundle augmentedData = new Bundle();
        augmentedData.putParcelable("pfd_augmented", createReadOnlyPfd());
        augmentedData = getParcelledBundle(augmentedData);

        IResponseCallback wrapper =
                BundleUtil.wrapWithValidation(
                        mockCallback, mDirectExecutor, future, mockStore, false);
        ArgumentCaptor<RemoteCallback> callbackCaptor =
                ArgumentCaptor.forClass(RemoteCallback.class);

        wrapper.onDataAugmentRequest(processedContent, mockRemoteCallback);
        verify(mockCallback).onDataAugmentRequest(any(Bundle.class), callbackCaptor.capture());

        callbackCaptor.getValue().sendResult(augmentedData);
        verify(mockRemoteCallback).sendResult(augmentedData);

        assertFalse(
                processedContent
                        .getParcelable("pfd_processed", ParcelFileDescriptor.class)
                        .getFileDescriptor()
                        .valid());
        assertFalse(
                augmentedData
                        .getParcelable("pfd_augmented", ParcelFileDescriptor.class)
                        .getFileDescriptor()
                        .valid());
    }

    @Test
    public void wrapWithValidation_ITokenInfoCallback_onSuccess() throws Exception {
        ITokenInfoCallback mockCallback = mock(ITokenInfoCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        InferenceInfoStore mockStore = mock(InferenceInfoStore.class);
        TokenInfo tokenInfo = new TokenInfo(1, PersistableBundle.EMPTY);

        ITokenInfoCallback wrapper = BundleUtil.wrapWithValidation(mockCallback, future, mockStore);
        wrapper.onSuccess(tokenInfo);

        verify(mockCallback).onSuccess(tokenInfo);
        verify(mockStore).addInferenceInfoFromBundle(tokenInfo.getInfoParams());
        assertTrue(future.isDone());
    }

    @Test
    public void wrapWithValidation_ITokenInfoCallback_onFailure() throws Exception {
        ITokenInfoCallback mockCallback = mock(ITokenInfoCallback.class);
        AndroidFuture<Void> future = new AndroidFuture<>();
        InferenceInfoStore mockStore = mock(InferenceInfoStore.class);
        PersistableBundle errorParams = new PersistableBundle();
        ITokenInfoCallback wrapper = BundleUtil.wrapWithValidation(mockCallback, future, mockStore);
        wrapper.onFailure(1, "error", errorParams);
        verify(mockCallback).onFailure(1, "error", errorParams);
        verify(mockStore).addInferenceInfoFromBundle(errorParams);
        assertTrue(future.isDone());
    }
}
