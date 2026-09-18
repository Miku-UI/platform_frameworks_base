/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef _ANDROID_GRAPHICS_SURFACETEXTURE_H
#define _ANDROID_GRAPHICS_SURFACETEXTURE_H

#include <com_android_graphics_libgui_flags.h>
#include <utils/StrongPointer.h>

#include "jni.h"

namespace android {

class IGraphicBufferProducer;
class Surface;
class SurfaceTexture;

extern bool android_SurfaceTexture_isInstanceOf(JNIEnv* env, jobject thiz);

/* Gets the underlying C++ SurfaceTexture object from a SurfaceTexture Java object. */
extern sp<SurfaceTexture> SurfaceTexture_getSurfaceTexture(JNIEnv* env, jobject thiz);

/* gets the producer end of the SurfaceTexture */
#if COM_ANDROID_GRAPHICS_LIBGUI_FLAGS(WB_SURFACETEXTURE)
extern sp<Surface> SurfaceTexture_getSurface(JNIEnv* env, jobject thiz);
#else
extern sp<IGraphicBufferProducer> SurfaceTexture_getProducer(JNIEnv* env, jobject thiz);
#endif

} // namespace android

#endif // _ANDROID_GRAPHICS_SURFACETEXTURE_H
