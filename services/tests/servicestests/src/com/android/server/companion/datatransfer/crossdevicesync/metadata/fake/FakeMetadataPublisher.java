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
package com.android.server.companion.datatransfer.crossdevicesync.metadata.fake;

import android.os.PersistableBundle;
import android.util.IndentingPrintWriter;

import com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisher;

import java.util.HashMap;
import java.util.Map;

/** Fake implementation of {@link MetadataPublisher}. */
public class FakeMetadataPublisher implements MetadataPublisher {
    private final Map<Integer, PersistableBundle> mUserMetadata = new HashMap<>();
    private boolean mIsInitialized = false;

    public FakeMetadataPublisher() {}

    @Override
    public void init() {
        mIsInitialized = true;
    }

    @Override
    public void destroy() {
        throwIfUninitialized();
        mIsInitialized = false;
        mUserMetadata.clear();
    }

    @Override
    public void putBooleanMetaData(int userId, String key, boolean val) {
        throwIfUninitialized();
        getOrCreateBundleForUser(userId).putBoolean(key, val);
    }

    @Override
    public void putIntMetaData(int userId, String key, int val) {
        throwIfUninitialized();
        getOrCreateBundleForUser(userId).putInt(key, val);
    }

    @Override
    public void putStringMetaData(int userId, String key, String val) {
        throwIfUninitialized();
        getOrCreateBundleForUser(userId).putString(key, val);
    }

    /** Returns the metadata for the given user for testing. */
    public PersistableBundle getMetadata(int userId) {
        throwIfUninitialized();
        return getOrCreateBundleForUser(userId);
    }

    private PersistableBundle getOrCreateBundleForUser(int userId) {
        return mUserMetadata.computeIfAbsent(userId, k -> new PersistableBundle());
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        throwIfUninitialized();
        // No-op for fake.
    }

    private void throwIfUninitialized() {
        if (!mIsInitialized) {
            throw new IllegalStateException("Not initialized!");
        }
    }
}
