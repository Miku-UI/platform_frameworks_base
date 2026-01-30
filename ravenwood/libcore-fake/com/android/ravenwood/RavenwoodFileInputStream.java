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
package com.android.ravenwood;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * FileInputStream tracking fd ownership, emulating libcore behavior.
 */
public class RavenwoodFileInputStream extends FileInputStream {

    private final boolean mIsFdOwner;

    /**
     * {@inheritDoc}
     */
    public RavenwoodFileInputStream(String name) throws FileNotFoundException {
        super(name);
        mIsFdOwner = true;
    }

    /**
     * {@inheritDoc}
     */
    public RavenwoodFileInputStream(File file) throws FileNotFoundException {
        super(file);
        mIsFdOwner = true;
    }

    /**
     * {@inheritDoc}
     */
    public RavenwoodFileInputStream(FileDescriptor fdObj) {
        this(fdObj, false);
    }

    /**
     * {@inheritDoc}
     */
    public RavenwoodFileInputStream(FileDescriptor fdObj, boolean isFdOwner) {
        super(fdObj);
        this.mIsFdOwner = isFdOwner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        if (mIsFdOwner) {
            // Only close when we are the owner
            super.close();
        }
    }
}
