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
package com.android.server.timezonedetector.ftzd;

import static com.android.server.timezonedetector.FusedTimeZoneDetector.Quality;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_LOCATION;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_TELEPHONY;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.Origin;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents the fusion of time zone signals from multiple origins, such as telephony and location,
 * which can be used to determine a time zone.
 *
 * <p>This class encapsulates the state of the fused signals, including:
 *
 * <ul>
 *   <li>The primary time zone ID (e.g., "America/Los_Angeles") derived from the signals.
 *   <li>A set of all candidate time zone IDs considered from all origins.
 *   <li>A map of origins (e.g., {@link
 *       com.android.server.timezonedetector.TimeZoneDetectorStrategy#ORIGIN_TELEPHONY}) that have
 *       contributed signals, along with quality and timestamp information for each.
 * </ul>
 *
 * <p>This class is not thread-safe.
 *
 * @hide
 */
public final class FusedSignals {

    /** The primary time zone ID. */
    private final String mZoneId;

    /**
     * A set of all candidate time zone IDs considered by all origins.
     *
     * <p>This field is not used for the moment. It may be used in the future for disagreement
     * resolution, so we keep it for now.
     */
    private final Set<String> mZoneIdCandidates;

    /**
     * A map of origins (e.g., {@link
     * com.android.server.timezonedetector.TimeZoneDetectorStrategy#ORIGIN_TELEPHONY}) that have
     * contributed to this time zone, along with quality and timestamp information for each.
     */
    private final Map<@Origin Integer, OriginInfo> mOrigins;

    /**
     * Creates a copy of another {@link FusedSignals}.
     *
     * @param other the {@link FusedSignals} to copy
     */
    public static FusedSignals copy(FusedSignals other) {
        return new FusedSignals(other.mZoneId, other.mZoneIdCandidates, other.mOrigins);
    }

    /**
     * Creates a copy of another {@link FusedSignals} without the origin information.
     *
     * @param other the {@link FusedSignals} to copy
     */
    public static FusedSignals copyWithoutOrigins(FusedSignals other) {
        return new FusedSignals(other.mZoneId, other.mZoneIdCandidates, new ArrayMap<>());
    }

    /**
     * Creates a new {@link FusedSignals} with a single time zone ID and an optional origin.
     *
     * @param zoneId the primary time zone ID
     * @param origin the initial origin, or {@code null} if none
     */
    public FusedSignals(@NonNull String zoneId, @Nullable @Origin Integer origin) {
        this(List.of(zoneId), origin);
    }

    /**
     * Creates a new {@link FusedSignals} with a list of candidate time zone IDs and an optional
     * origin. The first ID in the list is treated as the primary time zone.
     *
     * @param zoneIdCandidates a list of candidate time zone IDs
     * @param origin the initial origin, or {@code null} if none
     */
    public FusedSignals(@NonNull List<String> zoneIdCandidates, @Nullable @Origin Integer origin) {
        if (zoneIdCandidates.isEmpty()) {
            throw new IllegalArgumentException("Zone ID candidates must not be empty");
        }

        mZoneId = zoneIdCandidates.getFirst();
        mZoneIdCandidates = new ArraySet<>(zoneIdCandidates);
        mOrigins = new ArrayMap<>();

        if (origin != null) {
            mOrigins.put(origin, new OriginInfo());
        }
    }

    private FusedSignals(
            @Nullable String zoneId,
            Set<String> zoneIdCandidates,
            Map<@Origin Integer, OriginInfo> origins) {
        mZoneId = zoneId;
        mZoneIdCandidates = new ArraySet<>(zoneIdCandidates);
        mOrigins =
                origins.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey, x -> new OriginInfo(x.getValue())));
    }

    /**
     * Updates the time zone with new candidate IDs from a specific origin. If the origin is new, it
     * is added. Otherwise, its last-updated timestamp is refreshed.
     *
     * @param origin the origin providing the update
     * @param zoneIdCandidates the list of candidate time zone IDs from this origin
     */
    public FusedSignals update(@Origin int origin, List<String> zoneIdCandidates) {
        mZoneIdCandidates.addAll(zoneIdCandidates);

        if (mOrigins.containsKey(origin)) {
            mOrigins.get(origin).updateLastUpdated();
        } else {
            mOrigins.put(origin, new OriginInfo());
        }

        return this;
    }

    /**
     * Adds an origin with a specific timestamp.
     *
     * <p>If the origin already exists, this operation is a no-op.
     *
     * @param origin the origin providing the update
     * @param zoneIdCandidates the list of candidate time zone IDs from this origin
     * @param timestamp the explicit timestamp to use for the origin info
     */
    public FusedSignals addOrigin(
            @Origin int origin, List<String> zoneIdCandidates, long timestamp) {
        if (mOrigins.containsKey(origin)) {
            return this;
        }

        mZoneIdCandidates.addAll(zoneIdCandidates);
        mOrigins.put(origin, new OriginInfo(/* timestampDetected= */ timestamp));
        return this;
    }

    /**
     * Updates the detected timestamp for a given origin.
     *
     * @param origin the origin to update
     * @param timestamp the new detected timestamp
     */
    public FusedSignals setOriginDetectedTimestamp(@Origin int origin, long timestamp) {
        if (mOrigins.containsKey(origin)) {
            mOrigins.get(origin).setDetectedTimestamp(timestamp);
        }
        return this;
    }

    /**
     * Sets a quality score for a given origin.
     *
     * @param origin the origin to update
     * @param quality the quality score
     * @return this {@link FusedSignals} instance for chaining
     */
    public FusedSignals setQualityForOrigin(@Origin int origin, @Quality int quality) {
        if (mOrigins.containsKey(origin)) {
            mOrigins.get(origin).setQuality(quality);
        }
        return this;
    }

    /**
     * Checks if this time zone has no origins with a quality score meeting the specified threshold.
     *
     * @param qualityThreshold the minimum quality score required for an origin to be considered
     * @return {@code true} if no origins meet the quality threshold
     */
    public boolean hasNoOrigins(@Quality int qualityThreshold) {
        return mOrigins.isEmpty()
                || mOrigins.values().stream().noneMatch(x -> x.getQuality() >= qualityThreshold);
    }

    /**
     * Checks if this time zone is supported only by a single, specific origin with a quality score
     * meeting the specified threshold.
     *
     * @param origin the origin to check for
     * @param qualityThreshold the minimum quality score required
     * @return {@code true} if the given origin is the only one meeting the quality threshold
     */
    public boolean hasSingleOrigin(@Origin int origin, @Quality int qualityThreshold) {
        boolean foundOrigin = false;

        for (Map.Entry<@Origin Integer, OriginInfo> entry : mOrigins.entrySet()) {
            if (entry.getValue().getQuality() >= qualityThreshold) {
                if (entry.getKey().equals(origin)) {
                    foundOrigin = true;
                } else {
                    // Only one origin is allowed to meet the quality threshold.
                    return false;
                }
            }
        }

        return foundOrigin;
    }

    /**
     * Checks if a specific origin supports this time zone with a quality score meeting the
     * specified threshold.
     *
     * @param origin the origin to check for
     * @param qualityThreshold the minimum quality score required
     * @return {@code true} if the origin exists and meets the quality threshold
     */
    public boolean hasOrigin(@Origin int origin, @Quality int qualityThreshold) {
        return mOrigins.containsKey(origin)
                && mOrigins.get(origin).getQuality() >= qualityThreshold;
    }

    /**
     * Removes all origin information from this time zone.
     *
     * @return this {@link FusedSignals} instance for chaining
     */
    public FusedSignals clearOrigins() {
        mOrigins.clear();
        return this;
    }

    /**
     * Removes a specific origin from this time zone.
     *
     * @param origin the origin to remove
     * @return this {@link FusedSignals} instance for chaining
     */
    public FusedSignals removeOrigin(@Origin int origin) {
        mOrigins.remove(origin);
        return this;
    }

    /** Returns the primary time zone ID. */
    public String getTimeZoneId() {
        return mZoneId;
    }

    /** Returns the set of origins that support this time zone. */
    public Set<@Origin Integer> getOrigins() {
        return Set.copyOf(mOrigins.keySet());
    }

    /** Returns the {@link OriginInfo} for a given origin. */
    public OriginInfo getOriginInfoForOrigin(@Origin int origin) {
        return mOrigins.get(origin);
    }

    /** Returns the set of candidate time zone IDs. */
    @VisibleForTesting
    public Set<String> getZoneIdCandidates() {
        return Set.copyOf(mZoneIdCandidates);
    }

    @Override
    public String toString() {
        return "FusedSignals{"
                + "mZoneId="
                + mZoneId
                + ", mZoneIdCandidates="
                + mZoneIdCandidates
                + ", mOrigins="
                + formatOrigins(mOrigins)
                + '}';
    }

    @GuardedBy("this")
    private static String formatOrigins(Map<@Origin Integer, OriginInfo> origins) {
        return origins.entrySet().stream()
                .map(entry -> originName(entry.getKey()) + ":" + entry.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String originName(@Origin int origin) {
        return switch (origin) {
            case ORIGIN_LOCATION -> "LOCATION";
            case ORIGIN_TELEPHONY -> "TELEPHONY";
            default -> "UNKNOWN";
        };
    }
}
