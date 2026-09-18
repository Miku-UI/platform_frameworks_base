/*
** Copyright 2013, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

// This source file is automatically generated

#pragma GCC diagnostic ignored "-Wunused-variable"
#pragma GCC diagnostic ignored "-Wunused-but-set-variable"
#pragma GCC diagnostic ignored "-Wunused-function"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android_runtime/AndroidRuntime.h>
#include <android_runtime/android_graphics_SurfaceTexture.h>
#include <android_runtime/android_hardware_HardwareBuffer.h>
#include <android_runtime/android_view_Surface.h>
#include <assert.h>
#include <gui/GLConsumer.h>
#include <gui/Surface.h>
#include <nativehelper/JNIPlatformHelp.h>
#include <ui/ANativeObjectBase.h>
#include <utils/misc.h>

#include "jni.h"

static jclass egldisplayClass;
static jclass eglsurfaceClass;
static jclass eglsyncClass;

static jmethodID egldisplayGetHandleID;
static jmethodID eglsurfaceGetHandleID;
static jmethodID eglsyncGetHandleID;

/* Cache method IDs each time the class is loaded. */

static void
nativeClassInit(JNIEnv *_env, jclass glImplClass)
{
    jclass egldisplayClassLocal = _env->FindClass("android/opengl/EGLDisplay");
    egldisplayClass = (jclass) _env->NewGlobalRef(egldisplayClassLocal);
    jclass eglsurfaceClassLocal = _env->FindClass("android/opengl/EGLSurface");
    eglsurfaceClass = (jclass) _env->NewGlobalRef(eglsurfaceClassLocal);
    jclass eglsyncClassLocal = _env->FindClass("android/opengl/EGLSync");
    eglsyncClass = (jclass) _env->NewGlobalRef(eglsyncClassLocal);

    egldisplayGetHandleID = _env->GetMethodID(egldisplayClass, "getNativeHandle", "()J");
    eglsurfaceGetHandleID = _env->GetMethodID(eglsurfaceClass, "getNativeHandle", "()J");
    eglsyncGetHandleID = _env->GetMethodID(eglsyncClass, "getNativeHandle", "()J");
}

static void *
fromEGLHandle(JNIEnv *_env, jmethodID mid, jobject obj) {
    if (obj == NULL){
        jniThrowException(_env, "java/lang/IllegalArgumentException",
                          "Object is set to null.");
        return nullptr;
    }

    return reinterpret_cast<void*>(_env->CallLongMethod(obj, mid));
}

// TODO: this should be generated from the .spec file, but needs to be renamed and made private
static jint android_eglDupNativeFenceFDANDROID(JNIEnv *env, jobject, jobject dpy, jobject sync) {
    EGLDisplay dpy_native = (EGLDisplay)fromEGLHandle(env, egldisplayGetHandleID, dpy);
    EGLSync sync_native = (EGLSync)fromEGLHandle(env, eglsyncGetHandleID, sync);

    return eglDupNativeFenceFDANDROID(dpy_native, sync_native);
}

// --------------------------------------------------------------------------
/* EGLBoolean eglPresentationTimeANDROID ( EGLDisplay dpy, EGLSurface sur, EGLnsecsANDROID time ) */
static jboolean
android_eglPresentationTimeANDROID
  (JNIEnv *_env, jobject _this, jobject dpy, jobject sur, jlong time) {
    EGLBoolean _returnValue = (EGLBoolean) 0;
    EGLDisplay dpy_native = (EGLDisplay) fromEGLHandle(_env, egldisplayGetHandleID, dpy);
    EGLSurface sur_native = (EGLSurface) fromEGLHandle(_env, eglsurfaceGetHandleID, sur);

    _returnValue = eglPresentationTimeANDROID(
        (EGLDisplay)dpy_native,
        (EGLSurface)sur_native,
        (EGLnsecsANDROID)time
    );
    return (jboolean)_returnValue;
}

/* EGLClientBuffer eglGetNativeClientBufferANDROID ( struct AHardwareBuffer const *buffer ) */
static jlong android_eglGetNativeClientBufferANDROID(JNIEnv* _env, jobject, jobject buffer) {
    auto ahb = android::android_hardware_HardwareBuffer_getNativeHardwareBuffer(_env, buffer);
    return reinterpret_cast<jlong>(eglGetNativeClientBufferANDROID(ahb));
}

static const char *classPathName = "android/opengl/EGLExt";

static const JNINativeMethod methods[] = {
        {"_nativeClassInit", "()V", (void*)nativeClassInit},
        {"eglPresentationTimeANDROID", "(Landroid/opengl/EGLDisplay;Landroid/opengl/EGLSurface;J)Z",
         (void*)android_eglPresentationTimeANDROID},
        {"eglDupNativeFenceFDANDROIDImpl", "(Landroid/opengl/EGLDisplay;Landroid/opengl/EGLSync;)I",
         (void*)android_eglDupNativeFenceFDANDROID},
        {"eglGetNativeClientBufferANDROID", "(Landroid/hardware/HardwareBuffer;)J",
         (void*)android_eglGetNativeClientBufferANDROID},
};

int register_android_opengl_jni_EGLExt(JNIEnv *_env)
{
    int err;
    err = android::AndroidRuntime::registerNativeMethods(_env, classPathName, methods, NELEM(methods));
    return err;
}
