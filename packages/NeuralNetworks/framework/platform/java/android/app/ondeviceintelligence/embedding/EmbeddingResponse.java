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

package android.app.ondeviceintelligence.embedding;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ondeviceintelligence.flags.Flags;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the response for the corresponding {@link EmbeddingRequest}.
 *
 * <p>This class contains a list of {@link EmbeddingVector} objects, one for each {@link Content}
 * input supplied in the {@link EmbeddingRequest}.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
public final class EmbeddingResponse implements Parcelable {
    private static final String TAG = "EmbeddingResponse";
    // If the serialized embeddings are larger than this threshold, we will use SharedMemory to
    // transfer the data. Otherwise, we will transfer the data directly through the IPC.
    private static final int SHARED_MEMORY_THRESHOLD_BYTES = 500 * 1024; // 500 KB

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private List<EmbeddingVector> mEmbeddings;

    @GuardedBy("mLock")
    private final @Nullable SharedMemory mSharedMemory;

    private final int mSharedMemorySize;

    /**
     * Constructs a new {@link EmbeddingResponse} with the given list of embeddings.
     *
     * @param embeddings The list of generated {@link EmbeddingVector}s.
     */
    public EmbeddingResponse(@NonNull List<EmbeddingVector> embeddings) {
        synchronized (mLock) {
            mEmbeddings = Objects.requireNonNull(embeddings);
            mSharedMemory = null;
            mSharedMemorySize = -1;
        }
    }

    /** Returns the list of generated embeddings. */
    @NonNull
    public List<EmbeddingVector> getEmbeddings() {
        synchronized (mLock) {
            if (mEmbeddings == null) {
                if (mSharedMemorySize >= 0 && mSharedMemory != null) {
                    mEmbeddings = readEmbeddingsFromSharedMemory();
                }
                if (mEmbeddings == null) {
                    mEmbeddings = new ArrayList<>();
                }
            }
        }
        return mEmbeddings;
    }

    private List<EmbeddingVector> readEmbeddingsFromSharedMemory() {
        ByteBuffer buffer = null;
        try {
            buffer = mSharedMemory.mapReadOnly();
            byte[] bytes = new byte[mSharedMemorySize];
            buffer.get(bytes);
            Parcel parcel = Parcel.obtain();
            try {
                parcel.unmarshall(bytes, 0, bytes.length);
                parcel.setDataPosition(0);
                return parcel.createTypedArrayList(EmbeddingVector.CREATOR);
            } finally {
                parcel.recycle();
            }
        } catch (RuntimeException | ErrnoException e) {
            Log.e(TAG, "Failed to map SharedMemory. Embedding data is lost.", e);
            return null;
        } finally {
            if (buffer != null) {
                SharedMemory.unmap(buffer);
            }
            mSharedMemory.close();
        }
    }

    @Override
    public int describeContents() {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        synchronized (mLock) {
            if (mEmbeddings == null && mSharedMemory != null) {
                dest.writeInt(mSharedMemorySize);
                dest.writeTypedObject(mSharedMemory, flags);
                return;
            }

            List<EmbeddingVector> embeddings = getEmbeddings();
            byte[] bytes = serializeEmbeddings(embeddings);
            if (bytes.length > SHARED_MEMORY_THRESHOLD_BYTES) {
                SharedMemory sharedMemory = writeEmbeddingsToSharedMemory(bytes);
                if (sharedMemory != null) {
                    try {
                        dest.writeInt(bytes.length);
                        dest.writeTypedObject(sharedMemory, flags);
                    } finally {
                        // Safe to close here because writeTypedObject internally dupes the file
                        // descriptor backing the SharedMemory, which is required for IPC.
                        sharedMemory.close();
                    }
                    return;
                }
            }
            dest.writeInt(-1);
            dest.writeTypedList(embeddings);
        }
    }

    @Nullable
    private SharedMemory writeEmbeddingsToSharedMemory(byte[] bytes) {
        SharedMemory sharedMemory = null;
        ByteBuffer buffer = null;
        try {
            sharedMemory = SharedMemory.create(/* debugName= */ "EmbeddingResponse", bytes.length);
            buffer = sharedMemory.mapReadWrite();
            buffer.put(bytes);
            SharedMemory.unmap(buffer);
            buffer = null;
            sharedMemory.setProtect(OsConstants.PROT_READ);
            return sharedMemory;
        } catch (RuntimeException | ErrnoException e) {
            Log.e(TAG, "Failed to create SharedMemory", e);
            if (sharedMemory != null) {
                sharedMemory.close();
            }
            return null;
        } finally {
            if (buffer != null) {
                SharedMemory.unmap(buffer);
            }
        }
    }

    public static final @NonNull Creator<EmbeddingResponse> CREATOR =
            new Creator<EmbeddingResponse>() {
                @Override
                public EmbeddingResponse createFromParcel(Parcel in) {
                    return new EmbeddingResponse(in);
                }

                @Override
                public EmbeddingResponse[] newArray(int size) {
                    return new EmbeddingResponse[size];
                }
            };

    private EmbeddingResponse(Parcel in) {
        synchronized (mLock) {
            int size = in.readInt();
            if (size >= 0) {
                mSharedMemorySize = size;
                mSharedMemory = in.readTypedObject(SharedMemory.CREATOR);
                mEmbeddings = null;
            } else {
                mSharedMemorySize = -1;
                mSharedMemory = null;
                List<EmbeddingVector> embeddings = new ArrayList<>();
                in.readTypedList(embeddings, EmbeddingVector.CREATOR);
                mEmbeddings = embeddings;
            }
        }
    }

    private byte[] serializeEmbeddings(List<EmbeddingVector> embeddings) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeTypedList(embeddings);
            return parcel.marshall();
        } finally {
            parcel.recycle();
        }
    }
}
