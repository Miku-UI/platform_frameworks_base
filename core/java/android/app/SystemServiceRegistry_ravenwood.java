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
package android.app;

import android.annotation.NonNull;
import android.app.SystemServiceRegistry.CachedServiceFetcher;
import android.app.SystemServiceRegistry.ServiceFetcher;
import android.app.admin.DevicePolicyManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.IUserManager;
import android.os.PermissionEnforcer;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;
import android.os.UserManager;
import android.platform.test.ravenwood.RavenwoodPermissionEnforcer;
import android.platform.test.ravenwood.RavenwoodUnsupportedApiException;
import android.ravenwood.example.BlueManager;
import android.ravenwood.example.RedManager;
import android.telephony.euicc.EuiccManager;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.view.accessibility.AccessibilityManager;
import android.view.autofill.AutofillManager;
import android.view.autofill.IAutoFillManager;
import android.view.contentcapture.ContentCaptureManager;
import android.view.inputmethod.InputMethodManager;
import android.view.textclassifier.TextClassificationManager;
import android.view.textservice.TextServicesManager;

import com.android.internal.policy.PhoneLayoutInflater;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

public class SystemServiceRegistry_ravenwood {
    private SystemServiceRegistry_ravenwood() {
    }

    /**
     * RavenwoodEquivalent of {@link SystemServiceRegistry#onUnknownSystemServiceError}.
     */
    static void onUnknownSystemServiceError(String name) {
        throw new RavenwoodUnsupportedApiException(String.format("The system service '%s'", name))
                .setReason(String.format("Context#getSystemService(%s)", name));
    }

    /**
     * RavenwoodEquivalent of {@link SystemServiceRegistry#registerServices}.
     *
     * TODO: Extract the common part between this and the other method to reuse the
     * same code on Ravenwood. (For now, we don't want to touch the production code
     * for Ravenwood.)
     */
    static void registerServices() {
        // This is the same as the real one.
        registerService(Context.CLIPBOARD_SERVICE, ClipboardManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public ClipboardManager createService(ContextImpl ctx)
                            throws ServiceNotFoundException {
                        return new ClipboardManager(ctx.getOuterContext(),
                                ctx.mMainThread.getHandler());
                    }});

        // For now, we use a custom version of PermissionEnforcer on Ravenwood,
        // which skips all permission checks.
        registerService(Context.PERMISSION_ENFORCER_SERVICE, PermissionEnforcer.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public PermissionEnforcer createService(ContextImpl ctx) {
                        return new RavenwoodPermissionEnforcer();
                    }});

        registerExperimentalServices();
        registerStubServices();
        registerNullServices();
        registerRavenwoodSpecificServices();
    }

    /**
     * Wrapper method for registerServiceForRavenwood(), just so we can use the name
     * "registerService" in this class.
     */
    private static <T> void registerService(@NonNull String serviceName,
            @NonNull Class<T> serviceClass, @NonNull ServiceFetcher<T> serviceFetcher) {
        SystemServiceRegistry.registerServiceForRavenwood(
                serviceName, serviceClass, serviceFetcher);
    }

    private static void registerRavenwoodSpecificServices() {
        // Additional services we provide for testing purposes
        registerService(BlueManager.SERVICE_NAME, BlueManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public BlueManager createService(ContextImpl ctx) {
                        return new BlueManager();
                    }
                });
        registerService(RedManager.SERVICE_NAME, RedManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public RedManager createService(ContextImpl ctx) {
                        return new RedManager();
                    }
                });
    }

    /**
     * Register "experimental" system services, which are _not_ supported. They're used only for
     * Ravenwood internal development.
     */
    private static void registerExperimentalServices() {
        registerService(Context.INPUT_SERVICE, InputManager.class,
                new CachedServiceFetcher<InputManager>() {
            @Override
            public InputManager createService(ContextImpl ctx) {
                return new InputManager(ctx.getOuterContext());
            }});

        registerService(Context.INPUT_METHOD_SERVICE, InputMethodManager.class,
                new ServiceFetcher<InputMethodManager>() {
            @Override
            public InputMethodManager getService(ContextImpl ctx) {
                return InputMethodManager.forContext(ctx.getOuterContext());
            }});

        registerService(Context.WINDOW_SERVICE, WindowManager.class,
                new CachedServiceFetcher<WindowManager>() {
            @Override
            public WindowManager createService(ContextImpl ctx) {
                return new WindowManagerImpl(ctx);
            }});

        registerService(Context.LAYOUT_INFLATER_SERVICE, LayoutInflater.class,
                new CachedServiceFetcher<LayoutInflater>() {
            @Override
            public LayoutInflater createService(ContextImpl ctx) {
                return new PhoneLayoutInflater(ctx.getOuterContext());
            }});

        registerService(Context.USER_SERVICE, UserManager.class,
                new CachedServiceFetcher<UserManager>() {
            @Override
            public UserManager createService(ContextImpl ctx) throws ServiceNotFoundException {
                IBinder b = ServiceManager.getServiceOrThrow(Context.USER_SERVICE);
                IUserManager service = IUserManager.Stub.asInterface(b);
                return new UserManager(ctx, service);
            }});

        registerService(Context.AUTOFILL_SERVICE, AutofillManager.class,
                new CachedServiceFetcher<AutofillManager>() {
            @Override
            public AutofillManager createService(ContextImpl ctx) throws ServiceNotFoundException {
                // Get the services without throwing as this is an optional feature
                IBinder b = ServiceManager.getService(Context.AUTOFILL_SERVICE);
                IAutoFillManager service = IAutoFillManager.Stub.asInterface(b);
                return new AutofillManager(ctx.getOuterContext(), service);
            }});

        registerService(Context.DISPLAY_SERVICE, DisplayManager.class,
                new CachedServiceFetcher<>() {
            @Override
            public DisplayManager createService(ContextImpl ctx) {
                return new DisplayManager(ctx.getOuterContext());
            }});
    }

    private static void registerStubServices() {
        Objenesis objenesis = new ObjenesisStd();
        registerService(Context.ACTIVITY_SERVICE, ActivityManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public ActivityManager createService(ContextImpl ctx) {
                        return objenesis.newInstance(ActivityManager.class);
                    }
                });
        registerService(Context.AUDIO_SERVICE, AudioManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public AudioManager createService(ContextImpl ctx) {
                        return objenesis.newInstance(AudioManager.class);
                    }
                });
        registerService(Context.DEVICE_POLICY_SERVICE, DevicePolicyManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public DevicePolicyManager createService(ContextImpl ctx) {
                        return objenesis.newInstance(DevicePolicyManager.class);
                    }
                });
        registerService(Context.POWER_SERVICE, PowerManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public PowerManager createService(ContextImpl ctx) {
                        return objenesis.newInstance(PowerManager.class);
                    }
                });
        registerService(Context.WALLPAPER_SERVICE, WallpaperManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public WallpaperManager createService(ContextImpl ctx) {
                        return DisabledWallpaperManager.getInstance();
                    }
                });
        registerService(Context.ACCESSIBILITY_SERVICE, AccessibilityManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public AccessibilityManager createService(ContextImpl ctx) {
                        return objenesis.newInstance(AccessibilityManager.class);
                    }
                });
    }

    private static void registerNullServices() {
        registerService(Context.CONTENT_CAPTURE_MANAGER_SERVICE, ContentCaptureManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public ContentCaptureManager createService(ContextImpl ctx) {
                        return null;
                    }
                });
        registerService(Context.EUICC_SERVICE, EuiccManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public EuiccManager createService(ContextImpl ctx) {
                        return null;
                    }
                });
        registerService(Context.UI_MODE_SERVICE, UiModeManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public UiModeManager createService(ContextImpl ctx) {
                        return null;
                    }
                });
        registerService(Context.TEXT_CLASSIFICATION_SERVICE, TextClassificationManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public TextClassificationManager createService(ContextImpl ctx) {
                        return null;
                    }
                });
        registerService(Context.TEXT_SERVICES_MANAGER_SERVICE, TextServicesManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public TextServicesManager createService(ContextImpl ctx) {
                        // TextServicesManager is optional, see SystemServerRegistry
                        return null;
                    }
                });
    }
}
