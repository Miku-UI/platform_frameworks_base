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
package android.platform.test.ravenwood;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ChangedPackages;
import android.content.pm.FeatureInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.InstantAppInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.IntentFilterVerificationInfo;
import android.content.pm.KeySet;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.VerifierDeviceIdentity;
import android.content.pm.VersionedPackage;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.os.storage.VolumeInfo;

import java.util.List;

public class RavenwoodBasePackageManager extends PackageManager {

    RavenwoodBasePackageManager() {
        // Only usable by ravenwood.
    }

    private static RavenwoodUnsupportedApiException notSupported() {
        return new RavenwoodUnsupportedApiException("This PackageManager API is not yet supported "
                + "under the Ravenwood deviceless testing environment. Contact g/ravenwood")
                .skipStackTraces(1);
    }


    @Override
    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public PackageInfo getPackageInfo(VersionedPackage versionedPackage, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public PackageInfo getPackageInfoAsUser(String packageName, int flags, int userId)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public String[] currentToCanonicalPackageNames(String[] packageNames) {
        throw notSupported();
    }

    @Override
    public String[] canonicalToCurrentPackageNames(String[] packageNames) {
        throw notSupported();
    }

    @Override
    public Intent getLaunchIntentForPackage(String packageName) {
        throw notSupported();
    }

    @Override
    public Intent getLeanbackLaunchIntentForPackage(String packageName) {
        throw notSupported();
    }

    @Override
    public Intent getCarLaunchIntentForPackage(String packageName) {
        throw notSupported();
    }

    @Override
    public int[] getPackageGids(String packageName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public int[] getPackageGids(String packageName, int flags) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public int getPackageUid(String packageName, int flags) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public int getPackageUidAsUser(String packageName, int userId) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public int getPackageUidAsUser(String packageName, int flags, int userId)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public PermissionInfo getPermissionInfo(String permName, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public List<PermissionInfo> queryPermissionsByGroup(String permissionGroup, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public boolean arePermissionsIndividuallyControlled() {
        throw notSupported();
    }

    @Override
    public boolean isWirelessConsentModeEnabled() {
        throw notSupported();
    }

    @Override
    public PermissionGroupInfo getPermissionGroupInfo(String groupName, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public List<PermissionGroupInfo> getAllPermissionGroups(int flags) {
        throw notSupported();
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public ApplicationInfo getApplicationInfoAsUser(String packageName, int flags, int userId)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName component, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public List<PackageInfo> getInstalledPackages(int flags) {
        throw notSupported();
    }

    @Override
    public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions, int flags) {
        throw notSupported();
    }

    @Override
    public List<PackageInfo> getInstalledPackagesAsUser(int flags, int userId) {
        throw notSupported();
    }

    @Override
    public int checkPermission(String permName, String packageName) {
        throw notSupported();
    }

    @Override
    public boolean isPermissionRevokedByPolicy(String permName, String packageName) {
        throw notSupported();
    }

    @Override
    public boolean addPermission(PermissionInfo info) {
        throw notSupported();
    }

    @Override
    public boolean addPermissionAsync(PermissionInfo info) {
        throw notSupported();
    }

    @Override
    public void removePermission(String permName) {
        throw notSupported();
    }

    @Override
    public void grantRuntimePermission(String packageName, String permName, UserHandle user) {
        throw notSupported();
    }

    @Override
    public void revokeRuntimePermission(String packageName, String permName, UserHandle user) {
        throw notSupported();
    }

    @Override
    public int getPermissionFlags(String permName, String packageName, UserHandle user) {
        throw notSupported();
    }

    @Override
    public void updatePermissionFlags(String permName, String packageName, int flagMask,
            int flagValues, UserHandle user) {
        throw notSupported();
    }

    @Override
    public boolean shouldShowRequestPermissionRationale(String permName) {
        throw notSupported();
    }

    @Override
    public int checkSignatures(String packageName1, String packageName2) {
        throw notSupported();
    }

    @Override
    public int checkSignatures(int uid1, int uid2) {
        throw notSupported();
    }

    @Override
    public String[] getPackagesForUid(int uid) {
        throw notSupported();
    }

    @Override
    public String getNameForUid(int uid) {
        throw notSupported();
    }

    @Override
    public String[] getNamesForUids(int[] uids) {
        throw notSupported();
    }

    @Override
    public int getUidForSharedUser(String sharedUserName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications(int flags) {
        throw notSupported();
    }

    @Override
    public List<ApplicationInfo> getInstalledApplicationsAsUser(int flags, int userId) {
        throw notSupported();
    }

    @Override
    public List<InstantAppInfo> getInstantApps() {
        throw notSupported();
    }

    @Override
    public Drawable getInstantAppIcon(String packageName) {
        throw notSupported();
    }

    @Override
    public boolean isInstantApp() {
        throw notSupported();
    }

    @Override
    public boolean isInstantApp(String packageName) {
        throw notSupported();
    }

    @Override
    public int getInstantAppCookieMaxBytes() {
        throw notSupported();
    }

    @Override
    public int getInstantAppCookieMaxSize() {
        throw notSupported();
    }

    @Override
    public byte[] getInstantAppCookie() {
        throw notSupported();
    }

    @Override
    public void clearInstantAppCookie() {
        throw notSupported();
    }

    @Override
    public void updateInstantAppCookie(byte[] cookie) {
        throw notSupported();
    }

    @Override
    public boolean setInstantAppCookie(byte[] cookie) {
        throw notSupported();
    }

    @Override
    public String[] getSystemSharedLibraryNames() {
        throw notSupported();
    }

    @Override
    public List<SharedLibraryInfo> getSharedLibraries(int flags) {
        throw notSupported();
    }

    @Override
    public List<SharedLibraryInfo> getSharedLibrariesAsUser(int flags, int userId) {
        throw notSupported();
    }

    @Override
    public String getServicesSystemSharedLibraryPackageName() {
        throw notSupported();
    }

    @Override
    public String getSharedSystemSharedLibraryPackageName() {
        throw notSupported();
    }

    @Override
    public ChangedPackages getChangedPackages(int sequenceNumber) {
        throw notSupported();
    }

    @Override
    public FeatureInfo[] getSystemAvailableFeatures() {
        throw notSupported();
    }

    @Override
    public boolean hasSystemFeature(String featureName) {
        throw notSupported();
    }

    @Override
    public boolean hasSystemFeature(String featureName, int version) {
        throw notSupported();
    }

    @Override
    public ResolveInfo resolveActivity(Intent intent, int flags) {
        throw notSupported();
    }

    @Override
    public ResolveInfo resolveActivityAsUser(Intent intent, int flags, int userId) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryIntentActivitiesAsUser(Intent intent, int flags, int userId) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics,
            Intent intent, int flags) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceiversAsUser(Intent intent, int flags, int userId) {
        throw notSupported();
    }

    @Override
    public ResolveInfo resolveService(Intent intent, int flags) {
        throw notSupported();
    }

    @Override
    public ResolveInfo resolveServiceAsUser(Intent intent, int flags, int userId) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryIntentServicesAsUser(Intent intent, int flags, int userId) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryIntentContentProvidersAsUser(Intent intent, int flags,
            int userId) {
        throw notSupported();
    }

    @Override
    public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
        throw notSupported();
    }

    @Override
    public ProviderInfo resolveContentProvider(String authority, int flags) {
        throw notSupported();
    }

    @Override
    public ProviderInfo resolveContentProviderAsUser(String providerName, int flags, int userId) {
        throw notSupported();
    }

    @Override
    public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags) {
        throw notSupported();
    }

    @Override
    public InstrumentationInfo getInstrumentationInfo(ComponentName className, int flags)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public List<InstrumentationInfo> queryInstrumentation(String targetPackage, int flags) {
        throw notSupported();
    }

    @Override
    public Drawable getDrawable(String packageName, int resid, ApplicationInfo appInfo) {
        throw notSupported();
    }

    @Override
    public Drawable getActivityIcon(ComponentName activityName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getActivityIcon(Intent intent) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getActivityBanner(ComponentName activityName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getActivityBanner(Intent intent) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getDefaultActivityIcon() {
        throw notSupported();
    }

    @Override
    public Drawable getApplicationIcon(ApplicationInfo info) {
        throw notSupported();
    }

    @Override
    public Drawable getApplicationIcon(String packageName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getApplicationBanner(ApplicationInfo info) {
        throw notSupported();
    }

    @Override
    public Drawable getApplicationBanner(String packageName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getActivityLogo(ComponentName activityName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getActivityLogo(Intent intent) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getApplicationLogo(ApplicationInfo info) {
        throw notSupported();
    }

    @Override
    public Drawable getApplicationLogo(String packageName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Drawable getUserBadgedIcon(Drawable drawable, UserHandle user) {
        throw notSupported();
    }

    @Override
    public Drawable getUserBadgedDrawableForDensity(Drawable drawable, UserHandle user,
            Rect badgeLocation, int badgeDensity) {
        throw notSupported();
    }

    @Override
    public Drawable getUserBadgeForDensity(UserHandle user, int density) {
        throw notSupported();
    }

    @Override
    public Drawable getUserBadgeForDensityNoBackground(UserHandle user, int density) {
        throw notSupported();
    }

    @Override
    public CharSequence getUserBadgedLabel(CharSequence label, UserHandle user) {
        throw notSupported();
    }

    @Override
    public CharSequence getText(String packageName, int resid, ApplicationInfo appInfo) {
        throw notSupported();
    }

    @Override
    public XmlResourceParser getXml(String packageName, int resid, ApplicationInfo appInfo) {
        throw notSupported();
    }

    @Override
    public CharSequence getApplicationLabel(ApplicationInfo info) {
        throw notSupported();
    }

    @Override
    public Resources getResourcesForActivity(ComponentName activityName)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Resources getResourcesForApplication(ApplicationInfo app) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Resources getResourcesForApplication(String packageName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public Resources getResourcesForApplicationAsUser(String packageName, int userId)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public int installExistingPackage(String packageName) throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public int installExistingPackage(String packageName, int installReason)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public int installExistingPackageAsUser(String packageName, int userId)
            throws NameNotFoundException {
        throw notSupported();
    }

    @Override
    public void verifyPendingInstall(int id, int verificationCode) {
        throw notSupported();
    }

    @Override
    public void extendVerificationTimeout(int id, int verificationCodeAtTimeout,
            long millisecondsToDelay) {
        throw notSupported();
    }

    @Override
    public void verifyIntentFilter(int verificationId, int verificationCode,
            List<String> failedDomains) {
        throw notSupported();
    }

    @Override
    public int getIntentVerificationStatusAsUser(String packageName, int userId) {
        throw notSupported();
    }

    @Override
    public boolean updateIntentVerificationStatusAsUser(String packageName, int status,
            int userId) {
        throw notSupported();
    }

    @Override
    public List<IntentFilterVerificationInfo> getIntentFilterVerifications(String packageName) {
        throw notSupported();
    }

    @Override
    public List<IntentFilter> getAllIntentFilters(String packageName) {
        throw notSupported();
    }

    @Override
    public String getDefaultBrowserPackageNameAsUser(int userId) {
        throw notSupported();
    }

    @Override
    public boolean setDefaultBrowserPackageNameAsUser(String packageName, int userId) {
        throw notSupported();
    }

    @Override
    public void setInstallerPackageName(String targetPackage, String installerPackageName) {
        throw notSupported();
    }

    @Override
    public void setUpdateAvailable(String packageName, boolean updateAvailable) {
        throw notSupported();
    }

    @Override
    public void deletePackage(String packageName, IPackageDeleteObserver observer, int flags) {
        throw notSupported();
    }

    @Override
    public void deletePackageAsUser(String packageName, IPackageDeleteObserver observer, int flags,
            int userId) {
        throw notSupported();
    }

    @Override
    public String getInstallerPackageName(String packageName) {
        throw notSupported();
    }

    @Override
    public void clearApplicationUserData(String packageName, IPackageDataObserver observer) {
        throw notSupported();
    }

    @Override
    public void deleteApplicationCacheFiles(String packageName, IPackageDataObserver observer) {
        throw notSupported();
    }

    @Override
    public void deleteApplicationCacheFilesAsUser(String packageName, int userId,
            IPackageDataObserver observer) {
        throw notSupported();
    }

    @Override
    public void freeStorageAndNotify(String volumeUuid, long freeStorageSize,
            IPackageDataObserver observer) {
        throw notSupported();
    }

    @Override
    public void freeStorage(String volumeUuid, long freeStorageSize, IntentSender pi) {
        throw notSupported();
    }

    @Override
    public void getPackageSizeInfoAsUser(String packageName, int userId,
            IPackageStatsObserver observer) {
        throw notSupported();
    }

    @Override
    public void addPackageToPreferred(String packageName) {
        throw notSupported();
    }

    @Override
    public void removePackageFromPreferred(String packageName) {
        throw notSupported();
    }

    @Override
    public List<PackageInfo> getPreferredPackages(int flags) {
        throw notSupported();
    }

    @Override
    public void addPreferredActivity(IntentFilter filter, int match, ComponentName[] set,
            ComponentName activity) {
        throw notSupported();
    }

    @Override
    public void replacePreferredActivity(IntentFilter filter, int match, ComponentName[] set,
            ComponentName activity) {
        throw notSupported();
    }

    @Override
    public void clearPackagePreferredActivities(String packageName) {
        throw notSupported();
    }

    @Override
    public int getPreferredActivities(List<IntentFilter> outFilters,
            List<ComponentName> outActivities, String packageName) {
        throw notSupported();
    }

    @Override
    public ComponentName getHomeActivities(List<ResolveInfo> outActivities) {
        throw notSupported();
    }

    @Override
    public void setComponentEnabledSetting(ComponentName componentName, int newState, int flags) {
        throw notSupported();
    }

    @Override
    public int getComponentEnabledSetting(ComponentName componentName) {
        throw notSupported();
    }

    @Override
    public void setApplicationEnabledSetting(String packageName, int newState, int flags) {
        throw notSupported();
    }

    @Override
    public int getApplicationEnabledSetting(String packageName) {
        throw notSupported();
    }

    @Override
    public void flushPackageRestrictionsAsUser(int userId) {
        throw notSupported();
    }

    @Override
    public boolean setApplicationHiddenSettingAsUser(String packageName, boolean hidden,
            UserHandle userHandle) {
        throw notSupported();
    }

    @Override
    public boolean getApplicationHiddenSettingAsUser(String packageName, UserHandle userHandle) {
        throw notSupported();
    }

    @Override
    public boolean isSafeMode() {
        throw notSupported();
    }

    @Override
    public void addOnPermissionsChangeListener(OnPermissionsChangedListener listener) {
        throw notSupported();
    }

    @Override
    public void removeOnPermissionsChangeListener(OnPermissionsChangedListener listener) {
        throw notSupported();
    }

    @Override
    public KeySet getKeySetByAlias(String packageName, String alias) {
        throw notSupported();
    }

    @Override
    public KeySet getSigningKeySet(String packageName) {
        throw notSupported();
    }

    @Override
    public boolean isSignedBy(String packageName, KeySet ks) {
        throw notSupported();
    }

    @Override
    public boolean isSignedByExactly(String packageName, KeySet ks) {
        throw notSupported();
    }

    @Override
    public boolean isPackageSuspendedForUser(String packageName, int userId) {
        throw notSupported();
    }

    @Override
    public void setApplicationCategoryHint(String packageName, int categoryHint) {
        throw notSupported();
    }

    @Override
    public int getMoveStatus(int moveId) {
        throw notSupported();
    }

    @Override
    public void registerMoveCallback(MoveCallback callback, Handler handler) {
        throw notSupported();
    }

    @Override
    public void unregisterMoveCallback(MoveCallback callback) {
        throw notSupported();
    }

    @Override
    public int movePackage(String packageName, VolumeInfo vol) {
        throw notSupported();
    }

    @Override
    public VolumeInfo getPackageCurrentVolume(ApplicationInfo app) {
        throw notSupported();
    }

    @Override
    public List<VolumeInfo> getPackageCandidateVolumes(ApplicationInfo app) {
        throw notSupported();
    }

    @Override
    public int movePrimaryStorage(VolumeInfo vol) {
        throw notSupported();
    }

    @Override
    public VolumeInfo getPrimaryStorageCurrentVolume() {
        throw notSupported();
    }

    @Override
    public List<VolumeInfo> getPrimaryStorageCandidateVolumes() {
        throw notSupported();
    }

    @Override
    public VerifierDeviceIdentity getVerifierDeviceIdentity() {
        throw notSupported();
    }

    @Override
    public boolean isUpgrade() {
        throw notSupported();
    }

    @Override
    public PackageInstaller getPackageInstaller() {
        throw notSupported();
    }

    @Override
    public void addCrossProfileIntentFilter(IntentFilter filter, int sourceUserId, int targetUserId,
            int flags) {
        throw notSupported();
    }

    @Override
    public void clearCrossProfileIntentFilters(int sourceUserId) {
        throw notSupported();
    }

    @Override
    public Drawable loadItemIcon(PackageItemInfo itemInfo, ApplicationInfo appInfo) {
        throw notSupported();
    }

    @Override
    public Drawable loadUnbadgedItemIcon(PackageItemInfo itemInfo, ApplicationInfo appInfo) {
        throw notSupported();
    }

    @Override
    public boolean isPackageAvailable(String packageName) {
        throw notSupported();
    }

    @Override
    public int getInstallReason(String packageName, UserHandle user) {
        throw notSupported();
    }

    @Override
    public boolean canRequestPackageInstalls() {
        throw notSupported();
    }

    @Override
    public ComponentName getInstantAppResolverSettingsComponent() {
        throw notSupported();
    }

    @Override
    public ComponentName getInstantAppInstallerComponent() {
        throw notSupported();
    }

    @Override
    public String getInstantAppAndroidId(String packageName, UserHandle user) {
        throw notSupported();
    }

    @Override
    public void registerDexModule(String dexModulePath, DexModuleRegisterCallback callback) {
        throw notSupported();
    }
}
