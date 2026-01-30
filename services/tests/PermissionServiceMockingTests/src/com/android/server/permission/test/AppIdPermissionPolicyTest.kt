/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.permission.test

import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.os.Build
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.permission.flags.Flags
import com.android.internal.pm.pkg.component.ParsedPermission
import com.android.internal.pm.pkg.component.ParsedValidPurpose
import com.android.server.permission.access.GetStateScope
import com.android.server.permission.access.immutable.* // ktlint-disable no-wildcard-imports
import com.android.server.permission.access.permission.AppIdPermissionPolicy
import com.android.server.permission.access.permission.Permission
import com.android.server.permission.access.permission.PermissionFlags
import com.android.server.pm.pkg.PackageState
import com.android.server.testutils.mock
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class AppIdPermissionPolicyTest : BasePermissionPolicyTest() {
    @Test
    fun testOnAppIdRemoved_appIdIsRemoved_permissionFlagsCleared() {
        val parsedPermission = mockParsedPermission(PERMISSION_NAME_0, PACKAGE_NAME_0)
        val permissionOwnerPackageState = mockPackageState(
            APP_ID_0,
            mockAndroidPackage(PACKAGE_NAME_0, permissions = listOf(parsedPermission))
        )
        val requestingPackageState = mockPackageState(
            APP_ID_1,
            mockAndroidPackage(PACKAGE_NAME_1, requestedPermissions = setOf(PERMISSION_NAME_0))
        )
        addPackageState(permissionOwnerPackageState)
        addPackageState(requestingPackageState)
        addPermission(parsedPermission)
        setPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0, PermissionFlags.INSTALL_GRANTED)

        mutateState {
            with(appIdPermissionPolicy) {
                onAppIdRemoved(APP_ID_1)
            }
        }

        val actualFlags = getPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = 0
        assertWithMessage(
            "After onAppIdRemoved() is called for appId $APP_ID_1 that requests a permission" +
                " owns by appId $APP_ID_0 with existing permission flags. The actual permission" +
                " flags $actualFlags should be null"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithNoPurpose_permissionRevoked() {
        testOnPackageAddedForPurposeDeclaration(purposes = emptySet())

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED or PermissionFlags.PURPOSE_REVOKED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a" +
                " purpose-required permission with no purpose the actual permission flags" +
                " ($actualFlags) should be install granted + purpose revoked ($expectedNewFlags)"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithNoPurposeAndFlagDisabled_permissionGranted() {
        testOnPackageAddedForPurposeDeclaration(purposes = emptySet())

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "With purpose declaration flag disabled, after onPackageAdded() is called for appId" +
                " $APP_ID_0 that requests a purpose-required permission with no purpose, the" +
                " actual permission flags ($actualFlags) should be granted ($expectedNewFlags)"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithInvalidPurpose_permissionRevoked() {
        testOnPackageAddedForPurposeDeclaration(purposes = setOf(INVALID_PURPOSE))

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED or PermissionFlags.PURPOSE_REVOKED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a" +
                " purpose-required permission with invalid purpose, the actual permission flags" +
                " ($actualFlags) should be install granted + purpose revoked ($expectedNewFlags)"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithValidAndInvalidPurpose_permissionGranted() {
        testOnPackageAddedForPurposeDeclaration(
            // At least one valid purpose is sufficient for the permission to be granted.
            purposes = setOf(VALID_PURPOSE_0, INVALID_PURPOSE),
            oldFlags = PermissionFlags.INSTALL_REVOKED or PermissionFlags.PURPOSE_REVOKED
        )

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a" +
                " purpose-required permission with valid and invalid purpose, the actual" +
                " permission flags ($actualFlags) should be granted ($expectedNewFlags)"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithValidPurpose_permissionGranted() {
        testOnPackageAddedForPurposeDeclaration(
            purposes = setOf(VALID_PURPOSE_0),
            oldFlags = PermissionFlags.INSTALL_REVOKED or PermissionFlags.PURPOSE_REVOKED
        )

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a" +
                " purpose-required permission with valid purpose, the actual permission flags" +
                " ($actualFlags) should be granted ($expectedNewFlags)"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithNoPurposeTargetingOldSdk_permissionGranted() {
        testOnPackageAddedForPurposeDeclaration(
            purposes = emptySet(),
            targetSdkVersion = Build.VERSION_CODES.BAKLAVA
        )

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a" +
                " purpose-required permission with no purpose on an older SDK than Android C, the" +
                " actual permission flags ($actualFlags) should be granted ($expectedNewFlags)"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithValidPurposeInAnotherPackage_permissionGranted() {
        // Set package1 with valid purpose, so it should be sufficient for any other package
        // sharing the same UID to pass the purpose validation check.
        val sharedUidPackage1 = mockPackageStateForPurposeDeclaration(
            PACKAGE_NAME_1,
            purposes = setOf(VALID_PURPOSE_0)
        )
        addPackageState(sharedUidPackage1)

        testOnPackageAddedForPurposeDeclaration(purposes = emptySet())

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageAdded() is called for package $PACKAGE_NAME_0 / appId $APP_ID_0" +
                " that requests a purpose-required permission with no purpose, the actual" +
                " permission flags ($actualFlags) should be granted ($expectedNewFlags)" +
                " due to valid purpose declared package in $PACKAGE_NAME_1 / appId $APP_ID_0"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithNoPurposeAndAnotherPackageTargetingOldSdk_permissionGranted() {
        // Set package1 targeting old SDK, so it should be sufficient for any other package
        // sharing the same UID to pass the purpose validation check.
        val sharedUidPackage1 = mockPackageStateForPurposeDeclaration(
            PACKAGE_NAME_1,
            purposes = emptySet(),
            targetSdkVersion = Build.VERSION_CODES.BAKLAVA,
        )
        addPackageState(sharedUidPackage1)

        testOnPackageAddedForPurposeDeclaration(purposes = emptySet())

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageAdded() is called for package $PACKAGE_NAME_0 / appId $APP_ID_0 that" +
                " requests a purpose-required permission with no purpose, the actual permission" +
                " flags ($actualFlags) should be granted ($expectedNewFlags) due to" +
                " $PACKAGE_NAME_1 / appId $APP_ID_0 which targets an old SDK version."
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithDeprecatedPurposeOnLastValidSdk_permissionGranted() {
        testOnPackageAddedForPurposeDeclaration(purposes = setOf(VALID_PURPOSE_1))

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a permission," +
                " which requires purpose, with a deprecated purpose whose maxTargetSdkVersion" +
                " matches the app's targetSdkVersion, permission flags ($actualFlags) should be" +
                " install granted ($expectedNewFlags)."
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithNoPurposeTargetSdkLowerThanRequiresPurposeSdk_permissionGranted() {
        testOnPackageAddedForPurposeDeclaration(
            purposes = emptySet(),
            requiresPurposeTargetSdkVersion = BUILD_VERSION_CODES_D
        )

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a permission," +
                " which requires purpose, with no purpose but the app's targetSdkVersion is lower" +
                " than the permission's requiresPurposeTargetSdkVersion, permission flags" +
                " ($actualFlags) should be install granted ($expectedNewFlags)."
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithDeprecatedPurpose_permissionRevoked() {
        testOnPackageAddedForPurposeDeclaration(
            purposes = setOf(VALID_PURPOSE_1), // This purpose is deprecated as of Android C.
            targetSdkVersion = BUILD_VERSION_CODES_D
        )

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED or PermissionFlags.PURPOSE_REVOKED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a permission," +
                " which requires purpose, with a deprecated purpose, the actual permission flags" +
                " ($actualFlags) should be install granted + purpose revoked ($expectedNewFlags)."
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageAdded_requestsPermissionWithDeprecatedAndValidPurpose_permissionGranted() {
        testOnPackageAddedForPurposeDeclaration(
            purposes = setOf(VALID_PURPOSE_1, VALID_PURPOSE_0),
            targetSdkVersion = BUILD_VERSION_CODES_D,
        )

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageAdded() is called for appId $APP_ID_0 that requests a permission," +
                " which requires purpose, with a deprecated and active (valid) purpose, the" +
                " permission flags ($actualFlags) should be install granted ($expectedNewFlags)."
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageRemoved_removeLastSharedUidPackageWithValidPurpose_permissionRevoked() {
        testSharedUidOnPackageRemovedForPurposeDeclaration(
            purposesForRemovedPackage = setOf(VALID_PURPOSE_0),
            purposesForRetainedPackage = emptySet(),
        )

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED or PermissionFlags.PURPOSE_REVOKED

        assertWithMessage(
            "After onPackageRemoved() is called for package $PACKAGE_NAME_0 / appId $APP_ID_0" +
                " that requests a purpose-required permission with a valid purpose, the actual" +
                " permission flags ($actualFlags) for $APP_ID_0 should be install granted +" +
                " purpose revoked ($expectedNewFlags) as $PACKAGE_NAME_1 / appId $APP_ID_0 does" +
                " not declare a valid purpose."
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_PURPOSE_DECLARATION_ENABLED)
    fun testOnPackageRemoved_removeSharedUidPackageWithValidPurpose_permissionStillGranted() {
        testSharedUidOnPackageRemovedForPurposeDeclaration(
            purposesForRemovedPackage = setOf(VALID_PURPOSE_0),
            purposesForRetainedPackage = setOf(VALID_PURPOSE_0)
        )

        val actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.INSTALL_GRANTED

        assertWithMessage(
            "After onPackageRemoved() is called for package $PACKAGE_NAME_0 / appId $APP_ID_0" +
                " that requests a purpose-required permission with a valid purpose, the actual" +
                " permission flags ($actualFlags) for $APP_ID_0 should still be install granted" +
                " ($expectedNewFlags) as $PACKAGE_NAME_1 / appId $APP_ID_0 declares valid purpose."
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)

        assertThat(PermissionFlags.isPermissionGranted(actualFlags)).isTrue()
    }

    private fun testOnPackageAddedForPurposeDeclaration(
        purposes: Set<String>,
        targetSdkVersion: Int = BUILD_VERSION_CODES_C,
        requiresPurposeTargetSdkVersion: Int = BUILD_VERSION_CODES_C,
        oldFlags: Int = PermissionFlags.INSTALL_GRANTED,
    ) {
        val addedPackage = mockPackageStateForPurposeDeclaration(
            PACKAGE_NAME_0,
            purposes = purposes,
            targetSdkVersion = targetSdkVersion
        )
        val permission = mockPermissionRequiringPurpose(
            requiresPurposeTargetSdkVersion = requiresPurposeTargetSdkVersion,
            validPurposes =
                listOf(
                    mockParsedValidPurpose(VALID_PURPOSE_0),
                    mockParsedValidPurpose(VALID_PURPOSE_1, requiresPurposeTargetSdkVersion)
                )
        )
        val platformPackage = mockPackageState(
            PLATFORM_APP_ID,
            mockAndroidPackage(PLATFORM_PACKAGE_NAME)
        )
        addPackageState(platformPackage)
        addPackageState(addedPackage)
        addPermission(permission)
        setPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0, oldFlags)

        mutateState {
            with(appIdPermissionPolicy) {
                onPackageAdded(addedPackage)
            }
        }
    }

    private fun testSharedUidOnPackageRemovedForPurposeDeclaration(
        purposesForRemovedPackage: Set<String>,
        purposesForRetainedPackage: Set<String>,
    ) {
        val removedPackage = mockPackageStateForPurposeDeclaration(
            PACKAGE_NAME_0,
            purposes = purposesForRemovedPackage
        )
        val retainedPackage = mockPackageStateForPurposeDeclaration(
            PACKAGE_NAME_1,
            purposes = purposesForRetainedPackage
        )
        val platformPackage = mockPackageState(
            PLATFORM_APP_ID,
            mockAndroidPackage(PLATFORM_PACKAGE_NAME)
        )
        val permission = mockPermissionRequiringPurpose()
        addPackageState(platformPackage)
        addPackageState(removedPackage)
        addPackageState(retainedPackage)
        addPermission(permission)
        setPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0, PermissionFlags.INSTALL_GRANTED)

        mutateState {
            removePackageState(removedPackage)
            with(appIdPermissionPolicy) {
                onPackageRemoved(PACKAGE_NAME_0, APP_ID_0)
            }
        }
    }

    private fun mockPackageStateForPurposeDeclaration(
        packageName: String,
        purposes: Set<String>,
        targetSdkVersion: Int = BUILD_VERSION_CODES_C,
    ) : PackageState = mockPackageState(
            APP_ID_0,
            mockAndroidPackage(
                packageName,
                targetSdkVersion = targetSdkVersion,
                requestedPermissions = setOf(PERMISSION_NAME_0),
                usesPermissionMapping =
                    mapOf(
                        PERMISSION_NAME_0 to
                            mockParsedUsesPermission(PERMISSION_NAME_0, purposes = purposes)
                    )
            )
        )

    private fun mockPermissionRequiringPurpose(
        requiresPurposeTargetSdkVersion: Int = BUILD_VERSION_CODES_C,
        validPurposes: List<ParsedValidPurpose> = listOf(mockParsedValidPurpose(VALID_PURPOSE_0))
    ) : ParsedPermission = mockParsedPermission(
            PERMISSION_NAME_0,
            PLATFORM_PACKAGE_NAME,
            isPurposeRequired = true,
            requiresPurposeTargetSdkVersion = requiresPurposeTargetSdkVersion,
            validPurposes = validPurposes
        )

    @Test
    fun testOnStorageVolumeMounted_nonSystemAppAfterNonSystemUpdate_remainsRevoked() {
        val permissionOwnerPackageState = mockPackageState(APP_ID_0, mockSimpleAndroidPackage())
        val installedPackageState = mockPackageState(
            APP_ID_1,
            mockAndroidPackage(PACKAGE_NAME_1, requestedPermissions = setOf(PERMISSION_NAME_0))
        )
        addPackageState(permissionOwnerPackageState)
        addPackageState(installedPackageState)
        addPermission(defaultPermission)
        val oldFlags = PermissionFlags.INSTALL_REVOKED
        setPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0, oldFlags)

        mutateState {
            with(appIdPermissionPolicy) {
                onStorageVolumeMounted(null, listOf(installedPackageState.packageName), false)
            }
        }

        val actualFlags = getPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = oldFlags
        assertWithMessage(
            "After onStorageVolumeMounted() is called for a non-system app that requests a normal" +
                " permission with existing INSTALL_REVOKED flag after a non-system-update" +
                " (such as an OTA update), the actual permission flags should remain revoked." +
                " The actual permission flags $actualFlags should match the expected flags" +
                " $expectedNewFlags"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)
    }

    @Test
    fun testOnPackageRemoved_packageIsRemoved_permissionDefinitionsAndStatesAreUpdated() {
        val permissionOwnerPackageState = mockPackageState(
            APP_ID_0,
            mockAndroidPackage(
                PACKAGE_NAME_0,
                requestedPermissions = setOf(PERMISSION_NAME_0),
                permissions = listOf(defaultPermission)
            )
        )
        val requestingPackageState = mockPackageState(
            APP_ID_1,
            mockAndroidPackage(PACKAGE_NAME_1, requestedPermissions = setOf(PERMISSION_NAME_0))
        )
        addPackageState(permissionOwnerPackageState)
        addPackageState(requestingPackageState)
        addPermission(defaultPermission)
        val oldFlags = PermissionFlags.INSTALL_GRANTED
        setPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0, oldFlags)
        setPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0, oldFlags)

        mutateState {
            removePackageState(permissionOwnerPackageState)
            with(appIdPermissionPolicy) {
                onPackageRemoved(PACKAGE_NAME_0, APP_ID_0)
            }
        }

        assertWithMessage(
            "After onPackageRemoved() is called for a permission owner, the permission" +
                " definitions owned by this package should be removed"
        )
            .that(getPermission(PERMISSION_NAME_0))
            .isNull()

        val app0ActualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
        val app0ExpectedNewFlags = 0
        assertWithMessage(
            "After onPackageRemoved() is called for a permission owner, the permission states of" +
                " this app should be trimmed. The actual permission flags $app0ActualFlags should" +
                " match the expected flags $app0ExpectedNewFlags"
        )
            .that(app0ActualFlags)
            .isEqualTo(app0ExpectedNewFlags)

        val app1ActualFlags = getPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0)
        val app1ExpectedNewFlags = PermissionFlags.INSTALL_REVOKED
        assertWithMessage(
            "After onPackageRemoved() is called for a permission owner, the permission states of" +
                " the permission requester should remain unchanged. The actual permission flags" +
                " $app1ActualFlags should match the expected flags $app1ExpectedNewFlags"
        )
            .that(app1ActualFlags)
            .isEqualTo(app1ExpectedNewFlags)
    }

    @Test
    fun testOnPackageInstalled_nonSystemAppIsInstalled_upgradeExemptFlagIsCleared() {
        val oldFlags = PermissionFlags.SOFT_RESTRICTED or PermissionFlags.UPGRADE_EXEMPT
        testOnPackageInstalled(
            oldFlags,
            permissionInfoFlags = PermissionInfo.FLAG_SOFT_RESTRICTED
        ) {}
        val actualFlags = getPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = PermissionFlags.SOFT_RESTRICTED
        assertWithMessage(
            "After onPackageInstalled() is called for a non-system app that requests a runtime" +
                " soft restricted permission, UPGRADE_EXEMPT flag should be removed. The actual" +
                " permission flags $actualFlags should match the expected flags $expectedNewFlags"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)
    }

    @Test
    fun testOnPackageInstalled_systemAppIsInstalled_upgradeExemptFlagIsRetained() {
        val oldFlags = PermissionFlags.SOFT_RESTRICTED or PermissionFlags.UPGRADE_EXEMPT
        testOnPackageInstalled(
            oldFlags,
            permissionInfoFlags = PermissionInfo.FLAG_SOFT_RESTRICTED,
            isInstalledPackageSystem = true
        ) {}
        val actualFlags = getPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = oldFlags
        assertWithMessage(
            "After onPackageInstalled() is called for a system app that requests a runtime" +
                " soft restricted permission, UPGRADE_EXEMPT flag should be retained. The actual" +
                " permission flags $actualFlags should match the expected flags $expectedNewFlags"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)
    }

    @Test
    fun testOnPackageInstalled_requestedPermissionAlsoRequestedBySystemApp_exemptFlagIsRetained() {
        val oldFlags = PermissionFlags.SOFT_RESTRICTED or PermissionFlags.UPGRADE_EXEMPT
        testOnPackageInstalled(
            oldFlags,
            permissionInfoFlags = PermissionInfo.FLAG_SOFT_RESTRICTED
        ) {
            val systemAppPackageState = mockPackageState(
                APP_ID_1,
                mockAndroidPackage(PACKAGE_NAME_2, requestedPermissions = setOf(PERMISSION_NAME_0)),
                isSystem = true
            )
            addPackageState(systemAppPackageState)
        }
        val actualFlags = getPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = oldFlags
        assertWithMessage(
            "After onPackageInstalled() is called for a non-system app that requests a runtime" +
                " soft restricted permission, and that permission is also requested by a system" +
                " app in the same appId, UPGRADE_EXEMPT flag should be retained. The actual" +
                " permission flags $actualFlags should match the expected flags $expectedNewFlags"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)
    }

    @Test
    fun testOnPackageInstalled_restrictedPermissionsNotExempt_getsRestrictionFlags() {
        val oldFlags = PermissionFlags.RESTRICTION_REVOKED
        testOnPackageInstalled(
            oldFlags,
            permissionInfoFlags = PermissionInfo.FLAG_HARD_RESTRICTED
        ) {}
        val actualFlags = getPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0)
        val expectedNewFlags = oldFlags
        assertWithMessage(
            "After onPackageInstalled() is called for a non-system app that requests a runtime" +
                " hard restricted permission that is not exempted. The actual permission flags" +
                " $actualFlags should match the expected flags $expectedNewFlags"
        )
            .that(actualFlags)
            .isEqualTo(expectedNewFlags)
    }

    private fun testOnPackageInstalled(
        oldFlags: Int,
        permissionInfoFlags: Int = 0,
        isInstalledPackageSystem: Boolean = false,
        additionalSetup: () -> Unit
    ) {
        val parsedPermission = mockParsedPermission(
            PERMISSION_NAME_0,
            PACKAGE_NAME_0,
            protectionLevel = PermissionInfo.PROTECTION_DANGEROUS,
            flags = permissionInfoFlags
        )
        val permissionOwnerPackageState = mockPackageState(
            APP_ID_0,
            mockAndroidPackage(PACKAGE_NAME_0, permissions = listOf(parsedPermission))
        )
        addPackageState(permissionOwnerPackageState)
        addPermission(parsedPermission)

        additionalSetup()

        mutateState {
            val installedPackageState = mockPackageState(
                APP_ID_1,
                mockAndroidPackage(
                    PACKAGE_NAME_1,
                    requestedPermissions = setOf(PERMISSION_NAME_0),
                ),
                isSystem = isInstalledPackageSystem,
            )
            addPackageState(installedPackageState, newState)
            setPermissionFlags(APP_ID_1, USER_ID_0, PERMISSION_NAME_0, oldFlags, newState)
            with(appIdPermissionPolicy) {
                onPackageInstalled(installedPackageState, USER_ID_0)
            }
        }
    }

    @Test
    fun testOnStateMutated_notEmpty_isCalledForEachListener() {
        val mockListener = mock<AppIdPermissionPolicy.OnPermissionFlagsChangedListener> {}
        appIdPermissionPolicy.addOnPermissionFlagsChangedListener(mockListener)

        GetStateScope(oldState).apply {
            with(appIdPermissionPolicy) {
                onStateMutated()
            }
        }

        verify(mockListener, times(1)).onStateMutated()
    }

    @Test
    fun testGetPermissionTrees() {
        val permissionTrees: IndexedMap<String, Permission>
        GetStateScope(oldState).apply {
            with(appIdPermissionPolicy) {
                permissionTrees = getPermissionTrees()
            }
        }

        assertThat(oldState.systemState.permissionTrees).isEqualTo(permissionTrees)
    }

    @Test
    fun testFindPermissionTree() {
        val permissionTree = createSimplePermission(isTree = true)
        val actualPermissionTree: Permission?
        oldState.mutateSystemState().mutatePermissionTrees()[PERMISSION_TREE_NAME] = permissionTree

        GetStateScope(oldState).apply {
            with(appIdPermissionPolicy) {
                actualPermissionTree = findPermissionTree(PERMISSION_BELONGS_TO_A_TREE)
            }
        }

        assertThat(actualPermissionTree).isEqualTo(permissionTree)
    }

    @Test
    fun testAddPermissionTree() {
        val permissionTree = createSimplePermission(isTree = true)

        mutateState {
            with(appIdPermissionPolicy) {
                addPermissionTree(permissionTree)
            }
        }

        assertThat(newState.systemState.permissionTrees[PERMISSION_TREE_NAME])
            .isEqualTo(permissionTree)
    }

    @Test
    fun testGetPermissionGroups() {
        val permissionGroups: IndexedMap<String, PermissionGroupInfo>
        GetStateScope(oldState).apply {
            with(appIdPermissionPolicy) {
                permissionGroups = getPermissionGroups()
            }
        }

        assertThat(oldState.systemState.permissionGroups).isEqualTo(permissionGroups)
    }

    @Test
    fun testGetPermissions() {
        val permissions: IndexedMap<String, Permission>
        GetStateScope(oldState).apply {
            with(appIdPermissionPolicy) {
                permissions = getPermissions()
            }
        }

        assertThat(oldState.systemState.permissions).isEqualTo(permissions)
    }

    @Test
    fun testAddPermission() {
        val permission = createSimplePermission()

        mutateState {
            with(appIdPermissionPolicy) {
                addPermission(permission)
            }
        }

        assertThat(newState.systemState.permissions[PERMISSION_NAME_0]).isEqualTo(permission)
    }

    @Test
    fun testRemovePermission() {
        val permission = createSimplePermission()

        mutateState {
            with(appIdPermissionPolicy) {
                addPermission(permission)
                removePermission(permission)
            }
        }

        assertThat(newState.systemState.permissions[PERMISSION_NAME_0]).isNull()
    }

    @Test
    fun testGetUidPermissionFlags() {
        val uidPermissionFlags: IndexedMap<String, Int>?
        GetStateScope(oldState).apply {
            with(appIdPermissionPolicy) {
                uidPermissionFlags = getUidPermissionFlags(APP_ID_0, USER_ID_0)
            }
        }

        assertThat(oldState.userStates[USER_ID_0]!!.appIdPermissionFlags[APP_ID_0])
            .isEqualTo(uidPermissionFlags)
    }

    @Test
    fun testUpdateAndGetPermissionFlags() {
        val flags = PermissionFlags.INSTALL_GRANTED
        var actualFlags = 0
        mutateState {
            with(appIdPermissionPolicy) {
                updatePermissionFlags(
                    APP_ID_0,
                    USER_ID_0,
                    PERMISSION_NAME_0,
                    PermissionFlags.MASK_ALL,
                    flags
                )
                actualFlags = getPermissionFlags(APP_ID_0, USER_ID_0, PERMISSION_NAME_0)
            }
        }

        assertThat(actualFlags).isEqualTo(flags)
    }
}
