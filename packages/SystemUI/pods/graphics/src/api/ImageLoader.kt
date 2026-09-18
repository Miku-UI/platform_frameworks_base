/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.systemui.graphics

import android.annotation.AnyThread
import android.annotation.DrawableRes
import android.annotation.Px
import android.annotation.SuppressLint
import android.annotation.WorkerThread
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.Size
import java.io.IOException

/**
 * Helper class to load images for SystemUI. It allows for memory efficient image loading with size
 * restriction and attempts to use hardware bitmaps when sensible.
 */
public interface ImageLoader {

    /** Source of the image data. */
    public sealed interface Source

    /**
     * Load image from a Resource ID. If the resource is part of another package or if it requires
     * tinting, pass in a correct [Context].
     */
    public data class Res(@DrawableRes val resId: Int, val context: Context?) : Source {
        public constructor(@DrawableRes resId: Int) : this(resId, null)
    }

    /** Load image from a Uri. */
    public data class Uri(val uri: android.net.Uri, val context: Context?) : Source {
        public constructor(uri: String) : this(android.net.Uri.parse(uri), null)

        public constructor(uri: android.net.Uri) : this(uri, null)
    }

    /** Load image from a [File]. */
    public data class File(val file: java.io.File) : Source {
        public constructor(path: String) : this(java.io.File(path))
    }

    /** Load image from an [InputStream]. */
    public data class InputStream(val inputStream: java.io.InputStream, val context: Context?) :
        Source {
        public constructor(inputStream: java.io.InputStream) : this(inputStream, null)
    }

    /** Exception thrown when the image is too large to be decoded. */
    public class OversizedImageException(message: String) : IOException(message)

    /**
     * Loads passed [Source] on a background thread and returns the [Bitmap].
     *
     * Maximum height and width can be passed as optional parameters - the image decoder will make
     * sure to keep the decoded drawable size within those passed constraints while keeping aspect
     * ratio.
     *
     * @param maxWidth Maximum width of the returned drawable (if able). 0 means no restriction. Set
     *   to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param maxHeight Maximum height of the returned drawable (if able). 0 means no restriction.
     *   Set to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param allocator Allocator to use for the loaded drawable - one of [ImageDecoder] allocator
     *   ints. Use [ImageDecoder.ALLOCATOR_SOFTWARE] to force software bitmap.
     * @return loaded [Bitmap] or `null` if loading failed.
     */
    @AnyThread
    public suspend fun loadBitmap(
        source: Source,
        @Px maxWidth: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        @Px maxHeight: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        allocator: Int = ImageDecoder.ALLOCATOR_DEFAULT,
    ): Bitmap?

    /**
     * Loads passed [Source] synchronously and returns the [Bitmap].
     *
     * Maximum height and width can be passed as optional parameters - the image decoder will make
     * sure to keep the decoded drawable size within those passed constraints while keeping aspect
     * ratio.
     *
     * @param maxWidth Maximum width of the returned drawable (if able). 0 means no restriction. Set
     *   to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param maxHeight Maximum height of the returned drawable (if able). 0 means no restriction.
     *   Set to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param allocator Allocator to use for the loaded drawable - one of [ImageDecoder] allocator
     *   ints. Use [ImageDecoder.ALLOCATOR_SOFTWARE] to force software bitmap.
     * @return loaded [Bitmap] or `null` if loading failed.
     */
    @WorkerThread
    public fun loadBitmapSync(
        source: Source,
        @Px maxWidth: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        @Px maxHeight: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        allocator: Int = ImageDecoder.ALLOCATOR_DEFAULT,
    ): Bitmap?

    /**
     * Loads passed [ImageDecoder.Source] synchronously and returns the drawable.
     *
     * Maximum height and width can be passed as optional parameters - the image decoder will make
     * sure to keep the decoded drawable size within those passed constraints (while keeping aspect
     * ratio).
     *
     * @param maxWidth Maximum width of the returned drawable (if able). 0 means no restriction. Set
     *   to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param maxHeight Maximum height of the returned drawable (if able). 0 means no restriction.
     *   Set to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param allocator Allocator to use for the loaded drawable - one of [ImageDecoder] allocator
     *   ints. Use [ImageDecoder.ALLOCATOR_SOFTWARE] to force software bitmap.
     * @return loaded [Bitmap] or `null` if loading failed.
     */
    @WorkerThread
    public fun loadBitmapSync(
        source: ImageDecoder.Source,
        @Px maxWidth: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        @Px maxHeight: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        allocator: Int = ImageDecoder.ALLOCATOR_DEFAULT,
    ): Bitmap?

    /**
     * Loads passed [Source] on a background thread and returns the [Drawable].
     *
     * Maximum height and width can be passed as optional parameters - the image decoder will make
     * sure to keep the decoded drawable size within those passed constraints (while keeping aspect
     * ratio).
     *
     * @param maxWidth Maximum width of the returned drawable (if able). 0 means no restriction. Set
     *   to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param maxHeight Maximum height of the returned drawable (if able). 0 means no restriction.
     *   Set to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param allocator Allocator to use for the loaded drawable - one of [ImageDecoder] allocator
     *   ints. Use [ImageDecoder.ALLOCATOR_SOFTWARE] to force software bitmap.
     * @return loaded [Drawable] or `null` if loading failed.
     */
    @AnyThread
    public suspend fun loadDrawable(
        source: Source,
        @Px maxWidth: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        @Px maxHeight: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        allocator: Int = ImageDecoder.ALLOCATOR_DEFAULT,
    ): Drawable?

    /**
     * Loads passed [Icon] on a background thread and returns the drawable.
     *
     * Maximum height and width can be passed as optional parameters - the image decoder will make
     * sure to keep the decoded drawable size within those passed constraints (while keeping aspect
     * ratio).
     *
     * @param context Alternate context to use for resource loading (for e.g. cross-process use)
     * @param maxWidth Maximum width of the returned drawable (if able). 0 means no restriction. Set
     *   to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param maxHeight Maximum height of the returned drawable (if able). 0 means no restriction.
     *   Set to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param allocator Allocator to use for the loaded drawable - one of [ImageDecoder] allocator
     *   ints. Use [ImageDecoder.ALLOCATOR_SOFTWARE] to force software bitmap.
     * @return loaded [Drawable] or `null` if loading failed.
     */
    @AnyThread
    public suspend fun loadDrawable(
        icon: Icon,
        context: Context,
        @Px maxWidth: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        @Px maxHeight: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        allocator: Int = ImageDecoder.ALLOCATOR_DEFAULT,
    ): Drawable?

    /**
     * Loads passed [Source] synchronously and returns the drawable.
     *
     * Maximum height and width can be passed as optional parameters - the image decoder will make
     * sure to keep the decoded drawable size within those passed constraints (while keeping aspect
     * ratio).
     *
     * @param maxWidth Maximum width of the returned drawable (if able). 0 means no restriction. Set
     *   to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param maxHeight Maximum height of the returned drawable (if able). 0 means no restriction.
     *   Set to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param allocator Allocator to use for the loaded drawable - one of [ImageDecoder] allocator
     *   ints. Use [ImageDecoder.ALLOCATOR_SOFTWARE] to force software bitmap.
     * @return loaded [Drawable] or `null` if loading failed.
     */
    @WorkerThread
    @SuppressLint("UseCompatLoadingForDrawables")
    public fun loadDrawableSync(
        source: Source,
        @Px maxWidth: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        @Px maxHeight: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        allocator: Int = ImageDecoder.ALLOCATOR_DEFAULT,
    ): Drawable?

    /**
     * Loads passed [ImageDecoder.Source] synchronously and returns the drawable.
     *
     * Maximum height and width can be passed as optional parameters - the image decoder will make
     * sure to keep the decoded drawable size within those passed constraints (while keeping aspect
     * ratio).
     *
     * @param maxWidth Maximum width of the returned drawable (if able). 0 means no restriction. Set
     *   to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param maxHeight Maximum height of the returned drawable (if able). 0 means no restriction.
     *   Set to [DEFAULT_MAX_SAFE_BITMAP_SIZE_PX] by default.
     * @param allocator Allocator to use for the loaded drawable - one of [ImageDecoder] allocator
     *   ints. Use [ImageDecoder.ALLOCATOR_SOFTWARE] to force software bitmap.
     * @return loaded [Drawable] or `null` if loading failed.
     */
    @WorkerThread
    public fun loadDrawableSync(
        source: ImageDecoder.Source,
        @Px maxWidth: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        @Px maxHeight: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        allocator: Int = ImageDecoder.ALLOCATOR_DEFAULT,
    ): Drawable?

    /** Loads icon drawable while attempting to size restrict the drawable. */
    @WorkerThread
    public fun loadDrawableSync(
        icon: Icon,
        context: Context,
        @Px maxWidth: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        @Px maxHeight: Int = DEFAULT_MAX_SAFE_BITMAP_SIZE_PX,
        allocator: Int = ImageDecoder.ALLOCATOR_DEFAULT,
    ): Drawable?

    @WorkerThread public fun loadIconDrawable(icon: Icon, context: Context): Drawable?

    /**
     * Obtains the image size from the image header, without decoding the full image.
     *
     * @param icon an [Icon] representing the source of the image
     * @return the [Size] if it could be determined from the image header, or `null` otherwise
     */
    public suspend fun loadSize(icon: Icon, context: Context): Size?

    /**
     * Obtains the image size from the image header, without decoding the full image.
     *
     * @param icon an [Icon] representing the source of the image
     * @return the [Size] if it could be determined from the image header, or `null` otherwise
     */
    @WorkerThread public fun loadSizeSync(icon: Icon, context: Context): Size?

    /**
     * Obtains the image size from the image header, without decoding the full image.
     *
     * @param source [ImageDecoder.Source] of the image
     * @return the [Size] if it could be determined from the image header, or `null` otherwise
     */
    @WorkerThread public fun loadSizeSync(source: ImageDecoder.Source): Size?

    public companion object {
        // 4096 is a reasonable default - most devices will support 4096x4096 texture size for
        // Canvas rendering and by default we SystemUI has no need to render larger bitmaps.
        // This prevents exceptions and crashes if the code accidentally loads larger Bitmap
        // and then attempts to render it on Canvas.
        // It can always be overridden by the parameters.
        public const val DEFAULT_MAX_SAFE_BITMAP_SIZE_PX: Int = 4096
    }
}
