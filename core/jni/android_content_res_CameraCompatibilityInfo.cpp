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
#include "core_jni_helpers.h"
#include "jni.h"

#include <android_runtime/android_content_res_CameraCompatibilityInfo.h>
#include <android/content/res/CameraCompatibilityInfo.h>

namespace android {
static struct {
    jclass clazz;
    jmethodID ctor;
    // CameraCompatibilityInfo.mRotateAndCropRotation
    jfieldID mRotateAndCropRotation;
    // CameraCompatibilityInfo.mShouldOverrideSensorOrientation
    jfieldID mShouldOverrideSensorOrientation;
    // CameraCompatibilityInfo.mShouldLetterboxForCameraCompat
    jfieldID mShouldLetterboxForCameraCompat;
    // CameraCompatibilityInfo.mDisplayRotationSandbox
    jfieldID mDisplayRotationSandbox;
    // CameraCompatibilityInfo.mShouldAllowTransformInverseDisplay
    jfieldID mShouldAllowTransformInverseDisplay;
} gCameraCompatibilityInfoClassInfo;

using content::res::CameraCompatibilityInfo;
jobject android_content_res_CameraCompatibilityInfo_fromNative(JNIEnv *env,
                                                               const CameraCompatibilityInfo& cci) {
    int tmpIntRotateAndCropRotation = -1;
    if (cci.getRotateAndCropRotation().has_value()) {
        tmpIntRotateAndCropRotation = ui::toRotationInt(cci.getRotateAndCropRotation().value());
    }
    int tmpIntDisplayRotationSandbox = -1;
    if (cci.getDisplayRotationSandbox().has_value()) {
        tmpIntDisplayRotationSandbox = ui::toRotationInt(
                cci.getDisplayRotationSandbox().value());
    }
    return env->NewObject(gCameraCompatibilityInfoClassInfo.clazz,
                          gCameraCompatibilityInfoClassInfo.ctor,
                          tmpIntRotateAndCropRotation,
                          cci.shouldOverrideSensorOrientation(),
                          cci.shouldLetterboxForCameraCompat(),
                          tmpIntDisplayRotationSandbox,
                          cci.shouldAllowTransformInverseDisplay());
}

 status_t android_content_res_CameraCompatibilityInfo_toNative(JNIEnv *env,
                        jobject cciObject, CameraCompatibilityInfo* compatInfo) {
     if (env == nullptr) {
         ALOGE("%s: env is null.", __FUNCTION__);
         return BAD_VALUE;
     }

    if (cciObject == nullptr) {
        ALOGE("%s: cciObject is null.", __FUNCTION__);
        return BAD_VALUE;
    }

    int tmpIntRotation = env->GetIntField(cciObject,
                                          gCameraCompatibilityInfoClassInfo.mRotateAndCropRotation);
    if (tmpIntRotation < 0) {
        compatInfo->setRotateAndCropRotation(std::nullopt);
    } else {
        compatInfo->setRotateAndCropRotation(ui::toRotation(tmpIntRotation));
    }
    compatInfo->setShouldOverrideSensorOrientation(env->GetBooleanField(cciObject,
                gCameraCompatibilityInfoClassInfo.mShouldOverrideSensorOrientation));
    compatInfo->setShouldLetterboxForCameraCompat(env->GetBooleanField(cciObject,
                gCameraCompatibilityInfoClassInfo.mShouldLetterboxForCameraCompat));
    tmpIntRotation = env->GetIntField(cciObject,
                gCameraCompatibilityInfoClassInfo.mDisplayRotationSandbox);
    if (tmpIntRotation < 0) {
        compatInfo->setDisplayRotationSandbox(std::nullopt);
    } else {
        compatInfo->setDisplayRotationSandbox(ui::toRotation(tmpIntRotation));
    }
    compatInfo->setShouldAllowTransformInverseDisplay(env->GetBooleanField(cciObject,
                gCameraCompatibilityInfoClassInfo.mShouldAllowTransformInverseDisplay));
    return OK;
}

int register_android_content_res_CameraCompatibilityInfo(JNIEnv* env) {
    jclass clazz = FindClassOrDie(env, "android/content/res/CameraCompatibilityInfo");

    gCameraCompatibilityInfoClassInfo.mRotateAndCropRotation = GetFieldIDOrDie(env, clazz,
            "mRotateAndCropRotation", "I");
    gCameraCompatibilityInfoClassInfo.mShouldOverrideSensorOrientation = GetFieldIDOrDie(env, clazz,
            "mShouldOverrideSensorOrientation", "Z");
    gCameraCompatibilityInfoClassInfo.mShouldLetterboxForCameraCompat = GetFieldIDOrDie(env, clazz,
            "mShouldLetterboxForCameraCompat", "Z");
    gCameraCompatibilityInfoClassInfo.mDisplayRotationSandbox = GetFieldIDOrDie(env, clazz,
            "mDisplayRotationSandbox", "I");
    gCameraCompatibilityInfoClassInfo.mShouldAllowTransformInverseDisplay = GetFieldIDOrDie(env,
            clazz, "mShouldAllowTransformInverseDisplay", "Z");
    gCameraCompatibilityInfoClassInfo.clazz = MakeGlobalRefOrDie(env, clazz);
    gCameraCompatibilityInfoClassInfo.ctor = env->GetMethodID(
            gCameraCompatibilityInfoClassInfo.clazz, "<init>", "()V");
    return 0;
}
}