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

package com.android.server.companion.datatransfer.continuity.messages;

import android.annotation.NonNull;
import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.util.Log;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class HandoffActivityDataSerializer {

    private static final String TAG = "HandoffActivityDataSerializer";

    public static void writeToProto(
            HandoffActivityData handoffActivityData,
            ProtoOutputStream pos) throws IOException {

        String flattenedComponentName = handoffActivityData.getComponentName().flattenToString();
        pos.writeString(
            android.companion.HandoffActivityData.COMPONENT_NAME,
            flattenedComponentName);

        Uri fallbackUri = handoffActivityData.getFallbackUri();
        if (fallbackUri != null) {
            pos.writeString(
                android.companion.HandoffActivityData.FALLBACK_URI,
                fallbackUri.toString());
        }

        PersistableBundle extras = handoffActivityData.getExtras();
        if (!extras.isEmpty()) {
            ByteArrayOutputStream extrasStream = new ByteArrayOutputStream();
            extras.writeToStream(extrasStream);
            pos.writeBytes(
                android.companion.HandoffActivityData.EXTRAS,
                extrasStream.toByteArray());
        }
    }

    public static HandoffActivityData readFromProto(ProtoInputStream pis) throws IOException {

        ComponentName componentName = null;
        Uri fallbackUri = null;
        PersistableBundle extras = new PersistableBundle();

        while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (pis.getFieldNumber()) {
                case (int) android.companion.HandoffActivityData.COMPONENT_NAME:
                    String flattenedComponentName
                        = pis.readString(android.companion.HandoffActivityData.COMPONENT_NAME);

                    componentName = ComponentName.unflattenFromString(flattenedComponentName);
                    if (componentName == null) {
                        throw new IOException(
                                "Invalid component name in proto: " + flattenedComponentName);
                    }

                    break;
                case (int) android.companion.HandoffActivityData.FALLBACK_URI:
                    String flattenedFallbackUri
                        = pis.readString(android.companion.HandoffActivityData.FALLBACK_URI);

                    fallbackUri = Uri.parse(flattenedFallbackUri);
                    if (fallbackUri == null) {
                        Log.w(TAG, "Invalid URI received in HandoffActivityData proto. Ignoring.");
                    }

                    break;
                case (int) android.companion.HandoffActivityData.EXTRAS:
                    byte[] rawExtras
                        = pis.readBytes(android.companion.HandoffActivityData.EXTRAS);

                    ByteArrayInputStream extrasStream = new ByteArrayInputStream(rawExtras);
                    PersistableBundle newExtras = PersistableBundle.readFromStream(extrasStream);
                    if (newExtras != null) {
                        extras = newExtras;
                    }
                    break;
                default:
                    Log.w(TAG, "Skipping unknown field in HandoffActivityData: "
                        + pis.getFieldNumber());
                    break;
            }
        }

        if (componentName == null) {
            throw new IOException("No component name in proto");
        }

        return new HandoffActivityData.Builder(componentName)
                .setFallbackUri(fallbackUri)
                .setExtras(extras)
                .build();
    }
}