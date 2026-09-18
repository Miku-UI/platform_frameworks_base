package com.android.systemui.deviceentry.data.repository

import com.android.internal.widget.LockPatternUtils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.user.data.repository.UserRepository
import dagger.Binds
import dagger.Module
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow

/** Interface for classes that can access device-entry-related application state. */
interface DeviceEntryRepository {
    val deviceUnlockStatus: MutableStateFlow<DeviceUnlockStatus>
}

/** Encapsulates application state for device entry. */
@SysUISingleton
class DeviceEntryRepositoryImpl
@Inject
constructor(
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val userRepository: UserRepository,
    private val lockPatternUtils: LockPatternUtils,
) : DeviceEntryRepository {

    override val deviceUnlockStatus =
        MutableStateFlow(DeviceUnlockStatus(isUnlocked = false, deviceUnlockSource = null))
}

@Module
interface DeviceEntryRepositoryModule {
    @Binds fun repository(impl: DeviceEntryRepositoryImpl): DeviceEntryRepository
}
