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

package android.media.tv.extension.servicedb;

import android.media.tv.extension.servicedb.IServiceListEditListener;
import android.os.Bundle;

/**
 * Interface for editing and retrieving service and service list information.
 * <p>
 * This interface allows clients to open an edit session, retrieve detailed information
 * about Services, Transport Streams, Networks, and Satellites, and commit changes
 * back to the system database (tv.db) and middleware.
 * @hide
 */
interface IServiceListEdit {
    /**
     * Opens the service list in edit mode.
     * <p>
     * <b>Important:</b> You must call #close() after editing is done, otherwise
     * channel-change operations may be stuck.
     *
     * @param listener Listener to be notified when a commit() action completes.
     * @return ServicedbConstants.ResultCode.RESULT_SUCCESS if opened, RESULT_ERROR_LOCKED if
     *         it is already opened by another client, or RESULT_ERROR for general failure.
     */
    int open(IServiceListEditListener listener);
    /**
     * Closes the edit mode session.
     *
     * @return ServicedbConstants.ResultCode.RESULT_SUCCESS or RESULT_ERROR.
     */
    int close();
    /**
     * Commits all changes made to the service information lists to the Channels table
     * of tv.db and the middleware database.
     *
     * @return The commit request ID.
     */
    int commit();
    /**
     * Performs a commit and immediately closes the session in one operation.
     *
     * @return The commit request ID.
     */
    int userEditCommit();

    /*************************************** Service Info *****************************************/
    /**
     * Retrieves a single record's information (Service, TransportStream, Network, or Satellite)
     * from tv.db.
     *
     * @param serviceInfoId The record ID (_ID from tv.db).
     * @param keys The array of metadata keys to retrieve.
     * @return A bundle containing the requested service info bundle, bundle keys defined but not
     *         limited to @ServicedbConstants.ServiceInfoKeys.
     */
    Bundle getServiceInfo(String serviceInfoId, in String[] keys);
    /**
     * Retrieves a list of all service records for a specific service list from tv.db.
     *
     * @param serviceListId The ServiceList ID (derived from channel_list_id in tv.db).
     * @param keys The keys to retrieve.
     * @return A wrapper bundle, where the outer bundle has the key
     *         @ServicedbConstants.KEY_SERVICE_INFO_ID and inner bundle has keys defined but not
     *         limited to @ServicedbConstants.ServiceInfoKeys.
     */
    Bundle getServiceInfoList(String serviceListId, in String[] keys);
    /**
     * Retrieves all Service Info IDs associated with a specific Service List.
     *
     * @param serviceListId The ServiceList ID.
     * @return An array of Service Info IDs (row id from tv.db).
     */
    String[] getServiceInfoIdsFromDatabase(String serviceListId);
    /**
     * Updates a single service record.
     *
     * @param updateServiceInfo The Bundle containing the fields to update, keys defined but not
     *        limited to @ServicedbConstants.ServiceInfoKeys.
     * @return @ServicedbConstants.ResultCode.
     */
    int updateServiceInfo(in Bundle updateServiceInfo);
    /**
     * Updates multiple service records in a batch.
     *
     * @param updateServiceInfoList An array of Bundles containing the fields to update.
     * @return @ServicedbConstants.ResultCode.
     */
    int updateServiceInfoByList(in Bundle[] updateServiceInfoList);
    /**
     * Removes a specific service record from the service list.
     *
     * @param serviceInfoId The record id of the service to remove.
     * @return @ServicedbConstants.ResultCode.
     */
    int removeServiceInfo(String serviceInfoId);
    /**
     * Removes a list of service records from the service list.
     *
     * @param serviceInfoIdList An array of service info IDs to remove.
     * @return @ServicedbConstants.ResultCode.
     */
    int removeServiceInfoByList(in String[] serviceInfoIdList);

    /*************************************** Service List *****************************************/
    /**
     * Creates a new Service List Info record.
     * <p>You must call #commit() to finalize this creation.</p>
     *
     * @param broadcastType The broadcast type, one of the value in @{com.android.tv.extension.scan.
     *                      ScanConstants.BroadcastType}
     * @param serviceListType one of the value in @ServicedbConstants.ServiceListType.
     * @param serviceListPrefix The prefix string for the list, one of the value in
     *                          @ServicedbConstants.ServiceListPrefix.
     * @param countryCode The target country code that follows ISO 3166.
     * @param operatorId The operator ID.
     * @return The new serviceListId.
     */
    String addServiceListInfo(int broadcastType, String serviceListType,
        String serviceListPrefix, String countryCode, int operatorId);
    /**
     * Adds a list of predefined serviceList.
     *
     * @param serviceListId The ID returned from #addServiceListInfo.
     * @param predefinedServiceListBundle A list of bundles, where each bundle contains a
     *                                    predefined service list info.
     * @return @ServicedbConstants.ResultCode.RESULT_SUCCESS or RESULT_ERROR.
     */
    int addPredefinedServiceList(String serviceListId, in Bundle[] predefinedServiceListBundle);

    /************************************ Transport Stream ****************************************/
    /**
     * Retrieves a list of Transport Stream records for a specific service list from tv.db.
     *
     * @param serviceListId The ServiceList ID (channel_list_id in tv.db).
     * @param keys The keys to retrieve.
     * @return A wrapper bundle containing the TS records, the inner bundle keys defined but not
     *         limited to @ServicedbConstants.TransportStreamInfoKey, outer bundle key should be
     *         @ServicedbConstants.KEY_SERVICE_INFO_ID (row id from tv.db).
     */
    Bundle getTransportStreamInfoList(String serviceListId, in String[] keys);
    /**
     * Retrieves a list of Transport Stream records directly from the working database.
     *
     * @param serviceListId The ServiceList ID (channel_list_id in tv.db).
     * @param keys The keys to retrieve.
     * @return A wrapper bundle containing the TS records from the work DB, the inner bundle keys
     *         defined but not limited to @ServicedbConstants.TransportStreamInfoKey, outer bundle
     *         key should be @ServicedbConstants.KEY_SERVICE_INFO_ID (row id from tv.db).
     */
    Bundle getTransportStreamInfoListForce(String serviceListId, in String[] keys);

    /****************************************** Network ******************************************/
    /**
     * Retrieves a list of Network records for a specific service list.
     *
     * @param serviceListId The ServiceList ID (channel_list_id in tv.db).
     * @param keys The keys to retrieve.
     * @return A wrapper Bundle containing the Network records, the inner bundle keys defined but
     *         not imited to @ServicedbConstants.NetworkInfoKey, and outer bundle key should be
     *         @ServicedbConstants.KEY_SERVICE_INFO_ID (row id from tv.db).
     */
    Bundle getNetworkInfoList(String serviceListId, in String[] keys);

    /****************************************** Satellite *****************************************/
    /**
     * Retrieves a list of Satellite records for a specific service list.
     *
     * @param serviceListId The ServiceList ID (channel_list_id in tv.db).
     * @param keys The keys to retrieve.
     * @return A wrapper Bundle containing the Satellite records, the inner bundle keys defined but
     *         not imited to @ServicedbConstants.SatelliteInfoKey, and outer bundle key should be
     *         @ServicedbConstants.KEY_SERVICE_INFO_ID (row id from tv.db).
     */
    Bundle getSatelliteInfoList(String serviceListId, in String[] keys);
    /**
     * Adds a predefined satellite definition.
     * @param serviceListId The ID returned from #addServiceListInfo.
     * @param satInfo A Bundle containing @ServicedbConstants.SatelliteInfoKey.
     */
    int addPredefinedSatelliteInfo(String serviceListId, in Bundle predefinedSatInfoBundle);

    /************************************ Processing **********************************************/
    /**
     * Decompresses a raw record bundle into a readable string format.
     * <p>(Renamed from {@code toRecordInfoByType})</p>
     *
     * @param recordInfoBundle The raw bundle obtained via #getServiceInfo.
     * @param recordType The type of record: @ServicedbConstants.RecordType.
     * @return The decompressed record info string.
     */
    String decompressRecord(in Bundle recordInfoBundle, String recordType);
    /**
     * Synchronizes channel modifications (from tv.db) to the middleware database(SVL/TSL/NWL/SATL).
     *
     * @param serviceListId The ServiceList ID (channel_list_id in tv.db).
     * @param recordIdListBundle recordIds The array of internal provider IDs (from tv.db) to apply.
     * @param optType The operation type: @ServicedbConstants.OptType.
     * @return @ServicedbConstants.ResultCode.RESULT_SUCCESS or RESULT_ERROR.
     */
    int updateRecordIdList(String serviceListId, in String[] recordIds, int optType);

    /***************************************** DVB-I **********************************************/
    /**
     * Retrieves the logo URI for a specific DVB-I service.
     *
     * @param serviceInfoId The unique internal database ID of the service.
     * @return A String containing the URI of the service logo, or null if not found.
     */
    String getServiceLogoUri(int serviceInfoId);
    /**
     * Retrieves the configuration information for a specific installed DVB-I service list.
     *
     * @param channelListId The ID of the channel list to retrieve, maps to
     *                      TvContract.Channels#COLUMN_CHANNEL_LIST_ID
     * @return A bundle containing metadata about the service list, keys defined but not limited to
     *         @ServicedbConstants.DvbiServiceListInfoKey.
     */
    Bundle getInstalledServiceListInfo(String channelListId);
    /**
     * Retrieves configuration information for all installed DVB-I service lists.
     *
     * @return An array of bundle, where each one represents a service list info with
     *         keys defined in @ServicedbConstants.DvbiServiceListInfoKey.
     */
    Bundle[] getAllInstalledServiceListInfo();
}
