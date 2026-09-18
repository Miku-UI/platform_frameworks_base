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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * A composable that loads an image from a URI and displays it. Shows [placeholderContent] if the
 * [uri] is null or can't be loaded.
 *
 * @param uri the URI of the image
 * @param contentDescription A descriptive text for accessibility
 * @param size the size to render the image at.
 * @param loadBitmap the function that will be invoked to load the image.
 * @param placeholderContent content to use while the image is loading or if there was an error when
 *   loading the image.
 */
@Composable
fun AsyncUriImage(
    uri: Uri?,
    contentDescription: String?,
    size: Dp,
    loadBitmap: suspend (uri: Uri, context: Context, sizePx: Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    placeholderContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx().roundToInt() }
    val bitmap: Bitmap? by
        produceState(initialValue = null, uri, loadBitmap, context, sizePx) {
            uri?.let { value = loadBitmap(it, context, sizePx) }
        }
    Box(modifier = modifier.size(size)) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            placeholderContent()
        }
    }
}
