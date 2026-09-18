package com.android.systemui.keyguard.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.data.repository.KeyguardRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.BiometricUnlockModel
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_DISMISS
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_DISMISS_BOUNCER
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_NONE
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_NONE_UNLOCKED
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_ONLY_WAKE
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_ONLY_WAKE_UNLOCKED
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_SHOW_BOUNCER
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_WAKE_AND_DISMISS
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_WAKE_AND_DISMISS_FROM_DREAM
import com.android.systemui.statusbar.phone.BiometricUnlockController.MODE_WAKE_AND_DISMISS_PULSING
import com.android.systemui.statusbar.phone.BiometricUnlockController.WakeAndUnlockMode
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@SysUISingleton
class BiometricUnlockInteractor
@Inject
constructor(private val keyguardRepository: KeyguardRepository) {

    val unlockState: StateFlow<BiometricUnlockModel> = keyguardRepository.biometricUnlockState

    fun setBiometricUnlockState(
        @WakeAndUnlockMode unlockStateInt: Int,
        biometricUnlockSource: BiometricUnlockSource?,
    ) {
        val state = biometricModeIntToObject(unlockStateInt)
        keyguardRepository.setBiometricUnlockState(state, biometricUnlockSource)
    }

    private fun biometricModeIntToObject(@WakeAndUnlockMode value: Int): BiometricUnlockMode {
        return when (value) {
            MODE_NONE -> BiometricUnlockMode.NONE
            MODE_WAKE_AND_DISMISS -> BiometricUnlockMode.WAKE_AND_DISMISS
            MODE_WAKE_AND_DISMISS_PULSING -> BiometricUnlockMode.WAKE_AND_DISMISS_PULSING
            MODE_SHOW_BOUNCER -> BiometricUnlockMode.SHOW_BOUNCER
            MODE_ONLY_WAKE -> BiometricUnlockMode.ONLY_WAKE
            MODE_DISMISS -> BiometricUnlockMode.DISMISS
            MODE_WAKE_AND_DISMISS_FROM_DREAM -> BiometricUnlockMode.WAKE_AND_DISMISS_FROM_DREAM
            MODE_DISMISS_BOUNCER -> BiometricUnlockMode.DISMISS_BOUNCER
            MODE_NONE_UNLOCKED -> BiometricUnlockMode.NONE_UNLOCKED
            MODE_ONLY_WAKE_UNLOCKED -> BiometricUnlockMode.ONLY_WAKE_UNLOCKED
            else -> throw IllegalArgumentException("Invalid BiometricUnlockModel value: $value")
        }
    }
}
