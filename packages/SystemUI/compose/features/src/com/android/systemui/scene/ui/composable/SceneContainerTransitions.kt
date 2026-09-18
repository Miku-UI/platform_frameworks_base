package com.android.systemui.scene.ui.composable

import android.content.res.Resources
import com.android.compose.animation.scene.DefaultInterruptionHandler
import com.android.compose.animation.scene.SceneTransitions
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.reveal.ContainerRevealHaptics
import com.android.compose.animation.scene.transitions
import com.android.internal.jank.Cuj
import com.android.mechanics.behavior.VerticalExpandContainerSpec
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.qs.panels.ui.viewmodel.AnimateQsTilesViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys
import com.android.systemui.scene.shared.model.TransitionKeys.SlightlyFasterShadeTransition
import com.android.systemui.scene.shared.model.TransitionKeys.SystemCommunalTransition
import com.android.systemui.scene.shared.model.TransitionKeys.ToAlwaysOnDisplay
import com.android.systemui.scene.shared.model.TransitionKeys.ToSplitShade
import com.android.systemui.scene.ui.composable.transitions.aodToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.bouncerToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.bouncerToLockscreenTransition
import com.android.systemui.scene.ui.composable.transitions.communalToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.communalToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.communalToSingleShadeTransition
import com.android.systemui.scene.ui.composable.transitions.communalToSplitShadeTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToCommunalTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToNotificationsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToQuickSettingsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToSingleShadeTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToSplitShadeTransition
import com.android.systemui.scene.ui.composable.transitions.fromBouncerPreview
import com.android.systemui.scene.ui.composable.transitions.fromBouncerTransition
import com.android.systemui.scene.ui.composable.transitions.goneToAodEnterFromTop
import com.android.systemui.scene.ui.composable.transitions.goneToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.goneToSingleShadeTransition
import com.android.systemui.scene.ui.composable.transitions.goneToSplitShadeTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToBouncerTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToCommunalSystemTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToCommunalUserTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToDreamTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToGoneWithAnimationOverLockscreenTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToNotificationsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToOccludedTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToQuickSettingsOverlayTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToQuickSettingsSceneTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToSingleShadeTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToSplitShadeTransition
import com.android.systemui.scene.ui.composable.transitions.quickSettingsToAlwaysOnDisplayTransition
import com.android.systemui.scene.ui.composable.transitions.quickSettingsToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.shadeToAlwaysOnDisplayTransition
import com.android.systemui.scene.ui.composable.transitions.shadeToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.sharedBouncerTransitions
import com.android.systemui.scene.ui.composable.transitions.toBouncerTransition
import com.android.systemui.scene.ui.composable.transitions.toNotificationsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.toQuickActionsTransition
import com.android.systemui.scene.ui.composable.transitions.toQuickSettingsShadeTransition
import com.android.systemui.scene.ui.viewmodel.ToBouncerTransitionViewModel
import com.android.systemui.shade.ui.composable.Shade

/**
 * Comprehensive definition of all transitions between scenes and overlays in [SceneContainer].
 *
 * Transitions are automatically reversible, so define only one transition per scene pair. By\
 * convention, use the more common transition direction when defining the pair order, e.g.
 * Lockscreen to Bouncer rather than Bouncer to Lockscreen.
 *
 * The actual transition DSL must be placed in a separate file under the package
 * [com.android.systemui.scene.ui.composable.transitions].
 *
 * Please keep the list sorted alphabetically.
 */
class SceneContainerTransitions : SceneContainerTransitionsBuilder {
    override fun build(
        shadeExpansionMotion: VerticalExpandContainerSpec,
        revealHaptics: ContainerRevealHaptics,
        animateQsTilesViewModel: AnimateQsTilesViewModel,
        toBouncerTransitionViewModel: ToBouncerTransitionViewModel,
        resources: Resources,
    ): SceneTransitions {
        return transitions {
            interruptionHandler = DefaultInterruptionHandler

            val lockscreenToShadeTransitionDistancePx =
                resources.getDimension(R.dimen.lockscreen_shade_full_transition_distance)

            val singleShadeMarginHorizontalPx =
                resources.getDimension(R.dimen.notification_panel_margin_horizontal)

            // Scene transitions

            from(Scenes.Dream, to = Scenes.Communal) { dreamToCommunalTransition() }
            from(Scenes.Dream, to = Scenes.Gone) { dreamToGoneTransition() }
            from(
                Scenes.Dream,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                dreamToSingleShadeTransition(
                    singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx
                )
            }
            from(
                Scenes.Dream,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                key = ToSplitShade,
                cujTag = TAG_EXPAND,
            ) {
                dreamToSplitShadeTransition()
            }
            from(
                Scenes.Dream,
                to = Scenes.QuickSettings,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                dreamToQuickSettingsTransition()
            }
            from(
                Scenes.Gone,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                goneToSingleShadeTransition(
                    singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx
                )
            }
            from(
                Scenes.Gone,
                to = Scenes.Shade,
                key = ToSplitShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                goneToSplitShadeTransition()
            }
            from(
                Scenes.Gone,
                to = Scenes.Shade,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                goneToSingleShadeTransition(
                    singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx,
                    durationScale = 0.9,
                )
            }
            from(
                Scenes.Gone,
                to = Scenes.QuickSettings,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                goneToQuickSettingsTransition()
            }
            from(
                Scenes.Gone,
                to = Scenes.QuickSettings,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                goneToQuickSettingsTransition(durationScale = 0.9)
            }

            from(Scenes.Lockscreen, to = Scenes.Communal) { lockscreenToCommunalUserTransition() }
            from(Scenes.Lockscreen, to = Scenes.Communal, key = SystemCommunalTransition) {
                lockscreenToCommunalSystemTransition()
            }
            from(Scenes.Lockscreen, to = Scenes.Dream) { lockscreenToDreamTransition() }
            from(Scenes.Lockscreen, to = Scenes.Occluded) { lockscreenToOccludedTransition() }

            from(
                Scenes.Lockscreen,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                lockscreenToSingleShadeTransition(
                    transitionDistancePx = lockscreenToShadeTransitionDistancePx,
                    singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx,
                    seekAnimation = true,
                )
            }
            from(
                Scenes.Lockscreen,
                to = Scenes.Shade,
                key = ToSplitShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                lockscreenToSplitShadeTransition(
                    transitionDistancePx = lockscreenToShadeTransitionDistancePx
                )
                sharedElement(Shade.Elements.BackgroundScrim, enabled = false)
            }
            from(
                Scenes.Lockscreen,
                to = Scenes.Shade,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                lockscreenToSingleShadeTransition(
                    transitionDistancePx = lockscreenToShadeTransitionDistancePx,
                    singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx,
                    durationScale = 0.9,
                    seekAnimation = true,
                )
            }
            from(
                Scenes.Lockscreen,
                to = Scenes.QuickSettings,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                lockscreenToQuickSettingsSceneTransition()
            }
            from(Scenes.Lockscreen, to = Scenes.Gone) { lockscreenToGoneTransition() }
            from(
                Scenes.Lockscreen,
                to = Scenes.Gone,
                key = KeyguardTransitionKeys.WithAnimationOverLockscreen,
            ) {
                lockscreenToGoneWithAnimationOverLockscreenTransition()
            }
            from(Scenes.Gone, to = Scenes.Lockscreen, key = ToAlwaysOnDisplay) {
                goneToAodEnterFromTop()
            }
            from(
                Scenes.Lockscreen,
                to = Scenes.Gone,
                key = KeyguardTransitionKeys.AodToGoneTransition,
            ) {
                aodToGoneTransition()
            }
            from(
                Scenes.QuickSettings,
                to = Scenes.Gone,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed { goneToQuickSettingsTransition() }
            }
            from(
                Scenes.QuickSettings,
                to = Scenes.Lockscreen,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed { lockscreenToQuickSettingsSceneTransition() }
            }
            from(
                Scenes.QuickSettings,
                to = Scenes.Lockscreen,
                key = ToAlwaysOnDisplay,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                quickSettingsToAlwaysOnDisplayTransition()
            }
            from(
                Scenes.QuickSettings,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                quickSettingsToShadeTransition(
                    animateQsTilesAsShared = { animateQsTilesViewModel.animateQsTiles }
                )
            }
            from(
                Scenes.QuickSettings,
                to = Scenes.Dream,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed { dreamToQuickSettingsTransition() }
            }

            from(
                Scenes.Shade,
                to = Scenes.Gone,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    goneToSingleShadeTransition(
                        singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx
                    )
                }
            }
            from(Scenes.Occluded, to = Scenes.Shade) {
                goneToSingleShadeTransition(
                    singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx
                )
            }
            from(
                Scenes.Shade,
                to = Scenes.Gone,
                key = ToSplitShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed { goneToSplitShadeTransition() }
            }
            from(
                Scenes.Shade,
                to = Scenes.Lockscreen,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    lockscreenToSingleShadeTransition(
                        transitionDistancePx = null,
                        singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx,
                    )
                }
                sharedElement(Notifications.Elements.StackPlaceholder, enabled = false)
                sharedElement(
                    Notifications.Elements.HeadsUpNotificationPlaceholder,
                    enabled = false,
                )
            }
            from(
                Scenes.Shade,
                to = Scenes.Lockscreen,
                key = ToSplitShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    lockscreenToSplitShadeTransition(
                        transitionDistancePx = lockscreenToShadeTransitionDistancePx
                    )
                }
                sharedElement(Notifications.Elements.StackPlaceholder, enabled = false)
            }
            from(
                Scenes.Shade,
                to = Scenes.Lockscreen,
                key = ToAlwaysOnDisplay,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
            ) {
                shadeToAlwaysOnDisplayTransition()
            }
            from(Scenes.Shade, to = Scenes.Occluded) {
                reversed {
                    goneToSingleShadeTransition(
                        singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx
                    )
                }
            }
            from(
                Scenes.Shade,
                to = Scenes.QuickSettings,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                shadeToQuickSettingsTransition(
                    animateQsTilesAsShared = { animateQsTilesViewModel.animateQsTiles }
                )
            }
            from(
                Scenes.Communal,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                communalToSingleShadeTransition(
                    singleShadeMarginHorizontalPx = singleShadeMarginHorizontalPx
                )
            }
            from(
                Scenes.Communal,
                to = Scenes.Shade,
                key = ToSplitShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                communalToSplitShadeTransition()
            }
            from(
                Scenes.Communal,
                to = Scenes.QuickSettings,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                communalToQuickSettingsTransition()
            }
            from(
                Scenes.QuickSettings,
                to = Scenes.Communal,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed { communalToQuickSettingsTransition() }
            }

            // Overlay transitions

            to(Overlays.QuickActions) { toQuickActionsTransition() }

            sharedBouncerTransitions(toBouncerTransitionViewModel = toBouncerTransitionViewModel)
            from(Overlays.Bouncer, to = Scenes.Gone) { bouncerToGoneTransition() }
            from(Scenes.Dream, to = Overlays.Bouncer) {
                toBouncerTransition(viewModel = toBouncerTransitionViewModel)
            }
            from(
                Scenes.Dream,
                to = Overlays.NotificationsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                dreamToNotificationsShadeTransition(
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Dream,
                to = Overlays.QuickSettingsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                dreamToQuickSettingsShadeTransition(
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Overlays.NotificationsShade,
                to = Scenes.Dream,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    dreamToNotificationsShadeTransition(
                        shadeExpansionMotion = shadeExpansionMotion,
                        revealHaptics = revealHaptics,
                    )
                }
            }
            from(
                Overlays.QuickSettingsShade,
                to = Scenes.Dream,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    dreamToQuickSettingsShadeTransition(
                        shadeExpansionMotion = shadeExpansionMotion,
                        revealHaptics = revealHaptics,
                    )
                }
            }
            from(Overlays.Bouncer, to = Scenes.Dream) { fromBouncerTransition() }
            from(
                Overlays.Bouncer,
                to = Scenes.Dream,
                key = TransitionKey.PredictiveBack,
                preview = { fromBouncerPreview() },
            ) {
                fromBouncerTransition()
            }
            from(Scenes.Lockscreen, to = Overlays.Bouncer) {
                lockscreenToBouncerTransition(
                    toBouncerTransitionViewModel = toBouncerTransitionViewModel
                )
            }
            from(Overlays.Bouncer, to = Scenes.Lockscreen) { bouncerToLockscreenTransition() }
            from(
                Overlays.Bouncer,
                to = Scenes.Lockscreen,
                key = TransitionKey.PredictiveBack,
                preview = { fromBouncerPreview() },
            ) {
                bouncerToLockscreenTransition()
            }
            from(Scenes.Communal, to = Overlays.Bouncer) {
                toBouncerTransition(viewModel = toBouncerTransitionViewModel)
            }
            from(Scenes.Communal, to = Scenes.Gone, key = TransitionKeys.SwipeUpToGone) {
                communalToGoneTransition()
            }
            from(
                Overlays.Bouncer,
                to = Scenes.Communal,
                key = TransitionKey.PredictiveBack,
                preview = { fromBouncerPreview() },
            ) {
                fromBouncerTransition()
            }
            to(
                Overlays.NotificationsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                toNotificationsShadeTransition(
                    enableSharedElements = true,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Overlays.NotificationsShade,
                to = Scenes.Gone,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    toNotificationsShadeTransition(
                        shadeExpansionMotion = shadeExpansionMotion,
                        enableSharedElements = false,
                        revealHaptics = revealHaptics,
                    )
                }
            }
            from(
                Scenes.Gone,
                to = Overlays.NotificationsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                toNotificationsShadeTransition(
                    shadeExpansionMotion = shadeExpansionMotion,
                    enableSharedElements = false,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Gone,
                to = Overlays.QuickSettingsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                toQuickSettingsShadeTransition(
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Overlays.QuickSettingsShade,
                to = Scenes.Gone,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    toQuickSettingsShadeTransition(
                        shadeExpansionMotion = shadeExpansionMotion,
                        revealHaptics = revealHaptics,
                    )
                }
            }

            from(
                Scenes.Gone,
                to = Overlays.NotificationsShade,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                toNotificationsShadeTransition(
                    durationScale = 0.9,
                    enableSharedElements = false,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Gone,
                to = Overlays.QuickSettingsShade,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                toQuickSettingsShadeTransition(
                    durationScale = 0.9,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Lockscreen,
                to = Overlays.NotificationsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                lockscreenToNotificationsShadeTransition(
                    useSharedElementTransitions = true,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Lockscreen,
                to = Overlays.NotificationsShade,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                lockscreenToNotificationsShadeTransition(
                    durationScale = 0.9,
                    useSharedElementTransitions = true,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Lockscreen,
                to = Overlays.QuickSettingsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                lockscreenToQuickSettingsOverlayTransition(
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Lockscreen,
                to = Overlays.QuickSettingsShade,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_EXPAND,
            ) {
                lockscreenToQuickSettingsOverlayTransition(
                    durationScale = 0.9,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Overlays.NotificationsShade,
                to = Scenes.Lockscreen,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    lockscreenToNotificationsShadeTransition(
                        useSharedElementTransitions = false,
                        shadeExpansionMotion = shadeExpansionMotion,
                        revealHaptics = revealHaptics,
                    )
                }
            }
            from(
                Overlays.NotificationsShade,
                to = Scenes.Lockscreen,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    lockscreenToNotificationsShadeTransition(
                        durationScale = 0.9,
                        useSharedElementTransitions = false,
                        shadeExpansionMotion = shadeExpansionMotion,
                        revealHaptics = revealHaptics,
                    )
                }
            }
            from(
                Overlays.QuickSettingsShade,
                to = Scenes.Lockscreen,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    lockscreenToQuickSettingsOverlayTransition(
                        shadeExpansionMotion = shadeExpansionMotion,
                        revealHaptics = revealHaptics,
                    )
                }
            }
            from(
                Overlays.QuickSettingsShade,
                to = Scenes.Lockscreen,
                key = SlightlyFasterShadeTransition,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE,
                cujTag = TAG_COLLAPSE,
            ) {
                reversed {
                    lockscreenToQuickSettingsOverlayTransition(
                        durationScale = 0.9,
                        shadeExpansionMotion = shadeExpansionMotion,
                        revealHaptics = revealHaptics,
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG_EXPAND = "Expand"
        const val TAG_COLLAPSE = "Collapse"
    }
}
