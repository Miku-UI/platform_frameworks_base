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

package com.android.server.am.psc;

import java.lang.ref.WeakReference;
import java.util.function.BiConsumer;

/**
 * An subclass of {@link BinderSession} on top of a {@link ConnectionRecordInternal} to signify a
 * bound service connection, where the host of the bound service is eligible to get frozen by
 * {@link ProcessStateController}.
 */
public final class BoundServiceSession extends BinderSession<ConnectionRecordInternal> {
    private static final String TRACE_TRACK = "bound_service_calls";

    public BoundServiceSession(BiConsumer<ConnectionRecordInternal, Boolean> processStateUpdater,
                        ConnectionRecordInternal connectionRecord) {
        super(processStateUpdater, new WeakReference<>(connectionRecord),
                connectionRecord.toShortString());
    }

    @Override
    protected String getTraceTrack() {
        return TRACE_TRACK;
    }
}
