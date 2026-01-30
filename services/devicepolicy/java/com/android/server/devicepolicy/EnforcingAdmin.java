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
import android.app.admin.flags.Flags;
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
final class EnforcingAdmin {

    static final String TAG = "EnforcingAdmin";

    static final String ROLE_AUTHORITY_PREFIX = "role:";
    static final String SYSTEM_AUTHORITY_PREFIX = "system:";
    static final String DPC_AUTHORITY = "enterprise";
    static final String DEVICE_ADMIN_AUTHORITY = "device_admin";
    static final String DEFAULT_AUTHORITY = "default";

    private static final String ATTR_PACKAGE_NAME = "package-name";
    private static final String ATTR_SYSTEM_ENTITY = "system-entity";
    private static final String ATTR_CLASS_NAME = "class-name";
    private static final String ATTR_AUTHORITIES = "authorities";
    private static final String ATTR_AUTHORITIES_SEPARATOR = ";";
    private static final String ATTR_USER_ID = "user-id";
    private static final String ATTR_IS_ROLE = "is-role";
    @Deprecated // It is no longer read and will be removed.
    private static final String ATTR_IS_SYSTEM = "is-system";

    private final String mPackageName;
    // Name of the system entity. Only used when mIsSystemAuthority is true.
    private final String mSystemEntity;
    // This is needed for DPCs and active admins
    private final ComponentName mComponentName;
    private Set<String> mAuthorities;
    private final int mUserId;
    private final boolean mIsRoleAuthority;

    static EnforcingAdmin createEnforcingAdmin(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);
        return new EnforcingAdmin(packageName, userId);
    }

    static EnforcingAdmin createEnterpriseEnforcingAdmin(
            @NonNull ComponentName componentName, int userId) {
        Objects.requireNonNull(componentName);
        return new EnforcingAdmin(
                componentName.getPackageName(), componentName, Set.of(DPC_AUTHORITY), userId);
    }

    static EnforcingAdmin createDeviceAdminEnforcingAdmin(ComponentName componentName, int userId) {
        Objects.requireNonNull(componentName);
        return new EnforcingAdmin(
                componentName.getPackageName(), componentName, Set.of(DEVICE_ADMIN_AUTHORITY),
                userId);
    }

    static EnforcingAdmin createSystemEnforcingAdmin(@NonNull String systemEntity) {
        Objects.requireNonNull(systemEntity);
        return new EnforcingAdmin(systemEntity);
    }

    static EnforcingAdmin createEnforcingAdmin(android.app.admin.EnforcingAdmin admin) {
        Objects.requireNonNull(admin);
        Authority authority = admin.getAuthority();
        if (DpcAuthority.DPC_AUTHORITY.equals(authority)) {
            return new EnforcingAdmin(
                    admin.getPackageName(), admin.getComponentName(),
                    Set.of(DPC_AUTHORITY), admin.getUserHandle().getIdentifier());
        } else if (DeviceAdminAuthority.DEVICE_ADMIN_AUTHORITY.equals(authority)) {
            return new EnforcingAdmin(
                    admin.getPackageName(), admin.getComponentName(),
                    Set.of(DEVICE_ADMIN_AUTHORITY), admin.getUserHandle().getIdentifier());
        } else if (authority instanceof RoleAuthority roleAuthority) {
            return new EnforcingAdmin(
                    admin.getPackageName(), admin.getComponentName(),
                    roleAuthority.getRoles(), admin.getUserHandle().getIdentifier(),
                    /* isRoleAuthority = */ true);
        } else if (authority instanceof SystemAuthority systemAuthority) {
            return new EnforcingAdmin(systemAuthority.getSystemEntity());
        }
        return new EnforcingAdmin(admin.getPackageName(), admin.getComponentName(),
                Set.of(), admin.getUserHandle().getIdentifier());
    }

    static String getRoleAuthorityOf(String roleName) {
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

    private EnforcingAdmin(
            String packageName, @Nullable ComponentName componentName, Set<String> authorities,
            int userId) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(authorities);

        // Role/System authorities should not be using this constructor
        mIsRoleAuthority = false;
        mPackageName = packageName;
        mSystemEntity = null;
        mComponentName = componentName;
        mAuthorities = new HashSet<>(authorities);
        mUserId = userId;
    }

    private EnforcingAdmin(String packageName, int userId) {
        Objects.requireNonNull(packageName);

        // Only role authorities use this constructor.
        mIsRoleAuthority = true;
        mPackageName = packageName;
        mSystemEntity = null;
        mUserId = userId;
        mComponentName = null;
        // authorities will be loaded when needed
        mAuthorities = null;
    }

    /** Constructor for System authorities. */
    private EnforcingAdmin(@NonNull String systemEntity) {
        Objects.requireNonNull(systemEntity);

        // Only system authorities use this constructor.
        mIsRoleAuthority = false;
        // Package name is not used for a system enforcing admin, so an empty string is fine.
        mPackageName = "";
        mSystemEntity = systemEntity;
        mUserId = UserHandle.USER_SYSTEM;
        mComponentName = null;
        mAuthorities = getSystemAuthority(systemEntity);
    }

    private EnforcingAdmin(
            String packageName, @Nullable ComponentName componentName, Set<String> authorities,
            int userId, boolean isRoleAuthority) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(authorities);

        mIsRoleAuthority = isRoleAuthority;
        mPackageName = packageName;
        mSystemEntity = null;
        mComponentName = componentName;
        mAuthorities = new HashSet<>(authorities);
        mUserId = userId;
    }

    private static Set<String> getRoleAuthoritiesOrDefault(String packageName, int userId) {
        Set<String> roles = getRoles(packageName, userId);
        Set<String> authorities = new HashSet<>();
        for (String role : roles) {
            authorities.add(ROLE_AUTHORITY_PREFIX + role);
        }
        return authorities.isEmpty() ? Set.of(DEFAULT_AUTHORITY) : authorities;
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
            mAuthorities = getRoleAuthoritiesOrDefault(mPackageName, mUserId);
        }
        return mAuthorities;
    }

    void reloadRoleAuthorities() {
        if (mIsRoleAuthority) {
            mAuthorities = getRoleAuthoritiesOrDefault(mPackageName, mUserId);
        }
    }

    boolean hasAuthority(String authority) {
        return getAuthorities().contains(authority);
    }

    boolean isSystemAuthority() {
        return mSystemEntity != null;
    }

    boolean isSupervisionAdmin() {
        return hasAuthority(getRoleAuthorityOf(RoleManager.ROLE_SYSTEM_SUPERVISION));
    }

    @NonNull
    String getPackageName() {
        return mPackageName;
    }

    int getUserId() {
        return mUserId;
    }

    @Nullable
    ComponentName getComponentName() {
        return mComponentName;
    }

    @Nullable
    String getSystemEntity() {
        return mSystemEntity;
    }

    @NonNull
    android.app.admin.EnforcingAdmin getParcelableAdmin() {
        Authority authority;
        if (mIsRoleAuthority) {
            Set<String> roles = getRoles(mPackageName, mUserId);
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
            authority = new SystemAuthority(mSystemEntity);
        } else {
            authority = UnknownAuthority.UNKNOWN_AUTHORITY;
        }
        return new android.app.admin.EnforcingAdmin(
                mPackageName,
                authority,
                UserHandle.of(mUserId),
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
        return Objects.equals(mPackageName, other.mPackageName)
                && Objects.equals(mSystemEntity, other.mSystemEntity)
                && Objects.equals(mComponentName, other.mComponentName)
                && Objects.equals(mIsRoleAuthority, other.mIsRoleAuthority)
                && (isSystemAuthority() == other.isSystemAuthority())
                && hasMatchingAuthorities(this, other);
    }

    private static boolean hasMatchingAuthorities(EnforcingAdmin admin1, EnforcingAdmin admin2) {
        if (admin1.mIsRoleAuthority && admin2.mIsRoleAuthority) {
            return true;
        }
        return admin1.getAuthorities().equals(admin2.getAuthorities());
    }

    @Override
    public int hashCode() {
        if (mIsRoleAuthority) {
            return Objects.hash(mPackageName, mUserId);
        } else if (isSystemAuthority()) {
            return Objects.hash(mSystemEntity);
        } else {
            return Objects.hash(
                    mComponentName == null ? mPackageName : mComponentName,
                    mUserId,
                    getAuthorities());
        }
    }

    void saveToXml(TypedXmlSerializer serializer) throws IOException {
        serializer.attribute(/* namespace= */ null, ATTR_PACKAGE_NAME, mPackageName);
        serializer.attributeBoolean(/* namespace= */ null, ATTR_IS_ROLE, mIsRoleAuthority);
        if (!Flags.dontWriteIsSystemAuthority()) {
            serializer.attributeBoolean(/* namespace= */ null, ATTR_IS_SYSTEM, isSystemAuthority());
        }
        serializer.attributeInt(/* namespace= */ null, ATTR_USER_ID, mUserId);
        if (isSystemAuthority()) {
            serializer.attribute(/* namespace= */ null, ATTR_SYSTEM_ENTITY, mSystemEntity);
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
                Slogf.wtf(TAG, "Error parsing EnforcingAdmin with RoleAuthority, packageName is "
                        + "null.");
                return null;
            }
            // TODO(b/281697976): load active admin
            return new EnforcingAdmin(packageName, userId);
        } else if (systemEntity != null) {
            return new EnforcingAdmin(systemEntity);
        } else {
            if (packageName == null || authoritiesStr == null) {
                Slogf.wtf(TAG, "Error parsing EnforcingAdmin, packageName is "
                        + (packageName == null ? "null" : packageName) + ", and authorities is "
                        + (authoritiesStr == null ? "null" : authoritiesStr) + ".");
                return null;
            }
            String className = parser.getAttributeValue(/* namespace= */ null, ATTR_CLASS_NAME);
            ComponentName componentName = className == null
                    ? null :  new ComponentName(packageName, className);
            Set<String> authorities = Set.of(authoritiesStr.split(ATTR_AUTHORITIES_SEPARATOR));
            // TODO(b/281697976): load active admin
            return new EnforcingAdmin(packageName, componentName, authorities, userId);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EnforcingAdmin { mPackageName= ");
        sb.append(mPackageName);
        if (mComponentName != null) {
            sb.append(", mComponentName= ");
            sb.append(mComponentName);
        }
        if (mAuthorities != null) {
            sb.append(", mAuthorities= ");
            sb.append(mAuthorities);
        }
        sb.append(", mUserId= ");
        sb.append(mUserId);
        sb.append(", mIsRoleAuthority= ");
        sb.append(mIsRoleAuthority);
        sb.append(", mSystemEntity = ");
        sb.append(mSystemEntity);
        sb.append(" }");
        return sb.toString();
    }
}
