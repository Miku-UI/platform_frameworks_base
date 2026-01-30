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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Interface for classes that can access device-entry-related application state. */
interface DeviceEntryRepository {
    /**
     * Whether the lockscreen is enabled for the current user. This is `true` whenever the user has
     * chosen any secure authentication method and even if they set the lockscreen to be dismissed
     * when the user swipes on it.
     */
    val isLockscreenEnabled: StateFlow<Boolean>

    val deviceUnlockStatus: MutableStateFlow<DeviceUnlockStatus>

    /**
     * Whether the lockscreen is enabled for the current user. This is `true` whenever the user has
     * chosen any secure authentication method and even if they set the lockscreen to be dismissed
     * when the user swipes on it.
     */
    suspend fun isLockscreenEnabled(): Boolean
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

    private val _isLockscreenEnabled = MutableStateFlow(true)
    override val isLockscreenEnabled: StateFlow<Boolean> = _isLockscreenEnabled.asStateFlow()

    override val deviceUnlockStatus =
        MutableStateFlow(DeviceUnlockStatus(isUnlocked = false, deviceUnlockSource = null))

    override suspend fun isLockscreenEnabled(): Boolean {
        return withContext(backgroundDispatcher) {
            val selectedUserId = userRepository.getSelectedUserInfo().id
            val isEnabled = !lockPatternUtils.isLockScreenDisabled(selectedUserId)
            _isLockscreenEnabled.value = isEnabled
            isEnabled
        }
    }
}

@Module
interface DeviceEntryRepositoryModule {
    @Binds fun repository(impl: DeviceEntryRepositoryImpl): DeviceEntryRepository
}
