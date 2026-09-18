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

#include "android_media_AudioFormat.h"

audio_channel_mask_t ChannelMasks::toNative(jboolean isInput) {
    if (indexMask != CHANNEL_INVALID) { // channel index mask takes priority
        // To convert to a native channel mask, the Java channel index mask
        // requires adding the index representation.
        return audio_channel_mask_from_representation_and_bits(AUDIO_CHANNEL_REPRESENTATION_INDEX,
                                                               indexMask);
    }
    if (positionMask != CHANNEL_INVALID) {
        // Haptic channels should be preserved between java and native masks.
        uint32_t audioChannels = (positionMask & ~AUDIO_CHANNEL_HAPTIC_ALL);
        uint32_t hapticChannels = (positionMask & AUDIO_CHANNEL_HAPTIC_ALL);
        uint32_t convertedAudioChannels = isInput ? inChannelMaskToNative(audioChannels)
                                                  : outChannelMaskToNative(audioChannels);
        return (audio_channel_mask_t)(convertedAudioChannels | hapticChannels);
    }
    if (acnMask != CHANNEL_INVALID) {
        return audio_channel_mask_from_representation_and_bits(AUDIO_CHANNEL_REPRESENTATION_ACN,
                                                               acnMask);
    }
    return AUDIO_CHANNEL_NONE;
}

ScopedLocalRef<jobject> javaChannelMasksFromNativeChannelMask(JNIEnv* env,
                                                              const ChannelMasks::fields_t fields,
                                                              audio_channel_mask_t nMask,
                                                              jboolean isInput) {
    ChannelMasks masks{};
    switch (audio_channel_mask_get_representation(nMask)) {
        case AUDIO_CHANNEL_REPRESENTATION_INDEX:
            masks.indexMask = audio_channel_mask_get_bits(nMask);
            break;
        case AUDIO_CHANNEL_REPRESENTATION_POSITION:
            masks.positionMask =
                    isInput ? inChannelMaskFromNative(nMask) : outChannelMaskFromNative(nMask);
            break;
        case AUDIO_CHANNEL_REPRESENTATION_ACN:
            masks.acnMask = audio_channel_mask_get_bits(nMask);
            break;
        default:
            break;
    }
    return ScopedLocalRef<jobject>(env,
                                   env->NewObject(fields.clazz, fields.constructID,
                                                  masks.positionMask, masks.indexMask,
                                                  masks.acnMask));
}

ScopedLocalRef<jobject> javaChannelMasksArrayFromNative(JNIEnv* env,
                                                        const ChannelMasksArray::fields_t& fields,
                                                        unsigned int num_channel_masks,
                                                        const audio_channel_mask_t* channel_masks,
                                                        jboolean useInMask) {
    size_t numPositionMasks = 0;
    size_t numIndexMasks = 0;
    size_t numAcnMasks = 0;
    // count up how many masks are per type
    for (size_t index = 0; index < num_channel_masks; index++) {
        const audio_channel_mask_t mask = channel_masks[index];
        const audio_channel_representation_t kind = audio_channel_mask_get_representation(mask);
        if (kind == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
            numIndexMasks++;
        } else if (kind == AUDIO_CHANNEL_REPRESENTATION_ACN) {
            numAcnMasks++;
        } else {
            numPositionMasks++;
        }
    }
    ScopedLocalRef<jintArray> jChannelMasks(env, env->NewIntArray(numPositionMasks));
    ScopedLocalRef<jintArray> jChannelIndexMasks(env, env->NewIntArray(numIndexMasks));
    ScopedLocalRef<jintArray> jChannelAcnMasks(env, env->NewIntArray(numAcnMasks));
    if (!jChannelMasks.get() || !jChannelIndexMasks.get() || !jChannelAcnMasks.get()) {
        return ScopedLocalRef<jobject>(env);
    }
    // put the masks in the output arrays
    for (size_t maskIndex = 0, posMaskIndex = 0, indexedMaskIndex = 0, acnMaskIndex = 0;
         maskIndex < num_channel_masks; maskIndex++) {
        const audio_channel_mask_t mask = channel_masks[maskIndex];
        const audio_channel_representation_t kind = audio_channel_mask_get_representation(mask);
        if (kind == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
            jint jMask = audio_channel_mask_get_bits(mask);
            env->SetIntArrayRegion(jChannelIndexMasks.get(), indexedMaskIndex++, 1, &jMask);
        } else if (kind == AUDIO_CHANNEL_REPRESENTATION_ACN) {
            jint jMask = audio_channel_mask_get_bits(mask);
            env->SetIntArrayRegion(jChannelAcnMasks.get(), acnMaskIndex++, 1, &jMask);
        } else {
            jint jMask = useInMask ? inChannelMaskFromNative(mask) : outChannelMaskFromNative(mask);
            env->SetIntArrayRegion(jChannelMasks.get(), posMaskIndex++, 1, &jMask);
        }
    }
    return ScopedLocalRef<jobject>(env,
                                   env->NewObject(fields.clazz, fields.constructID,
                                                  jChannelMasks.get(), jChannelIndexMasks.get(),
                                                  jChannelAcnMasks.get()));
}
