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

package com.android.server.security.advancedprotection;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.test.FakePermissionEnforcer;
import android.os.test.TestLooper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionFeature;
import android.security.advancedprotection.AdvancedProtectionFeature.ProvisioningMode;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.security.advancedprotection.AdvancedProtectionManager.FeatureId;
import android.security.advancedprotection.IAdvancedProtectionCallback;
import android.security.advancedprotection.IAdvancedProtectionFeatureCallback;

import androidx.annotation.NonNull;

import com.android.server.pm.UserManagerInternal;
import com.android.server.security.advancedprotection.features.AdvancedProtectionHook;
import com.android.server.security.advancedprotection.features.AdvancedProtectionProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@SuppressLint("VisibleForTests")
@RunWith(JUnitParamsRunner.class)
public class AdvancedProtectionServiceTest {
    private static final int HOOK_ID = AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G;
    private static final String HOOK_NAME = "DISALLOW_CELLULAR_2G";
    private static final int PROVIDER_ID =
            AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES;
    private static final String PROVIDER_NAME = "DISALLOW_INSTALL_UNKNOWN_SOURCES";

    private FakePermissionEnforcer mPermissionEnforcer;
    private UserManagerInternal mUserManager;
    private Context mContext;
    private TestLooper mLooper;
    private AdvancedProtectionConfigLoader.Injector mInjector;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setup() throws Settings.SettingNotFoundException {
        mContext = mock(Context.class);
        mUserManager = mock(UserManagerInternal.class);
        mInjector = new AdvancedProtectionConfigLoader.Injector();
        mLooper = new TestLooper();
        mPermissionEnforcer = new FakePermissionEnforcer();
        mPermissionEnforcer.grant(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        mPermissionEnforcer.grant(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        Mockito.when(mUserManager.getUserInfo(ArgumentMatchers.anyInt()))
                .thenReturn(new UserInfo(0, "user", UserInfo.FLAG_ADMIN));
    }

    @Test
    public void testToggleProtection() {
        AdvancedProtectionService service = createService(null, null);
        service.setAdvancedProtectionEnabled(true);
        assertTrue(service.isAdvancedProtectionEnabled());

        service.setAdvancedProtectionEnabled(false);
        assertFalse(service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testDisableProtection_byDefault() {
        AdvancedProtectionService service = createService(null, null);
        assertFalse(service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testSetProtection_nonAdminUser() {
        Mockito.when(mUserManager.getUserInfo(ArgumentMatchers.anyInt()))
                .thenReturn(new UserInfo(1, "user2", UserInfo.FLAG_FULL));
        AdvancedProtectionService service = createService(null, null);

        assertThrows(SecurityException.class, () -> service.setAdvancedProtectionEnabled(true));
    }

    @Test
    public void testEnableProtection_withHook() {
        AtomicBoolean callbackCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, callbackCaptor);
        AdvancedProtectionService service = createService(hook, null);

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();

        assertTrue(callbackCaptor.get());
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testEnableProtection_withFeatures_canBeForcedEnabled() throws IOException {
        // TODO: Fix issue where if a config is not mocked, it does not pull the actual file on test device.
        mockSystemConfigWithFeatures();
        AdvancedProtectionHook hook = createHook(/* isAvailable */ false, null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionStore store = createAdvancedProtectionStore();
        AdvancedProtectionService service = createService(hook, provider, store);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertDoesNotContain(features, HOOK_ID);
        assertDoesNotContain(features, PROVIDER_ID);

        service.setAdbProvisioned(HOOK_ID, true);
        service.setAdbProvisioned(PROVIDER_ID, true);
        mockSystemConfigWithFeatures();
        AdvancedProtectionService rebootedService = createService(hook, provider, store);
        List<AdvancedProtectionFeature> rebootedFeatures =
                rebootedService.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertContainsInAnyOrder(rebootedFeatures, HOOK_ID, PROVIDER_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testEnableProtection_withFeatures_canBeForcedDisabled() throws IOException {
        // TODO: Fix issue where if a config is not mocked, it does not pull the actual file on test device.
        mockSystemConfigWithFeatures(HOOK_NAME, PROVIDER_NAME);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionStore store = createAdvancedProtectionStore();
        AdvancedProtectionService service = createService(hook, provider, store);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertContainsInAnyOrder(features, HOOK_ID, PROVIDER_ID);

        service.setAdbProvisioned(HOOK_ID, false);
        service.setAdbProvisioned(PROVIDER_ID, false);
        mockSystemConfigWithFeatures(HOOK_NAME, PROVIDER_NAME);
        AdvancedProtectionService rebootedService = createService(hook, provider, store);
        List<AdvancedProtectionFeature> rebootedFeatures =
                rebootedService.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertDoesNotContain(rebootedFeatures, HOOK_ID);
        assertDoesNotContain(rebootedFeatures, PROVIDER_ID);
    }

    @Test
    public void testEnableProtection_withFeature_notAvailable() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ false, callbackCalledCaptor);
        AdvancedProtectionService service = createService(hook, null);

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();

        assertFalse(callbackCalledCaptor.get());
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testEnableProtection_withFeature_notProvisioned() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, callbackCalledCaptor);
        AdvancedProtectionService service = createService(hook, null);
        service.updateAdvancedProtectionFeaturesProvisioning(null, new int[] {HOOK_ID});

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        assertFalse(callbackCalledCaptor.get());
    }

    @DisableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testEnableProtection_withFeature_v2Disabled_provisioningIgnored() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, callbackCalledCaptor);
        AdvancedProtectionService service = createService(hook, null);
        service.updateAdvancedProtectionFeaturesProvisioning(null, new int[] {HOOK_ID});

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        assertTrue(callbackCalledCaptor.get());
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testEnableProtection_withFeature_provisioned() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, callbackCalledCaptor);
        AdvancedProtectionService service = createService(hook, null);
        service.updateAdvancedProtectionFeaturesProvisioning(new int[] {HOOK_ID}, null);

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        assertTrue(callbackCalledCaptor.get());
    }

    @Test
    public void testEnableProtection_withFeature_notCalledIfModeNotChanged() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, callbackCalledCaptor);
        AdvancedProtectionService service = createService(hook, null);

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();

        assertTrue(callbackCalledCaptor.get());

        callbackCalledCaptor.set(false);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        assertFalse(callbackCalledCaptor.get());
    }

    @Test
    public void testRegisterCallback() throws RemoteException {
        AtomicBoolean callbackCaptor = new AtomicBoolean(false);
        IAdvancedProtectionCallback callback = createCallback(callbackCaptor);
        AdvancedProtectionService service = createService(null, null);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        service.registerAdvancedProtectionCallback(callback);
        mLooper.dispatchNext();

        assertTrue(callbackCaptor.get());

        service.setAdvancedProtectionEnabled(false);
        mLooper.dispatchNext();

        assertFalse(callbackCaptor.get());
    }

    @Test
    public void testUnregisterCallback() throws RemoteException {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        IAdvancedProtectionCallback callback = createCallback(callbackCalledCaptor);
        AdvancedProtectionService service = createService(null, null);

        service.setAdvancedProtectionEnabled(true);
        service.registerAdvancedProtectionCallback(callback);
        mLooper.dispatchAll();
        callbackCalledCaptor.set(false);

        service.unregisterAdvancedProtectionCallback(callback);
        service.setAdvancedProtectionEnabled(false);
        mLooper.dispatchNext();

        assertFalse(callbackCalledCaptor.get());
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testRegisterFeatureCallback() throws RemoteException {
        AtomicBoolean callbackCaptor = new AtomicBoolean(false);
        AdvancedProtectionProvider provider = createProvider();
        IAdvancedProtectionFeatureCallback callback =
                createFeatureCallback(callbackCaptor, PROVIDER_ID);
        AdvancedProtectionService service = createService(null, provider);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        service.registerAdvancedProtectionFeatureCallback(new int[] {PROVIDER_ID}, callback);
        mLooper.dispatchAll();

        assertTrue(callbackCaptor.get());

        service.setAdvancedProtectionEnabled(false);
        mLooper.dispatchAll();

        assertFalse(callbackCaptor.get());
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUnregisterFeatureCallback() throws RemoteException {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionProvider provider = createProvider();
        IAdvancedProtectionFeatureCallback callback =
                createFeatureCallback(callbackCalledCaptor, PROVIDER_ID);
        AdvancedProtectionService service = createService(null, provider);

        service.setAdvancedProtectionEnabled(true);
        service.registerAdvancedProtectionFeatureCallback(new int[] {PROVIDER_ID}, callback);
        mLooper.dispatchAll();
        callbackCalledCaptor.set(false);

        service.unregisterAdvancedProtectionFeatureCallback(callback);
        service.setAdvancedProtectionEnabled(false);
        mLooper.dispatchAll();

        assertFalse(callbackCalledCaptor.get());
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testRegisterFeatureCallback_invalidFeatureId_throwsException() {
        IAdvancedProtectionFeatureCallback callback =
                createFeatureCallback(new AtomicBoolean(false), PROVIDER_ID);
        AdvancedProtectionService service = createService(null, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerAdvancedProtectionFeatureCallback(new int[] {-1}, callback));
    }

    @DisableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertContainsInAnyOrder(features, HOOK_ID, PROVIDER_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_canBeAdbDeprovisioned() throws IOException {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        mockSystemConfigWithFeatures(HOOK_NAME);
        AdvancedProtectionService service = createService(hook, null);
        service.setAdbProvisioned(HOOK_ID, false);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertDoesNotContain(features, HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featureNotAvailable_canBeAdbProvisioned() throws IOException {
        AdvancedProtectionHook hook =
                createHook(/* isAvailable */ false, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);
        service.setAdbProvisioned(HOOK_ID, true);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(
                features, HOOK_ID, AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB);
    }

    @DisableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featureNotAvailable() throws IOException {
        AdvancedProtectionHook hook =
                createHook(/* isAvailable */ false, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertContainsInAnyOrder(features, PROVIDER_ID);
        assertDoesNotContain(features, HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featuresAvailableInHookAndConfig() throws IOException {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        mockSystemConfigWithFeatures(HOOK_NAME, PROVIDER_NAME);
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertContainsInAnyOrder(features, HOOK_ID, PROVIDER_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featureAvailableInHookButNotAvailableInConfig() throws IOException {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        mockSystemConfigWithFeatures(PROVIDER_NAME);
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertContainsInAnyOrder(features, PROVIDER_ID);
        assertDoesNotContain(features, HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featureNotAvailableInHookButAvailableInConfig() throws IOException {
        AdvancedProtectionHook hook =
                createHook(/* isAvailable */ false, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        mockSystemConfigWithFeatures(HOOK_NAME, PROVIDER_NAME);
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertContainsInAnyOrder(features, PROVIDER_ID);
        assertDoesNotContain(features, HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featuresNotAvailableInHookAndConfig() throws IOException {
        // TODO: Fix issue where if a config is not mocked, it does not pull the actual file on device.
        mockSystemConfigWithFeatures();
        AdvancedProtectionHook hook =
                createHook(/* isAvailable */ false, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertTrue(features.isEmpty());
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    @Parameters(method = "parametersFor_testGetFeatures_featureDefaultProvisioningMode")
    public void testGetFeatures_featureDefaultProvisioningMode(
            int featureId, int provisioningMode) {
        AdvancedProtectionHook hook =
                createHook(featureId, /* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(features, featureId, provisioningMode);
    }

    private Object[] parametersFor_testGetFeatures_featureDefaultProvisioningMode() {
        return new Object[] {
            new Object[] {
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT
            },
            new Object[] {
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT
            },
            new Object[] {
                AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT
            },
            new Object[] {
                AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT
            }
        };
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateProvisioning_provisioned() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);

        List<AdvancedProtectionFeature> features =
                service.updateAdvancedProtectionFeaturesProvisioning(new int[] {HOOK_ID}, null);

        assertProvisioningMode(
                features,
                HOOK_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN);

        features = service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(
                features,
                HOOK_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateProvisioning_deprovisioned() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);

        List<AdvancedProtectionFeature> features =
                service.updateAdvancedProtectionFeaturesProvisioning(null, new int[] {HOOK_ID});

        assertProvisioningMode(
                features,
                HOOK_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_FEATURE_ADMIN);

        features = service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(
                features,
                HOOK_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_FEATURE_ADMIN);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateProvisioning_provisionedAndDeprovisioned_differentFeatureIds() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features =
                service.updateAdvancedProtectionFeaturesProvisioning(
                        new int[] {HOOK_ID}, new int[] {PROVIDER_ID});

        assertProvisioningMode(
                features,
                HOOK_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN);
        assertProvisioningMode(
                features,
                PROVIDER_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_FEATURE_ADMIN);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateProvisioning_provisionedAndDeprovisioned_sameFeatureId() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.updateAdvancedProtectionFeaturesProvisioning(
                                new int[] {HOOK_ID}, new int[] {HOOK_ID}));
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateProvisioning_emptyFeatureIds() {
        AdvancedProtectionService service = createService(null, null);

        List<AdvancedProtectionFeature> features =
                service.updateAdvancedProtectionFeaturesProvisioning(new int[] {}, new int[] {});

        assertEquals(features.size(), 0);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateProvisioning_nullFeatureIds() {
        AdvancedProtectionService service = createService(null, null);

        List<AdvancedProtectionFeature> features =
                service.updateAdvancedProtectionFeaturesProvisioning(null, null);

        assertEquals(features.size(), 0);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateProvisioning_doesNotTriggerRegularCallbacks() throws RemoteException {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        IAdvancedProtectionCallback callback = createCallback(callbackCalledCaptor);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, null);
        AdvancedProtectionService service = createService(hook, null);

        service.setAdvancedProtectionEnabled(true);
        service.registerAdvancedProtectionCallback(callback);
        mLooper.dispatchAll();
        callbackCalledCaptor.set(false);

        service.updateAdvancedProtectionFeaturesProvisioning(new int[] {HOOK_ID}, null);
        mLooper.dispatchAll();

        assertFalse(callbackCalledCaptor.get());
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateProvisioning_invalidFeatureId() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateAdvancedProtectionFeaturesProvisioning(new int[] {-1}, null));
    }

    @DisableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_apiV2Disabled_returnsDefaultProvisioningMode() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);
        service.updateAdvancedProtectionFeaturesProvisioning(null, new int[] {HOOK_ID});

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(
                features,
                HOOK_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_withFeatureIds_returnsOnlyRequestedFeatures() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(new int[] {HOOK_ID});

        assertEquals(features.size(), 1);
        assertEquals(features.get(0).getId(), HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_withFeatureIds_throwsExceptionForInvalidFeatureId() {
        AdvancedProtectionService service = createService(null, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getAdvancedProtectionFeatures(new int[] {-1}));
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testAdbProvisioning_provisioned() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);
        service.setAdbProvisioned(HOOK_ID, true);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(
                features, HOOK_ID, AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testAdbProvisioning_deprovisioned() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);
        service.setAdbProvisioned(HOOK_ID, false);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertDoesNotContain(features, HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testAdbProvisioning_removeProvisioning() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);
        service.setAdbProvisioned(HOOK_ID, true);
        service.removeAdbProvisioning(HOOK_ID);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(
                features,
                HOOK_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testAdbAndFeatureAdminProvisioning_adbProvisioningTakesPrecedence() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionService service = createService(hook, null);
        service.setAdbProvisioned(HOOK_ID, true);
        service.updateAdvancedProtectionFeaturesProvisioning(new int[] {HOOK_ID}, null);

        List<AdvancedProtectionFeature> features =
                service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(
                features, HOOK_ID, AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB);

        service.removeAdbProvisioning(HOOK_ID);

        features = service.getAdvancedProtectionFeatures(/* featureIds */ null);

        assertProvisioningMode(
                features,
                HOOK_ID,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN);
    }

    @Test
    public void testSetProtection_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        assertThrows(SecurityException.class, () -> service.setAdvancedProtectionEnabled(true));
    }

    @Test
    public void testGetProtection_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(SecurityException.class, () -> service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testRegisterCallback_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(
                SecurityException.class,
                () ->
                        service.registerAdvancedProtectionCallback(
                                new IAdvancedProtectionCallback.Default()));
    }

    @Test
    public void testUnregisterCallback_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(
                SecurityException.class,
                () ->
                        service.unregisterAdvancedProtectionCallback(
                                new IAdvancedProtectionCallback.Default()));
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testUpdateAdvancedProtectionFeaturesProvisioning_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        assertThrows(
                SecurityException.class,
                () ->
                        service.updateAdvancedProtectionFeaturesProvisioning(
                                new int[] {PROVIDER_ID}, new int[] {HOOK_ID}));
    }

    private void assertContainsInAnyOrder(
            List<AdvancedProtectionFeature> features, Integer... featureIds) {
        assertThat(
                features.stream().map(AdvancedProtectionFeature::getId).toList(),
                containsInAnyOrder(featureIds));
    }

    private void assertDoesNotContain(List<AdvancedProtectionFeature> features, int featureId) {
        assertThat(
                features.stream().map(AdvancedProtectionFeature::getId).toList(),
                not(containsInAnyOrder(featureId)));
    }

    private void assertProvisioningMode(
            List<AdvancedProtectionFeature> features,
            @FeatureId int featureId,
            @ProvisioningMode int provisioningMode) {
        AdvancedProtectionFeature feature =
                features.stream().filter(f -> f.getId() == featureId).findFirst().get();
        assertEquals(featureId, feature.getId());
        assertEquals(provisioningMode, feature.getProvisioningMode());
    }

    private IAdvancedProtectionCallback createCallback(AtomicBoolean callbackCaptor) {
        return new IAdvancedProtectionCallback.Stub() {
            @Override
            public void onAdvancedProtectionChanged(boolean enabled) {
                callbackCaptor.set(enabled);
            }
        };
    }

    private IAdvancedProtectionFeatureCallback createFeatureCallback(
            AtomicBoolean callbackCaptor, @FeatureId int featureId) {
        return new IAdvancedProtectionFeatureCallback.Stub() {
            @Override
            public void onFeatureEnabledChanged(List<AdvancedProtectionFeature> features) {
                features.stream()
                        .filter(f -> f.getId() == featureId)
                        .findFirst()
                        .ifPresent(f -> callbackCaptor.set(f.isEnabled()));
            }
        };
    }

    private AdvancedProtectionHook createHook(
            boolean isAvailable, @Nullable AtomicBoolean callbackCaptor) {
        return createHook(HOOK_ID, isAvailable, callbackCaptor);
    }

    private AdvancedProtectionHook createHook(
            @FeatureId int featureId, boolean isAvailable, @Nullable AtomicBoolean callbackCaptor) {
        return new AdvancedProtectionHook(mContext, true) {
            @Override
            public @FeatureId int getFeatureId() {
                return featureId;
            }

            @Override
            public boolean isAvailable() {
                return isAvailable;
            }

            @Override
            public void onAdvancedProtectionChanged(boolean enabled) {
                if (callbackCaptor != null) {
                    callbackCaptor.set(enabled);
                }
            }
        };
    }

    private AdvancedProtectionProvider createProvider() {
        return new AdvancedProtectionProvider() {
            @Override
            public List<Integer> getFeatureIds(Context context) {
                return List.of(PROVIDER_ID);
            }
        };
    }

    private void mockSystemConfigWithFeatures(String... featureNames) throws IOException {
        String configContent =
                """
                <advanced-protection-config>
                    <available-protections>\
                """;
        for (String featureName : featureNames) {
            configContent += String.format("<protection id=\"%s\" />", featureName);
        }
        configContent +=
                """
                    </available-protections>
                </advanced-protection-config>\
                """;
        mockSystemConfig(configContent);
    }

    private void mockSystemConfig(String configContent) throws IOException {
        mInjector = mock(AdvancedProtectionConfigLoader.Injector.class);
        Mockito.when(mInjector.readSystemConfig())
                .thenReturn(
                        new ByteArrayInputStream(configContent.getBytes(StandardCharsets.UTF_8)));
    }

    private AdvancedProtectionService createService(
            AdvancedProtectionHook hook,
            AdvancedProtectionProvider provider) {
        return new AdvancedProtectionService(
                mContext,
                createAdvancedProtectionStore(),
                mUserManager,
                mLooper.getLooper(),
                mPermissionEnforcer,
                hook,
                provider,
                mInjector);
    }

    private AdvancedProtectionService createService(
            AdvancedProtectionHook hook,
            AdvancedProtectionProvider provider,
            AdvancedProtectionStore store) {
        return new AdvancedProtectionService(
                mContext,
                store,
                mUserManager,
                mLooper.getLooper(),
                mPermissionEnforcer,
                hook,
                provider,
                mInjector);
    }

    private AdvancedProtectionStore createAdvancedProtectionStore() {
        return new AdvancedProtectionStore(mContext) {
            private boolean mAdvancedProtectionModeEnabled = false;
            private boolean mUsbDataProtectionEnabled = false;
            private Map<Integer, Boolean> mFeatureIdToAdminProvisioned = new HashMap<>();
            private Map<Integer, Boolean> mFeatureIdToAdbProvisioned = new HashMap<>();

            @Override
            void saveAdvancedProtectionModeEnabled(boolean enabled) {
                mAdvancedProtectionModeEnabled = enabled;
            }

            @Override
            boolean retrieveAdvancedProtectionModeEnabled() {
                return mAdvancedProtectionModeEnabled;
            }

            @Override
            void saveUsbDataProtectionEnabled(boolean enabled) {
                mUsbDataProtectionEnabled = enabled;
            }

            @Override
            boolean retrieveUsbDataProtectionEnabled() {
                return mUsbDataProtectionEnabled;
            }

            @Override
            void saveFeatureAdminProvisioned(@FeatureId int featureId, boolean isProvisioned) {
                mFeatureIdToAdminProvisioned.put(featureId, isProvisioned);
            }

            @Override
            Boolean retrieveFeatureAdminProvisioned(@FeatureId int featureId) {
                return mFeatureIdToAdminProvisioned.get(featureId);
            }

            @Override
            void saveFeatureAdbProvisioned(@FeatureId int featureId, boolean isProvisioned) {
                mFeatureIdToAdbProvisioned.put(featureId, isProvisioned);
            }

            @Override
            Boolean retrieveFeatureAdbProvisioned(@FeatureId int featureId) {
                return mFeatureIdToAdbProvisioned.get(featureId);
            }

            @Override
            void removeFeatureAdbProvisioning(@FeatureId int featureId) {
                mFeatureIdToAdbProvisioned.remove(featureId);
            }

            @Override
            void saveEnabledChangeTime(long value) {
                // Do nothing
            }

            @Override
            long retrieveEnabledChangeTime() {
                return 0;
            }

            @Override
            void saveDialogShown(
                    int featureId, int type, boolean learnMoreClicked, int hoursSinceEnabled) {
                // Do nothing
            }

            @Override
            int retrieveLastDialogFeatureId() {
                return -1;
            }

            @Override
            int retrieveLastDialogType() {
                return -1;
            }

            @Override
            boolean retrieveLastDialogLearnMoreClicked() {
                return false;
            }

            @Override
            int retrieveLastDialogHoursSinceEnabled() {
                return -1;
            }
        };
    }
}
