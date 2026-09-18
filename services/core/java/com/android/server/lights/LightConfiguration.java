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

package com.android.server.lights;

import android.hardware.lights.LightState;
import android.hardware.lights.MultiLightEffect;
import android.text.TextUtils;

import java.util.concurrent.atomic.AtomicInteger;

final class LightConfiguration {
    private static final AtomicInteger sNextVersion = new AtomicInteger(0);
    private final MultiLightEffect mEffect;
    private final LightState mState;
    private final int mVersion;

    LightConfiguration(MultiLightEffect effect) {
        mState = null;
        mEffect = effect;
        mVersion = sNextVersion.getAndAdd(1);
    }

    LightConfiguration(LightState state) {
        mState = state;
        mEffect = null;
        mVersion = sNextVersion.getAndAdd(1);
    }

    boolean isDynamic() {
        return mEffect != null;
    }

    public MultiLightEffect getEffect() {
        return mEffect;
    }

    public LightState getState() {
        return mState;
    }

    public int getVersion() {
        return mVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LightConfiguration that = (LightConfiguration) o;
        return mVersion == that.mVersion;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Configuration ");

        sb.append(TextUtils.formatSimple("version=%d", mVersion));
        if (mState != null) {
            sb.append(TextUtils.formatSimple(" state:{color=%08x}", mState.getColor()));
        }

        if (mEffect != null) {
            sb.append(TextUtils.formatSimple(" effect:{preemptive:%b segments=%d, duration=%dx%d}",
                    mEffect.isPreemptive(),
                    mEffect.getLights().length,
                    mEffect.getIterationDurationMillis(),
                    mEffect.getIterations()));
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(mVersion);
    }
}
