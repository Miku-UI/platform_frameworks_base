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

package com.android.systemui.animation;

/**
 * Interface to support app interactions with the transition/animation framework. Apps choosing
 * to implement this can override the callback methods in which they are interested -
 * specifically:
 *
 * <ul>
 *   <li/>Apps that want to run a coordinated, in-app animation immediately prior to the system
 *       provided launch animation should override
 *       {@link #onPreLaunchAnimationReady(IPreLaunchAnimationFinishedCallback)}.
 *       Upon receiving this callback, the app can safely run the pre-launch animation. Once the
 *       animation is complete, the app should invoke the `onDone.run()` runnable to signal the
 *       system to proceed with the launch animation. If the pre-launch animation runs beyond
 *       the time allowed by the system it will be truncated and the launch animation will
 *       proceed.
 *   <li>Apps that want to be notified to be able to the start/finish of a coordinated launch
 *       transition should use {@link #onLaunchAnimationStarted()} and {@link
 *       #onLaunchAnimationFinished()} respectively.
 *   <li>Similarly, apps that want to be notified to be able to the start/finish of a
 *       coordinated return transition should use {@link #onReturnAnimationStarted()} and {@link
 *       #onReturnAnimationFinished()} respectively.
 * </ul>
 */
public interface IOriginTransitionCallback {

    /**
     * Notify the client that the launch transition is ready to start. Clients can override this
     * method if they want to run a *pre* app launch animation in coordination with the actual
     * app launch transition/animation provided by the framework. The client should run its
     * animation and then immediately invoke the finishCallback which will signal the framework to
     * start the app launch animation. If the duration of the pre-launch animation exceeds the
     * system timeout (default is 500ms), it will be truncated and the system will proceed with the
     * launch animation.
     *
     * @param finishCallback the finish callback  to indicate that the prelaunch animation is
     *     complete and the system should start the launch animation.
     */
    void onPreLaunchAnimationReady(IPreLaunchAnimationFinishedCallback finishCallback);

    /** Notify the client that the app launch animation has started. */
    void onLaunchAnimationStarted();

    /** Notify the client that the app launch animation has finished. */
    void onLaunchAnimationFinished();

    /** Notify the client that the app return animation has started. */
    void onReturnAnimationStarted();

    /** Notify the client that the app return animation has finished. */
    void onReturnAnimationFinished();
}
