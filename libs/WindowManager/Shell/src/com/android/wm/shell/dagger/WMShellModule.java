/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.dagger;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.UserManager;
import android.view.Choreographer;
import android.view.IWindowManager;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.window.DesktopExperienceFlags;
import android.window.TaskSnapshotManager;

import androidx.annotation.OptIn;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.policy.DesktopModeCompatPolicy;
import com.android.internal.util.LatencyTracker;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.activityembedding.ActivityEmbeddingController;
import com.android.wm.shell.apptoweb.AppToWebGenericLinksParser;
import com.android.wm.shell.apptoweb.AppToWebRepository;
import com.android.wm.shell.apptoweb.AppToWebRepositoryImpl;
import com.android.wm.shell.apptoweb.AssistContentRequester;
import com.android.wm.shell.apptoweb.data.AppToWebDatastoreRepository;
import com.android.wm.shell.appzoomout.AppZoomOutController;
import com.android.wm.shell.back.BackAnimationController;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.bubbles.BubbleHelper;
import com.android.wm.shell.bubbles.BubbleRootTask;
import com.android.wm.shell.bubbles.BubbleTaskUnfoldTransitionMerger;
import com.android.wm.shell.bubbles.bar.DragToBubbleController;
import com.android.wm.shell.bubbles.transitions.BubbleTransitions;
import com.android.wm.shell.common.ClientFullscreenRequestController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.HomeIntentProvider;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.LockTaskChangeListener;
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController;
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorSurface;
import com.android.wm.shell.common.MultiInstanceHelper;
import com.android.wm.shell.common.QuitFocusedAppKeyGestureHandler;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.UserProfileContexts;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.split.SplitState;
import com.android.wm.shell.common.suppliers.TransactionSupplier;
import com.android.wm.shell.common.transition.TransitionStateHolder;
import com.android.wm.shell.compatui.api.CompatUIHandler;
import com.android.wm.shell.compatui.api.CompatUISharedRepositoryCleanUp;
import com.android.wm.shell.compatui.letterbox.DelegateLetterboxTransitionObserver;
import com.android.wm.shell.compatui.letterbox.LetterboxCommandHandler;
import com.android.wm.shell.compatui.letterbox.config.DefaultLetterboxDependenciesHelper;
import com.android.wm.shell.compatui.letterbox.config.IgnoreLetterboxDependenciesHelper;
import com.android.wm.shell.compatui.letterbox.config.LetterboxDependenciesHelper;
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxCleanupAdapter;
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskListenerAdapter;
import com.android.wm.shell.crashhandling.ShellCrashHandler;
import com.android.wm.shell.dagger.back.ShellBackAnimationModule;
import com.android.wm.shell.dagger.desktop.DesktopModule;
import com.android.wm.shell.dagger.hierarchy.ContainerHierarchyDependency;
import com.android.wm.shell.dagger.hierarchy.HandheldContainersModule;
import com.android.wm.shell.dagger.pinnedlayer.PinnedLayerModule;
import com.android.wm.shell.dagger.pip.PipModule;
import com.android.wm.shell.desktopmode.CloseDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopActivityOrientationChangeHandler;
import com.android.wm.shell.desktopmode.DesktopAnimationConfiguration;
import com.android.wm.shell.desktopmode.DesktopBackNavTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopDisplayEventHandler;
import com.android.wm.shell.desktopmode.DesktopImeHandler;
import com.android.wm.shell.desktopmode.DesktopImmersiveController;
import com.android.wm.shell.desktopmode.DesktopInOrderTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopMinimizationTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopMixedTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopModeDragAndDropAnimatorHelper;
import com.android.wm.shell.desktopmode.DesktopModeDragAndDropTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopModeEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeKeyGestureHandler;
import com.android.wm.shell.desktopmode.DesktopModeLoggerTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopModeMoveToDisplayTransitionHandler;
import com.android.wm.shell.desktopmode.DesktopModeShellCommandHandler;
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger;
import com.android.wm.shell.desktopmode.DesktopRemoteListener;
import com.android.wm.shell.desktopmode.DesktopTaskChangeListener;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopTasksLimiter;
import com.android.wm.shell.desktopmode.DesktopTasksTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.DisplayDisconnectTransitionHandler;
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler;
import com.android.wm.shell.desktopmode.EnterDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.ExitDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.FreeformFallbackTransitionHandler;
import com.android.wm.shell.desktopmode.FreeformFallbackTransitionObserver;
import com.android.wm.shell.desktopmode.NormalAppLayerController;
import com.android.wm.shell.desktopmode.NormalAppLayerHandler;
import com.android.wm.shell.desktopmode.OverviewToDesktopTransitionObserver;
import com.android.wm.shell.desktopmode.PipDisplayDisconnectHandler;
import com.android.wm.shell.desktopmode.PipDisplayReconnectHandler;
import com.android.wm.shell.desktopmode.ReturnToDragStartAnimator;
import com.android.wm.shell.desktopmode.ShellDesktopState;
import com.android.wm.shell.desktopmode.ShellDesktopStateImpl;
import com.android.wm.shell.desktopmode.SnapController;
import com.android.wm.shell.desktopmode.SpringDragToDesktopTransitionHandler;
import com.android.wm.shell.desktopmode.ToggleResizeDesktopTaskTransitionHandler;
import com.android.wm.shell.desktopmode.VisualIndicatorUpdateScheduler;
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository;
import com.android.wm.shell.desktopmode.WindowDragTransitionHandler;
import com.android.wm.shell.desktopmode.api.DesktopMode;
import com.android.wm.shell.desktopmode.api.impl.DesktopModeAidlProvider;
import com.android.wm.shell.desktopmode.api.impl.DesktopModeImpl;
import com.android.wm.shell.desktopmode.clientfullscreenrequest.DesktopFullscreenRequestHandler;
import com.android.wm.shell.desktopmode.compatui.SystemModalsTransitionHandler;
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer;
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializerImpl;
import com.android.wm.shell.desktopmode.data.persistence.DesktopPersistentRepository;
import com.android.wm.shell.desktopmode.desktopfirst.DesktopDisplayModeController;
import com.android.wm.shell.desktopmode.desktopfirst.DesktopFirstListenerManager;
import com.android.wm.shell.desktopmode.desktoptaskshandlers.DesktopTasksTransitionHandler;
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider;
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityUtils;
import com.android.wm.shell.desktopmode.education.AppHandleEducationController;
import com.android.wm.shell.desktopmode.education.AppHandleEducationFilter;
import com.android.wm.shell.desktopmode.education.AppToWebEducationController;
import com.android.wm.shell.desktopmode.education.AppToWebEducationFilter;
import com.android.wm.shell.desktopmode.education.data.AppHandleEducationDatastoreRepository;
import com.android.wm.shell.desktopmode.education.data.AppToWebEducationDatastoreRepository;
import com.android.wm.shell.desktopmode.homescreenpeeking.DesktopHomeScreenPeekController;
import com.android.wm.shell.desktopmode.multidesks.DeskSwitchTransitionHandler;
import com.android.wm.shell.desktopmode.multidesks.DesksController;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver;
import com.android.wm.shell.desktopmode.multidesks.RootTaskDesksOrganizer;
import com.android.wm.shell.draganddrop.DragAndDropController;
import com.android.wm.shell.draganddrop.GlobalDragListener;
import com.android.wm.shell.freeform.FreeformComponents;
import com.android.wm.shell.freeform.FreeformTaskListener;
import com.android.wm.shell.freeform.FreeformTaskTransitionHandler;
import com.android.wm.shell.freeform.FreeformTaskTransitionObserver;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarterInitializer;
import com.android.wm.shell.freeform.TaskChangeListener;
import com.android.wm.shell.fullscreen.FullscreenDisconnectHandler;
import com.android.wm.shell.fullscreen.FullscreenReconnectHandler;
import com.android.wm.shell.keyguard.KeyguardTransitionHandler;
import com.android.wm.shell.onehanded.OneHandedController;
import com.android.wm.shell.packageupdate.PackageUpdateController;
import com.android.wm.shell.packageupdate.PackageUpdateTransitionHandler;
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController;
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerFlags;
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerHandler;
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUiState;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler;
import com.android.wm.shell.pip2.phone.PipScheduler;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.recents.PerDisplayRecentsTransitionStateListener;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.scrolltotop.ScrollToTopController;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.annotations.ShellAnimationThread;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellDesktopThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.annotations.ShellMainThreadImmediate;
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfig;
import com.android.wm.shell.shared.desktopmode.DesktopConfig;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.DefaultMixedHandler;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.HomeTransitionObserver;
import com.android.wm.shell.transition.InteractiveTasksTransitionObserver;
import com.android.wm.shell.transition.MixedTransitionHandler;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider;
import com.android.wm.shell.unfold.UnfoldAnimationController;
import com.android.wm.shell.unfold.UnfoldBackgroundController;
import com.android.wm.shell.unfold.UnfoldTransitionHandler;
import com.android.wm.shell.unfold.animation.FullscreenUnfoldTaskAnimator;
import com.android.wm.shell.unfold.animation.SplitTaskUnfoldAnimator;
import com.android.wm.shell.unfold.animation.UnfoldTaskAnimator;
import com.android.wm.shell.unfold.qualifier.UnfoldShellTransition;
import com.android.wm.shell.unfold.qualifier.UnfoldTransition;
import com.android.wm.shell.windowdecor.CaptionWindowDecorViewModel;
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel;
import com.android.wm.shell.windowdecor.FluidTaskResizer;
import com.android.wm.shell.windowdecor.MultiDisplayTaskMover;
import com.android.wm.shell.windowdecor.VeiledTaskResizer;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer;
import com.android.wm.shell.windowdecor.common.CaptionVisibilityHelper;
import com.android.wm.shell.windowdecor.common.DecorThemeUtil;
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader;
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoaderImpl;
import com.android.wm.shell.windowdecor.common.viewhost.DefaultWindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.common.viewhost.PooledWindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationPromoController;
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationTooltipController;
import com.android.wm.shell.windowdecor.tiling.DesktopTilingDecorViewModel;
import com.android.wm.shell.windowdecor.viewholder.AppHandleNotifier;

import com.google.android.msdl.domain.MSDLPlayer;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.MainCoroutineDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides dependencies from {@link com.android.wm.shell}, these dependencies are only accessible
 * from components within the WM subcomponent (can be explicitly exposed to the SysUIComponent, see
 * {@link WMComponent}).
 *
 * <p>This module only defines Shell dependencies for handheld SystemUI implementation. Common
 * dependencies should go into {@link WMShellBaseModule}.
 */
@Module(
        includes = {
                WMShellBaseModule.class,
                PipModule.class,
                ShellBackAnimationModule.class,
                LetterboxModule.class,
                PinnedLayerModule.class,
                DesktopModule.class,
                HandheldContainersModule.class,
                BubbleModule.class,
        })
public abstract class WMShellModule {

    //
    // Window decoration
    //

    @WMSingleton
    @Provides
    static WindowDecorViewModel provideWindowDecorViewModel(
            Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler,
            @ShellMainThread Choreographer mainChoreographer,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            IWindowManager windowManager,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            FocusTransitionObserver focusTransitionObserver,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            Optional<DesktopModeWindowDecorViewModel> desktopModeWindowDecorViewModel,
            DesktopState desktopState,
            DesktopConfig desktopConfig,
            InteractionJankMonitor interactionJankMonitor) {
        if (desktopModeWindowDecorViewModel.isPresent()) {
            return desktopModeWindowDecorViewModel.get();
        }
        return new CaptionWindowDecorViewModel(
                context,
                mainHandler,
                mainExecutor,
                bgExecutor,
                mainChoreographer,
                windowManager,
                shellInit,
                taskOrganizer,
                displayController,
                rootTaskDisplayAreaOrganizer,
                syncQueue,
                transitions,
                focusTransitionObserver,
                windowDecorViewHostSupplier,
                desktopState,
                desktopConfig,
                interactionJankMonitor);
    }

    @WMSingleton
    @Provides
    static AppToWebGenericLinksParser provideGenericLinksParser(
            Context context, @ShellMainThread ShellExecutor mainExecutor,
            DesktopConfig desktopConfig) {
        return new AppToWebGenericLinksParser(context, mainExecutor, desktopConfig);
    }

    @WMSingleton
    @Provides
    static AppToWebRepositoryImpl provideAppToWebRepositoryImpl(
            Context context, AssistContentRequester assistContentRequester,
            AppToWebGenericLinksParser appToWebGenericLinksParser,
            AppToWebDatastoreRepository appToWebDatastoreRepository,
            @ShellMainThread CoroutineScope mainScope,
            @ShellBackgroundThread CoroutineScope bgScope,
            ShellTaskOrganizer shellTaskOrganizer,
            LauncherApps launcherApps,
            ShellInit shellInit,
            ShellController shellController,
            ShellCommandHandler shellCommandHandler) {
        return new AppToWebRepositoryImpl(context, assistContentRequester,
                appToWebGenericLinksParser, appToWebDatastoreRepository, mainScope, bgScope,
                shellTaskOrganizer, launcherApps, shellInit, shellController, shellCommandHandler);
    }

    @WMSingleton
    @Provides
    static AppToWebRepository provideAppToWebRepository(
            AppToWebRepositoryImpl appToWebRepositoryImpl,
            Optional<DesktopModeWindowDecorViewModel> desktopModeWindowDecorViewModel
    ) {
        if (DesktopExperienceFlags.ENABLE_WINDOW_DECORATION_REFACTOR.isTrue()
                || desktopModeWindowDecorViewModel.isEmpty()) {
            return appToWebRepositoryImpl;
        }
        return desktopModeWindowDecorViewModel.get();
    }

    @WMSingleton
    @Provides
    static AppToWebDatastoreRepository provideAppToWebDatastoreRepository(
            Context context,
            @ShellBackgroundThread CoroutineScope bgCoroutineScope) {
        return new AppToWebDatastoreRepository(context, bgCoroutineScope);
    }

    @Provides
    static AssistContentRequester provideAssistContentRequester(
            Context context,
            @ShellMainThread ShellExecutor shellExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor) {
        return new AssistContentRequester(context, shellExecutor, bgExecutor);
    }

    @Provides
    static AdditionalSystemViewContainer.Factory provideAdditionalSystemViewContainerFactory() {
        return new AdditionalSystemViewContainer.Factory();
    }

    @WMSingleton
    @Provides
    static WindowDecorViewHostSupplier<WindowDecorViewHost> provideWindowDecorViewHostSupplier(
            @NonNull Context context,
            @ShellMainThread @NonNull CoroutineScope mainScope,
            @NonNull ShellInit shellInit,
            DisplayController displayController,
            DesktopState desktopState,
            DesktopConfig desktopConfig) {
        final int poolSize = desktopConfig.getWindowDecorScvhPoolSize();
        final int preWarmSize = desktopConfig.getWindowDecorPreWarmSize();
        if (desktopState.canEnterDesktopModeOrShowAppHandle() && poolSize > 0) {
            return new PooledWindowDecorViewHostSupplier(
                    context, mainScope, shellInit, desktopState, displayController, poolSize,
                    preWarmSize);
        }
        return new DefaultWindowDecorViewHostSupplier(mainScope);
    }

    //
    // Freeform
    //

    @WMSingleton
    @Provides
    @DynamicOverride
    static FreeformComponents provideFreeformComponents(
            FreeformTaskListener taskListener,
            FreeformTaskTransitionHandler transitionHandler,
            FreeformTaskTransitionObserver transitionObserver,
            FreeformTaskTransitionStarterInitializer transitionStarterInitializer) {
        return new FreeformComponents(
                taskListener,
                Optional.of(transitionHandler),
                Optional.of(transitionObserver),
                Optional.of(transitionStarterInitializer));
    }

    @WMSingleton
    @Provides
    static FreeformTaskListener provideFreeformTaskListener(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Optional<DesktopTasksController> desktopTasksController,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver,
            LaunchAdjacentController launchAdjacentController,
            WindowDecorViewModel windowDecorViewModel,
            Optional<TaskChangeListener> taskChangeListener,
            DesktopState desktopState) {
        return new FreeformTaskListener(
                context,
                shellInit,
                shellTaskOrganizer,
                desktopUserRepositories,
                desktopTasksController,
                desktopModeLoggerTransitionObserver,
                launchAdjacentController,
                windowDecorViewModel,
                taskChangeListener,
                desktopState);
    }

    @WMSingleton
    @Provides
    static FreeformTaskTransitionHandler provideFreeformTaskTransitionHandler(
            Transitions transitions,
            DisplayController displayController,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            @ShellAnimationThread Handler animHandler) {
        return new FreeformTaskTransitionHandler(
                transitions, displayController, mainExecutor, animExecutor, animHandler);
    }

    @WMSingleton
    @Provides
    static FreeformTaskTransitionObserver provideFreeformTaskTransitionObserver(
            ShellInit shellInit,
            Transitions transitions,
            WindowDecorViewModel windowDecorViewModel,
            Optional<TaskChangeListener> taskChangeListener,
            @DynamicOverride DesksOrganizer desksOrganizer,
            DesktopState desktopState,
            Optional<DesktopInOrderTransitionObserver> desktopInOrderTransitionObserver,
            Optional<FreeformFallbackTransitionObserver> freeformFallbackTransitionObserver) {
        return new FreeformTaskTransitionObserver(
                shellInit,
                transitions,
                windowDecorViewModel,
                taskChangeListener,
                desksOrganizer,
                desktopState,
                desktopInOrderTransitionObserver,
                freeformFallbackTransitionObserver);
    }

    @WMSingleton
    @Provides
    static FreeformTaskTransitionStarterInitializer provideFreeformTaskTransitionStarterInitializer(
            ShellInit shellInit,
            WindowDecorViewModel windowDecorViewModel,
            FreeformTaskTransitionHandler freeformTaskTransitionHandler,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler) {
        FreeformTaskTransitionStarter transitionStarter;
        if (desktopMixedTransitionHandler.isPresent()) {
            transitionStarter = desktopMixedTransitionHandler.get();
        } else {
            transitionStarter = freeformTaskTransitionHandler;
        }
        return new FreeformTaskTransitionStarterInitializer(
                shellInit, windowDecorViewModel, transitionStarter);
    }

    //
    // One handed mode
    //

    // Needs the shell main handler for ContentObserver callbacks
    @WMSingleton
    @Provides
    @DynamicOverride
    static OneHandedController provideOneHandedController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            WindowManager windowManager,
            DisplayController displayController,
            DisplayLayout displayLayout,
            TaskStackListenerImpl taskStackListener,
            UiEventLogger uiEventLogger,
            InteractionJankMonitor jankMonitor,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler) {
        return OneHandedController.create(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                windowManager,
                displayController,
                displayLayout,
                taskStackListener,
                jankMonitor,
                uiEventLogger,
                mainExecutor,
                mainHandler);
    }

    //
    // Scroll To Top
    //

    @WMSingleton
    @Provides
    static ScrollToTopController provideScrollToTopController(
            @ShellMainThread ShellExecutor mainExecutor,
            IWindowManager windowManager,
            Optional<SplitScreenController> splitScreenController) {
        return new ScrollToTopController(mainExecutor, windowManager,
                splitScreenController);
    }



    //
    // Splitscreen
    //

    @WMSingleton
    @Provides
    @DynamicOverride
    static SplitScreenController provideSplitScreenController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DisplayController displayController,
            DisplayImeController displayImeController,
            DisplayInsetsController displayInsetsController,
            DragAndDropController dragAndDropController,
            Transitions transitions,
            TransactionPool transactionPool,
            IconProvider iconProvider,
            Optional<RecentTasksController> recentTasks,
            LaunchAdjacentController launchAdjacentController,
            Optional<WindowDecorViewModel> windowDecorViewModel,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            MultiInstanceHelper multiInstanceHelper,
            SplitState splitState,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler,
            RootDisplayAreaOrganizer rootDisplayAreaOrganizer,
            DesktopState desktopState,
            IActivityTaskManager activityTaskManager,
            MSDLPlayer msdlPlayer,
            Optional<BubbleController> bubbleController,
            Optional<ClientFullscreenRequestController> clientFullscreenRequestController,
            Optional<PackageUpdateController> packageUpdateController) {
        return new SplitScreenController(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                shellTaskOrganizer,
                syncQueue,
                rootTaskDisplayAreaOrganizer,
                displayController,
                displayImeController,
                displayInsetsController,
                dragAndDropController,
                transitions,
                transactionPool,
                iconProvider,
                recentTasks,
                launchAdjacentController,
                windowDecorViewModel,
                desktopTasksController,
                desktopUserRepositories,
                null /* stageCoordinator */,
                multiInstanceHelper,
                splitState,
                mainExecutor,
                mainHandler,
                rootDisplayAreaOrganizer,
                desktopState,
                activityTaskManager,
                msdlPlayer,
                bubbleController,
                clientFullscreenRequestController,
                packageUpdateController);
    }

    //
    // Transitions
    //

    @WMSingleton
    @DynamicOverride
    @Provides
    static MixedTransitionHandler provideMixedTransitionHandler(
            ShellInit shellInit,
            Optional<SplitScreenController> splitScreenOptional,
            @Nullable PipTransitionController pipTransitionController,
            PipScheduler pipScheduler,
            Optional<NormalAppLayerHandler> normalAppLayerHandler,
            Optional<PinnedLayerHandler> pinnedLayerHandler,
            Optional<RecentsTransitionHandler> recentsTransitionHandler,
            KeyguardTransitionHandler keyguardTransitionHandler,
            Optional<DesktopTasksController> desktopTasksController,
            DesktopTasksTransitionHandler desktopTasksTransitionHandler,
            Optional<UnfoldTransitionHandler> unfoldHandler,
            Optional<ActivityEmbeddingController> activityEmbeddingController,
            BubbleTransitions bubbleTransitions,
            BubbleHelper bubbleHelper,
            Transitions transitions) {
        return new DefaultMixedHandler(
                shellInit,
                transitions,
                splitScreenOptional,
                pipTransitionController,
                PipFlags.isPip2ExperimentEnabled() ? Optional.of(pipScheduler) : Optional.empty(),
                normalAppLayerHandler.orElse(null),
                pinnedLayerHandler.orElse(null),
                recentsTransitionHandler,
                keyguardTransitionHandler,
                desktopTasksController,
                desktopTasksTransitionHandler,
                unfoldHandler,
                activityEmbeddingController,
                bubbleTransitions,
                bubbleHelper);
    }

    @WMSingleton
    @Provides
    static RecentsTransitionHandler provideRecentsTransitionHandler(
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            Transitions transitions,
            Optional<RecentTasksController> recentTasksController,
            HomeTransitionObserver homeTransitionObserver,
            DisplayController displayController,
            @DynamicOverride DesksOrganizer desksOrganizer,
            BubbleHelper bubbleHelper) {
        return new RecentsTransitionHandler(
                shellInit,
                shellTaskOrganizer,
                transitions,
                recentTasksController.orElse(null),
                homeTransitionObserver,
                displayController,
                desksOrganizer,
                bubbleHelper);
    }

    //
    // Unfold transition
    //

    @WMSingleton
    @Provides
    @DynamicOverride
    static UnfoldAnimationController provideUnfoldAnimationController(
            Optional<ShellUnfoldProgressProvider> progressProvider,
            TransactionPool transactionPool,
            Optional<SplitTaskUnfoldAnimator> splitAnimator,
            FullscreenUnfoldTaskAnimator fullscreenAnimator,
            Lazy<Optional<UnfoldTransitionHandler>> unfoldTransitionHandler,
            ShellInit shellInit,
            @ShellMainThread ShellExecutor mainExecutor) {
        final List<UnfoldTaskAnimator> animators = new ArrayList<>();
        splitAnimator.ifPresent(animators::add);
        animators.add(fullscreenAnimator);

        return new UnfoldAnimationController(
                shellInit,
                transactionPool,
                progressProvider.get(),
                animators,
                unfoldTransitionHandler,
                mainExecutor);
    }

    @Provides
    static FullscreenUnfoldTaskAnimator provideFullscreenUnfoldTaskAnimator(
            Context context,
            UnfoldBackgroundController unfoldBackgroundController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController) {
        return new FullscreenUnfoldTaskAnimator(
                context, unfoldBackgroundController, shellController, displayInsetsController);
    }

    @Provides
    static Optional<SplitTaskUnfoldAnimator> provideSplitTaskUnfoldAnimatorBase(
            Context context,
            UnfoldBackgroundController backgroundController,
            ShellController shellController,
            @ShellMainThread ShellExecutor executor,
            Lazy<Optional<SplitScreenController>> splitScreenOptional,
            DisplayInsetsController displayInsetsController) {
        if (!ActivityTaskManager.supportsSplitScreenMultiWindow(context)) {
            return Optional.empty();
        }
        // TODO(b/238217847): The lazy reference here causes some dependency issues since it
        // immediately registers a listener on that controller on init.  We should reference the
        // controller directly once we refactor ShellTaskOrganizer to not depend on the unfold
        // animation controller directly.
        return Optional.of(new SplitTaskUnfoldAnimator(
                context,
                executor,
                splitScreenOptional,
                shellController,
                backgroundController,
                displayInsetsController));
    }

    @WMSingleton
    @UnfoldShellTransition
    @Binds
    abstract SplitTaskUnfoldAnimator provideShellSplitTaskUnfoldAnimator(
            SplitTaskUnfoldAnimator splitTaskUnfoldAnimator);

    @WMSingleton
    @UnfoldTransition
    @Binds
    abstract SplitTaskUnfoldAnimator provideSplitTaskUnfoldAnimator(
            SplitTaskUnfoldAnimator splitTaskUnfoldAnimator);

    @WMSingleton
    @Provides
    @DynamicOverride
    static UnfoldTransitionHandler provideUnfoldTransitionHandler(
            Optional<ShellUnfoldProgressProvider> progressProvider,
            FullscreenUnfoldTaskAnimator animator,
            Optional<SplitTaskUnfoldAnimator> unfoldAnimatorOptional,
            TransactionPool transactionPool,
            Transitions transitions,
            @ShellMainThread ShellExecutor executor,
            @ShellMainThread Handler handler,
            ShellInit shellInit,
            Optional<BubbleTaskUnfoldTransitionMerger> bubbleTaskUnfoldTransitionMerger) {
        return new UnfoldTransitionHandler(
                shellInit,
                progressProvider.get(),
                animator,
                unfoldAnimatorOptional,
                transactionPool,
                executor,
                handler,
                transitions,
                bubbleTaskUnfoldTransitionMerger);
    }

    @WMSingleton
    @Provides
    static UnfoldBackgroundController provideUnfoldBackgroundController(Context context) {
        return new UnfoldBackgroundController(context);
    }

    //
    // Desktop mode (optional feature)
    //

    @WMSingleton
    @Provides
    @DynamicOverride
    static DesksOrganizer provideDesksOrganizer(
            @NonNull ShellInit shellInit,
            @NonNull ShellCommandHandler shellCommandHandler,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull LaunchAdjacentController launchAdjacentController,
            @NonNull RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer) {
        return new RootTaskDesksOrganizer(shellInit, shellCommandHandler, shellTaskOrganizer,
                launchAdjacentController, rootTaskDisplayAreaOrganizer);
    }

    @WMSingleton
    @Provides
    @DynamicOverride
    static DesktopMode provideDesktopMode(
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopFirstListenerManager> desktopFirstListenerManager,
            DesktopHomeScreenPeekController desktopHomeScreenPeekController,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new DesktopModeImpl(
                desktopTasksController,
                desktopFirstListenerManager,
                desktopHomeScreenPeekController,
                mainExecutor);
    }

    @WMSingleton
    @Provides
    @DynamicOverride
    static DesktopTasksController provideDesktopTasksController(
            Context context,
            DesktopAnimationConfiguration desktopAnimationConfiguration,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            DisplayController displayController,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DragAndDropController dragAndDropController,
            Transitions transitions,
            KeyguardManager keyguardManager,
            ReturnToDragStartAnimator returnToDragStartAnimator,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler,
            EnterDesktopTaskTransitionHandler enterDesktopTransitionHandler,
            ExitDesktopTaskTransitionHandler exitDesktopTransitionHandler,
            DesktopModeDragAndDropTransitionHandler desktopModeDragAndDropTransitionHandler,
            ToggleResizeDesktopTaskTransitionHandler toggleResizeDesktopTaskTransitionHandler,
            DragToDesktopTransitionHandler dragToDesktopTransitionHandler,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopRepositoryInitializer desktopRepositoryInitializer,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            DesktopFullscreenRequestHandler desktopFullscreenRequestHandler,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver,
            LaunchAdjacentController launchAdjacentController,
            RecentsTransitionHandler recentsTransitionHandler,
            MultiInstanceHelper multiInstanceHelper,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread CoroutineScope mainScope,
            @ShellMainThreadImmediate CoroutineScope mainScopeImmediate,
            @ShellMainThread Handler mainHandler,
            @ShellDesktopThread ShellExecutor desktopExecutor,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            Optional<RecentTasksController> recentTasksController,
            InteractionJankMonitor interactionJankMonitor,
            InputManager inputManager,
            FocusTransitionObserver focusTransitionObserver,
            DesktopModeEventLogger desktopModeEventLogger,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            Optional<BubbleController> bubbleController,
            OverviewToDesktopTransitionObserver overviewToDesktopTransitionObserver,
            @DynamicOverride DesksOrganizer desksOrganizer,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            UserProfileContexts userProfileContexts,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            WindowDragTransitionHandler windowDragTransitionHandler,
            DeskSwitchTransitionHandler deskSwitchTransitionHandler,
            DesktopModeMoveToDisplayTransitionHandler moveToDisplayTransitionHandler,
            HomeIntentProvider homeIntentProvider,
            ShellDesktopState shellDesktopState,
            DesktopConfig desktopConfig,
            VisualIndicatorUpdateScheduler visualIndicatorUpdateScheduler,
            TaskSnapshotManager taskSnapshotManager,
            TransactionPool transactionPool,
            PipTransitionState pipTransitionState,
            LockTaskChangeListener lockTaskChangeListener,
            LauncherApps launcherApps,
            TransitionStateHolder transitionStateHolder,
            DesksController desksController,
            Optional<DesktopTasksTransitionObserver> desktopTasksTransitionObserver,
            SnapController snapController,
            DesktopRemoteListener desktopRemoteListener,
            DesktopWallpaperActivityUtils desktopWallpaperActivityUtils) {
        return new DesktopTasksController(
                context,
                desktopAnimationConfiguration,
                shellInit,
                shellCommandHandler,
                shellController,
                displayController,
                shellTaskOrganizer,
                syncQueue,
                rootTaskDisplayAreaOrganizer,
                dragAndDropController,
                transitions,
                keyguardManager,
                returnToDragStartAnimator,
                desktopMixedTransitionHandler.get(),
                enterDesktopTransitionHandler,
                exitDesktopTransitionHandler,
                desktopModeDragAndDropTransitionHandler,
                toggleResizeDesktopTaskTransitionHandler,
                dragToDesktopTransitionHandler,
                desktopImmersiveController.get(),
                desktopFullscreenRequestHandler,
                desktopUserRepositories,
                desktopRepositoryInitializer,
                recentsTransitionHandler,
                multiInstanceHelper,
                mainExecutor,
                mainScope,
                mainScopeImmediate,
                desktopExecutor,
                desktopTasksLimiter,
                recentTasksController.orElse(null),
                interactionJankMonitor,
                mainHandler,
                focusTransitionObserver,
                desktopModeEventLogger,
                desktopModeUiEventLogger,
                desktopWallpaperActivityTokenProvider,
                bubbleController,
                overviewToDesktopTransitionObserver,
                desksOrganizer,
                desksTransitionObserver.get(),
                userProfileContexts,
                desktopModeCompatPolicy,
                windowDragTransitionHandler,
                deskSwitchTransitionHandler,
                moveToDisplayTransitionHandler,
                homeIntentProvider,
                shellDesktopState,
                desktopConfig,
                visualIndicatorUpdateScheduler,
                taskSnapshotManager,
                transactionPool,
                PipFlags.isPip2ExperimentEnabled() ? Optional.of(pipTransitionState)
                        : Optional.empty(),
                lockTaskChangeListener,
                launcherApps,
                transitionStateHolder,
                desksController,
                desktopTasksTransitionObserver.get(),
                snapController,
                desktopRemoteListener,
                desktopWallpaperActivityUtils);
    }

    @WMSingleton
    @Provides
    static DesktopTilingDecorViewModel provideDesktopTilingViewModel(Context context,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellMainThreadImmediate CoroutineScope mainImmediateScope,
            @ShellBackgroundThread CoroutineScope bgScope,
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            ShellTaskOrganizer shellTaskOrganizer,
            ToggleResizeDesktopTaskTransitionHandler toggleResizeDesktopTaskTransitionHandler,
            ReturnToDragStartAnimator returnToDragStartAnimator,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopModeEventLogger desktopModeEventLogger,
            WindowDecorTaskResourceLoader windowDecorTaskResourceLoader,
            FocusTransitionObserver focusTransitionObserver,
            @ShellMainThread ShellExecutor mainExecutor,
            DesktopState desktopState,
            ShellInit shellInit,
            ShellController shellController,
            InteractionJankMonitor interactionJankMonitor
    ) {
        return new DesktopTilingDecorViewModel(
                context,
                mainDispatcher,
                mainImmediateScope,
                bgScope,
                displayController,
                rootTaskDisplayAreaOrganizer,
                syncQueue,
                transitions,
                shellTaskOrganizer,
                toggleResizeDesktopTaskTransitionHandler,
                returnToDragStartAnimator,
                desktopUserRepositories,
                desktopModeEventLogger,
                windowDecorTaskResourceLoader,
                focusTransitionObserver,
                mainExecutor,
                desktopState,
                shellInit,
                shellController,
                interactionJankMonitor
        );
    }

    @WMSingleton
    @Provides
    static Optional<TaskChangeListener> provideDesktopTaskChangeListener(
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopState desktopState,
            ShellController shellController,
            Optional<PinnedLayerController> pinnedLayerController,
            @DynamicOverride DesksOrganizer desksOrganizer) {
        if (desktopState.canEnterDesktopMode()) {
            return Optional.of(
                    new DesktopTaskChangeListener(
                            desktopUserRepositories,
                            desktopState,
                            shellController,
                            pinnedLayerController.orElse(null),
                            desksOrganizer));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopInOrderTransitionObserver> provideDesktopInOrderTransitionObserver(
            ShellInit shellInit,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            FocusTransitionObserver focusTransitionObserver,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            DesktopState desktopState,
            Optional<DesktopImeHandler> desktopImeHandler,
            Optional<DesktopBackNavTransitionObserver> desktopBackNavTransitionObserver,
            DesktopModeLoggerTransitionObserver desktopModeLoggerTransitionObserver) {
        if (desktopState.canEnterDesktopMode()) {
            return Optional.of(new DesktopInOrderTransitionObserver(
                    desktopImmersiveController,
                    focusTransitionObserver,
                    desksTransitionObserver,
                    desktopImeHandler,
                    desktopBackNavTransitionObserver,
                    desktopModeLoggerTransitionObserver));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopTasksLimiter> provideDesktopTasksLimiter(
            Transitions transitions,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            ShellTaskOrganizer shellTaskOrganizer,
            @DynamicOverride DesksOrganizer desksOrganizer,
            DesktopConfig desktopConfig,
            DesktopState desktopState,
            SnapController snapController,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        int maxTaskLimit = desktopConfig.getMaxTaskLimit();
        return Optional.of(
                new DesktopTasksLimiter(
                        transitions,
                        desktopUserRepositories,
                        shellTaskOrganizer,
                        desksOrganizer,
                        desktopMixedTransitionHandler.get(),
                        snapController,
                        maxTaskLimit <= 0 ? null : maxTaskLimit));
    }

    @WMSingleton
    @Provides
    static Optional<DesktopImmersiveController> provideDesktopImmersiveController(
            ShellInit shellInit,
            Transitions transitions,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DisplayController displayController,
            ShellTaskOrganizer shellTaskOrganizer,
            ShellCommandHandler shellCommandHandler,
            DesktopState desktopState) {
        if (desktopState.canEnterDesktopModeOrShowAppHandle()) {
            return Optional.of(
                    new DesktopImmersiveController(
                            shellInit,
                            transitions,
                            desktopUserRepositories,
                            displayController,
                            shellTaskOrganizer,
                            shellCommandHandler));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static ReturnToDragStartAnimator provideReturnToDragStartAnimator(
            InteractionJankMonitor interactionJankMonitor) {
        return new ReturnToDragStartAnimator(interactionJankMonitor);
    }

    @WMSingleton
    @Provides
    static DragToDesktopTransitionHandler provideDragToDesktopTransitionHandler(
            Context context,
            Transitions transitions,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            @DynamicOverride DesksOrganizer desksOrganizer,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            InteractionJankMonitor interactionJankMonitor,
            Optional<BubbleController> bubbleController,
            DesktopState desktopState,
            DesktopConfig desktopConfig,
            DisplayController displayController) {
        return new SpringDragToDesktopTransitionHandler(
                context, transitions, rootTaskDisplayAreaOrganizer, desksOrganizer,
                desktopUserRepositories, interactionJankMonitor, bubbleController, desktopState,
                desktopConfig, displayController);
    }

    @WMSingleton
    @Provides
    static DeskSwitchTransitionHandler provideDeskSwitchTransitionHandler(
            Context context,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopState desktopState,
            Transitions transitions,
            DisplayController displayController,
            @ShellMainThread Handler shellMainHandler,
            InteractionJankMonitor interactionJankMonitor,
            SnapController snapController
    ) {
        return new DeskSwitchTransitionHandler(context, desktopUserRepositories, desktopState,
                transitions, displayController, shellMainHandler, interactionJankMonitor,
                snapController);

    }

    @WMSingleton
    @Provides
    static Optional<DisplayDisconnectTransitionHandler> provideDisplayDisconnectTransitionHandler(
            ShellInit shellInit, Transitions transitions,
            Optional<SplitScreenController> splitScreenOptional,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<FullscreenDisconnectHandler> fullscreenDisconnectHandler,
            Optional<PinnedLayerController> pinnedLayerController,
            Optional<PipDisplayDisconnectHandler> pipDisplayDisconnectHandler) {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue()) {
            return Optional.empty();
        } else {
            return Optional.of(
                    new DisplayDisconnectTransitionHandler(transitions, shellInit,
                            splitScreenOptional, desktopTasksController,
                            fullscreenDisconnectHandler, pinnedLayerController,
                            pipDisplayDisconnectHandler
                    )
            );
        }
    }

    @WMSingleton
    @Provides
    static Optional<PipDisplayDisconnectHandler> providePipDisplayDisconnectHandler(
            PipScheduler pipScheduler,
            PipTransitionState pipTransitionState,
            PipBoundsState pipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            ShellDesktopState desktopState,
            RootTaskDisplayAreaOrganizer taskDisplayAreaOrganizer,
            PipDisplayTransferHandler pipDisplayTransferHandler,
            PipDisplayReconnectHandler pipDisplayReconnectHandler
    ) {
        if (!com.android.window.flags.Flags.enableDisplayDisconnectPip()
                || !PipFlags.isPip2ExperimentEnabled()) {
            return Optional.empty();
        } else {
            return Optional.of(
                    new PipDisplayDisconnectHandler(
                            pipScheduler,
                            pipTransitionState,
                            pipBoundsState,
                            pipDisplayLayoutState,
                            desktopState,
                            taskDisplayAreaOrganizer,
                            pipDisplayTransferHandler,
                            pipDisplayReconnectHandler
                    )
            );
        }
    }

    @WMSingleton
    @Provides
    static PipDisplayReconnectHandler providePipDisplayReconnectHandler(
            KeyguardManager keyguardManager,
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            ShellController shellController,
            PipTransitionState pipState,
            PipDisplayTransferHandler pipDisplayTransferHandler,
            ShellInit shellInit
    ) {
        return new PipDisplayReconnectHandler(
                keyguardManager,
                displayController,
                rootTaskDisplayAreaOrganizer,
                shellController,
                pipState,
                pipDisplayTransferHandler,
                shellInit
        );
    }

    @WMSingleton
    @Provides
    static Optional<FullscreenDisconnectHandler> provideFullscreenDisconnectHandler(
            ShellTaskOrganizer shellTaskOrganizer,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            FullscreenReconnectHandler fullscreenReconnectHandler
    ) {
        if (!com.android.window.flags.Flags.enableDisplayDisconnectFullscreen()) {
            return Optional.empty();
        } else {
            return Optional.of(
                    new FullscreenDisconnectHandler(shellTaskOrganizer,
                            rootTaskDisplayAreaOrganizer, fullscreenReconnectHandler));
        }
    }

    @WMSingleton
    @Provides
    static FullscreenReconnectHandler provideFullscreenReconnectHandler(
            KeyguardManager keyguardManager,
            DisplayController displayController,
            Transitions transitions,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<RecentTasksController> recentTasksController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DesktopState desktopState,
            Optional<SplitScreenController> splitScreenController,
            ShellController shellController,
            ShellInit shellInit
    ) {
        return new FullscreenReconnectHandler(keyguardManager, displayController, transitions,
                shellTaskOrganizer, recentTasksController.orElse(null),
                rootTaskDisplayAreaOrganizer, desktopState, splitScreenController, shellController,
                shellInit);
    }

    @WMSingleton
    @Provides
    static DesktopWallpaperActivityTokenProvider provideDesktopWallpaperActivityTokenProvider() {
        return new DesktopWallpaperActivityTokenProvider();
    }

    @WMSingleton
    @Provides
    static WindowDragTransitionHandler provideWindowDragTransitionHandler(
            MultiDisplayDragMoveIndicatorController multiDisplayDragMoveIndicatorController,
            InteractionJankMonitor interactionJankMonitor) {
        return new WindowDragTransitionHandler(multiDisplayDragMoveIndicatorController,
                interactionJankMonitor);
    }

    @WMSingleton
    @Provides
    static DesktopModeMoveToDisplayTransitionHandler provideMoveToDisplayTransitionHandler(
            InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler shellMainHandler,
            DisplayController displayController) {
        return new DesktopModeMoveToDisplayTransitionHandler(
                new SurfaceControl.Transaction(),
                interactionJankMonitor,
                shellMainHandler,
                displayController);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopModeKeyGestureHandler> provideDesktopModeKeyGestureHandler(
            Context context,
            Optional<DesktopModeWindowDecorViewModel> desktopModeWindowDecorViewModel,
            Optional<DesktopTasksController> desktopTasksController,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            InputManager inputManager,
            ShellTaskOrganizer shellTaskOrganizer,
            FocusTransitionObserver focusTransitionObserver,
            @ShellMainThread ShellExecutor mainExecutor,
            DisplayController displayController,
            DesktopState desktopState,
            AccessibilityManager accessibilityManager,
            ShellController shellController,
            KeyguardManager keyguardManager) {
        if (desktopState.canEnterDesktopMode()) {
            return Optional.of(new DesktopModeKeyGestureHandler(context,
                    desktopModeWindowDecorViewModel, desktopTasksController,
                    desktopUserRepositories, inputManager, shellTaskOrganizer,
                    focusTransitionObserver, mainExecutor, displayController, desktopState,
                    accessibilityManager, shellController, keyguardManager));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static QuitFocusedAppKeyGestureHandler provideQuitFocusedAppKeyGestureHandler(
            Context context,
            InputManager inputManager,
            DisplayController displayController,
            LockTaskChangeListener lockTaskChangeListener,
            Optional<DesktopModeKeyGestureHandler> desktopModeKeyGestureHandler,
            IActivityTaskManager activityTaskManagerService,
            FocusTransitionObserver focusTransitionObserver,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new QuitFocusedAppKeyGestureHandler(context, inputManager, displayController,
                lockTaskChangeListener, desktopModeKeyGestureHandler, activityTaskManagerService,
                focusTransitionObserver, mainExecutor);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopModeWindowDecorViewModel> provideDesktopModeWindowDecorViewModel(
            Context context,
            @ShellMainThread ShellExecutor shellExecutor,
            @ShellMainThread Handler mainHandler,
            @ShellMainThread Choreographer mainChoreographer,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellMainThread CoroutineScope mainScope,
            @ShellMainThreadImmediate CoroutineScope mainImmediateScope,
            @ShellBackgroundThread CoroutineScope bgScope,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            IWindowManager windowManager,
            ShellTaskOrganizer taskOrganizer,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DisplayController displayController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            InteractionJankMonitor interactionJankMonitor,
            AppToWebGenericLinksParser genericLinksParser,
            AppToWebRepositoryImpl appToWebRepository,
            AssistContentRequester assistContentRequester,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            MultiInstanceHelper multiInstanceHelper,
            AppHandleEducationController appHandleEducationController,
            CaptionVisibilityHelper captionVisibilityHelper,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            Optional<DesktopActivityOrientationChangeHandler> activityOrientationChangeHandler,
            FocusTransitionObserver focusTransitionObserver,
            DesktopModeEventLogger desktopModeEventLogger,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            WindowDecorTaskResourceLoader taskResourceLoader,
            PerDisplayRecentsTransitionStateListener perDisplayRecentsTransitionStateListener,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DesktopTilingDecorViewModel desktopTilingDecorViewModel,
            MultiDisplayDragMoveIndicatorController multiDisplayDragMoveIndicatorController,
            Optional<CompatUIHandler> compatUI,
            @DynamicOverride DesksOrganizer desksOrganizer,
            ShellDesktopState shelldesktopState,
            DesktopConfig desktopConfig,
            UserProfileContexts userProfileContexts,
            LockTaskChangeListener lockTaskChangeListener,
            Optional<PinnedLayerController> pinnedLayerController,
            Optional<PinnedLayerUiState> pinnedLayerUiState,
            FluidTaskResizer fluidTaskResizer,
            VeiledTaskResizer veiledTaskResizer,
            MultiDisplayTaskMover multiDisplayTaskMover,
            SnapController snapController,
            DecorThemeUtil.Factory decorThemeUtilFactory
    ) {
        if (!shelldesktopState.canEnterDesktopModeOrShowAppHandle()) {
            return Optional.empty();
        }
        return Optional.of(new DesktopModeWindowDecorViewModel(context, shellExecutor, mainHandler,
                mainChoreographer, mainDispatcher, mainScope, mainImmediateScope, bgScope,
                bgExecutor, shellInit, shellCommandHandler, windowManager,
                taskOrganizer, desktopUserRepositories, displayController, shellController,
                displayInsetsController, syncQueue, transitions, desktopTasksController,
                desktopImmersiveController.get(),
                rootTaskDisplayAreaOrganizer, interactionJankMonitor, genericLinksParser,
                appToWebRepository, assistContentRequester, windowDecorViewHostSupplier,
                multiInstanceHelper, appHandleEducationController,
                captionVisibilityHelper, windowDecorCaptionRepository,
                activityOrientationChangeHandler, focusTransitionObserver, desktopModeEventLogger,
                desktopModeUiEventLogger, taskResourceLoader,
                perDisplayRecentsTransitionStateListener,
                desktopModeCompatPolicy, desktopTilingDecorViewModel,
                multiDisplayDragMoveIndicatorController, compatUI.orElse(null),
                desksOrganizer, shelldesktopState, desktopConfig, userProfileContexts,
                lockTaskChangeListener, pinnedLayerController.orElse(null),
                pinnedLayerUiState.orElse(null), fluidTaskResizer, veiledTaskResizer,
                multiDisplayTaskMover, snapController, decorThemeUtilFactory));
    }

    @WMSingleton
    @Provides
    static LockTaskChangeListener provideLockTaskChangeListener(ShellInit shellInit,
            TaskStackListenerImpl taskStackListenerImpl) {
        return new LockTaskChangeListener(shellInit, taskStackListenerImpl);
    }

    @Provides
    static FluidTaskResizer provideFluidTaskResizer(
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            DesktopState desktopState) {
        return new FluidTaskResizer(taskOrganizer, displayController, desktopState);
    }

    @Provides
    static VeiledTaskResizer provideVeiledTaskResizer(
            DisplayController displayController,
            DesktopState desktopState) {
        return new VeiledTaskResizer(displayController, desktopState);
    }

    @Provides
    static MultiDisplayTaskMover provideMultiDisplayTaskMover(
            DisplayController displayController,
            MultiDisplayDragMoveIndicatorController indicatorController) {
        return new MultiDisplayTaskMover(
                displayController,
                SurfaceControl.Transaction::new,
                indicatorController);
    }

    @WMSingleton
    @Provides
    static MultiDisplayDragMoveIndicatorController providesMultiDisplayDragMoveIndicatorController(
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            MultiDisplayDragMoveIndicatorSurface.Factory
                    multiDisplayDragMoveIndicatorSurfaceFactory,
            ShellDesktopState shellDesktopState) {
        return new MultiDisplayDragMoveIndicatorController(
                displayController, rootTaskDisplayAreaOrganizer,
                multiDisplayDragMoveIndicatorSurfaceFactory, shellDesktopState);
    }

    @WMSingleton
    @Provides
    static MultiDisplayDragMoveIndicatorSurface.Factory
                providesMultiDisplayDragMoveIndicatorSurfaceFactory() {
        return new MultiDisplayDragMoveIndicatorSurface.Factory();
    }

    @WMSingleton
    @Provides
    static ShellDesktopState provideShellDesktopState(
            DesktopState desktopState,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            FocusTransitionObserver focusTransitionObserver,
            ShellController shellController,
            ShellTaskOrganizer shellTaskOrganizer) {
        return new ShellDesktopStateImpl(desktopState, desktopUserRepositories,
                focusTransitionObserver, shellController, shellTaskOrganizer);
    }

    @WMSingleton
    @Provides
    static CaptionVisibilityHelper provideCaptionVisibilityHelper(
            @NonNull DisplayController displayController,
            @NonNull DesktopModeCompatPolicy desktopModeCompatPolicy,
            @NonNull DesktopState desktopState,
            Optional<BubbleController> bubbleController,
            LockTaskChangeListener lockTaskChangeListener) {
        return new CaptionVisibilityHelper(displayController,
                desktopModeCompatPolicy, desktopState, bubbleController, lockTaskChangeListener);
    }

    @WMSingleton
    @Provides
    static WindowDecorTaskResourceLoader provideWindowDecorTaskResourceLoader(
            @NonNull Context context, @NonNull ShellInit shellInit,
            @NonNull ShellController shellController,
            @ShellMainThread Handler mainHandler,
            @ShellMainThread CoroutineScope mainScope,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread MainCoroutineDispatcher bgDispatcher,
            @NonNull ShellCommandHandler shellCommandHandler,
            @NonNull UserProfileContexts userProfileContexts) {
        return new WindowDecorTaskResourceLoaderImpl(context, shellInit, shellController,
                mainHandler, mainScope, mainDispatcher.getImmediate(), bgDispatcher,
                shellCommandHandler, userProfileContexts);
    }

    @WMSingleton
    @Provides
    static Optional<SystemModalsTransitionHandler> provideSystemModalsTransitionHandler(
            Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            ShellInit shellInit,
            Transitions transitions,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DesktopState desktopState) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        return Optional.of(
                new SystemModalsTransitionHandler(
                        context, mainExecutor, animExecutor, shellInit, transitions,
                        desktopUserRepositories, desktopModeCompatPolicy));
    }

    @WMSingleton
    @Provides
    static Optional<FreeformFallbackTransitionHandler> provideFreeformFallbackTransitionHandler(
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DesktopState desktopState) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        return Optional.of(
                new FreeformFallbackTransitionHandler(desktopUserRepositories));
    }

    @WMSingleton
    @Provides
    static EnterDesktopTaskTransitionHandler provideEnterDesktopModeTaskTransitionHandler(
            Transitions transitions,
            Context context,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            InteractionJankMonitor interactionJankMonitor,
            LatencyTracker latencyTracker) {
        return new EnterDesktopTaskTransitionHandler(
                transitions, context, interactionJankMonitor, latencyTracker);
    }

    @WMSingleton
    @Provides
    static ToggleResizeDesktopTaskTransitionHandler provideToggleResizeDesktopTaskTransitionHandler(
            Transitions transitions, InteractionJankMonitor interactionJankMonitor,
            Optional<DesktopTasksTransitionObserver> desktopTasksTransitionObserver) {
        return new ToggleResizeDesktopTaskTransitionHandler(transitions, interactionJankMonitor,
                desktopTasksTransitionObserver);
    }

    @WMSingleton
    @Provides
    static ExitDesktopTaskTransitionHandler provideExitDesktopTaskTransitionHandler(
            Transitions transitions,
            Context context,
            InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler handler,
            DisplayController displayController) {
        return new ExitDesktopTaskTransitionHandler(
                transitions, context, interactionJankMonitor, handler, displayController);
    }

    @WMSingleton
    @Provides
    static CloseDesktopTaskTransitionHandler provideCloseDesktopTaskTransitionHandler(
            Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            @ShellAnimationThread Handler animHandler) {
        return new CloseDesktopTaskTransitionHandler(context, mainExecutor, animExecutor,
                animHandler);
    }

    @WMSingleton
    @Provides
    static DesktopMinimizationTransitionHandler provideDesktopMinimizationTransitionHandler(
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            DisplayController displayController,
            @ShellAnimationThread Handler mainHandler) {
        return new DesktopMinimizationTransitionHandler(mainExecutor, animExecutor,
                displayController, mainHandler);
    }

    @WMSingleton
    @Provides
    static DesktopModeDragAndDropTransitionHandler provideDesktopModeDragAndDropTransitionHandler(
            Transitions transitions, DesktopModeDragAndDropAnimatorHelper animatorHelper) {
        return new DesktopModeDragAndDropTransitionHandler(transitions, animatorHelper);
    }

    @WMSingleton
    @Provides
    static DesktopModeDragAndDropAnimatorHelper provideDesktopModeDragAndDropAnimatorHelper(
            Context context) {
        return new DesktopModeDragAndDropAnimatorHelper(context, SurfaceControl.Transaction::new);
    }

    @WMSingleton
    @Provides
    @DynamicOverride
    static DesktopUserRepositories provideDesktopUserRepositories(
            ShellInit shellInit,
            ShellController shellController,
            DesktopPersistentRepository desktopPersistentRepository,
            DesktopRepositoryInitializer desktopRepositoryInitializer,
            @ShellMainThread CoroutineScope mainScope,
            @ShellBackgroundThread CoroutineScope bgScope,
            UserManager userManager,
            DesktopState desktopState,
            DesktopConfig desktopConfig
    ) {
        return new DesktopUserRepositories(shellInit, shellController,
                desktopPersistentRepository,
                desktopRepositoryInitializer,
                mainScope, bgScope, userManager, desktopState, desktopConfig);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopActivityOrientationChangeHandler> provideActivityOrientationHandler(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            TaskStackListenerImpl taskStackListener,
            ToggleResizeDesktopTaskTransitionHandler toggleResizeDesktopTaskTransitionHandler,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            DisplayController displayController,
            DesktopState desktopState) {
        if (desktopState.canEnterDesktopMode()) {
            return Optional.of(
                    new DesktopActivityOrientationChangeHandler(
                            context,
                            shellInit,
                            shellTaskOrganizer,
                            taskStackListener,
                            toggleResizeDesktopTaskTransitionHandler,
                            desktopUserRepositories,
                            displayController,
                            desktopState));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopTasksTransitionObserver> provideDesktopTasksTransitionObserver(
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Transitions transitions,
            ShellTaskOrganizer shellTaskOrganizer,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler,
            DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            DisplayController displayController,
            Optional<PinnedLayerController> pinnedLayerController,
            DesktopState desktopState,
            ShellInit shellInit) {
        return desktopUserRepositories.flatMap(
                repository ->
                        Optional.of(
                                new DesktopTasksTransitionObserver(
                                        repository,
                                        transitions,
                                        shellTaskOrganizer,
                                        desktopMixedTransitionHandler.get(),
                                        desktopWallpaperActivityTokenProvider,
                                        displayController,
                                        pinnedLayerController.orElse(null),
                                        desktopState,
                                        shellInit)));
    }
    @WMSingleton
    @Provides
    static Optional<DesktopBackNavTransitionObserver> provideDesktopBackNavTransitionObserver(
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Optional<DesktopMixedTransitionHandler> desktopMixedTransitionHandler,
            Optional<BackAnimationController> backAnimationController,
            @DynamicOverride DesksOrganizer desksOrganizer,
            Transitions transitions,
            DesktopState desktopState,
            ShellInit shellInit) {
        return desktopUserRepositories.flatMap(
                repository ->
                        Optional.of(
                                new DesktopBackNavTransitionObserver(
                                        repository,
                                        desktopMixedTransitionHandler.get(),
                                        backAnimationController.get(),
                                        desksOrganizer,
                                        transitions,
                                        desktopState,
                                        shellInit)));
    }

    @WMSingleton
    @Provides
    static Optional<DesksTransitionObserver> provideDesksTransitionObserver(
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            @DynamicOverride DesksOrganizer desksOrganizer,
            @NonNull Transitions transitions,
            @NonNull DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            @NonNull @ShellMainThread CoroutineScope mainScope,
            DesktopState desktopState,
            @NonNull DesktopModeEventLogger desktopModeEventLogger,
            @NonNull ShellController shellController,
            @NonNull DisplayController displayController,
            DesktopHomeScreenPeekController desktopHomeScreenPeekController
    ) {
        if (desktopState.canEnterDesktopModeOrShowAppHandle()) {
            return Optional.of(
                    new DesksTransitionObserver(desktopUserRepositories, desksOrganizer,
                            transitions, desktopWallpaperActivityTokenProvider, mainScope,
                            desktopModeEventLogger, shellController, displayController,
                            desktopHomeScreenPeekController));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<FreeformFallbackTransitionObserver> provideFreeformFallbackTransitionObserver(
            @NonNull Transitions transitions,
            @ShellMainThreadImmediate CoroutineScope mainScopeImmediate,
            Optional<PinnedLayerController> pinnedLayerController,
            dagger.Lazy<Optional<DesktopTasksController>> desktopTasksControllerLazy,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            @DynamicOverride DesksOrganizer desksOrganizer,
            DesksController desksController,
            DesktopConfig desktopConfig,
            Optional<FreeformFallbackTransitionHandler> freeformFallbackTransitionHandler,
            DesktopState desktopState
    ) {
        if (desktopState.canEnterDesktopModeOrShowAppHandle()) {
            return Optional.of(
                    new FreeformFallbackTransitionObserver(
                            transitions,
                            mainScopeImmediate,
                            pinnedLayerController,
                            desktopTasksControllerLazy,
                            desktopUserRepositories,
                            desksOrganizer,
                            desksController,
                            desktopConfig,
                            freeformFallbackTransitionHandler
                    )
            );
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static DesktopFullscreenRequestHandler provideDesktopFullscreenRequestHandler(
            ShellInit shellInit,
            Context context,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            @DynamicOverride DesksOrganizer desksOrganizer,
            DesksController desksController,
            DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            DisplayController displayController,
            Optional<ClientFullscreenRequestController> clientFullscreenRequestController
    ) {
        return new DesktopFullscreenRequestHandler(shellInit, context,
                desktopUserRepositories, desksOrganizer, desksController,
                desktopWallpaperActivityTokenProvider, displayController,
                clientFullscreenRequestController);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopMixedTransitionHandler> provideDesktopMixedTransitionHandler(
            Context context,
            Transitions transitions,
            @DynamicOverride DesktopUserRepositories desktopUserRepositories,
            FreeformTaskTransitionHandler freeformTaskTransitionHandler,
            CloseDesktopTaskTransitionHandler closeDesktopTaskTransitionHandler,
            DesktopFullscreenRequestHandler desktopFullscreenRequestHandler,
            Optional<DesktopImmersiveController> desktopImmersiveController,
            DesktopMinimizationTransitionHandler desktopMinimizationTransitionHandler,
            DesktopModeDragAndDropTransitionHandler desktopModeDragAndDropTransitionHandler,
            Optional<SystemModalsTransitionHandler> systemModalsTransitionHandler,
            InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler handler,
            ShellInit shellInit,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            DesktopState desktopState,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            DeskSwitchTransitionHandler deskSwitchTransitionHandler
    ) {
        if (!desktopState.canEnterDesktopMode()
                && !desktopState.overridesShowAppHandle()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopMixedTransitionHandler(
                        context,
                        transitions,
                        desktopUserRepositories,
                        freeformTaskTransitionHandler,
                        closeDesktopTaskTransitionHandler,
                        desktopImmersiveController.get(),
                        desktopFullscreenRequestHandler,
                        desktopMinimizationTransitionHandler,
                        desktopModeDragAndDropTransitionHandler,
                        systemModalsTransitionHandler,
                        interactionJankMonitor,
                        handler,
                        shellInit,
                        rootTaskDisplayAreaOrganizer,
                        desksTransitionObserver.get(),
                        deskSwitchTransitionHandler));
    }

    @WMSingleton
    @Provides
    static DesktopModeLoggerTransitionObserver provideDesktopModeLoggerTransitionObserver(
            ShellInit shellInit,
            DesktopModeEventLogger desktopModeEventLogger,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            DesktopState desktopState,
            @DynamicOverride DesksOrganizer desksOrganizer) {
        return new DesktopModeLoggerTransitionObserver(
                shellInit, desktopModeEventLogger,
                desktopTasksLimiter, desktopState, desksOrganizer);
    }

    @WMSingleton
    @Provides
    static DesktopModeEventLogger provideDesktopModeEventLogger() {
        return new DesktopModeEventLogger();
    }

    @WMSingleton
    @Provides
    static Optional<DesktopDisplayEventHandler> provideDesktopDisplayEventHandler(
            ShellInit shellInit,
            @ShellMainThread CoroutineScope mainScope,
            ShellController shellController,
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            @DynamicOverride DesksOrganizer desksOrganizer,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopDisplayModeController> desktopDisplayModeController,
            DesktopRepositoryInitializer desktopRepositoryInitializer,
            Optional<DesksTransitionObserver> desksTransitionObserver,
            DesktopState desktopState,
            Transitions transitions,
            KeyguardManager keyguardManager
    ) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopDisplayEventHandler(
                        shellInit,
                        mainScope,
                        shellController,
                        displayController,
                        rootTaskDisplayAreaOrganizer,
                        desksOrganizer,
                        desktopRepositoryInitializer,
                        desktopUserRepositories.get(),
                        desktopTasksController.get(),
                        desktopDisplayModeController.get(),
                        desksTransitionObserver.get(),
                        desktopState,
                        transitions,
                        keyguardManager));
    }

    @WMSingleton
    @Provides
    static Optional<DesktopFirstListenerManager> provideDesktopFirstListenerManager(
            @NonNull DesktopState desktopState,
            @NonNull ShellInit shellInit,
            @NonNull RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            @NonNull DisplayController displayController
    ) {
        if (desktopState.canEnterDesktopMode()) {
            return Optional.of(
                    new DesktopFirstListenerManager(shellInit, rootTaskDisplayAreaOrganizer,
                            displayController));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static AppHandleNotifier provideAppHandleNotifier(
            @ShellMainThread ShellExecutor shellExecutor,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            @ShellMainThread CoroutineScope mainScope) {
        return new AppHandleNotifier(
                shellExecutor, windowDecorCaptionRepository, mainScope);
    }

    @WMSingleton
    @Provides
    static AppHandleEducationDatastoreRepository provideAppHandleEducationDatastoreRepository(
            Context context) {
        return new AppHandleEducationDatastoreRepository(context);
    }

    @WMSingleton
    @Provides
    static AppHandleEducationFilter provideAppHandleEducationFilter(
            Context context,
            AppHandleEducationDatastoreRepository appHandleEducationDatastoreRepository) {
        return new AppHandleEducationFilter(context, appHandleEducationDatastoreRepository);
    }

    @WMSingleton
    @Provides
    static WindowDecorCaptionRepository provideAppHandleRepository() {
        return new WindowDecorCaptionRepository();
    }

    @WMSingleton
    @Provides
    static DesktopWindowingEducationTooltipController
    provideDesktopWindowingEducationTooltipController(
            Context context,
            AdditionalSystemViewContainer.Factory additionalSystemViewContainerFactory,
            DisplayController displayController) {
        return new DesktopWindowingEducationTooltipController(
                context, additionalSystemViewContainerFactory, displayController);
    }

    @WMSingleton
    @Provides
    static DesktopWindowingEducationPromoController provideDesktopWindowingEducationPromoController(
            Context context,
            AdditionalSystemViewContainer.Factory additionalSystemViewContainerFactory,
            DisplayController displayController,
            ShellController shellController,
            @ShellBackgroundThread MainCoroutineDispatcher bgDispatcher
    ) {
        return new DesktopWindowingEducationPromoController(
                context,
                additionalSystemViewContainerFactory,
                displayController,
                shellController,
                bgDispatcher
        );
    }

    @OptIn(markerClass = ExperimentalCoroutinesApi.class)
    @WMSingleton
    @Provides
    static AppHandleEducationController provideAppHandleEducationController(
            Context context,
            AppHandleEducationFilter appHandleEducationFilter,
            AppHandleEducationDatastoreRepository appHandleEducationDatastoreRepository,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            DesktopWindowingEducationTooltipController desktopWindowingEducationTooltipController,
            @ShellMainThread CoroutineScope applicationScope,
            @ShellBackgroundThread MainCoroutineDispatcher backgroundDispatcher,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            DesktopState desktopState) {
        return new AppHandleEducationController(
                context,
                appHandleEducationFilter,
                appHandleEducationDatastoreRepository,
                windowDecorCaptionRepository,
                desktopWindowingEducationTooltipController,
                applicationScope,
                backgroundDispatcher,
                desktopModeUiEventLogger,
                desktopState);
    }

    @WMSingleton
    @Provides
    static AppToWebEducationDatastoreRepository provideAppToWebEducationDatastoreRepository(
            Context context) {
        return new AppToWebEducationDatastoreRepository(context);
    }

    @WMSingleton
    @Provides
    static AppToWebEducationFilter provideAppToWebEducationFilter(
            Context context,
            AppToWebEducationDatastoreRepository appToWebEducationDatastoreRepository,
            AppToWebRepository appToWebRepository,
            FocusTransitionObserver focusTransitionObserver
    ) {
        return new AppToWebEducationFilter(
                context, appToWebEducationDatastoreRepository, appToWebRepository,
                focusTransitionObserver);
    }

    @OptIn(markerClass = ExperimentalCoroutinesApi.class)
    @WMSingleton
    @Provides
    static AppToWebEducationController provideAppToWebEducationController(
            Context context,
            AppToWebEducationFilter appToWebEducationFilter,
            AppToWebEducationDatastoreRepository appToWebEducationDatastoreRepository,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            DesktopWindowingEducationPromoController desktopWindowingEducationPromoController,
            @ShellMainThread CoroutineScope applicationScope,
            @ShellBackgroundThread MainCoroutineDispatcher backgroundDispatcher,
            DesktopState desktopState) {
        return new AppToWebEducationController(context, appToWebEducationFilter,
                appToWebEducationDatastoreRepository, windowDecorCaptionRepository,
                desktopWindowingEducationPromoController, applicationScope,
                backgroundDispatcher, desktopState);
    }

    @WMSingleton
    @Provides
    static DesktopPersistentRepository provideDesktopPersistentRepository(
            Context context, @ShellBackgroundThread CoroutineScope bgScope) {
        return new DesktopPersistentRepository(context, bgScope);
    }

    @WMSingleton
    @Provides
    static DesktopRepositoryInitializer provideDesktopRepositoryInitializer(
            Context context,
            DesktopPersistentRepository desktopPersistentRepository,
            @ShellMainThread CoroutineScope mainScope,
            DesktopConfig desktopConfig,
            DesktopState desktopState,
            DisplayController displayController) {
        return new DesktopRepositoryInitializerImpl(context, desktopPersistentRepository,
                mainScope, desktopConfig, desktopState, displayController);
    }

    @WMSingleton
    @Provides
    static DesktopModeUiEventLogger provideDesktopUiEventLogger(
            UiEventLogger uiEventLogger,
            PackageManager packageManager
    ) {
        return new DesktopModeUiEventLogger(uiEventLogger, packageManager);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopDisplayModeController> provideDesktopDisplayModeController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            Transitions transitions,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            IWindowManager windowManager,
            ShellTaskOrganizer shellTaskOrganizer,
            DesktopWallpaperActivityTokenProvider desktopWallpaperActivityTokenProvider,
            InputManager inputManager,
            DisplayController displayController,
            @ShellMainThread Handler mainHandler,
            @ShellMainThread ShellExecutor mainExecutor,
            DesktopState desktopState,
            DeviceStateManager deviceStateManager
    ) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopDisplayModeController(
                        context,
                        shellInit,
                        shellCommandHandler,
                        shellController,
                        transitions,
                        rootTaskDisplayAreaOrganizer,
                        windowManager,
                        shellTaskOrganizer,
                        desktopWallpaperActivityTokenProvider,
                        inputManager,
                        displayController,
                        mainHandler,
                        mainExecutor,
                        desktopState,
                        deviceStateManager));
    }

    @WMSingleton
    @Provides
    static Optional<DesktopImeHandler> provideDesktopImeHandler(
            Optional<DesktopUserRepositories> desktopUserRepositories,
            FocusTransitionObserver focusTransitionObserver,
            DisplayImeController displayImeController,
            Optional<DesktopModeWindowDecorViewModel> desktopModeWindowDecorViewModel,
            DisplayController displayController,
            ShellTaskOrganizer shellTaskOrganizer,
            Transitions transitions,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellAnimationThread ShellExecutor animExecutor,
            Context context,
            ShellInit shellInit,
            DesktopState desktopState) {
        if (!desktopState.canEnterDesktopMode()) {
            return Optional.empty();
        }
        return Optional.of(
                new DesktopImeHandler(desktopUserRepositories.get(),
                        focusTransitionObserver, shellTaskOrganizer,
                        displayImeController, desktopModeWindowDecorViewModel, displayController,
                        transitions, mainExecutor,
                        animExecutor, context, shellInit));
    }

    @WMSingleton
    @Provides
    static VisualIndicatorUpdateScheduler provideVisualIndicatorUpdateScheduler(
            ShellInit shellInit,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread CoroutineScope bgScope,
            DisplayController displayController) {
        return new VisualIndicatorUpdateScheduler(shellInit, mainDispatcher, bgScope,
                displayController);
    }

    @WMSingleton
    @Provides
    static Optional<NormalAppLayerHandler> provideNormalAppLayerHandler(
            ShellInit shellInit,
            Transitions transitions,
            Optional<NormalAppLayerController> normalAppLayerController) {
        if (PinnedLayerFlags.isPinnedLayerEnabled()) {
            return Optional.of(
                    new NormalAppLayerHandler(
                            shellInit, transitions, normalAppLayerController.get()));

        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<NormalAppLayerController> provideNormalAppLayerController(
            ShellInit shellInit,
            Transitions transitions,
            Optional<DesktopTasksController> desktopTasksController,
            Optional<DesktopUserRepositories> desktopUserRepositoriesOptional,
            Optional<PinnedLayerController> pinnedLayerController,
            DesktopState desktopState) {
        if (PinnedLayerFlags.isPinnedLayerEnabled()) {
            return Optional.of(
                    new NormalAppLayerController(
                            shellInit, transitions, desktopUserRepositoriesOptional.orElse(null),
                            desktopTasksController.orElse(null), pinnedLayerController.get(),
                            desktopState));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<ClientFullscreenRequestController> provideClientFullscreenRequestController(
            ShellInit shellInit,
            Transitions transitions,
            ShellTaskOrganizer shellTaskOrganizer) {
        if (com.android.window.flags.Flags.delegateRequestFullscreenHandlingToShell()) {
            return Optional.of(new ClientFullscreenRequestController(shellInit, transitions,
                    shellTaskOrganizer));
        }
        return Optional.empty();
    }

    //
    // Package Update
    //

    @WMSingleton
    @Provides
    @DynamicOverride
    static PackageUpdateController providePackageUpdateController(
            Transitions transitions,
            ShellTaskOrganizer shellTaskOrganizer,
            ShellInit shellInit,
            UserProfileContexts userProfileContexts,
            WindowDecorTaskResourceLoader taskResourceLoader,
            Optional<DesktopModeWindowDecorViewModel> desktopModeWindowDecorViewModel,
            PackageUpdateTransitionHandler packageUpdateTransitionHandler,
            PackageManager packageManager,
            @ShellMainThreadImmediate CoroutineScope mainImmediateScope
    ) {
        return new PackageUpdateController(transitions, shellTaskOrganizer,
                shellInit, userProfileContexts, taskResourceLoader,
                desktopModeWindowDecorViewModel, packageUpdateTransitionHandler, packageManager,
                mainImmediateScope);
    }

    @WMSingleton
    @Provides
    static PackageUpdateTransitionHandler providePackageUpdateTransitionHandler(
            TransactionSupplier transactionSupplier,
            Context context,
            @ShellAnimationThread ShellExecutor animExecutor,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler shellMainHandler,
            InteractionJankMonitor interactionJankMonitor
    ) {
        return new PackageUpdateTransitionHandler(transactionSupplier, context, animExecutor,
                mainExecutor, shellMainHandler, interactionJankMonitor);
    }
    //
    // App zoom out
    //

    @WMSingleton
    @Provides
    static AppZoomOutController provideAppZoomOutController(
            Context context,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            DisplayController displayController,
            DisplayLayout displayLayout,
            @ShellMainThread ShellExecutor mainExecutor,
            InteractionJankMonitor interactionJankMonitor) {
        return AppZoomOutController.create(context, shellInit, shellTaskOrganizer,
                displayController, displayLayout, mainExecutor, interactionJankMonitor);
    }

    //
    // Drag and drop
    //

    @WMSingleton
    @Provides
    static GlobalDragListener provideGlobalDragListener(
            IWindowManager wmService, @ShellMainThread ShellExecutor mainExecutor) {
        return new GlobalDragListener(wmService, mainExecutor);
    }

    @WMSingleton
    @Provides
    static DragAndDropController provideDragAndDropController(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellCommandHandler shellCommandHandler,
            ShellTaskOrganizer shellTaskOrganizer,
            DisplayController displayController,
            UiEventLogger uiEventLogger,
            IconProvider iconProvider,
            GlobalDragListener globalDragListener,
            Transitions transitions,
            Lazy<DragToBubbleController> dragToBubbleControllerLazy,
            @ShellMainThread ShellExecutor mainExecutor,
            DesktopState desktopState,
            BubbleFeatureConfig bubbleFeatureConfig,
            Optional<ContainerHierarchyDependency> containerHierarchyDependency) {
        return new DragAndDropController(
                context,
                shellInit,
                shellController,
                shellCommandHandler,
                shellTaskOrganizer,
                displayController,
                uiEventLogger,
                iconProvider,
                globalDragListener,
                transitions,
                dragToBubbleControllerLazy,
                mainExecutor,
                desktopState,
                bubbleFeatureConfig);
    }

    @WMSingleton
    @Provides
    static DragToBubbleController getDragToBubbleController(Context context,
            BubbleController bubbleController) {
        return new DragToBubbleController(context, bubbleController);
    }

    //
    // Misc
    //

    // TODO: Temporarily move dependencies to this instead of ShellInit since that is needed to add
    // the callback. We will be moving to a different explicit startup mechanism in a follow- up CL.
    @WMSingleton
    @ShellCreateTriggerOverride
    @Provides
    static Object provideIndependentShellComponentsToCreate(
            DragAndDropController dragAndDropController,
            @NonNull DelegateLetterboxTransitionObserver letterboxTransitionObserver,
            @NonNull LetterboxCommandHandler letterboxCommandHandler,
            @NonNull LetterboxTaskListenerAdapter letterboxTaskListenerAdapter,
            @NonNull LetterboxCleanupAdapter letterboxCleanupAdapter,
            @NonNull Optional<CompatUISharedRepositoryCleanUp> compatUISharedStateManager,
            Optional<ClientFullscreenRequestController> clientFullscreenRequestController,
            Optional<DesktopDisplayEventHandler> desktopDisplayEventHandler,
            Optional<DesktopModeKeyGestureHandler> desktopModeKeyGestureHandler,
            Optional<SystemModalsTransitionHandler> systemModalsTransitionHandler,
            Optional<DisplayDisconnectTransitionHandler> displayDisconnectTransitionHandler,
            Optional<DesktopImeHandler> desktopImeHandler,
            ShellCrashHandler shellCrashHandler,
            AppToWebEducationController appToWebEducationController,
            QuitFocusedAppKeyGestureHandler quitFocusedAppKeyGestureHandler,
            BubbleRootTask bubbleRootTask,
            DesktopModeAidlProvider desktopModeAidlProvider,
            DesktopTasksTransitionHandler desktopTasksTransitionHandler,
            DesktopModeShellCommandHandler desktopModeShellCommandHandler) {
        return new Object();
    }

    @WMSingleton
    @Provides
    static OverviewToDesktopTransitionObserver provideOverviewToDesktopTransitionObserver(
            Transitions transitions, ShellInit shellInit) {
        return new OverviewToDesktopTransitionObserver(transitions, shellInit);
    }

    @WMSingleton
    @Provides
    static LetterboxDependenciesHelper provideLetterboxDependenciesHelper(
            @NonNull DesktopState desktopState,
            @NonNull Optional<DesktopUserRepositories> desktopRepositories) {
        if (desktopState.canEnterDesktopMode()) {
            return new DefaultLetterboxDependenciesHelper(desktopRepositories.get().getCurrent());
        } else {
            return new IgnoreLetterboxDependenciesHelper();
        }
    }

    @WMSingleton
    @Provides
    static UserProfileContexts provideUserProfilesContexts(
            Context context,
            ShellController shellController,
            ShellInit shellInit) {
        return new UserProfileContexts(context, shellController, shellInit);
    }

    @WMSingleton
    @Provides
    static ShellCrashHandler provideShellCrashHandler(
            ShellTaskOrganizer shellTaskOrganizer,
            Transitions transitions,
            HomeIntentProvider homeIntentProvider,
            DesktopState desktopState,
            Optional<BubbleHelper> bubbleHelper,
            ShellInit shellInit) {
        return new ShellCrashHandler(shellTaskOrganizer, transitions, homeIntentProvider,
                desktopState, bubbleHelper, shellInit);
    }

    @WMSingleton
    @Provides
    static HomeIntentProvider provideHomeIntentProvider(Context context) {
        return new HomeIntentProvider(context);
    }

    @WMSingleton
    @Provides
    @DynamicOverride
    static InteractiveTasksTransitionObserver provideInteractiveTasksTransitionObserver(
            ShellInit shellInit,
            Transitions transitions,
            ShellTaskOrganizer shellTaskOrganizer) {
        // As a dynamic override it's binded as optional in the base module. Since that creates a
        // optional multi-binding situation, we need to provide here a real instance and rely on
        // lazy inject.
        return new InteractiveTasksTransitionObserver(shellInit, transitions,
                shellTaskOrganizer);
    }
}
