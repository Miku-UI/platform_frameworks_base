/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.devicepolicy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.Authority;
import android.app.admin.DeviceAdminAuthority;
import android.app.admin.DpcAuthority;
import android.app.admin.RoleAuthority;
import android.app.admin.SystemAuthority;
import android.app.admin.UnknownAuthority;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.os.UserHandle;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.role.RoleManagerLocal;
import com.android.server.LocalManagerRegistry;
import com.android.server.utils.Slogf;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code EnforcingAdmins} can have the following authority types:
 *
 * <ul>
 *     <li> {@link #DPC_AUTHORITY} meaning it's an enterprise admin (e.g. PO, DO, COPE)
 *     <li> {@link #DEVICE_ADMIN_AUTHORITY} which is a legacy non enterprise admin
 *     <li> Or a role authority, in which case {@link #mAuthorities} contains a list of all roles
 *     held by the given {@code packageName}
 * </ul>
 *
 */
public final class EnforcingAdmin {

    static final String TAG = "EnforcingAdmin";

    public static final String ROLE_AUTHORITY_PREFIX = "role:";
    public static final String SYSTEM_AUTHORITY_PREFIX = "system:";
    public static final String DPC_AUTHORITY = "enterprise";
    public static final String DEVICE_ADMIN_AUTHORITY = "device_admin";

    private static final String ATTR_PACKAGE_NAME = "package-name";
    private static final String ATTR_SYSTEM_ENTITY = "system-entity";
    private static final String ATTR_CLASS_NAME = "class-name";
    private static final String ATTR_AUTHORITIES = "authorities";
    private static final String ATTR_AUTHORITIES_SEPARATOR = ";";
    private static final String ATTR_USER_ID = "user-id";
    private static final String ATTR_IS_ROLE = "is-role";

    // This is needed for DPCs and active admins
    private final ComponentName mComponentName;
    private final AdminKey mAdminKey;
    private Set<String> mAuthorities;
    private final boolean mIsRoleAuthority;

    static EnforcingAdmin createRoleEnforcingAdmin(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        return new EnforcingAdmin(
                new AdminKey.Package(userId, packageName),
                null, /* componentName */
                true, /* isRoleAuthority */
                null  /* authorities */
        );
    }

    static EnforcingAdmin createEnterpriseEnforcingAdmin(
            @NonNull ComponentName componentName, int userId) {
        Objects.requireNonNull(componentName);

        return new EnforcingAdmin(
                new AdminKey.Package(userId, componentName.getPackageName()),
                componentName,
                false, /* isRoleAuthority */
                new HashSet<>(Set.of(DPC_AUTHORITY))
        );
    }

    static EnforcingAdmin createDeviceAdminEnforcingAdmin(
            @NonNull ComponentName componentName, int userId) {
        Objects.requireNonNull(componentName);

        return new EnforcingAdmin(
                new AdminKey.Legacy(userId, componentName),
                componentName,
                false, /* isRoleAuthority */
                new HashSet<>(Set.of(DEVICE_ADMIN_AUTHORITY))
        );
    }

    static EnforcingAdmin createSystemEnforcingAdmin(@NonNull String systemEntity) {
        Objects.requireNonNull(systemEntity);

        return new EnforcingAdmin(
                new AdminKey.System(systemEntity),
                null, /* componentName */
                false, /* isRoleAuthority */
                getSystemAuthority(systemEntity)
        );
    }

    static EnforcingAdmin createEnforcingAdmin(android.app.admin.EnforcingAdmin admin) {
        Objects.requireNonNull(admin);
        Authority authority = admin.getAuthority();
        int userId = admin.getUserHandle().getIdentifier();
        if (DpcAuthority.DPC_AUTHORITY.equals(authority)) {
            return createEnterpriseEnforcingAdmin(admin.getComponentName(), userId);
        } else if (DeviceAdminAuthority.DEVICE_ADMIN_AUTHORITY.equals(authority)) {
            return createDeviceAdminEnforcingAdmin(admin.getComponentName(), userId);
        } else if (authority instanceof RoleAuthority roleAuthority) {
            return createRoleEnforcingAdmin(admin.getPackageName(), userId);
        } else if (authority instanceof SystemAuthority systemAuthority) {
            return createSystemEnforcingAdmin(systemAuthority.getSystemEntity());
        }
        throw new IllegalArgumentException("Unknown admin type: " + admin);
    }

    public static String getRoleAuthorityOf(String roleName) {
        return ROLE_AUTHORITY_PREFIX + roleName;
    }

    static Authority getParcelableAuthority(String authority) {
        if (authority == null || authority.isEmpty()) {
            return UnknownAuthority.UNKNOWN_AUTHORITY;
        }
        if (DPC_AUTHORITY.equals(authority)) {
            return DpcAuthority.DPC_AUTHORITY;
        }
        if (DEVICE_ADMIN_AUTHORITY.equals(authority)) {
            return DeviceAdminAuthority.DEVICE_ADMIN_AUTHORITY;
        }
        if (authority.startsWith(ROLE_AUTHORITY_PREFIX)) {
            String role = authority.substring(ROLE_AUTHORITY_PREFIX.length());
            return new RoleAuthority(Set.of(role));
        }
        if (authority.startsWith(SYSTEM_AUTHORITY_PREFIX)) {
            String systemEntity = authority.substring(SYSTEM_AUTHORITY_PREFIX.length());
            return new SystemAuthority(systemEntity);
        }
        return UnknownAuthority.UNKNOWN_AUTHORITY;
    }

    private EnforcingAdmin(AdminKey adminKey, ComponentName componentName, boolean isRoleAuthority,
            Set<String> authorities) {
        mAdminKey = adminKey;
        mComponentName = componentName;
        mIsRoleAuthority = isRoleAuthority;
        mAuthorities = authorities;
    }

    private static Set<String> getRoleAuthorities(String packageName, int userId) {
        Set<String> roles = getRoles(packageName, userId);
        Set<String> authorities = new HashSet<>();
        for (String role : roles) {
            authorities.add(ROLE_AUTHORITY_PREFIX + role);
        }
        return authorities;
    }

    /**
     * Returns a set of authorities for system authority.
     *
     * <p>Note that a system authority enforcing admin has only one authority that has the package
     * name of the calling system service. Therefore, the returned set always contains one element.
     */
    private static Set<String> getSystemAuthority(String systemEntity) {
        Set<String> authorities = new HashSet<>();
        authorities.add(SYSTEM_AUTHORITY_PREFIX + systemEntity);
        return authorities;
    }

    // TODO(b/259042794): move this logic to RoleManagerLocal
    private static Set<String> getRoles(String packageName, int userId) {
        RoleManagerLocal roleManagerLocal = LocalManagerRegistry.getManager(
                RoleManagerLocal.class);
        Set<String> roles = new HashSet<>();
        Map<String, Set<String>> rolesAndHolders = roleManagerLocal.getRolesAndHolders(userId);
        for (String role : rolesAndHolders.keySet()) {
            if (rolesAndHolders.get(role).contains(packageName)) {
                roles.add(role);
            }
        }
        return roles;
    }

    private Set<String> getAuthorities() {
        if (mAuthorities == null && mIsRoleAuthority) {
            mAuthorities = getRoleAuthorities(mAdminKey.getPackageName(), mAdminKey.getUserId());
        }
        return mAuthorities;
    }

    void reloadRoleAuthorities() {
        if (mIsRoleAuthority) {
            mAuthorities = getRoleAuthorities(mAdminKey.getPackageName(), mAdminKey.getUserId());
        }
    }

    boolean hasAuthority(String authority) {
        return getAuthorities().contains(authority);
    }

    boolean isSystemAuthority() {
        return mAdminKey instanceof AdminKey.System;
    }

    boolean isSupervisionAdmin() {
        return hasAuthority(getRoleAuthorityOf(RoleManager.ROLE_SYSTEM_SUPERVISION));
    }

    @NonNull
    String getPackageName() {
        return mAdminKey.getPackageName();
    }

    int getUserId() {
        return mAdminKey.getUserId();
    }

    @Nullable
    ComponentName getComponentName() {
        return mComponentName;
    }

    @Nullable
    String getSystemEntity() {
        return mAdminKey.getSystemEntity();
    }

    @NonNull
    android.app.admin.EnforcingAdmin getParcelableAdmin() {
        Authority authority;
        if (mIsRoleAuthority) {
            Set<String> roles = getRoles(mAdminKey.getPackageName(), mAdminKey.getUserId());
            if (roles.isEmpty()) {
                authority = UnknownAuthority.UNKNOWN_AUTHORITY;
            } else {
                authority = new RoleAuthority(roles);
            }
        } else if (mAuthorities.contains(DPC_AUTHORITY)) {
            authority = DpcAuthority.DPC_AUTHORITY;
        } else if (mAuthorities.contains(DEVICE_ADMIN_AUTHORITY)) {
            authority = DeviceAdminAuthority.DEVICE_ADMIN_AUTHORITY;
        } else if (isSystemAuthority()) {
            authority = new SystemAuthority(mAdminKey.getSystemEntity());
        } else {
            authority = UnknownAuthority.UNKNOWN_AUTHORITY;
        }
        return new android.app.admin.EnforcingAdmin(
                mAdminKey.getPackageName(),
                authority,
                UserHandle.of(mAdminKey.getUserId()),
                mComponentName);
    }

    /**
     * For two EnforcingAdmins to be equal they must:
     *
     * <ul>
     *     <li> have the same package names and component names and either
     *     <li> have exactly the same authorities ({@link #DPC_AUTHORITY} or
     *     {@link #DEVICE_ADMIN_AUTHORITY}), or have any role or default authorities.
     * </ul>
     *
     * <p>EnforcingAdmins are considered equal if they have any role authority as they can have
     * roles granted/revoked between calls.
     */
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnforcingAdmin other = (EnforcingAdmin) o;
        return mAdminKey.equals(other.mAdminKey);
    }

    @Override
    public int hashCode() {
        return mAdminKey.hashCode();
    }

    void saveToXml(TypedXmlSerializer serializer) throws IOException {
        serializer.attribute(/* namespace= */ null, ATTR_PACKAGE_NAME, mAdminKey.getPackageName());
        serializer.attributeBoolean(/* namespace= */ null, ATTR_IS_ROLE, mIsRoleAuthority);
        serializer.attributeInt(/* namespace= */ null, ATTR_USER_ID, mAdminKey.getUserId());
        if (isSystemAuthority()) {
            serializer.attribute(
                    /* namespace= */ null,
                    ATTR_SYSTEM_ENTITY,
                    mAdminKey.getSystemEntity());
        }
        if (!mIsRoleAuthority && !isSystemAuthority()) {
            if (mComponentName != null) {
                serializer.attribute(
                        /* namespace= */ null, ATTR_CLASS_NAME, mComponentName.getClassName());
            }
            // Role authorities get recomputed on load so no need to save them.
            serializer.attribute(
                    /* namespace= */ null,
                    ATTR_AUTHORITIES,
                    String.join(ATTR_AUTHORITIES_SEPARATOR, getAuthorities()));
        }
    }

    @Nullable
    static EnforcingAdmin readFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException {
        String packageName = parser.getAttributeValue(/* namespace= */ null, ATTR_PACKAGE_NAME);
        String systemEntity = parser.getAttributeValue(/* namespace= */ null, ATTR_SYSTEM_ENTITY);
        boolean isRoleAuthority = parser.getAttributeBoolean(/* namespace= */ null, ATTR_IS_ROLE);
        String authoritiesStr = parser.getAttributeValue(/* namespace= */ null, ATTR_AUTHORITIES);
        int userId = parser.getAttributeInt(/* namespace= */ null, ATTR_USER_ID);

        if (isRoleAuthority) {
            if (packageName == null) {
                Slogf.wtf(TAG,
                        "Error parsing EnforcingAdmin with RoleAuthority, packageName is null.");
                return null;
            }
            return createRoleEnforcingAdmin(packageName, userId);
        } else if (systemEntity != null) {
            return createSystemEnforcingAdmin(systemEntity);
        } else {
            if (packageName == null || authoritiesStr == null) {
                Slogf.wtf(TAG, "Error parsing EnforcingAdmin, packageName=%s, authorities=%s.",
                        packageName, authoritiesStr);
                return null;
            }
            String className = parser.getAttributeValue(/* namespace= */ null, ATTR_CLASS_NAME);
            ComponentName componentName = className == null
                    ? null : new ComponentName(packageName, className);
            String[] authorities = authoritiesStr.split(ATTR_AUTHORITIES_SEPARATOR);

            // The only well-formed admin types not handled above are DPCs and DAs.
            if (authorities.length == 1 && componentName != null) {
                switch (authorities[0]) {
                    case DPC_AUTHORITY -> {
                        return createEnterpriseEnforcingAdmin(componentName, userId);
                    }
                    case DEVICE_ADMIN_AUTHORITY -> {
                        return createDeviceAdminEnforcingAdmin(componentName, userId);
                    }
                }
            }

            // We've got a freak of an admin that should be impossible to create.
            Slogf.wtf(TAG,
                    "Invalid EnforcingAdmin, package: %s, component: %s, authorities: %s",
                    packageName, componentName, authoritiesStr);
            return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EnforcingAdmin { mPackageName= ");
        sb.append(mAdminKey.getPackageName());
        if (mComponentName != null) {
            sb.append(", mComponentName= ");
            sb.append(mComponentName);
        }
        if (mAuthorities != null) {
            sb.append(", mAuthorities= ");
            sb.append(mAuthorities);
        }
        sb.append(", mUserId= ");
        sb.append(mAdminKey.getUserId());
        sb.append(", mIsRoleAuthority= ");
        sb.append(mIsRoleAuthority);
        sb.append(", mSystemEntity = ");
        sb.append(mAdminKey.getSystemEntity());
        sb.append(" }");
        return sb.toString();
    }
}
