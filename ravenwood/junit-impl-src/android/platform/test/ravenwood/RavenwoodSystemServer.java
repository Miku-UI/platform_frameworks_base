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

import android.app.IActivityClientController;
import android.app.IActivityTaskManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.hardware.display.IDisplayManager;
import android.hardware.input.IInputManager;
import android.os.IUserManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.platform.test.ravenwood.RavenwoodProxyHelper.BinderHelper;
import android.ravenwood.example.BlueManager;
import android.ravenwood.example.RedManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.IWindowSession;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.WindowManagerGlobal;
import android.view.autofill.IAutoFillManager;

import com.android.internal.view.IInputMethodManager;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.SystemServiceManager;
import com.android.server.compat.PlatformCompat;
import com.android.server.compat.PlatformCompatNative;
import com.android.server.utils.TimingsTraceAndSlog;

import java.util.Collection;
import java.util.Set;

public class RavenwoodSystemServer {

    /**
     * Set of services that we know how to provide under Ravenwood. We keep this set distinct
     * from {@code com.android.server.SystemServer} to give us the ability to choose either
     * "real" or "fake" implementations based on the commitments of the service owner.
     *
     * Map from {@code FooManager.class} to the {@code com.android.server.SystemService}
     * lifecycle class name used to instantiate and drive that service.
     */
    private static final ArrayMap<Class<?>, String> sKnownServices = new ArrayMap<>();

    static {
        // Services provided by a typical shipping device
        sKnownServices.put(ClipboardManager.class,
                "com.android.server.FakeClipboardService$Lifecycle");

        // Additional services we provide for testing purposes
        sKnownServices.put(BlueManager.class,
                "com.android.server.example.BlueManagerService$Lifecycle");
        sKnownServices.put(RedManager.class,
                "com.android.server.example.RedManagerService$Lifecycle");
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
                IUserManager_ravenwood.sIBinder.getIBinder());

        ServiceManager.addService(Context.ACTIVITY_TASK_SERVICE,
                IActivityTaskManager_ravenwood.sIBinder.getIBinder());

        ServiceManager.addService(Context.WINDOW_SERVICE,
                IWindowManager_ravenwood.sIBinder.getIBinder());

        ServiceManager.addService(Context.DISPLAY_SERVICE,
                IDisplayManager_ravenwood.sIBinder.getIBinder());

        ServiceManager.addService(Context.INPUT_SERVICE,
                IInputManager_ravenwood.sIBinder.getIBinder());

        ServiceManager.addService(Context.INPUT_METHOD_SERVICE,
                IInputMethodManager_ravenwood.sIBinder.getIBinder());

        ServiceManager.addService(Context.AUTOFILL_MANAGER_SERVICE,
                IAutoFillManager_ravenwood.sIBinder.getIBinder());

        WindowManagerGlobal.setWindowManagerServiceForSystemProcess(
                IWindowManager_ravenwood.sIBinder.getObject());
    }

    public static void reset() {
        // TODO: consider introducing shutdown boot phases

        LocalServices.removeServiceForTest(SystemServiceManager.class);
        sServiceManager = null;
        sTimings = null;
        sStartedServices = null;
    }

    private static void startServices(Collection<Class<?>> serviceClasses) {
        for (Class<?> serviceClass : serviceClasses) {
            // Quietly ignore duplicate requests if service already started
            if (sStartedServices.contains(serviceClass)) continue;
            sStartedServices.add(serviceClass);

            final String serviceName = sKnownServices.get(serviceClass);
            if (serviceName == null) {
                throw new RuntimeException("The requested service " + serviceClass
                        + " is not yet supported under the Ravenwood deviceless testing "
                        + "environment; consider requesting support from the API owner or "
                        + "consider using Mockito; more details at go/ravenwood");
            }

            // Start service and then depth-first traversal of any dependencies
            final SystemService instance = sServiceManager.startService(serviceName);
            startServices(instance.getDependencies());
        }
    }

    /**
     * Minimal implementation of {@link IUserManager} to allow experimental APIs to work.
     */
    public static class IUserManager_ravenwood {
        private static final String TAG = "IUserManager_ravenwood";

        public static final BinderHelper<IUserManager> sIBinder =
                new BinderHelper<>(IUserManager.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                    }
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });
    }

    /**
     * Minimal implementation of {@link IDisplayManager} to allow experimental APIs to work.
     */
    public static class IDisplayManager_ravenwood {
        private static final String TAG = "IDisplayManager_ravenwood";

        public static final BinderHelper<IDisplayManager> sIBinder =
                new BinderHelper<>(IDisplayManager.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getDisplayInfo":
                            return new DisplayInfo();
                    }
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });
    }


    /**
     * Minimal implementation of {@link IInputManager} to allow experimental APIs to work.
     */
    public static class IInputManager_ravenwood {
        private static final String TAG = "IInputManager_ravenwood";

        private static final int VIRTUAL_KEYBOARD = -1;

        private static InputDevice getDefaultInputDevice() {
            return new InputDevice.Builder()
                    .setId(VIRTUAL_KEYBOARD)
                    .setKeyCharacterMap(KeyCharacterMap.obtainEmptyMap(VIRTUAL_KEYBOARD))
                    .build();
        }

        public static final BinderHelper<IInputManager> sIBinder =
                new BinderHelper<>(IInputManager.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getInputDeviceIds":
                            return new int[]{VIRTUAL_KEYBOARD};
                        case "getInputDevice":
                            return getDefaultInputDevice(); // TODO Cache it?
                    }
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });
    }

    /**
     * Minimal implementation of {@link IWindowManager} to allow experimental APIs to work.
     */
    public static class IWindowManager_ravenwood {
        private static final String TAG = "IWindowManager_ravenwood";

        public static final BinderHelper<IWindowManager> sIBinder =
                new BinderHelper<>(IWindowManager.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "openSession":
                            return IWindowSession_ravenwood.sIBinder.getObject();
                    }
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });
    }

    /**
     * Minimal implementation of {@link IWindowSession} to allow experimental APIs to work.
     */
    public static class IWindowSession_ravenwood {
        private static final String TAG = "IWindowSession_ravenwood";

        public static final BinderHelper<IWindowSession> sIBinder =
                new BinderHelper<>(IWindowSession.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                    case "addToDisplayAsUser":
                        return 0;
                    case "setOnBackInvokedCallbackInfo":
                        return null;
                    case "relayout":
                        return 0; //"int Result flags, defined in {@link WindowManagerGlobal}."
                    }
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });
    }

    /**
     * Minimal implementation of {@link IActivityClientController} to allow experimental APIs
     * to work.
     */
    public static class IActivityTaskManager_ravenwood {
        private static final String TAG = "IActivityTaskManager_ravenwood";

        public static final BinderHelper<IActivityClientController> sACC =
                new BinderHelper<>(IActivityClientController.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "finishActivity":
                        case "setTaskDescription":
                            return true;
                    }
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });

        public static final BinderHelper<IActivityTaskManager> sIBinder =
                new BinderHelper<>(IActivityTaskManager.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getActivityClientController":
                            return sACC.getObject();
                    }
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });
    }

    /**
     * Minimal implementation of {@link IInputMethodManager} to allow experimental APIs to work.
     */
    public static class IInputMethodManager_ravenwood {
        private static final String TAG = "IInputMethodManager_ravenwood";

        public static final BinderHelper<IInputMethodManager> sIBinder =
                new BinderHelper<>(IInputMethodManager.class, (proxy, method, args) -> {
                    switch (method.getName()) {
                    }
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });
    }

    /**
     * Minimal implementation of {@link IAutoFillManager} to allow experimental APIs to work.
     */
    public static class IAutoFillManager_ravenwood {
        private static final String TAG = "IAutoFillManager_ravenwood";

        public static final BinderHelper<IAutoFillManager> sIBinder =
                new BinderHelper<>(IAutoFillManager.class, (proxy, method, args) -> {
                    return RavenwoodProxyHelper.sDefaultHandler.invoke(proxy, method, args);
                });
    }
}
