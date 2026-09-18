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

package com.android.wm.shell.apptoweb.data

import android.content.Context
import android.util.Slog
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.android.framework.protobuf.InvalidProtocolBufferException
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** Updates data in App-to-Web datastore. */
class AppToWebDatastoreRepository constructor(private val dataStore: DataStore<AppToWebProto>) {
    constructor(
        context: Context,
        @ShellBackgroundThread bgCoroutineScope: CoroutineScope,
    ) : this(
        DataStoreFactory.create(
            serializer = AppToWebProtoSerializer,
            produceFile = { context.dataStoreFile(APP_TO_WEB_DATASTORE_FILEPATH) },
            corruptionHandler =
                ReplaceFileCorruptionHandler(
                    produceNewData = { AppToWebProto.getDefaultInstance() }
                ),
            scope = bgCoroutineScope,
        )
    )

    /** Provides dataStore.data flow and handles exceptions thrown during collection */
    val dataStoreFlow: Flow<AppToWebProto> =
        dataStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Slog.e(
                    TAG,
                    "Error in reading App-to-Web education related data from datastore," +
                        "data is stored in a file named" +
                        "$APP_TO_WEB_DATASTORE_FILEPATH",
                    exception,
                )
            } else {
                throw exception
            }
        }

    /**
     * Reads and returns the [AppToWebProto] Proto object from the DataStore. If the DataStore is
     * empty or there's an error reading, it returns the default value of Proto.
     */
    suspend fun getAppToWebProto(): AppToWebProto = dataStoreFlow.first()

    /** Update [AppToWebUserRepository.firstRunPromptAckedPackages] field. */
    suspend fun updateFirstRunPromptAckedPackages(
        firstRunPromptAckedPackagesByUserId: MutableMap<Int, MutableSet<String>>
    ) {
        dataStore.updateData { proto: AppToWebProto ->
            val builder = proto.toBuilder()
            firstRunPromptAckedPackagesByUserId.forEach { (userId, packages) ->
                val currentUserDataBuilder =
                    builder
                        .getAppToWebRepoByUserOrDefault(
                            userId,
                            AppToWebUserRepository.newBuilder().build(),
                        )
                        .toBuilder()
                currentUserDataBuilder
                    .clearFirstRunPromptAckedPackages()
                    .addAllFirstRunPromptAckedPackages(packages.toList())
                builder.putAppToWebRepoByUser(userId, currentUserDataBuilder.build())
            }
            builder.build()
        }
    }

    companion object {
        private const val TAG = "AppToWebDatastoreRepository"
        private const val APP_TO_WEB_DATASTORE_FILEPATH = "APP_TO_WEB.pb"

        object AppToWebProtoSerializer : Serializer<AppToWebProto> {

            override val defaultValue: AppToWebProto = AppToWebProto.getDefaultInstance()

            override suspend fun readFrom(input: InputStream): AppToWebProto =
                try {
                    AppToWebProto.parseFrom(input)
                } catch (exception: InvalidProtocolBufferException) {
                    throw CorruptionException("Cannot read proto.", exception)
                }

            override suspend fun writeTo(appToWebProto: AppToWebProto, output: OutputStream) =
                appToWebProto.writeTo(output)
        }
    }
}
