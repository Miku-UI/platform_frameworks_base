/*
 * Copyright 2025 The Android Open Source Project
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

package android.media.projection;

import android.annotation.FlaggedApi;
import android.util.Size;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Contains the requested characteristic for an app content projection.
 * <p>
 * An instance of this class is passed to
 * {@link AppContentProjectionService#onContentRequest(AppContentRequest)}
 * <p>
 * Available content must be returned by calling {@link #provideContent(List)}}
 */
@FlaggedApi(com.android.media.projection.flags.Flags.FLAG_APP_CONTENT_SHARING)
public final class AppContentRequest {

    private final Size mThumbnailSize;
    private final Consumer<MediaProjectionAppContent[]> mContentConsumer;

    /**
     * @hide
     */
    public AppContentRequest(@NonNull Size thumbnailSize,
            @NonNull Consumer<MediaProjectionAppContent[]> contentConsumer) {
        mThumbnailSize = Objects.requireNonNull(thumbnailSize);
        mContentConsumer = Objects.requireNonNull(contentConsumer);
    }

    /**
     * Used to return the content to the requester. The content passed here will replace any
     * previously provided content.
     * <p>
     * If no more content is available to offer, an empty list can be passed.
     */
    public void provideContent(@NonNull List<MediaProjectionAppContent> content) {
        Objects.requireNonNull(content,
                "content must not be null. If all the content needs to be removed, an empty list"
                        + " can be passed");
        mContentConsumer.accept(content.toArray(new MediaProjectionAppContent[0]));
    }

    /**
     * @return The requested thumbnail size, in px, for each {@link MediaProjectionAppContent}
     * item.
     */
    @NonNull
    public Size getThumbnailSize() {
        return mThumbnailSize;
    }

}
