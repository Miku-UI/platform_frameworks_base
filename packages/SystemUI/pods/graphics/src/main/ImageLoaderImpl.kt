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
import android.annotation.Px
import android.annotation.SuppressLint
import android.annotation.WorkerThread
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.Log
import android.util.Size
import androidx.annotation.VisibleForTesting
import androidx.core.content.res.ResourcesCompat
import com.android.app.tracing.traceSection
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.graphics.ImageLoader.File
import com.android.systemui.graphics.ImageLoader.InputStream
import com.android.systemui.graphics.ImageLoader.OversizedImageException
import com.android.systemui.graphics.ImageLoader.Res
import com.android.systemui.graphics.ImageLoader.Source
import com.android.systemui.graphics.ImageLoader.Uri
import javax.inject.Inject
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@SysUISingleton
class ImageLoaderImpl
@Inject
constructor(
    @Application private val defaultContext: Context,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : ImageLoader {

    @AnyThread
    override suspend fun loadBitmap(
        source: Source,
        @Px maxWidth: Int,
        @Px maxHeight: Int,
        allocator: Int,
    ): Bitmap? =
        withContext(backgroundDispatcher) { loadBitmapSync(source, maxWidth, maxHeight, allocator) }

    @WorkerThread
    override fun loadBitmapSync(
        source: Source,
        @Px maxWidth: Int,
        @Px maxHeight: Int,
        allocator: Int,
    ): Bitmap? {
        return try {
            loadBitmapSync(
                toImageDecoderSource(source, defaultContext),
                maxWidth,
                maxHeight,
                allocator,
            )
        } catch (e: NotFoundException) {
            Log.w(TAG, "Couldn't load resource $source", e)
            null
        }
    }

    @WorkerThread
    override fun loadBitmapSync(
        source: ImageDecoder.Source,
        @Px maxWidth: Int,
        @Px maxHeight: Int,
        allocator: Int,
    ): Bitmap? =
        traceSection("ImageLoader#loadBitmap") {
            return try {
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    configureDecoderForMaximumSize(decoder, info.size, maxWidth, maxHeight)
                    decoder.allocator = allocator
                }
            } catch (e: Exception) {
                // If we're loading an Uri, we can receive any exception from the other side.
                // So we have to catch them all.
                Log.w(TAG, "Failed to load source $source", e)
                return null
            }
        }

    @AnyThread
    override suspend fun loadDrawable(
        source: Source,
        @Px maxWidth: Int,
        @Px maxHeight: Int,
        allocator: Int,
    ): Drawable? =
        withContext(backgroundDispatcher) {
            loadDrawableSync(source, maxWidth, maxHeight, allocator)
        }

    @AnyThread
    override suspend fun loadDrawable(
        icon: Icon,
        context: Context,
        @Px maxWidth: Int,
        @Px maxHeight: Int,
        allocator: Int,
    ): Drawable? =
        withContext(backgroundDispatcher) {
            loadDrawableSync(icon, context, maxWidth, maxHeight, allocator)
        }

    @WorkerThread
    @SuppressLint("UseCompatLoadingForDrawables")
    override fun loadDrawableSync(
        source: Source,
        @Px maxWidth: Int,
        @Px maxHeight: Int,
        allocator: Int,
    ): Drawable? =
        traceSection("ImageLoader#loadDrawable") {
            return try {
                try {
                    ImageDecoder.decodeDrawable(toImageDecoderSource(source, defaultContext)) {
                        decoder,
                        info,
                        _ ->
                        configureDecoderForMaximumSize(decoder, info.size, maxWidth, maxHeight)
                        decoder.allocator = allocator
                    }
                } catch (e: Exception) {
                    if (e is OversizedImageException) {
                        // We don't want fallback if we detected an oversized drawable.
                        null
                    } else if (source is Res) {
                        // If we have a resource, retry fallback using the "normal" Resource loading
                        // system.
                        // This will come into effect in cases like trying to load
                        // AnimatedVectorDrawable.
                        val context = source.context ?: defaultContext
                        ResourcesCompat.getDrawable(context.resources, source.resId, context.theme)
                    } else {
                        null
                    }
                }
            } catch (e: NotFoundException) {
                Log.w(TAG, "Couldn't load resource $source", e)
                null
            }
        }

    @WorkerThread
    override fun loadDrawableSync(
        source: ImageDecoder.Source,
        @Px maxWidth: Int,
        @Px maxHeight: Int,
        allocator: Int,
    ): Drawable? =
        traceSection("ImageLoader#loadDrawable") {
            return try {
                ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    configureDecoderForMaximumSize(decoder, info.size, maxWidth, maxHeight)
                    decoder.allocator = allocator
                }
            } catch (e: Exception) {
                // If we're loading from an Uri, any exception can happen on the
                // other side. We have to catch them all.
                Log.w(TAG, "Failed to load source $source", e)
                return null
            }
        }

    @WorkerThread
    override fun loadDrawableSync(
        icon: Icon,
        context: Context,
        @Px maxWidth: Int,
        @Px maxHeight: Int,
        allocator: Int,
    ): Drawable? =
        traceSection("ImageLoader#loadDrawable") {
            return when (icon.type) {
                Icon.TYPE_URI,
                Icon.TYPE_URI_ADAPTIVE_BITMAP -> {
                    val source = ImageDecoder.createSource(context.contentResolver, icon.uri)
                    loadDrawableSync(source, maxWidth, maxHeight, allocator)
                }
                Icon.TYPE_RESOURCE -> {
                    val resources = resolveResourcesForIcon(context, icon)
                    resources?.let {
                        loadDrawableSync(
                            ImageDecoder.createSource(it, icon.resId),
                            maxWidth,
                            maxHeight,
                            allocator,
                        )
                    }
                        // Fallback to non-ImageDecoder load if the attempt failed (e.g. the
                        // resource
                        // is a Vector drawable which ImageDecoder doesn't support.)
                        ?: loadIconDrawable(icon, context)
                }
                Icon.TYPE_BITMAP -> {
                    BitmapDrawable(context.resources, icon.bitmap)
                }
                Icon.TYPE_ADAPTIVE_BITMAP -> {
                    AdaptiveIconDrawable(null, BitmapDrawable(context.resources, icon.bitmap))
                }
                Icon.TYPE_DATA -> {
                    loadDrawableSync(
                        ImageDecoder.createSource(icon.dataBytes, icon.dataOffset, icon.dataLength),
                        maxWidth,
                        maxHeight,
                        allocator,
                    )
                }
                else -> {
                    // We don't recognize this icon, just fallback.
                    loadIconDrawable(icon, context)
                }
            }?.let { drawable ->
                // Icons carry tint which we need to propagate down to a Drawable.
                tintDrawable(icon, drawable)
                drawable
            }
        }

    @WorkerThread
    override fun loadIconDrawable(icon: Icon, context: Context): Drawable? {
        icon.loadDrawable(context)?.let {
            return it
        }

        Log.w(TAG, "Failed to load drawable for $icon")
        return null
    }

    override suspend fun loadSize(icon: Icon, context: Context): Size? =
        withContext(backgroundDispatcher) { loadSizeSync(icon, context) }

    @WorkerThread
    override fun loadSizeSync(icon: Icon, context: Context): Size? {
        return when (icon.type) {
            Icon.TYPE_URI,
            Icon.TYPE_URI_ADAPTIVE_BITMAP -> {
                val source = ImageDecoder.createSource(context.contentResolver, icon.uri)
                loadSizeSync(source)
            }
            else -> null
        }
    }

    @WorkerThread
    override fun loadSizeSync(source: ImageDecoder.Source): Size? {
        return try {
            ImageDecoder.decodeHeader(source).size
        } catch (e: Exception) {
            // Any exception can happen when loading Uris, so we have to catch them all.
            Log.w(TAG, "Failed to load source $source", e)
            return null
        }
    }

    companion object {
        private const val TAG = "ImageLoader"

        /**
         * If an image is larger than this, we won't even attempt to decode it, as we risk taking up
         * all of the device memory.
         */
        private const val DEFAULT_DECODE_HARD_LIMIT_PX = 4096

        /**
         * This constant signals that ImageLoader shouldn't attempt to resize the passed bitmap in a
         * given dimension.
         *
         * Set both maxWidth and maxHeight to [DO_NOT_RESIZE] if you wish to prevent resizing.
         */
        @VisibleForTesting const val DO_NOT_RESIZE = 0

        /** Maps [Source] to [ImageDecoder.Source]. */
        private fun toImageDecoderSource(source: Source, defaultContext: Context) =
            when (source) {
                is Res -> {
                    val context = source.context ?: defaultContext
                    ImageDecoder.createSource(context.resources, source.resId)
                }
                is File -> ImageDecoder.createSource(source.file)
                is Uri -> {
                    val context = source.context ?: defaultContext
                    ImageDecoder.createSource(context.contentResolver, source.uri)
                }
                is InputStream -> {
                    val context = source.context ?: defaultContext
                    ImageDecoder.createSource(context.resources, source.inputStream)
                }
            }

        /**
         * This sets target size on the image decoder to conform to the maxWidth / maxHeight
         * parameters. The parameters are chosen to keep the existing drawable aspect ratio.
         */
        @AnyThread
        private fun configureDecoderForMaximumSize(
            decoder: ImageDecoder,
            imgSize: Size,
            @Px maxWidth: Int,
            @Px maxHeight: Int,
        ) {
            val width = imgSize.getWidth()
            val height = imgSize.getHeight()
            if (width > DEFAULT_DECODE_HARD_LIMIT_PX || height > DEFAULT_DECODE_HARD_LIMIT_PX) {
                Log.e(
                    TAG,
                    "Image dimensions (${width}x$height) " +
                        "exceed the maximum " +
                        "${DEFAULT_DECODE_HARD_LIMIT_PX}x${DEFAULT_DECODE_HARD_LIMIT_PX}",
                )
                // The image is larger than what we can reasonably expect to decode without filling
                // up the device memory, so let's bail.
                throw OversizedImageException(
                    "Image dimensions (${width}x$height) exceed the maximum allowed size."
                )
            }

            if (maxWidth == DO_NOT_RESIZE && maxHeight == DO_NOT_RESIZE) {
                return
            }

            if (width <= maxWidth && height <= maxHeight) {
                return
            }

            // Determine the scale factor for each dimension so it fits within the set constraint
            val wScale =
                if (maxWidth <= 0) {
                    1.0f
                } else {
                    maxWidth.toFloat() / imgSize.width.toFloat()
                }

            val hScale =
                if (maxHeight <= 0) {
                    1.0f
                } else {
                    maxHeight.toFloat() / imgSize.height.toFloat()
                }

            // Scale down to the dimension that demands larger scaling (smaller scale factor).
            // Use the same scale for both dimensions to keep the aspect ratio.
            val scale = min(wScale, hScale)
            if (scale < 1.0f) {
                val targetWidth = (width * scale).toInt()
                val targetHeight = (height * scale).toInt()
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Configured image size to $targetWidth x $targetHeight")
                }

                decoder.setTargetSize(targetWidth, targetHeight)
            }
        }

        /**
         * Attempts to retrieve [Resources] class required to load the passed icon. Icons can
         * originate from other processes so we need to make sure we load them from the right
         * package source.
         *
         * @return [Resources] to load the icon drawable or null if icon doesn't carry a resource or
         *   the resource package couldn't be resolved.
         */
        @WorkerThread
        private fun resolveResourcesForIcon(context: Context, icon: Icon): Resources? {
            if (icon.type != Icon.TYPE_RESOURCE) {
                return null
            }

            val resources = icon.resources
            if (resources != null) {
                return resources
            }

            val resPackage = icon.resPackage
            if (
                resPackage == null || resPackage.isEmpty() || context.packageName.equals(resPackage)
            ) {
                return context.resources
            }

            if ("android" == resPackage) {
                return Resources.getSystem()
            }

            val pm = context.packageManager
            try {
                val ai =
                    pm.getApplicationInfo(
                        resPackage,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES or
                            PackageManager.GET_SHARED_LIBRARY_FILES,
                    )
                if (ai != null) {
                    return pm.getResourcesForApplication(ai)
                } else {
                    Log.w(TAG, "Failed to resolve application info for $resPackage")
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Failed to resolve resource package", e)
                return null
            }
            return null
        }

        /** Applies tinting from [Icon] to the passed [Drawable]. */
        @AnyThread
        private fun tintDrawable(icon: Icon, drawable: Drawable) {
            if (icon.hasTint()) {
                drawable.mutate()
                drawable.setTintList(icon.tintList)
                drawable.setTintBlendMode(icon.tintBlendMode)
            }
        }
    }
}
