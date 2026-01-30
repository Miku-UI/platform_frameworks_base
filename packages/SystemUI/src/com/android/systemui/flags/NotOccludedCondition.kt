package com.android.systemui.flags

import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotOccludedCondition
@Inject
constructor(
    private val keyguardTransitionInteractorLazy: Lazy<KeyguardTransitionInteractor>,
    private val sceneInteractorProvider: Lazy<SceneInteractor>,
) : ConditionalRestarter.Condition {

    /** Returns true when the lockscreen isn't occluded by an activity (like a call or camera). */
    override val canRestartNow: Flow<Boolean>
        get() {
            return if (SceneContainerFlag.isEnabled) {
                sceneInteractorProvider.get().currentScene.map { it != Scenes.Occluded }
            } else {
                keyguardTransitionInteractorLazy.get().transitionValue(KeyguardState.OCCLUDED).map {
                    it == 0f
                }
            }
        }
}
