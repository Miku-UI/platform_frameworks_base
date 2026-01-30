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

package com.android.server.permission.access.appfunction

import android.os.UserHandle
import android.util.Slog
import com.android.modules.utils.BinaryXmlPullParser
import com.android.modules.utils.BinaryXmlSerializer
import com.android.server.permission.access.AccessState
import com.android.server.permission.access.AppIdAppFunctionAccessFlags
import com.android.server.permission.access.MutableAccessState
import com.android.server.permission.access.MutableAppIdAppFunctionAccessFlags
import com.android.server.permission.access.WriteMode
import com.android.server.permission.access.collection.*
import com.android.server.permission.access.immutable.*
import com.android.server.permission.access.util.attributeInt
import com.android.server.permission.access.util.forEachTag
import com.android.server.permission.access.util.getAttributeIntOrThrow
import com.android.server.permission.access.util.tag
import com.android.server.permission.access.util.tagName

class AppIdAppFunctionAccessPersistence {
    fun BinaryXmlPullParser.parseUserState(state: MutableAccessState, userId: Int) {
        when (tagName) {
            TAG_APP_ID_APP_FUNCTION_ACCESSES -> parseAppIdAppFunctionAccesses(state, userId)
            else -> {}
        }
    }

    private fun BinaryXmlPullParser.parseAppIdAppFunctionAccesses(
        state: MutableAccessState,
        userId: Int,
    ) {
        val userState = state.mutateUserState(userId, WriteMode.NONE)!!
        val appIdAppFunctionAccessFlags = userState.mutateAppIdAppFunctionAccessFlags()
        forEachTag {
            when (tagName) {
                TAG_APP_ID -> parseAppId(appIdAppFunctionAccessFlags)
                else ->
                    Slog.w(
                        LOG_TAG,
                        "Ignoring unknown tag $tagName when parsing app function accesses",
                    )
            }
        }
        appIdAppFunctionAccessFlags.forEachReversedIndexed { appIdIndex, appId, _ ->
            if (appId !in state.externalState.appIdPackageNames) {
                Slog.w(
                    LOG_TAG,
                    "Dropping unknown app ID $appId when parsing app function access state",
                )
                appIdAppFunctionAccessFlags.removeAt(appIdIndex)
                userState.requestWriteMode(WriteMode.ASYNCHRONOUS)
                return@forEachReversedIndexed
            }
            // check for invalid UIDs in the app's flags
            val appFunctionAccessFlags = appIdAppFunctionAccessFlags.mutateAt(appIdIndex)
            appFunctionAccessFlags.forEachReversedIndexed { targetUidIndex, targetUid, _ ->
                if (
                    UserHandle.getUserId(targetUid) !in state.externalState.userIds ||
                        UserHandle.getAppId(targetUid) !in state.externalState.appIdPackageNames
                ) {
                    Slog.w(
                        LOG_TAG,
                        "Dropping unknown target UID $targetUid with when parsing app function" +
                            "access state",
                    )
                    appFunctionAccessFlags.removeAt(targetUidIndex)
                    userState.requestWriteMode(WriteMode.ASYNCHRONOUS)
                }
            }
            if (appFunctionAccessFlags.isEmpty()) {
                appIdAppFunctionAccessFlags.removeAt(appIdIndex)
                userState.requestWriteMode(WriteMode.ASYNCHRONOUS)
            }
        }
    }

    private fun BinaryXmlPullParser.parseAppId(
        appIdAppFunctionAccessFlags: MutableAppIdAppFunctionAccessFlags
    ) {
        val appId = getAttributeIntOrThrow(ATTR_ID)
        val appFunctionAccessFlags = MutableIntIntMap()
        appIdAppFunctionAccessFlags.put(appId, appFunctionAccessFlags)
        forEachTag {
            when (tagName) {
                TAG_APP_FUNCTION_ACCESS -> parseAppFunctionAccess(appFunctionAccessFlags)
                else ->
                    Slog.w(
                        LOG_TAG,
                        "Ignoring unknown tag $name when parsing app function access state",
                    )
            }
        }
    }

    private fun BinaryXmlPullParser.parseAppFunctionAccess(
        appFunctionAccessFlags: MutableIntIntMap
    ) {
        val targetUid = getAttributeIntOrThrow(ATTR_TARGET_UID)
        val flags = getAttributeIntOrThrow(ATTR_FLAGS)
        appFunctionAccessFlags[targetUid] = flags
    }

    fun BinaryXmlSerializer.serializeUserState(state: AccessState, userId: Int) {
        serializeAppIdAppFunctionAccesses(state.userStates[userId]!!.appIdAppFunctionAccessFlags)
    }

    private fun BinaryXmlSerializer.serializeAppIdAppFunctionAccesses(
        appIdAppFunctionAccessFlags: AppIdAppFunctionAccessFlags
    ) {
        tag(TAG_APP_ID_APP_FUNCTION_ACCESSES) {
            appIdAppFunctionAccessFlags.forEachIndexed { _, appId, appFunctionAccessFlags ->
                serializeAppId(appId, appFunctionAccessFlags)
            }
        }
    }

    private fun BinaryXmlSerializer.serializeAppId(appId: Int, accessFlags: IntIntMap) {
        tag(TAG_APP_ID) {
            attributeInt(ATTR_ID, appId)
            accessFlags.forEachIndexed { _, targetUid, flags ->
                serializeAppIdAppFunctionAccess(targetUid, flags)
            }
        }
    }

    private fun BinaryXmlSerializer.serializeAppIdAppFunctionAccess(targetUid: Int, flags: Int) {
        tag(TAG_APP_FUNCTION_ACCESS) {
            attributeInt(ATTR_TARGET_UID, targetUid)
            attributeInt(ATTR_FLAGS, flags)
        }
    }

    companion object {
        private val LOG_TAG = AppIdAppFunctionAccessPersistence::class.java.simpleName

        private const val TAG_APP_ID = "app-id"
        private const val TAG_APP_ID_APP_FUNCTION_ACCESSES = "app-id-app-function-accesses"
        private const val TAG_APP_FUNCTION_ACCESS = "app-function-access"

        private const val ATTR_ID = "id"
        private const val ATTR_FLAGS = "flags"
        private const val ATTR_TARGET_UID = "targetUid"
    }
}
