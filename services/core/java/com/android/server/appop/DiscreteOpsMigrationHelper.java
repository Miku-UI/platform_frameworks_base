/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.appop;

import android.annotation.NonNull;
import android.util.Slog;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for migrating discrete ops from xml to sqlite or vice versa.
 */
public class DiscreteOpsMigrationHelper {
    private static final String LOG_TAG = "DiscreteOpsMigration";

    /**
     * migrate discrete ops from xml to sqlite.
     */
    static void migrateFromXmlToSqlite(DiscreteOpsXmlRegistry sourceRegistry,
            DiscreteOpsSqlRegistry targetRegistry) {
        try {
            DiscreteOpsXmlRegistry.DiscreteOps xmlOps = sourceRegistry.getAllDiscreteOps();
            List<DiscreteOpsSqlRegistry.DiscreteOp> discreteOps = convertXmlToSqlDiscreteOps(
                    xmlOps);
            targetRegistry.migrateDiscreteAppOpHistory(discreteOps, xmlOps.mChainIdOffset);
            if (!sourceRegistry.deleteDiscreteOpsDir()) {
                Slog.w(LOG_TAG, "Couldn't delete appops xml directories.");
            }
        } catch (Exception ex) {
            Slog.e(LOG_TAG, "migrateFromXmlToSqlite failed.", ex);
            sourceRegistry.deleteDiscreteOpsDir();
        }
    }

    /**
     * migrate discrete ops from xml to unified sqlite schema.
     */
    static void migrateFromXmlToUnifiedSchemaSqlite(DiscreteOpsXmlRegistry sourceRegistry,
            AppOpHistoryHelper targetRegistry) {
        try {
            DiscreteOpsXmlRegistry.DiscreteOps xmlOps = sourceRegistry.getAllDiscreteOps();
            List<DiscreteOpsSqlRegistry.DiscreteOp> discreteOps = convertXmlToSqlDiscreteOps(
                    xmlOps);
            List<AggregatedAppOpAccessEvent> convertedOps =
                    getAggregatedAppOpAccessEvents(discreteOps);
            targetRegistry.migrateDiscreteAppOpHistory(convertedOps);
            if (!sourceRegistry.deleteDiscreteOpsDir()) {
                Slog.w(LOG_TAG, "Couldn't delete appops xml directories.");
            }
        } catch (Exception ex) {
            Slog.e(LOG_TAG, "migrateFromXmlToUnifiedSchemaSqlite failed.", ex);
            sourceRegistry.deleteDiscreteOpsDir();
        }
    }

    /**
     * migrate discrete ops from sqlite to unified-schema sqlite.
     */
    static void migrateFromSqliteToUnifiedSchemaSqlite(DiscreteOpsSqlRegistry sourceRegistry,
            AppOpHistoryHelper targetRegistry) {
        try {
            List<DiscreteOpsSqlRegistry.DiscreteOp> sourceOps = sourceRegistry.getAllDiscreteOps();
            List<AggregatedAppOpAccessEvent> convertedOps =
                    getAggregatedAppOpAccessEvents(sourceOps);
            targetRegistry.migrateDiscreteAppOpHistory(convertedOps);
            if (!sourceRegistry.deleteDatabase()) {
                Slog.w(LOG_TAG, "Couldn't delete appops sql database.");
            }
        } catch (Exception ex) {
            Slog.e(LOG_TAG, "migrateFromSqliteToUnifiedSchemaSqlite failed.", ex);
            sourceRegistry.deleteDatabase();
        }
    }

    /**
     * rollback discrete ops from unified schema sqlite to sqlite schema.
     */
    static void rollbackFromUnifiedSchemaSqliteToSqlite(AppOpHistoryHelper sourceRegistry,
            DiscreteOpsSqlRegistry targetRegistry) {
        try {
            List<AggregatedAppOpAccessEvent> unifiedSchemaSqliteOps =
                    sourceRegistry.getAppOpHistory();
            List<DiscreteOpsSqlRegistry.DiscreteOp> discreteOps = new ArrayList<>();
            long largestChainId = 0;
            for (AggregatedAppOpAccessEvent event : unifiedSchemaSqliteOps) {
                discreteOps.add(new DiscreteOpsSqlRegistry.DiscreteOp(event.uid(),
                        event.packageName(), event.attributionTag(),
                        event.deviceId(), event.opCode(), event.opFlags(),
                        event.attributionFlags(),
                        event.uidState(), event.attributionChainId(),
                        event.accessTimeMillis(),
                        event.durationMillis()));
                largestChainId = Math.max(largestChainId, event.attributionChainId());
            }
            targetRegistry.migrateDiscreteAppOpHistory(discreteOps, largestChainId);
            if (!sourceRegistry.deleteDatabase()) {
                Slog.w(LOG_TAG, "Couldn't delete appops unified sql database.");
            }
        } catch (Exception ex) {
            Slog.e(LOG_TAG, "rollbackFromUnifiedSchemaSqliteToSqlite failed.", ex);
            sourceRegistry.deleteDatabase();
        }
    }

    /**
     * rollback discrete ops from unified schema sqlite to xml schema.
     */
    static void rollbackFromUnifiedSchemaSqliteToXml(AppOpHistoryHelper sourceRegistry,
            DiscreteOpsXmlRegistry targetRegistry) {
        try {
            List<AggregatedAppOpAccessEvent> unifiedSchemaSqliteOps =
                    sourceRegistry.getAppOpHistory();
            List<DiscreteOpsSqlRegistry.DiscreteOp> discreteOps = new ArrayList<>();
            for (AggregatedAppOpAccessEvent event : unifiedSchemaSqliteOps) {
                discreteOps.add(new DiscreteOpsSqlRegistry.DiscreteOp(event.uid(),
                        event.packageName(), event.attributionTag(),
                        event.deviceId(), event.opCode(), event.opFlags(),
                        event.attributionFlags(),
                        event.uidState(), event.attributionChainId(),
                        event.accessTimeMillis(),
                        event.durationMillis()));
            }
            DiscreteOpsXmlRegistry.DiscreteOps xmlOps = getXmlDiscreteOps(discreteOps);
            targetRegistry.migrateDiscreteAppOpHistory(xmlOps);
            if (!sourceRegistry.deleteDatabase()) {
                Slog.w(LOG_TAG, "Couldn't delete appops unified sql database.");
            }
        } catch (Exception ex) {
            Slog.e(LOG_TAG, "rollbackFromUnifiedSchemaSqliteToXml failed.", ex);
            sourceRegistry.deleteDatabase();
        }
    }

    /**
     * rollback discrete ops from sqlite to xml.
     */
    static void rollbackFromSqliteToXml(DiscreteOpsSqlRegistry sourceRegistry,
            DiscreteOpsXmlRegistry targetRegistry) {
        try {
            List<DiscreteOpsSqlRegistry.DiscreteOp> sqlOps = sourceRegistry.getAllDiscreteOps();
            DiscreteOpsXmlRegistry.DiscreteOps xmlOps = getXmlDiscreteOps(sqlOps);
            targetRegistry.migrateDiscreteAppOpHistory(xmlOps);
            if (!sourceRegistry.deleteDatabase()) {
                Slog.w(LOG_TAG, "Couldn't delete appops sql database.");
            }
        } catch (Exception ex) {
            Slog.e(LOG_TAG, "rollbackFromSqliteToXml failed.", ex);
            sourceRegistry.deleteDatabase();
        }
    }

    /**
     * Convert sqlite flat rows to hierarchical data.
     */
    @NonNull
    private static DiscreteOpsXmlRegistry.DiscreteOps getXmlDiscreteOps(
            List<DiscreteOpsSqlRegistry.DiscreteOp> discreteOps) {
        DiscreteOpsXmlRegistry.DiscreteOps xmlOps =
                new DiscreteOpsXmlRegistry.DiscreteOps(0);
        if (discreteOps.isEmpty()) {
            return xmlOps;
        }

        for (DiscreteOpsSqlRegistry.DiscreteOp discreteOp : discreteOps) {
            xmlOps.addDiscreteAccess(discreteOp.getOpCode(), discreteOp.getUid(),
                    discreteOp.getPackageName(), discreteOp.getDeviceId(),
                    discreteOp.getAttributionTag(), discreteOp.getOpFlags(),
                    discreteOp.getUidState(),
                    discreteOp.getAccessTime(), discreteOp.getDuration(),
                    discreteOp.getAttributionFlags(), (int) discreteOp.getChainId());
        }
        return xmlOps;
    }

    /**
     * Convert xml (hierarchical) data to flat row based data.
     */
    @NonNull
    private static List<DiscreteOpsSqlRegistry.DiscreteOp> convertXmlToSqlDiscreteOps(
            DiscreteOpsXmlRegistry.DiscreteOps discreteOps) {
        List<DiscreteOpsSqlRegistry.DiscreteOp> opEvents = new ArrayList<>();

        if (discreteOps.isEmpty()) {
            return opEvents;
        }

        discreteOps.mUids.forEach((uid, discreteUidOps) -> {
            discreteUidOps.mPackages.forEach((packageName, packageOps) -> {
                packageOps.mPackageOps.forEach((opcode, ops) -> {
                    ops.mDeviceAttributedOps.forEach((deviceId, deviceOps) -> {
                        deviceOps.mAttributedOps.forEach((tag, attributedOps) -> {
                            for (DiscreteOpsXmlRegistry.DiscreteOpEvent attributedOp :
                                    attributedOps) {
                                DiscreteOpsSqlRegistry.DiscreteOp
                                        opModel = new DiscreteOpsSqlRegistry.DiscreteOp(uid,
                                        packageName, tag,
                                        deviceId, opcode, attributedOp.mOpFlag,
                                        attributedOp.mAttributionFlags,
                                        attributedOp.mUidState, attributedOp.mAttributionChainId,
                                        attributedOp.mNoteTime,
                                        attributedOp.mNoteDuration);
                                opEvents.add(opModel);
                            }
                        });
                    });
                });
            });
        });

        return opEvents;
    }

    @NonNull
    private static List<AggregatedAppOpAccessEvent> getAggregatedAppOpAccessEvents(
            List<DiscreteOpsSqlRegistry.DiscreteOp> discreteOps) {
        List<AggregatedAppOpAccessEvent> convertedOps = new ArrayList<>();
        for (DiscreteOpsSqlRegistry.DiscreteOp event : discreteOps) {
            convertedOps.add(
                    new AggregatedAppOpAccessEvent(event.getUid(), event.getPackageName(),
                            event.getOpCode(), event.getDeviceId(), event.getAttributionTag(),
                            event.getOpFlags(), event.getUidState(),
                            event.getAttributionFlags(), event.getChainId(),
                            event.getAccessTime(),
                            event.getDuration(), 0, 1, 0));
        }
        return convertedOps;
    }

}
