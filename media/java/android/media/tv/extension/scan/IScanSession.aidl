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

package android.media.tv.extension.scan;

import android.os.Bundle;

/**
 * @hide
 */
interface IScanSession {
    /**
     * Start a service scan.
     *
     * @param broadcastType @ScanConstants.BroadcastType broadcast type, such as ATSC.
     * @param countryCode countryCode based on ISO 3166-1 alpha-3.
     * @param operator values based on @TvExtensionContract.TunerOperators.OperatorType.
     * @param frequency the list of the frequency of the scan.
     * @param scanType @ScanConstants.ScanType type of scan, such as MANUAL.
     * @param languageCode language code based on ISO 639-2/T.
     * @param optionalScanParams @ScanConstants.ScanSessionBundleKey other optional scan settings.
     * @return OpResult.RESULT_SUCCESS if successfully starts else OpResult.RESULT_FAILED.
     */
    int startScan(int broadcastType, String countryCode, String operator, in int[] frequency,
        String scanType, String languageCode, in Bundle optionalScanParams);
    /**
     * Reset the scan information held in TIS.
     *
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully resets else RESULT_FAILED.
     */
    int resetScan();
    /**
     * Cancel scan.
     *
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully cancels else RESULT_FAILED.
     */
    int cancelScan();
    /**
     * Get available interface for created ScanExtension interface.
     *
     * @return list of available extension interface names.
     */
    String[] getAvailableExtensionInterfaceNames();
    /**
     * Get extension interface for Scan.
     *
     * @param name same as TvInputServoceExtensionManager.StandardizedExtensionName names.
     * @return IBinder of the selected extension interface.
     */
    IBinder getExtensionInterface(String name);
    /**
     * Clear the results of the service scan from the service database.
     *
     * @param optionalClearParams optional clear parameters. If the bundle is not null,
     *        it should contain keys as defined in @ScanConstants.ClearServiceListBundleKey.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully clears else RESULT_FAILED.
     */
    int clearServiceList(in Bundle optionalClearParams);
    /**
     * Store the results of the service scan from the service database.
     *
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully stores else RESULT_FAILED.
     */
    int storeServiceList();
    /**
     * Get a service information specified by the service information ID.
     *
     * @param serviceInfoId id obtained from getServiceInfoIdList().
     * @param keys a list of keys to be included in the returned Bundle, keys are included but not
     *             limited to the keys defined in @ScanConstants.ServiceInfoBundleKey.
     * @return Bundle containing requested service information, bundle keys should cover at least
     *         the keys defined in @ScanConstants.ServiceInfoBundleKey.
     */
    Bundle getServiceInfo(String serviceInfoId, in String[] keys);
    /**
     * Get a service information ID list.
     *
     * @return requested service info id list.
     */
    String[] getServiceInfoIdList();
    /**
     * Get a list of service info by the filter.
     *
     * @param filterInfo Bundle must contain a list of Bundle, where each inner Bundle represents
     *        a single channel found by the filter.
     * @param keys a list of keys to be included in the returned Bundle, keys are included but not
     *             limited to the keys defined in @ScanConstants.ServiceInfoBundleKey.
     * @return a list of bundle selected by filterInfo, where each resulting bundle contains service
     *         info, keys as defined in @ScanConstants.ServiceInfoBundleKey.
     */
    Bundle[] getServiceInfoList(in Bundle filterInfo, in String[] keys);
    /**
     * Update the service information.
     *
     * @param serviceInfo Bundle containing requested service information, bundle keys should cover
     *                    at least the keys defined in @ScanConstants.ServiceInfoBundleKey.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully updates else RESULT_FAILED.
     */
    int updateServiceInfo(in Bundle serviceInfo);
    /**
     * Updates the service information for the specified service information ID in array list.
     *
     * @param serviceInfo List of bundles containing requested service information, each bundle keys
     *                    should cover the keys defined in @ScanConstants.ServiceInfoBundleKey.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully updates else RESULT_FAILED.
     */
    int updateServiceInfoByList(in Bundle[] serviceInfo);
    /**
     * Get unique session token for the scan.
     *
     * @return session token.
     */
    String getSessionToken();
    /**
     * Release scan resource, the register listener will be released.
     *
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully releases else RESULT_FAILED.
     */
    int release();
    /************************************ DVBI specific functions ********************************/
    /**
     * Get all of the serviceLists, parsed from Local TV storage, Broadcast, USB file discovery.
     *
     * @return a list of Bundle, where each bundle contains serviceList info for DVBI,
     *         bundle keys as defined in @ScanConstants.DvbiServiceListBundleKey.
     */
    Bundle[] getServiceLists();
    /**
     * Users choose one serviceList from the serviceLists, and install the services.
     *
     * @param serviceListRecId KEY_REC_ID from the ServiceList bundle.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully sets else RESULT_FAILED.
     */
    int setServiceList(int serviceListRecId);
    /**
     * Get all of the packageData, parsed from the selected serviceList XML.
     *
     * @return package data bundle, bundle keys as defined in @ScanConstants.PackageDataBundleKey.
     */
    Bundle getPackageData();
    /**
     * Choose the package using package id and install the corresponding services.
     *
     * @param packageId KEY_PACKAGE_ID from packageData bundle.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully sets else RESULT_FAILED.
     */
    int setPackage(String packageId);
    /**
     * Get all of the countryRegionData, parsed from the selected serviceList XML.
     *
     * @return country region data bundle, bundle keys as defined in
     *         @ScanConstants.CountryRegionDataBundleKey.
     */
    Bundle getCountryRegionData();
    /**
     * Choose the countryRegion using countryRegion id, and install the corresponding services.
     *
     * @param countryRegionId COUNTRY_REGION_ID from CountryRegionData bundle.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully sets else RESULT_FAILED.
     */
    int setCountryRegion(String countryRegionId);
    /**
     * Get all of the regionData, parsed from the selected serviceList XML.
     *
     * @return region data bundle, bundle keys as defined in @ScanConstants.RegionDataBundleKey.
     */
    Bundle getRegionData();
    /**
     * Choose the region using the regionData id, and install the corresponding services.
     *
     * @param regionId KEY_REGION_ID from regionData bundle.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully sets else RESULT_FAILED.
     */
    int setRegion(String regionId);
}
