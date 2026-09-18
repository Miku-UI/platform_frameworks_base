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

package com.android.systemui.display.data.repository

import android.view.Display
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Returns a flow that emits the display type for the given display ID. */
interface DisplayTypeRepository {
    val displayType: StateFlow<Int>
}

class DisplayTypeRepositoryImpl
@AssistedInject
constructor(
    private val displayRepository: DisplayRepository,
    @Assisted private val displayId: StateFlow<Int>,
    // Main Scope as this needs to be frame perfect in some cases (e.g. when the shade moves between
    // displays)
    @Application private val mainScope: CoroutineScope,
) : DisplayTypeRepository {

    private val display: Flow<Display?> =
        displayId.flatMapLatest { id ->
            displayRepository.displayChangeEvent
                .onStart { emit(id) }
                .filter { it == id }
                .mapLatest { displayRepository.getDisplay(id) }
        }

    override val displayType: StateFlow<Int>
        get() =
            display
                .map { display -> display?.type ?: Display.TYPE_INTERNAL }
                .stateIn(mainScope, started = SharingStarted.Eagerly, Display.TYPE_INTERNAL)

    @AssistedFactory
    interface Factory {
        fun create(displayId: StateFlow<Int>): DisplayTypeRepositoryImpl
    }
}

/** A [DisplayTypeRepository] that returns the type of the display hosting the shade. */
@SysUISingleton
class ShadeDisplayTypeRepository
@Inject
constructor(
    shadeDisplaysRepository: ShadeDisplaysRepository,
    displayTypeRepositoryFactory: DisplayTypeRepositoryImpl.Factory,
) : DisplayTypeRepository {
    private val displayTypeRepository =
        displayTypeRepositoryFactory.create(shadeDisplaysRepository.pendingDisplayId)
    override val displayType: StateFlow<Int>
        get() = displayTypeRepository.displayType
}
