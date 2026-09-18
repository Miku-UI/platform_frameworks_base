/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.platform.test.ravenwood;

import static android.platform.test.ravenwood.RavenwoodExperimentalApiChecker.isExperimentalApiEnabled;
import static android.platform.test.ravenwood.RavenwoodProxyHelper.newExperimentalProxy;
import static android.platform.test.ravenwood.RavenwoodProxyHelper.newProxy;
import static android.platform.test.ravenwood.RavenwoodProxyHelper.sDefaultHandler;
import static android.platform.test.ravenwood.RavenwoodProxyHelper.sNotImplementedHandler;

import android.app.IActivityClientController;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentService;
import android.content.IIntentSender;
import android.hardware.display.IDisplayManager;
import android.hardware.input.IInputManager;
import android.hardware.input.IInputManager_ravenwood;
import android.os.IUserManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.ravenwood.example.BlueManager;
import android.ravenwood.example.RedManager;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.IWindowSession;
import android.view.WindowManagerGlobal;
import android.view.autofill.IAutoFillManager;

import com.android.internal.view.IInputMethodManager;
import com.android.server.FakeClipboardService;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.SystemServiceManager;
import com.android.server.compat.PlatformCompat;
import com.android.server.compat.PlatformCompatNative;
import com.android.server.example.BlueManagerService;
import com.android.server.example.RedManagerService;
import com.android.server.utils.TimingsTraceAndSlog;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Start system services for Ravenwood.
 * (Which is done by SystemServer on a real device.)
 *
 * This class refers to various system service classes, including
 * the real ones and fake ones. These classes are renamed with jarjar/hoststubgen.
 * See ravenwood/texts/ravenwood-services-rename-policies.txt for more details.
 */
public class RavenwoodSystemServer {

    /**
     * Set of services that we know how to provide under Ravenwood. We keep this set distinct
     * from {@code com.android.server.SystemServer} to give us the ability to choose either
     * "real" or "fake" implementations based on the commitments of the service owner.
     *
     * Map from {@code FooManager.class} to the {@code com.android.server.SystemService}
     * lifecycle class name used to instantiate and drive that service.
     */
    private static final ArrayMap<Class<?>, Class<? extends SystemService>> sKnownServices =
            new ArrayMap<>();

    static {
        // Services provided by a typical shipping device
        sKnownServices.put(ClipboardManager.class, FakeClipboardService.Lifecycle.class);

        // Additional services we provide for testing purposes
        sKnownServices.put(BlueManager.class, BlueManagerService.Lifecycle.class);
        sKnownServices.put(RedManager.class, RedManagerService.Lifecycle.class);
    }

    private static Set<Class<?>> sStartedServices;
    private static TimingsTraceAndSlog sTimings;
    private static SystemServiceManager sServiceManager;

    public static void init(Context systemServerContext) {
        // Always start PlatformCompat, regardless of the requested services.
        // PlatformCompat is not really a SystemService, so it won't receive boot phases / etc.
        // This initialization code is copied from SystemServer.java.
        PlatformCompat platformCompat = new PlatformCompat(systemServerContext);
        ServiceManager.addService(Context.PLATFORM_COMPAT_SERVICE, platformCompat);
        ServiceManager.addService(Context.PLATFORM_COMPAT_NATIVE_SERVICE,
                new PlatformCompatNative(platformCompat));

        ServiceManager.addService(Context.INPUT_SERVICE,
                newProxy(IInputManager.class, new IInputManager_ravenwood()).asBinder());

        maybeRegisterExperimentalServices();

        sStartedServices = new ArraySet<>();
        sTimings = new TimingsTraceAndSlog();
        sServiceManager = new SystemServiceManager(systemServerContext);
        sServiceManager.setStartInfo(false,
                SystemClock.elapsedRealtime(),
                SystemClock.uptimeMillis());
        LocalServices.addService(SystemServiceManager.class, sServiceManager);

        startServices(sKnownServices.keySet());
        sServiceManager.sealStartedServices();

        // TODO: expand to include additional boot phases when relevant
        sServiceManager.startBootPhase(sTimings, SystemService.PHASE_SYSTEM_SERVICES_READY);
        sServiceManager.startBootPhase(sTimings, SystemService.PHASE_BOOT_COMPLETED);
    }

    private static void maybeRegisterExperimentalServices() {
        if (!isExperimentalApiEnabled()) {
            return;
        }
        ServiceManager.addService(Context.USER_SERVICE,
                IUserManager_ravenwood.sIBinder.asBinder());

        ServiceManager.addService(Context.ACTIVITY_TASK_SERVICE,
                IActivityTaskManager_ravenwood.sIBinder.asBinder());

        ServiceManager.addService(Context.WINDOW_SERVICE,
                IWindowManager_ravenwood.sIBinder.asBinder());

        ServiceManager.addService(Context.DISPLAY_SERVICE,
                IDisplayManager_ravenwood.sIBinder.asBinder());

        ServiceManager.addService(Context.INPUT_METHOD_SERVICE,
                IInputMethodManager_ravenwood.sIBinder.asBinder());

        ServiceManager.addService(Context.AUTOFILL_SERVICE,
                IAutoFillManager_ravenwood.sIBinder.asBinder());

        ServiceManager.addService(Context.ACTIVITY_SERVICE,
                IActivityManager_ravenwood.sIBinder.asBinder());

        ServiceManager.addService(ContentResolver.CONTENT_SERVICE_NAME,
                IContentService_ravenwood.sIBinder.asBinder());

        ServiceManager.addService(DreamService.DREAM_SERVICE,
                IDreamManager_ravenwood.sIBinder.asBinder());

        WindowManagerGlobal.setWindowManagerServiceForSystemProcess(
                IWindowManager_ravenwood.sIBinder);
    }

    public static void reset() {
        // TODO: consider introducing shutdown boot phases

        LocalServices.removeServiceForTest(SystemServiceManager.class);
        sServiceManager = null;
        sTimings = null;
        sStartedServices = null;
    }

    private static void startServices(Collection<Class<?>> managerClasses) {
        for (Class<?> managerClass : managerClasses) {
            // Quietly ignore duplicate requests if service already started
            if (sStartedServices.contains(managerClass)) continue;
            sStartedServices.add(managerClass);

            final Class<? extends SystemService> serviceClass = sKnownServices.get(managerClass);
            if (serviceClass == null) {
                throw new RavenwoodUnsupportedApiException("The requested service " + managerClass)
                        .setReason(managerClass.getName());
            }

            // Start service and then depth-first traversal of any dependencies
            final SystemService instance = sServiceManager.startService(serviceClass);
            startServices(instance.getDependencies());
        }
    }

    /**
     * Minimal implementation of {@link IUserManager} to allow experimental APIs to work.
     */
    public static class IUserManager_ravenwood {
        private static final String TAG = "IUserManager_ravenwood";

        public static final IUserManager sIBinder =
                newExperimentalProxy(IUserManager.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getUserRestrictionSources" -> Collections.emptyList();
                        case "isHeadlessSystemUserMode" -> false;
                        case "isUserUnlockingOrUnlocked" -> true;
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });
    }

    /**
     * Minimal implementation of {@link IDisplayManager} to allow experimental APIs to work.
     */
    public static class IDisplayManager_ravenwood {
        private static final String TAG = "IDisplayManager_ravenwood";

        public static final IDisplayManager sIBinder =
                newExperimentalProxy(IDisplayManager.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getDisplayInfo" -> new DisplayInfo();
                        case "getDisplayIds" -> new int[]{Display.DEFAULT_DISPLAY};
                        case "getOverlaySupport",
                             "getPreferredWideGamutColorSpaceId",
                             "registerCallbackWithEventMask" ->
                                sDefaultHandler.invoke(proxy, method, args);
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });
    }

    /**
     * Minimal implementation of {@link IWindowManager} to allow experimental APIs to work.
     */
    public static class IWindowManager_ravenwood {
        private static final String TAG = "IWindowManager_ravenwood";

        public static final IWindowManager sIBinder =
                newExperimentalProxy(IWindowManager.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "openSession" -> IWindowSession_ravenwood.sIBinder;
                        case "getWindowInsets",
                             "hasNavigationBar",
                             "setInTouchModeOnAllDisplays",
                             "syncInputTransactions",
                             "attachWindowContextToDisplayArea" ->
                                sDefaultHandler.invoke(proxy, method, args);
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });
    }

    /**
     * Minimal implementation of {@link IWindowSession} to allow experimental APIs to work.
     */
    public static class IWindowSession_ravenwood {
        private static final String TAG = "IWindowSession_ravenwood";

        public static final IWindowSession sIBinder =
                newExperimentalProxy(IWindowSession.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "addToDisplayAsUser",
                             "onRectangleOnScreenRequested",
                             "relayout",
                             "reportSystemGestureExclusionChanged",
                             "setOnBackInvokedCallbackInfo",
                             "updateRequestedVisibleTypes" ->
                                sDefaultHandler.invoke(proxy, method, args);
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });
    }

    /**
     * Minimal implementation of {@link IActivityClientController} to allow experimental APIs
     * to work.
     */
    public static class IActivityTaskManager_ravenwood {
        private static final String TAG = "IActivityTaskManager_ravenwood";

        public static final IActivityClientController sACC =
                newExperimentalProxy(IActivityClientController.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "finishActivity" -> true;
                        case "setTaskDescription",
                             "setTaskDescriptionOneWay" -> null;
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });

        public static final IActivityTaskManager sIBinder =
                newExperimentalProxy(IActivityTaskManager.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getActivityClientController" -> sACC;
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });
    }

    /**
     * Minimal implementation of {@link IInputMethodManager} to allow experimental APIs to work.
     */
    public static class IInputMethodManager_ravenwood {
        private static final String TAG = "IInputMethodManager_ravenwood";

        public static final IInputMethodManager sIBinder =
                newExperimentalProxy(IInputMethodManager.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "addClient",
                             "getImeTrackerService",
                             "startInputOrWindowGainedFocus" ->
                                sDefaultHandler.invoke(proxy, method, args);
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });
    }

    /**
     * Minimal implementation of {@link IAutoFillManager} to allow experimental APIs to work.
     */
    public static class IAutoFillManager_ravenwood {
        private static final String TAG = "IAutoFillManager_ravenwood";

        public static final IAutoFillManager sIBinder =
                newExperimentalProxy(IAutoFillManager.class, sDefaultHandler);
    }

    /**
     * Minimal implementation of {@link IActivityManager} to allow experimental APIs to work.
     */
    public static class IActivityManager_ravenwood {
        private static final String TAG = "IActivityManager_ravenwood";

        public static final IActivityManager sIBinder =
                newExperimentalProxy(IActivityManager.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getIntentSenderWithFeature" ->
                                newExperimentalProxy(IIntentSender.class, sNotImplementedHandler);
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });
    }

    /**
     * Minimal implementation of {@link IContentService} to allow experimental APIs to work.
     */
    public static class IContentService_ravenwood {
        private static final String TAG = "IContentService_ravenwood";

        public static final IContentService sIBinder =
                newExperimentalProxy(IContentService.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "registerContentObserver",
                             "unregisterContentObserver" ->
                                sDefaultHandler.invoke(proxy, method, args);
                        default -> sNotImplementedHandler.invoke(proxy, method, args);
                    };
                });
    }

    /**
     * Minimal implementation of {@link IDreamManager} to allow experimental APIs to work.
     */
    public static class IDreamManager_ravenwood {
        private static final String TAG = "IDreamManager_ravenwood";

        public static final IDreamManager sIBinder =
                newExperimentalProxy(IDreamManager.class, sNotImplementedHandler);
    }
}
