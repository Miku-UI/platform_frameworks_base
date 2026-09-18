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

package com.android.wm.shell.shared.compat;

import android.annotation.Nullable;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.TransitionInfo;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.shared.TransitionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Abstraction layer for a remote animation or remote transition that can animate surfaces.
 *
 * This class is meant to be a drop-in replacement for usages of IRemoteAnimationRunner as an
 * intermediate step of migrating to the Shell APIs. Once support for the old APIs is removed, this
 * abstraction can be swapped out for RemoteTransition.
 */
public class SurfaceTransition {
    private final Mode mMode;
    @Nullable private final IRemoteAnimationRunner mRunner;
    @Nullable private final IRemoteTransition mTransition;

    private SurfaceTransition(
            Mode mode, @Nullable IRemoteAnimationRunner runner,
            @Nullable IRemoteTransition transition) {
        boolean condition = switch (mode) {
            case LEGACY -> runner != null && transition == null;
            case SHELL -> runner == null && transition != null;
        };
        if (!condition) {
            throw new IllegalArgumentException("Invalid configuration for " + mode.name()
                    + ": transition=" + transition + " runner=" + runner);
        }

        mMode = mode;
        mRunner = runner;
        mTransition = transition;
    }

    /** Factory for compat use with the pre-Shell APIs. */
    public static SurfaceTransition from(IRemoteAnimationRunner runner) {
        return new SurfaceTransition(Mode.LEGACY, runner, null /* transition */);
    }

    /** Factory for direct use with the Shell APIs. */
    public static SurfaceTransition from(IRemoteTransition transition) {
        return new SurfaceTransition(Mode.SHELL, null /* runner */, transition);
    }

    /** Kicks off this transition using the given parameters. */
    public void startAnimation(Params params) throws RemoteException {
        switch (mMode) {
            case LEGACY -> {
                if (!(params instanceof ParamsLegacy paramsLegacy)) {
                    throw new IllegalArgumentException(
                            "SurfaceTransition in compat mode with Shell params");
                }

                if (mRunner != null) {
                    mRunner.onAnimationStart(
                            paramsLegacy.transit, paramsLegacy.appsCompat,
                            paramsLegacy.wallpapersCompat, paramsLegacy.nonAppsCompat,
                            paramsLegacy.finishedCallbackCompat);
                }
            }

            case SHELL -> {
                if (!(params instanceof ParamsShell paramsShell)) {
                    throw new IllegalArgumentException(
                            "SurfaceTransition in Shell mode with compat params");
                }

                if (mTransition != null) {
                    mTransition.startAnimation(
                            paramsShell.token, paramsShell.info, paramsShell.transaction,
                            paramsShell.finishedCallbackShell);
                }
            }
        }
    }

    /** Parameters for starting an instance of {@link SurfaceTransition}]. */
    public abstract static class Params {
        public final long startTime;
        public final long fadeoutDuration;

        public Params(long startTime, long fadeoutDuration) {
            this.startTime = startTime;
            this.fadeoutDuration = fadeoutDuration;
        }

        /**
         * Create animation parameters for compat use with the pre-Shell APIs.
         *
         * @param startTime the start time of the animation in uptime milliseconds. Deprecated.
         * @param fadeoutDuration the duration of the exit animation, in milliseconds Deprecated.
         * @param transit The legacy transition type.
         * @param apps The list of apps to animate.
         * @param wallpapers The list of wallpapers to animate.
         * @param nonApps The list of non-app windows such as Bubbles to animate.
         * @param finishedCallback The callback to invoke when the animation is finished.
         */
        public static Params create(
                long startTime, long fadeoutDuration, int transit,
                @Nullable RemoteAnimationTarget[] apps,
                @Nullable RemoteAnimationTarget[] wallpapers,
                @Nullable RemoteAnimationTarget[] nonApps,
                @Nullable IRemoteAnimationFinishedCallback finishedCallback) {
            return new ParamsLegacy(
                    startTime, fadeoutDuration, transit, apps, wallpapers, nonApps,
                    finishedCallback);
        }

        /**
         * Create animation parameters for direct use with the Shell APIs.
         *
         * @param startTime the start time of the animation in uptime milliseconds. Deprecated.
         * @param fadeoutDuration the duration of the exit animation, in milliseconds Deprecated.
         * @param token The unique transition identifier.
         * @param info The comprehensive information about the surfaces in this transition.
         * @param transaction The transaction to use to apply surface changes at the start of the
         *   transition.
         * @param finishedCallback The callback to invoke when the transition is finished.
         */
        public static Params create(
                long startTime, long fadeoutDuration, @Nullable IBinder token,
                @Nullable TransitionInfo info, @Nullable SurfaceControl.Transaction transaction,
                @Nullable IRemoteTransitionFinishedCallback finishedCallback) {
            return new ParamsShell(
                    startTime, fadeoutDuration, token, info, transaction, finishedCallback);
        }

        /** Returns the app surfaces changing as part of the transition. */
        @Nullable
        public abstract AnimatedSurface[] getApps();

        /** Returns the wallpaper surfaces changing as part of the transition. */
        @Nullable
        public abstract AnimatedSurface[] getWallpapers();

        /** Returns the non-app surfaces changing as part of the transition. */
        @Nullable
        public abstract AnimatedSurface[] getNonApps();

        /** Returns transaction to use to apply surface changes at the start of the transition. */
        @Nullable
        public abstract SurfaceControl.Transaction getStartTransaction();

        /** Returns whether the params include a valid finished callback. */
        public abstract boolean hasFinishedCallback();

        /** See {@link Params#invokeCallback(SurfaceControl.Transaction)}. */
        public void invokeCallback() throws RemoteException {
            invokeCallback(null /* transaction */);
        }

        /** Invoked the finished callback, if one is defined. */
        public abstract void invokeCallback(@Nullable SurfaceControl.Transaction transaction)
                throws RemoteException;

        /** Builds a copy of this object replacing the finished callback. */
        public abstract Params copyWith(ThrowingCallback callback);
    }

    /** Compat parameters for the pre-Shell remote animation APIs. */
    private static class ParamsLegacy extends Params {
        public final int transit;
        @Nullable public final RemoteAnimationTarget[] appsCompat;
        @Nullable public final RemoteAnimationTarget[] wallpapersCompat;
        @Nullable public final RemoteAnimationTarget[] nonAppsCompat;
        @Nullable public final IRemoteAnimationFinishedCallback finishedCallbackCompat;

        @Nullable private AnimatedSurface[] mApps;
        @Nullable private AnimatedSurface[] mWallpapers;
        @Nullable private AnimatedSurface[] mNonApps;

        ParamsLegacy(
                long startTime, long fadeoutDuration, int transit,
                @Nullable RemoteAnimationTarget[] appsCompat,
                @Nullable RemoteAnimationTarget[] wallpapersCompat,
                @Nullable RemoteAnimationTarget[] nonAppsCompat,
                @Nullable IRemoteAnimationFinishedCallback finishedCallbackCompat) {
            super(startTime, fadeoutDuration);

            this.transit = transit;
            this.appsCompat = appsCompat;
            this.wallpapersCompat = wallpapersCompat;
            this.nonAppsCompat = nonAppsCompat;
            this.finishedCallbackCompat = finishedCallbackCompat;

            if (appsCompat != null) {
                this.mApps = Arrays.stream(appsCompat).map(AnimatedSurfaceUtils::from)
                        .toArray(AnimatedSurface[]::new);
            }

            if (wallpapersCompat != null) {
                this.mWallpapers = Arrays.stream(wallpapersCompat).map(AnimatedSurfaceUtils::from)
                        .toArray(AnimatedSurface[]::new);
            }

            if (nonAppsCompat != null) {
                this.mNonApps = Arrays.stream(nonAppsCompat).map(AnimatedSurfaceUtils::from)
                        .toArray(AnimatedSurface[]::new);
            }
        }

        @Nullable
        @Override
        public AnimatedSurface[] getApps() {
            return mApps;
        }

        @Nullable
        @Override
        public AnimatedSurface[] getWallpapers() {
            return mWallpapers;
        }

        @Nullable
        @Override
        public AnimatedSurface[] getNonApps() {
            return mNonApps;
        }

        @Nullable
        @Override
        public SurfaceControl.Transaction getStartTransaction() {
            return null;
        }

        @Override
        public boolean hasFinishedCallback() {
            return finishedCallbackCompat != null;
        }

        @Override
        public void invokeCallback(
                @Nullable SurfaceControl.Transaction transaction) throws RemoteException {
            if (finishedCallbackCompat == null) {
                return;
            }

            finishedCallbackCompat.onAnimationFinished();
        }

        @Override
        public Params copyWith(ThrowingCallback callback) {
            IRemoteAnimationFinishedCallback wrapped = new IRemoteAnimationFinishedCallback() {
                @Override
                public void onAnimationFinished() throws RemoteException {
                    callback.invoke(null /* transaction */);
                }

                @Override
                public IBinder asBinder() {
                    if (finishedCallbackCompat != null) {
                        return finishedCallbackCompat.asBinder();
                    } else {
                        return new Binder();
                    }
                }
            };

            return new ParamsLegacy(
                    startTime, fadeoutDuration, transit, appsCompat, wallpapersCompat,
                    nonAppsCompat, wrapped);
        }
    }

    /** Parameters for the pre-Shell remote animation APIs. */
    private static class ParamsShell extends Params {
        @Nullable public final IBinder token;
        @Nullable public final TransitionInfo info;
        @Nullable public final SurfaceControl.Transaction transaction;
        @Nullable public final IRemoteTransitionFinishedCallback finishedCallbackShell;

        @Nullable private AnimatedSurface[] mApps;
        @Nullable private AnimatedSurface[] mWallpapers;
        @Nullable private AnimatedSurface[] mNonApps;

        ParamsShell(
                long startTime, long fadeoutDuration, @Nullable IBinder token,
                @Nullable TransitionInfo info, @Nullable SurfaceControl.Transaction transaction,
                @Nullable IRemoteTransitionFinishedCallback finishedCallbackShell) {
            super(startTime, fadeoutDuration);

            this.token = token;
            this.info = info;
            this.transaction = transaction;
            this.finishedCallbackShell = finishedCallbackShell;

            if (info != null) {
                TransitionUtil.LeafTaskFilter leafTaskFilter =
                        new TransitionUtil.LeafTaskFilter(info);
                List<AnimatedSurface> apps = new ArrayList<>();
                List<AnimatedSurface> wallpapers = new ArrayList<>();
                List<AnimatedSurface> nonApps = new ArrayList<>();
                final int numChanges = info.getChanges().size();
                for (int i = 0; i < info.getChanges().size(); i++) {
                    TransitionInfo.Change change = info.getChanges().get(i);
                    if (leafTaskFilter.test(change)) {
                        apps.add(AnimatedSurfaceUtils.from(change, numChanges - i));
                    }
                    if (TransitionUtil.isWallpaper(change)) {
                        wallpapers.add(AnimatedSurfaceUtils.from(change, numChanges - i));
                    }
                    if (TransitionUtil.isNonApp(change)) {
                        nonApps.add(AnimatedSurfaceUtils.from(change, numChanges - i));
                    }
                }
                this.mApps = apps.toArray(AnimatedSurface[]::new);
                this.mWallpapers = wallpapers.toArray(AnimatedSurface[]::new);
                this.mNonApps = nonApps.toArray(AnimatedSurface[]::new);
            }
        }

        @Nullable
        @Override
        public AnimatedSurface[] getApps() {
            return mApps;
        }

        @Nullable
        @Override
        public AnimatedSurface[] getWallpapers() {
            return mWallpapers;
        }

        @Nullable
        @Override
        public AnimatedSurface[] getNonApps() {
            return mNonApps;
        }

        @Nullable
        @Override
        public SurfaceControl.Transaction getStartTransaction() {
            return transaction;
        }

        @Override
        public boolean hasFinishedCallback() {
            return finishedCallbackShell != null;
        }

        @Override
        public void invokeCallback(
                @Nullable SurfaceControl.Transaction transaction) throws RemoteException {
            if (finishedCallbackShell == null) {
                return;
            }

            finishedCallbackShell.onTransitionFinished(null /* wct */, transaction);
        }

        @Override
        public Params copyWith(ThrowingCallback callback) {
            IRemoteTransitionFinishedCallback wrapped = new IRemoteTransitionFinishedCallback() {
                @Override
                public void onTransitionFinished(
                        WindowContainerTransaction windowContainerTransaction,
                        SurfaceControl.Transaction transaction) throws RemoteException {
                    callback.invoke(transaction);
                }

                @Override
                public IBinder asBinder() {
                    if (finishedCallbackShell != null) {
                        return finishedCallbackShell.asBinder();
                    } else {
                        return new Binder();
                    }
                }
            };

            return new ParamsShell(
                    startTime, fadeoutDuration, token, info, transaction, wrapped);
        }
    }

    /** Wrapper for an underlying callback which also throws a {@link RemoteException}. */
    @FunctionalInterface
    public interface ThrowingCallback {
        void invoke(@Nullable SurfaceControl.Transaction transaction) throws RemoteException;
    }

    private enum Mode {
        LEGACY,
        SHELL,
    }
}
