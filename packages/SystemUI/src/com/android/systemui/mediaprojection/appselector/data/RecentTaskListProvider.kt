/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.mediaprojection.appselector.data

import android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE
import android.content.pm.UserInfo
import android.os.UserManager
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.screencapture.sharescreen.domain.interactor.ScreenCaptureShareScreenFeaturesInteractor
import com.android.systemui.settings.UserTracker
import com.android.systemui.util.kotlin.getOrNull
import com.android.users.UserType
import com.android.wm.shell.recents.RecentTasks
import com.android.wm.shell.shared.GroupedTaskInfo
import java.util.Optional
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface RecentTaskListProvider {
    /** Loads recent tasks, the returned task list is from the most-recent to least-recent order */
    suspend fun loadRecentTasks(): List<RecentTask>
}

class ShellRecentTaskListProvider
@Inject
constructor(
    @Background private val coroutineDispatcher: CoroutineDispatcher,
    @Background private val backgroundExecutor: Executor,
    private val recentTasks: Optional<RecentTasks>,
    private val userTracker: UserTracker,
    private val userManager: UserManager,
    private val screenShareFeatureInteractor: ScreenCaptureShareScreenFeaturesInteractor,
) : RecentTaskListProvider {

    private val recents by lazy { recentTasks.getOrNull() }

    override suspend fun loadRecentTasks(): List<RecentTask> =
        withContext(coroutineDispatcher) {
            val groupedTasks: List<GroupedTaskInfo> = recents?.getTasks() ?: emptyList()
            val isLargeScreen = screenShareFeatureInteractor.isLargeScreenSharingEnabled
            // Note: the returned task list is from the most-recent to least-recent order.
            // When opening the app selector in full screen, index 0 will be just the app selector
            // activity and a null second task, so the foreground task will be index 1, but when
            // opening the app selector in split screen mode, the foreground task will be the second
            // task in index 0.
            // Note that the app selector does not exist in the task list when
            // isLargeScreenSharingEnabled is true.
            // TODO(346588978): This needs to be updated for mixed groups
            val foregroundGroup =
                if (groupedTasks.firstOrNull()?.splitBounds != null || isLargeScreen)
                    groupedTasks.firstOrNull()
                else groupedTasks.elementAtOrNull(1)

            val foregroundTaskIds =
                listOfNotNull(
                    foregroundGroup?.taskInfo1?.taskId,
                    foregroundGroup?.taskInfo2?.taskId,
                )

            groupedTasks.flatMap { groupedTaskInfo ->
                val recentTasks = mutableListOf<RecentTask>()
                if (groupedTaskInfo.isBaseType(GroupedTaskInfo.TYPE_DESK)) {
                    // Fullscreen and paired tasks always come before tasks in desks within
                    // groupedTasks (note: enableShellTopTaskTracking is disabled by default).
                    // Tasks in a desk are ordered from most-recent to least-recent.
                    // Therefore, the foreground task within a desk needs separate
                    // identification to ensure it's accurately marked when multiple desks
                    // are active.
                    val foregroundTask =
                        if (isLargeScreen) groupedTaskInfo.taskInfoList.getOrNull(0)
                        else groupedTaskInfo.taskInfoList.getOrNull(1)
                    groupedTaskInfo.taskInfoList.forEach { taskInfo ->
                        recentTasks.add(
                            RecentTask(
                                taskInfo,
                                taskInfo.taskId == foregroundTask?.taskId && taskInfo.isVisible,
                                userManager.getUserInfo(taskInfo.userId).toUserType(),
                                groupedTaskInfo.splitBounds,
                            )
                        )
                    }
                } else {
                    val task1 =
                        if (groupedTaskInfo.taskInfo1 != null) {
                            RecentTask(
                                groupedTaskInfo.taskInfo1!!,
                                groupedTaskInfo.taskInfo1!!.taskId in foregroundTaskIds &&
                                    groupedTaskInfo.taskInfo1!!.isVisible,
                                userManager
                                    .getUserInfo(groupedTaskInfo.taskInfo1!!.userId)
                                    .toUserType(),
                                groupedTaskInfo.splitBounds,
                            )
                        } else null

                    val task2 =
                        if (groupedTaskInfo.taskInfo2 != null) {
                            RecentTask(
                                groupedTaskInfo.taskInfo2!!,
                                groupedTaskInfo.taskInfo2!!.taskId in foregroundTaskIds &&
                                    groupedTaskInfo.taskInfo2!!.isVisible,
                                userManager
                                    .getUserInfo(groupedTaskInfo.taskInfo2!!.userId)
                                    .toUserType(),
                                groupedTaskInfo.splitBounds,
                            )
                        } else null
                    recentTasks.addAll(listOfNotNull(task1, task2))
                }
                recentTasks
            }
        }

    private suspend fun RecentTasks.getTasks(): List<GroupedTaskInfo> =
        suspendCoroutine { continuation ->
            getRecentTasks(
                Integer.MAX_VALUE,
                RECENT_IGNORE_UNAVAILABLE,
                userTracker.userId,
                backgroundExecutor,
            ) { tasks ->
                continuation.resume(tasks)
            }
        }

    private fun UserInfo.toUserType(): UserType =
        if (isCloneProfile) {
            UserType.CLONED
        } else if (isManagedProfile) {
            UserType.WORK
        } else if (isPrivateProfile) {
            UserType.PRIVATE
        } else {
            UserType.MAIN
        }
}
