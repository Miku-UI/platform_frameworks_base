/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static com.android.systemui.Flags.enableViewCaptureTracing;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.os.Handler;
import android.os.UserManager;
import android.view.IWindowManager;
import android.view.WindowManager;

import com.android.app.viewcapture.ViewCaptureAwareWindowManagerFactory;
import com.android.internal.logging.InstanceIdSequence;
import com.android.internal.policy.FoldLockSettingsObserver;
import com.android.internal.statusbar.IStatusBarService;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.bubbles.BubbleData;
import com.android.wm.shell.bubbles.BubbleDataRepository;
import com.android.wm.shell.bubbles.BubbleEducationController;
import com.android.wm.shell.bubbles.BubbleHelper;
import com.android.wm.shell.bubbles.BubbleHelperImpl;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleResizabilityChecker;
import com.android.wm.shell.bubbles.BubbleRootTask;
import com.android.wm.shell.bubbles.BubbleTaskUnfoldTransitionMerger;
import com.android.wm.shell.bubbles.BubbleViewInfoTask;
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfoProvider;
import com.android.wm.shell.bubbles.appinfo.PackageManagerBubbleAppInfoProvider;
import com.android.wm.shell.bubbles.fold.BubblesFoldLockSettingsObserver;
import com.android.wm.shell.bubbles.fold.BubblesFoldLockSettingsObserverImpl;
import com.android.wm.shell.bubbles.logging.BubbleLogger;
import com.android.wm.shell.bubbles.logging.BubbleSessionTracker;
import com.android.wm.shell.bubbles.logging.BubbleSessionTrackerImpl;
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository;
import com.android.wm.shell.bubbles.transitions.BubbleTransitions;
import com.android.wm.shell.bubbles.user.data.BubbleUserResolver;
import com.android.wm.shell.bubbles.user.data.UserManagerBubbleUserResolver;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.HomeIntentProvider;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.draganddrop.DragAndDropController;
import com.android.wm.shell.onehanded.OneHandedController;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfig;
import com.android.wm.shell.shared.bubbles.BubbleFeatureConfigImpl;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskViewRepository;
import com.android.wm.shell.taskview.TaskViewRootTask;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import java.util.Optional;

/**
 * Provides Bubble Shell implementation components to Dagger dependency graph.
 */
@Module
public abstract class BubbleModule {


    @WMSingleton
    @Provides
    static BubbleFeatureConfig providesBubbleFeatureConfig(Context context,
            DesktopState desktopState) {
        return new BubbleFeatureConfigImpl(context, desktopState);
    }

    @WMSingleton
    @Provides
    static BubblePositioner provideBubblePositioner(Context context, WindowManager windowManager) {
        return new BubblePositioner(context, windowManager);
    }

    @WMSingleton
    @Provides
    static BubbleEducationController provideBubbleEducationProvider(Context context) {
        return new BubbleEducationController(context);
    }

    @WMSingleton
    @Provides
    static BubbleData provideBubbleData(
            Context context,
            BubbleLogger logger,
            BubblePositioner positioner,
            BubbleEducationController educationController,
            BubbleAppInfoProvider appInfoProvider,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor) {
        return new BubbleData(
                context, logger, positioner, educationController, appInfoProvider, mainExecutor,
                bgExecutor);
    }

    @WMSingleton
    @Provides
    static Optional<BubbleTaskUnfoldTransitionMerger> provideBubbleTaskUnfoldTransitionMerger(
            Optional<BubbleController> bubbleController) {
        return bubbleController.map(controller -> controller);
    }

    @Binds
    abstract BubbleAppInfoProvider bindBubbleAppInfoProvider(
            PackageManagerBubbleAppInfoProvider appInfoProvider);

    @WMSingleton
    @Provides
    static BubbleTransitions provideBubbleTransitions(
            @NonNull Context context,
            @NonNull Transitions transitions,
            @NonNull ShellTaskOrganizer organizer,
            @NonNull TaskViewRepository repository,
            @NonNull BubbleData bubbleData,
            @NonNull @Bubbles TaskViewTransitions taskViewTransitions,
            @NonNull BubbleViewInfoTask.Factory bubbleViewInfoTaskFactory,
            @NonNull BubbleHelper bubbleHelper
    ) {
        return new BubbleTransitions(context, transitions, organizer, repository,
                bubbleData, taskViewTransitions, bubbleViewInfoTaskFactory, bubbleHelper);
    }

    @WMSingleton
    @Provides
    @Bubbles
    static TaskViewTransitions provideBubblesTaskViewTransitions(
            @NonNull Transitions transitions,
            @NonNull TaskViewRepository repository,
            @NonNull ShellTaskOrganizer organizer,
            Optional<BubbleHelper> bubbleHelper,
            @Bubbles Optional<TaskViewRootTask> taskViewRootTask
    ) {
        return new TaskViewTransitions(transitions, repository, organizer, bubbleHelper,
                taskViewRootTask);
    }

    @WMSingleton
    @Provides
    @Bubbles
    static FoldLockSettingsObserver provideFoldLockSettingsObserver(
            Context context,
            @ShellMainThread Handler mainHandler) {
        FoldLockSettingsObserver observer = new FoldLockSettingsObserver(mainHandler, context);
        observer.register();
        return observer;
    }

    @WMSingleton
    @Provides
    @Bubbles
    static InstanceIdSequence provideBubbleInstanceIdSequence() {
        return new InstanceIdSequence(Integer.MAX_VALUE);
    }

    @Binds
    abstract BubblesFoldLockSettingsObserver bindBubblesFoldLockSettingsObserver(
            BubblesFoldLockSettingsObserverImpl impl);

    @Binds
    abstract BubbleSessionTracker bindBubbleSessionTracker(BubbleSessionTrackerImpl impl);

    @Binds
    abstract BubbleUserResolver bindUserResolver(UserManagerBubbleUserResolver impl);

    @Provides
    @Bubbles
    static Optional<TaskViewRootTask> provideBubblesTaskViewRootTask(
            BubbleRootTask bubbleRootTask) {
        return Optional.of(bubbleRootTask);
    }

    @WMSingleton
    @Binds
    abstract BubbleHelper provideBubbleHelper(BubbleHelperImpl impl);

    // Note: Handler needed for LauncherApps.register
    @WMSingleton
    @Provides
    static BubbleController provideBubbleController(
            Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            BubbleData data,
            BubbleTransitions bubbleTransitions,
            FloatingContentCoordinator floatingContentCoordinator,
            IStatusBarService statusBarService,
            WindowManager windowManager,
            DisplayInsetsController displayInsetsController,
            DisplayImeController displayImeController,
            UserManager userManager,
            LauncherApps launcherApps,
            TaskStackListenerImpl taskStackListener,
            BubbleLogger logger,
            ShellTaskOrganizer organizer,
            BubblePositioner positioner,
            DisplayController displayController,
            @DynamicOverride Optional<OneHandedController> oneHandedOptional,
            DragAndDropController dragAndDropController,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            @Bubbles TaskViewTransitions taskViewTransitions,
            Transitions transitions,
            SyncTransactionQueue syncQueue,
            IWindowManager wmService,
            HomeIntentProvider homeIntentProvider,
            Lazy<Optional<SplitScreenController>> splitScreenController,
            @NonNull Optional<ShellUnfoldProgressProvider> unfoldProgressProvider,
            BubblesFoldLockSettingsObserver foldLockSettingsObserver,
            BubbleSessionTracker sessionTracker,
            BubbleViewInfoTask.Factory bubbleViewInfoTaskFactory,
            BubbleHelper bubbleHelper,
            BubbleFeatureConfig featureConfig) {
        final WindowManager wm = enableViewCaptureTracing()
                ? ViewCaptureAwareWindowManagerFactory.getInstance(context)
                : windowManager;
        return new BubbleController(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                data,
                null /* synchronizer */,
                floatingContentCoordinator,
                new BubbleDataRepository(
                        launcherApps,
                        mainExecutor,
                        bgExecutor,
                        new BubblePersistentRepository(context)),
                bubbleTransitions,
                statusBarService,
                wm,
                displayInsetsController,
                displayImeController,
                userManager,
                launcherApps,
                logger,
                taskStackListener,
                organizer,
                positioner,
                displayController,
                oneHandedOptional,
                dragAndDropController,
                mainExecutor,
                mainHandler,
                bgExecutor,
                taskViewTransitions,
                transitions,
                syncQueue,
                wmService,
                new BubbleResizabilityChecker(),
                homeIntentProvider,
                splitScreenController,
                unfoldProgressProvider,
                foldLockSettingsObserver,
                sessionTracker,
                bubbleViewInfoTaskFactory,
                bubbleHelper,
                featureConfig);
    }
}
