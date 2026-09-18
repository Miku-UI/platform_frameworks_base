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

package com.android.server.display.config;

import android.view.FrameRateVelocityPoint;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Represents the mapping between frame rate and velocity.
 * This data is used to determine the appropriate frame rate based on the velocity of motion.
 */
public class FrameRateVelocityData {

    private FrameRateVelocityData() {}

    /**
     * Load the frame rate / velocity mapping from DisplayConfiguration
     * and return the value.
     * Sample input from DisplayConfiguration:
     *
     * <frameRateVelocityMapping>
     *   <point>
     *     <first>120.0</first>
     *     <second>300.0</second>
     *   </point>
     *   <point>
     *     <first>80.0</first>
     *     <second>125.0</second>
     *   </point>
     *   <point>
     *     <first>60.0</first>
     *     <second>0.0</second>
     *   </point>
     * </frameRateVelocityMapping>
     *
     * @return A List of FrameRateVelocityPoint
     */
    public static List<FrameRateVelocityPoint> load(DisplayConfiguration config) {
        List<FrameRateVelocityPoint> pointList = new ArrayList<>();

        if (config.getFrameRateVelocityMapping() != null) {
            var points = config.getFrameRateVelocityMapping().getPoint();
            if (points != null && !points.isEmpty()) {
                pointList = new ArrayList<>(points.size());
                for (int i = 0; i < points.size(); i++) {
                    final var point = points.get(i);
                    pointList.add(new FrameRateVelocityPoint(
                            point.getFirst().floatValue(),
                            point.getSecond().floatValue()));
                }
            }
        }

        if (pointList.isEmpty()) {
            String sysProp = android.sysprop.ViewProperties.vrr_velocity_threshold().orElse("");
            if (!sysProp.isEmpty()) {
                pointList = parseFrameRateVelocityMapping(sysProp);
            }
        }

        if (pointList.isEmpty()) {
            pointList = getDefaultFrameRateVelocityMapping();
        }

        Collections.sort(pointList, Comparator.comparingDouble(
                point -> point.getFramePerSecond()));
        verifyFrameRateVelocityData(pointList); // Verify the sorted list
        return pointList;
    }

    @VisibleForTesting
    @SuppressWarnings("MixedMutabilityReturnType")
    static List<FrameRateVelocityPoint> parseFrameRateVelocityMapping(String mappings) {
        if (mappings.isEmpty()) {
            return Collections.emptyList();
        }

        int columnCount = 0;
        int atCount = 0;
        int pairCount = 0;
        int startIndex = 0;
        int endIndex = mappings.length() - 1;

        // Find the first non column character
        while (startIndex <= endIndex && mappings.charAt(startIndex) == ':') {
            startIndex++;
        }
        // Find the last non column character
        while (startIndex <= endIndex && mappings.charAt(endIndex) == ':') {
            endIndex--;
        }
        if (startIndex >= endIndex) {
            return Collections.emptyList();
        }

        // First pass: Count the number of mappings
        for (int i = startIndex; i <= endIndex; i++) {
            if (mappings.charAt(i) == ':') {
                if (((i > 0) && mappings.charAt(i - 1) == ':')) {
                    continue;
                }
                pairCount++;
            }
        }
        pairCount++; // Add 1 for the last mapping

        int[][] mappingArray = new int[pairCount][2];
        int currentIndex = startIndex;
        try {
            for (int i = startIndex; i <= endIndex; i++) {
                if (mappings.charAt(i) == ':') {
                    // handle consecutive columns
                    if (((i > 0) && mappings.charAt(i - 1) == ':')) {
                        currentIndex = i + 1;
                        continue;
                    }
                    // assign velocity threshold value
                    mappingArray[columnCount][1] =
                            Integer.parseInt(mappings.substring(currentIndex, i).trim());
                    columnCount++;
                    if (columnCount != atCount) {
                        throw new IllegalArgumentException();
                    }
                    currentIndex = i + 1;
                } else if (mappings.charAt(i) == '@') {
                    // handle consecutive @
                    if ((i > 0) && mappings.charAt(i - 1) == '@') {
                        currentIndex = i + 1;
                        continue;
                    }
                    // assign frame rate value
                    mappingArray[columnCount][0] =
                            Integer.parseInt(mappings.substring(currentIndex, i).trim());
                    atCount++;
                    if (atCount != columnCount + 1) {
                        throw new IllegalArgumentException();
                    }
                    currentIndex = i + 1;
                }
            }

            if (atCount != columnCount + 1 || atCount != pairCount) {
                throw new IllegalArgumentException();
            }
            // the last velocity threshold value
            mappingArray[columnCount][1] =
                    Integer.parseInt(mappings.substring(currentIndex, endIndex + 1).trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }
        List<FrameRateVelocityPoint> points = new ArrayList<>(pairCount);
        // mappingArray[i][0] is frame rate, mappingArray[i][1] is velocity
        for (int i = 0; i < pairCount; i++) {
            points.add(new FrameRateVelocityPoint(mappingArray[i][0], mappingArray[i][1]));
        }
        points.sort(Comparator.comparingDouble(FrameRateVelocityPoint::getDpPerSecond));
        return points;
    }

    private static List<FrameRateVelocityPoint> getDefaultFrameRateVelocityMapping() {
        return java.util.Arrays.asList(
            new FrameRateVelocityPoint(60f, 0f),
            new FrameRateVelocityPoint(80f, 125f),
            new FrameRateVelocityPoint(120f, 300f));
    }

    private static void verifyFrameRateVelocityData(List<FrameRateVelocityPoint> sortedPoints) {
        if (sortedPoints == null || sortedPoints.size() <= 1) return;
        for (int i = 1; i < sortedPoints.size(); i++) {
            FrameRateVelocityPoint prev = sortedPoints.get(i - 1);
            FrameRateVelocityPoint curr = sortedPoints.get(i);

            if (prev.getFramePerSecond() == curr.getFramePerSecond()
                    || prev.getDpPerSecond() == curr.getDpPerSecond()) {
                throw new IllegalStateException("Found two entries in the frame rate"
                        + " veolicy map with the same frame per second: " + prev + ", " + curr);
            } else if (prev.getFramePerSecond() > curr.getFramePerSecond()
                    || prev.getDpPerSecond() >= curr.getDpPerSecond()) {
                throw new IllegalStateException("Found two entries in the frame rate veolicy map"
                        + " with increasing frame per second but decreasing dp per second: "
                        + prev + ", " + curr);
            }
        }
    }
}
