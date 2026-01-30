package com.android.systemui.bouncer.ui.binder

import android.view.ViewGroup
import com.android.keyguard.KeyguardMessageAreaController
import com.android.keyguard.dagger.KeyguardBouncerComponent
import com.android.systemui.Flags.contAuthPlugin
import com.android.systemui.biometrics.plugins.AuthContextPlugins
import com.android.systemui.bouncer.domain.interactor.BouncerMessageInteractor
import com.android.systemui.bouncer.ui.viewmodel.KeyguardBouncerViewModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.ui.viewmodel.GlanceableHubToPrimaryBouncerTransitionViewModel
import com.android.systemui.keyguard.ui.viewmodel.PrimaryBouncerToDreamingTransitionViewModel
import com.android.systemui.keyguard.ui.viewmodel.PrimaryBouncerToGoneTransitionViewModel
import com.android.systemui.log.BouncerLogger
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import dagger.Lazy
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

/** Helper data class that allows to lazy load all the dependencies of the legacy bouncer. */
@SysUISingleton
data class LegacyBouncerDependencies
@Inject
constructor(
    @Main val mainDispatcher: CoroutineDispatcher,
    val viewModel: KeyguardBouncerViewModel,
    val primaryBouncerToDreamingTransitionViewModel: PrimaryBouncerToDreamingTransitionViewModel,
    val primaryBouncerToGoneTransitionViewModel: PrimaryBouncerToGoneTransitionViewModel,
    val glanceableHubToPrimaryBouncerTransitionViewModel:
        GlanceableHubToPrimaryBouncerTransitionViewModel,
    val componentFactory: KeyguardBouncerComponent.Factory,
    val messageAreaControllerFactory: KeyguardMessageAreaController.Factory,
    val bouncerMessageInteractor: BouncerMessageInteractor,
    val bouncerLogger: BouncerLogger,
    val selectedUserInteractor: SelectedUserInteractor,
)

/**
 * Toggles between the compose and non compose version of the bouncer, instantiating only the
 * dependencies required for each.
 */
@SysUISingleton
class BouncerViewBinder
@Inject
constructor(
    private val legacyBouncerDependencies: Lazy<LegacyBouncerDependencies>,
    private val contextPlugins: Optional<AuthContextPlugins>,
) {
    fun bind(view: ViewGroup) {
        val deps = legacyBouncerDependencies.get()
        KeyguardBouncerViewBinder.bind(
            deps.mainDispatcher,
            view,
            deps.viewModel,
            deps.primaryBouncerToDreamingTransitionViewModel,
            deps.primaryBouncerToGoneTransitionViewModel,
            deps.glanceableHubToPrimaryBouncerTransitionViewModel,
            deps.componentFactory,
            deps.messageAreaControllerFactory,
            deps.bouncerMessageInteractor,
            deps.bouncerLogger,
            deps.selectedUserInteractor,
            if (contAuthPlugin()) contextPlugins.orElse(null) else null,
        )
    }
}
