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

import android.os.PowerManager;
import android.util.Slog;
import android.util.SparseArray;
import android.view.SurfaceControl;
import android.view.SurfaceControl.WorkDuration;

import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.feature.flags.Flags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thermal throttling configuration for display
 * <pre>
 * {@code
 *  <displayConfiguration>
 *      ...
 *      <thermalThrottling>
 *          <!-- Example of brightnessThrottlingMap -->
 *              <brightnessThrottlingMap id="default">
 *              <brightnessThrottlingPoint>
 *                  <thermalStatus>severe</thermalStatus>
 *                  <brightness>0.4</brightness>
 *              </brightnessThrottlingPoint>
 *              <brightnessThrottlingPoint>
 *                  <thermalStatus>critical</thermalStatus>
 *                  <brightness>0.3</brightness>
 *              </brightnessThrottlingPoint>
 *              <brightnessThrottlingPoint>
 *                  <thermalStatus>emergency</thermalStatus>
 *                  <brightness>0.2</brightness>
 *              </brightnessThrottlingPoint>
 *              <brightnessThrottlingPoint>
 *                  <thermalStatus>shutdown</thermalStatus>
 *                  <brightness>0.1</brightness>
 *              </brightnessThrottlingPoint>
 *          </brightnessThrottlingMap>
 *
 *          <!-- Example of refreshRateThrottlingMap -->
 *          <refreshRateThrottlingMap id="default">
 *              <refreshRateThrottlingPoint>
 *                  <thermalStatus>severe</thermalStatus>
 *                  <refreshRateRange>
 *                      <minimum>30</minimum>
 *                      <maximum>60</maximum>
 *                  </refreshRateRange>
 *              </refreshRateThrottlingPoint>
 *              <refreshRateThrottlingPoint>
 *                  <thermalStatus>critical</thermalStatus>
 *                  <refreshRateRange>
 *                      <minimum>30</minimum>
 *                      <maximum>30</maximum>
 *                  </refreshRateRange>
 *              </refreshRateThrottlingPoint>
 *          </refreshRateThrottlingMap>
 *
 *          <!-- Example of thermalThrottlingWorkDurations -->
 *          <workDurationsThrottlingMap id="default">
 *              <workDurationsThrottlingPair>
 *                  <thermalStatus>severe</thermalStatus>
 *                  <workDurations>
 *                      <lateWorkDuration>20000000</lateWorkDuration>
 *                      <earlyWorkDuration>21000000</earlyWorkDuration>
 *                      <appWorkDuration>21000000</appWorkDuration>
 *                  </workDurations>
 *              </workDurationsThrottlingPair>
 *              <workDurationsThrottlingPair>
 *                  <thermalStatus>critical</thermalStatus>
 *                  <workDurations>
 *                      <lateWorkDuration>25000000</lateWorkDuration>
 *                      <earlyWorkDuration>26000000</earlyWorkDuration>
 *                      <appWorkDuration>26000000</appWorkDuration>
 *                  </workDurations>
 *              </workDurationsThrottlingPair>
 *          </workDurationsThrottlingMap>
 *      </thermalThrottling>
 *  </displayConfiguration>
 * }
 * </pre>
 */
public class ThermalThrottlingData {
    private static final String TAG = "DisplayDeviceConfig";

    private final Map<String, DisplayDeviceConfig.ThermalBrightnessThrottlingData>
            mThermalBrightnessThrottlingDataMapByThrottlingId = new HashMap<>();

    private final Map<String, SparseArray<SurfaceControl.RefreshRateRange>>
            mRefreshRateThrottlingMap = new HashMap<>();

    private final Map<String, SparseArray<WorkDuration>>
            mThermalThrottlingWorkDurations = new HashMap<>();

    /**
     * Loads the brightness throttling map, refresh rate throttling map and thermal throttling work
     * durations from the given display configuration.
     *
     * @param config The {@link DisplayConfiguration} to load the thermal throttling configuration
     * from.
     */
    public void loadThermalThrottlingConfig(DisplayConfiguration config) {
        final ThermalThrottling throttlingConfig = config.getThermalThrottling();
        if (throttlingConfig == null) {
            Slog.i(TAG, "No thermal throttling config found");
            return;
        }
        loadThermalBrightnessThrottlingMaps(throttlingConfig);
        loadThermalRefreshRateThrottlingMap(throttlingConfig);
        loadThermalThrottlingWorkDurationsMap(throttlingConfig);
    }

    private void loadThermalBrightnessThrottlingMaps(ThermalThrottling throttlingConfig) {
        final List<BrightnessThrottlingMap> maps = throttlingConfig.getBrightnessThrottlingMap();
        if (maps == null || maps.isEmpty()) {
            Slog.i(TAG, "No brightness throttling map found");
            return;
        }

        for (BrightnessThrottlingMap map : maps) {
            final List<BrightnessThrottlingPoint> points = map.getBrightnessThrottlingPoint();
            // At least 1 point is guaranteed by the display device config schema
            List<DisplayDeviceConfig.ThermalBrightnessThrottlingData.ThrottlingLevel>
                    throttlingLevels = new ArrayList<>(points.size());

            boolean badConfig = false;
            for (BrightnessThrottlingPoint point : points) {
                @PowerManager.ThermalStatus int status = DisplayDeviceConfigUtils
                        .convertValidThermalStatus(point.getThermalStatus());
                if (status == PowerManager.THERMAL_STATUS_INVALID) {
                    badConfig = true;
                    break;
                }

                throttlingLevels.add(new DisplayDeviceConfig.ThermalBrightnessThrottlingData
                        .ThrottlingLevel(status, point.getBrightness().floatValue()));
            }

            if (!badConfig) {
                String id = map.getId() == null ? DisplayDeviceConfig.DEFAULT_ID
                        : map.getId();
                if (mThermalBrightnessThrottlingDataMapByThrottlingId.containsKey(id)) {
                    throw new RuntimeException("Brightness throttling data with ID " + id
                            + " already exists");
                }
                mThermalBrightnessThrottlingDataMapByThrottlingId.put(id,
                        DisplayDeviceConfig.ThermalBrightnessThrottlingData
                                .create(throttlingLevels));
            }
        }
    }

    public Map<String, DisplayDeviceConfig.ThermalBrightnessThrottlingData>
            getThermalBrightnessThrottlingDataMapByThrottlingId() {
        return mThermalBrightnessThrottlingDataMapByThrottlingId;
    }

    private void loadThermalRefreshRateThrottlingMap(ThermalThrottling throttlingConfig) {
        List<RefreshRateThrottlingMap> maps = throttlingConfig.getRefreshRateThrottlingMap();
        if (maps == null || maps.isEmpty()) {
            Slog.w(TAG, "RefreshRateThrottling: map not found");
            return;
        }

        for (RefreshRateThrottlingMap map : maps) {
            List<RefreshRateThrottlingPoint> points = map.getRefreshRateThrottlingPoint();
            String id = map.getId() == null ? DisplayDeviceConfig.DEFAULT_ID : map.getId();

            if (points == null || points.isEmpty()) {
                // Expected at lease 1 throttling point for each map
                Slog.w(TAG, "RefreshRateThrottling: points not found for mapId=" + id);
                continue;
            }
            if (mRefreshRateThrottlingMap.containsKey(id)) {
                Slog.wtf(TAG, "RefreshRateThrottling: map already exists, mapId=" + id);
                continue;
            }

            SparseArray<SurfaceControl.RefreshRateRange> refreshRates = new SparseArray<>();
            for (RefreshRateThrottlingPoint point : points) {
                @PowerManager.ThermalStatus int status = DisplayDeviceConfigUtils
                        .convertValidThermalStatus(point.getThermalStatus());
                if (status == PowerManager.THERMAL_STATUS_INVALID) {
                    Slog.wtf(TAG,
                            "RefreshRateThrottling: Invalid thermalStatus="
                                    + point.getThermalStatus().getRawName() + ",mapId=" + id);
                    continue;
                }
                if (refreshRates.contains(status)) {
                    Slog.wtf(TAG, "RefreshRateThrottling: thermalStatus="
                            + point.getThermalStatus().getRawName()
                            + " is already in the map, mapId=" + id);
                    continue;
                }

                refreshRates.put(status, new SurfaceControl.RefreshRateRange(
                        point.getRefreshRateRange().getMinimum().floatValue(),
                        point.getRefreshRateRange().getMaximum().floatValue()
                ));
            }
            if (refreshRates.size() == 0) {
                Slog.w(TAG, "RefreshRateThrottling: no valid throttling points found for map, "
                        + "mapId=" + id);
                continue;
            }
            mRefreshRateThrottlingMap.put(id, refreshRates);
        }
    }

    public Map<String, SparseArray<SurfaceControl.RefreshRateRange>> getRefreshRateThrottlingMap() {
        return mRefreshRateThrottlingMap;
    }

    private void loadThermalThrottlingWorkDurationsMap(ThermalThrottling throttlingConfig) {
        if (!Flags.enableWorkDurations()) return;

        List<WorkDurationsThrottlingMap> maps = throttlingConfig.getWorkDurationsThrottlingMap();
        if (maps == null || maps.isEmpty()) {
            Slog.w(TAG, "ThermalThrottlingWorkDurations: map not found");
            return;
        }

        for (WorkDurationsThrottlingMap map : maps) {
            List<WorkDurationsThrottlingPair> pairs = map.getWorkDurationsThrottlingPair();
            String id = map.getId() == null ? DisplayDeviceConfig.DEFAULT_ID : map.getId();

            if (pairs == null || pairs.isEmpty()) {
                Slog.w(TAG, "ThermalThrottlingWorkDurations: work durations not found for"
                        + " mapId=" + id);
                continue;
            }
            if (mThermalThrottlingWorkDurations.containsKey(id)) {
                Slog.wtf(TAG, "ThermalThrottlingWorkDurations: map already exists, mapId=" + id);
                continue;
            }

            SparseArray<WorkDuration> thermalThrottlingWorkDurations = new SparseArray<>();
            for (WorkDurationsThrottlingPair pair : pairs) {
                @PowerManager.ThermalStatus int status = DisplayDeviceConfigUtils
                        .convertValidThermalStatus(pair.getThermalStatus());
                if (status == PowerManager.THERMAL_STATUS_INVALID) {
                    Slog.wtf(TAG,
                            "ThermalThrottlingWorkDurations: Invalid thermalStatus="
                                    + pair.getThermalStatus().getRawName() + ",mapId=" + id);
                    continue;
                }
                if (thermalThrottlingWorkDurations.contains(status)) {
                    Slog.wtf(TAG, "ThermalThrottlingWorkDurations: thermalStatus="
                            + pair.getThermalStatus().getRawName()
                            + " is already in the map, mapId=" + id);
                    continue;
                }

                thermalThrottlingWorkDurations.put(status,
                        WorkDurationsConfigLoader.loadThermalThrottlingWorkDurations(pair));
            }
            if (thermalThrottlingWorkDurations.size() == 0) {
                Slog.w(TAG, "ThermalThrottlingWorkDurations: no valid throttling pairs"
                        + "found for map, mapId=" + id);
                continue;
            }
            mThermalThrottlingWorkDurations.put(id, thermalThrottlingWorkDurations);
        }

    }

    public Map<String, SparseArray<WorkDuration>> getThermalThrottlingWorkDurations() {
        return mThermalThrottlingWorkDurations;
    }

    @Override
    public String toString() {
        return "ThermalThrottlingData{"
                + "mThermalBrightnessThrottlingDataMapByThrottlingId="
                + mThermalBrightnessThrottlingDataMapByThrottlingId
                + ", mRefreshRateThrottlingMap=" + mRefreshRateThrottlingMap
                + ", mThermalThrottlingWorkDurations=" + mThermalThrottlingWorkDurations
                + "}";
    }

}
