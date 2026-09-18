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

package com.android.server.attention;

import android.attention.InteractionState;

import androidx.annotation.Keep;

import java.util.List;

/**
 * Native implementation of {@link InteractionProvider}.
 * This is used by InputManager to register a interaction provider.
 */
@Keep // Used from JNI code.
public class NativeInteractionProvider implements InteractionProvider {

    // This is a pointer to the native InteractionProvider object.
    // The object's lifetime is managed by the native InteractionProviderServiceInternal,
    // which holds a shared_ptr to it, keeping it alive as long as it's registered.
    private final long mNativePtr;

    public NativeInteractionProvider(long nativePtr) {
        mNativePtr = nativePtr;
    }

    @Override
    public List<InteractionState> getSourceInteractions() {
        return getSourceInteractions(mNativePtr);
    }

    @Override
    public void requestWakeupCallback(InteractionWakeupCallback callback) {
        requestWakeupCallback(mNativePtr, callback);
    }

    private native List<InteractionState> getSourceInteractions(long nativePtr);

    private native void requestWakeupCallback(long nativePtr, InteractionWakeupCallback callback);

}
