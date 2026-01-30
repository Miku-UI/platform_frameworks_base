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

import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_PREGRANTED
import android.os.Build
import android.os.Environment
import android.util.AtomicFile
import android.util.Slog
import android.util.Xml
import com.android.server.permission.access.MutateStateScope
import com.android.server.permission.access.collection.* // ktlint-disable no-wildcard-imports
import com.android.server.permission.access.util.forEachTag
import com.android.server.permission.access.util.getAttributeValueOrThrow
import com.android.server.permission.access.util.tagName
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import kotlin.collections.set
import org.xmlpull.v1.XmlPullParser

class AppFunctionAccessPregrant(private val service: AppFunctionAccessService) {
    fun MutateStateScope.applyIfNeeded(userId: Int) {
        val userState = oldState.userStates[userId] ?: return
        if (userState.appFunctionAccessPregrantFingerprint == Build.FINGERPRINT) {
            if (DEBUG) {
                Slog.i(
                    LOG_TAG,
                    "No change to fingerprint " +
                        "${userState.appFunctionAccessPregrantFingerprint} for user $userId",
                )
            }
            return
        }
        for ((agentPackageName, targets) in parseAppFunctionPregrantFiles()) {
            val packageStates = oldState.externalState.packageStates
            if (packageStates[agentPackageName]?.isSystem != true) {
                // only system apps can be pregranted appfunction access
                continue
            }
            for (targetPackageName in targets) {
                if (packageStates[targetPackageName]?.isSystem != true) {
                    // only system apps can be targets for appfunction pregrants
                    continue
                }
                service.updateAccessFlags(
                    agentPackageName,
                    userId,
                    targetPackageName,
                    userId,
                    ACCESS_FLAG_PREGRANTED,
                    ACCESS_FLAG_PREGRANTED,
                )
                if (DEBUG) {
                    Slog.i(
                        LOG_TAG,
                        "Applied app function access pregrant for " +
                            "$agentPackageName with target $targetPackageName on user $userId",
                    )
                }
            }
        }
        if (DEBUG) {
            Slog.i(
                LOG_TAG,
                "Updating fingerprint from ${userState.appFunctionAccessPregrantFingerprint}" +
                    " to ${Build.FINGERPRINT} on user $userId",
            )
        }
        newState.mutateUserState(userId)!!.appFunctionAccessPregrantFingerprint = Build.FINGERPRINT
    }

    private fun parseAppFunctionPregrantFiles(): Map<String, List<String>> {
        if (DEBUG) {
            Slog.i(LOG_TAG, "Parsing app function access pregrant files...")
        }
        val pregrants = mutableMapOf<String, MutableList<String>>()
        pregrantFiles.forEachIndexed { _, file ->
            if (DEBUG) {
                Slog.i(LOG_TAG, "Parsing app function access pregrant file $file")
            }
            if (!file.path.endsWith(".xml")) {
                Slog.w(LOG_TAG, "Non-xml file $file in ${file.getParent()} directory, ignoring")
                return@forEachIndexed
            }
            file.parse { parseAppFunctionPregrants(pregrants) }
        }
        return pregrants
    }

    /**
     * @return {@code true} if the file is successfully read from the disk; {@code false} if the
     *   file doesn't exist yet.
     */
    private inline fun File.parse(block: XmlPullParser.() -> Unit) =
        try {
            AtomicFile(this).openRead().use {
                Xml.newPullParser().apply {
                    setInput(it, StandardCharsets.UTF_8.name())
                    block()
                }
            }
        } catch (_: FileNotFoundException) {
            Slog.i(LOG_TAG, "$this not found")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to read $this", e)
        }

    private val pregrantFiles: List<File> by
        lazy(LazyThreadSafetyMode.NONE) {
            buildList {
                var directory = File(Environment.getRootDirectory(), DIRECTORY_PATH)
                if (directory.isDirectory() && directory.canRead()) {
                    addAll(directory.listFiles())
                }
                directory = File(Environment.getVendorDirectory(), DIRECTORY_PATH)
                if (directory.isDirectory() && directory.canRead()) {
                    addAll(directory.listFiles())
                }
                directory = File(Environment.getOdmDirectory(), DIRECTORY_PATH)
                if (directory.isDirectory() && directory.canRead()) {
                    addAll(directory.listFiles())
                }
                directory = File(Environment.getProductDirectory(), DIRECTORY_PATH)
                if (directory.isDirectory() && directory.canRead()) {
                    addAll(directory.listFiles())
                }
                directory = File(Environment.getSystemExtDirectory(), DIRECTORY_PATH)
                if (directory.isDirectory() && directory.canRead()) {
                    addAll(directory.listFiles())
                }
                directory = File(Environment.getOemDirectory(), DIRECTORY_PATH)
                if (directory.isDirectory() && directory.canRead()) {
                    addAll(directory.listFiles())
                }
            }
        }

    private fun XmlPullParser.parseAppFunctionPregrants(
        pregrants: MutableMap<String, MutableList<String>>
    ) {
        forEachTag {
            if (DEBUG) {
                Slog.i(LOG_TAG, "Parsing app function access pregrant for top level tag $name")
            }
            when (tagName) {
                TAG_PREGRANTS -> parseAppFunctionAgent(pregrants)
                else -> Slog.w(LOG_TAG, "Unknown tag $name")
            }
        }
    }

    private fun XmlPullParser.parseAppFunctionAgent(
        pregrants: MutableMap<String, MutableList<String>>
    ) {
        forEachTag {
            when (tagName) {
                TAG_AGENT -> {
                    val agentPackageName = getAttributeValueOrThrow(ATTR_PACKAGE)
                    val targets = pregrants.getOrPut(agentPackageName) { mutableListOf() }
                    parseAppFunctionTargets(targets)
                    pregrants[agentPackageName] = targets
                    if (DEBUG) {
                        Slog.i(
                            LOG_TAG,
                            "Parsed app function access pregrant for " +
                                "$agentPackageName, with targets $targets",
                        )
                    }
                }
                else -> Slog.w(LOG_TAG, "Unknown tag $name under $TAG_PREGRANTS")
            }
        }
    }

    private fun XmlPullParser.parseAppFunctionTargets(pregrants: MutableList<String>) {
        forEachTag {
            when (tagName) {
                TAG_TARGET -> pregrants.add(getAttributeValueOrThrow(ATTR_PACKAGE))
                else -> Slog.w(LOG_TAG, "Unknown tag $name under $TAG_AGENT")
            }
        }
    }

    companion object {
        private const val DEBUG = false

        private const val LOG_TAG = "AppFunctionPregrant"

        private const val DIRECTORY_PATH = "etc/app-function-access-pregrants"

        private const val TAG_PREGRANTS = "pregrants"
        private const val TAG_AGENT = "agent"
        private const val TAG_TARGET = "target"
        private const val ATTR_PACKAGE = "package"
    }
}
