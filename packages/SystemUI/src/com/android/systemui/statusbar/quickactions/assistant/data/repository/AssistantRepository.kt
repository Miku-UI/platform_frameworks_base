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

package com.android.systemui.statusbar.quickactions.assistant.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.UserInfo
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.assist.AssistManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Repository responsible for observing the user assistant settings. */
interface AssistantRepository {
    /** This invokes the assistant UI. */
    fun startAssistant(context: Context)

    /** The component name of the primary assistant app for the current user. */
    val assistInfo: StateFlow<ComponentName?>
}

@SysUISingleton
class AssistantRepositoryImpl
@Inject
constructor(
    @Background private val scope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val assistManager: AssistManager,
    private val userRepository: UserRepository,
    private val secureSettings: SecureSettings,
) : AssistantRepository {
    override fun startAssistant(context: Context) {
        assistManager.startAssist(
            context,
            Bundle().apply {
                putInt(
                    AssistManager.INVOCATION_TYPE_KEY,
                    AssistManager.INVOCATION_TYPE_STATUS_BAR_ICON,
                )
            },
        )
    }

    override val assistInfo: StateFlow<ComponentName?> =
        userRepository.selectedUserInfo
            .filter { it.isFull }
            .distinctUntilChanged()
            .flatMapConcat { userInfo -> assistantInfoForUser(userInfo) }
            .stateIn(scope, started = SharingStarted.WhileSubscribed(), initialValue = null)

    /** Refresh the primary assistant info on setting changes. */
    private fun assistantInfoForUser(userInfo: UserInfo): Flow<ComponentName?> =
        secureSettings
            // emit whenever the setting has changed
            .observerFlow(UserHandle.USER_ALL, SETTING)
            .emitOnStart()
            .map { assistManager.getAssistInfoForUser(userInfo.id) }
            .flowOn(backgroundDispatcher)

    private companion object {
        private const val SETTING = Settings.Secure.ASSISTANT
    }
}
