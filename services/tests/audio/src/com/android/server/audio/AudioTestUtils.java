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

package com.android.server.audio;

import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.util.SparseIntArray;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class AudioTestUtils {

    private AudioTestUtils() {
        throw new UnsupportedOperationException();
    }

    static SparseIntArray getMaxGroupValues(List<AudioProductStrategy> productStrategies,
            AudioManager audioManager) {
        Objects.requireNonNull(productStrategies, "Audio product strategies cannot be null");
        Objects.requireNonNull(audioManager, "Audio manager cannot be null");
        return getBoundaryValueForVolumeGroups(productStrategies,
                audioManager::getVolumeGroupMaxVolumeIndex);
    }

    static SparseIntArray getMinGroupValues(List<AudioProductStrategy> productStrategies,
            AudioManager audioManager) {
        Objects.requireNonNull(productStrategies, "Audio product strategies cannot be null");
        Objects.requireNonNull(audioManager, "Audio manager cannot be null");
        return getBoundaryValueForVolumeGroups(productStrategies,
                audioManager::getVolumeGroupMinVolumeIndex);
    }

    private static SparseIntArray getBoundaryValueForVolumeGroups(
            List<AudioProductStrategy> productStrategies,
            Function<Integer, Integer> groupToBoundaryValueMapper) {
        SparseIntArray boundaryValues = new SparseIntArray();
        for (int stream : AudioManager.getPublicStreamTypes()) {
            var id = AudioProductStrategy.getVolumeGroupIdForStreamType(productStrategies, stream);
            if (id == AudioProductStrategy.DEFAULT_GROUP) {
                continue;
            }
            boundaryValues.put(id, groupToBoundaryValueMapper.apply(id));
        }
        return boundaryValues;
    }
}
