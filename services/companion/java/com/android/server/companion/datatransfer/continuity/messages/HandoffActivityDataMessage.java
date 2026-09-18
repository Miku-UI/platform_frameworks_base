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
import android.annotation.Nullable;
import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.net.Uri;
import android.os.PersistableBundle;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record HandoffActivityDataMessage(
        @Nullable HandoffActivityData activity, @NonNull List<byte[]> packageSignatureDigests)
        implements Proto {

    public HandoffActivityDataMessage {
        Objects.requireNonNull(packageSignatureDigests);
    }

    public static class Builder extends Proto.Builder<HandoffActivityDataMessage> {
        private ComponentName componentName = null;
        private Uri fallbackUri = null;
        private PersistableBundle extras = null;
        private List<byte[]> packageSignatureDigests = new ArrayList<>();

        @NonNull
        public Builder setComponentName(@Nullable ComponentName componentName) {
            this.componentName = componentName;
            return this;
        }

        @NonNull
        public Builder setFallbackUri(@Nullable Uri fallbackUri) {
            this.fallbackUri = fallbackUri;
            return this;
        }

        @NonNull
        public Builder setExtras(@Nullable PersistableBundle extras) {
            this.extras = extras;
            return this;
        }

        @NonNull
        public Builder addPackageSignatureDigest(@NonNull byte[] packageSignatureDigest) {
            this.packageSignatureDigests.add(Objects.requireNonNull(packageSignatureDigest));
            return this;
        }

        @Override
        protected void processField(@NonNull ProtoInputStream pis, int fieldNumber)
                throws IOException {
            switch (fieldNumber) {
                case (int) android.companion.HandoffActivityDataMessage.COMPONENT_NAME -> {
                    String flattenedComponentName =
                            pis.readString(
                                    android.companion.HandoffActivityDataMessage.COMPONENT_NAME);
                    if (flattenedComponentName != null) {
                        setComponentName(ComponentName.unflattenFromString(flattenedComponentName));
                    }
                }
                case (int) android.companion.HandoffActivityDataMessage.FALLBACK_URI -> {
                    String flattenedFallbackUri =
                            pis.readString(
                                    android.companion.HandoffActivityDataMessage.FALLBACK_URI);
                    if (flattenedFallbackUri != null) {
                        setFallbackUri(Uri.parse(flattenedFallbackUri));
                    }
                }
                case (int) android.companion.HandoffActivityDataMessage.EXTRAS -> {
                    byte[] rawExtras =
                            pis.readBytes(android.companion.HandoffActivityDataMessage.EXTRAS);
                    if (rawExtras != null) {
                        setExtras(
                                PersistableBundle.readFromStream(
                                        new ByteArrayInputStream(rawExtras)));
                    }
                }
                case (int) android.companion.HandoffActivityDataMessage.SIGNATURE_DIGESTS -> {
                    addPackageSignatureDigest(
                            pis.readBytes(
                                    android.companion.HandoffActivityDataMessage
                                            .SIGNATURE_DIGESTS));
                }
            }
        }

        @Override
        public HandoffActivityDataMessage build() {
            HandoffActivityData activityData = null;
            if (componentName != null) {
                activityData =
                        new HandoffActivityData.Builder(componentName)
                                .setFallbackUri(fallbackUri)
                                .setExtras(extras)
                                .build();
            } else if (fallbackUri != null) {
                activityData = HandoffActivityData.createWebHandoff(fallbackUri);
            }

            return new HandoffActivityDataMessage(activityData, packageSignatureDigests);
        }
    }

    @Override
    public void write(@NonNull ProtoOutputStream protoOutputStream) throws IOException {
        Objects.requireNonNull(protoOutputStream);
        if (activity != null) {
            ComponentName componentName = activity.getComponentName();
            if (componentName != null) {
                protoOutputStream.writeString(
                        android.companion.HandoffActivityDataMessage.COMPONENT_NAME,
                        componentName.flattenToString());
            }

            Uri fallbackUri = activity.getFallbackUri();
            if (fallbackUri != null) {
                protoOutputStream.writeString(
                        android.companion.HandoffActivityDataMessage.FALLBACK_URI,
                        fallbackUri.toString());
            }

            PersistableBundle extras = activity.getExtras();
            if (extras != null) {
                ByteArrayOutputStream extrasStream = new ByteArrayOutputStream();
                extras.writeToStream(extrasStream);
                protoOutputStream.write(
                        android.companion.HandoffActivityDataMessage.EXTRAS,
                        extrasStream.toByteArray());
            }
        }

        for (byte[] signatureDigest : packageSignatureDigests()) {
            protoOutputStream.write(
                    android.companion.HandoffActivityDataMessage.SIGNATURE_DIGESTS,
                    signatureDigest);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(activity, packageSignatureDigests);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HandoffActivityDataMessage)) {
            return false;
        }
        HandoffActivityDataMessage other = (HandoffActivityDataMessage) o;
        if (!Objects.equals(activity, other.activity)) {
            return false;
        }

        if (other.packageSignatureDigests.size() != packageSignatureDigests.size()) {
            return false;
        }

        for (int i = 0; i < other.packageSignatureDigests.size(); i++) {
            if (!Arrays.equals(
                    other.packageSignatureDigests.get(i), packageSignatureDigests.get(i))) {
                return false;
            }
        }

        return true;
    }
}
